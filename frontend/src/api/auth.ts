import http from './http'
import type { AuthConfigVO, LoginVO, TokenVO } from '@/types'

/**
 * account 为当前认证模式（app.auth.mode）下的账号：
 * phone 模式=手机号，email 模式=邮箱。
 */
export interface SendCodeParams {
  account: string
  scene: 'register' | 'login' | 'resetPassword'
}

export interface RegisterParams {
  account: string
  password: string
  code: string
  nickname?: string
}

export interface LoginParams {
  account: string
  password: string
}

export interface CodeLoginParams {
  account: string
  code: string
}

export const authApi = {
  /** 认证配置：authMode=phone/email，供登录/注册页按模式渲染 */
  config: (): Promise<AuthConfigVO> => http.get<AuthConfigVO, AuthConfigVO>('/auth/config'),

  sendCode: (payload: SendCodeParams): Promise<string> =>
    http.post<string, string>('/auth/send-code', payload),

  register: (payload: RegisterParams): Promise<LoginVO> =>
    http.post<LoginVO, LoginVO>('/auth/register', payload),

  login: (payload: LoginParams): Promise<LoginVO> =>
    http.post<LoginVO, LoginVO>('/auth/login', payload),

  loginByCode: (payload: CodeLoginParams): Promise<LoginVO> =>
    http.post<LoginVO, LoginVO>('/auth/login/code', payload),

  refresh: (refreshToken: string): Promise<TokenVO> =>
    http.post<TokenVO, TokenVO>('/auth/refresh', { refreshToken }),

  logout: (refreshToken: string): Promise<null> =>
    http.post<null, null>('/auth/logout', { refreshToken }),
}
