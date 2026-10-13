import http from './http'
import type { AuditMomentVO, PageVO, ReportAdminVO, UserAdminVO, UserLogVO } from '@/types'

/**
 * 管理端接口（阶段七合规）。仅 is_admin=1 的用户可调用，后端 requireAdmin 校验。
 */

export const adminApi = {
  /** 用户列表（keyword：手机号/昵称，status 筛选） */
  listUsers: (params: { keyword?: string; status?: number; page?: number; size?: number }): Promise<PageVO<UserAdminVO>> =>
    http.get<PageVO<UserAdminVO>, PageVO<UserAdminVO>>('/admin/users', { params }),

  /** 封禁（status=1）/ 解封（status=0） */
  setUserStatus: (userId: number, status: number): Promise<null> =>
    http.put<null, null>(`/admin/users/${userId}/status`, null, { params: { status } }),

  /** 管理员修改任意用户昵称（不受昵称修改间隔限制） */
  setUserNickname: (userId: number, nickname: string): Promise<null> =>
    http.put<null, null>(`/admin/users/${userId}/nickname`, null, { params: { nickname } }),

  /** 内容审核队列 */
  listAudits: (params: { page?: number; size?: number }): Promise<PageVO<AuditMomentVO>> =>
    http.get<PageVO<AuditMomentVO>, PageVO<AuditMomentVO>>('/admin/audits', { params }),

  /** 审核：approve / reject */
  auditMoment: (momentId: number, action: 'approve' | 'reject'): Promise<null> =>
    http.post<null, null>(`/admin/audits/${momentId}/${action}`),

  /** 举报队列 */
  listReports: (params: { page?: number; size?: number }): Promise<PageVO<ReportAdminVO>> =>
    http.get<PageVO<ReportAdminVO>, PageVO<ReportAdminVO>>('/admin/reports', { params }),

  /** 处理举报：delete（删动态）/ ignore（忽略） */
  handleReport: (reportId: number, action: 'delete' | 'ignore'): Promise<null> =>
    http.post<null, null>(`/admin/reports/${reportId}/${action}`),

  /** 操作日志 */
  listLogs: (params: { page?: number; size?: number }): Promise<PageVO<UserLogVO>> =>
    http.get<PageVO<UserLogVO>, PageVO<UserLogVO>>('/admin/logs', { params }),
}
