import Taro from '@tarojs/taro'

/**
 * P1-10：通知 WebSocket（Taro 端）。
 *
 * 网关路由 /ws/notifications/**（lb:ws://mall-notification，无 StripPrefix），
 * 服务端 WebSocketHandler 挂在 /ws/notifications，token 经 query 传递（对齐 Web 端）。
 * 单例连接 + 多页面订阅（notifications/message 页共享同一条连接）；
 * 心跳 30s（小于服务端空闲阈值）；指数退避重连最多 5 次，失败由页面
 * 自身的拉取兜底（断线降级 unread-count 轮询语义不变）。
 *
 * 小程序 WS 并发上限 5 条：直播间连接前应先 closeNotificationSocket()
 * 释放名额（pages/liveRoom 已接入）。
 */

/** 解析 WS 基址：weapp 用 TARO_APP_API_HOST（scheme→ws，缺端口补 :8090）；H5 走同源 /ws 代理 */
export function resolveWsBase(): string {
  if (process.env.TARO_ENV === 'weapp') {
    const configured = process.env.TARO_APP_API_HOST?.trim()
    if (configured) {
      const host = configured.replace(/\/+$/, '')
      const withPort = /:\d+$/.test(host) ? host : `${host}:8090`
      return withPort.replace(/^http/, 'ws')
    }
    return 'ws://127.0.0.1:8090'
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}`
}

export interface WsNotification {
  id: number | string
  type: string
  title?: string
  content: string
  bizId?: number | null
  bizType?: string | null
  actorId?: number | null
  createdAt?: string
}

type NotificationListener = (notification: WsNotification) => void
type UnreadListener = (count: number) => void

const HEARTBEAT_MS = 30_000
const RECONNECT_BASE_MS = 5_000
const RECONNECT_MAX_ATTEMPTS = 5

let socketTask: Taro.SocketTask | null = null
let connecting = false
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectAttempts = 0
let intentionallyClosed = false

const notificationListeners = new Set<NotificationListener>()
const unreadListeners = new Set<UnreadListener>()

function clearTimers() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function scheduleReconnect() {
  if (intentionallyClosed || reconnectAttempts >= RECONNECT_MAX_ATTEMPTS) return
  const delay = RECONNECT_BASE_MS * Math.pow(2, reconnectAttempts)
  reconnectAttempts += 1
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    ensureNotificationSocket()
  }, delay)
}

/** 建立通知 WS 连接（已连接/连接中/未登录时为幂等 no-op） */
export function ensureNotificationSocket(): void {
  const token = Taro.getStorageSync('access_token')
  if (!token || connecting) return
  if (socketTask) return // 单例：已建立则只增订阅

  connecting = true
  intentionallyClosed = false
  const url = `${resolveWsBase()}/ws/notifications?token=${encodeURIComponent(token)}`

  try {
    void Taro.connectSocket({ url, fail: () => {
      connecting = false
      scheduleReconnect()
    } }).then((socket) => {
      socketTask = socket
      connecting = false

      socket.onOpen(() => {
        reconnectAttempts = 0
        // 心跳：服务端以 ping/pong 判活（对齐 Web 端）
        heartbeatTimer = setInterval(() => {
          try {
            socket.send({ data: 'ping' })
          } catch {
            // 发送失败交给 onClose 重连
          }
        }, HEARTBEAT_MS)
      })

      socket.onMessage((res) => {
        if (typeof res.data !== 'string' || res.data === 'pong') return
        try {
          const data = JSON.parse(res.data) as Record<string, unknown>
          if (data.type === 'UNREAD_COUNT' && typeof data.count === 'number') {
            unreadListeners.forEach((cb) => cb(data.count as number))
            return
          }
          if (data.id && data.type && data.content) {
            const notification = data as unknown as WsNotification
            notificationListeners.forEach((cb) => cb(notification))
          }
        } catch {
          // 非 JSON 消息忽略
        }
      })

      socket.onClose(() => {
        socketTask = null
        clearTimers()
        scheduleReconnect()
      })

      socket.onError(() => {
        // onClose 会随后触发统一重连；此处仅防悬挂
        connecting = false
      })
    }).catch(() => {
      connecting = false
      scheduleReconnect()
    })
  } catch {
    connecting = false
    scheduleReconnect()
  }
}

/** 主动关闭（小程序 WS 上限 5 条：进直播间等场景先释放名额） */
export function closeNotificationSocket(): void {
  intentionallyClosed = true
  clearTimers()
  if (socketTask) {
    try {
      socketTask.close({})
    } catch {
      // 已断开时忽略
    }
    socketTask = null
  }
}

/** 订阅实时通知（返回退订函数；首个订阅者自动建连，最后一个退订者不主动断开以便复用） */
export function subscribeNotifications(listener: NotificationListener): () => void {
  notificationListeners.add(listener)
  ensureNotificationSocket()
  return () => {
    notificationListeners.delete(listener)
  }
}

/** 订阅未读数推送 */
export function subscribeUnreadCount(listener: UnreadListener): () => void {
  unreadListeners.add(listener)
  ensureNotificationSocket()
  return () => {
    unreadListeners.delete(listener)
  }
}
