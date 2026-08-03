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
import { useTheme } from '@/hooks/use-theme-context'
import { liveApi } from '@/api/live'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

const SCREEN_HEIGHT = Dimensions.get('window').height
const STATUS_BAR_HEIGHT = StatusBar.currentHeight ?? 0

interface LiveRoom {
  id: number
  title: string
  coverImage: string
  anchorName: string
  anchorAvatar: string
  viewerCount: number
  status: number
  startTime?: string
}

interface ChatMessage {
  id: number
  nickname: string
  content: string
  isAnchor?: boolean
}

const SAMPLE_COMMENTS: ChatMessage[] = [
  { id: 1, nickname: '小明', content: '主播好厉害！' },
  { id: 2, nickname: '阿花', content: '这个商品多少钱？' },
  { id: 3, nickname: '大壮', content: '来了来了，支持主播' },
  { id: 4, nickname: '小红', content: '能再展示一下吗？' },
  { id: 5, nickname: '老王', content: '已下单，坐等收货' },
]

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

  const [room, setRoom] = useState<LiveRoom | null>(null)
  const [loading, setLoading] = useState(true)
  const [isFollowing, setIsFollowing] = useState(false)
  const [commentText, setCommentText] = useState('')
  const [comments, setComments] = useState<ChatMessage[]>(SAMPLE_COMMENTS)
  const [countdown, setCountdown] = useState('')
  const [likeCount, setLikeCount] = useState(0)

  const likeScale = useRef(new Animated.Value(1)).current
  const heartAnimations = useRef<Animated.Value[]>([])
  const commentIdRef = useRef(SAMPLE_COMMENTS.length + 1)
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const loadRoom = useCallback(async () => {
    try {
      const res = await liveApi.getRoom(roomId)
      setRoom((res.data as any)?.data ?? null)
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
    }
  }, [roomId, loadRoom, enterRoom, leaveRoom])

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

  // 模拟弹幕
  useEffect(() => {
    if (!room || room.status !== 1) return

    const fakeNicknames = ['观众A', '观众B', '观众C', '观众D', '观众E']
    const fakeMessages = [
      '主播加油！',
      '太棒了',
      '666',
      '好看',
      '买买买',
      '已关注',
      '冲冲冲',
      '好物推荐',
    ]

    const timer = setInterval(() => {
      const newComment: ChatMessage = {
        id: commentIdRef.current++,
        nickname: fakeNicknames[Math.floor(Math.random() * fakeNicknames.length)],
        content: fakeMessages[Math.floor(Math.random() * fakeMessages.length)],
      }
      setComments((prev) => [...prev.slice(-30), newComment])
    }, 3000)

    return () => clearInterval(timer)
  }, [room])

  const handleSendComment = () => {
    if (!commentText.trim()) return
    const newComment: ChatMessage = {
      id: commentIdRef.current++,
      nickname: '我',
      content: commentText.trim(),
    }
    setComments((prev) => [...prev, newComment])
    setCommentText('')
  }

  const handleLike = () => {
    setLikeCount((prev) => prev + 1)

    Animated.sequence([
      Animated.timing(likeScale, { toValue: 1.4, duration: 100, useNativeDriver: true }),
      Animated.timing(likeScale, { toValue: 1, duration: 100, useNativeDriver: true }),
    ]).start()

    const heartY = new Animated.Value(0)
    const heartOpacity = new Animated.Value(1)
    heartAnimations.current.push(heartY)

    Animated.parallel([
      Animated.timing(heartY, { toValue: -120, duration: 1000, useNativeDriver: true }),
      Animated.timing(heartOpacity, { toValue: 0, duration: 1000, useNativeDriver: true }),
    ]).start(() => {
      heartAnimations.current = heartAnimations.current.filter((v) => v !== heartY)
    })
  }

  const handleShare = () => {
    Alert.alert('分享', '复制直播间链接分享给好友', [
      { text: '取消', style: 'cancel' },
      { text: '复制链接', onPress: () => Alert.alert('提示', '链接已复制') },
    ])
  }

  const handleFollow = () => {
    setIsFollowing((prev) => !prev)
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
          <View style={{ alignItems: 'center' }}>
            <View style={{ width: 80, height: 80, borderRadius: BorderRadius.full, backgroundColor: 'rgba(255,255,255,0.1)', justifyContent: 'center', alignItems: 'center' }}>
              <Text style={{ fontSize: 36 }}>▶</Text>
            </View>
            <Text style={{ color: 'rgba(255,255,255,0.6)', fontSize: FontSize.md, marginTop: Spacing.md }}>
              直播画面
            </Text>
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
              {room.viewerCount}
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

      {/* 直播状态标签 */}
      {isLive && (
        <View style={{ position: 'absolute', top: STATUS_BAR_HEIGHT + 52, left: Spacing.md }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', backgroundColor: 'rgba(255,50,50,0.8)', paddingHorizontal: Spacing.md, paddingVertical: 3, borderRadius: BorderRadius.sm, gap: 4 }}>
            <View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: '#FFFFFF' }} />
            <Text style={{ color: '#FFFFFF', fontSize: FontSize.xs, fontWeight: '600' }}>直播中</Text>
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
              backgroundColor: msg.isAnchor ? 'rgba(255,215,0,0.15)' : 'rgba(0,0,0,0.35)',
              alignSelf: 'flex-start',
              borderRadius: BorderRadius.sm,
              paddingHorizontal: Spacing.sm,
              paddingVertical: Spacing.xs,
              maxWidth: '80%',
            }}
          >
            <Text style={{ color: msg.isAnchor ? '#FFD700' : theme.primary, fontSize: FontSize.sm, fontWeight: '600', marginRight: Spacing.xs }}>
              {msg.nickname}
            </Text>
            <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: FontSize.sm, lineHeight: 18 }}>
              {msg.content}
            </Text>
          </View>
        ))}
      </View>

      {/* 点赞飘心动画 */}
      {heartAnimations.current.map((_, idx) => {
        const offsetX = (Math.random() - 0.5) * 40
        return (
          <Animated.View
            key={idx}
            style={{
              position: 'absolute',
              bottom: 80,
              right: 60 + offsetX,
              transform: [{ translateY: heartAnimations.current[idx] || new Animated.Value(0) }],
              opacity: new Animated.Value(1),
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
        <TouchableOpacity onPress={handleShare} style={{ paddingHorizontal: Spacing.sm }}>
          <Text style={{ fontSize: 24 }}>🔗</Text>
        </TouchableOpacity>
      </View>
    </View>
  )
}
