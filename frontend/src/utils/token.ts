import type { UserVO } from '@/types'

/**
 * 令牌 / 用户信息本地存储。
 *
 * <p>独立于 Pinia：http 拦截器（401 刷新）与 store 都从这里读写，
 * 避免 http.ts 与 store/user.ts 产生循环依赖。
 */
const ACCESS_KEY = 'social_access_token'
const REFRESH_KEY = 'social_refresh_token'
const USER_KEY = 'social_user_info'

export function getAccessToken(): string {
  return localStorage.getItem(ACCESS_KEY) ?? ''
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_KEY) ?? ''
}

export function getUserInfo(): UserVO | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserVO) : null
  } catch {
    return null
  }
}

export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_KEY, accessToken)
  localStorage.setItem(REFRESH_KEY, refreshToken)
}

export function setUserInfo(info: UserVO | null): void {
  if (info) {
    localStorage.setItem(USER_KEY, JSON.stringify(info))
  } else {
    localStorage.removeItem(USER_KEY)
  }
}

export function clearAuth(): void {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
}
