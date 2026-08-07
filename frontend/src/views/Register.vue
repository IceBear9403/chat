<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const phone = ref('')
const code = ref('')
const password = ref('')
const nickname = ref('')
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
    await authApi.sendCode({ phone: phone.value, scene: 'register' })
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
  if (password.value.length < 6) {
    showToast('密码至少 6 位')
    return
  }
  if (code.value.length !== 6) {
    showToast('请输入 6 位验证码')
    return
  }
  loading.value = true
  try {
    await userStore.register({
      phone: phone.value,
      password: password.value,
      code: code.value,
      nickname: nickname.value || undefined,
    })
    showToast('注册成功')
    router.replace('/home')
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
  <div class="register-page">
    <div class="page-header">
      <van-icon name="arrow-left" size="20" @click="router.back()" />
      <span style="margin-left: 8px">注册</span>
    </div>

    <van-cell-group inset style="margin-top: 24px">
      <van-field
        v-model="phone"
        type="tel"
        label="手机号"
        placeholder="请输入手机号"
        maxlength="11"
      />
      <van-field
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
      <van-field
        v-model="password"
        type="password"
        label="密码"
        placeholder="设置密码（6-64 位）"
      />
      <van-field
        v-model="nickname"
        label="昵称"
        placeholder="选填，默认'用户+手机尾号'"
        maxlength="20"
      />
    </van-cell-group>

    <div class="register-actions">
      <van-button type="primary" block round :loading="loading" @click="onSubmit">
        注 册
      </van-button>
      <div class="register-links">
        <span class="text-secondary">已有账号？</span>
        <router-link to="/login" class="link">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  padding: 8px 0 24px;
  background: var(--bg);
}

.register-actions {
  padding: 20px 24px 0;
}

.register-links {
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
