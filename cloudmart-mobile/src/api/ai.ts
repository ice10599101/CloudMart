import request from '@/utils/request'
import type { Product, PaginatedResult } from '@/types'

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export interface AiChatResult {
  reply?: string
  content?: string
  message?: string
  conversationId?: string
}

export interface AiReviewSummary {
  summary: string
}

export const aiApi = {
  chat: (data: { message: string; conversationId?: string }) =>
    request<AiChatResult>({ url: '/ai/chat', method: 'POST', data }),
  search: (query: string) =>
    request<PaginatedResult<Product>>({ url: `/ai/search${buildQuery({ query })}` }),
  getProductReviewSummary: (productId: number) =>
    request<AiReviewSummary>({ url: `/ai/reviews/summary/${productId}` }),
}
