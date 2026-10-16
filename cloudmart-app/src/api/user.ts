import request from '@/utils/request'
import type { User, Address, WishlistItem, PaginatedResult } from '@/types'

/** R19：我的邀请信息（契约对齐 GET /user/invites/my） */
export interface MyInviteInfo {
  code: string
  invitedCount: number
  rewardPerInvite: number
}

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
  /** 他人公开资料（契约对齐 Web 端 getUserPublicProfile：GET /user/users/{id}，后端按隐私过滤字段） */
  getPublicProfile: (id: number | string) => request<User>({ url: `/user/users/${id}` }),
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
  // BASE-01：商品 ID 保持字符串联合（雪花 ID 防精度损失）
  addToWishlist: (productId: number | string) => request<void>({ url: `/product/wishlists/${productId}`, method: 'POST' }),
  removeFromWishlist: (productId: number | string) => request<void>({ url: `/product/wishlists/${productId}`, method: 'DELETE' }),
  checkWishlist: (productId: number | string) => request<boolean>({ url: `/product/wishlists/check/${productId}` }),

  // ---- R19 用户邀请（契约对齐 mall-user InviteController）----
  /** 我的邀请码与邀请统计 */
  getMyInvites: () => request<MyInviteInfo>({ url: '/user/invites/my' }),
  /** 绑定邀请人（成功后建立邀请关系，返回关系 ID） */
  bindInvite: (code: string) =>
    request<{ relationId: number | string }>({
      url: '/user/invites/bind',
      method: 'POST',
      data: { code },
    }),
}
