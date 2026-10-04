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
  /** T20：客户端幂等键（乐观消息持有，重试复用同键） */
  clientMessageId?: string
  /** T20：发送失败标记（失败消息可点击重发，重发复用同幂等键） */
  sendFailed?: boolean
}

export function getConversations() {
  return request.get<ApiResponse<ChatConversation[]>>('/notification/conversations')
}

export function getMessages(conversationId: number, beforeId?: number, pageSize = 30) {
  return request.get<ApiResponse<ChatMessage[]>>(`/notification/conversations/${conversationId}/messages`, {
    params: { beforeId, pageSize },
  })
}

/** T20：幂等键随消息意图生成（进入发送队列时），重试复用同键而非每次调用新键 */
export function newClientMessageId(): string {
  return crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
}

export function sendMessage(
  conversationId: number,
  content: string,
  type = 'TEXT',
  clientMessageId?: string,
) {
  // N01/T20：同键重发返回原消息（同键异内容 409），不产生重复
  return request.post<ApiResponse<ChatMessage>>(`/notification/conversations/${conversationId}/messages`, {
    content,
    type,
    clientMessageId: clientMessageId ?? newClientMessageId(),
  })
}

export function createConversation(otherUserId: number | string) {
  return request.post<ApiResponse<ChatConversation>>('/notification/conversations', { otherUserId })
}

export function markConversationRead(conversationId: number) {
  return request.put<ApiResponse<void>>(`/notification/conversations/${conversationId}/read`)
}

export function recallMessage(messageId: number) {
  return request.put<ApiResponse<ChatMessage>>(`/notification/conversations/messages/${messageId}/recall`)
}
