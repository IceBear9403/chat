<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showDialog, showToast } from 'vant'
import { userApi } from '@/api/user'
import { useThemeStore } from '@/store/theme'
import { useUserStore } from '@/store/user'

const router = useRouter()
const themeStore = useThemeStore()
const userStore = useUserStore()

// 注销密码确认弹窗
const deleteDialog = ref(false)
const deletePassword = ref('')
const deleteLoading = ref(false)

const realnameText = computed(() =>
  userStore.realnameStatus === 1 ? '已实名' : '未实名',
)

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

async function onDeleteAccount() {
  if (!deletePassword.value) {
    showToast('请输入登录密码')
    return
  }
  deleteLoading.value = true
  try {
    await userApi.deleteAccount(deletePassword.value)
    deleteDialog.value = false
    await showDialog({
      title: '账号已注销',
      message: '你的账号已注销，感谢使用。',
    })
    await userStore.logout()
    router.replace('/login')
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    deleteLoading.value = false
  }
}

async function onDeleteConfirm() {
  try {
    await showConfirmDialog({
      title: '注销账号',
      message: '注销后账号不可登录，实名等敏感信息将被抹除，确定继续？',
    })
  } catch {
    return
  }
  deletePassword.value = ''
  deleteDialog.value = true
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
        <div class="text-secondary profile-phone">
          {{ userStore.userInfo?.phone || userStore.userInfo?.email || '' }}
        </div>
        <div class="text-secondary profile-sig">
          {{ userStore.userInfo?.signature || '这个人很懒，什么都没写' }}
        </div>
      </div>
    </div>

    <van-cell-group inset title="设置">
      <van-cell title="编辑资料" is-link to="/profile/edit" />
      <van-cell title="深色模式" center>
        <template #right-icon>
          <!-- 注意：不能 v-model + toggle 双绑（会双重翻转）。用单向 model-value + setDark 写入 -->
          <van-switch :model-value="themeStore.isDark" size="22" @change="themeStore.setDark" />
        </template>
      </van-cell>
      <van-cell title="版本" value="v0.7.0（阶段七）" />
    </van-cell-group>

    <van-cell-group inset title="合规与账号">
      <van-cell
        title="实名认证"
        :value="realnameText"
        :is-link="userStore.realnameStatus !== 1"
        :to="userStore.realnameStatus === 1 ? '' : '/realname'"
      />
      <van-cell title="隐私政策" is-link to="/privacy" />
      <van-cell v-if="userStore.isAdmin" title="管理后台" is-link to="/admin" />
      <van-cell title="注销账号" is-link @click="onDeleteConfirm" />
    </van-cell-group>

    <div class="logout-wrap">
      <van-button block round plain type="danger" @click="onLogout">
        退出登录
      </van-button>
    </div>

    <!-- 注销确认：二次输入密码 -->
    <van-dialog
      v-model:show="deleteDialog"
      title="注销账号"
      show-cancel-button
      confirm-button-text="确认注销"
      confirm-button-color="#ee0a24"
      @confirm="onDeleteAccount"
    >
      <div class="delete-dialog-body">
        <p class="text-secondary delete-tip">注销后不可恢复，请输入登录密码确认。</p>
        <van-field
          v-model="deletePassword"
          type="password"
          label="登录密码"
          placeholder="请输入密码"
          :maxlength="64"
        />
      </div>
    </van-dialog>
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

.delete-dialog-body {
  padding: 4px 20px 24px;
}

.delete-tip {
  margin: 0 0 12px;
  font-size: 13px;
}
</style>
