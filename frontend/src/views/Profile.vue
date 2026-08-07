<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useThemeStore } from '@/store/theme'
import { useUserStore } from '@/store/user'

const router = useRouter()
const themeStore = useThemeStore()
const userStore = useUserStore()

onMounted(() => {
  // 静默拉取最新资料（token 失效由拦截器统一处理）
  userStore.fetchMe().catch(() => {
    // 网络异常时使用本地缓存展示即可
  })
})

function avatarText(): string {
  const nickname = userStore.userInfo?.nickname
  return nickname ? nickname.slice(0, 1) : '我'
}

async function onLogout() {
  await userStore.logout()
  showToast('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <span>我的</span>
    </div>

    <div class="card profile-card">
      <van-image
        round
        width="64"
        height="64"
        :src="userStore.avatar || ''"
        fit="cover"
      >
        <template #error>
          <div class="avatar-fallback">{{ avatarText() }}</div>
        </template>
      </van-image>
      <div class="profile-info">
        <div class="profile-name">{{ userStore.nickname || '未设置昵称' }}</div>
        <div class="text-secondary profile-phone">{{ userStore.userInfo?.phone ?? '' }}</div>
        <div class="text-secondary profile-sig">
          {{ userStore.userInfo?.signature || '这个人很懒，什么都没写' }}
        </div>
      </div>
    </div>

    <van-cell-group inset title="设置">
      <van-cell title="深色模式" center>
        <template #right-icon>
          <van-switch v-model="themeStore.isDark" size="22" @change="themeStore.toggle" />
        </template>
      </van-cell>
      <van-cell title="版本" value="v0.5.0（阶段五）" />
    </van-cell-group>

    <div class="logout-wrap">
      <van-button block round plain type="danger" @click="onLogout">
        退出登录
      </van-button>
    </div>
  </div>
</template>

<style scoped>
.profile-card {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar-fallback {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4da8ff, #1e90ff);
  color: #fff;
  font-size: 26px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-name {
  font-size: 18px;
  font-weight: 700;
}

.profile-phone {
  margin-top: 4px;
  font-size: 13px;
}

.profile-sig {
  margin-top: 6px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-wrap {
  margin-top: 32px;
  padding: 0 16px;
}
</style>
