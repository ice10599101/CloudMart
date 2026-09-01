import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { Order, CreateOrderRequest, OrderQueryParams, Payment } from '@/types'

export function createOrder(data: CreateOrderRequest) {
  return request.post<ApiResponse<Order>>('/order/orders', data)
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
