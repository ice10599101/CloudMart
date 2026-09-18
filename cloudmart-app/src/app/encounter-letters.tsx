import { useState, useEffect, useCallback, useRef } from 'react'
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
  Switch,
  Modal,
  TextInput,
  Image,
  useWindowDimensions,
} from 'react-native'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import type { DriftBottleCandidateWish, DriftBottleCommentItem, DriftBottleItem } from '@/types'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import { RichTextEditor, type RichTextEditorRef } from '@/components/RichTextEditor'
import RichHtml from '@/components/RichHtml'

const ROLE_LABELS: Record<DriftBottleItem['role'], string> = {
  THROWN: '我投出的',
  PICKED: '我捞到的',
}

const STATUS_LABELS: Record<DriftBottleItem['status'], string> = {
  FLOATING: '漂流中',
  PICKED: '已被捞起',
  RETURNED: '被扔回海里',
}

const COMMENT_PAGE_SIZE = 10
const COMMENT_CONTENT_MAX = 500

/** HTML 转纯文本并压缩空白，用于投瓶判空（空 HTML 如 <p></p> 必须拦截） */
function stripHtml(html: string): string {
  return (html || '')
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

/** 提取异常响应中的错误码/文案（APP 端 request 不统一弹错，页面自行 alert） */
function extractError(err: unknown): { code?: string; message?: string } | null {
  const e = err as { response?: { data?: { error?: { code?: string; message?: string } } } }
  return e?.response?.data?.error ?? null
}

/** 互动业务错误提示（区分当日限频与星光不足） */
function showInteractError(code?: string, message?: string) {
  if (code === 'WISH_RATE_LIMITED') {
    alert('这个漂流瓶今天已经回应过啦')
  } else if (code === 'WISH_STARLIGHT_INSUFFICIENT') {
    alert('星光不足，可通过每日签到、打卡获取星光')
  } else {
    alert(message || '互动失败，请稍后重试')
  }
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString('zh-CN')
}

interface CommentRowProps {
  item: DriftBottleCommentItem
  onReply: (item: DriftBottleCommentItem) => void
}

function CommentRow({ item, onReply }: CommentRowProps) {
  const isReply = item.parentId != null
  const clickable = item.userId != null

  const openProfile = () => {
    if (item.userId != null) router.push(`/user-profile?id=${item.userId}`)
  }

  return (
    <View style={{ flexDirection: 'row', paddingLeft: isReply ? Spacing.md : 0 }}>
      <TouchableOpacity disabled={!clickable} onPress={openProfile} activeOpacity={clickable ? 0.8 : 1}>
        {item.avatar ? (
          <Image source={{ uri: item.avatar }} style={{ width: 32, height: 32, borderRadius: 16 }} />
        ) : (
          <View
            style={{
              width: 32,
              height: 32,
              borderRadius: 16,
              backgroundColor: 'rgba(255,255,255,0.1)',
              justifyContent: 'center',
              alignItems: 'center',
            }}
          >
            <Text style={{ fontSize: 10, color: WishColors.textTertiary }}>匿</Text>
          </View>
        )}
      </TouchableOpacity>
      <View style={{ flex: 1, marginLeft: Spacing.sm }}>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <TouchableOpacity disabled={!clickable} onPress={openProfile} activeOpacity={clickable ? 0.8 : 1}>
            <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.textSecondary }}>
              {item.nickname}
            </Text>
          </TouchableOpacity>
          {item.isAnonymous ? (
            <Text
              style={{
                fontSize: FontSize.xs,
                color: WishColors.accentPurple,
                marginLeft: Spacing.xs,
                backgroundColor: 'rgba(147,112,219,0.15)',
                borderRadius: BorderRadius.sm,
                paddingHorizontal: 4,
                paddingVertical: 1,
                overflow: 'hidden',
              }}
            >
              匿名
            </Text>
          ) : null}
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginLeft: 'auto' }}>
            {formatTime(item.createdAt)}
          </Text>
        </View>
        <Text style={{ fontSize: FontSize.md, color: WishColors.text, lineHeight: 22, marginTop: 2 }}>
          {isReply && item.replyToNickname ? `回复 @${item.replyToNickname}：` : ''}
          {item.content}
        </Text>
        <TouchableOpacity
          onPress={() => onReply(item)}
          style={{ marginTop: Spacing.xs }}
          accessibilityLabel={`回复 ${item.nickname} 的评论`}
        >
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>回复</Text>
        </TouchableOpacity>
      </View>
    </View>
  )
}

