import { useCallback, useEffect, useRef, useState } from 'react'
import { App, Button, Checkbox, Empty, Input, Spin } from 'antd'
import { CommentOutlined, SendOutlined } from '@ant-design/icons'
import { history } from 'umi'
import {
  addDriftBottleComment,
  listDriftBottleComments,
  type DriftBottleCommentItem,
} from '@/api/wish'
import DecoratedAvatar from '@/components/DecoratedAvatar'
import styles from './style.module.css'

/**
 * 漂流瓶瓶下评论树（捞到瓶后与瓶子作者交流的载体）：
 * 仅投瓶人与捞起人可看/可评；评论默认匿名（可切实名）；支持回复（parentId）。
 * cursor 分页 + 发表后重载首屏。
 */

const COMMENT_PAGE_SIZE = 10

interface DriftBottleCommentsProps {
  bottleId: number
}

export default function DriftBottleComments({ bottleId }: DriftBottleCommentsProps) {
  const { message } = App.useApp()
  const [comments, setComments] = useState<DriftBottleCommentItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [posting, setPosting] = useState(false)
  const [draft, setDraft] = useState('')
  /** 回复目标（null = 发表顶级评论） */
  const [replyTo, setReplyTo] = useState<DriftBottleCommentItem | null>(null)
  /** 是否匿名发言（默认匿名） */
  const [anonymous, setAnonymous] = useState(true)
  /** 防重复加载（重载/竞态） */
  const requestSeq = useRef(0)

  const loadFirstPage = useCallback(async () => {
    const seq = ++requestSeq.current
    setLoading(true)
    try {
      const res = await listDriftBottleComments(bottleId, { pageSize: COMMENT_PAGE_SIZE })
      if (seq !== requestSeq.current) return
      if (res.data.success) {
        setComments(res.data.data ?? [])
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      if (seq === requestSeq.current) setLoading(false)
    }
  }, [bottleId])

  useEffect(() => {
    loadFirstPage()
  }, [loadFirstPage])

  const handleLoadMore = async () => {
    if (!cursor || loadingMore) return
    const seq = ++requestSeq.current
    setLoadingMore(true)
    try {
      const res = await listDriftBottleComments(bottleId, {
        cursor,
        pageSize: COMMENT_PAGE_SIZE,
      })
      if (seq !== requestSeq.current) return
      if (res.data.success) {
        // cursor 分页按 id 去重合并，防御服务端数据变动导致的重复
        setComments((prev) => {
          const seen = new Set(prev.map((c) => c.id))
          return [...prev, ...(res.data.data ?? []).filter((c) => !seen.has(c.id))]
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

  const handleSubmit = async () => {
    const content = draft.trim()
    if (!content) {
      message.warning('先写点什么吧')
      return
    }
    setPosting(true)
    try {
      await addDriftBottleComment(bottleId, {
        content,
        parentId: replyTo?.id ?? null,
        isAnonymous: anonymous,
      })
      message.success(replyTo ? '回复已送达 💬' : '评论已发出 💬')
      setDraft('')
      setReplyTo(null)
      loadFirstPage()
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setPosting(false)
    }
  }

  const goProfile = (userId: number | null) => {
    if (userId !== null) history.push(`/user/${userId}`)
  }

  const renderTime = (createdAt: string) =>
    new Date(createdAt).toLocaleString('zh-CN', { hour12: false })

  return (
    <div className={styles.section}>
      {/* 评论输入区 */}
      <div className={styles.composer}>
        {replyTo && (
          <div className={styles.replyBar}>
            <span className={styles.replyLabel}>回复 @{replyTo.nickname}</span>
            <Button size="small" type="text" onClick={() => setReplyTo(null)} aria-label="取消回复">
              取消
            </Button>
          </div>
        )}
        <Input.TextArea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={replyTo ? `回复 @${replyTo.nickname}…` : '与瓶子主人说点什么…'}
          autoSize={{ minRows: 2, maxRows: 5 }}
          maxLength={500}
          className={styles.composerInput}
        />
        <div className={styles.composerFooter}>
          <Checkbox checked={anonymous} onChange={(e) => setAnonymous(e.target.checked)}>
            匿名发言（不选将展示你的昵称头像）
          </Checkbox>
          <Button
            type="primary"
            size="small"
            icon={<SendOutlined />}
            loading={posting}
            onClick={handleSubmit}
          >
            发送
          </Button>
        </div>
      </div>

      {/* 评论列表 */}
      {loading ? (
        <div className={styles.loadingWrap}>
          <Spin />
        </div>
      ) : comments.length === 0 ? (
        <Empty description="还没有评论，来聊聊吧" className={styles.empty} />
      ) : (
        <ul className={styles.list}>
          {comments.map((comment) => (
            <li key={comment.id} className={styles.item}>
              <DecoratedAvatar
                userId={comment.userId}
                size={32}
                src={comment.avatar || undefined}
                fallback={<CommentOutlined />}
                style={comment.userId === null ? undefined : { cursor: 'pointer' }}
                onClick={() => goProfile(comment.userId)}
              />
              <div className={styles.itemBody}>
                <div className={styles.itemMeta}>
                  <span
                    className={comment.userId === null ? styles.itemNicknamePlain : styles.itemNickname}
                    onClick={() => goProfile(comment.userId)}
                  >
                    {comment.nickname}
                    {comment.isAnonymous && <em className={styles.anonMark}>匿名</em>}
                  </span>
                  <span className={styles.itemTime}>{renderTime(comment.createdAt)}</span>
                </div>
                <p className={styles.itemContent}>
                  {comment.replyToNickname && (
                    <span className={styles.replyTarget}>回复 @{comment.replyToNickname}：</span>
                  )}
                  {comment.content}
                </p>
                <Button
                  size="small"
                  type="text"
                  className={styles.replyBtn}
                  onClick={() => setReplyTo(comment)}
                >
                  回复
                </Button>
              </div>
            </li>
          ))}
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