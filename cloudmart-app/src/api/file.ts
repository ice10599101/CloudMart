import request from '@/utils/request'

export interface FileUploadResult {
  url: string
  [key: string]: unknown
}

export const fileApi = {
  upload: (data: FormData | { file: string; type?: string }) =>
    request<FileUploadResult>({
      url: '/file/upload',
      method: 'POST',
      data,
      header: data instanceof FormData ? { 'Content-Type': 'multipart/form-data' } : undefined,
    }),
}
