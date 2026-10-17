import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/**
 * ADMIN-04：管理员域文件资产（mall-admin 代理 mall-file，仅 owner_type=ADMIN）。
 * fileId 对外字符串；refCount>0 的文件不可删除（409 FILE_REFERENCED）。
 */
export interface AdminFileAssetRow {
  fileId: string
  originalName: string
  mime: string
  sizeBytes: number
  visibility: 'PUBLIC' | 'PRIVATE' | string
  status: 'READY' | 'DELETING' | 'DELETED' | string
  purpose: string | null
  /** BASE-01：管理员账号 ID 保持字符串联合 */
  createdBy: number | string | null
  refCount: number
  /** PUBLIC 资产可预览 URL；PRIVATE 为 null */
  url: string | null
  createdAt: string
}

export interface AdminFileAssetPage {
  items: AdminFileAssetRow[]
  total: number
  page: number
  size: number
}

export function pageAdminFileAssets(
  params: { page?: number; size?: number; status?: string; purpose?: string } = {},
) {
  return request.get<ApiResponse<AdminFileAssetPage>>('/admin/file/assets', { params })
}

export function getAdminFileAsset(fileId: string) {
  return request.get<ApiResponse<AdminFileAssetRow>>(`/admin/file/assets/${fileId}`)
}

export function deleteAdminFileAsset(fileId: string) {
  return request.delete<ApiResponse<void>>(`/admin/file/assets/${fileId}`)
}
