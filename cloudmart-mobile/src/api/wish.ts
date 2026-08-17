import request from '@/utils/request'
import type {
  WishCategory,
  WishListItem,
  MyWishListItem,
  WishDetail,
  WishStatus,
  WishVisibility,
  HomeAggregation,
} from '@/types'

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export interface CreateWishPayload {
  title: string
  description: string
  categoryId: number
  visibility: WishVisibility
  mediaUrls?: string[]
  tags?: string[]
  expectedAt?: string
}

export interface UpdateWishPayload {
  title?: string
  description?: string
  categoryId?: number
  visibility?: WishVisibility
  mediaUrls?: string[]
  tags?: string[]
  expectedAt?: string
}

export interface WishListQuery {
  categoryId?: number
  keyword?: string
  cursor?: string
  pageSize?: number
}

export interface MyWishListQuery {
  status?: WishStatus
  cursor?: string
  pageSize?: number
}

export const wishApi = {
  getHome: () => request<HomeAggregation>({ url: '/wish/home' }),
  getCategories: () => request<WishCategory[]>({ url: '/wish/categories' }),
  listWishes: (params: WishListQuery) =>
    request<WishListItem[]>({ url: `/wish/wishes${buildQuery(params as Record<string, unknown>)}` }),
  getWishDetail: (id: number) => request<WishDetail>({ url: `/wish/wishes/${id}` }),
  createWish: (data: CreateWishPayload) =>
    request<WishDetail>({ url: '/wish/wishes', method: 'POST', data }),
  updateWish: (id: number, data: UpdateWishPayload) =>
    request<WishDetail>({ url: `/wish/wishes/${id}`, method: 'PUT', data }),
  deleteWish: (id: number) => request<void>({ url: `/wish/wishes/${id}`, method: 'DELETE' }),
  listMyWishes: (params: MyWishListQuery) =>
    request<MyWishListItem[]>({ url: `/wish/wishes/my${buildQuery(params as Record<string, unknown>)}` }),
}
