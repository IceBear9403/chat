import http from './http'
import type {
  FeedPageVO,
  LikeVO,
  MomentCommentVO,
  MomentDetailVO,
  MomentMediaVO,
  MomentVO,
} from '@/types'

export interface PublishMomentParams {
  content?: string
  /** 0 图文，1 纯文字，2 转发 */
  type: number
  /** 0 公开，1 仅好友，2 私密 */
  visibility: number
  media?: MomentMediaVO[]
  repostFrom?: number
}

export interface CommentParams {
  content: string
  parentId?: number
  replyTo?: number
}

export interface RepostParams {
  content?: string
  visibility: number
}

export const momentApi = {
  publish: (payload: PublishMomentParams): Promise<MomentVO> =>
    http.post<MomentVO, MomentVO>('/moment/publish', payload),

  feed: (type: string, cursorTime?: string | null, cursorId?: number | null, size = 10): Promise<FeedPageVO> =>
    http.get<FeedPageVO, FeedPageVO>('/moment/feed', {
      params: { type, cursorTime: cursorTime ?? undefined, cursorId: cursorId ?? undefined, size },
    }),

  my: (cursorTime?: string | null, cursorId?: number | null, size = 10): Promise<FeedPageVO> =>
    http.get<FeedPageVO, FeedPageVO>('/moment/my', {
      params: { cursorTime: cursorTime ?? undefined, cursorId: cursorId ?? undefined, size },
    }),

  detail: (id: number): Promise<MomentDetailVO> =>
    http.get<MomentDetailVO, MomentDetailVO>(`/moment/${id}`),

  like: (id: number): Promise<LikeVO> =>
    http.post<LikeVO, LikeVO>(`/moment/${id}/like`),

  comment: (id: number, payload: CommentParams): Promise<MomentCommentVO> =>
    http.post<MomentCommentVO, MomentCommentVO>(`/moment/${id}/comment`, payload),

  comments: (id: number): Promise<MomentCommentVO[]> =>
    http.get<MomentCommentVO[], MomentCommentVO[]>(`/moment/${id}/comments`),

  repost: (id: number, payload: RepostParams): Promise<MomentVO> =>
    http.post<MomentVO, MomentVO>(`/moment/${id}/repost`, payload),

  remove: (id: number): Promise<null> => http.delete<null, null>(`/moment/${id}`),

  report: (id: number): Promise<null> => http.post<null, null>(`/moment/${id}/report`),
}
