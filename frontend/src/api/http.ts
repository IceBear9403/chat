import axios, { type AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'
import router from '@/router'
import type { Result, TokenVO } from '@/types'
import { ApiError } from '@/types'
import { prefixUploadsInPlace } from '@/utils/asset'
import { clearAuth, getAccessToken, getRefreshToken, setTokens } from '@/utils/token'

/**
 * 统一 HTTP 封装。
 *
 * <ul>
 *   <li>请求拦截：自动携带 Bearer accessToken</li>
 *   <li>响应拦截：解包 Result.data；code!=0 抛 ApiError
 *       （解包前先给 /uploads/xxx 资源路径补上部署前缀，见 utils/asset.ts）</li>
 *   <li>401 / token 失效：自动用 refreshToken 刷新并重放原请求，刷新失败跳登录页</li>
 * </ul>
 *
 * 类型约定：api 层调用 {@code http.post<T, T>} 时，第二个泛型为真实返回类型（已解包）。
 */

const http: AxiosInstance = axios.create({
  // BASE_URL 由 vite.config.ts 的 base 决定（子路径部署时为 '/chat/'，带尾斜杠）
  baseURL: `${import.meta.env.BASE_URL}api`,
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
  async (response) => {
    const body = response.data as Result<unknown>
    if (body.code !== 0) {
      // 后端业务错误以 HTTP 200 返回（GlobalExceptionHandler 无 @ResponseStatus），
      // 登录失效的 code（2006 无效 / 2007 过期 / 1002 未登录）也走这里：静默刷新，失败跳登录页
      if (body.code === 2007 || body.code === 2006 || body.code === 1002) {
        const config = response.config as AxiosRequestConfig & { _retry?: boolean }
        const ok = await handleAuthExpired(config)
        if (ok) {
          return http(config)
        }
      }
      return Promise.reject(new ApiError(body.code, body.message || '请求失败'))
    }
    // 后端返回的上传资源是 /uploads/xxx，补上部署前缀（子路径部署时必需）
    prefixUploadsInPlace(body.data)
    return body.data as never
  },
  async (error: AxiosError) => {
    // HTTP 层 401（代理/网关返回）与业务码登录失效：同样静默刷新，失败跳登录页
    const status = error.response?.status
    const code = (error.response?.data as Result<unknown>)?.code
    if (status === 401 || code === 2007 || code === 2006 || code === 1002) {
      const config = error.config as (AxiosRequestConfig & { _retry?: boolean }) | undefined
      const ok = await handleAuthExpired(config)
      if (ok && config) {
        return http(config)
      }
    }
    const message = (error.response?.data as Result<unknown>)?.message || error.message || '网络异常'
    return Promise.reject(new ApiError(code ?? status ?? -1, message))
  },
)

/**
 * 登录失效统一处理：静默刷新成功返回 true（由调用方重放原请求）；
 * 刷新失败 / 无 refresh token 时清空登录态并跳转登录页（带 redirect 回跳）。
 */
async function handleAuthExpired(config?: AxiosRequestConfig & { _retry?: boolean }): Promise<boolean> {
  if (config && !config._retry) {
    config._retry = true
    const ok = await refreshFlow()
    if (ok) {
      return true
    }
  }
  clearAuth()
  router.replace({
    path: '/login',
    query: { redirect: router.currentRoute.value.fullPath },
  })
  return false
}

// ---------- 刷新令牌（多请求并发 401 时只刷新一次，其余排队后统一重放） ----------

let refreshing = false
let waiters: Array<(ok: boolean) => void> = []

/** 不带拦截器的裸实例，避免刷新自身的递归 */
const raw: AxiosInstance = axios.create({
  baseURL: `${import.meta.env.BASE_URL}api`,
  timeout: 15000,
})

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
