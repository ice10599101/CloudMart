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
  getList: (params?: { status?: number; page?: number; pageSize?: number }) =>
    request<PaginatedResult<Order>>({ url: `/order/orders${buildQuery(params as Record<string, unknown>)}` }),
  getDetail: (id: number) => request<Order>({ url: `/order/orders/${id}` }),
  create: (data: Record<string, unknown>) => request<Order>({ url: '/order/orders', method: 'POST', data }),
  getPayment: (id: number) => request<unknown>({ url: `/order/orders/${id}/payment` }),
  pay: (id: number, data: { paymentMethod: string }) =>
    request<void>({ url: `/order/orders/${id}/pay`, method: 'POST', data }),
  cancel: (id: number) => request<void>({ url: `/order/orders/${id}/cancel`, method: 'PUT' }),
  confirmReceive: (id: number) => request<void>({ url: `/order/orders/${id}/confirm`, method: 'PUT' }),
  refund: (id: number, reason: string) =>
    request<void>({ url: `/order/orders/${id}/refund?refundReason=${encodeURIComponent(reason)}`, method: 'POST' }),
}
