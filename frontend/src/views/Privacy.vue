<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <van-icon name="arrow-left" size="20" class="back-icon" @click="router.back()" />
      <span>隐私政策</span>
    </div>

    <div class="card policy">
      <h3>隐私政策</h3>
      <p class="updated">更新日期：2026-08-08</p>

      <h4>一、我们收集的信息</h4>
      <p>
        手机号（登录凭证）、昵称/头像等资料、动态/评论内容、通话与聊天记录。
        你主动提交的实名信息（姓名、身份证号）仅用于合规实名登记。
      </p>

      <h4>二、信息的存储与保护</h4>
      <p>
        手机号、真实姓名、身份证号等个人敏感信息使用 AES-256 加密存储（密文带 ENC: 前缀），
        生产环境密钥经环境变量注入，不落明文。密钥变更会导致历史密文不可解密，请妥善保管。
      </p>

      <h4>三、信息的使用</h4>
      <p>
        内容仅用于向你提供服务、保障社区安全（敏感词审核、举报处理）与履行法定合规义务，
        不会向第三方出售或提供你的个人信息。
      </p>

      <h4>四、信息的留存与删除</h4>
      <p>
        操作日志保留 180 天后由系统自动清理。你可以随时在「我的-注销账号」中注销账号，
        注销后敏感信息将被抹除，账号不可再登录。
      </p>

      <h4>五、未成年人保护</h4>
      <p>我们不会主动收集未满 14 周岁未成年人的个人信息；若你未满 14 周岁，请在监护人陪同下使用。</p>

      <h4>六、联系我们</h4>
      <p>如对本政策有疑问，可通过站内反馈或注册邮箱联系我们。</p>
    </div>
  </div>
</template>

<style scoped>
.back-icon {
  margin-right: 8px;
  cursor: pointer;
}

.policy {
  padding: 18px 18px 24px;
}

.policy h3 {
  margin: 0 0 4px;
  font-size: 17px;
}

.updated {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--text-secondary);
}

.policy h4 {
  margin: 16px 0 6px;
  font-size: 14px;
}

.policy p {
  margin: 0 0 4px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text);
}
</style>
