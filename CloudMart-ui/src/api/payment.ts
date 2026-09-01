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
