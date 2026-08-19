import { useCallback, useEffect, useRef, useState } from 'react'
import { App, Avatar, Button, Empty, Input, Popconfirm, Spin, Tag } from 'antd'
import { DeleteOutlined, MessageOutlined, StarOutlined } from '@ant-design/icons'
import {
  createWishComment,
  deleteWishComment,
  listWishComments,
  type WishCommentItem,
} from '@/api/wish'
import styles from './style.module.css'

/**
 * 心愿评论模块（文档 2.2 节，Sprint 1.2）。
 *
 * 结构：扁平列表 + parentId/replyToNickname，回复缩进由前端组装；
 * cursor 分页（时间倒序）+ 加载更多；仅作者本人可删除自己的评论（软删）。
 */

const COMMENT_PAGE_SIZE = 10
const COMMENT_CONTENT_MAX = 500

interface WishCommentSectionProps {
  wishId: number
  commentCount: number
  isLoggedIn: boolean
  currentUserId?: number
  onCountChange: (delta: number) => void
  onRequireLogin: () => void
}

export default function WishCommentSection({
  wishId,
  commentCount,
  isLoggedIn,
  currentUserId,
  onCountChange,
  onRequireLogin,
}: WishCommentSectionProps) {
  const { message } = App.useApp()
  const [comments, setComments] = useState<WishCommentItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [replyTo, setReplyTo] = useState<WishCommentItem | null>(null)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  /** 防重复加载（触底/重复点击） */
  const requestSeq = useRef(0)

  const loadFirstPage = useCallback(async () => {
    const seq = ++requestSeq.current
    setLoading(true)
    try {
      const res = await listWishComments(wishId, { pageSize: COMMENT_PAGE_SIZE })
      if (seq !== requestSeq.current) return
      if (res.data.success) {
        setComments(res.data.data)
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      if (seq === requestSeq.current) setLoading(false)
    }
  }, [wishId])

  useEffect(() => {
    loadFirstPage()
  }, [loadFirstPage])

  const handleLoadMore = async () => {
    if (!cursor || loadingMore) return
    const seq = ++requestSeq.current
    setLoadingMore(true)
    try {
      const res = await listWishComments(wishId, {
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
      // 错误已由 request 拦截器处理
    } finally {
      if (seq === requestSeq.current) setLoadingMore(false)
    }
  }

  const startReply = (comment: WishCommentItem) => {
    if (!isLoggedIn) {
      message.info('登录后即可回复')
      onRequireLogin()
      return
    }
    setReplyTo(comment)
  }

  const handleSubmit = async () => {
    const trimmed = content.trim()
    if (!trimmed) {
      message.warning('写点什么再发送吧')
      return
    }
    setSubmitting(true)
    try {
      const res = await createWishComment(wishId, {
        content: trimmed,
        parentId: replyTo?.id,
      })
      if (res.data.success) {
        setContent('')
        setReplyTo(null)
        onCountChange(1)
        // 重新拉第一页：新评论应出现在时间倒序首位
        loadFirstPage()
        message.success(replyTo ? '回复已发送' : '评论已发表')
      }
    } catch {
      // 错误已由 request 拦截器处理（含 WISH_VALIDATION_ERROR 内容校验）
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (commentId: number) => {
    setDeletingId(commentId)
    try {
      const res = await deleteWishComment(wishId, commentId)
      if (res.data.success) {
        setComments((prev) => prev.filter((c) => c.id !== commentId))
        onCountChange(-1)
        message.success('评论已删除')
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setDeletingId(null)
    }
  }

  const submitDisabled = !isLoggedIn || !content.trim()

  return (
    <div className={styles.section}>
      <div className={styles.header}>
        <MessageOutlined className={styles.headerIcon} aria-hidden="true" />
        <span className={styles.headerTitle}>评论</span>
        <span className={styles.headerCount}>{commentCount}</span>
      </div>

      {/* 发表/回复输入区 */}
      {isLoggedIn ? (
        <div className={styles.composer}>
          {replyTo && (
            <div className={styles.replyBanner}>
              <Tag color="gold" className={styles.replyTag}>
                回复 @{replyTo.nickname}
              </Tag>
              <Button
                type="link"
                size="small"
                onClick={() => setReplyTo(null)}
                aria-label="取消回复"
              >
                取消回复
              </Button>
            </div>
          )}
          <Input.TextArea
            value={content}
            onChange={(e) => setContent(e.target.value.slice(0, COMMENT_CONTENT_MAX))}
            placeholder={replyTo ? `回复 @${replyTo.nickname}...` : '写下你的鼓励与祝福...'}
            rows={3}
            maxLength={COMMENT_CONTENT_MAX}
            showCount
            disabled={submitting}
          />
          <div className={styles.composerActions}>
            <Button
              type="primary"
              loading={submitting}
              disabled={submitDisabled}
              onClick={handleSubmit}
            >
              {replyTo ? '发送回复' : '发表评论'}
            </Button>
          </div>
        </div>
      ) : (
        <Button
          className={styles.loginPrompt}
          type="dashed"
          block
          onClick={onRequireLogin}
        >
          登录后发表评论
        </Button>
      )}

      {/* 评论列表 */}
      {loading ? (
        <div className={styles.loadingWrap}>
          <Spin />
        </div>
      ) : comments.length === 0 ? (
        <Empty description="还没有评论，来写下第一条祝福吧" className={styles.empty} />
      ) : (
        <ul className={styles.list}>
          {comments.map((comment) => {
            const isReply = comment.parentId !== null
            const isMine = comment.userId === currentUserId
            return (
              <li
                key={comment.id}
                className={isReply ? `${styles.item} ${styles.itemReply}` : styles.item}
              >
                <Avatar
                  size={32}
                  src={comment.avatar || undefined}
                  icon={<StarOutlined />}
                  alt={`${comment.nickname} 的头像`}
                />
                <div className={styles.itemBody}>
                  <div className={styles.itemMeta}>
                    <span className={styles.itemNickname}>{comment.nickname}</span>
                    <span className={styles.itemTime}>
                      {new Date(comment.createdAt).toLocaleString('zh-CN')}
                    </span>
                  </div>
                  {/* content 后端已 XSS 转义，可直接渲染 */}
                  <p className={styles.itemContent}>
                    {isReply && comment.replyToNickname && (
                      <span className={styles.replyRef}>回复 @{comment.replyToNickname}：</span>
                    )}
                    {comment.content}
                  </p>
                  <div className={styles.itemActions}>
                    <Button
                      type="link"
                      size="small"
                      className={styles.actionBtn}
                      onClick={() => startReply(comment)}
                    >
                      回复
                    </Button>
                    {isMine && (
                      <Popconfirm
                        title="删除这条评论？"
                        description="删除后不可恢复"
                        okText="删除"
                        cancelText="取消"
                        onConfirm={() => handleDelete(comment.id)}
                      >
                        <Button
                          type="link"
                          size="small"
                          danger
                          className={styles.actionBtn}
                          icon={<DeleteOutlined />}
                          loading={deletingId === comment.id}
                        >
                          删除
                        </Button>
                      </Popconfirm>
                    )}
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {hasMore && !loading && (
        <div className={styles.loadMoreWrap}>
          <Button loading={loadingMore} onClick={handleLoadMore}>
            加载更多评论
          </Button>
        </div>
      )}
    </div>
  )
}
