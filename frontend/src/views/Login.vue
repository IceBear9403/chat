<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const mode = ref<'password' | 'code'>('password')
const phone = ref('')
const password = ref('')
const code = ref('')
const loading = ref(false)
const countdown = ref(0)
let timer: number | undefined

function startCountdown() {
  countdown.value = 60
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) {
      window.clearInterval(timer)
      timer = undefined
    }
  }, 1000)
}

async function sendCode() {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    showToast('请输入正确的手机号')
    return
  }
  try {
    await authApi.sendCode({ phone: phone.value, scene: 'login' })
    showToast('验证码已发送')
    startCountdown()
  } catch (e) {
    showToast((e as Error).message)
  }
}

async function onSubmit() {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    showToast('请输入正确的手机号')
    return
  }
  loading.value = true
  try {
    if (mode.value === 'password') {
      await userStore.login({ phone: phone.value, password: password.value })
    } else {
      await userStore.loginByCode({ phone: phone.value, code: code.value })
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    router.replace(redirect)
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<template>
  <div class="login-page">
    <div class="login-brand">
      <div class="brand-logo">社</div>
      <h1 class="brand-title">社交圈</h1>
      <p class="brand-slogan text-secondary">连接你我，分享生活</p>
    </div>

    <div class="login-card">
      <van-tabs v-model:active="mode" sticky>
        <van-tab title="密码登录" name="password" />
        <van-tab title="验证码登录" name="code" />
      </van-tabs>

      <van-cell-group inset>
        <van-field
          v-model="phone"
          type="tel"
          label="手机号"
          placeholder="请输入手机号"
          maxlength="11"
        />
        <van-field
          v-if="mode === 'password'"
          v-model="password"
          type="password"
          label="密码"
          placeholder="请输入密码"
        />
        <van-field
          v-else
          v-model="code"
          type="digit"
          label="验证码"
          placeholder="请输入验证码"
          maxlength="6"
        >
          <template #button>
            <van-button
              size="small"
              type="primary"
              plain
              :disabled="countdown > 0"
              @click="sendCode"
            >
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </van-button>
          </template>
        </van-field>
      </van-cell-group>

      <div class="login-actions">
        <van-button type="primary" block round :loading="loading" @click="onSubmit">
          登 录
        </van-button>
        <div class="login-links">
          <span class="text-secondary">还没有账号？</span>
          <router-link to="/register" class="link">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 24px;
  background: var(--bg);
}

.login-brand {
  text-align: center;
  margin-bottom: 36px;
}

.brand-logo {
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  border-radius: 20px;
  background: linear-gradient(135deg, #4da8ff, #1e90ff);
  color: #fff;
  font-size: 36px;
  font-weight: 700;
  line-height: 72px;
  box-shadow: 0 8px 24px rgba(30, 144, 255, 0.35);
}

.brand-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
}

.brand-slogan {
  margin: 8px 0 0;
  font-size: 14px;
}

.login-card {
  background: var(--card-bg);
  border-radius: 16px;
  padding: 8px 0 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
}

.login-actions {
  padding: 20px 16px 0;
}

.login-links {
  margin-top: 16px;
  text-align: center;
  font-size: 14px;
}

.link {
  color: var(--primary);
  text-decoration: none;
  font-weight: 600;
}
</style>
