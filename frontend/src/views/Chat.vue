<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { chatApi } from '@/api/chat'
import { uploadFile } from '@/api/file'
import { useChatStore } from '@/store/chat'
import { useCallStore } from '@/store/call'
import { useUserStore } from '@/store/user'
import type { ConversationVO, MessageVO } from '@/types'
import { formatTime } from '@/utils/format'
import { uuid } from '@/utils/uuid'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const callStore = useCallStore()
const userStore = useUserStore()

const conversationId = Number(route.params.conversationId)
const peerId = Number(route.query.peerId ?? 0)
const meId = userStore.userInfo?.id ?? 0

const conv = ref<ConversationVO | null>(null)
const messages = ref<MessageVO[]>([])
const listEl = ref<HTMLElement>()

let cursorId: number | null = null
let hasMore = true
let loadingOlder = false
let loadingHistory = false

const input = ref('')
const sending = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()

const previewShow = ref(false)
const previewUrl = ref('')

// ---------- 加载历史 ----------
async function loadHistory() {
  if (loadingHistory) return
  loadingHistory = true
  try {
    const page = await chatApi.messages(conversationId, cursorId, 20)
    hasMore = page.hasMore
    if (messages.value.length === 0) {
      messages.value = page.records
      await nextTick()
      scrollToBottom()
    } else {
      const el = listEl.value
      const oldHeight = el?.scrollHeight ?? 0
      messages.value = [...page.records, ...messages.value]
      await nextTick()
      if (el) {
        el.scrollTop = el.scrollHeight - oldHeight
      }
    }
    if (page.records.length > 0) {
      cursorId = page.records[0].id
    }
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    loadingHistory = false
  }
}

function onScroll() {
  const el = listEl.value
  if (!el || loadingOlder || !hasMore || loadingHistory) return
  if (el.scrollTop < 40) {
    loadingOlder = true
    loadHistory().finally(() => {
      loadingOlder = false
    })
  }
}

function scrollToBottom() {
  const el = listEl.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

// ---------- 发送 ----------
function nowStr(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function makeTemp(content: string, msgType: number, clientMsgId: string): MessageVO {
  return {
    id: -Date.now(),
    conversationId,
    senderId: meId,
    receiverId: peerId,
    msgType,
    content,
    status: 0,
    clientMsgId,
    createdAt: nowStr(),
  }
}

function replaceById(clientMsgId: string, saved: MessageVO) {
  const idx = messages.value.findIndex((m) => m.clientMsgId === clientMsgId)
  if (idx >= 0) {
    messages.value.splice(idx, 1, saved)
  } else {
    messages.value.push(saved)
  }
  scrollToBottom()
}

async function sendText() {
  const text = input.value.trim()
  if (!text || sending.value) return
  input.value = ''
  sending.value = true
  const clientMsgId = uuid()
  messages.value.push(makeTemp(text, 1, clientMsgId))
  scrollToBottom()
  try {
    const saved = await chatApi.send({ receiverId: peerId, msgType: 1, content: text, clientMsgId })
    replaceById(clientMsgId, saved)
  } catch (e) {
    showToast((e as Error).message)
    // 发送失败保留气泡，用户可看到状态
    const temp = messages.value.find((m) => m.clientMsgId === clientMsgId)
    if (temp) temp.content = `${text}（发送失败）`
  } finally {
    sending.value = false
  }
}

function pickImage() {
  fileInput.value?.click()
}

async function onFileChange(e: Event) {
  const inputEl = e.target as HTMLInputElement
  const file = inputEl.files?.[0]
  inputEl.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    showToast('只能选择图片')
    return
  }
  if (file.size > 20 * 1024 * 1024) {
    showToast('图片不能超过 20MB')
    return
  }
  uploading.value = true
  const clientMsgId = uuid()
  try {
    const res = await uploadFile(file, 'chat')
    messages.value.push(makeTemp(res.url, 2, clientMsgId))
    scrollToBottom()
    const saved = await chatApi.send({ receiverId: peerId, msgType: 2, content: res.url, clientMsgId })
    replaceById(clientMsgId, saved)
  } catch (err) {
    showToast((err as Error).message)
  } finally {
    uploading.value = false
  }
}

// ---------- 实时推送（store 分发） ----------
function onPushMessage(message: MessageVO) {
  const exists = messages.value.some(
    (m) => m.id === message.id || (m.clientMsgId && m.clientMsgId === message.clientMsgId),
  )
  if (exists) {
    // 自己的消息推送回显：用服务端消息替换本地临时消息（拿到真实 id）
    replaceById(message.clientMsgId ?? '', message)
    return
  }
  messages.value.push(message)
  scrollToBottom()
}

function onPushRead() {
  messages.value = messages.value.map((m) => (m.senderId === meId ? { ...m, status: 1 } : m))
}

// ---------- 生命周期 ----------
onMounted(async () => {
  chatStore.ensureConnected()
  chatStore.setListener({ conversationId, onMessage: onPushMessage, onRead: onPushRead })
  try {
    const c = await chatApi.conversationDetail(conversationId)
    conv.value = c
  } catch {
    // 详情失败不阻塞聊天
  }
  await loadHistory()
  chatApi.read(conversationId).catch(() => {})
})

onBeforeUnmount(() => {
  chatStore.clearListener(conversationId)
  chatApi.read(conversationId).catch(() => {})
})

// ---------- 展示 ----------
const lastOwn = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].senderId === meId) return messages.value[i]
  }
  return null
})

