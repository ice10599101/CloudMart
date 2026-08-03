import { create } from 'zustand'
import {
  getCart,
  addToCart,
  updateCartItem,
  removeCartItem,
  clearCart as clearCartApi,
  clearCheckedItems as clearCheckedItemsApi,
} from '@/api/cart'
import type { CartItem } from '@/types'
import { message } from 'antd'

interface CartState {
  items: CartItem[]
  totalCount: number
  totalPrice: number
  loading: boolean
  fetchCart: () => Promise<void>
  addItem: (productId: number, skuId: number, quantity?: number) => Promise<void>
  updateItem: (skuId: number, data: { quantity?: number; checked?: number }) => Promise<void>
  removeItem: (skuId: number) => Promise<void>
  clearCart: () => Promise<void>
  clearCheckedItems: () => Promise<void>
}

export const useCartStore = create<CartState>((set) => ({
  items: [],
  totalCount: 0,
  totalPrice: 0,
  loading: false,

  fetchCart: async () => {
    set({ loading: true })
    try {
      const { data: response } = await getCart()
      const cart = response.data ?? { items: [], totalQuantity: 0, totalPrice: 0 }
      set({
        items: cart.items,
        totalCount: cart.totalQuantity,
        totalPrice: cart.totalPrice,
      })
    } finally {
      set({ loading: false })
    }
  },

  addItem: async (productId, skuId, quantity = 1) => {
    await addToCart({ productId, skuId, quantity })
    message.success('已添加到购物车')
    const { data: response } = await getCart()
    const cart = response.data ?? { items: [], totalQuantity: 0, totalPrice: 0 }
    set({
      items: cart.items,
      totalCount: cart.totalQuantity,
      totalPrice: cart.totalPrice,
    })
  },

  updateItem: async (skuId, data) => {
    await updateCartItem(skuId, data)
    const { data: response } = await getCart()
    const cart = response.data ?? { items: [], totalQuantity: 0, totalPrice: 0 }
    set({
      items: cart.items,
      totalCount: cart.totalQuantity,
      totalPrice: cart.totalPrice,
    })
  },

  removeItem: async (skuId) => {
    await removeCartItem(skuId)
    const { data: response } = await getCart()
    const cart = response.data ?? { items: [], totalQuantity: 0, totalPrice: 0 }
    set({
      items: cart.items,
      totalCount: cart.totalQuantity,
      totalPrice: cart.totalPrice,
    })
  },

  clearCart: async () => {
    await clearCartApi()
    set({ items: [], totalCount: 0, totalPrice: 0 })
  },

  clearCheckedItems: async () => {
    await clearCheckedItemsApi()
    const { data: response } = await getCart()
    const cart = response.data ?? { items: [], totalQuantity: 0, totalPrice: 0 }
    set({
      items: cart.items,
      totalCount: cart.totalQuantity,
      totalPrice: cart.totalPrice,
    })
  },
}))
