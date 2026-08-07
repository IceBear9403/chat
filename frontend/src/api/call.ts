import http from './http'
import type { CallConfigVO } from '@/types'

/** 通话接口：初始化 RTCPeerConnection 前拉取 ICE 服务器配置。 */
export const callApi = {
  config: (): Promise<CallConfigVO> => http.get<CallConfigVO, CallConfigVO>('/call/config'),
}
