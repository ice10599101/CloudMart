import request from '@/utils/request'

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export const marketingApi = {
  getCoupons: (params?: { page?: number; pageSize?: number }) =>
    request({ url: `/coupon/coupon-templates${buildQuery(params as Record<string, unknown>)}` }),
  claimCoupon: (templateId: number) =>
    request({ url: `/coupon/user-coupons/claim?templateId=${templateId}`, method: 'POST' }),
  getMyCoupons: (params?: { status?: string; page?: number; pageSize?: number }) =>
    request({ url: `/coupon/user-coupons${buildQuery(params as Record<string, unknown>)}` }),
  getSeckillActivities: (params?: { status?: number }) =>
    request({ url: `/seckill/activities${buildQuery(params as Record<string, unknown>)}` }),
  getSeckillActivity: (id: number) => request({ url: `/seckill/activities/${id}` }),
  getSeckillProducts: (activityId: number) =>
    request({ url: `/seckill/products/activity/${activityId}` }),
  executeSeckill: (data: { activityId: number; seckillProductId: number }) =>
    request({ url: '/seckill/execute', method: 'POST', data }),
  getSeckillResult: (params: { activityId: number; seckillProductId: number }) =>
    request({ url: `/seckill/result${buildQuery(params as Record<string, unknown>)}` }),
  getGroupActivities: (params?: { page?: number; pageSize?: number }) =>
    request({ url: `/marketing/group/activities${buildQuery(params as Record<string, unknown>)}` }),
  getGroupActivity: (id: number) => request({ url: `/marketing/group/activities/${id}` }),
  joinGroup: (data: { activityId: number; groupOrderId?: number }) =>
    request({ url: '/marketing/group/join', method: 'POST', data }),
  getGroupOrders: (params?: { page?: number; pageSize?: number }) =>
    request({ url: `/marketing/group/orders${buildQuery(params as Record<string, unknown>)}` }),

  // 优惠券推荐与兑换码
  recommendCoupons: (orderAmount: number) =>
    request({ url: `/coupon/user-coupons/recommend?orderAmount=${orderAmount}` }),
  exchangeCode: (code: string) =>
    request({ url: `/coupon/user-coupons/exchange?code=${encodeURIComponent(code)}`, method: 'POST' }),
}
