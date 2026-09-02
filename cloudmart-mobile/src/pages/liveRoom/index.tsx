import { useState, useEffect, useRef } from 'react'
import { View, Text, Image, Input, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { liveApi } from '@/api/live'
import { wishApi } from '@/api/wish'
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
  isFollowed?: boolean
}

interface Comment {
  id: number
  nickname: string
  content: string
}

const MOCK_COMMENTS: Comment[] = [
  { id: 1, nickname: '小明', content: '来了来了！' },
  { id: 2, nickname: '阿花', content: '主播好漂亮' },
  { id: 3, nickname: '大壮', content: '这个商品多少钱？' },
  { id: 4, nickname: '小红', content: '666' },
  { id: 5, nickname: '老王', content: '能再介绍一下吗' },
]

function formatCount(count: number): string {
  if (count >= 10000) return `${(count / 10000).toFixed(1)}万`
  return String(count)
}

export default function LiveRoomPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const roomId = Number(Taro.getCurrentInstance().router?.params?.id || 0)

  const [room, setRoom] = useState<RoomDetail | null>(null)
  // 直播心愿挂件（Sprint 3.4 B10）：10s 轮询，接口失败/隐藏时保持上次值
  const [widget, setWidget] = useState<LiveWidgetData | null>(null)
  const [widgetClosed, setWidgetClosed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [isFollowed, setIsFollowed] = useState(false)
  const [likeCount, setLikeCount] = useState(0)
  const [commentText, setCommentText] = useState('')
  const [comments, setComments] = useState<Comment[]>(MOCK_COMMENTS)
  const [countdown, setCountdown] = useState('')
  const scrollViewRef = useRef('')

  useEffect(() => {
    if (!roomId) {
      Taro.showToast({ title: '直播间不存在', icon: 'none' })
      return
    }
    loadRoom()
    enterRoom()
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

  const handleBack = () => {
    Taro.navigateBack()
  }

  const handleFollow = () => {
    setIsFollowed(!isFollowed)
    Taro.showToast({ title: isFollowed ? '已取消关注' : '关注成功', icon: 'none' })
  }

  const handleLike = () => {
    setLikeCount(likeCount + 1)
  }

  const handleSendComment = () => {
    if (!commentText.trim()) return
    const newComment: Comment = {
      id: Date.now(),
      nickname: '我',
      content: commentText.trim(),
    }
    setComments([...comments, newComment])
    setCommentText('')
    scrollViewRef.current = `comment-${newComment.id}`
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
          <Text className={styles.viewerTagText}>👁 {formatCount(room?.viewerCount || 0)}</Text>
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
              <Text className={styles.commentNickname}>{c.nickname}：</Text>
              <Text className={styles.commentContent}>{c.content}</Text>
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
        <View className={styles.likeBtn} onClick={handleLike}>
          <Text className={styles.likeIcon}>❤️</Text>
          <Text className={styles.likeCount}>{formatCount(likeCount)}</Text>
        </View>
      </View>
    </View>
  )
}
