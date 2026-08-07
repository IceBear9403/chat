import http from './http'
import type { FeedRuleVO, UserVO } from '@/types'

export interface UpdateProfileParams {
  nickname?: string
  avatar?: string
  signature?: string
  gender?: number
  region?: string
}

export interface UpdatePrivacyParams {
  momentsVisible?: number
  allowStrangerReply?: number
  chatPolicy?: number
}

export const userApi = {
  me: (): Promise<UserVO> => http.get<UserVO, UserVO>('/user/me'),

  /** 搜索用户（昵称/手机号），发起会话用 */
  search: (keyword: string): Promise<UserVO[]> =>
    http.get<UserVO[], UserVO[]>('/user/search', { params: { keyword } }),

  updateProfile: (payload: UpdateProfileParams): Promise<UserVO> =>
    http.put<UserVO, UserVO>('/user/profile', payload),

  getPrivacy: (): Promise<FeedRuleVO> => http.get<FeedRuleVO, FeedRuleVO>('/user/privacy'),

  updatePrivacy: (payload: UpdatePrivacyParams): Promise<FeedRuleVO> =>
    http.put<FeedRuleVO, FeedRuleVO>('/user/privacy', payload),
}
