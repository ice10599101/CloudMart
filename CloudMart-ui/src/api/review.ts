import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface ReviewItem {
  id: number
  productId: number
  userId: number
  username: string
  userAvatar: string | null
  orderId: number
  skuId: number
  skuAttributes: string | null
  rating: number
  content: string
  images: string[]
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
