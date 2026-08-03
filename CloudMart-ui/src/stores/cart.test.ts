import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useCartStore } from './cart'

vi.mock('@/api/cart', () => ({
  getCart: vi.fn(),
  addToCart: vi.fn(),
  updateCartItem: vi.fn(),
  removeCartItem: vi.fn(),
  clearCart: vi.fn(),
  clearCheckedItems: vi.fn(),
}))

vi.mock('antd', () => ({
  message: { success: vi.fn(), error: vi.fn() },
}))

import { getCart, addToCart, removeCartItem, clearCart as clearCartApi, clearCheckedItems } from '@/api/cart'
import { message } from 'antd'

describe('useCartStore', () => {
  beforeEach(() => {
    useCartStore.setState({
      items: [],
      totalCount: 0,
      totalPrice: 0,
      loading: false,
    })
    vi.clearAllMocks()
  })

  it('initializes with empty cart', () => {
    const state = useCartStore.getState()
    expect(state.items).toEqual([])
    expect(state.totalCount).toBe(0)
    expect(state.totalPrice).toBe(0)
    expect(state.loading).toBe(false)
  })

  it('fetchCart() updates items, totalCount, totalPrice', async () => {
    vi.mocked(getCart).mockResolvedValue({
      data: {
        success: true,
        data: {
          items: [{ skuId: 1, productName: 'Test', quantity: 2, price: 99.99 }],
          totalQuantity: 2,
          totalPrice: 199.98,
        },
      },
    } as any)

    await useCartStore.getState().fetchCart()

    const state = useCartStore.getState()
    expect(state.items).toHaveLength(1)
    expect(state.totalCount).toBe(2)
    expect(state.totalPrice).toBe(199.98)
    expect(state.loading).toBe(false)
  })

  it('fetchCart() handles null data gracefully', async () => {
    vi.mocked(getCart).mockResolvedValue({
      data: { success: true, data: null },
    } as any)

    await useCartStore.getState().fetchCart()

    const state = useCartStore.getState()
    expect(state.items).toEqual([])
    expect(state.totalCount).toBe(0)
    expect(state.totalPrice).toBe(0)
  })

  it('fetchCart() sets loading to false even on error', async () => {
    vi.mocked(getCart).mockRejectedValue(new Error('Network error'))

    await expect(useCartStore.getState().fetchCart()).rejects.toThrow('Network error')

    expect(useCartStore.getState().loading).toBe(false)
  })

  it('addItem() calls API and refreshes cart', async () => {
    vi.mocked(addToCart).mockResolvedValue({ data: { success: true } } as any)
    vi.mocked(getCart).mockResolvedValue({
      data: {
        success: true,
        data: {
          items: [{ skuId: 1, productName: 'Added', quantity: 1, price: 50 }],
          totalQuantity: 1,
          totalPrice: 50,
        },
      },
    } as any)

    await useCartStore.getState().addItem(1, 1, 1)

    expect(addToCart).toHaveBeenCalledWith({ productId: 1, skuId: 1, quantity: 1 })
    expect(message.success).toHaveBeenCalledWith('已添加到购物车')
    expect(useCartStore.getState().totalCount).toBe(1)
  })

  it('removeItem() calls API and refreshes cart', async () => {
    vi.mocked(removeCartItem).mockResolvedValue({ data: { success: true } } as any)
    vi.mocked(getCart).mockResolvedValue({
      data: {
        success: true,
        data: { items: [], totalQuantity: 0, totalPrice: 0 },
      },
    } as any)

    await useCartStore.getState().removeItem(1)

    expect(removeCartItem).toHaveBeenCalledWith(1)
    expect(useCartStore.getState().items).toEqual([])
  })

  it('clearCart() resets state', async () => {
    useCartStore.setState({
      items: [{ skuId: 1 } as any],
      totalCount: 1,
      totalPrice: 100,
    })

    vi.mocked(clearCartApi).mockResolvedValue({ data: { success: true } } as any)

    await useCartStore.getState().clearCart()

    expect(clearCartApi).toHaveBeenCalled()
    expect(useCartStore.getState().items).toEqual([])
    expect(useCartStore.getState().totalCount).toBe(0)
    expect(useCartStore.getState().totalPrice).toBe(0)
  })

  it('clearCheckedItems() refreshes cart', async () => {
    vi.mocked(clearCheckedItems).mockResolvedValue({ data: { success: true } } as any)
    vi.mocked(getCart).mockResolvedValue({
      data: {
        success: true,
        data: { items: [], totalQuantity: 0, totalPrice: 0 },
      },
    } as any)

    await useCartStore.getState().clearCheckedItems()

    expect(clearCheckedItems).toHaveBeenCalled()
    expect(getCart).toHaveBeenCalled()
  })
})
