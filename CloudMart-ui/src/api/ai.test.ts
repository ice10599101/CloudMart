import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { sendChatMessage, aiSearch, vectorSearch, hybridSearch, getReviewSummary } from './ai'

describe('ai API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sendChatMessage() calls POST /ai/chat with data', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await sendChatMessage({ message: 'Hello', conversationId: 'sess1' })

    expect(request.post).toHaveBeenCalledWith('/ai/chat', { message: 'Hello', conversationId: 'sess1' })
  })

  it('sendChatMessage() calls POST /ai/chat without sessionId', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await sendChatMessage({ message: 'Hello' })

    expect(request.post).toHaveBeenCalledWith('/ai/chat', { message: 'Hello' })
  })

  it('aiSearch() calls GET /ai/search with query', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await aiSearch('phone')

    expect(request.get).toHaveBeenCalledWith('/ai/search', { params: { query: 'phone' } })
  })

  it('vectorSearch() calls GET /ai/vector-search with query and topK', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await vectorSearch('phone', 5)

    expect(request.get).toHaveBeenCalledWith('/ai/vector-search', { params: { query: 'phone', topK: 5 } })
  })

  it('hybridSearch() calls GET /ai/hybrid-search with query and topK', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await hybridSearch('phone', 15)

    expect(request.get).toHaveBeenCalledWith('/ai/hybrid-search', { params: { query: 'phone', topK: 15 } })
  })

  it('getReviewSummary() calls GET /ai/reviews/summary/:productId', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getReviewSummary(42)

    expect(request.get).toHaveBeenCalledWith('/ai/reviews/summary/42')
  })
})
