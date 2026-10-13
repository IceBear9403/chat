<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { userApi } from '@/api/user'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const realName = ref('')
const idCard = ref('')
const loading = ref(false)
const agreed = ref(false)

const alreadyReal = computed(() => userStore.realnameStatus === 1)

async function onSubmit() {
  if (!agreed.value) {
    showToast('请先阅读并同意《隐私政策》')
    return
  }
  if (!/^[一-龥·]{2,20}$/.test(realName.value.trim())) {
    showToast('请输入 2-20 个汉字的真实姓名')
    return
  }
  if (!/^\d{17}[\dXx]$/.test(idCard.value.trim())) {
    showToast('请输入 18 位身份证号')
    return
  }
  loading.value = true
  try {
    const user = await userApi.bindRealName({ realName: realName.value.trim(), idCard: idCard.value.trim() })
    userStore.userInfo = user
    showToast('实名认证成功')
    router.replace('/profile')
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <van-icon name="arrow-left" size="20" class="back-icon" @click="router.back()" />
      <span>实名认证</span>
    </div>

    <div class="card intro">
      <p class="intro-title">为什么要实名认证？</p>
      <p class="text-secondary intro-text">
        为落实《网络安全法》实名制要求，社交内容平台需对用户进行实名登记。
        你的姓名与身份证号将采用 AES-256 加密存储，仅用于合规登记，不会对外展示。
      </p>
    </div>

    <div v-if="alreadyReal" class="card done">
      <van-icon name="passed" color="#07c160" size="48" />
      <p class="done-title">已完成实名认证</p>
      <p class="text-secondary">感谢你的配合，账号已通过实名登记。</p>
    </div>

    <template v-else>
      <van-cell-group inset title="填写信息">
        <van-field v-model="realName" label="真实姓名" placeholder="与身份证一致" maxlength="20" clearable />
        <van-field v-model="idCard" label="身份证号" placeholder="18 位身份证号码" maxlength="18" clearable />
      </van-cell-group>

      <div class="agree-row">
        <van-checkbox v-model="agreed" icon-size="16" shape="square">
          我已阅读并同意
        </van-checkbox>
        <span class="link" @click="router.push('/privacy')">《隐私政策》</span>
      </div>

      <div class="submit-wrap">
        <van-button type="primary" block round :loading="loading" :disabled="!agreed" @click="onSubmit">
          提交实名认证
        </van-button>
      </div>
      <p class="text-secondary tip">当前为接入阿里云实人认证前的过渡方案，信息将加密留存。</p>
    </template>
  </div>
</template>

<style scoped>
.back-icon {
  margin-right: 8px;
  cursor: pointer;
}

.intro {
  padding: 14px 16px;
}

.intro-title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
}

.intro-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}

.done {
  text-align: center;
  padding: 28px 16px;
}

.done-title {
  margin: 10px 0 4px;
  font-size: 16px;
  font-weight: 600;
}

.agree-row {
  display: flex;
  align-items: center;
  gap: 2px;
  margin: 16px 4px 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.link {
  color: var(--primary);
  cursor: pointer;
}

.submit-wrap {
  margin-top: 20px;
  padding: 0 16px;
}

.tip {
  margin: 12px 16px 0;
  font-size: 12px;
}
</style>
