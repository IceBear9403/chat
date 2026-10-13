import { createRouter, createWebHistory } from 'vue-router'
import { getAccessToken } from '@/utils/token'
import MainLayout from '@/layout/MainLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册' },
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页', keepAlive: true },
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/views/Messages.vue'),
        meta: { title: '消息' },
      },
      {
        path: 'publish',
        name: 'publish',
        component: () => import('@/views/Publish.vue'),
        meta: { title: '发布' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '我的' },
      },
      {
        path: 'profile/edit',
        name: 'profileEdit',
        component: () => import('@/views/EditProfile.vue'),
        meta: { title: '编辑资料' },
      },
    ],
  },
  {
    path: '/chat/:conversationId',
    name: 'chat',
    component: () => import('@/views/Chat.vue'),
    meta: { title: '聊天' },
  },
  {
    path: '/realname',
    name: 'realname',
    component: () => import('@/views/RealName.vue'),
    meta: { title: '实名认证' },
  },
  {
    path: '/privacy',
    name: 'privacy',
    component: () => import('@/views/Privacy.vue'),
    meta: { title: '隐私政策' },
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('@/views/Admin.vue'),
    meta: { title: '管理后台' },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/home',
  },
]

const router = createRouter({
  // 与 Vite base 保持一致（子路径部署时 BASE_URL 为 '/chat/'），
  // 否则刷新页面会 404、router.replace 也会跳到应用之外
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach((to) => {
  const token = getAccessToken()
  if (!token && to.path !== '/login' && to.path !== '/register') {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (token && (to.path === '/login' || to.path === '/register')) {
    return '/'
  }
  document.title = to.meta.title ? `${to.meta.title} · 社交圈` : '社交圈'
  return true
})

export default router
