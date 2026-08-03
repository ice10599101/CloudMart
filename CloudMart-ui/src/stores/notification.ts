import { create } from 'zustand'
import { getUnreadCount, listNotifications, type NotificationItem } from '@/api/notification'

interface NotificationState {
  unreadCount: number
  ws: WebSocket | null
  notifications: NotificationItem[]
  connect: (token: string) => void
  disconnect: () => void
  fetchUnreadCount: () => Promise<void>
  incrementUnread: () => void
  addNotification: (item: NotificationItem) => void
  resetUnread: () => void
  setNotifications: (items: NotificationItem[]) => void
}

const WS_BASE = (() => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.hostname
  const port = (typeof process !== 'undefined' && process.env?.WS_PORT) || '8090'
  return `${protocol}//${host}:${port}`
})()

export const useNotificationStore = create<NotificationState>((set, get) => ({
  unreadCount: 0,
  ws: null,
  notifications: [],

  connect: (token: string) => {
    const existing = get().ws
    if (existing && (existing.readyState === WebSocket.OPEN || existing.readyState === WebSocket.CONNECTING)) {
      return
    }

    const url = `${WS_BASE}/ws/notifications?token=${encodeURIComponent(token)}`
    const socket = new WebSocket(url)

    socket.onopen = () => {
      set({ ws: socket })
    }

    socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)

        if (data.type === 'UNREAD_COUNT' && typeof data.count === 'number') {
          set({ unreadCount: data.count })
          return
        }

        if (data.id && data.type && data.content) {
          const notification: NotificationItem = {
            id: data.id,
            userId: data.userId ?? 0,
            type: data.type,
            title: data.title ?? '',
            content: data.content,
            isRead: false,
            bizId: data.bizId ?? null,
            bizType: data.bizType ?? null,
            createdAt: data.createdAt ?? new Date().toISOString(),
          }
          set((state) => ({
            notifications: [notification, ...state.notifications],
            unreadCount: state.unreadCount + 1,
          }))
        }
      } catch {
        // ignore non-JSON messages
      }
    }

    socket.onclose = () => {
      set({ ws: null })
      if (token && get().ws === null) {
        setTimeout(() => {
          if (get().ws === null) {
            get().connect(token)
          }
        }, 5000)
      }
    }

    socket.onerror = () => {
      socket.close()
    }
  },

  disconnect: () => {
    const socket = get().ws
    if (socket) {
      socket.onclose = null
      socket.close()
      set({ ws: null })
    }
  },

  fetchUnreadCount: async () => {
    try {
      const res = await getUnreadCount()
      set({ unreadCount: res.data.data?.count ?? 0 })
    } catch {
      // fallback silently
    }
  },

  incrementUnread: () => {
    set((state) => ({ unreadCount: state.unreadCount + 1 }))
  },

  addNotification: (item: NotificationItem) => {
    set((state) => ({
      notifications: [item, ...state.notifications],
      unreadCount: state.unreadCount + 1,
    }))
  },

  resetUnread: () => {
    set({ unreadCount: 0 })
  },

  setNotifications: (items: NotificationItem[]) => {
    set({ notifications: items })
  },
}))
