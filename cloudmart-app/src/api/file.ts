import request from '@/utils/request'
import { resolveMediaUrl } from '@/utils/mediaUrl'

export interface FileUploadResult {
  fileId: string
  url: string | null
  mime: string
  fileSize: number
  visibility: 'PUBLIC' | 'PRIVATE'
  status: string
  [key: string]: unknown
}

export const fileApi = {
  // S01/LC05：唯一资产上传入口 /file/assets（旧 /file/upload 已删除）
  upload: async (data: FormData | { file: string; type?: string }) => {
    const res = await request<FileUploadResult>({
      url: '/file/assets',
      method: 'POST',
      data,
      header: data instanceof FormData ? { 'Content-Type': 'multipart/form-data' } : undefined,
    })
    // 后端本地存储返回相对 URL：Native 端（RN Image/Audio）需要完整地址
    const url = res.data?.data?.url
    if (url) {
      res.data.data.url = resolveMediaUrl(url)
    }
    return res
  },
}
