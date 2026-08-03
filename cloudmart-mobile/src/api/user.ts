import request from '@/utils/request'
import type { User, Address, WishlistItem, PaginatedResult } from '@/types'

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export const userApi = {
  getProfile: () => request<User>({ url: '/user/users/me' }),
  updateProfile: (data: {
    nickname?: string
    avatar?: string
    signature?: string
    gender?: string
    birthday?: string
    constellation?: string
    occupation?: string
    school?: string
    location?: string
    hobbies?: string
  }) => request<User>({ url: '/user/users/profile', method: 'PUT', data }),
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    request<void>({ url: '/user/users/password', method: 'PUT', data }),
  getAddresses: () => request<Address[]>({ url: '/user/users/addresses' }),
  getDefaultAddress: () => request<Address>({ url: '/user/users/addresses/default' }),
  createAddress: (data: Record<string, unknown>) => request<Address>({ url: '/user/users/addresses', method: 'POST', data }),
  updateAddress: (id: number, data: Record<string, unknown>) =>
    request<Address>({ url: `/user/users/addresses/${id}`, method: 'PUT', data }),
  deleteAddress: (id: number) => request<void>({ url: `/user/users/addresses/${id}`, method: 'DELETE' }),
  setDefaultAddress: (id: number) => request<void>({ url: `/user/users/addresses/${id}/default`, method: 'PUT' }),
  getWishlist: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<WishlistItem>>({ url: `/product/wishlists${buildQuery(params as Record<string, unknown>)}` }),
  addToWishlist: (productId: number) => request<void>({ url: `/product/wishlists/${productId}`, method: 'POST' }),
  removeFromWishlist: (productId: number) => request<void>({ url: `/product/wishlists/${productId}`, method: 'DELETE' }),
  checkWishlist: (productId: number) => request<boolean>({ url: `/product/wishlists/check/${productId}` }),
}
