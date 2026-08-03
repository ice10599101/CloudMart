import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface NotificationItem {
  id: number
  userId: number
  type: string
  title: string
  content: string
  isRead: boolean
  bizId: number | null
  bizType: string | null
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

export function markAsRead(notificationId: number) {
  return request.put<ApiResponse<void>>(`/notification/notifications/${notificationId}/read`)
}

export function markAllAsRead() {
  return request.put<ApiResponse<void>>('/notification/notifications/read-all')
}
