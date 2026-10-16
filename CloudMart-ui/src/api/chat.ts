import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface ChatConversation {
  /** BASE-01：会话/用户 ID 保持字符串（后端 Long 统一十进制字符串序列化） */
  id: string
  otherUserId: string
  otherUserNickname: string
  otherUserAvatar: string
  lastMessage: string
  lastMessageTime: string
  unreadCount: number
}

export interface ChatMessage {
  id: string
  conversationId: string
  senderId: string
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

export function getMessages(conversationId: number | string, beforeId?: number | string, pageSize = 30) {
  return request.get<ApiResponse<ChatMessage[]>>(`/notification/conversations/${conversationId}/messages`, {
    params: { beforeId, pageSize },
  })
}

/** T20：幂等键随消息意图生成（进入发送队列时），重试复用同键而非每次调用新键 */
export function newClientMessageId(): string {
  return crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
}

export function sendMessage(
  conversationId: number | string,
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

export function markConversationRead(conversationId: number | string) {
  return request.put<ApiResponse<void>>(`/notification/conversations/${conversationId}/read`)
}

export function recallMessage(messageId: number | string) {
  return request.put<ApiResponse<ChatMessage>>(`/notification/conversations/messages/${messageId}/recall`)
}
