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

// ==================== T13：商品问答（小程序端闭环） ====================

export interface ProductAnswerItem {
  id: number | string
  authorUserId: number | string
  authorNickname: string | null
  body: string
  purchased: boolean
  createdAt: string
}

export interface ProductQuestionItem {
  id: number | string
  productId: number | string
  userId: number | string
  question: string
  createdAt: string
  answers: ProductAnswerItem[]
}

export interface MyProductQuestionItem extends ProductQuestionItem {
  productName?: string | null
}

export const productQuestionApi = {
  /** 商品详情问答列表（含答案） */
  list: (productId: number | string, page = 1, size = 10) =>
    request<{ records: ProductQuestionItem[]; total: number }>({
      url: `/products/${productId}/questions`, method: 'GET', data: { page, size } }),
  /** 提问（1~500 字） */
  ask: (productId: number | string, question: string) =>
    request<ProductQuestionItem>({
      url: `/products/${productId}/questions`, method: 'POST', data: { question } }),
  /** 回答（同人重复提交=编辑本人答案） */
  answer: (productId: number | string, questionId: number | string, answer: string) =>
    request<ProductQuestionItem>({
      url: `/products/${productId}/questions/${questionId}/answer`, method: 'POST', data: { answer } }),
  /** 我的提问（跨商品；不含本人已删） */
  myQuestions: (page = 1, size = 10) =>
    request<{ records: MyProductQuestionItem[]; total: number }>({
      url: '/products/questions/my', method: 'GET', data: { page, size } }),
  /** 删除自己的提问（仅本人；软删；幂等） */
  deleteOwn: (questionId: number | string) =>
    request<boolean>({ url: `/products/questions/my/${questionId}`, method: 'DELETE' }),
}