interface BottleCardProps {
  bottle: DriftBottleItem
  contentWidth: number
  onBottleChange: (bottle: DriftBottleItem) => void
}

function BottleCard({ bottle, contentWidth, onBottleChange }: BottleCardProps) {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [interacting, setInteracting] = useState<'BLESS' | 'LIGHT' | null>(null)
  const [commentsOpen, setCommentsOpen] = useState(false)
  const [commentsLoaded, setCommentsLoaded] = useState(false)
  const [comments, setComments] = useState<DriftBottleCommentItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [commentsLoading, setCommentsLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [commentText, setCommentText] = useState('')
  const [commentAnon, setCommentAnon] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [replyTo, setReplyTo] = useState<DriftBottleCommentItem | null>(null)

  const canInteract = bottle.role === 'PICKED' && bottle.wishId != null

  const handleInteract = async (type: 'BLESS' | 'LIGHT') => {
    if (!isLoggedIn) {
      router.push('/login')
      return
    }
    setInteracting(type)
    try {
      const res = await wishApi.interactDriftBottle(bottle.bottleId, type)
      if (res.data?.success) {
        onBottleChange(res.data.data)
        alert(type === 'BLESS' ? '已送上祝福 🌟' : '已为 TA 点亮 ✨')
      } else {
        showInteractError(res.data?.error?.code, res.data?.error?.message)
      }
    } catch (err) {
      const e = extractError(err)
      showInteractError(e?.code, e?.message)
    } finally {
      setInteracting(null)
    }
  }

  const loadComments = useCallback(async () => {
    setCommentsLoading(true)
    try {
      const res = await wishApi.listDriftBottleComments(bottle.bottleId, { pageSize: COMMENT_PAGE_SIZE })
      if (res.data?.success) {
        setComments(res.data.data ?? [])
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
        setCommentsLoaded(true)
      } else if (res.data) {
        alert(res.data.error?.message || '评论加载失败')
      }
    } catch {
      // 评论加载失败不阻断卡片展示
    } finally {
      setCommentsLoading(false)
    }
  }, [bottle.bottleId])

  const toggleComments = () => {
    const next = !commentsOpen
    setCommentsOpen(next)
    if (next && !commentsLoaded) loadComments()
  }

  const loadMore = async () => {
    if (!hasMore || loadingMore || !cursor) return
    setLoadingMore(true)
    try {
      const res = await wishApi.listDriftBottleComments(bottle.bottleId, {
        cursor,
        pageSize: COMMENT_PAGE_SIZE,
      })
      if (res.data?.success) {
        setComments((prev) => {
          const seen = new Set(prev.map((c) => c.id))
          return [...prev, ...res.data.data.filter((c) => !seen.has(c.id))]
        })
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 加载更多失败静默，用户可再次点击重试
    } finally {
      setLoadingMore(false)
    }
  }

  const startReply = (item: DriftBottleCommentItem) => {
    if (!isLoggedIn) {
      router.push('/login')
      return
    }
    setReplyTo(item)
  }

  const handleSubmitComment = async () => {
    const content = commentText.trim()
    if (!content) {
      alert('写点什么再发送吧')
      return
    }
    if (!isLoggedIn) {
      router.push('/login')
      return
    }
    setSubmitting(true)
    try {
      const res = await wishApi.addDriftBottleComment(bottle.bottleId, {
        content,
        parentId: replyTo?.id,
        isAnonymous: commentAnon,
      })
      if (res.data?.success) {
        setCommentText('')
        setReplyTo(null)
        onBottleChange({ ...bottle, commentCount: bottle.commentCount + 1 })
        setCommentsLoaded(false)
        loadComments()
      } else if (res.data) {
        alert(res.data.error?.message || '发送失败，请稍后重试')
      }
    } catch {
      alert('发送失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <View
      style={{
        backgroundColor: WishColors.bgContainer,
        borderRadius: BorderRadius.lg,
        padding: Spacing.md,
        marginBottom: Spacing.sm,
      }}
    >
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.accentCyan, fontWeight: '600' }}>
            {ROLE_LABELS[bottle.role]}
          </Text>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>
            {STATUS_LABELS[bottle.status]}
          </Text>
        </View>
        <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>{formatTime(bottle.thrownAt)}</Text>
      </View>

      {bottle.content ? (
        <View style={{ marginTop: Spacing.sm }}>
          <RichHtml content={bottle.content} width={contentWidth} color={WishColors.text} fontSize={FontSize.md} />
        </View>
      ) : null}

      {bottle.wishId != null ? (
        <View
          style={{
            marginTop: Spacing.sm,
            padding: Spacing.sm,
            borderRadius: BorderRadius.md,
            backgroundColor: 'rgba(255,255,255,0.04)',
          }}
        >
          <Text style={{ fontSize: FontSize.xs, color: WishColors.accentPurple, fontWeight: '600' }}>心愿</Text>
          {bottle.wishTitle ? (
            <Text style={{ fontSize: FontSize.md, color: WishColors.text, fontWeight: '600', marginTop: 2 }}>
              {bottle.wishTitle}
            </Text>
          ) : null}
          {bottle.wishTags.length > 0 ? (
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: Spacing.xs }}>
              {bottle.wishTags.map((tag) => (
                <Text
                  key={tag}
                  style={{
                    fontSize: FontSize.xs,
                    color: WishColors.accentCyan,
                    backgroundColor: 'rgba(0,212,255,0.1)',
                    borderRadius: BorderRadius.sm,
                    paddingHorizontal: Spacing.sm,
                    paddingVertical: 2,
                    overflow: 'hidden',
                  }}
                >
                  {tag}
                </Text>
              ))}
            </View>
          ) : null}
        </View>
      ) : null}

      {!bottle.isAnonymous && bottle.throwerNickname ? (
        <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary, marginTop: Spacing.sm }}>
          投瓶人：{bottle.throwerNickname}
        </Text>
      ) : null}

      {canInteract ? (
        <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.sm, marginTop: Spacing.sm }}>
          {(['BLESS', 'LIGHT'] as const).map((type) => (
            <TouchableOpacity
              key={type}
              activeOpacity={0.85}
              disabled={interacting !== null}
              onPress={() => handleInteract(type)}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                paddingHorizontal: Spacing.md,
                paddingVertical: 6,
                borderRadius: BorderRadius.md,
                borderWidth: 1,
                borderColor: WishColors.border,
              }}
            >
              {interacting === type ? (
                <ActivityIndicator size="small" color={WishColors.accentCyan} />
              ) : (
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>
                  {type === 'BLESS' ? '🌟 祝福' : '✨ 点亮'}
                </Text>
              )}
            </TouchableOpacity>
          ))}
        </View>
      ) : null}

      <TouchableOpacity
        onPress={toggleComments}
        style={{ marginTop: Spacing.sm, paddingVertical: Spacing.sm }}
        accessibilityLabel="展开或收起评论"
      >
        <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan }}>
          {commentsOpen ? '收起评论' : `查看评论 ${bottle.commentCount}`}
        </Text>
      </TouchableOpacity>

      {commentsOpen ? (
        <View style={{ marginTop: Spacing.xs }}>
          {replyTo ? (
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                padding: Spacing.sm,
                borderRadius: BorderRadius.sm,
                backgroundColor: 'rgba(0,212,255,0.08)',
                marginBottom: Spacing.sm,
              }}
            >
              <Text style={{ flex: 1, fontSize: FontSize.sm, color: WishColors.accentCyan }}>
                回复 @{replyTo.nickname}
              </Text>
              <TouchableOpacity onPress={() => setReplyTo(null)} accessibilityLabel="取消回复">
                <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary }}>取消</Text>
              </TouchableOpacity>
            </View>
          ) : null}

          {isLoggedIn ? (
            <View>
              <TextInput
                value={commentText}
                onChangeText={(t) => setCommentText(t.slice(0, COMMENT_CONTENT_MAX))}
                placeholder={replyTo ? `回复 @${replyTo.nickname}...` : '写下你的回应…'}
                placeholderTextColor={WishColors.textTertiary}
                multiline
                editable={!submitting}
                style={{
                  minHeight: 72,
                  padding: Spacing.md,
                  borderRadius: BorderRadius.md,
                  backgroundColor: 'rgba(255,255,255,0.08)',
                  color: WishColors.text,
                  fontSize: FontSize.md,
                  textAlignVertical: 'top',
                }}
              />
              <View
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginTop: Spacing.sm,
                }}
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.xs }}>
                  <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>匿名</Text>
                  <Switch
                    value={commentAnon}
                    onValueChange={setCommentAnon}
                    trackColor={{ true: WishColors.accentCyan, false: WishColors.border }}
                  />
                </View>
                <TouchableOpacity
                  onPress={submitting ? undefined : handleSubmitComment}
                  disabled={submitting || !commentText.trim()}
                  style={{
                    paddingHorizontal: Spacing.lg,
                    paddingVertical: Spacing.sm,
                    borderRadius: BorderRadius.xl,
                    alignItems: 'center',
                    backgroundColor: WishColors.primary,
                    opacity: commentText.trim() && !submitting ? 1 : 0.5,
                  }}
                  accessibilityLabel={replyTo ? '发送回复' : '发送评论'}
                >
                  <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: '#fff' }}>
                    {submitting ? '发送中...' : replyTo ? '发送回复' : '发送'}
                  </Text>
                </TouchableOpacity>
              </View>
            </View>
          ) : (
            <TouchableOpacity
              onPress={() => router.push('/login')}
              style={{
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.md,
                alignItems: 'center',
                borderWidth: 1,
                borderColor: WishColors.border,
              }}
              accessibilityLabel="登录后参与评论"
            >
              <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary }}>登录后参与评论</Text>
            </TouchableOpacity>
          )}

          {commentsLoading ? (
            <ActivityIndicator size="small" color={WishColors.textTertiary} style={{ marginTop: Spacing.md }} />
          ) : comments.length === 0 ? (
            <Text
              style={{
                textAlign: 'center',
                color: WishColors.textTertiary,
                fontSize: FontSize.sm,
                marginTop: Spacing.md,
              }}
            >
              还没有回应，来写下第一条吧
            </Text>
          ) : (
            <View style={{ marginTop: Spacing.sm, gap: Spacing.md }}>
              {comments.map((item) => (
                <CommentRow key={item.id} item={item} onReply={startReply} />
              ))}
            </View>
          )}

          {commentsLoaded && hasMore ? (
            <TouchableOpacity
              onPress={() => !loadingMore && loadMore()}
              activeOpacity={0.7}
              style={{ alignItems: 'center', paddingVertical: Spacing.sm }}
              accessibilityLabel="加载更多评论"
            >
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>
                {loadingMore ? '加载中...' : '加载更多'}
              </Text>
            </TouchableOpacity>
          ) : null}
        </View>
      ) : null}
    </View>
  )
}

