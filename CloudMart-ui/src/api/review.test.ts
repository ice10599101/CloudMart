import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { createReview, getProductReviews, getReviewStats, checkReview } from './review'

describe('review API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('createReview() calls POST /product/reviews with data', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await createReview({ orderId: 1, productId: 2, skuId: 3, rating: 5, content: 'Great!', images: ['img.jpg'] })

    expect(request.post).toHaveBeenCalledWith('/product/reviews', {
      orderId: 1, productId: 2, skuId: 3, rating: 5, content: 'Great!', images: ['img.jpg'],
    })
  })

  it('getProductReviews() calls GET /product/reviews/product/:id with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getProductReviews(1, 1, 20)

    expect(request.get).toHaveBeenCalledWith('/product/reviews/product/1', { params: { page: 1, size: 20 } })
  })

  it('getReviewStats() calls GET /product/reviews/stats/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getReviewStats(1)

    expect(request.get).toHaveBeenCalledWith('/product/reviews/stats/1')
  })

  it('checkReview() calls GET /product/reviews/check with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await checkReview(1, 2)

    expect(request.get).toHaveBeenCalledWith('/product/reviews/check', { params: { orderId: 1, productId: 2 } })
  })
})
