import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  getConversations,
  getMessages,
  sendMessage,
  createConversation,
  markConversationRead,
  recallMessage,
} from './chat'

describe('chat API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getConversations() calls GET /notification/conversations', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getConversations()

    expect(request.get).toHaveBeenCalledWith('/notification/conversations')
  })

  it('getMessages() calls GET with conversationId and params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getMessages(1, 100, 30)

    expect(request.get).toHaveBeenCalledWith('/notification/conversations/1/messages', {
      params: { beforeId: 100, pageSize: 30 },
    })
  })

  it('getMessages() uses default params when optional args omitted', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getMessages(1)

    expect(request.get).toHaveBeenCalledWith('/notification/conversations/1/messages', {
      params: { beforeId: undefined, pageSize: 30 },
    })
  })

  it('sendMessage() calls POST with content and type', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await sendMessage(1, 'Hello!', 'TEXT')

    expect(request.post).toHaveBeenCalledWith('/notification/conversations/1/messages', {
      content: 'Hello!',
      type: 'TEXT',
    })
  })

  it('sendMessage() defaults type to TEXT', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await sendMessage(1, 'Hi')

    expect(request.post).toHaveBeenCalledWith('/notification/conversations/1/messages', {
      content: 'Hi',
      type: 'TEXT',
    })
  })

  it('createConversation() calls POST /notification/conversations', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await createConversation(42)

    expect(request.post).toHaveBeenCalledWith('/notification/conversations', { otherUserId: 42 })
  })

  it('markConversationRead() calls PUT with conversationId', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await markConversationRead(1)

    expect(request.put).toHaveBeenCalledWith('/notification/conversations/1/read')
  })

  it('recallMessage() calls PUT with messageId', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await recallMessage(100)

    expect(request.put).toHaveBeenCalledWith('/notification/conversations/messages/100/recall')
  })
})
