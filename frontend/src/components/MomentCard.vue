<script setup lang="ts">
import { computed, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { momentApi, type RepostParams } from '@/api/moment'
import { useUserStore } from '@/store/user'
import type { MomentCommentVO, MomentVO } from '@/types'
import { formatTime, visibilityText } from '@/utils/format'

const props = defineProps<{ moment: MomentVO }>()
const emit = defineEmits<{
  (e: 'deleted', id: number): void
  (e: 'reposted'): void
}>()

const userStore = useUserStore()
const isOwner = computed(() => props.moment.author.userId === userStore.userInfo?.id)

// ---------- 点赞动效 ----------
const likeLoading = ref(false)
const poping = ref(false)
const bursting = ref(false)
let burstTimer: number | undefined

function triggerBurst() {
  poping.value = true
  bursting.value = true
  window.setTimeout(() => (poping.value = false), 450)
  if (burstTimer) window.clearTimeout(burstTimer)
  burstTimer = window.setTimeout(() => (bursting.value = false), 650)
}

const particles = computed(() =>
  Array.from({ length: 6 }, (_, i) => {
    const angle = (i * Math.PI) / 3
    const dist = 18 + (i % 2) * 8
    return {
      dx: `${Math.round(Math.cos(angle) * dist)}px`,
      dy: `${Math.round(Math.sin(angle) * dist)}px`,
    }
  }),
)

async function onLike() {
  if (likeLoading.value) return
  likeLoading.value = true
  try {
    const res = await momentApi.like(props.moment.id)
    props.moment.liked = res.liked
    props.moment.likeCount = res.likeCount
    if (res.liked) triggerBurst()
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    likeLoading.value = false
  }
}

// ---------- 评论 ----------
const commentOpen = ref(false)
const comments = ref<MomentCommentVO[]>([])
const commentLoading = ref(false)
const commentText = ref('')
const replyToId = ref<number | null>(null)
const replyToUserId = ref<number | null>(null)
const replyToName = ref('')

async function openComments() {
  commentOpen.value = true
  if (comments.value.length === 0) {
    await loadComments()
  }
}

async function loadComments() {
  commentLoading.value = true
  try {
    comments.value = await momentApi.comments(props.moment.id)
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    commentLoading.value = false
  }
}

function startReply(comment: MomentCommentVO) {
  replyToId.value = comment.parentId ?? comment.id
  replyToUserId.value = comment.author.userId
  replyToName.value = comment.author.nickname
}

async function submitComment() {
  const content = commentText.value.trim()
  if (!content) {
    showToast('请输入评论内容')
    return
  }
  try {
    const created = await momentApi.comment(props.moment.id, {
      content,
      parentId: replyToId.value ?? undefined,
      replyTo: replyToUserId.value ?? undefined,
    })
    comments.value.push(created)
    props.moment.commentCount += 1
    commentText.value = ''
    replyToId.value = null
    replyToUserId.value = null
    replyToName.value = ''
  } catch (e) {
    showToast((e as Error).message)
  }
}

// ---------- 转发 ----------
const repostOpen = ref(false)
const repostText = ref('')
const repostVisibility = ref(0)

async function submitRepost() {
  const payload: RepostParams = {
    content: repostText.value.trim() || undefined,
    visibility: repostVisibility.value,
  }
  try {
    await momentApi.repost(props.moment.id, payload)
    props.moment.repostCount += 1
    repostOpen.value = false
    repostText.value = ''
    showToast('已转发')
    emit('reposted')
  } catch (e) {
    showToast((e as Error).message)
  }
}

// ---------- 删除 / 图片预览 ----------
async function onDelete() {
  try {
    await showConfirmDialog({ title: '删除动态', message: '确定删除这条动态吗？' })
  } catch {
    return
  }
  try {
    await momentApi.remove(props.moment.id)
    showToast('已删除')
    emit('deleted', props.moment.id)
  } catch (e) {
    showToast((e as Error).message)
  }
}

const previewShow = ref(false)
const previewStart = ref(0)
// 预览列表 = 本人图片 + 转发来源图片（转发动态自身无 media）
const previewImages = computed(() => {
  const urls = props.moment.media.map((m) => m.url)
  if (props.moment.repostFrom) {
    urls.push(...props.moment.repostFrom.media.map((m) => m.url))
  }
  return urls
})

function onPreview(index: number) {
  previewStart.value = index
  previewShow.value = true
}

// 转发卡片内的图片：从本人图片之后的索引开始（offset = 本人 media 数）
function onPreviewRepost(index: number) {
  onPreview(props.moment.media.length + index)
}

// 图片网格布局
const gridClass = computed(() => {
  const n = props.moment.media.length
  return n === 1 ? 'media-grid grid-1' : `media-grid grid-${Math.min(n, 9)}`
})

function mediaStyle(media: { width?: number | null; height?: number | null }) {
  if (media.width && media.height) {
    return { aspectRatio: `${media.width} / ${media.height}` }
  }
  return {}
}
</script>

<template>
  <div class="moment-card">
    <!-- 头部：作者 -->
    <div class="moment-head">
      <van-image round width="40" height="40" :src="moment.author.avatar || ''" fit="cover">
        <template #error>
          <div class="avatar-fallback">{{ moment.author.nickname?.slice(0, 1) }}</div>
        </template>
      </van-image>
      <div class="moment-meta">
        <div class="moment-nickname">{{ moment.author.nickname }}</div>
        <div class="moment-time text-secondary">
          {{ formatTime(moment.createdAt) }}
          <van-icon :name="moment.visibility === 1 ? 'friends-o' : moment.visibility === 2 ? 'lock' : 'eye-o'" size="13" />
          <span>{{ visibilityText(moment.visibility) }}</span>
        </div>
      </div>
    </div>

    <!-- 正文 -->
    <p v-if="moment.content" class="moment-content">{{ moment.content }}</p>

    <!-- 转发来源卡片 -->
    <div v-if="moment.type === 2 && moment.repostFrom" class="repost-card" @click="onPreviewRepost(0)">
      <div class="repost-author">
        {{ moment.repostFrom.author.nickname }}：
      </div>
      <p v-if="moment.repostFrom.content" class="repost-content">{{ moment.repostFrom.content }}</p>
      <div v-if="moment.repostFrom.media.length" class="repost-media">
        <img
          v-for="m in moment.repostFrom.media.slice(0, 3)"
          :key="m.id"
          :src="m.url"
          class="repost-thumb"
          loading="lazy"
        />
      </div>
    </div>

    <!-- 图片九宫格 -->
    <div v-if="moment.media.length" :class="gridClass">
      <van-image
        v-for="(m, i) in moment.media"
        :key="m.id"
        :src="m.url"
        :style="mediaStyle(m)"
        :class="moment.media.length === 1 ? '' : 'grid-cell'"
        fit="cover"
        loading="lazy"
        @click="onPreview(i)"
      />
    </div>

    <!-- 操作栏 -->
    <div class="moment-actions">
      <div class="action like-action" :class="{ liked: moment.liked }" @click="onLike">
        <span class="like-icon-wrap">
          <van-icon
            :name="moment.liked ? 'like' : 'like-o'"
            size="20"
            :class="{ liked: moment.liked, pop: poping }"
          />
          <span v-if="bursting" class="particles">
            <i v-for="(p, i) in particles" :key="i" class="particle" :style="{ '--dx': p.dx, '--dy': p.dy }" />
          </span>
        </span>
        <span>{{ moment.likeCount > 0 ? moment.likeCount : '赞' }}</span>
      </div>

      <div class="action" @click="openComments">
        <van-icon name="comment-o" size="19" />
        <span>{{ moment.commentCount > 0 ? moment.commentCount : '评论' }}</span>
      </div>

      <div class="action" @click="repostOpen = true">
        <van-icon name="share-o" size="19" />
        <span>{{ moment.repostCount > 0 ? moment.repostCount : '转发' }}</span>
      </div>

      <div v-if="isOwner" class="action danger" @click="onDelete">
        <van-icon name="delete-o" size="19" />
        <span>删除</span>
      </div>
    </div>

    <!-- 图片预览（右上角关闭按钮） -->
    <van-image-preview
      v-model:show="previewShow"
      :images="previewImages"
      :start-position="previewStart"
      closeable
      close-icon-position="top-right"
    />

    <!-- 评论弹层 -->
    <van-popup v-model:show="commentOpen" position="bottom" round :style="{ height: '72vh' }">
      <div class="sheet">
        <div class="sheet-header">
          <span class="sheet-title">评论</span>
          <van-icon name="cross" size="20" @click="commentOpen = false" />
        </div>
        <div class="comment-list">
          <div v-if="commentLoading" class="text-secondary sheet-tip">加载中...</div>
          <div v-else-if="comments.length === 0" class="text-secondary sheet-tip">还没有评论，来抢沙发～</div>
          <div v-else v-for="c in comments" :key="c.id" class="comment-item" @click="startReply(c)">
            <van-image round width="30" height="30" :src="c.author.avatar || ''" fit="cover">
              <template #error>
                <div class="avatar-fallback small">{{ c.author.nickname?.slice(0, 1) }}</div>
              </template>
            </van-image>
            <div class="comment-body">
              <div class="comment-author">{{ c.author.nickname }}</div>
              <div class="comment-text">
                <span v-if="c.replyToName" class="text-secondary">回复 {{ c.replyToName }}：</span>
                {{ c.content }}
              </div>
              <div class="text-secondary comment-time">{{ formatTime(c.createdAt) }}</div>
            </div>
          </div>
        </div>
        <div class="sheet-input">
          <van-field
            v-model="commentText"
            :placeholder="replyToName ? `回复 ${replyToName}` : '友善评论，理性发言'"
            rows="1"
            autosize
          >
            <template #button>
              <van-button size="small" type="primary" @click="submitComment">发送</van-button>
            </template>
          </van-field>
        </div>
      </div>
    </van-popup>

    <!-- 转发弹层 -->
    <van-popup v-model:show="repostOpen" position="bottom" round>
      <div class="sheet">
        <div class="sheet-header">
          <span class="sheet-title">转发动态</span>
          <van-icon name="cross" size="20" @click="repostOpen = false" />
        </div>
        <div class="repost-form">
          <van-field v-model="repostText" type="textarea" rows="3" maxlength="200" show-word-limit placeholder="说说你的想法..." />
          <div class="visibility-row">
            <span class="text-secondary">可见范围</span>
            <van-radio-group v-model="repostVisibility" direction="horizontal">
              <van-radio :name="0">公开</van-radio>
              <van-radio :name="1">仅好友</van-radio>
              <van-radio :name="2">私密</van-radio>
            </van-radio-group>
          </div>
          <van-button type="primary" block round @click="submitRepost">转 发</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.moment-card {
  background: var(--card-bg);
  border-radius: 12px;
  padding: 14px 14px 6px;
  margin: 12px 0;
}

/* 头部 */
.moment-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar-fallback {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4da8ff, #1e90ff);
  color: #fff;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-fallback.small {
  width: 30px;
  height: 30px;
  font-size: 14px;
}

.moment-meta {
  min-width: 0;
}

.moment-nickname {
  font-size: 15px;
  font-weight: 600;
}

.moment-time {
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 正文 */
.moment-content {
  margin: 10px 0 0;
  font-size: 15px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 转发卡片 */
.repost-card {
  margin-top: 10px;
  padding: 10px 12px;
  background: var(--bg);
  border-radius: 8px;
}

.repost-author {
  font-size: 13px;
  font-weight: 600;
  color: var(--primary);
}

.repost-content {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text);
  white-space: pre-wrap;
}

.repost-media {
  display: flex;
  gap: 4px;
  margin-top: 8px;
}

.repost-thumb {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 6px;
}

/* 图片网格 */
.media-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  margin-top: 10px;
  border-radius: 8px;
  overflow: hidden;
}

.grid-cell {
  width: 100%;
  aspect-ratio: 1;
}

.grid-1 {
  display: block;
  width: 220px;
  max-width: 100%;
}

/* 操作栏 */
.moment-actions {
  display: flex;
  align-items: center;
  padding: 10px 0 6px;
  gap: 0;
}

.action {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 13px;
  color: var(--text-secondary);
  padding: 6px 0;
  border-radius: 8px;
  cursor: pointer;
}

.action:active {
  background: var(--bg);
}

.action.liked {
  color: #ff4d6a;
}

.action.danger {
  color: #ff4d6a;
}

/* 点赞动效 */
.like-icon-wrap {
  position: relative;
  display: inline-flex;
}

.like-icon-wrap .van-icon.liked {
  color: #ff4d6a;
}

.like-icon-wrap .van-icon.pop {
  animation: like-pop 0.4s ease;
}

@keyframes like-pop {
  0% { transform: scale(1); }
  40% { transform: scale(1.4); }
  70% { transform: scale(0.9); }
  100% { transform: scale(1); }
}

.particles {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 0;
  height: 0;
  pointer-events: none;
}

.particle {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: radial-gradient(circle, #ff9a9e, #ff4d6a);
  opacity: 0;
  animation: particle-fly 0.6s ease-out forwards;
}

@keyframes particle-fly {
  from {
    opacity: 1;
    transform: translate(0, 0) scale(1);
  }
  to {
    opacity: 0;
    transform: translate(var(--dx), var(--dy)) scale(0.3);
  }
}

/* 弹层 */
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

.sheet-tip {
  text-align: center;
  padding: 32px 0;
  font-size: 13px;
}

.comment-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 16px;
}

.comment-item {
  display: flex;
  gap: 8px;
  padding: 10px 0;
  cursor: pointer;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-author {
  font-size: 13px;
  font-weight: 600;
}

.comment-text {
  font-size: 14px;
  margin-top: 2px;
  line-height: 1.5;
  word-break: break-word;
}

.comment-time {
  font-size: 11px;
  margin-top: 4px;
}

.sheet-input {
  padding: 10px 12px;
  border-top: 1px solid var(--border);
}

.repost-form {
  padding: 16px;
}

.visibility-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 14px 0;
}
</style>