/**
 * 漂流瓶（替代旧「相遇信笺」体验）：
 * 捞瓶 / 投瓶（自由文字 或 关联心愿）/ 我的漂流瓶 + 瓶下评论树 + 祝福/点亮互动。
 */
export default function DriftBottleScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const { width: windowWidth } = useWindowDimensions()

  const [fishing, setFishing] = useState(false)
  const [fishedBottle, setFishedBottle] = useState<DriftBottleItem | null>(null)

  const editorRef = useRef<RichTextEditorRef>(null)
  const [throwMode, setThrowMode] = useState<'text' | 'wish'>('text')
  const [candidateWishes, setCandidateWishes] = useState<DriftBottleCandidateWish[]>([])
  const [candidatesVisible, setCandidatesVisible] = useState(false)
  const [candidatesLoading, setCandidatesLoading] = useState(false)
  const [selectedWish, setSelectedWish] = useState<DriftBottleCandidateWish | null>(null)
  const [isAnonymous, setIsAnonymous] = useState(true)
  const [throwing, setThrowing] = useState(false)

  const [bottles, setBottles] = useState<DriftBottleItem[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

  const cardContentWidth = windowWidth - Spacing.lg * 2 - Spacing.md * 2

  const loadBottles = useCallback(async () => {
    if (!isLoggedIn) return
    try {
      const res = await wishApi.listMyDriftBottles()
      if (res.data?.success) setBottles(res.data.data ?? [])
    } catch {
      // 静默
    } finally {
      setLoading(false)
    }
  }, [isLoggedIn])

  useEffect(() => {
    loadBottles()
  }, [loadBottles])

  const handleFish = async () => {
    if (!isLoggedIn) {
      router.push('/login')
      return
    }
    setFishing(true)
    try {
      const res = await wishApi.fishDriftBottle()
      if (res.data?.success) {
        if (res.data.data) {
          setFishedBottle(res.data.data)
          loadBottles()
        } else {
          setFishedBottle(null)
          alert('海面暂时没有漂流瓶')
        }
      } else if (res.data) {
        alert(res.data.error?.message || '捞瓶失败，请稍后重试')
      }
    } catch (err) {
      const e = extractError(err)
      alert(e?.message || '捞瓶失败，请稍后重试')
    } finally {
      setFishing(false)
    }
  }

  const openCandidateWishes = async () => {
    setCandidatesVisible(true)
    setCandidatesLoading(true)
    try {
      const res = await wishApi.listDriftBottleCandidateWishes()
      if (res.data?.success) setCandidateWishes(res.data.data ?? [])
    } catch {
      // 静默
    } finally {
      setCandidatesLoading(false)
    }
  }

  const handleThrow = async () => {
    if (!isLoggedIn) {
      router.push('/login')
      return
    }
    let payload: { content?: string; wishId?: number; isAnonymous: boolean }
    if (throwMode === 'wish') {
      if (!selectedWish) {
        alert('请选择一个关联心愿')
        return
      }
      payload = { wishId: selectedWish.wishId, isAnonymous }
    } else {
      const html = (await editorRef.current?.getHTML()) ?? ''
      if (!stripHtml(html)) {
        alert('写点什么再投出去吧')
        return
      }
      payload = { content: html, isAnonymous }
    }
    setThrowing(true)
    try {
      const res = await wishApi.throwDriftBottle(payload)
      if (res.data?.success) {
        alert('漂流瓶已抛出，愿它漂向有缘人 🍾')
        editorRef.current?.setHTML('')
        setThrowMode('text')
        setSelectedWish(null)
        loadBottles()
      } else if (res.data) {
        alert(res.data.error?.message || '投瓶失败，请稍后重试')
      }
    } catch (err) {
      const e = extractError(err)
      alert(e?.message || '投瓶失败，请稍后重试')
    } finally {
      setThrowing(false)
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: Spacing.lg,
          paddingBottom: Spacing.sm,
        }}
      >
        <TouchableOpacity onPress={() => router.back()} accessibilityLabel="返回">
          <Text style={{ fontSize: FontSize.md, color: WishColors.accentCyan }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>漂流瓶</Text>
        <View style={{ width: 48 }} />
      </View>

      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={{ padding: Spacing.lg, paddingBottom: 40 }}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={async () => {
              setRefreshing(true)
              await loadBottles()
              setRefreshing(false)
            }}
            tintColor={WishColors.accentCyan}
          />
        }
      >
        {!isLoggedIn ? (
          <Text style={{ textAlign: 'center', marginTop: 80, color: WishColors.textTertiary, fontSize: FontSize.sm }}>
            请先登录后体验漂流瓶
          </Text>
        ) : (
          <>
            <View
              style={{
                backgroundColor: WishColors.bgContainer,
                borderRadius: BorderRadius.lg,
                padding: Spacing.md,
                marginBottom: Spacing.md,
              }}
            >
              <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text }}>🌊 捞一只漂流瓶</Text>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 4 }}>
                海里有陌生人投下的瓶子，捞起来看看另一位许愿人的心事
              </Text>
              <TouchableOpacity
                onPress={handleFish}
                disabled={fishing}
                activeOpacity={0.85}
                style={{
                  marginTop: Spacing.md,
                  paddingVertical: Spacing.md,
                  borderRadius: BorderRadius.md,
                  backgroundColor: WishColors.accentCyan,
                  alignItems: 'center',
                }}
              >
                {fishing ? (
                  <ActivityIndicator color="#0b1026" />
                ) : (
                  <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#0b1026' }}>捞瓶</Text>
                )}
              </TouchableOpacity>
              {fishedBottle ? (
                <View style={{ marginTop: Spacing.md }}>
                  <BottleCard bottle={fishedBottle} contentWidth={cardContentWidth} onBottleChange={setFishedBottle} />
                </View>
              ) : null}
            </View>

            <View
              style={{
                backgroundColor: WishColors.bgContainer,
                borderRadius: BorderRadius.lg,
                padding: Spacing.md,
                marginBottom: Spacing.md,
              }}
            >
              <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text }}>🍾 投一只漂流瓶</Text>

              <View style={{ flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.md }}>
                <TouchableOpacity
                  onPress={() => setThrowMode('text')}
                  style={{
                    flex: 1,
                    paddingVertical: Spacing.sm,
                    borderRadius: BorderRadius.md,
                    alignItems: 'center',
                    backgroundColor: throwMode === 'text' ? 'rgba(0,212,255,0.15)' : 'transparent',
                    borderWidth: 1,
                    borderColor: throwMode === 'text' ? WishColors.accentCyan : WishColors.border,
                  }}
                >
                  <Text
                    style={{
                      fontSize: FontSize.sm,
                      color: throwMode === 'text' ? WishColors.accentCyan : WishColors.textSecondary,
                    }}
                  >
                    自由文字
                  </Text>
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => setThrowMode('wish')}
                  style={{
                    flex: 1,
                    paddingVertical: Spacing.sm,
                    borderRadius: BorderRadius.md,
                    alignItems: 'center',
                    backgroundColor: throwMode === 'wish' ? 'rgba(0,212,255,0.15)' : 'transparent',
                    borderWidth: 1,
                    borderColor: throwMode === 'wish' ? WishColors.accentCyan : WishColors.border,
                  }}
                >
                  <Text
                    style={{
                      fontSize: FontSize.sm,
                      color: throwMode === 'wish' ? WishColors.accentCyan : WishColors.textSecondary,
                    }}
                  >
                    关联心愿
                  </Text>
                </TouchableOpacity>
              </View>

              {throwMode === 'text' ? (
                <View style={{ marginTop: Spacing.md }}>
                  <RichTextEditor ref={editorRef} placeholder="写下此刻的心事，随海漂流…" />
                </View>
              ) : selectedWish ? (
                <View
                  style={{
                    marginTop: Spacing.md,
                    padding: Spacing.md,
                    borderRadius: BorderRadius.md,
                    backgroundColor: 'rgba(255,255,255,0.04)',
                    borderWidth: 1,
                    borderColor: WishColors.border,
                  }}
                >
                  <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text }}>
                    {selectedWish.title}
                  </Text>
                  {selectedWish.tags.length > 0 ? (
                    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: Spacing.sm }}>
                      {selectedWish.tags.map((tag) => (
                        <Text
                          key={tag}
                          style={{
                            fontSize: FontSize.xs,
                            color: WishColors.accentCyan,
                            backgroundColor: 'rgba(0,212,255,0.1)',
                            borderRadius: BorderRadius.sm,
                            paddingHorizontal: Spacing.sm,
                            paddingVertical: 2,
                            overflow: 'hidden',
                          }}
                        >
                          {tag}
                        </Text>
                      ))}
                    </View>
                  ) : null}
                  <TouchableOpacity onPress={() => setSelectedWish(null)} style={{ alignSelf: 'flex-start', marginTop: Spacing.sm }}>
                    <Text style={{ fontSize: FontSize.xs, color: WishColors.accentCyan }}>移除关联</Text>
                  </TouchableOpacity>
                </View>
              ) : (
                <TouchableOpacity
                  onPress={openCandidateWishes}
                  style={{
                    marginTop: Spacing.md,
                    paddingVertical: Spacing.lg,
                    borderRadius: BorderRadius.md,
                    borderWidth: 1,
                    borderColor: WishColors.border,
                    borderStyle: 'dashed',
                    alignItems: 'center',
                  }}
                >
                  <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>选择一个关联心愿</Text>
                  <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 4 }}>
                    从你发布的公开进行中心愿中挑选
                  </Text>
                </TouchableOpacity>
              )}

              <View
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginTop: Spacing.md,
                }}
              >
                <View style={{ flex: 1, marginRight: Spacing.md }}>
                  <Text style={{ fontSize: FontSize.sm, color: WishColors.text }}>匿名投出（默认）</Text>
                  <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>
                    关闭后投瓶人可见你的昵称
                  </Text>
                </View>
                <Switch
                  value={isAnonymous}
                  onValueChange={setIsAnonymous}
                  trackColor={{ true: WishColors.accentCyan, false: WishColors.border }}
                />
              </View>

              <TouchableOpacity
                onPress={throwing ? undefined : handleThrow}
                disabled={throwing}
                style={{
                  marginTop: Spacing.md,
                  paddingVertical: Spacing.md,
                  borderRadius: BorderRadius.xl,
                  backgroundColor: WishColors.primary,
                  alignItems: 'center',
                  opacity: throwing ? 0.6 : 1,
                }}
              >
                <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#fff' }}>
                  {throwing ? '投出中...' : '投出漂流瓶'}
                </Text>
              </TouchableOpacity>
            </View>

            <View>
              <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text, marginBottom: Spacing.md }}>
                我的漂流瓶
              </Text>
              {loading ? (
                <ActivityIndicator color={WishColors.accentCyan} style={{ marginTop: 40 }} />
              ) : bottles.length === 0 ? (
                <Text
                  style={{
                    textAlign: 'center',
                    marginTop: 40,
                    color: WishColors.textTertiary,
                    fontSize: FontSize.sm,
                    lineHeight: 22,
                  }}
                >
                  还没有漂流瓶{'\n'}投出或捞起你的第一只吧
                </Text>
              ) : (
                bottles.map((bottle) => (
                  <BottleCard
                    key={bottle.bottleId}
                    bottle={bottle}
                    contentWidth={cardContentWidth}
                    onBottleChange={(updated) =>
                      setBottles((prev) => prev.map((b) => (b.bottleId === updated.bottleId ? updated : b)))
                    }
                  />
                ))
              )}
            </View>
          </>
        )}
      </ScrollView>

      <Modal visible={candidatesVisible} transparent animationType="slide" onRequestClose={() => setCandidatesVisible(false)}>
        <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' }}>
          <View
            style={{
              backgroundColor: WishColors.bgContainer,
              borderTopLeftRadius: BorderRadius.xl,
              borderTopRightRadius: BorderRadius.xl,
              padding: Spacing.lg,
              paddingBottom: Spacing.xxl,
            }}
          >
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: Spacing.md }}>
              <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>选择关联心愿</Text>
              <TouchableOpacity onPress={() => setCandidatesVisible(false)} accessibilityLabel="关闭">
                <Text style={{ fontSize: FontSize.md, color: WishColors.textTertiary }}>关闭</Text>
              </TouchableOpacity>
            </View>
            {candidatesLoading ? (
              <ActivityIndicator color={WishColors.accentCyan} style={{ marginVertical: 40 }} />
            ) : candidateWishes.length === 0 ? (
              <Text
                style={{
                  textAlign: 'center',
                  color: WishColors.textTertiary,
                  fontSize: FontSize.sm,
                  paddingVertical: 40,
                }}
              >
                暂无可关联的公开心愿
              </Text>
            ) : (
              <ScrollView style={{ maxHeight: 400 }}>
                {candidateWishes.map((w) => (
                  <TouchableOpacity
                    key={w.wishId}
                    onPress={() => {
                      setSelectedWish(w)
                      setCandidatesVisible(false)
                    }}
                    style={{
                      paddingVertical: Spacing.md,
                      borderBottomWidth: 1,
                      borderBottomColor: WishColors.border,
                    }}
                  >
                    <Text style={{ fontSize: FontSize.md, color: WishColors.text, fontWeight: '600' }}>{w.title}</Text>
                    {w.tags.length > 0 ? (
                      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: Spacing.sm }}>
                        {w.tags.map((tag) => (
                          <Text
                            key={tag}
                            style={{
                              fontSize: FontSize.xs,
                              color: WishColors.accentCyan,
                              backgroundColor: 'rgba(0,212,255,0.1)',
                              borderRadius: BorderRadius.sm,
                              paddingHorizontal: Spacing.sm,
                              paddingVertical: 2,
                              overflow: 'hidden',
                            }}
                          >
                            {tag}
                          </Text>
                        ))}
                      </View>
                    ) : null}
                  </TouchableOpacity>
                ))}
              </ScrollView>
            )}
          </View>
        </View>
      </Modal>
    </View>
  )
}