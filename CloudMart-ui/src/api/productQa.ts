import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/** PRODUCT-01：商品问答（两表模型——question 一问多答） */
export interface ProductAnswerItem {
  id: number | string
  authorUserId: number | string
  authorNickname: string
  body: string
  /** 回答人已购快照：历史资格展示，不作为授权依据 */
  purchased: boolean
  /** 0 正常 / 1 隐藏（管理视图可见隐藏项） */
  status: number
  createdAt: string
}

export interface ProductQuestionItem {
  id: number | string
  productId: number | string
  askerUserId: number | string
  askerNickname: string
  question: string
  answers: ProductAnswerItem[]
  /** 0 正常 / 1 隐藏（管理视图可见隐藏项） */
  status: number
  createdAt: string
}

export interface ProductQuestionPage {
  records: ProductQuestionItem[]
  total: number
  current?: number
  size?: number
}

/** 问答列表（公开；未回答的问题也返回） */
export function listProductQuestions(productId: number | string, page = 1, size = 10) {
  return request.get<ApiResponse<ProductQuestionPage>>(
    `/product/products/${productId}/questions`,
    { params: { page, size } },
  )
}

/** 提问（登录；1..500 字；服务端转义与校验） */
export function askProductQuestion(productId: number | string, question: string) {
  return request.post<ApiResponse<ProductQuestionItem>>(
    `/product/products/${productId}/questions`,
    { question },
  )
}

/** 回答（登录；归属校验在服务端；同人重复提交为编辑本人答案） */
export function answerProductQuestion(
  productId: number | string,
  questionId: number | string,
  answer: string,
) {
  return request.post<ApiResponse<ProductQuestionItem>>(
    `/product/products/${productId}/questions/${questionId}/answer`,
    { answer },
  )
}

/** 我的问答（本人提问，含回答进度；跨商品，T13 独立路由，返回数组+meta） */
export function listMyProductQuestions(page = 1, size = 10) {
  return request.get<ApiResponse<ProductQuestionItem[]>>('/product/products/questions/my', {
    params: { page, size },
  })
}

// ===== 管理端（商城→商品问答；business:product:question） =====

export function adminListProductQuestions(params: {
  productId?: number | string
  status?: number
  page?: number
  pageSize?: number
}) {
  return request.get<ApiResponse<ProductQuestionPage>>('/admin/business/products/questions', {
    params,
  })
}

export function adminHideProductQuestion(questionId: number | string, reason?: string) {
  return request.post<ApiResponse<void>>(
    `/admin/business/products/questions/${questionId}/hide`,
    { reason },
  )
}

export function adminRestoreProductQuestion(questionId: number | string) {
  return request.post<ApiResponse<void>>(
    `/admin/business/products/questions/${questionId}/restore`,
  )
}

export function adminHideProductAnswer(answerId: number | string, reason?: string) {
  return request.post<ApiResponse<void>>(`/admin/business/products/answers/${answerId}/hide`, {
    reason,
  })
}

export function adminRestoreProductAnswer(answerId: number | string) {
  return request.post<ApiResponse<void>>(`/admin/business/products/answers/${answerId}/restore`)
}