function own(m: MessageVO): boolean {
  return m.senderId === meId
}

function statusText(m: MessageVO): string {
  if (!own(m)) return ''
  if (m.id < 0) return '发送中'
  return m.status === 1 ? '已读' : '未读'
}

function showTime(index: number): boolean {
  if (index === 0) return true
  const cur = new Date(messages.value[index].createdAt.replace(' ', 'T')).getTime()
  const prev = new Date(messages.value[index - 1].createdAt.replace(' ', 'T')).getTime()
  return cur - prev > 5 * 60_000
}

function onPreviewImage(url: string) {
  previewUrl.value = url
  previewShow.value = true
}

function goBack() {
  router.back()
}

function peerName(): string {
  return conv.value?.peer.nickname || `用户${peerId}`
}

function startCall(type: number) {
  const peer = conv.value?.peer
  if (!peer) {
    showToast('对方信息未加载')
    return
  }
  callStore.call(peer, type)
}
</script>

<template>
  <div class="chat-page">
    <!-- 头部 -->
    <div class="chat-header">
      <van-icon name="arrow-left" size="20" class="back" @click="goBack" />
      <div class="header-title">
        <div class="title-name">{{ peerName() }}</div>
        <div v-if="conv" class="title-sub">{{ conv.peerOnline ? '在线' : '离线' }}</div>
      </div>
      <div class="header-actions">
        <!-- 在线时提供语音/视频入口 -->
        <template v-if="conv && conv.peerOnline">
          <van-icon name="phone-o" size="20" class="action-icon" @click="startCall(1)" />
          <van-icon name="video-o" size="22" class="action-icon" @click="startCall(2)" />
        </template>
        <van-icon v-else name="user-circle-o" size="20" />
      </div>
    </div>

    <!-- 消息列表 -->
    <div ref="listEl" class="msg-list" @scroll="onScroll">
      <div v-if="!hasMore" class="msg-tip text-secondary">没有更多消息了</div>
      <div v-for="(m, i) in messages" :key="m.id" class="msg-item">
        <div v-if="showTime(i)" class="msg-time">{{ formatTime(m.createdAt) }}</div>

        <!-- 图片消息 -->
        <div v-if="m.msgType === 2" :class="['msg-bubble', own(m) ? 'right' : 'left']">
          <van-image
            v-if="!own(m)"
            round
            width="32"
            height="32"
            :src="conv?.peer.avatar || ''"
            class="msg-avatar"
            fit="cover"
          >
            <template #error>
              <div class="avatar-fallback small">{{ conv?.peer.nickname?.slice(0, 1) }}</div>
            </template>
          </van-image>
          <div class="img-wrap">
            <van-image :src="m.content" width="160" :height="m.content ? undefined : 120" fit="cover" class="chat-img" @click="onPreviewImage(m.content)" />
          </div>
          <span v-if="own(m) && m === lastOwn" class="status">{{ statusText(m) }}</span>
        </div>

        <!-- 文本消息 -->
        <div v-else :class="['msg-bubble', own(m) ? 'right' : 'left']">
          <van-image
            v-if="!own(m)"
            round
            width="32"
            height="32"
            :src="conv?.peer.avatar || ''"
            class="msg-avatar"
            fit="cover"
          >
            <template #error>
              <div class="avatar-fallback small">{{ conv?.peer.nickname?.slice(0, 1) }}</div>
            </template>
          </van-image>
          <div class="msg-text">{{ m.content }}</div>
          <span v-if="own(m) && m === lastOwn" class="status">{{ statusText(m) }}</span>
        </div>
      </div>
    </div>

    <!-- 输入栏 -->
    <div class="chat-input-bar">
      <van-icon name="photo-o" size="24" class="tool-icon" @click="pickImage" />
      <input
        ref="fileInput"
        type="file"
        accept="image/*"
        style="display: none"
        @change="onFileChange"
      />
      <van-field
        v-model="input"
        class="input-field"
        placeholder="说点什么..."
        maxlength="2000"
        @keyup.enter="sendText"
      />
      <van-button
        type="primary"
        size="small"
        round
        class="send-btn"
        :loading="sending || uploading"
        @click="sendText"
      >
        发送
      </van-button>
    </div>

    <!-- 图片预览 -->
    <van-image-preview v-model:show="previewShow" :images="[previewUrl]" closeable close-icon-position="top-right" />
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg);
}

