import request from '@/utils/request'
import type { Order, PaginatedResult } from '@/types'

/** 订单项入参（契约对齐后端 CreateOrderRequest.OrderItemInput） */
export interface OrderItemInput {
  productId?: number
  skuId: number
  quantity: number
  productName?: string
  skuImage?: string
  skuAttributes?: string
  price: number
}

/** 创建订单入参（契约对齐后端 CreateOrderRequest） */
export interface CreateOrderPayload {
  /** 幂等键（同键重试返回同一订单） */
  requestId: string
  items: OrderItemInput[]
  receiverName?: string
  receiverPhone?: string
  receiverAddress?: string
  couponId?: number
  activityId?: number
}

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export const orderApi = {
  create: (data: CreateOrderPayload) =>
    request<Order>({ url: '/order/orders', method: 'POST', data: data as unknown as Record<string, unknown> }),
  /** TRADE-01：服务端报价——金额/商品信息以服务端为准 */
  createQuote: (data: { items: Array<{ skuId: number | string; quantity: number }>; couponId?: number | string }) =>
    request<{ quoteId: string; version: number; expiresAt: string; totalAmount: number | string; payAmount: number | string }>({
      url: '/order/quotes', method: 'POST', data: data as unknown as Record<string, unknown>,
    }),
  /** TRADE-01：报价下单——无价格字段，金额取报价快照 */
  createFromQuote: (data: { quoteId: number | string; receiverName: string; receiverPhone: string; receiverAddress: string }) =>
    request<Order>({ url: '/order/orders/v2', method: 'POST', data: data as unknown as Record<string, unknown> }),
  getList: (params?: { status?: number; page?: number; pageSize?: number }) =>
    request<PaginatedResult<Order>>({ url: `/order/orders${buildQuery(params as Record<string, unknown>)}` }),
  getDetail: (id: number | string) => request<Order>({ url: `/order/orders/${id}` }),
  cancel: (id: number | string) => request<void>({ url: `/order/orders/${id}/cancel`, method: 'PUT' }),
  confirm: (id: number | string) => request<void>({ url: `/order/orders/${id}/confirm`, method: 'PUT' }),
  /** 申请退款（refundReason 为 query 参数，契约对齐后端） */
  refund: (id: number | string, refundReason: string) =>
    request<void>({ url: `/order/orders/${id}/refund?refundReason=${encodeURIComponent(refundReason)}`, method: 'POST' }),
}
