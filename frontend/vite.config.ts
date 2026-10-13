import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * 部署在子路径 /chat 下（服务器上与其他项目共用同一域名的不同路径）。
 * base 会同时决定：
 *   - 构建产物里 index.html 引用的资源路径（/chat/assets/xxx.js、/chat/favicon.svg）
 *   - 运行时 import.meta.env.BASE_URL 的取值（'/chat/'，带尾斜杠）
 * 路由、axios baseURL、WebSocket 地址都由 BASE_URL 派生，改这里即可全站生效。
 */
const BASE = '/chat/'

// 开发服务器同样挂在 /chat 下（访问 http://localhost:5173/chat/），
// 代理把前缀剥掉再转发，保证后端收到的仍是 /api、/ws、/uploads
const proxyTarget = 'http://localhost:18081'
const stripBase = (path: string) => path.replace(/^\/chat/, '')

// https://vite.dev/config/
export default defineConfig({
  base: BASE,
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
      '/chat/api': {
        target: proxyTarget,
        changeOrigin: true,
        rewrite: stripBase,
      },
      // WebSocket/STOMP 信令（原生 WS 升级 + SockJS 轮询兜底）
      '/chat/ws': {
        target: proxyTarget,
        changeOrigin: true,
        ws: true,
        rewrite: stripBase,
      },
      // 上传的本地静态文件（后端 /uploads/** 映射）
      '/chat/uploads': {
        target: proxyTarget,
        changeOrigin: true,
        rewrite: stripBase,
      },
    },
  },
})
