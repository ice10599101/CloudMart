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

// ==================== PAY-01 支付尝试（新流程；当前收银台仍走 createPayment，迁移待排期） ====================

/** 创建支付尝试（单订单单活动尝试；归属/金额服务端判定，返回商户支付号） */
export function createPaymentAttempt(data: { orderId: number | string; payMethod?: string }) {
  return request.post<ApiResponse<{ attemptId: string; merchantOrderNo?: string; status?: string }>>(
    '/payment/payment-attempts',
    data,
  )
}
