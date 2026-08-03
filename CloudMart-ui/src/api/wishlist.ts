import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface WishlistItem {
  id: number
  productId: number
  productName: string
  mainImage: string
  minPrice: number
  brand: string
  createdAt: string
}

export function addWishlist(productId: number) {
  return request.post<ApiResponse<void>>(`/product/wishlists/${productId}`)
}

export function removeWishlist(productId: number) {
  return request.delete<ApiResponse<void>>(`/product/wishlists/${productId}`)
}

export function getWishlistList(page: number, size: number) {
  return request.get<ApiResponse<WishlistItem[]>>('/product/wishlists', { params: { page, size } })
}

export function checkWishlist(productId: number) {
  return request.get<ApiResponse<{ isInWishlist: boolean }>>(`/product/wishlists/check/${productId}`)
}
