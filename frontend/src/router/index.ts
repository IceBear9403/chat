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
    ],
  },
  {
    path: '/chat/:conversationId',
    name: 'chat',
    component: () => import('@/views/Chat.vue'),
    meta: { title: '聊天' },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/home',
  },
]

const router = createRouter({
  history: createWebHistory(),
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
