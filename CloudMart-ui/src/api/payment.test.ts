import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { createPayment, getPaymentByOrderId, simulateCallback, refundPayment } from './payment'

describe('payment API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('createPayment() calls POST /payment/payments with data', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await createPayment({ orderId: 1, amount: 99.99, payMethod: 'ALIPAY' })

    expect(request.post).toHaveBeenCalledWith('/payment/payments', { orderId: 1, amount: 99.99, payMethod: 'ALIPAY' })
  })

  it('createPayment() calls POST /payment/payments without payMethod', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await createPayment({ orderId: 1, amount: 50 })

    expect(request.post).toHaveBeenCalledWith('/payment/payments', { orderId: 1, amount: 50 })
  })

  it('getPaymentByOrderId() calls GET /payment/payments/order/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getPaymentByOrderId(42)

    expect(request.get).toHaveBeenCalledWith('/payment/payments/order/42')
  })

  it('simulateCallback() calls POST /payment/payments/callback with data', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await simulateCallback({ paymentId: 1, status: 'SUCCESS', transactionNo: 'TX123' })

    expect(request.post).toHaveBeenCalledWith('/payment/payments/callback', {
      paymentId: 1, status: 'SUCCESS', transactionNo: 'TX123',
    })
  })

  it('refundPayment() calls POST /payment/payments/:id/refund', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await refundPayment(1)

    expect(request.post).toHaveBeenCalledWith('/payment/payments/1/refund')
  })
})
