import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { Cart, CartItem } from '@/types'

export function getCart() {
  return request.get<ApiResponse<Cart>>('/cart')
}

export function addToCart(data: { productId: number; skuId: number; quantity: number }) {
  return request.post<ApiResponse<CartItem>>('/cart/items', data)
}

export function updateCartItem(skuId: number, data: { quantity?: number; checked?: number }) {
  return request.put<ApiResponse<CartItem>>(`/cart/items/${skuId}`, data)
}

export function removeCartItem(skuId: number) {
  return request.delete<ApiResponse<void>>(`/cart/items/${skuId}`)
}

export function clearCart() {
  return request.delete<ApiResponse<void>>('/cart')
}

export function clearCheckedItems() {
  return request.delete<ApiResponse<void>>('/cart/checked')
}
