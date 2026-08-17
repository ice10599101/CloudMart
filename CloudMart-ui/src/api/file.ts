import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { AxiosProgressEvent, CancelToken } from 'axios'

export interface FileUploadResult {
  url: string
  originalFilename: string
  fileSize: number
}

export interface UploadFileOptions {
  onProgress?: (progress: number) => void
  cancelToken?: CancelToken
}

export function uploadFile(file: File, options?: UploadFileOptions) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<FileUploadResult>>('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event: AxiosProgressEvent) => {
      if (!options?.onProgress || !event.total) return
      const percent = Math.round((event.loaded / event.total) * 100)
      options.onProgress(percent)
    },
    cancelToken: options?.cancelToken,
    timeout: 60000,
  })
}

export function deleteFile(url: string) {
  return request.delete<ApiResponse<void>>('/file/delete', { params: { url } })
}
