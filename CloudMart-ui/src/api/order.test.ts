import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  createOrder, fetchOrders, fetchOrderById, cancelOrder,
  payForOrder, fetchPaymentByOrderId, simulatePaymentSuccess,
  shipOrder, confirmReceipt, requestRefund,
} from './order'

describe('order API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('createOrder() calls POST /order/orders with data', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    const data = { addressId: 1, items: [{ skuId: 1, quantity: 1 }] } as any

    await createOrder(data)

    expect(request.post).toHaveBeenCalledWith('/order/orders', data)
  })

  it('fetchOrders() calls GET /order/orders with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await fetchOrders({ status: 'PAID', page: 1, size: 10 } as any)

    expect(request.get).toHaveBeenCalledWith('/order/orders', { params: { status: 'PAID', page: 1, size: 10 } })
  })

  it('fetchOrderById() calls GET /order/orders/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await fetchOrderById(42)

    expect(request.get).toHaveBeenCalledWith('/order/orders/42')
  })

  it('cancelOrder() calls PUT /order/orders/:id/cancel', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await cancelOrder(42)

    expect(request.put).toHaveBeenCalledWith('/order/orders/42/cancel')
  })

  it('payForOrder() calls POST /order/orders/:id/pay', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await payForOrder(42)

    expect(request.post).toHaveBeenCalledWith('/order/orders/42/pay')
  })

  it('fetchPaymentByOrderId() calls GET /order/orders/:id/payment', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await fetchPaymentByOrderId(42)

    expect(request.get).toHaveBeenCalledWith('/order/orders/42/payment')
  })

  it('simulatePaymentSuccess() calls PUT /payment/payments/:id/simulate-success', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await simulatePaymentSuccess(100)

    expect(request.put).toHaveBeenCalledWith('/payment/payments/100/simulate-success')
  })

  it('shipOrder() calls PUT /order/orders/:id/ship', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await shipOrder(42)

    expect(request.put).toHaveBeenCalledWith('/order/orders/42/ship')
  })

  it('confirmReceipt() calls PUT /order/orders/:id/confirm', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await confirmReceipt(42)

    expect(request.put).toHaveBeenCalledWith('/order/orders/42/confirm')
  })

  it('requestRefund() calls POST /order/orders/:id/refund with params', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await requestRefund(42, '商品损坏')

    expect(request.post).toHaveBeenCalledWith('/order/orders/42/refund', null, {
      params: { refundReason: '商品损坏' },
    })
  })
})
