import http from './http'
import type { LoginVO, TokenVO } from '@/types'

export interface SendCodeParams {
  phone: string
  scene: 'register' | 'login' | 'resetPassword'
}

export interface RegisterParams {
  phone: string
  password: string
  code: string
  nickname?: string
}

export interface LoginParams {
  phone: string
  password: string
}

export interface CodeLoginParams {
  phone: string
  code: string
}

export const authApi = {
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
