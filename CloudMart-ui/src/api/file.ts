import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface FileUploadResult {
  url: string
  originalFilename: string
  fileSize: number
}

export function uploadFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<FileUploadResult>>('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteFile(url: string) {
  return request.delete<ApiResponse<void>>('/file/delete', { params: { url } })
}
