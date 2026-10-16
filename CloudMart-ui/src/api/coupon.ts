import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { CouponTemplate, UserCoupon } from '@/types'

export function listTemplates(params?: {
  type?: string
  status?: string
  page?: number
  pageSize?: number
}) {
  return request.get<ApiResponse<CouponTemplate[]>>('/coupon/coupon-templates', { params })
}

// BASE-01：模板 ID 为雪花 ID，保持字符串联合（URL 参数直接透传，禁止 Number 转换）
export function claimCoupon(templateId: number | string) {
  return request.post<ApiResponse<UserCoupon>>('/coupon/user-coupons/claim', null, { params: { templateId } })
}

export function listUserCoupons(params?: {
  status?: string
  page?: number
  pageSize?: number
}) {
  return request.get<ApiResponse<UserCoupon[]>>('/coupon/user-coupons', { params })
}
