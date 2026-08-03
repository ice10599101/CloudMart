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

export function claimCoupon(templateId: number) {
  return request.post<ApiResponse<UserCoupon>>('/coupon/user-coupons/claim', null, { params: { templateId } })
}

export function listUserCoupons(params?: {
  status?: string
  page?: number
  pageSize?: number
}) {
  return request.get<ApiResponse<UserCoupon[]>>('/coupon/user-coupons', { params })
}
