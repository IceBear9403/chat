<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, type UploaderAfterRead, type UploaderFileListItem } from 'vant'
import { uploadFile } from '@/api/file'
import { userApi } from '@/api/user'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const nickname = ref(userStore.userInfo?.nickname ?? '')
const signature = ref(userStore.userInfo?.signature ?? '')
const avatar = ref(userStore.userInfo?.avatar ?? '')
const avatarList = ref<UploaderFileListItem[]>([])
const saving = ref(false)

// 已设置过头像时预填预览，便于查看当前头像
if (avatar.value) {
  avatarList.value = [{ url: avatar.value, isImage: true }]
}

// 删除头像预览时同步清空头像
watch(avatarList, (list) => {
  if (list.length === 0) {
    avatar.value = ''
  }
})

const afterReadAvatar: UploaderAfterRead = async (item) => {
  const it = Array.isArray(item) ? item[0] : item
  if (!it.file) return
  try {
    const res = await uploadFile(it.file, 'avatar')
    it.url = res.url
    avatar.value = res.url
  } catch (e) {
    showToast((e as Error).message)
  }
}

async function onSave() {
  const name = nickname.value.trim()
  if (!name) {
    showToast('请输入昵称')
    return
  }
  saving.value = true
  try {
    await userApi.updateProfile({
      nickname: name,
      avatar: avatar.value || undefined,
      signature: signature.value.trim() || undefined,
    })
    await userStore.fetchMe()
    showToast('已保存')
    router.back()
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <van-icon name="arrow-left" size="20" class="back-icon" @click="router.back()" />
      <span>编辑资料</span>
    </div>

    <van-cell-group inset title="头像">
      <van-cell title="更换头像" center>
        <template #right-icon>
          <van-uploader
            v-model="avatarList"
            :max-count="1"
            :after-read="afterReadAvatar"
            :max-size="20 * 1024 * 1024"
            @oversize="showToast('图片不能超过 20MB')"
          />
        </template>
      </van-cell>
    </van-cell-group>

    <van-cell-group inset title="基本资料">
      <van-field
        v-model="nickname"
        label="昵称"
        :maxlength="20"
        clearable
        placeholder="请输入昵称"
      />
      <van-field
        v-model="signature"
        label="个性签名"
        type="textarea"
        rows="2"
        autosize
        :maxlength="100"
        show-word-limit
        placeholder="介绍一下自己吧"
      />
    </van-cell-group>

    <p class="text-secondary hint">
      昵称修改受间隔限制（按后台配置），不得包含敏感词；不满足时会提示具体原因。
    </p>

    <div class="save-wrap">
      <van-button type="primary" block round :loading="saving" @click="onSave">保 存</van-button>
    </div>
  </div>
</template>

<style scoped>
.back-icon {
  margin-right: 8px;
  cursor: pointer;
}

.hint {
  margin: 12px 16px;
  font-size: 12px;
}

.save-wrap {
  padding: 8px 16px 0;
}
</style>
