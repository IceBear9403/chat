<script setup lang="ts">
import { computed } from 'vue'
import { useThemeStore } from '@/store/theme'
import { useCallStore } from '@/store/call'
import CallPanel from '@/components/CallPanel.vue'
import type { ConfigProviderThemeVars } from 'vant'

const themeStore = useThemeStore()
const callStore = useCallStore()
themeStore.init()
// 注册通话信令订阅（幂等），并确保登出后重新登录能再次连接
callStore.init()

const themeVars = computed<ConfigProviderThemeVars>(() => ({
  primaryColor: '#1e90ff',
  // 让卡片/背景跟随我们的主题变量
  background: themeStore.isDark ? '#0e1116' : '#f5f6f7',
  cardBackground: themeStore.isDark ? '#171b22' : '#ffffff',
  textColor: themeStore.isDark ? '#e8eaed' : '#1a1a1a',
}))
</script>

<template>
  <van-config-provider
    :theme="themeStore.isDark ? 'dark' : 'light'"
    :theme-vars="themeVars"
  >
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
    <!-- 通话全屏面板（独立于路由之上，任何页面来电都能弹出） -->
    <CallPanel />
  </van-config-provider>
</template>
