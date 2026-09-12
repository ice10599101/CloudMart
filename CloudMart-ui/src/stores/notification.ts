import { create } from 'zustand'
import { getUnreadCount, type NotificationItem } from '@/api/notification'
import request from '@/utils/request'

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
// 同源地址：dev 走 .umirc.ts 的 /ws 代理转发到 Gateway（与 /api 同链路），
// 生产由部署层 nginx 等反代 /ws；禁止直连 {hostname}:8090（Gateway 不在同机时必然失败）
const WS_BASE = (() => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}`
})()

/** 心跳间隔：需小于网关/服务端空闲断连阈值，避免频繁重连触发 UNREAD_COUNT 重复推送 */
const WS_HEARTBEAT_MS = 30_000

/** 重连退避：连续失败（如 token 过期未刷新）时逐步拉长间隔，避免握手失败日志刷屏 */
const WS_RECONNECT_BASE_MS = 5_000
const WS_RECONNECT_MAX_MS = 60_000
let reconnectAttempts = 0

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
    const heartbeat = setInterval(() => {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send('ping')
      }
    }, WS_HEARTBEAT_MS)
    socket.addEventListener('close', () => clearInterval(heartbeat))

    socket.onopen = () => {
      reconnectAttempts = 0
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
        const delay = Math.min(WS_RECONNECT_BASE_MS * 2 ** reconnectAttempts, WS_RECONNECT_MAX_MS)
        reconnectAttempts += 1
        setTimeout(async () => {
          if (get().ws !== null) return
          // 重连前先走一次轻量鉴权请求：access token 过期时 axios 拦截器会自动 refresh
          // 并同步 localStorage/store，然后才能用新 token 完成握手（过期 token 握手必被拒）
          try {
            await request.get('/user/users/me')
          } catch {
            // 刷新也失败（重新登录场景）→ 本轮不重连，等下次 API 401 流程
          }
          if (get().ws !== null) return
          const latest = localStorage.getItem('access_token')
          if (latest) get().connect(latest)
        }, delay)
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
