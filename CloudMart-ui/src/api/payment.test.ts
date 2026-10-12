import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  createPaymentAttempt,
  submitMockPaymentCallback,
  getPaymentAttemptByOrderId,
  getPaymentChannels,
} from './payment'

// R09：渠道列表唯一来自 GET /payment/channels；创建尝试携带 scene+requestKey，
// 金额仍由服务端按订单权威计算。
describe('payment API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getPaymentChannels(H5) calls GET /payment/channels?scene=H5', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getPaymentChannels('H5')

    expect(request.get).toHaveBeenCalledWith('/payment/channels', { params: { scene: 'H5' } })
  })

  it('getPaymentChannels() 默认 H5 场景', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getPaymentChannels()

    expect(request.get).toHaveBeenCalledWith('/payment/channels', { params: { scene: 'H5' } })
  })

  it('createPaymentAttempt() posts orderId/channel/scene/requestKey aligned with R09 contract', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await createPaymentAttempt({
      orderId: 7,
      channel: 'MOCK',
      scene: 'H5',
      requestKey: 'rk-abc',
    })

    expect(request.post).toHaveBeenCalledWith('/payment/payment-attempts', {
      orderId: 7,
      channel: 'MOCK',
      scene: 'H5',
      requestKey: 'rk-abc',
    })
  })

  it('createPaymentAttempt() 不接受客户端金额字段', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await createPaymentAttempt({
      orderId: '90071992547409931',
      channel: 'MOCK',
      scene: 'H5',
      requestKey: 'rk-big',
    })

    const [, body] = vi.mocked(request.post).mock.calls[0]
    expect((body as Record<string, unknown>).amount).toBeUndefined()
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
