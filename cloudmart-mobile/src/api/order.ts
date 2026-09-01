import request from '@/utils/request'
import type { Order, PaginatedResult } from '@/types'

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export const orderApi = {
  create: (data: { addressId: number; items: Array<{ skuId: number; quantity: number }>; couponId?: number }) =>
    request<Order>({ url: '/order/orders', method: 'POST', data }),
  getList: (params?: { status?: number; page?: number; pageSize?: number }) =>
    request<PaginatedResult<Order>>({ url: `/order/orders${buildQuery(params as Record<string, unknown>)}` }),
  getDetail: (id: number | string) => request<Order>({ url: `/order/orders/${id}` }),
  cancel: (id: number | string) => request<void>({ url: `/order/orders/${id}/cancel`, method: 'PUT' }),
  pay: (id: number | string, data: { paymentMethod: string }) =>
    request<void>({ url: `/order/orders/${id}/pay`, method: 'POST', data }),
  confirm: (id: number | string) => request<void>({ url: `/order/orders/${id}/confirm`, method: 'PUT' }),
  refund: (id: number | string, data: { reason: string }) =>
    request<void>({ url: `/order/orders/${id}/refund`, method: 'POST', data }),
  getPayment: (id: number | string) => request<unknown>({ url: `/order/orders/${id}/payment` }),
}
