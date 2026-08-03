import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface ChatConversation {
  id: number
  otherUserId: number
  otherUserNickname: string
  otherUserAvatar: string
  lastMessage: string
  lastMessageTime: string
  unreadCount: number
}

export interface ChatMessage {
  id: number
  conversationId: number
  senderId: number
  senderNickname: string
  senderAvatar: string
  content: string
  type: 'TEXT' | 'IMAGE' | 'PRODUCT'
  isRecalled: boolean
  createdAt: string
}

export function getConversations() {
  return request.get<ApiResponse<ChatConversation[]>>('/notification/conversations')
}

export function getMessages(conversationId: number, beforeId?: number, pageSize = 30) {
  return request.get<ApiResponse<ChatMessage[]>>(`/notification/conversations/${conversationId}/messages`, {
    params: { beforeId, pageSize },
  })
}

export function sendMessage(conversationId: number, content: string, type = 'TEXT') {
  return request.post<ApiResponse<ChatMessage>>(`/notification/conversations/${conversationId}/messages`, {
    content,
    type,
  })
}

export function createConversation(otherUserId: number) {
  return request.post<ApiResponse<ChatConversation>>('/notification/conversations', { otherUserId })
}

export function markConversationRead(conversationId: number) {
  return request.put<ApiResponse<void>>(`/notification/conversations/${conversationId}/read`)
}

export function recallMessage(messageId: number) {
  return request.put<ApiResponse<ChatMessage>>(`/notification/conversations/messages/${messageId}/recall`)
}
