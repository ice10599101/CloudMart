import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { AxiosProgressEvent, CancelToken } from 'axios'

export interface FileUploadResult {
  fileId: string
  url: string | null
  mime: string
  fileSize: number
  visibility: 'PUBLIC' | 'PRIVATE'
  status: string
}

export interface UploadFileOptions {
  onProgress?: (progress: number) => void
  cancelToken?: CancelToken
}

// S01/LC05：唯一资产上传统一走 /file/assets（配额+内容校验+可见性分域）；
// 旧 /file/upload 与 /file/delete 通道已删除，旧 URL 无任何执行入口。
export function uploadFile(file: File, options?: UploadFileOptions) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('visibility', 'PUBLIC')
  return request.post<ApiResponse<FileUploadResult>>('/file/assets', formData, {
    onUploadProgress: (event: AxiosProgressEvent) => {
      if (!options?.onProgress || !event.total) return
      const percent = Math.round((event.loaded / event.total) * 100)
      options.onProgress(percent)
    },
    cancelToken: options?.cancelToken,
    timeout: 60000,
  })
}

// ==================== FILE-01 文件资产（fileId 体系；区别于旧 /file/upload 的 url 体系） ====================

export interface AssetUploadResult {
  /** 资产 ID（对外 string，19 位安全） */
  fileId: string
  url: string
  mime: string
  fileSize: number
}

/** 上传文件资产（内容魔数校验，SVG 拒绝；visibility 缺省 PUBLIC） */
export function uploadFileAsset(file: File, visibility: 'PUBLIC' | 'PRIVATE' = 'PUBLIC') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('visibility', visibility)
  return request.post<ApiResponse<AssetUploadResult>>('/file/assets', formData)
}

/** 按 fileId 删除（引用计数；被引用 409） */
export function deleteFileAsset(fileId: string) {
  return request.delete<ApiResponse<void>>(`/file/assets/${fileId}`)
}

/** 私有附件 10 分钟签名下载地址 */
export function getAssetDownloadUrl(fileId: string) {
  return request.get<ApiResponse<{ url: string; expiresAt: string }>>(`/file/assets/${fileId}/download-url`)
}
