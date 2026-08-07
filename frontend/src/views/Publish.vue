<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, type UploaderAfterRead, type UploaderBeforeRead, type UploaderFileListItem } from 'vant'
import { uploadFile } from '@/api/file'
import { momentApi } from '@/api/moment'
import type { MomentMediaVO } from '@/types'

const router = useRouter()

const content = ref('')
const visibility = ref(0)
const publishing = ref(false)

const fileList = ref<UploaderFileListItem[]>([])

const beforeRead: UploaderBeforeRead = (file) => {
  const files = Array.isArray(file) ? file : [file]
  for (const f of files) {
    if (!f.type.startsWith('image/')) {
      showToast('只能选择图片文件')
      return false
    }
    if (f.size > 20 * 1024 * 1024) {
      showToast('图片不能超过 20MB')
      return false
    }
  }
  return true
}

const afterRead: UploaderAfterRead = async (item) => {
  const items = Array.isArray(item) ? item : [item]
  for (const it of items) {
    if (!it.file) continue
    try {
      const res = await uploadFile(it.file, 'moment')
      it.url = res.url
    } catch (e) {
      showToast((e as Error).message)
    }
  }
}

async function onSubmit() {
  const media: MomentMediaVO[] = fileList.value
    .filter((f) => f.url)
    .map((f, i) => ({
      id: 0,
      mediaType: 1,
      url: f.url as string,
      thumbnailUrl: null,
      width: null,
      height: null,
      sort: i,
    }))

  if (!content.value.trim() && media.length === 0) {
    showToast('写点什么或选张图片吧')
    return
  }

  publishing.value = true
  try {
    await momentApi.publish({
      content: content.value.trim() || undefined,
      type: media.length > 0 ? 0 : 1,
      visibility: visibility.value,
      media,
    })
    showToast('发布成功')
    router.replace('/home')
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <div class="page-container publish-page">
    <div class="page-header">
      <span>发布</span>
    </div>

    <div class="publish-box">
      <textarea
        v-model="content"
        class="content-input"
        placeholder="这一刻的想法...（支持换行）"
        maxlength="2000"
        rows="6"
      />

      <van-uploader
        v-model="fileList"
        :max-count="9"
        :before-read="beforeRead"
        :after-read="afterRead"
        multiple
        class="uploader"
      />

      <div class="visibility-row">
        <span class="text-secondary">可见范围</span>
        <van-radio-group v-model="visibility" direction="horizontal">
          <van-radio :name="0">公开</van-radio>
          <van-radio :name="1">仅好友</van-radio>
          <van-radio :name="2">私密</van-radio>
        </van-radio-group>
      </div>

      <div class="publish-btn">
        <van-button type="primary" block round :loading="publishing" @click="onSubmit">
          发 布
        </van-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.publish-box {
  padding: 8px 0;
}

.content-input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text);
  font-size: 16px;
  line-height: 1.6;
  resize: none;
  padding: 8px 4px;
}

.content-input::placeholder {
  color: var(--text-secondary);
}

.uploader {
  margin-top: 12px;
}

.visibility-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20px;
  padding: 0 4px;
}

.publish-btn {
  margin-top: 32px;
}
</style>
