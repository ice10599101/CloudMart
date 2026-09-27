import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/**
 * 通知项（P03/FE-01）：服务端对雪花 ID 按 JsSafeLong 规则可能返回字符串，
 * ID 一律按 number | string 消费，禁止 Number()/parseInt() 转换（精度丢失）。
 */
export interface NotificationItem {
  id: number | string
  userId: number | string
  type: string
  title: string
  content: string
  isRead: boolean
  bizId: number | string | null
  bizType: string | null
  /** 操作者用户 ID（谁做的互动；历史/系统通知为 null） */
  actorId: number | string | null
  createdAt: string
}

export interface UnreadCount {
  count: number
}

export function listNotifications(page = 1, pageSize = 20, type?: string) {
  return request.get<ApiResponse<NotificationItem[]>>('/notification/notifications', {
    params: { page, pageSize, type: type || undefined },
  })
}

export function getUnreadCount() {
  return request.get<ApiResponse<UnreadCount>>('/notification/notifications/unread-count')
}

export function markAsRead(notificationId: number | string) {
  return request.put<ApiResponse<void>>(`/notification/notifications/${notificationId}/read`)
}

export function markAllAsRead() {
  return request.put<ApiResponse<void>>('/notification/notifications/read-all')
}
