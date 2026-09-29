import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { Order, CreateOrderRequest, OrderQueryParams, Payment } from '@/types'

export function createOrder(data: CreateOrderRequest) {
  return request.post<ApiResponse<Order>>('/order/orders', data)
}

/** TRADE-01：服务端报价——金额/商品信息以服务端为准 */
export interface QuoteRequest {
  items: Array<{ skuId: number | string; quantity: number }>
  couponId?: number | string
}

export interface QuoteResult {
  quoteId: string
  version: number
  expiresAt: string
  totalAmount: number | string
  payAmount: number | string
}

export function createQuote(data: QuoteRequest) {
  return request.post<ApiResponse<QuoteResult>>('/order/quotes', data)
}

/** 查询本人报价详情（5 分钟有效；他人报价按不存在处理） */
export function getQuoteById(quoteId: string) {
  return request.get<ApiResponse<QuoteResult>>(`/order/quotes/${quoteId}`)
}

/** TRADE-01：报价下单——无价格字段，金额取报价快照 */
export function createOrderFromQuote(data: {
  quoteId: number | string
  receiverName: string
  receiverPhone: string
  receiverAddress: string
}) {
  return request.post<ApiResponse<Order>>('/order/orders/v2', data)
}

export function fetchOrders(params: OrderQueryParams) {
  return request.get<ApiResponse<Order[]>>('/order/orders', { params })
}

export function fetchOrderById(id: number | string) {
  return request.get<ApiResponse<Order>>(`/order/orders/${id}`)
}

export function cancelOrder(id: number | string) {
  return request.put<ApiResponse<Order>>(`/order/orders/${id}/cancel`)
}

export function payForOrder(orderId: number | string) {
  return request.post<ApiResponse<Payment>>(`/order/orders/${orderId}/pay`)
}

export function fetchPaymentByOrderId(orderId: number) {
  return request.get<ApiResponse<Payment>>(`/order/orders/${orderId}/payment`)
}

export function simulatePaymentSuccess(paymentId: number) {
  return request.put<ApiResponse<Payment>>(`/payment/payments/${paymentId}/simulate-success`)
}

export function shipOrder(orderId: number) {
  return request.put<ApiResponse<Order>>(`/order/orders/${orderId}/ship`)
}

export function confirmReceipt(orderId: number) {
  return request.put<ApiResponse<Order>>(`/order/orders/${orderId}/confirm`)
}

export function requestRefund(orderId: number, refundReason: string) {
  return request.post<ApiResponse<Order>>(`/order/orders/${orderId}/refund`, null, {
    params: { refundReason },
  })
}
