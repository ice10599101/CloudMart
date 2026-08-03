import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { listTemplates, claimCoupon, listUserCoupons } from './coupon'

describe('coupon API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listTemplates() calls GET /coupon/coupon-templates with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listTemplates({ type: 'FIXED', status: 'ACTIVE', page: 1, pageSize: 10 })

    expect(request.get).toHaveBeenCalledWith('/coupon/coupon-templates', {
      params: { type: 'FIXED', status: 'ACTIVE', page: 1, pageSize: 10 },
    })
  })

  it('listTemplates() calls GET /coupon/coupon-templates without params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listTemplates()

    expect(request.get).toHaveBeenCalledWith('/coupon/coupon-templates', { params: undefined })
  })

  it('claimCoupon() calls POST /coupon/user-coupons/claim with templateId', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await claimCoupon(5)

    expect(request.post).toHaveBeenCalledWith('/coupon/user-coupons/claim', null, { params: { templateId: 5 } })
  })

  it('listUserCoupons() calls GET /coupon/user-coupons with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listUserCoupons({ status: 'UNUSED', page: 1, pageSize: 20 })

    expect(request.get).toHaveBeenCalledWith('/coupon/user-coupons', {
      params: { status: 'UNUSED', page: 1, pageSize: 20 },
    })
  })
})
