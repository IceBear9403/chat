<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { chatApi } from '@/api/chat'
import { userApi } from '@/api/user'
import { useChatStore } from '@/store/chat'
import type { ConversationVO, UserVO } from '@/types'
import { formatTime } from '@/utils/format'

const router = useRouter()
const chatStore = useChatStore()

const loading = ref(false)

async function onRefresh() {
  await chatStore.refresh()
  loading.value = false
}

onMounted(() => {
  chatStore.ensureConnected()
  chatStore.refresh()
})

/** 会话列表最后一屏摘要（按消息类型） */
function previewOf(conv: ConversationVO): string {
  const m = conv.lastMessage
  if (!m) return '暂无消息'
  const prefix =
    m.msgType === 2 ? '[图片] ' : m.msgType === 3 ? '[语音] ' : m.msgType === 4 ? '[视频] ' : ''
  return `${prefix}${m.content}`
}

function openChat(conv: ConversationVO) {
  router.push({
    path: `/chat/${conv.id}`,
    query: { peerId: conv.peerId },
  })
}

// ---------- 滑动删除 ----------
async function onDeleteConv(conv: ConversationVO) {
  try {
    await showConfirmDialog({ title: '删除会话', message: `删除与 ${conv.peer.nickname} 的会话？` })
  } catch {
    return
  }
  try {
    await chatApi.deleteConversation(conv.id)
    chatStore.conversations = chatStore.conversations.filter((c) => c.id !== conv.id)
    showToast('已删除')
  } catch (e) {
    showToast((e as Error).message)
  }
}

// ---------- 发起新会话：搜索用户 ----------
const searchShow = ref(false)
const keyword = ref('')
const searching = ref(false)
const results = ref<UserVO[]>([])

function openSearch() {
  keyword.value = ''
  results.value = []
  searchShow.value = true
}

let searchTimer: number | undefined
function onSearch() {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(async () => {
    const kw = keyword.value.trim()
    if (!kw) {
      results.value = []
      return
    }
    searching.value = true
    try {
      results.value = await userApi.search(kw)
    } catch (e) {
      showToast((e as Error).message)
    } finally {
      searching.value = false
    }
  }, 300)
}

async function startChat(user: UserVO) {
  try {
    const conv = await chatApi.openConversation(user.id)
    searchShow.value = false
    router.push({ path: `/chat/${conv.id}`, query: { peerId: conv.peerId } })
  } catch (e) {
    showToast((e as Error).message)
  }
}
</script>

<template>
  <div class="page-container messages-page">
    <div class="page-header">
      <span>消息</span>
      <span class="header-action" @click="openSearch">
        <van-icon name="plus" size="22" />
      </span>
    </div>

    <div v-if="!chatStore.connected" class="conn-tip">
      <van-loading size="14" color="var(--text-secondary)" /> 连接中...
    </div>

    <van-pull-refresh v-model="loading" @refresh="onRefresh">
      <div v-if="chatStore.conversations.length === 0" class="empty-box">
        <van-empty description="暂无会话，点右上角 + 找人聊聊">
          <template #image>
            <van-icon name="chat-o" size="64" color="var(--text-secondary)" />
          </template>
        </van-empty>
      </div>

      <div class="conv-list">
        <van-swipe-cell v-for="conv in chatStore.conversations" :key="conv.id">
          <div class="conv-item" @click="openChat(conv)">
            <div class="conv-avatar">
              <van-image round width="46" height="46" :src="conv.peer.avatar || ''" fit="cover" lazy-load>
                <template #error>
                  <div class="avatar-fallback">{{ conv.peer.nickname?.slice(0, 1) }}</div>
                </template>
              </van-image>
              <span v-if="conv.peerOnline" class="online-dot" />
            </div>

            <div class="conv-body">
              <div class="conv-row">
                <span class="conv-nickname">{{ conv.peer.nickname }}</span>
                <span class="conv-time text-secondary">{{ formatTime(conv.lastMessageTime || conv.lastMessage?.createdAt || '') }}</span>
              </div>
              <div class="conv-row">
                <span class="conv-preview text-secondary">{{ previewOf(conv) }}</span>
                <van-badge v-if="conv.unreadCount > 0" :content="conv.unreadCount > 99 ? '99+' : conv.unreadCount" />
              </div>
            </div>
          </div>
          <template #right>
            <div class="swipe-delete" @click="onDeleteConv(conv)">
              <van-icon name="delete-o" size="20" />
              <span>删除</span>
            </div>
          </template>
        </van-swipe-cell>
      </div>
    </van-pull-refresh>

    <!-- 搜索用户发起会话 -->
    <van-popup v-model:show="searchShow" position="bottom" round :style="{ height: '80vh' }">
      <div class="sheet">
        <div class="sheet-header">
          <span class="sheet-title">发起会话</span>
          <van-icon name="cross" size="20" @click="searchShow = false" />
        </div>
        <div class="search-input">
          <van-search
            v-model="keyword"
            placeholder="搜索昵称或邮箱"
            @update:model-value="onSearch"
          />
        </div>
        <div class="search-results">
          <div v-if="searching" class="text-secondary sheet-tip">搜索中...</div>
          <div v-else-if="results.length === 0" class="text-secondary sheet-tip">
            {{ keyword ? '未找到相关用户' : '输入昵称或邮箱查找好友' }}
          </div>
          <div
            v-for="u in results"
            :key="u.id"
            class="result-item"
            @click="startChat(u)"
          >
            <van-image round width="40" height="40" :src="u.avatar || ''" fit="cover" lazy-load>
              <template #error>
                <div class="avatar-fallback small">{{ u.nickname?.slice(0, 1) }}</div>
              </template>
            </van-image>
            <div class="result-info">
              <div class="result-nickname">{{ u.nickname }}</div>
              <div class="text-secondary result-phone">{{ u.phone || u.email || '' }}</div>
            </div>
            <van-button size="small" type="primary" round>发消息</van-button>
          </div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.messages-page {
  padding-bottom: calc(56px + env(safe-area-inset-bottom));
}

.page-header {
  padding: 0 16px;
}

.header-action {
  margin-left: auto;
  color: var(--text-secondary);
}

.conn-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  padding: 6px 0;
}

.empty-box {
  padding-top: 40px;
}

.conv-list {
  padding: 0 12px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--border);
}

.conv-item:active {
  opacity: 0.7;
}

.conv-avatar {
  position: relative;
  flex-shrink: 0;
}

.avatar-fallback {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4da8ff, #1e90ff);
  color: #fff;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-fallback.small {
  width: 40px;
  height: 40px;
  font-size: 17px;
}

.online-dot {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #07c160;
  border: 2px solid var(--bg);
}

.swipe-delete {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 72px;
  height: 100%;
  background: #ee0a24;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.swipe-delete:active {
  background: #c5081e;
}

.conv-body {
  flex: 1;
  min-width: 0;
}

.conv-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.conv-nickname {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 12px;
  flex-shrink: 0;
}

.conv-preview {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 搜索弹层 */
.sheet {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}

.sheet-title {
  font-size: 16px;
  font-weight: 600;
}

.search-input {
  padding: 8px 12px 0;
}

.search-results {
  flex: 1;
  overflow-y: auto;
  padding: 4px 16px;
}

.sheet-tip {
  text-align: center;
  padding: 32px 0;
  font-size: 13px;
}

.result-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}

.result-info {
  flex: 1;
  min-width: 0;
}

.result-nickname {
  font-size: 15px;
  font-weight: 600;
}

.result-phone {
  font-size: 12px;
}
</style>