/* 头部 */
.chat-header {
  display: flex;
  align-items: center;
  padding: 12px 14px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.back {
  cursor: pointer;
}

.header-title {
  flex: 1;
  text-align: center;
}

.title-name {
  font-size: 16px;
  font-weight: 600;
}

.title-sub {
  font-size: 11px;
  color: var(--text-secondary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.action-icon {
  color: var(--text-secondary);
}

/* 消息列表 */
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 12px calc(12px + env(safe-area-inset-bottom));
  -webkit-overflow-scrolling: touch;
}

.msg-tip {
  text-align: center;
  font-size: 12px;
  padding: 8px 0;
}

.msg-item {
  margin-bottom: 14px;
}

.msg-time {
  text-align: center;
  font-size: 11px;
  color: #999;
  margin-bottom: 10px;
}

.msg-bubble {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.msg-bubble.left {
  justify-content: flex-start;
}

.msg-bubble.right {
  justify-content: flex-end;
}

.msg-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

.avatar-fallback.small {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4da8ff, #1e90ff);
  color: #fff;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.msg-text {
  max-width: 65%;
  padding: 10px 13px;
  border-radius: 10px;
  font-size: 15px;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}

.msg-bubble.right .msg-text {
  background: var(--primary);
  color: #fff;
  border-top-right-radius: 2px;
}

.msg-bubble.left .msg-text {
  background: var(--card-bg);
  color: var(--text);
  border-top-left-radius: 2px;
}

.img-wrap {
  max-width: 65%;
}

.chat-img {
  border-radius: 10px;
}

.status {
  align-self: flex-end;
  font-size: 11px;
  color: #999;
  flex-shrink: 0;
  padding: 2px;
}

/* 输入栏 */
.chat-input-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  background: var(--card-bg);
  border-top: 1px solid var(--border);
  flex-shrink: 0;
}

.tool-icon {
  color: var(--text-secondary);
  flex-shrink: 0;
}

.input-field {
  flex: 1;
  background: var(--bg);
  border-radius: 18px;
  padding: 4px 12px;
}

.send-btn {
  flex-shrink: 0;
}
</style>
