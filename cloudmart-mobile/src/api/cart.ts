import request from '@/utils/request'
import type { CartItem } from '@/types'

export const cartApi = {
  getCart: () => request<CartItem[]>({ url: '/cart' }),
  addItem: (data: { skuId: number | string; quantity: number }) =>
    request<CartItem>({ url: '/cart/items', method: 'POST', data }),
  updateItem: (skuId: number, data: { quantity: number }) =>
    request<CartItem>({ url: `/cart/items/${skuId}`, method: 'PUT', data }),
  removeItem: (skuId: number) => request<void>({ url: `/cart/items/${skuId}`, method: 'DELETE' }),
  clearCart: () => request<void>({ url: '/cart', method: 'DELETE' }),
  clearChecked: () => request<void>({ url: '/cart/checked', method: 'DELETE' }),
}
