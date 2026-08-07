import { defineStore } from 'pinia'
import { showToast } from 'vant'
import { callApi } from '@/api/call'
import type { CallSignalVO, UserVO } from '@/types'
import { isConnected, publishSignal } from '@/utils/stomp'
import { useUserStore } from '@/store/user'
import { startRing, stopRing } from '@/utils/ringtone'

/**
 * 通话（WebRTC）状态机。
 *
 * <ul>
 *   <li>信令走 STOMP：上行 /app/call，下行 /user/queue/call（connectChat 注册 onCallSignal）</li>
 *   <li>主叫：call() 建连接收媒体 → invite；收到 accept 后发 offer；收到 answer/candidate 交换 ICE</li>
 *   <li>被叫：收到 invite → 铃声 + 来电页；accept() 时建连接收媒体再回 accept；收到 offer 回 answer</li>
 *   <li>remoteDescription 未就绪前的本地 ICE 候选先入队，就绪后统一加入</li>
 * </ul>
 */

type CallStatus = 'idle' | 'outgoing' | 'incoming' | 'active'

export const useCallStore = defineStore('call', {
  state: () => ({
    status: 'idle' as CallStatus,
    /** 1 语音，2 视频 */
    callType: 1 as number,
    callId: '',
    callerId: 0,
    calleeId: 0,
    peerId: 0,
    peerName: '',
    peerAvatar: '',
    pc: null as RTCPeerConnection | null,
    localStream: null as MediaStream | null,
    remoteStream: null as MediaStream | null,
    /** remoteDescription 就绪前收集的本地候选 */
    pendingCandidates: [] as RTCIceCandidateInit[],
    iceServers: [] as RTCIceServer[],
    iceLoaded: false,
    duration: 0,
    timer: null as number | null,
    startTs: 0,
    initialized: false,
  }),

  actions: {
    /** 注册通话信令订阅 + 预取 ICE 配置（幂等）。 */
    init() {
      if (this.initialized) {
        return
      }
      this.initialized = true
      import('@/utils/stomp').then(({ connectChat }) => {
        connectChat({ onCallSignal: (sig) => this.handleSignal(sig) })
      })
      this.loadIceServers()
    },

    async loadIceServers() {
      if (this.iceLoaded) {
        return
      }
      try {
        const config = await callApi.config()
        this.iceServers = (config.iceServers ?? []).map((s) => ({
          urls: s.urls,
          username: s.username,
          credential: s.credential,
        }))
        this.iceLoaded = true
      } catch {
        // 配置失败时用默认 STUN 兜底
        this.iceServers = [{ urls: 'stun:stun.l.google.com:19302' }]
        this.iceLoaded = true
      }
    },

    /** 主叫：发起通话。媒体延后到对方接听后获取（避免振铃阶段占住摄像头/麦克风）。 */
    call(peer: UserVO, type: number) {
      if (this.status !== 'idle') {
        showToast('你正在通话中')
        return
      }
      if (!isConnected()) {
        showToast('网络未连接，请稍后再试')
        return
      }
      const meId = useUserStore().userInfo?.id ?? 0
      this.status = 'outgoing'
      this.callType = type
      this.callId = crypto.randomUUID().replace(/-/g, '')
      this.callerId = meId
      this.calleeId = peer.id
      this.peerId = peer.id
      this.peerName = peer.nickname
      this.peerAvatar = peer.avatar ?? ''
      startRing()
      publishSignal({
        action: 'invite',
        callId: this.callId,
        callType: this.callType,
        to: this.calleeId,
      })
    },

    /** 被叫：接听。先建连接收媒体，成功后再回 accept（保证 offer 到达时连接就绪）。 */
    async accept() {
      if (this.status !== 'incoming') {
        return
      }
      try {
        await this.setupPeerConnection()
      } catch {
        // 媒体不可用无法接听：通知对方结束，而非 reject（避免误导"对方拒听"）
        const callId = this.callId
        const to = this.peerId
        this.cleanup()
        publishSignal({ action: 'end', callId, to, reason: 'media' })
        showToast('无法获取麦克风/摄像头权限')
        return
      }
      stopRing()
      publishSignal({ action: 'accept', callId: this.callId, to: this.callerId })
      this.status = 'active'
      this.startTimer()
    },

    /** 被叫：拒绝。 */
    reject() {
      publishSignal({ action: 'reject', callId: this.callId, to: this.callerId })
      this.cleanup()
    },

    /** 主叫：未接通前取消。 */
    cancel() {
      publishSignal({ action: 'cancel', callId: this.callId, to: this.calleeId })
      this.cleanup()
    },

    /** 挂断（通话中）。 */
    hangup() {
      publishSignal({ action: 'end', callId: this.callId, to: this.peerId, reason: 'hangup' })
      this.cleanup()
    },

    /** 建 RTCPeerConnection + 采集媒体（主叫接通时 / 被叫接听时）。 */
    async setupPeerConnection() {
      await this.loadIceServers()
      if (this.pc) {
        this.pc.close()
        this.localStream?.getTracks().forEach((t) => t.stop())
        this.pc = null
        this.localStream = null
      }
      const pc = new RTCPeerConnection({ iceServers: this.iceServers })
      pc.onicecandidate = (e) => {
        if (e.candidate) {
          this.queueOrSendCandidate(e.candidate)
        }
      }
      pc.ontrack = (e) => {
        this.remoteStream = e.streams[0] ?? null
      }
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: this.callType === 2,
      })
      stream.getTracks().forEach((track) => pc.addTrack(track, stream))
      this.localStream = stream
      this.pc = pc
    },

    /** 本地候选：远端描述就绪则立即发，否则入队。 */
    queueOrSendCandidate(candidate: RTCIceCandidate) {
      if (this.pc?.remoteDescription) {
        publishSignal({
          action: 'candidate',
          callId: this.callId,
          to: this.peerId,
          candidate: JSON.stringify(candidate.toJSON()),
        })
      } else {
        this.pendingCandidates.push(candidate.toJSON())
      }
    },

    async flushCandidates() {
      const list = this.pendingCandidates.splice(0)
      for (const init of list) {
        try {
          await this.pc?.addIceCandidate(init)
        } catch {
          // 过时候选忽略
        }
      }
    },

    // ---------- 信令分发 ----------
    handleSignal(sig: CallSignalVO) {
      switch (sig.action) {
        case 'invite':
          void this.handleInvite(sig)
          break
        case 'accept':
          void this.handleAccept(sig)
          break
        case 'offer':
          void this.handleOffer(sig)
          break
        case 'answer':
          void this.handleAnswer(sig)
          break
        case 'candidate':
          void this.handleCandidate(sig)
          break
        case 'reject':
          this.cleanup()
          showToast('对方已拒绝')
          break
        case 'cancel':
          this.cleanup()
          showToast('对方已取消')
          break
        case 'end':
          this.cleanup()
          showToast(sig.reason === 'offline' ? '对方网络已断开' : '通话已结束')
          break
        case 'busy':
          this.cleanup()
          showToast(sig.reason || '对方正在通话中')
          break
        case 'offline':
          this.cleanup()
          showToast(sig.reason || '对方不在线')
          break
      }
    },

    async handleInvite(sig: CallSignalVO) {
      // 防御：忽略发给自己的回显/残留 invite（正常不会出现；避免自拒接导致"对方拒听"）
      const meId = useUserStore().userInfo?.id ?? 0
      if (sig.callerId === meId) {
        return
      }
      // 已在通话中：防御性拒绝（正常由服务端 busy 拦截）
      if (this.status !== 'idle') {
        publishSignal({ action: 'reject', callId: sig.callId ?? '', to: sig.callerId ?? 0 })
        return
      }
      this.status = 'incoming'
      this.callId = sig.callId ?? ''
      this.callType = sig.callType ?? 1
      this.callerId = sig.callerId ?? 0
      this.calleeId = useUserStore().userInfo?.id ?? 0
      this.peerId = this.callerId
      this.peerName = sig.peerName ?? ''
      this.peerAvatar = sig.peerAvatar ?? ''
      startRing()
    },

    /** 主叫：对方接听 → 获取媒体、发 offer。 */
    async handleAccept(_sig: CallSignalVO) {
      if (this.status !== 'outgoing') {
        return
      }
      this.status = 'active'
      stopRing()
      this.startTimer()
      try {
        await this.setupPeerConnection()
        const pc = this.pc
        if (!pc) return
        const offer = await pc.createOffer()
        await pc.setLocalDescription(offer)
        publishSignal({
          action: 'offer',
          callId: this.callId,
          to: this.calleeId,
          sdp: offer.sdp ?? '',
        })
      } catch {
        const callId = this.callId
        const to = this.calleeId
        this.cleanup()
        publishSignal({ action: 'end', callId, to, reason: 'media' })
        showToast('无法获取麦克风/摄像头权限')
      }
    },

    /** 被叫：收到 offer → 回 answer。 */
    async handleOffer(sig: CallSignalVO) {
      const pc = this.pc
      if (!pc) return
      try {
        await pc.setRemoteDescription({ type: 'offer', sdp: sig.sdp ?? '' })
        await this.flushCandidates()
        const answer = await pc.createAnswer()
        await pc.setLocalDescription(answer)
        publishSignal({
          action: 'answer',
          callId: this.callId,
          to: this.callerId,
          sdp: answer.sdp ?? '',
        })
      } catch {
        // SDP 不匹配等异常 → 直接挂断
        this.hangup()
      }
    },

    /** 主叫：收到 answer。 */
    async handleAnswer(sig: CallSignalVO) {
      try {
        await this.pc?.setRemoteDescription({ type: 'answer', sdp: sig.sdp ?? '' })
        await this.flushCandidates()
      } catch {
        // 忽略重复/异常 answer
      }
    },

    async handleCandidate(sig: CallSignalVO) {
      if (!sig.candidate) return
      let init: RTCIceCandidateInit
      try {
        init = JSON.parse(sig.candidate) as RTCIceCandidateInit
      } catch {
        return
      }
      if (this.pc?.remoteDescription) {
        try {
          await this.pc.addIceCandidate(init)
        } catch {
          // 忽略
        }
      } else {
        this.pendingCandidates.push(init)
      }
    },

    // ---------- 清理 ----------
    startTimer() {
      this.startTs = Date.now()
      this.duration = 0
      if (this.timer) {
        window.clearInterval(this.timer)
      }
      this.timer = window.setInterval(() => {
        this.duration = Math.floor((Date.now() - this.startTs) / 1000)
      }, 1000)
    },

    stopTimer() {
      if (this.timer !== null) {
        window.clearInterval(this.timer)
        this.timer = null
      }
    },

    cleanup() {
      this.stopTimer()
      stopRing()
      this.localStream?.getTracks().forEach((t) => t.stop())
      this.pc?.close()
      this.pc = null
      this.localStream = null
      this.remoteStream = null
      this.pendingCandidates = []
      this.status = 'idle'
      this.callId = ''
      this.callerId = 0
      this.calleeId = 0
      this.peerId = 0
      this.peerName = ''
      this.peerAvatar = ''
      this.duration = 0
    },

    logout() {
      this.cleanup()
      this.initialized = false
    },
  },
})
