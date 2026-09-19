import { useState, useEffect, useRef, useCallback } from 'react'
import { View, Text, Image, Input, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { liveApi } from '@/api/live'
import { wishApi } from '@/api/wish'
import { communityApi } from '@/api/community'
import { useAuthStore } from '@/store/auth'
import GiftSection from '@/components/GiftSection'
import type { LiveWidgetData } from '@/api/wish'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

interface RoomDetail {
  id: number
  title: string
  coverImage: string
  anchorAvatar: string
  anchorName: string
  anchorUserId: number
  viewerCount: number
  likeCount: number
  status: number
  startTime?: string
  productId?: number | null
  isFollowed?: boolean
}

interface DanmakuMessage {
  id: number
  nickname: string
  content: string
  type: 'chat' | 'system' | 'gift'
}

/** 由 API_BASE 推导 WS 基址：网关 WS 端点在 /ws（H5 走同源反代，小程序直连网关） */
function resolveWsBase(): string {
  if (process.env.TARO_ENV === 'weapp') {
    const host = (process.env.TARO_APP_API_HOST || 'http://127.0.0.1').replace(/^http/, 'ws')
    return `${host}:8090/ws`.replace(/^ws:\/\/ws/, 'ws')
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
}

function formatCount(count: number): string {
  if (count >= 10000) return `${(count / 10000).toFixed(1)}万`
  return String(count)
}

export default function LiveRoomPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const roomId = Number(Taro.getCurrentInstance().router?.params?.id || 0)
  const { user, isLoggedIn } = useAuthStore()

  const [room, setRoom] = useState<RoomDetail | null>(null)
  // 直播心愿挂件（Sprint 3.4 B10）：10s 轮询，接口失败/隐藏时保持上次值
  const [widget, setWidget] = useState<LiveWidgetData | null>(null)
  const [widgetClosed, setWidgetClosed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [isFollowed, setIsFollowed] = useState(false)
  const [likeCount, setLikeCount] = useState(0)
  const [commentText, setCommentText] = useState('')
  const [comments, setComments] = useState<DanmakuMessage[]>([])
  const [countdown, setCountdown] = useState('')
  const [wsConnected, setWsConnected] = useState(false)
  const [giftTick] = useState(0)
  const scrollViewRef = useRef('')
  const socketRef = useRef<Taro.SocketTask | null>(null)
  const msgIdRef = useRef(0)
  const reconnectRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const reconnectAttemptsRef = useRef(0)

  useEffect(() => {
    if (!roomId) {
      Taro.showToast({ title: '直播间不存在', icon: 'none' })
      return
    }
    loadRoom()
    enterRoom()
    return () => {
      if (reconnectRef.current) clearTimeout(reconnectRef.current)
      try {
        socketRef.current?.close({ code: 1000 })
      } catch {
        // 已关闭
      }
      socketRef.current = null
      liveApi.leaveRoom(roomId).catch(() => {})
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const loadRoom = async () => {
    try {
      const res = await liveApi.getRoom(roomId)
      const data = (res.data?.data || res.data) as unknown as RoomDetail
      setRoom(data)
      setIsFollowed(data?.isFollowed || false)
      setLikeCount(data?.likeCount || 0)
      if (data?.status === 0 && data?.startTime) {
        startCountdown(data.startTime)
      }
    } catch {
      Taro.showToast({ title: '加载失败', icon: 'none' })
    } finally {
      setLoading(false)
    }
  }

  const enterRoom = async () => {
    try {
      await liveApi.enterRoom(roomId)
    } catch {
      // 静默处理，不影响用户体验
    }
  }

  const startCountdown = (startTime: string) => {
    const update = () => {
      const diff = new Date(startTime).getTime() - Date.now()
      if (diff <= 0) {
        setCountdown('即将开始')
        return
      }
      const hours = Math.floor(diff / 3600000)
      const minutes = Math.floor((diff % 3600000) / 60000)
      const seconds = Math.floor((diff % 60000) / 1000)
      setCountdown(`${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`)
    }
    update()
    const timer = setInterval(update, 1000)
    return () => clearInterval(timer)
  }

  const pushMessage = useCallback((msg: Omit<DanmakuMessage, 'id'>) => {
    msgIdRef.current += 1
    const full = { ...msg, id: msgIdRef.current }
    setComments((prev) => [...prev.slice(-80), full])
    scrollViewRef.current = `comment-${full.id}`
  }, [])

  /** WS 弹幕连接（对齐 Web 端 LiveRoom：/ws/live/danmaku，心跳+退避重连） */
  const connectSocket = useCallback(() => {
    if (!isLoggedIn || !roomId) return
    const token = Taro.getStorageSync('access_token')
    if (!token) return

    const url = `${resolveWsBase()}/live/danmaku?roomId=${roomId}&token=${encodeURIComponent(token)}`
    try {
      // Taro.connectSocket 返回 Promise<SocketTask>，事件绑定须在 resolve 后进行
      void Taro.connectSocket({ url, fail: () => setWsConnected(false) }).then((socket) => {
        socketRef.current = socket

        socket.onOpen(() => {
        setWsConnected(true)
        reconnectAttemptsRef.current = 0
        pushMessage({ nickname: '系统', content: '已连接到直播间', type: 'system' })
      })

      socket.onMessage((res) => {
        if (typeof res.data !== 'string') return
        if (res.data === 'pong') return
        try {
          const data = JSON.parse(res.data) as Record<string, unknown>
          if (data.type === 'like') {
            setLikeCount((prev) => prev + 1)
            return
          }
          if (data.type === 'system') {
            pushMessage({ nickname: '系统', content: String(data.content ?? ''), type: 'system' })
            return
          }
          if (data.type === 'GIFT') {
            pushMessage({
              nickname: String(data.senderNickname ?? '神秘人'),
              content: `送出 ${data.giftName ?? '礼物'} ×${data.count ?? 1}${data.message ? `：“${data.message}”` : ''}`,
              type: 'gift',
            })
            return
          }
          pushMessage({
            nickname: String(data.nickname ?? data.username ?? '匿名'),
            content: String(data.content ?? ''),
            type: 'chat',
          })
        } catch {
          // 非 JSON 消息忽略
        }
      })

      socket.onClose(() => {
        setWsConnected(false)
        // 指数退避重连（对齐 Web 端，最多 5 次）
        if (reconnectAttemptsRef.current < 5) {
          const delay = 1000 * Math.pow(2, reconnectAttemptsRef.current)
          reconnectAttemptsRef.current += 1
          reconnectRef.current = setTimeout(() => connectSocket(), delay)
        }
      })

        socket.onError(() => setWsConnected(false))
      })
    } catch {
      setWsConnected(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn, roomId, pushMessage])

  useEffect(() => {
    if (room?.status === 1) connectSocket()
  }, [room?.status, connectSocket])

  const handleBack = () => {
    Taro.navigateBack()
  }

  /** 关注主播（真实调接口，对齐 Web 端） */
  const handleFollow = async () => {
    if (!isLoggedIn) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    if (!room?.anchorUserId) return
    try {
      if (isFollowed) {
        await communityApi.unfollowUser(room.anchorUserId)
      } else {
        await communityApi.followUser(room.anchorUserId)
      }
      setIsFollowed(!isFollowed)
      Taro.showToast({ title: isFollowed ? '已取消关注' : '关注成功', icon: 'none' })
    } catch {
      Taro.showToast({ title: '操作失败', icon: 'none' })
    }
  }

  /** 点赞：WS 广播 + 本地计数（对齐 Web 端 sendLike） */
  const handleLike = () => {
    setLikeCount((prev) => prev + 1)
    try {
      socketRef.current?.send({ data: JSON.stringify({ type: 'like' }) })
    } catch {
      // 未连接时仅本地计数
    }
  }

  /** 发送弹幕：WS 广播 + 本地回显（对齐 Web 端 sendMessage） */
  const handleSendComment = () => {
    const content = commentText.trim()
    if (!content) return
    if (!isLoggedIn) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    pushMessage({ nickname: user?.nickname ?? '我', content, type: 'chat' })
    setCommentText('')
    try {
      socketRef.current?.send({
        data: JSON.stringify({ type: 'chat', username: user?.nickname ?? '匿名', content }),
      })
    } catch {
      // 未连接时仅本地回显
    }
  }

  // 挂件数据轮询（10s；服务端缓存 10s；streamerId=主播用户 ID）
  useEffect(() => {
    if (!room?.anchorUserId) return
    let alive = true
    const load = () => {
      wishApi.getLiveWidget(room.anchorUserId)
        .then((res) => {
          if (alive && res.data.success && res.data.data) setWidget(res.data.data)
        })
        .catch(() => undefined)
    }
    load()
    const timer = setInterval(load, 10_000)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [room?.anchorUserId])

  const renderCenter = () => {
    if (!room) return null
    if (room.status === 1) {
      return (
        <View className={styles.liveArea}>
          <View className={styles.livePlaceholder}>
            <Text className={styles.playIcon}>▶</Text>
            <Text className={styles.liveLabel}>直播画面</Text>
          </View>
          <View className={styles.liveStatusTag}>
            <Text className={styles.liveStatusText}>{wsConnected ? '已连接' : '弹幕未连接'}</Text>
          </View>
          {widget && widget.visible && !widgetClosed && (
            <View className={styles.wishWidget}>
              <View className={styles.wishWidgetHeader}>
                <Text className={styles.wishWidgetTitle}>🌠 心愿挂件</Text>
                <Text className={styles.wishWidgetClose} onClick={() => setWidgetClosed(true)}>✕</Text>
              </View>
              {widget.hasWish && widget.wishId ? (
                <View
                  onClick={() => Taro.navigateTo({ url: `/pages/wishDetail/index?id=${widget.wishId}` })}
                >
                  <Text className={styles.wishWidgetText} numberOfLines={1}>{widget.title}</Text>
                  <View className={styles.wishWidgetBar}>
                    <View
                      className={styles.wishWidgetBarFill}
                      style={{ width: `${Math.min(Math.max(widget.progressPercentage ?? 0, 0), 100)}%` }}
                    />
                  </View>
                  <Text className={styles.wishWidgetMeta}>
                    {widget.progressCurrent}/{widget.progressTarget} · 打卡 {widget.checkinDays} 天 · ⭐ {widget.starlightBalance}
                  </Text>
                </View>
              ) : (
                <Text
                  className={styles.wishWidgetText}
                  onClick={() => Taro.navigateTo({ url: '/pages/wishCreate/index' })}
                >
                  主播还没许愿，点击去许愿 ✨
                </Text>
              )}
            </View>
          )}
          {widget && !widget.visible && widgetClosed && (
            <View className={styles.wishWidgetClosedBtn} onClick={() => setWidgetClosed(false)}>
              <Text className={styles.wishWidgetText}>🌠 心愿</Text>
            </View>
          )}
          {/* 直播商品侧栏（对齐 Web 端「直播商品/立即购买」） */}
          {room.productId ? (
            <View
              className={styles.liveProductCard}
              onClick={() => Taro.navigateTo({ url: `/pages/productDetail/index?id=${room.productId}` })}
            >
              <Text className={styles.liveProductIcon}>🛍️</Text>
              <Text className={styles.liveProductText}>讲解商品</Text>
              <Text className={styles.liveProductCta}>去购买</Text>
            </View>
          ) : null}
        </View>
      )
    }
    if (room.status === 0) {
      return (
        <View className={styles.liveArea}>
          <View className={styles.scheduledPlaceholder}>
            <Text className={styles.countdownLabel}>直播未开始</Text>
            {countdown && <Text className={styles.countdown}>{countdown}</Text>}
          </View>
        </View>
      )
    }
    return (
      <View className={styles.liveArea}>
        <View className={styles.endedPlaceholder}>
          <Text className={styles.endedLabel}>直播已结束</Text>
        </View>
      </View>
    )
  }

  if (loading) {
    return (
      <View data-theme={dataTheme} className={styles.page} style={{ ...themeStyle, backgroundColor: '#000000' }}>
        <View className={styles.loadingWrap}>
          <View className={styles.spinner} />
          <Text className={styles.loadingText}>加载中...</Text>
        </View>
      </View>
    )
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={{ ...themeStyle, backgroundColor: '#000000' }}>
      {/* Top Bar */}
      <View className={styles.topBar}>
        <View className={styles.backBtn} onClick={handleBack}>
          <Text className={styles.backIcon}>←</Text>
        </View>
        <Image className={styles.anchorAvatar} src={room?.anchorAvatar || ''} />
        <Text className={styles.anchorName}>{room?.anchorName || '主播'}</Text>
        <View className={styles.viewerTag}>
          <Text className={styles.viewerTagText}>👁 {formatCount((room?.viewerCount || 0) + likeCount)}</Text>
        </View>
        <View
          className={`${styles.followBtn} ${isFollowed ? styles.followBtnActive : ''}`}
          onClick={handleFollow}
        >
          <Text className={styles.followBtnText}>{isFollowed ? '已关注' : '+ 关注'}</Text>
        </View>
      </View>

      {/* Center - Live Area */}
      {renderCenter()}

      {/* Comment List Overlay */}
      <View className={styles.commentOverlay}>
        <ScrollView
          scrollY
          className={styles.commentList}
          scrollIntoView={scrollViewRef.current}
          scrollWithAnimation
        >
          {comments.map((c) => (
            <View key={c.id} id={`comment-${c.id}`} className={styles.commentItem}>
              {c.type === 'gift' ? (
                <Text className={styles.commentGift}>🎁 {c.nickname} {c.content}</Text>
              ) : c.type === 'system' ? (
                <Text className={styles.commentSystem}>{c.content}</Text>
              ) : (
                <>
                  <Text className={styles.commentNickname}>{c.nickname}：</Text>
                  <Text className={styles.commentContent}>{c.content}</Text>
                </>
              )}
            </View>
          ))}
        </ScrollView>
      </View>

      {/* Bottom Bar */}
      <View className={styles.bottomBar}>
        <View className={styles.inputWrap}>
          <Input
            className={styles.commentInput}
            placeholder='说点什么...'
            placeholderStyle='color: rgba(255,255,255,0.4)'
            value={commentText}
            onInput={(e) => setCommentText(e.detail.value)}
            confirmType='send'
            onConfirm={handleSendComment}
          />
        </View>
        <View className={styles.sendBtn} onClick={handleSendComment}>
          <Text className={styles.sendBtnText}>发送</Text>
        </View>
        <View className={styles.giftEntry} onClick={() => (isLoggedIn ? undefined : Taro.showToast({ title: '请先登录', icon: 'none' }))}>
          <Text className={styles.giftIcon}>🎁</Text>
        </View>
        <View className={styles.likeBtn} onClick={handleLike}>
          <Text className={styles.likeIcon}>❤️</Text>
          <Text className={styles.likeCount}>{formatCount(likeCount)}</Text>
        </View>
      </View>

      {/* 全站虚拟礼物（直播间场景，对齐 Web 端 GiftPickerModal） */}
      <GiftSection targetType='LIVE_ROOM' targetId={roomId} refreshTick={giftTick} />
    </View>
  )
}
