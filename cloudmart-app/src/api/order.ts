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
  getDetail: (id: number | string) => request<Order>({ url: `/order/orders/${id}` }),
  create: (data: Record<string, unknown>) => request<Order>({ url: '/order/orders', method: 'POST', data }),
  /** TRADE-01：服务端报价——金额/商品信息以服务端为准 */
  createQuote: (data: { items: Array<{ skuId: number | string; quantity: number }>; couponId?: number | string }) =>
    request<{ quoteId: string; version: number; expiresAt: string; totalAmount: number | string; payAmount: number | string }>({
      url: '/order/quotes', method: 'POST', data,
    }),
  /** TRADE-01：报价下单——无价格字段，金额取报价快照 */
  createFromQuote: (data: { quoteId: number | string; receiverName: string; receiverPhone: string; receiverAddress: string }) =>
    request<Order>({ url: '/order/orders/v2', method: 'POST', data }),
  // T01：旧支付代理（/pay、/payment）已删除——收银台使用 paymentApi（api/payment.ts）
  cancel: (id: number) => request<void>({ url: `/order/orders/${id}/cancel`, method: 'PUT' }),
  confirmReceive: (id: number) => request<void>({ url: `/order/orders/${id}/confirm`, method: 'PUT' }),
  refund: (id: number, reason: string) =>
    request<void>({ url: `/order/orders/${id}/refund?refundReason=${encodeURIComponent(reason)}`, method: 'POST' }),
}
