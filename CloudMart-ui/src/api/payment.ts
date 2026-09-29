import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { Payment } from '@/types'

export function createPayment(data: { orderId: number | string; amount: number; payMethod?: string }) {
  return request.post<ApiResponse<Payment>>('/payment/payments', data)
}

export function getPaymentByOrderId(orderId: number | string) {
  return request.get<ApiResponse<Payment>>(`/payment/payments/order/${orderId}`)
}

export function simulateCallback(data: {
  paymentId: number
  status: string
  transactionNo?: string
}) {
  return request.post<ApiResponse<Payment>>('/payment/payments/callback', data)
}

export function refundPayment(paymentId: number) {
  return request.post<ApiResponse<Payment>>(`/payment/payments/${paymentId}/refund`)
}

// ==================== PAY-01 支付尝试（收银台主流程；旧 createPayment 保留为兜底） ====================

/** MOCK 渠道回调载荷（仅测试环境由创建尝试返回，签名由服务端签发） */
export interface MockCallbackPayload {
  merchantPaymentNo: string
  amount: string
  providerTxnNo: string
  notificationId: string
  signature: string
}

/** 创建尝试返回（归属/状态/金额全部服务端判定；MOCK 渠道附带 mockCallback） */
export interface PaymentAttemptResult {
  merchantPaymentNo: string
  status: string
  amount: number | string
  expiresAt: string | null
  mockCallback?: MockCallbackPayload
}

/** 创建支付尝试（单订单单活动尝试；只收 orderId + channel，客户端金额不参与） */
export function createPaymentAttempt(data: { orderId: number | string; channel: string }) {
  return request.post<ApiResponse<PaymentAttemptResult>>('/payment/payment-attempts', data)
}

/** 按订单查最近一次支付尝试状态（attempts 链路的收银台轮询真值源；归属服务端校验） */
export function getPaymentAttemptByOrderId(orderId: number | string) {
  return request.get<ApiResponse<PaymentAttemptResult>>(`/payment/payment-attempts/order/${orderId}`)
}

/** MOCK 渠道回调（HMAC 验签 + 重放防护 + 金额核对；仅测试环境启用） */
export function submitMockPaymentCallback(data: MockCallbackPayload) {
  return request.post<ApiResponse<{ result: string }>>('/payment/payment-attempts/mock-callbacks', data)
}
