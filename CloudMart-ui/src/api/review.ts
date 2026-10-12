import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface ReviewItem {
  id: number
  productId: number
  userId: number
  username: string
  userAvatar: string | null
  orderId: number
  /** R08 订单行锚点 */
  orderItemId?: number | null
  skuId: number
  skuAttributes: string | null
  rating: number
  content: string
  images: string[]
  status?: number
  /** R08 下架/驳回理由（作者可见） */
  hideReason?: string | null
  createdAt: string
}

export interface ReviewStats {
  productId: number
  averageRating: number
  totalReviews: number
  fiveStarCount: number
  fourStarCount: number
  threeStarCount: number
  twoStarCount: number
  oneStarCount: number
}

export interface CreateReviewRequest {
  /** R08 订单行 ID（资格唯一锚点） */
  orderItemId: number
  orderId: number
  productId: number
  skuId: number
  rating: number
  content: string
  images?: string[]
}

export function createReview(data: CreateReviewRequest) {
  return request.post<ApiResponse<ReviewItem>>('/product/reviews', data)
}

export function getProductReviews(productId: number | string, page: number, size: number) {
  return request.get<ApiResponse<ReviewItem[]>>(`/product/reviews/product/${productId}`, { params: { page, size } })
}

export function getReviewStats(productId: number | string) {
  return request.get<ApiResponse<ReviewStats>>(`/product/reviews/stats/${productId}`)
}

export function checkReview(orderId: number, productId: number) {
  return request.get<ApiResponse<{ hasReviewed: boolean }>>('/product/reviews/check', { params: { orderId, productId } })
}

/** R08 查我在某订单行的评价（未评价 data=null；下架时带 hideReason） */
export function getOwnReviewByOrderItem(orderItemId: number) {
  return request.get<ApiResponse<ReviewItem | null>>(`/product/reviews/order-item/${orderItemId}`)
}
