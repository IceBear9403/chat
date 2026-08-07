import { defineStore } from 'pinia'
import { authApi, type CodeLoginParams, type LoginParams, type RegisterParams } from '@/api/auth'
import { userApi } from '@/api/user'
import { useChatStore } from '@/store/chat'
import { useCallStore } from '@/store/call'
import type { LoginVO } from '@/types'
import { clearAuth, getAccessToken, getRefreshToken, getUserInfo, setTokens, setUserInfo } from '@/utils/token'

/**
 * 登录状态。令牌持久化由 utils/token.ts 负责（http 拦截器与其共享，避免循环依赖）。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: getAccessToken(),
    refreshToken: getRefreshToken(),
    userInfo: getUserInfo(),
  }),

  getters: {
    isLoggedIn: (state) => !!state.accessToken,
    nickname: (state) => state.userInfo?.nickname ?? '',
    avatar: (state) => state.userInfo?.avatar ?? '',
  },

  actions: {
    applyLogin(login: LoginVO) {
      this.setTokens(login.token.accessToken, login.token.refreshToken)
      this.userInfo = login.user
      setUserInfo(login.user)
    },

    setTokens(accessToken: string, refreshToken: string) {
      this.accessToken = accessToken
      this.refreshToken = refreshToken
      setTokens(accessToken, refreshToken)
    },

    async login(payload: LoginParams) {
      const login = await authApi.login(payload)
      this.applyLogin(login)
    },

    async loginByCode(payload: CodeLoginParams) {
      const login = await authApi.loginByCode(payload)
      this.applyLogin(login)
    },

    async register(payload: RegisterParams) {
      const login = await authApi.register(payload)
      this.applyLogin(login)
    },

    /** 刷新/静默刷新成功后同步 store 中的令牌 */
    syncTokensFromStorage() {
      this.accessToken = getAccessToken()
      this.refreshToken = getRefreshToken()
      this.userInfo = getUserInfo()
    },

    /** 拉取最新用户信息（如更新资料后） */
    async fetchMe() {
      const user = await userApi.me()
      this.userInfo = user
      setUserInfo(user)
    },

    async logout() {
      if (this.refreshToken) {
        try {
          await authApi.logout(this.refreshToken)
        } catch {
          // 登出接口失败不阻塞本地登出
        }
      }
      clearAuth()
      this.accessToken = ''
      this.refreshToken = ''
      this.userInfo = null
      // 断开 STOMP、清空会话未读、清理进行中的通话
      useChatStore().logout()
      useCallStore().logout()
    },
  },
})
