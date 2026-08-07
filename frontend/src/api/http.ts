import axios, { type AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'
import router from '@/router'
import type { Result, TokenVO } from '@/types'
import { ApiError } from '@/types'
import { clearAuth, getAccessToken, getRefreshToken, setTokens } from '@/utils/token'

/**
 * 统一 HTTP 封装。
 *
 * <ul>
 *   <li>请求拦截：自动携带 Bearer accessToken</li>
 *   <li>响应拦截：解包 Result.data；code!=0 抛 ApiError</li>
 *   <li>401 / token 失效：自动用 refreshToken 刷新并重放原请求，刷新失败跳登录页</li>
 * </ul>
 *
 * 类型约定：api 层调用 {@code http.post<T, T>} 时，第二个泛型为真实返回类型（已解包）。
 */

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as Result<unknown>
    if (body.code !== 0) {
      return Promise.reject(new ApiError(body.code, body.message || '请求失败'))
    }
    return body.data as never
  },
  async (error: AxiosError) => {
    const status = error.response?.status
    const code = (error.response?.data as Result<unknown>)?.code

    // 令牌过期(2007) / 无效(2006) / HTTP 401：尝试静默刷新
    if (code === 2007 || code === 2006 || status === 401) {
      const config = error.config as (AxiosRequestConfig & { _retry?: boolean }) | undefined
      if (config && !config._retry) {
        config._retry = true
        const ok = await refreshFlow()
        if (ok) {
          return http(config)
        }
        clearAuth()
        router.replace({
          path: '/login',
          query: { redirect: router.currentRoute.value.fullPath },
        })
      }
    }
    const message = (error.response?.data as Result<unknown>)?.message || error.message || '网络异常'
    return Promise.reject(new ApiError(code ?? status ?? -1, message))
  },
)

// ---------- 刷新令牌（多请求并发 401 时只刷新一次，其余排队后统一重放） ----------

let refreshing = false
let waiters: Array<(ok: boolean) => void> = []

/** 不带拦截器的裸实例，避免刷新自身的递归 */
const raw: AxiosInstance = axios.create({ baseURL: '/api', timeout: 15000 })

async function doRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return false
  }
  try {
    const resp = await raw.post<Result<TokenVO>>('/auth/refresh', { refreshToken })
    const body = resp.data
    if (body.code !== 0 || !body.data) {
      return false
    }
    setTokens(body.data.accessToken, body.data.refreshToken)
    return true
  } catch {
    return false
  }
}

async function refreshFlow(): Promise<boolean> {
  if (refreshing) {
    // 已有刷新在进行，排队等待其完成
    return new Promise<boolean>((resolve) => {
      waiters.push(resolve)
    })
  }
  refreshing = true
  const ok = await doRefresh()
  refreshing = false
  const list = waiters
  waiters = []
  list.forEach((resolve) => resolve(ok))
  return ok
}

export default http
