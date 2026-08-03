import request from '@/utils/request'
import type {
  CouponTemplate,
  SeckillActivity,
  SeckillProduct,
  GroupActivity,
  RecommendationResult,
  PaginatedResult,
} from '@/types'

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

interface OrderResult {
  orderNo?: string
}

export const marketingApi = {
  // Coupons
  getCouponTemplates: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<CouponTemplate>>({ url: `/coupon/coupon-templates${buildQuery(params as Record<string, unknown>)}` }),
  claimCoupon: (templateId: number) =>
    request<void>({ url: `/coupon/user-coupons/claim?templateId=${templateId}`, method: 'POST' }),
  getUserCoupons: (params?: { status?: number; page?: number; pageSize?: number }) =>
    request<PaginatedResult<CouponTemplate>>({ url: `/coupon/user-coupons${buildQuery(params as Record<string, unknown>)}` }),
  // Seckill
  getSeckillActivities: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<SeckillActivity>>({ url: `/seckill/activities${buildQuery(params as Record<string, unknown>)}` }),
  getSeckillActivity: (id: number) => request<SeckillActivity>({ url: `/seckill/activities/${id}` }),
  getSeckillProducts: (activityId: number) =>
    request<SeckillProduct[]>({ url: `/seckill/products/activity/${activityId}` }),
  executeSeckill: (data: { activityId: number; seckillProductId: number }) =>
    request<OrderResult>({ url: '/seckill/execute', method: 'POST', data }),
  getSeckillResult: (params: { activityId: number; seckillProductId: number }) =>
    request<OrderResult>({ url: `/seckill/result${buildQuery(params as Record<string, unknown>)}` }),
  // Group Buy
  getGroupActivities: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<GroupActivity>>({ url: `/marketing/group/activities${buildQuery(params as Record<string, unknown>)}` }),
  getGroupActivity: (id: number) => request<GroupActivity>({ url: `/marketing/group/activities/${id}` }),
  joinGroup: (data: { activityId: number; groupOrderId?: number }) =>
    request<OrderResult>({ url: '/marketing/group/join', method: 'POST', data }),
  getGroupOrders: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<OrderResult>>({ url: `/marketing/group/orders${buildQuery(params as Record<string, unknown>)}` }),
  // Tiered Promotion
  calculateTiered: (data: { productId: number; quantity: number }) =>
    request<unknown>({ url: '/marketing/tiered/calculate', method: 'POST', data }),

  // 优惠券推荐与兑换码
  recommendCoupons: (orderAmount: number) =>
    request<RecommendationResult>({ url: `/coupon/user-coupons/recommend?orderAmount=${orderAmount}` }),
  exchangeCode: (code: string) =>
    request<void>({ url: `/coupon/user-coupons/exchange?code=${encodeURIComponent(code)}`, method: 'POST' }),
}
