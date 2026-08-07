<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import { useCallStore } from '@/store/call'

const callStore = useCallStore()

const remoteVideoEl = ref<HTMLVideoElement>()
const localVideoEl = ref<HTMLVideoElement>()

const isVideo = computed(() => callStore.callType === 2)
const typeText = computed(() => (isVideo.value ? '视频' : '语音'))

const durationText = computed(() => {
  const s = callStore.duration
  const mm = String(Math.floor(s / 60)).padStart(2, '0')
  const ss = String(s % 60).padStart(2, '0')
  return `${mm}:${ss}`
})

/** 流绑定：元素或流变化时同步到 <video>.srcObject */
watchEffect(() => {
  if (remoteVideoEl.value) remoteVideoEl.value.srcObject = callStore.remoteStream
})
watchEffect(() => {
  if (localVideoEl.value) localVideoEl.value.srcObject = callStore.localStream
})

function displayName(): string {
  return callStore.peerName || `用户${callStore.peerId}`
}

function onAccept() {
  callStore.accept()
}

function onReject() {
  callStore.reject()
}

function onCancelOrHangup() {
  if (callStore.status === 'outgoing') {
    callStore.cancel()
  } else {
    callStore.hangup()
  }
}
</script>

<template>
  <transition name="fade">
    <div v-if="callStore.status !== 'idle'" class="call-panel">
      <!-- 视频通话：远端全屏 + 本地小窗 -->
      <template v-if="isVideo && callStore.status === 'active'">
        <video ref="remoteVideoEl" class="video-remote" autoplay playsinline />
        <video ref="localVideoEl" class="video-local" autoplay playsinline muted />
        <div class="video-top">
          <div class="video-name">{{ displayName() }}</div>
          <div class="video-duration">{{ durationText }}</div>
        </div>
        <div class="call-actions video-actions">
          <button class="action-btn action-hangup" @click="callStore.hangup()">
            <van-icon name="phone-o" size="26" />
          </button>
        </div>
      </template>

      <!-- 语音通话 / 呼叫中（全屏暗底） -->
      <template v-else>
        <div class="voice-bg">
          <div class="voice-avatar">
            <van-image
              round
              width="84"
              height="84"
              :src="callStore.peerAvatar || ''"
              fit="cover"
            >
              <template #error>
                <div class="avatar-fallback lg">{{ displayName().slice(0, 1) }}</div>
              </template>
            </van-image>
          </div>

          <div class="voice-name">{{ displayName() }}</div>

          <!-- 通话中：显示时长；呼出/来电：状态文案 -->
          <div class="voice-status">
            <template v-if="callStore.status === 'active'">
              {{ durationText }}
            </template>
            <template v-else-if="callStore.status === 'outgoing'">
              正在等待对方接听...
            </template>
            <template v-else>
              {{ displayName() }} 邀请你{{ typeText }}通话
            </template>
          </div>

          <div class="call-actions">
            <!-- 来电：拒绝 + 接听 -->
            <template v-if="callStore.status === 'incoming'">
              <div class="action-group">
                <button class="action-btn action-reject" @click="onReject">
                  <van-icon name="close" size="26" />
                </button>
                <span class="action-label">拒绝</span>
              </div>
              <div class="action-group">
                <button class="action-btn action-accept" @click="onAccept">
                  <van-icon :name="isVideo ? 'video-o' : 'phone-o'" size="26" />
                </button>
                <span class="action-label">接听</span>
              </div>
            </template>

            <!-- 呼出：取消 -->
            <div v-else-if="callStore.status === 'outgoing'" class="action-group">
              <button class="action-btn action-reject" @click="onCancelOrHangup">
                <van-icon name="close" size="26" />
              </button>
              <span class="action-label">取消</span>
            </div>

            <!-- 通话中：挂断 -->
            <div v-else class="action-group">
              <button class="action-btn action-hangup" @click="callStore.hangup()">
                <van-icon name="phone-o" size="26" />
              </button>
              <span class="action-label">挂断</span>
            </div>
          </div>
        </div>
      </template>
    </div>
  </transition>
</template>

<style scoped>
.call-panel {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: #000;
  color: #fff;
}

/* 视频通话 */
.video-remote {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-local {
  position: absolute;
  top: env(safe-area-inset-top);
  right: 12px;
  width: 96px;
  height: 128px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.video-top {
  position: absolute;
  top: calc(env(safe-area-inset-top) + 16px);
  left: 0;
  right: 0;
  text-align: center;
  pointer-events: none;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
}

.video-name {
  font-size: 18px;
  font-weight: 600;
}

.video-duration {
  font-size: 13px;
  opacity: 0.85;
  margin-top: 4px;
}

/* 语音 / 呼叫中 */
.voice-bg {
  height: 100%;
  background: radial-gradient(circle at 50% 35%, #1a2740 0%, #0a0f1a 70%);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 22vh;
}

.avatar-fallback.lg {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4da8ff, #1e90ff);
  color: #fff;
  font-size: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.voice-name {
  margin-top: 18px;
  font-size: 20px;
  font-weight: 600;
}

.voice-status {
  margin-top: 8px;
  font-size: 14px;
  opacity: 0.8;
}

/* 底部操作区 */
.call-actions {
  position: absolute;
  bottom: calc(env(safe-area-inset-bottom) + 48px);
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  gap: 64px;
}

.video-actions {
  gap: 0;
}

.action-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.action-btn {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  border: none;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.action-accept {
  background: #07c160;
}

.action-reject {
  background: #ee0a24;
}

.action-hangup {
  background: #ee0a24;
}

.action-label {
  font-size: 12px;
  opacity: 0.85;
}
</style>
