import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { getCart, addToCart, updateCartItem, removeCartItem, clearCart, clearCheckedItems } from './cart'

describe('cart API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getCart() calls GET /cart', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getCart()

    expect(request.get).toHaveBeenCalledWith('/cart')
  })

  it('addToCart() calls POST /cart/items with data', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await addToCart({ productId: 1, skuId: 2, quantity: 3 })

    expect(request.post).toHaveBeenCalledWith('/cart/items', { productId: 1, skuId: 2, quantity: 3 })
  })

  it('updateCartItem() calls PUT /cart/items/:skuId with data', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await updateCartItem(5, { quantity: 10, checked: 1 })

    expect(request.put).toHaveBeenCalledWith('/cart/items/5', { quantity: 10, checked: 1 })
  })

  it('removeCartItem() calls DELETE /cart/items/:skuId', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)

    await removeCartItem(5)

    expect(request.delete).toHaveBeenCalledWith('/cart/items/5')
  })

  it('clearCart() calls DELETE /cart', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)

    await clearCart()

    expect(request.delete).toHaveBeenCalledWith('/cart')
  })

  it('clearCheckedItems() calls DELETE /cart/checked', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)

    await clearCheckedItems()

    expect(request.delete).toHaveBeenCalledWith('/cart/checked')
  })
})
