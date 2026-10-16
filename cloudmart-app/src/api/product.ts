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

/** R08：当前用户在某订单行的评价（未评价时接口 data=null）；status 0=下架，hideReason 为下架理由 */
export interface OwnReview {
  id: number
  rating: number
  content: string
  images?: string[]
  status: number
  hideReason?: string | null
  orderItemId?: number
  orderId?: number
  skuId?: number
  productId?: number
  createdAt: string
}

export const productApi = {
  search: (params: { keyword?: string; categoryId?: number; page?: number; size?: number; sort?: string }) =>
    request<PaginatedResult<Product>>({ url: `/product/products/search${buildQuery(params as Record<string, unknown>)}` }),
  // BASE-01：商品 ID 保持字符串联合（雪花 ID 防精度损失）
  getDetail: (id: number | string) => request<Product>({ url: `/product/products/${id}` }),
  getCategories: () => request<ProductCategory[]>({ url: '/product/categories' }),
  getReviews: (productId: number | string, params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<Review>>({ url: `/product/reviews/product/${productId}${buildQuery(params as Record<string, unknown>)}` }),
  getReviewStats: (productId: number | string) =>
    request<ReviewStats>({ url: `/product/reviews/stats/${productId}` }),
  createReview: (data: {
    orderItemId: number
    orderId: number
    productId: number
    skuId: number
    rating: number
    content: string
  }) => request<OwnReview>({ url: '/product/reviews', method: 'POST', data }),
  /** R08：查询当前用户在某订单行的评价；未评价返回 data=null */
  getOwnReviewByOrderItem: (orderItemId: number) =>
    request<OwnReview | null>({ url: `/product/reviews/order-item/${orderItemId}` }),
}
