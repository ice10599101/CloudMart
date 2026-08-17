import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

// ========== Types（与 mall-wish AdminWishVO / CategoryVO 对齐） ==========

export type AdminWishStatus = 'DRAFT' | 'ACTIVE' | 'OVERDUE' | 'FULFILLING' | 'FULFILLED' | 'ARCHIVED'
export type AdminAuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'AUTO_HIDDEN'
export type AdminWishVisibility = 'PUBLIC' | 'PRIVATE' | 'TREE_HOLE'
export type AdminFruitType = 'GLOW' | 'RESONANCE' | 'BLOOM' | 'SPARK'

export interface AdminWishCategoryRecord {
  id: number
  code: string
  name: string
  icon: string | null
  sortOrder: number
}

export interface AdminWishRecord {
  id: number
  userId: number
  title: string
  description: string
  mediaUrls: string[]
  categoryId: number
  categoryName: string
  tags: string[]
  visibility: AdminWishVisibility
  status: AdminWishStatus
  fruitType: AdminFruitType
  auditStatus: AdminAuditStatus
  auditStrategy: string
  isVisible: boolean
  lightCount: number
  sameWishCount: number
  blessCount: number
  supportCount: number
  expectedAt: string | null
  fulfilledAt: string | null
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export interface AdminWishListParams {
  userId?: number
  categoryId?: number
  status?: AdminWishStatus
  auditStatus?: AdminAuditStatus
  visibility?: AdminWishVisibility
  keyword?: string
  page?: number
  pageSize?: number
}

// ========== 枚举展示映射 ==========

export const WISH_STATUS_MAP: Record<AdminWishStatus, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  ACTIVE: { label: '进行中', color: 'processing' },
  OVERDUE: { label: '已逾期', color: 'warning' },
  FULFILLING: { label: '还愿中', color: 'cyan' },
  FULFILLED: { label: '已还愿', color: 'success' },
  ARCHIVED: { label: '已归档', color: 'default' },
}

export const AUDIT_STATUS_MAP: Record<AdminAuditStatus, { label: string; color: string }> = {
  PENDING: { label: '待审核', color: 'warning' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已拒绝', color: 'error' },
  AUTO_HIDDEN: { label: '自动隐藏', color: 'default' },
}

export const VISIBILITY_MAP: Record<AdminWishVisibility, { label: string; color: string }> = {
  PUBLIC: { label: '公开', color: 'green' },
  PRIVATE: { label: '私密', color: 'default' },
  TREE_HOLE: { label: '树洞', color: 'purple' },
}

export const FRUIT_TYPE_MAP: Record<AdminFruitType, { label: string; emoji: string }> = {
  GLOW: { label: '微光', emoji: '🌱' },
  RESONANCE: { label: '共鸣', emoji: '💫' },
  BLOOM: { label: '绽放', emoji: '🌸' },
  SPARK: { label: '星光', emoji: '⭐' },
}

// ========== 心愿列表与审核 ==========

export function getAdminWishes(params: AdminWishListParams) {
  return request.get<ApiResponse<AdminWishRecord[]>>('/admin/wish/wishes', { params })
}

export function getAdminWishDetail(id: number) {
  return request.get<ApiResponse<AdminWishRecord>>(`/admin/wish/wishes/${id}`)
}

export function auditAdminWish(id: number, data: { auditStatus: 'APPROVED' | 'REJECTED'; rejectReason?: string }) {
  return request.put<ApiResponse<AdminWishRecord>>(`/admin/wish/wishes/${id}/audit`, data)
}

// ========== 心愿分类管理 ==========

export function getAdminWishCategories() {
  return request.get<ApiResponse<AdminWishCategoryRecord[]>>('/admin/wish/categories')
}

export function createAdminWishCategory(data: { code: string; name: string; sort?: number; icon?: string }) {
  return request.post<ApiResponse<AdminWishCategoryRecord>>('/admin/wish/categories', data)
}

export function updateAdminWishCategory(id: number, data: { name?: string; sort?: number; icon?: string }) {
  return request.put<ApiResponse<AdminWishCategoryRecord>>(`/admin/wish/categories/${id}`, data)
}

export function deleteAdminWishCategory(id: number) {
  return request.delete<ApiResponse<null>>(`/admin/wish/categories/${id}`)
}
