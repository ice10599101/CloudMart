import request from '@/utils/request'
import type { Product, ProductCategory, PaginatedResult } from '@/types'

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export interface ReviewStats {
  averageRating: number
  totalCount: number
  goodCount: number
  mediumCount: number
  badCount: number
  goodRate: number
}

export interface Review {
  id: number
  productId: number
  userId: number
  rating: number
  content: string
  images?: string[]
  createdAt: string
  user?: { id: number; nickname: string; avatar?: string }
}

/** R08：发表评价入参（契约对齐后端，以订单行为唯一资格锚点） */
export interface CreateReviewPayload {
  orderItemId: number
  orderId: number
  productId: number
  skuId: number
  rating: number
  content: string
  images?: string[]
}

/** R08：当前用户在某订单行的评价（GET /reviews/order-item/{id}，未评价为 null） */
export interface OwnReview {
  id: number
  rating: number
  content: string
  images?: string[] | null
  /** 状态：1=显示 0=下架 */
  status: number
  /** 下架/驳回理由（作者可见） */
  hideReason?: string | null
  createdAt: string
}

export const productApi = {
  search: (params: { keyword?: string; categoryId?: number; page?: number; size?: number; sort?: string }) =>
    request<PaginatedResult<Product>>({ url: `/product/products/search${buildQuery(params as Record<string, unknown>)}` }),
  getDetail: (id: number | string) => request<Product>({ url: `/product/products/${id}` }),
  getCategories: () => request<ProductCategory[]>({ url: '/product/categories' }),
  getReviews: (productId: number | string, params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<Review>>({ url: `/product/reviews/product/${productId}${buildQuery(params as Record<string, unknown>)}` }),
  getReviewStats: (productId: number | string) =>
    request<ReviewStats>({ url: `/product/reviews/stats/${productId}` }),
  createReview: (data: CreateReviewPayload) =>
    request<Review>({ url: '/product/reviews', method: 'POST', data: data as unknown as Record<string, unknown> }),
  /** R08：查我在某订单行的评价（含下架理由；未评价 data=null） */
  getOwnReviewByOrderItem: (orderItemId: number) =>
    request<OwnReview | null>({ url: `/product/reviews/order-item/${orderItemId}` }),
}
