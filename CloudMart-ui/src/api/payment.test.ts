import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  createPaymentAttempt, submitMockPaymentCallback, getPaymentAttemptByOrderId,
} from './payment'

// T01：旧 /payments 链路（createPayment/getPaymentByOrderId/simulateCallback/refundPayment）
// 已删除，唯一支付 API 为 payment-attempts；旧路径无可执行 handler。
describe('payment API (attempts only)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('createPaymentAttempt() posts orderId + channel aligned with PaymentAttemptController', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await createPaymentAttempt({ orderId: 7, channel: 'MOCK' })

    expect(request.post).toHaveBeenCalledWith('/payment/payment-attempts', { orderId: 7, channel: 'MOCK' })
  })

  it('createPaymentAttempt() 不接受客户端金额字段', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    // 金额由服务端按订单权威计算——请求体只有 orderId + channel
    await createPaymentAttempt({ orderId: '90071992547409931', channel: 'MOCK' })

    const [, body] = vi.mocked(request.post).mock.calls[0]
    expect(Object.keys(body as object).sort()).toEqual(['channel', 'orderId'])
  })

  it('submitMockPaymentCallback() posts signed mock callback payload', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    const payload = {
      merchantPaymentNo: 'MP1',
      amount: '99.99',
      providerTxnNo: 'TX1',
      notificationId: 'N1',
      signature: 'sig',
    }
    await submitMockPaymentCallback(payload)

    expect(request.post).toHaveBeenCalledWith('/payment/payment-attempts/mock-callbacks', payload)
  })

  it('getPaymentAttemptByOrderId() calls GET /payment/payment-attempts/order/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getPaymentAttemptByOrderId(7)

    expect(request.get).toHaveBeenCalledWith('/payment/payment-attempts/order/7')
  })

  it('19 位订单 ID 全链路字符串传输（QA38：无精度损失）', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    const bigOrderId = '90071992547409931'
    await getPaymentAttemptByOrderId(bigOrderId)

    expect(request.get).toHaveBeenCalledWith(`/payment/payment-attempts/order/${bigOrderId}`)
  })
})
