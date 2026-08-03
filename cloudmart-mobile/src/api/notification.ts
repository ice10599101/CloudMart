import request from '@/utils/request'
import type { Notification, Conversation, ChatMessage, PaginatedResult } from '@/types'

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export const notificationApi = {
  getList: (params?: { type?: number; page?: number; pageSize?: number }) =>
    request<PaginatedResult<Notification>>({ url: `/notification/notifications${buildQuery(params as Record<string, unknown>)}` }),
  getUnreadCount: () => request<number>({ url: '/notification/notifications/unread-count' }),
  markRead: (id: number) => request<void>({ url: `/notification/notifications/${id}/read`, method: 'PUT' }),
  markAllRead: () => request<void>({ url: '/notification/notifications/read-all', method: 'PUT' }),
  getConversations: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<Conversation>>({ url: `/notification/conversations${buildQuery(params as Record<string, unknown>)}` }),
  getMessages: (conversationId: number, params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<ChatMessage>>({ url: `/notification/conversations/${conversationId}/messages${buildQuery(params as Record<string, unknown>)}` }),
  sendMessage: (conversationId: number, data: { content: string; type?: number }) =>
    request<ChatMessage>({ url: `/notification/conversations/${conversationId}/messages`, method: 'POST', data }),
  createConversation: (data: { otherUserId: number }) =>
    request<Conversation>({ url: '/notification/conversations', method: 'POST', data }),
  markConversationRead: (conversationId: number) =>
    request<void>({ url: `/notification/conversations/${conversationId}/read`, method: 'PUT' }),
  recallMessage: (messageId: number) =>
    request<void>({ url: `/notification/conversations/messages/${messageId}/recall`, method: 'PUT' }),
}
