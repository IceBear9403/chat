import http from './http'
import type { FileUploadVO } from '@/types'

/** 上传文件（multipart），返回可访问 url */
export function uploadFile(file: File, bucket = 'moment'): Promise<FileUploadVO> {
  const form = new FormData()
  form.append('file', file)
  form.append('bucket', bucket)
  return http.post<FileUploadVO, FileUploadVO>('/file/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
