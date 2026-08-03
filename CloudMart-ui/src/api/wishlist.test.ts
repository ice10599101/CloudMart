import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { addWishlist, removeWishlist, getWishlistList, checkWishlist } from './wishlist'

describe('wishlist API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('addWishlist() calls POST /product/wishlists/:productId', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await addWishlist(1)

    expect(request.post).toHaveBeenCalledWith('/product/wishlists/1')
  })

  it('removeWishlist() calls DELETE /product/wishlists/:productId', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)

    await removeWishlist(1)

    expect(request.delete).toHaveBeenCalledWith('/product/wishlists/1')
  })

  it('getWishlistList() calls GET /product/wishlists with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getWishlistList(1, 20)

    expect(request.get).toHaveBeenCalledWith('/product/wishlists', { params: { page: 1, size: 20 } })
  })

  it('checkWishlist() calls GET /product/wishlists/check/:productId', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await checkWishlist(42)

    expect(request.get).toHaveBeenCalledWith('/product/wishlists/check/42')
  })
})
