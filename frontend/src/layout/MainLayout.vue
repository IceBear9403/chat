<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { computed } from 'vue'
import { useChatStore } from '@/store/chat'
import { useCallStore } from '@/store/call'

const route = useRoute()
const chatStore = useChatStore()
const callStore = useCallStore()

/** 消息 Tab 未读数（0 不显示） */
const unreadBadge = computed(() =>
  chatStore.unreadTotal > 0 ? (chatStore.unreadTotal > 99 ? '99+' : chatStore.unreadTotal) : undefined,
)

onMounted(() => {
  chatStore.init()
  callStore.init()
})
</script>

<template>
  <div class="main-layout">
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <keep-alive include="HomeView">
          <component :is="Component" :key="route.fullPath" />
        </keep-alive>
      </transition>
    </router-view>

    <van-tabbar :fixed="true" :placeholder="true" route>
      <van-tabbar-item replace to="/home" icon="wap-home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/messages" icon="chat-o" :badge="unreadBadge">
        消息
      </van-tabbar-item>
      <van-tabbar-item replace to="/publish" icon="plus">
        发布
      </van-tabbar-item>
      <van-tabbar-item replace to="/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style scoped>
.main-layout {
  min-height: 100vh;
  background: var(--bg);
}
</style>
