import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react'
import { View, Text, Image, Textarea } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import type { WishCommentItem } from '@/types'
import styles from './index.module.scss'

/**
 * 心愿评论模块（Sprint 1.2）。
 *
 * 结构：扁平列表 + parentId/replyToNickname（回复缩进前端组装）；
 * cursor 分页由页面 ScrollView 触底触发（经 ref.loadMore 暴露）；
 * 仅作者本人可删除自己的评论（软删）。
 */

const COMMENT_PAGE_SIZE = 10
const COMMENT_CONTENT_MAX = 500

export interface WishCommentSectionHandle {
  /** 页面 ScrollView onScrollToLower 时调用（内部自带 hasMore/loadingMore 防抖） */
  loadMore: () => void
}

interface WishCommentSectionProps {
  wishId: number | string
  commentCount: number
  isLoggedIn: boolean
  currentUserId?: number
  onCountChange: (delta: number) => void
  onRequireLogin: () => void
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const WishCommentSection = forwardRef<WishCommentSectionHandle, WishCommentSectionProps>(
  function WishCommentSection(
    { wishId, commentCount, isLoggedIn, currentUserId, onCountChange, onRequireLogin },
    ref,
  ) {
    const [comments, setComments] = useState<WishCommentItem[]>([])
    const [cursor, setCursor] = useState<string | null>(null)
    const [hasMore, setHasMore] = useState(false)
    const [loading, setLoading] = useState(true)
    const [loadingMore, setLoadingMore] = useState(false)
    const [content, setContent] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [replyTo, setReplyTo] = useState<WishCommentItem | null>(null)
    /** 防重复请求与过期响应覆盖 */
    const requestSeq = useRef(0)

    const loadFirstPage = useCallback(async () => {
      const seq = ++requestSeq.current
      setLoading(true)
      try {
        const res = await wishApi.listComments(wishId, { pageSize: COMMENT_PAGE_SIZE })
        if (seq !== requestSeq.current) return
        if (res.data.success) {
          setComments(res.data.data)
          setCursor(res.data.meta?.nextCursor ?? null)
          setHasMore(Boolean(res.data.meta?.hasMore))
        }
      } catch {
        // 错误已由 request 处理
      } finally {
        if (seq === requestSeq.current) setLoading(false)
      }
    }, [wishId])

    useEffect(() => {
      loadFirstPage()
    }, [loadFirstPage])

    const loadMore = useCallback(async () => {
      if (!hasMore || loadingMore || !cursor) return
      const seq = ++requestSeq.current
      setLoadingMore(true)
      try {
        const res = await wishApi.listComments(wishId, {
          cursor,
          pageSize: COMMENT_PAGE_SIZE,
        })
        if (seq !== requestSeq.current) return
        if (res.data.success) {
          // cursor 分页按 id 去重合并，防御服务端数据变动导致的重复
          setComments((prev) => {
            const seen = new Set(prev.map((c) => c.id))
            return [...prev, ...res.data.data.filter((c) => !seen.has(c.id))]
          })
          setCursor(res.data.meta?.nextCursor ?? null)
          setHasMore(Boolean(res.data.meta?.hasMore))
        }
      } catch {
        // 错误已由 request 处理
      } finally {
        if (seq === requestSeq.current) setLoadingMore(false)
      }
    }, [wishId, hasMore, loadingMore, cursor])

    useImperativeHandle(ref, () => ({ loadMore }), [loadMore])

    const startReply = (comment: WishCommentItem) => {
      if (!isLoggedIn) {
        Taro.showToast({ title: '登录后即可回复', icon: 'none' })
        onRequireLogin()
        return
      }
      setReplyTo(comment)
    }

    const handleSubmit = async () => {
      const trimmed = content.trim()
      if (!trimmed) {
        Taro.showToast({ title: '写点什么再发送吧', icon: 'none' })
        return
      }
      setSubmitting(true)
      try {
        const res = await wishApi.createComment(wishId, {
          content: trimmed,
          parentId: replyTo?.id,
        })
        if (res.data.success) {
          setContent('')
          setReplyTo(null)
          onCountChange(1)
          loadFirstPage()
          Taro.showToast({ title: replyTo ? '回复已发送' : '评论已发表', icon: 'none' })
        } else {
          const errMsg = res.data.error?.message ?? '发表失败，请稍后重试'
          Taro.showToast({ title: errMsg, icon: 'none' })
        }
      } catch {
        // 错误已由 request 处理
      } finally {
        setSubmitting(false)
      }
    }

    const handleDelete = async (commentId: number) => {
      const confirmed = await Taro.showModal({
        title: '删除评论',
        content: '删除后不可恢复，确定删除吗？',
      })
      if (!confirmed.confirm) return

      const res = await wishApi.deleteComment(wishId, commentId)
      if (res.data.success) {
        setComments((prev) => prev.filter((c) => c.id !== commentId))
        onCountChange(-1)
        Taro.showToast({ title: '已删除', icon: 'none' })
      } else {
        const errMsg = res.data.error?.message ?? '删除失败，请稍后重试'
        Taro.showToast({ title: errMsg, icon: 'none' })
      }
    }

    return (
      <View className={styles.section}>
        <View className={styles.header}>
          <Text className={styles.headerTitle}>评论</Text>
          <Text className={styles.headerCount}>{commentCount}</Text>
        </View>

        {/* 发表/回复输入区（未登录显示引导） */}
        {isLoggedIn ? (
          <View className={styles.composer}>
            {replyTo && (
              <View className={styles.replyBanner}>
                <Text className={styles.replyBannerText}>回复 @{replyTo.nickname}</Text>
                <Text className={styles.replyCancel} onClick={() => setReplyTo(null)}>取消</Text>
              </View>
            )}
            <Textarea
              className={styles.composerInput}
              value={content}
              onInput={(e) => setContent(e.detail.value.slice(0, COMMENT_CONTENT_MAX))}
              maxlength={COMMENT_CONTENT_MAX}
              placeholder={replyTo ? `回复 @${replyTo.nickname}...` : '写下你的鼓励与祝福...'}
              disabled={submitting}
              showConfirmBar={false}
            />
            <View className={styles.composerActions}>
              <View
                className={`${styles.submitBtn} ${!content.trim() || submitting ? styles.submitBtnDisabled : ''}`}
                onClick={() => content.trim() && !submitting && handleSubmit()}
              >
                <Text className={styles.submitBtnText}>
                  {replyTo ? '发送回复' : '发表评论'}
                </Text>
              </View>
            </View>
          </View>
        ) : (
          <View className={styles.loginPrompt} onClick={onRequireLogin}>
            <Text className={styles.loginPromptText}>登录后发表评论</Text>
          </View>
        )}

        {/* 评论列表 */}
        {loading ? (
          <View className={styles.loadingWrap}>
            <Text className={styles.loadingText}>加载中...</Text>
          </View>
        ) : comments.length === 0 ? (
          <View className={styles.emptyWrap}>
            <Text className={styles.emptyIcon}>💬</Text>
            <Text className={styles.emptyText}>还没有评论，来写下第一条祝福吧</Text>
          </View>
        ) : (
          <View className={styles.list}>
            {comments.map((comment) => {
              const isReply = comment.parentId !== null
              const isMine = comment.userId === currentUserId
              return (
                <View
                  key={comment.id}
                  className={isReply ? `${styles.item} ${styles.itemReply}` : styles.item}
                >
                  {comment.avatar ? (
                    <Image className={styles.avatar} src={comment.avatar} mode='aspectFill' />
                  ) : (
                    <View className={styles.avatarPlaceholder}>
                      <Text className={styles.avatarStar}>★</Text>
                    </View>
                  )}
                  <View className={styles.itemBody}>
                    <View className={styles.itemMeta}>
                      <Text className={styles.itemNickname}>{comment.nickname}</Text>
                      <Text className={styles.itemTime}>{formatTime(comment.createdAt)}</Text>
                    </View>
                    {/* content 后端已 XSS 转义，可直接渲染 */}
                    <Text className={styles.itemContent}>
                      {isReply && comment.replyToNickname
                        ? `回复 @${comment.replyToNickname}：`
                        : ''}
                      {comment.content}
                    </Text>
                    <View className={styles.itemActions}>
                      <Text className={styles.actionText} onClick={() => startReply(comment)}>
                        回复
                      </Text>
                      {isMine && (
                        <Text
                          className={styles.actionDelete}
                          onClick={() => handleDelete(comment.id)}
                        >
                          删除
                        </Text>
                      )}
                    </View>
                  </View>
                </View>
              )
            })}
            {hasMore && (
              <View className={styles.loadMoreWrap}>
                <Text className={styles.loadMoreText}>
                  {loadingMore ? '加载中...' : '上拉加载更多'}
                </Text>
              </View>
            )}
            {!hasMore && comments.length > 0 && (
              <View className={styles.loadMoreWrap}>
                <Text className={styles.loadMoreText}>已经到底啦~</Text>
              </View>
            )}
          </View>
        )}
      </View>
    )
  },
)

export default WishCommentSection
