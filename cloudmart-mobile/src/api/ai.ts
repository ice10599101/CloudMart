import request from '@/utils/request'

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

/** 契约对齐 mall-ai ReviewSummaryVO（P1-11 修正：不止 summary，含好差评占比） */
export interface AiReviewSummary {
  productId: number
  summary: string
  positiveRatio: number
  negativeRatio: number
  totalReviews: number
}

/** 契约对齐 mall-ai SearchResultVO（语义搜索） */
export interface AiSearchResult {
  productId: number
  name: string
  price: number
  image: string
  score: number
}

/** 契约对齐 mall-ai VectorSearchResult（向量/混合搜索） */
export interface AiVectorSearchResult {
  id: number
  name: string
  description: string
  price: number
  mainImage: string
  categoryName: string
  similarityScore: number
}

export const aiApi = {
  chat: (data: { message: string; conversationId?: string }) =>
    request<AiChatResult>({ url: '/ai/chat', method: 'POST', data }),
  /** 语义搜索（向量+全文，LLM 不可用降级关键词） */
  search: (query: string) =>
    request<AiSearchResult[]>({ url: `/ai/search${buildQuery({ query })}` }),
  /** P1-11：纯向量相似度检索（KNN） */
  vectorSearch: (query: string, topK = 20) =>
    request<AiVectorSearchResult[]>({ url: `/ai/vector-search${buildQuery({ query, topK })}` }),
  /** P1-11：混合搜索（向量相似度 + ES 全文，综合排序） */
  hybridSearch: (query: string, topK = 20) =>
    request<AiVectorSearchResult[]>({ url: `/ai/hybrid-search${buildQuery({ query, topK })}` }),
  /** 评论语义摘要（优缺点+总体评价） */
  getProductReviewSummary: (productId: number) =>
    request<AiReviewSummary>({ url: `/ai/reviews/summary/${productId}` }),
}
