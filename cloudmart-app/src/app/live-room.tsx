import {
  View,
  Text,
  TouchableOpacity,
  Image,
  TextInput,
  ActivityIndicator,
  Alert,
  Animated,
  Dimensions,
  StatusBar,
} from 'react-native'
import { useState, useEffect, useRef, useCallback } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { Platform } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { liveApi } from '@/api/live'
import { wishApi } from '@/api/wish'
import { communityApi } from '@/api/community'
import GiftSection from '@/components/GiftSection'
import type { LiveWidgetData } from '@/api/wish'
import { API_BASE } from '@/utils/request'
import { storage } from '@/utils/storage'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

const SCREEN_HEIGHT = Dimensions.get('window').height
const STATUS_BAR_HEIGHT = StatusBar.currentHeight ?? 0

interface LiveRoom {
  id: number
  title: string
  coverImage: string
  anchorName: string
  anchorAvatar: string
  anchorUserId: number
  viewerCount: number
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

/** 由 API_BASE 推导 WS 基址：网关 WS 端点在 /ws（web 同源 /ws 反代，native 直连网关） */
function resolveWsBase(): string {
  if (Platform.OS === 'web') {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}/ws`
  }
  // API_BASE 形如 http://host:8090/api → ws://host:8090/ws
  return API_BASE.replace(/^http/, 'ws').replace(/\/api$/, '/ws')
}

function getCountdown(targetTime: string): string {
  const diff = new Date(targetTime).getTime() - Date.now()
  if (diff <= 0) return '00:00:00'
  const hours = Math.floor(diff / 3600000)
  const minutes = Math.floor((diff % 3600000) / 60000)
  const seconds = Math.floor((diff % 60000) / 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
}

export default function LiveRoomScreen() {
  const theme = useTheme()
  const { id } = useLocalSearchParams<{ id: string }>()
  const roomId = Number(id)
  const { user, isLoggedIn } = useAuthStore()

  const [room, setRoom] = useState<LiveRoom | null>(null)
  // 直播心愿挂件（Sprint 3.4 B10）：10s 轮询，失败保留上次值
  const [widget, setWidget] = useState<LiveWidgetData | null>(null)
  const [widgetClosed, setWidgetClosed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [isFollowing, setIsFollowing] = useState(false)
  const [commentText, setCommentText] = useState('')
  const [comments, setComments] = useState<DanmakuMessage[]>([])
  const [countdown, setCountdown] = useState('')
  const [likeCount, setLikeCount] = useState(0)
  const [wsConnected, setWsConnected] = useState(false)

  const likeScale = useRef(new Animated.Value(1)).current
  const heartAnimations = useRef<Animated.Value[]>([])
  const msgIdRef = useRef(0)
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const reconnectAttemptsRef = useRef(0)

  const pushMessage = useCallback((msg: Omit<DanmakuMessage, 'id'>) => {
    msgIdRef.current += 1
    const full = { ...msg, id: msgIdRef.current }
    setComments((prev) => [...prev.slice(-80), full])
  }, [])

  const loadRoom = useCallback(async () => {
    try {
      const res = await liveApi.getRoom(roomId)
      const data = (res.data as { data?: LiveRoom })?.data ?? null
      setRoom(data)
      setIsFollowing(!!data?.isFollowed)
    } catch {
      setRoom(null)
    } finally {
      setLoading(false)
    }
  }, [roomId])

  const enterRoom = useCallback(async () => {
    try {
      await liveApi.enterRoom(roomId)
    } catch {
      // 静默处理，不影响用户体验
    }
  }, [roomId])

  const leaveRoom = useCallback(async () => {
    try {
      await liveApi.leaveRoom(roomId)
    } catch {
      // 静默处理
    }
  }, [roomId])

  useEffect(() => {
    if (roomId) {
      loadRoom()
      enterRoom()
    }
    return () => {
      leaveRoom()
      if (reconnectRef.current) clearTimeout(reconnectRef.current)
      if (wsRef.current) {
        wsRef.current.onclose = null
        wsRef.current.close()
        wsRef.current = null
      }
    }
  }, [roomId, loadRoom, enterRoom, leaveRoom])

  /** WS 弹幕连接（对齐 Web 端 LiveRoom：/ws/live/danmaku，退避重连最多 5 次） */
  const connectSocket = useCallback(() => {
    if (!isLoggedIn || !roomId) return
    // token 由 storage 异步读取
    void storage.getItem('access_token').then((token) => {
        if (!token || wsRef.current) return
        const url = `${resolveWsBase()}/live/danmaku?roomId=${roomId}&token=${encodeURIComponent(token)}`
        const ws = new WebSocket(url)
        wsRef.current = ws

        ws.onopen = () => {
          setWsConnected(true)
          reconnectAttemptsRef.current = 0
          pushMessage({ nickname: '系统', content: '已连接到直播间', type: 'system' })
        }

        ws.onmessage = (event) => {
          if (typeof event.data !== 'string') return
          if (event.data === 'pong') return
          try {
            const data = JSON.parse(event.data) as Record<string, unknown>
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
        }

        ws.onclose = () => {
          setWsConnected(false)
          wsRef.current = null
          // 指数退避重连（对齐 Web 端）
          if (reconnectAttemptsRef.current < 5) {
            const delay = 1000 * Math.pow(2, reconnectAttemptsRef.current)
            reconnectAttemptsRef.current += 1
            reconnectRef.current = setTimeout(() => connectSocket(), delay)
          }
        }

        ws.onerror = () => setWsConnected(false)
      })
  }, [isLoggedIn, roomId, pushMessage])

  // 直播中才连弹幕 WS（hooks 在早退 return 之前，修复原 Hooks 规则违例）
  useEffect(() => {
    if (room?.status === 1) connectSocket()
  }, [room?.status, connectSocket])

  // 倒计时
  useEffect(() => {
    if (!room || room.status !== 0 || !room.startTime) return

    const update = () => setCountdown(getCountdown(room.startTime!))
    update()
    countdownRef.current = setInterval(update, 1000)
    return () => {
      if (countdownRef.current) clearInterval(countdownRef.current)
    }
  }, [room])

  // 挂件数据轮询（10s；streamerId=主播用户 ID；hooks 提前到早退 return 之前）
  useEffect(() => {
    if (!room?.anchorUserId) return
    let alive = true
    const load = () => {
      wishApi.getLiveWidget(room.anchorUserId)
        .then((res) => {
          if (alive && res.data?.success && res.data.data) setWidget(res.data.data)
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

  const handleSendComment = () => {
    const content = commentText.trim()
    if (!content) return
    if (!isLoggedIn) {
      Alert.alert('提示', '请先登录')
      return
    }
    pushMessage({ nickname: user?.nickname ?? '我', content, type: 'chat' })
    setCommentText('')
    try {
      wsRef.current?.send(JSON.stringify({ type: 'chat', username: user?.nickname ?? '匿名', content }))
    } catch {
      // 未连接时仅本地回显
    }
  }

  /** 点赞：WS 广播 + 本地计数（对齐 Web 端 sendLike） */
  const handleLike = () => {
    setLikeCount((prev) => prev + 1)

    Animated.sequence([
      Animated.timing(likeScale, { toValue: 1.4, duration: 100, useNativeDriver: true }),
      Animated.timing(likeScale, { toValue: 1, duration: 100, useNativeDriver: true }),
    ]).start()

    const heartY = new Animated.Value(0)
    heartAnimations.current.push(heartY)

    Animated.timing(heartY, { toValue: -120, duration: 1000, useNativeDriver: true }).start(() => {
      heartAnimations.current = heartAnimations.current.filter((v) => v !== heartY)
    })

    try {
      wsRef.current?.send(JSON.stringify({ type: 'like' }))
    } catch {
      // 未连接时仅本地计数
    }
  }

  const handleFollow = async () => {
    if (!isLoggedIn) {
      Alert.alert('提示', '请先登录')
      return
    }
    if (!room?.anchorUserId) return
    try {
      if (isFollowing) await communityApi.unfollowUser(room.anchorUserId)
      else await communityApi.followUser(room.anchorUserId)
      setIsFollowing(!isFollowing)
    } catch {
      Alert.alert('提示', '操作失败')
    }
  }

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: '#000000', justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    )
  }

  if (!room) {
    return (
      <View style={{ flex: 1, backgroundColor: '#000000', justifyContent: 'center', alignItems: 'center' }}>
        <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: FontSize.lg }}>直播间不存在</Text>
        <TouchableOpacity
          onPress={() => router.back()}
          style={{ marginTop: Spacing.lg, paddingHorizontal: Spacing.xl, paddingVertical: Spacing.md, borderRadius: BorderRadius.xl, borderWidth: 1, borderColor: 'rgba(255,255,255,0.3)' }}
        >
          <Text style={{ color: '#FFFFFF', fontSize: FontSize.md }}>返回</Text>
        </TouchableOpacity>
      </View>
    )
  }

  const isLive = room.status === 1
  const isScheduled = room.status === 0
  const isEnded = room.status === 2

  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      {/* 直播画面 / 状态区域 */}
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
        {isLive && (
          <View style={{ width: '100%', alignItems: 'center', position: 'relative' }}>
            <View style={{ width: 80, height: 80, borderRadius: BorderRadius.full, backgroundColor: 'rgba(255,255,255,0.1)', justifyContent: 'center', alignItems: 'center' }}>
              <Text style={{ fontSize: 36 }}>▶</Text>
            </View>
            <Text style={{ color: 'rgba(255,255,255,0.6)', fontSize: FontSize.md, marginTop: Spacing.md }}>
              直播画面
            </Text>
            {/* 直播商品入口（对齐 Web 端「直播商品/立即购买」） */}
            {room.productId ? (
              <TouchableOpacity
                activeOpacity={0.8}
                onPress={() => router.push(`/product/${room.productId}`)}
                style={{ position: 'absolute', right: Spacing.md, bottom: -Spacing.xxxl, flexDirection: 'row', alignItems: 'center', gap: Spacing.xs, backgroundColor: 'rgba(0,0,0,0.55)', paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, borderRadius: BorderRadius.xl }}
              >
                <Text style={{ fontSize: 16 }}>🛍️</Text>
                <Text style={{ color: '#FFFFFF', fontSize: FontSize.xs }}>讲解商品</Text>
                <Text style={{ color: '#FFD700', fontSize: FontSize.xs, fontWeight: '700' }}>去购买</Text>
              </TouchableOpacity>
            ) : null}
            {widget && widget.visible && !widgetClosed && (
              <View
                style={{
                  position: 'absolute',
                  top: -Spacing.lg,
                  left: Spacing.md,
                  right: Spacing.md,
                  backgroundColor: 'rgba(11,16,38,0.78)',
                  borderRadius: BorderRadius.lg,
                  padding: Spacing.sm + 4,
                  borderWidth: 1,
                  borderColor: 'rgba(255,255,255,0.18)',
                }}
              >
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 2 }}>
                  <Text style={{ fontSize: FontSize.xs, fontWeight: '600', color: '#ffd97a' }}>🌠 心愿挂件</Text>
                  <TouchableOpacity onPress={() => setWidgetClosed(true)}>
                    <Text style={{ fontSize: FontSize.xs, color: 'rgba(255,255,255,0.6)' }}>✕</Text>
                  </TouchableOpacity>
                </View>
                {widget.hasWish && widget.wishId ? (
                  <TouchableOpacity
                    activeOpacity={0.85}
                    onPress={() => router.push(`/wish-detail?id=${widget.wishId}`)}
                  >
                    <Text style={{ fontSize: FontSize.sm, color: '#ffffff' }} numberOfLines={1}>
                      {widget.title}
                    </Text>
                    <View style={{ height: 6, borderRadius: BorderRadius.full, backgroundColor: 'rgba(255,255,255,0.15)', marginTop: 4, overflow: 'hidden' }}>
                      <View
                        style={{
                          height: '100%',
                          borderRadius: BorderRadius.full,
                          backgroundColor: '#4a90d9',
                          width: `${Math.min(Math.max(widget.progressPercentage ?? 0, 0), 100)}%`,
                        }}
                      />
                    </View>
                    <Text style={{ fontSize: FontSize.xs, color: 'rgba(255,255,255,0.45)', marginTop: 2 }}>
                      {widget.progressCurrent}/{widget.progressTarget} · 打卡 {widget.checkinDays} 天 · ⭐ {widget.starlightBalance}
                    </Text>
                  </TouchableOpacity>
                ) : (
                  <TouchableOpacity
                    activeOpacity={0.85}
                    onPress={() => router.push('/wish-create')}
                  >
                    <Text style={{ fontSize: FontSize.sm, color: '#ffffff' }}>
                      主播还没许愿，点击去许愿 ✨
                    </Text>
                  </TouchableOpacity>
                )}
              </View>
            )}
          </View>
        )}

        {isScheduled && (
          <View style={{ alignItems: 'center' }}>
            <Text style={{ color: 'rgba(255,255,255,0.5)', fontSize: FontSize.xxl, marginBottom: Spacing.lg }}>
              🕐
            </Text>
            <Text style={{ color: '#FFFFFF', fontSize: FontSize.xxl, fontWeight: '700', marginBottom: Spacing.sm }}>
              直播未开始
            </Text>
            {room.startTime && (
              <>
                <Text style={{ color: 'rgba(255,255,255,0.6)', fontSize: FontSize.md, marginBottom: Spacing.lg }}>
                  距离开播还有
                </Text>
                <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
                  {countdown.split(':').map((segment, idx) => (
                    <View key={idx} style={{ flexDirection: 'row', alignItems: 'center' }}>
                      <View style={{ backgroundColor: 'rgba(255,255,255,0.15)', paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, borderRadius: BorderRadius.sm, minWidth: 44, alignItems: 'center' }}>
                        <Text style={{ color: '#FFFFFF', fontSize: FontSize.xxl, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
                          {segment}
                        </Text>
                      </View>
                      {idx < 2 && (
                        <Text style={{ color: 'rgba(255,255,255,0.5)', fontSize: FontSize.xxl, marginHorizontal: 2 }}>:</Text>
                      )}
                    </View>
                  ))}
                </View>
              </>
            )}
          </View>
        )}

        {isEnded && (
          <View style={{ alignItems: 'center' }}>
            <Text style={{ color: 'rgba(255,255,255,0.5)', fontSize: FontSize.xxl, marginBottom: Spacing.lg }}>
              🏁
            </Text>
            <Text style={{ color: '#FFFFFF', fontSize: FontSize.xxl, fontWeight: '700', marginBottom: Spacing.lg }}>
              直播已结束
            </Text>
            <TouchableOpacity
              onPress={() => Alert.alert('提示', '回放功能开发中')}
              style={{ paddingHorizontal: Spacing.xxl, paddingVertical: Spacing.md, borderRadius: BorderRadius.xl, backgroundColor: theme.primary }}
            >
              <Text style={{ color: '#FFFFFF', fontSize: FontSize.md, fontWeight: '600' }}>观看回放</Text>
            </TouchableOpacity>
          </View>
        )}
      </View>

      {/* 顶部栏 - 透明覆盖 */}
      <View style={{ position: 'absolute', top: STATUS_BAR_HEIGHT, left: 0, right: 0, flexDirection: 'row', alignItems: 'center', paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm }}>
        <TouchableOpacity
          onPress={() => router.back()}
          style={{ width: 36, height: 36, borderRadius: BorderRadius.full, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'center', alignItems: 'center' }}
        >
          <Text style={{ color: '#FFFFFF', fontSize: 18 }}>✕</Text>
        </TouchableOpacity>

        <View style={{ flex: 1, flexDirection: 'row', alignItems: 'center', marginLeft: Spacing.sm, backgroundColor: 'rgba(0,0,0,0.4)', borderRadius: BorderRadius.full, paddingRight: Spacing.md, paddingVertical: Spacing.xs }}>
          <Image
            source={{ uri: room.anchorAvatar }}
            style={{ width: 32, height: 32, borderRadius: BorderRadius.full, marginLeft: 2 }}
          />
          <Text style={{ color: '#FFFFFF', fontSize: FontSize.sm, fontWeight: '600', marginLeft: Spacing.sm, flex: 1 }} numberOfLines={1}>
            {room.anchorName}
          </Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 3 }}>
            <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: FontSize.xs }}>👁</Text>
            <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: FontSize.xs, fontVariant: ['tabular-nums'] }}>
              {room.viewerCount + likeCount}
            </Text>
          </View>
        </View>

        <TouchableOpacity
          onPress={handleFollow}
          style={{
            marginLeft: Spacing.sm,
            paddingHorizontal: Spacing.md,
            paddingVertical: Spacing.xs,
            borderRadius: BorderRadius.xl,
            backgroundColor: isFollowing ? 'rgba(255,255,255,0.2)' : theme.primary,
          }}
        >
          <Text style={{ color: '#FFFFFF', fontSize: FontSize.sm, fontWeight: '600' }}>
            {isFollowing ? '已关注' : '关注'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* 直播状态标签（连接状态随 WS 同步） */}
      {isLive && (
        <View style={{ position: 'absolute', top: STATUS_BAR_HEIGHT + 52, left: Spacing.md, flexDirection: 'row', gap: Spacing.sm }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', backgroundColor: 'rgba(255,50,50,0.8)', paddingHorizontal: Spacing.md, paddingVertical: 3, borderRadius: BorderRadius.sm, gap: 4 }}>
            <View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: '#FFFFFF' }} />
            <Text style={{ color: '#FFFFFF', fontSize: FontSize.xs, fontWeight: '600' }}>直播中</Text>
          </View>
          <View style={{ backgroundColor: 'rgba(0,0,0,0.5)', paddingHorizontal: Spacing.md, paddingVertical: 3, borderRadius: BorderRadius.sm }}>
            <Text style={{ color: wsConnected ? '#2ED573' : 'rgba(255,255,255,0.5)', fontSize: FontSize.xs, fontWeight: '600' }}>
              {wsConnected ? '已连接' : '弹幕未连接'}
            </Text>
          </View>
        </View>
      )}

      {/* 评论列表 - 下半部分半透明覆盖 */}
      <View style={{ position: 'absolute', bottom: 60, left: 0, right: 0, maxHeight: SCREEN_HEIGHT * 0.4, paddingHorizontal: Spacing.md }}>
        {comments.slice(-15).map((msg) => (
          <View
            key={msg.id}
            style={{
              flexDirection: 'row',
              alignItems: 'flex-start',
              marginBottom: Spacing.xs,
              backgroundColor: msg.type === 'gift' ? 'rgba(255,215,0,0.15)' : 'rgba(0,0,0,0.35)',
              alignSelf: 'flex-start',
              borderRadius: BorderRadius.sm,
              paddingHorizontal: Spacing.sm,
              paddingVertical: Spacing.xs,
              maxWidth: '80%',
            }}
          >
            {msg.type === 'system' ? (
              <Text style={{ color: 'rgba(255,255,255,0.4)', fontSize: FontSize.xs }}>{msg.content}</Text>
            ) : msg.type === 'gift' ? (
              <Text style={{ color: '#FFD700', fontSize: FontSize.sm, fontWeight: '600' }}>
                🎁 {msg.nickname} {msg.content}
              </Text>
            ) : (
              <>
                <Text style={{ color: theme.primary, fontSize: FontSize.sm, fontWeight: '600', marginRight: Spacing.xs }}>
                  {msg.nickname}
                </Text>
                <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: FontSize.sm, lineHeight: 18 }}>
                  {msg.content}
                </Text>
              </>
            )}
          </View>
        ))}
      </View>

      {/* 点赞飘心动画 */}
      {heartAnimations.current.map((anim, idx) => {
        const offsetX = (Math.random() - 0.5) * 40
        return (
          <Animated.View
            key={idx}
            style={{
              position: 'absolute',
              bottom: 80,
              right: 60 + offsetX,
              transform: [{ translateY: anim }],
              opacity: anim.interpolate({ inputRange: [-120, 0], outputRange: [0, 1] }),
            }}
          >
            <Text style={{ fontSize: 24 }}>❤️</Text>
          </Animated.View>
        )
      })}

      {/* 底部操作栏 */}
      <View style={{ position: 'absolute', bottom: 0, left: 0, right: 0, flexDirection: 'row', alignItems: 'center', paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, backgroundColor: 'rgba(0,0,0,0.5)', gap: Spacing.sm }}>
        <TextInput
          placeholder="说点什么..."
          placeholderTextColor="rgba(255,255,255,0.4)"
          value={commentText}
          onChangeText={setCommentText}
          onSubmitEditing={handleSendComment}
          returnKeyType="send"
          style={{
            flex: 1,
            height: 38,
            backgroundColor: 'rgba(255,255,255,0.12)',
            borderRadius: BorderRadius.full,
            paddingHorizontal: Spacing.lg,
            color: '#FFFFFF',
            fontSize: FontSize.md,
          }}
        />
        <TouchableOpacity onPress={handleSendComment} style={{ paddingHorizontal: Spacing.sm }}>
          <Text style={{ color: theme.primary, fontSize: FontSize.md, fontWeight: '600' }}>发送</Text>
        </TouchableOpacity>
        <Animated.View style={{ transform: [{ scale: likeScale }] }}>
          <TouchableOpacity onPress={handleLike} style={{ alignItems: 'center', paddingHorizontal: Spacing.sm }}>
            <Text style={{ fontSize: 24 }}>❤️</Text>
            {likeCount > 0 && (
              <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: FontSize.xs, fontVariant: ['tabular-nums'] }}>
                {likeCount}
              </Text>
            )}
          </TouchableOpacity>
        </Animated.View>
      </View>

      {/* 全站虚拟礼物（直播间场景，对齐 Web 端 GiftPickerModal） */}
      <GiftSection targetType="LIVE_ROOM" targetId={roomId} />
    </View>
  )
}
