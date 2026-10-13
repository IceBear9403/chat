<script setup lang="ts">
import { onActivated, ref } from 'vue'
import { showToast } from 'vant'
import { momentApi } from '@/api/moment'
import MomentCard from '@/components/MomentCard.vue'
import type { MomentVO } from '@/types'

defineOptions({ name: 'HomeView' })

type FeedType = 'following' | 'recommend'

const activeTab = ref<FeedType>('following')
const list = ref<MomentVO[]>([])
const loading = ref(false)
const finished = ref(false)
const cursorTime = ref<string | null>(null)
const cursorId = ref<number | null>(null)

async function onLoad() {
  try {
    const page = await momentApi.feed(activeTab.value, cursorTime.value, cursorId.value, 10)
    list.value.push(...page.records)
    cursorTime.value = page.nextCursorTime
    cursorId.value = page.nextCursorId
    finished.value = !page.hasMore
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  list.value = []
  cursorTime.value = null
  cursorId.value = null
  finished.value = false
  loading.value = true
  onLoad()
}

async function onRefresh() {
  resetAndLoad()
}

function onTabChange() {
  resetAndLoad()
}

function onDeleted(id: number) {
  list.value = list.value.filter((m) => m.id !== id)
}

function onReposted() {
  resetAndLoad()
}

// 从发布页返回时刷新信息流（keep-alive 场景）
onActivated(() => {
  if (list.value.length > 0) {
    resetAndLoad()
  }
})
</script>

<template>
  <div class="page-container home-page">
    <div class="page-header">
      <span>朋友圈</span>
      <router-link to="/publish" class="header-action">
        <van-icon name="photograph" size="22" />
      </router-link>
    </div>

    <van-tabs
      v-model:active="activeTab"
      sticky
      class="feed-tabs"
      color="#1e90ff"
      title-active-color="#1e90ff"
      @change="onTabChange"
    >
      <van-tab title="关注" name="following" />
      <van-tab title="推荐" name="recommend" />
    </van-tabs>

    <van-pull-refresh v-model="loading" @refresh="onRefresh">
      <div v-if="list.length === 0 && !loading" class="feed-empty">
        <van-empty description="还没有动态">
          <template #description>
            <p class="text-secondary">点击右上角图标发布第一条朋友圈</p>
          </template>
        </van-empty>
      </div>

      <MomentCard
        v-for="m in list"
        :key="m.id"
        :moment="m"
        @deleted="onDeleted"
        @reposted="onReposted"
      />

      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      />
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.home-page {
  padding: 0 0 calc(56px + env(safe-area-inset-bottom));
}

.page-header {
  padding: 0 16px;
}

.header-action {
  margin-left: auto;
  color: var(--text-secondary);
}

.feed-tabs {
  position: sticky;
  top: 0;
  z-index: 10;
}

.feed-list {
  padding: 0 12px;
}

.feed-empty {
  padding-top: 24px;
}
</style>
