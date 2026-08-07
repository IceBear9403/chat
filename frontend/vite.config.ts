import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: true,
    port: 5173,
    proxy: {
      // 后端 REST 接口
      '/api': {
        target: 'http://localhost:18081',
        changeOrigin: true,
      },
      // WebSocket/STOMP 信令（原生 WS 升级 + SockJS 轮询兜底）
      '/ws': {
        target: 'http://localhost:18081',
        changeOrigin: true,
        ws: true,
      },
      // 上传的本地静态文件（后端 /uploads/** 映射，相对路径按前端域名解析）
      '/uploads': {
        target: 'http://localhost:18081',
        changeOrigin: true,
      },
    },
  },
})
