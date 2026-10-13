<script setup lang="ts">
import { computed } from 'vue'

/** 备案号页脚。构建时通过 .env.production 的 VITE_ICP 注入，如 VITE_ICP=京ICP备2026000000号-1 */
const icp = computed(() => (import.meta.env.VITE_ICP as string) || '')
</script>

<template>
  <div v-if="icp" class="icp-footer">
    <a
      class="icp-link"
      href="https://beian.miit.gov.cn/"
      target="_blank"
      rel="noopener noreferrer"
    >{{ icp }}</a>
  </div>
</template>

<style scoped>
.icp-footer {
  text-align: center;
  padding: 16px 0 calc(8px + env(safe-area-inset-bottom));
  font-size: 11px;
  color: var(--text-secondary);
}

.icp-link {
  color: inherit;
  text-decoration: none;
}
</style>
