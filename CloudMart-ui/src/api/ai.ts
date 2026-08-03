import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface ChatRequest {
  message: string
  conversationId?: string
}

export interface ChatResponse {
  reply: string
  conversationId: string
}

export interface SearchResult {
  productId: number
  productName: string
  price: number
  image: string
  score: number
  reason: string
}

export function sendChatMessage(data: ChatRequest) {
  return request.post<ApiResponse<ChatResponse>>('/ai/chat', data)
}

export function aiSearch(query: string) {
  return request.get<ApiResponse<SearchResult[]>>('/ai/search', { params: { query } })
}

export function vectorSearch(query: string, topK = 10) {
  return request.get<ApiResponse<unknown[]>>('/ai/vector-search', { params: { query, topK } })
}

export function hybridSearch(query: string, topK = 10) {
  return request.get<ApiResponse<unknown[]>>('/ai/hybrid-search', { params: { query, topK } })
}

export function getReviewSummary(productId: number) {
  return request.get<ApiResponse<unknown>>(`/ai/reviews/summary/${productId}`)
}
