import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, history } from 'umi'
import { Spin, Empty, Avatar, Input, message, Dropdown, Popconfirm } from 'antd'
import Skeleton from '@/components/Skeleton'
import {
  ArrowLeftOutlined,
  HeartOutlined,
  HeartFilled,
  StarOutlined,
  StarFilled,
  ShareAltOutlined,
  MessageOutlined,
  EyeOutlined,
  LikeOutlined,
  LikeFilled,
  LeftOutlined,
  RightOutlined,
  SendOutlined,
  MoreOutlined,
  WarningOutlined,
  EditOutlined,
  DeleteOutlined,
} from '@ant-design/icons'
import DOMPurify from 'dompurify'
import {
  getPostDetail,
  likePost,
  unlikePost,
  collectPost,
  uncollectPost,
  getPostComments,
  createComment,
  likeComment,
  unlikeComment,
  getFeedPosts,
  getPostsByTopic,
  deletePost,
  searchUsers,
} from '@/api/community'
import type { Post, PostComment, SearchUserResult } from '@/api/community'
import { useAuthStore } from '@/stores/auth'
import ShareModal from '@/components/ShareModal'
import ReportModal from '@/components/ReportModal'

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  const months = Math.floor(days / 30)
  if (months < 12) return `${months}月前`
  return `${Math.floor(months / 12)}年前`
}

const TAG_COLORS = [
  { bg: 'rgba(var(--color-primary-rgb), 0.12)', border: 'rgba(var(--color-primary-rgb), 0.25)', text: 'var(--color-primary)' },
  { bg: 'rgba(160, 120, 255, 0.12)', border: 'rgba(160, 120, 255, 0.25)', text: '#A078FF' },
  { bg: 'rgba(255, 107, 107, 0.12)', border: 'rgba(255, 107, 107, 0.25)', text: '#FF6B6B' },
  { bg: 'rgba(46, 213, 115, 0.12)', border: 'rgba(46, 213, 115, 0.25)', text: '#2ED573' },
  { bg: 'rgba(255, 165, 2, 0.12)', border: 'rgba(255, 165, 2, 0.25)', text: '#FFA502' },
  { bg: 'rgba(255, 107, 182, 0.12)', border: 'rgba(255, 107, 182, 0.25)', text: '#FF6BB6' },
]

function MediaGallery({ mediaUrls, mediaType, coverImage }: { mediaUrls: string[]; mediaType: string; coverImage?: string }) {
  const [currentIndex, setCurrentIndex] = useState(0)
  const [videoPlaying, setVideoPlaying] = useState(false)
  const videoRef = useRef<HTMLVideoElement>(null)

  if (!mediaUrls || mediaUrls.length === 0) return null

  const isVideo = mediaType === 'VIDEO'

  const goTo = (index: number) => {
    setCurrentIndex((index + mediaUrls.length) % mediaUrls.length)
  }

  const handleVideoClick = () => {
    if (!videoRef.current) return
    if (videoRef.current.paused) {
      videoRef.current.play()
      setVideoPlaying(true)
    } else {
      videoRef.current.pause()
      setVideoPlaying(false)
    }
  }

  return (
    <div style={{ position: 'relative', borderRadius: '12px', overflow: 'hidden', marginBottom: 20 }}>
      {isVideo ? (
        <div style={{ position: 'relative', background: '#000', borderRadius: '12px' }}>
          <video
            ref={videoRef}
            src={mediaUrls[currentIndex]}
            poster={coverImage}
            preload="metadata"
            playsInline
            controls
            style={{
              width: '100%',
              maxHeight: 500,
              borderRadius: '12px',
              background: '#000',
              outline: 'none',
            }}
            onPlay={() => setVideoPlaying(true)}
            onPause={() => setVideoPlaying(false)}
          >
            <track kind="captions" />
          </video>
        </div>
      ) : (
        <div style={{ position: 'relative' }}>
          <img
            src={mediaUrls[currentIndex]}
            alt=""
            style={{
              width: '100%',
              maxHeight: 500,
              objectFit: 'contain',
              borderRadius: '12px',
              background: 'var(--color-bg-input)',
              display: 'block',
            }}
          />
          {mediaUrls.length > 1 && (
            <>
              <button
                type="button"
                onClick={() => goTo(currentIndex - 1)}
                style={{
                  position: 'absolute',
                  left: 12,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  width: 36,
                  height: 36,
                  borderRadius: '50%',
                  border: 'none',
                  background: 'rgba(0,0,0,0.5)',
                  color: 'var(--color-text-secondary)',
                  fontSize: 16,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  backdropFilter: 'blur(4px)',
                  transition: 'background 0.2s',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.background = 'rgba(0,0,0,0.7)' }}
                onMouseLeave={(e) => { e.currentTarget.style.background = 'rgba(0,0,0,0.5)' }}
              >
                <LeftOutlined />
              </button>
              <button
                type="button"
                onClick={() => goTo(currentIndex + 1)}
                style={{
                  position: 'absolute',
                  right: 12,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  width: 36,
                  height: 36,
                  borderRadius: '50%',
                  border: 'none',
                  background: 'rgba(0,0,0,0.5)',
                  color: 'var(--color-text-secondary)',
                  fontSize: 16,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  backdropFilter: 'blur(4px)',
                  transition: 'background 0.2s',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.background = 'rgba(0,0,0,0.7)' }}
                onMouseLeave={(e) => { e.currentTarget.style.background = 'rgba(0,0,0,0.5)' }}
              >
                <RightOutlined />
              </button>
            </>
          )}
        </div>
      )}

      {mediaUrls.length > 1 && !isVideo && (
        <div style={{
          position: 'absolute',
          bottom: 12,
          right: 12,
          padding: '4px 10px',
          borderRadius: '6px',
          background: 'rgba(0,0,0,0.6)',
          color: 'var(--color-text-secondary)',
          fontSize: 12,
          backdropFilter: 'blur(4px)',
        }}>
          {currentIndex + 1} / {mediaUrls.length}
        </div>
      )}

      {mediaUrls.length > 1 && !isVideo && (
        <div style={{
          display: 'flex',
          gap: 6,
          justifyContent: 'center',
          padding: '10px 0 4px',
        }}>
          {mediaUrls.map((url, index) => (
            <div
              key={url}
              onClick={() => setCurrentIndex(index)}
              style={{
                width: index === currentIndex ? 24 : 8,
                height: 8,
                borderRadius: 4,
                background: index === currentIndex ? 'var(--color-primary)' : 'rgba(255,255,255,0.2)',
                cursor: 'pointer',
                transition: 'all 0.3s ease',
              }}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function CommentItem({
  comment,
  commentLikedIds,
  onToggleLike,
  onReply,
  onReport,
}: {
  comment: PostComment
  commentLikedIds: Set<number>
  onToggleLike: (id: number) => void
  onReply: (nickname: string, parentId: number, replyToUserId: number) => void
  onReport: (commentId: number) => void
}) {
  const isLiked = commentLikedIds.has(comment.id)

  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{ display: 'flex', gap: 12 }}>
        <Avatar
          size={36}
          src={comment.authorAvatar || undefined}
          style={{ background: 'var(--color-gradient-primary)', flexShrink: 0 }}
        >
          {comment.authorNickname?.charAt(0) || '?'}
        </Avatar>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
            <span
              style={{ color: 'var(--color-text-secondary)', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}
              onClick={() => history.push(`/user/${comment.userId}`)}
            >
              {comment.authorNickname}
            </span>
            {comment.replyToNickname && (
              <>
                <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>回复</span>
                <span style={{ color: 'var(--color-primary)', fontSize: 13, fontWeight: 500 }}>
                  {comment.replyToNickname}
                </span>
              </>
            )}
            <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12, marginLeft: 'auto' }}>
              {timeAgo(comment.createdAt)}
            </span>
            <Dropdown
              menu={{
                items: [
                  {
                    key: 'report',
                    icon: <WarningOutlined />,
                    label: '举报',
                    onClick: () => onReport(comment.id),
                  },
                ],
              }}
              placement="bottomRight"
              trigger={['click']}
            >
              <button
                type="button"
                style={{
                  border: 'none',
                  background: 'transparent',
                  color: 'var(--color-text-tertiary)',
                  fontSize: 14,
                  cursor: 'pointer',
                  padding: '0 4px',
                  transition: 'color 0.2s',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.color = 'var(--color-text-secondary)' }}
                onMouseLeave={(e) => { e.currentTarget.style.color = 'var(--color-text-tertiary)' }}
              >
                <MoreOutlined />
              </button>
            </Dropdown>
          </div>
          <div style={{ color: '#C8D6E5', fontSize: 14, lineHeight: 1.7, marginBottom: 8 }}>
            {renderCommentContent(comment.content)}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <button
              type="button"
              onClick={() => onToggleLike(comment.id)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 4,
                border: 'none',
                background: 'transparent',
                color: isLiked ? '#FF6B6B' : 'var(--color-text-tertiary)',
                fontSize: 12,
                cursor: 'pointer',
                padding: 0,
                transition: 'color 0.2s',
              }}
            >
              {isLiked ? <LikeFilled /> : <LikeOutlined />}
              {comment.likeCount > 0 && <span>{formatCount(comment.likeCount)}</span>}
            </button>
            <button
              type="button"
              onClick={() => onReply(comment.authorNickname, comment.parentId ?? comment.id, comment.userId)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 4,
                border: 'none',
                background: 'transparent',
                color: 'var(--color-text-tertiary)',
                fontSize: 12,
                cursor: 'pointer',
                padding: 0,
                transition: 'color 0.2s',
              }}
            >
              <MessageOutlined />
              回复
            </button>
          </div>

          {comment.replies && comment.replies.length > 0 && (
            <div style={{
              marginTop: 12,
              paddingLeft: 16,
              borderLeft: '2px solid var(--color-border)',
            }}>
              {comment.replies.map((reply) => (
                <CommentItem
                  key={reply.id}
                  comment={reply}
                  commentLikedIds={commentLikedIds}
                  onToggleLike={onToggleLike}
                  onReply={onReply}
                  onReport={onReport}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function renderCommentContent(content: string) {
  const parts = content.split(/(@[\w\u4e00-\u9fa5]+)/g)
  return parts.map((part, i) => {
    if (part.startsWith('@') && part.length > 1) {
      const nickname = part.slice(1)
      return (
        <span
          key={i}
          style={{ color: 'var(--color-primary)', cursor: 'pointer' }}
          onClick={() => history.push(`/search?q=${encodeURIComponent(nickname)}`)}
        >
          {part}
        </span>
      )
    }
    return part
  })
}

export default function PostDetail() {
  const { id } = useParams<{ id: string }>()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const currentUser = useAuthStore((s) => s.user)

  const [post, setPost] = useState<Post | null>(null)
  const [comments, setComments] = useState<PostComment[]>([])
  const [loading, setLoading] = useState(true)
  const [liked, setLiked] = useState(false)
  const [collected, setCollected] = useState(false)
  const [commentLikedIds, setCommentLikedIds] = useState<Set<number>>(new Set())
  const [commentText, setCommentText] = useState('')
  const [replyTarget, setReplyTarget] = useState<{
    nickname: string
    parentId: number
    replyToUserId: number
  } | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [shareModalVisible, setShareModalVisible] = useState(false)
  const [reportModalVisible, setReportModalVisible] = useState(false)
  const [reportTarget, setReportTarget] = useState<{ type: 'POST' | 'COMMENT'; id: number }>({
    type: 'POST',
    id: 0,
  })
  const [relatedPosts, setRelatedPosts] = useState<Post[]>([])
  const [relatedLoading, setRelatedLoading] = useState(false)
  const [mentionSearch, setMentionSearch] = useState('')
  const [mentionResults, setMentionResults] = useState<SearchUserResult[]>([])
  const [mentionVisible, setMentionVisible] = useState(false)
  const mentionTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const fetchPost = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const { data: res } = await getPostDetail(Number(id))
      const postData = res.data
      setPost(postData)
      setLiked(postData.isLiked)
      setCollected(postData.isCollected)
    } catch {
      setPost(null)
    } finally {
      setLoading(false)
    }
  }, [id])

  const fetchComments = useCallback(async () => {
    if (!id) return
    try {
      const { data: res } = await getPostComments(Number(id), 1, 50)
      const commentList = res.data ?? []
      setComments(commentList)
      const likedSet = new Set<number>()
      const collectLiked = (items: PostComment[]) => {
        for (const item of items) {
          if (item.isLiked) likedSet.add(item.id)
          if (item.replies?.length) collectLiked(item.replies)
        }
      }
      collectLiked(commentList)
      setCommentLikedIds(likedSet)
    } catch {
      setComments([])
    }
  }, [id])

  useEffect(() => {
    fetchPost()
    fetchComments()
  }, [fetchPost, fetchComments])

  useEffect(() => {
    if (!id) return
    const interval = setInterval(() => {
      fetchComments()
    }, 15000)
    return () => clearInterval(interval)
  }, [id, fetchComments])

  const fetchRelatedPosts = useCallback(async (tags: Array<{ id: number }>, currentPostId: number) => {
    if (!tags.length) return
    setRelatedLoading(true)
    try {
      const firstTagId = tags[0].id
      const { data: res } = await getPostsByTopic(firstTagId, 1, 8)
      const posts = ((res.data ?? []) as unknown as Post[]).filter((p) => p.id !== currentPostId)
      setRelatedPosts(posts.slice(0, 4))
    } catch {
      try {
        const { data: res } = await getFeedPosts('recommend', 1, 8)
        const posts = ((res.data ?? []) as unknown as Post[]).filter((p) => p.id !== currentPostId)
        setRelatedPosts(posts.slice(0, 4))
      } catch {
        setRelatedPosts([])
      }
    } finally {
      setRelatedLoading(false)
    }
  }, [])

  useEffect(() => {
    if (post?.tags?.length) {
      fetchRelatedPosts(post.tags, post.id)
    }
  }, [post, fetchRelatedPosts])

  const handleDeletePost = useCallback(async () => {
    if (!post || !id) return
    try {
      await deletePost(Number(id))
      message.success('帖子已删除')
      history.push('/')
    } catch {
      message.error('删除失败')
    }
  }, [post, id])

  const handleToggleLike = useCallback(async () => {
    if (!post || !isAuthenticated) {
      message.warning('请先登录')
      return
    }
    const willLike = !liked
    setLiked(willLike)
    setPost((prev) => prev ? {
      ...prev,
      likeCount: willLike ? prev.likeCount + 1 : Math.max(0, prev.likeCount - 1),
    } : prev)
    try {
      willLike ? await likePost(post.id) : await unlikePost(post.id)
    } catch {
      setLiked(!willLike)
      setPost((prev) => prev ? {
        ...prev,
        likeCount: !willLike ? prev.likeCount + 1 : Math.max(0, prev.likeCount - 1),
      } : prev)
    }
  }, [post, liked, isAuthenticated])

  const handleToggleCollect = useCallback(async () => {
    if (!post || !isAuthenticated) {
      message.warning('请先登录')
      return
    }
    const willCollect = !collected
    setCollected(willCollect)
    setPost((prev) => prev ? {
      ...prev,
      collectCount: willCollect ? prev.collectCount + 1 : Math.max(0, prev.collectCount - 1),
    } : prev)
    try {
      willCollect ? await collectPost(post.id) : await uncollectPost(post.id)
    } catch {
      setCollected(!willCollect)
      setPost((prev) => prev ? {
        ...prev,
        collectCount: !willCollect ? prev.collectCount + 1 : Math.max(0, prev.collectCount - 1),
      } : prev)
    }
  }, [post, collected, isAuthenticated])

  const handleToggleCommentLike = useCallback(async (commentId: number) => {
    if (!isAuthenticated) {
      message.warning('请先登录')
      return
    }
    const isCurrentlyLiked = commentLikedIds.has(commentId)
    const newSet = new Set(commentLikedIds)
    if (isCurrentlyLiked) {
      newSet.delete(commentId)
    } else {
      newSet.add(commentId)
    }
    setCommentLikedIds(newSet)

    const updateLikeCount = (items: PostComment[]): PostComment[] =>
      items.map((c) => {
        if (c.id === commentId) {
          return {
            ...c,
            likeCount: isCurrentlyLiked ? Math.max(0, c.likeCount - 1) : c.likeCount + 1,
          }
        }
        if (c.replies?.length) {
          return { ...c, replies: updateLikeCount(c.replies) }
        }
        return c
      })
    setComments((prev) => updateLikeCount(prev))

    try {
      isCurrentlyLiked ? await unlikeComment(commentId) : await likeComment(commentId)
    } catch {
      setCommentLikedIds(commentLikedIds)
      setComments((prev) =>
        prev.map((c) => {
          if (c.id === commentId) {
            return {
              ...c,
              likeCount: isCurrentlyLiked ? c.likeCount + 1 : Math.max(0, c.likeCount - 1),
            }
          }
          return c
        }),
      )
    }
  }, [commentLikedIds, isAuthenticated])

  const handleReply = useCallback((nickname: string, parentId: number, replyToUserId: number) => {
    setReplyTarget({ nickname, parentId, replyToUserId })
    setCommentText(`@${nickname} `)
  }, [])

  const handleSubmitComment = useCallback(async () => {
    if (!id || !commentText.trim() || !isAuthenticated) {
      if (!isAuthenticated) message.warning('请先登录')
      return
    }
    setSubmitting(true)
    try {
      await createComment(
        Number(id),
        {
          content: commentText.trim(),
          parentId: replyTarget?.parentId,
          replyToUserId: replyTarget?.replyToUserId,
        },
      )
      setCommentText('')
      setReplyTarget(null)
      await fetchComments()
      if (post) {
        setPost((prev) => prev ? { ...prev, commentCount: prev.commentCount + 1 } : prev)
      }
      message.success('评论成功')
    } catch {
      message.error('评论失败')
    } finally {
      setSubmitting(false)
    }
  }, [id, commentText, replyTarget, isAuthenticated, fetchComments, post])

  const handleShare = useCallback(() => {
    setShareModalVisible(true)
  }, [])

  const handleReportPost = useCallback(() => {
    if (!isAuthenticated) {
      message.warning('请先登录')
      return
    }
    setReportTarget({ type: 'POST', id: post!.id })
    setReportModalVisible(true)
  }, [isAuthenticated, post])

  const handleReportComment = useCallback((commentId: number) => {
    if (!isAuthenticated) {
      message.warning('请先登录')
      return
    }
    setReportTarget({ type: 'COMMENT', id: commentId })
    setReportModalVisible(true)
  }, [isAuthenticated])

  const handleCommentTextChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const value = e.target.value
    setCommentText(value)

    const lastAtIndex = value.lastIndexOf('@')
    if (lastAtIndex !== -1) {
      const afterAt = value.slice(lastAtIndex + 1)
      const hasSpace = afterAt.includes(' ')
      if (!hasSpace && afterAt.length > 0 && afterAt.length <= 20) {
        setMentionSearch(afterAt)
        setMentionVisible(true)
        if (mentionTimerRef.current) clearTimeout(mentionTimerRef.current)
        mentionTimerRef.current = setTimeout(async () => {
          try {
            const res = await searchUsers(afterAt)
            setMentionResults(res.data.data ?? [])
          } catch {
            setMentionResults([])
          }
        }, 300)
      } else if (hasSpace) {
        setMentionVisible(false)
        setMentionResults([])
      }
    } else {
      setMentionVisible(false)
      setMentionResults([])
    }
  }, [])

  const handleSelectMention = useCallback((user: { id: number; nickname: string }) => {
    const lastAtIndex = commentText.lastIndexOf('@')
    if (lastAtIndex !== -1) {
      const before = commentText.slice(0, lastAtIndex)
      const newText = `${before}@${user.nickname} `
      setCommentText(newText)
    }
    setMentionVisible(false)
    setMentionResults([])
  }, [commentText])

  if (loading) {
    return (
      <div style={{
        background: 'var(--color-bg-base)',
        minHeight: '100vh',
        padding: '20px',
      }}>
        <Skeleton variant="detail" />
      </div>
    )
  }

  if (!post) {
    return (
      <div style={{
        background: 'var(--color-bg-base)',
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 16,
      }}>
        <Empty description={<span style={{ color: 'var(--color-text-tertiary)' }}>帖子不存在或已被删除</span>} />
        <button
          type="button"
          onClick={() => history.back()}
          style={{
            padding: '8px 24px',
            border: '1px solid var(--color-border)',
            borderRadius: '8px',
            background: 'transparent',
            color: 'var(--color-text-secondary)',
            fontSize: 14,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          <ArrowLeftOutlined /> 返回
        </button>
      </div>
    )
  }

  return (
    <div style={{ background: 'var(--color-bg-base)', minHeight: '100vh', paddingBottom: 80 }}>
      <div style={{ maxWidth: 800, margin: '0 auto', padding: '20px 24px' }}>
        <button
          type="button"
          onClick={() => history.back()}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            border: 'none',
            background: 'transparent',
            color: 'var(--color-text-secondary)',
            fontSize: 14,
            cursor: 'pointer',
            padding: '8px 0',
            marginBottom: 16,
            transition: 'color 0.2s',
          }}
          onMouseEnter={(e) => { e.currentTarget.style.color = 'var(--color-primary)' }}
          onMouseLeave={(e) => { e.currentTarget.style.color = 'var(--color-text-secondary)' }}
        >
          <ArrowLeftOutlined /> 返回
        </button>

        <div style={{
          background: 'var(--color-bg-container)',
          borderRadius: '16px',
          border: '1px solid var(--color-border)',
          overflow: 'hidden',
        }}>
          <div style={{ padding: '24px 28px 0' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
              <Avatar
                size={44}
                src={post.authorAvatar || undefined}
                style={{ background: 'var(--color-gradient-primary)', flexShrink: 0, cursor: 'pointer' }}
                onClick={() => history.push(`/user/${post.userId}`)}
              >
                {post.authorNickname?.charAt(0) || '?'}
              </Avatar>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div
                  style={{ color: 'var(--color-text-secondary)', fontSize: 15, fontWeight: 600, cursor: 'pointer' }}
                  onClick={() => history.push(`/user/${post.userId}`)}
                >
                  {post.authorNickname}
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>{timeAgo(post.createdAt)}</span>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
                    <EyeOutlined /> {formatCount(post.viewCount)}
                  </span>
                </div>
              </div>
              {currentUser?.id === post.userId && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <button
                    type="button"
                    onClick={() => history.push(`/publish?edit=${post.id}`)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 4,
                      border: '1px solid rgba(255, 255, 255, 0.12)',
                      borderRadius: '8px',
                      background: 'transparent',
                      color: 'var(--color-text-secondary)',
                      fontSize: 13,
                      cursor: 'pointer',
                      padding: '4px 12px',
                      transition: 'all 0.2s',
                    }}
                    onMouseEnter={(e) => { e.currentTarget.style.color = 'var(--color-primary)'; e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.4)' }}
                    onMouseLeave={(e) => { e.currentTarget.style.color = 'var(--color-text-secondary)'; e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.12)' }}
                  >
                    <EditOutlined /> 编辑
                  </button>
                  <Popconfirm
                    title="确认删除这篇帖子？"
                    description="删除后无法恢复"
                    onConfirm={handleDeletePost}
                    okText="删除"
                    cancelText="取消"
                    okButtonProps={{ danger: true }}
                  >
                    <button
                      type="button"
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 4,
                        border: '1px solid rgba(255, 107, 107, 0.2)',
                        borderRadius: '8px',
                        background: 'transparent',
                        color: '#FF6B6B',
                        fontSize: 13,
                        cursor: 'pointer',
                        padding: '4px 12px',
                        transition: 'all 0.2s',
                      }}
                      onMouseEnter={(e) => { e.currentTarget.style.borderColor = 'rgba(255, 107, 107, 0.5)'; e.currentTarget.style.background = 'rgba(255, 107, 107, 0.08)' }}
                      onMouseLeave={(e) => { e.currentTarget.style.borderColor = 'rgba(255, 107, 107, 0.2)'; e.currentTarget.style.background = 'transparent' }}
                    >
                      <DeleteOutlined /> 删除
                    </button>
                  </Popconfirm>
                </div>
              )}
            </div>

            <h1 style={{
              color: 'var(--color-text-secondary)',
              fontSize: 24,
              fontWeight: 700,
              lineHeight: 1.4,
              marginBottom: 16,
            }}>
              {post.title}
            </h1>

            {post.tags && post.tags.length > 0 && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 20 }}>
                {post.tags.map((tag, index) => {
                  const colorSet = TAG_COLORS[index % TAG_COLORS.length]
                  return (
                    <span
                      key={tag.id}
                      style={{
                        padding: '4px 12px',
                        borderRadius: '6px',
                        background: colorSet.bg,
                        border: `1px solid ${colorSet.border}`,
                        color: colorSet.text,
                        fontSize: 12,
                        fontWeight: 500,
                      }}
                    >
                      #{tag.name}
                    </span>
                  )
                })}
              </div>
            )}
          </div>

          {post.mediaUrls && post.mediaUrls.length > 0 && (
            <div style={{ padding: '0 28px' }}>
              <MediaGallery mediaUrls={post.mediaUrls} mediaType={post.mediaType} coverImage={post.coverImage} />
            </div>
          )}

          {post.content && (
            <div style={{ padding: '0 28px 24px' }}>
              <div
                dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(post.content) }}
                style={{
                  color: '#C8D6E5',
                  lineHeight: 1.8,
                  fontSize: 15,
                  wordBreak: 'break-word',
                }}
                className="post-content-dark"
              />
            </div>
          )}

          <div style={{
            borderTop: '1px solid var(--color-border)',
            padding: '16px 28px',
            display: 'flex',
            alignItems: 'center',
            gap: 24,
          }}>
            <button
              type="button"
              onClick={handleToggleLike}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                border: 'none',
                background: 'transparent',
                color: liked ? '#FF6B6B' : 'var(--color-text-secondary)',
                fontSize: 14,
                cursor: 'pointer',
                padding: '6px 0',
                transition: 'all 0.2s',
              }}
            >
              {liked ? <HeartFilled style={{ fontSize: 20 }} /> : <HeartOutlined style={{ fontSize: 20 }} />}
              <span style={{ fontWeight: liked ? 600 : 400 }}>{formatCount(post.likeCount)}</span>
            </button>

            <button
              type="button"
              onClick={handleToggleCollect}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                border: 'none',
                background: 'transparent',
                color: collected ? '#FFA502' : 'var(--color-text-secondary)',
                fontSize: 14,
                cursor: 'pointer',
                padding: '6px 0',
                transition: 'all 0.2s',
              }}
            >
              {collected ? <StarFilled style={{ fontSize: 20 }} /> : <StarOutlined style={{ fontSize: 20 }} />}
              <span style={{ fontWeight: collected ? 600 : 400 }}>{formatCount(post.collectCount)}</span>
            </button>

            <button
              type="button"
              onClick={handleShare}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                border: 'none',
                background: 'transparent',
                color: 'var(--color-text-secondary)',
                fontSize: 14,
                cursor: 'pointer',
                padding: '6px 0',
                transition: 'color 0.2s',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.color = 'var(--color-primary)' }}
              onMouseLeave={(e) => { e.currentTarget.style.color = 'var(--color-text-secondary)' }}
            >
              <ShareAltOutlined style={{ fontSize: 20 }} />
              <span>分享</span>
            </button>

            <div style={{
              marginLeft: 'auto',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              color: 'var(--color-text-secondary)',
              fontSize: 14,
            }}>
              <MessageOutlined />
              <span>{formatCount(post.commentCount)}</span>
            </div>

            <Dropdown
              menu={{
                items: [
                  {
                    key: 'report',
                    icon: <WarningOutlined />,
                    label: '举报',
                    onClick: handleReportPost,
                  },
                ],
              }}
              placement="bottomRight"
              trigger={['click']}
            >
              <button
                type="button"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  border: 'none',
                  background: 'transparent',
                  color: 'var(--color-text-secondary)',
                  fontSize: 14,
                  cursor: 'pointer',
                  padding: '6px 0',
                  transition: 'color 0.2s',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.color = '#FF6B6B' }}
                onMouseLeave={(e) => { e.currentTarget.style.color = 'var(--color-text-secondary)' }}
              >
                <MoreOutlined style={{ fontSize: 20 }} />
              </button>
            </Dropdown>
          </div>
        </div>

        <div style={{
          background: 'var(--color-bg-container)',
          borderRadius: '16px',
          border: '1px solid var(--color-border)',
          marginTop: 20,
          padding: '24px 28px',
        }}>
          <h3 style={{ color: 'var(--color-text-secondary)', fontSize: 18, fontWeight: 700, marginBottom: 20 }}>
            评论
            <span style={{ color: 'var(--color-text-tertiary)', fontSize: 14, fontWeight: 400, marginLeft: 8 }}>
              ({post.commentCount})
            </span>
          </h3>

          <div style={{ marginBottom: 24 }}>
            {replyTarget && (
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '8px 12px',
                background: 'rgba(var(--color-primary-rgb), 0.08)',
                borderRadius: '8px',
                marginBottom: 8,
              }}>
                <span style={{ color: 'var(--color-primary)', fontSize: 13 }}>
                  回复 <strong>{replyTarget.nickname}</strong>
                </span>
                <button
                  type="button"
                  onClick={() => { setReplyTarget(null); setCommentText('') }}
                  style={{
                    border: 'none',
                    background: 'transparent',
                    color: 'var(--color-text-tertiary)',
                    fontSize: 12,
                    cursor: 'pointer',
                  }}
                >
                  取消回复
                </button>
              </div>
            )}
            <div style={{ display: 'flex', gap: 12, position: 'relative' }}>
              <div style={{ flex: 1, position: 'relative' }}>
                <Input.TextArea
                  value={commentText}
                  onChange={handleCommentTextChange}
                  placeholder={isAuthenticated ? '写下你的评论... (@提及用户)' : '请先登录后再评论'}
                  disabled={!isAuthenticated}
                  autoSize={{ minRows: 2, maxRows: 4 }}
                  style={{
                    flex: 1,
                    background: 'var(--color-bg-input)',
                    border: '1px solid var(--color-border)',
                    borderRadius: '10px',
                    color: 'var(--color-text-secondary)',
                    fontSize: 14,
                    padding: '10px 16px',
                    resize: 'none',
                  }}
                  onPressEnter={(e) => {
                    if (!e.shiftKey) {
                      e.preventDefault()
                      handleSubmitComment()
                    }
                  }}
                />
                {mentionVisible && mentionResults.length > 0 && (
                  <div style={{
                    position: 'absolute',
                    bottom: '100%',
                    left: 0,
                    right: 0,
                    background: 'var(--color-bg-elevated)',
                    border: '1px solid rgba(var(--color-primary-rgb), 0.2)',
                    borderRadius: '8px',
                    maxHeight: 180,
                    overflowY: 'auto',
                    zIndex: 100,
                    boxShadow: '0 4px 16px rgba(0, 0, 0, 0.4)',
                  }}>
                    {mentionResults.map((u) => (
                      <div
                        key={u.id}
                        onClick={() => handleSelectMention(u)}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          padding: '8px 12px',
                          cursor: 'pointer',
                          transition: 'background 0.15s',
                        }}
                        onMouseEnter={(e) => { e.currentTarget.style.background = 'rgba(var(--color-primary-rgb), 0.08)' }}
                        onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent' }}
                      >
                        <Avatar size={24} src={u.avatar || undefined} style={{ background: 'var(--color-gradient-primary)', flexShrink: 0 }}>
                          {u.nickname?.charAt(0) || '?'}
                        </Avatar>
                        <span style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>{u.nickname}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <button
                type="button"
                onClick={handleSubmitComment}
                disabled={submitting || !commentText.trim() || !isAuthenticated}
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: '10px',
                  border: 'none',
                  background: commentText.trim() && isAuthenticated
                    ? 'var(--color-gradient-primary)'
                    : 'var(--color-border)',
                  color: commentText.trim() && isAuthenticated ? 'var(--color-bg-base)' : 'var(--color-text-tertiary)',
                  fontSize: 18,
                  cursor: commentText.trim() && isAuthenticated && !submitting ? 'pointer' : 'not-allowed',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  alignSelf: 'flex-end',
                  transition: 'all 0.2s',
                  boxShadow: commentText.trim() && isAuthenticated ? '0 2px 12px rgba(var(--color-primary-rgb), 0.3)' : 'none',
                }}
              >
                <SendOutlined />
              </button>
            </div>
          </div>

          {comments.length === 0 ? (
            <div style={{
              textAlign: 'center',
              padding: '40px 0',
              color: 'var(--color-text-tertiary)',
              fontSize: 14,
            }}>
              暂无评论，快来抢沙发吧~
            </div>
          ) : (
            <div>
              {comments.map((comment) => (
                <CommentItem
                  key={comment.id}
                  comment={comment}
                  commentLikedIds={commentLikedIds}
                  onToggleLike={handleToggleCommentLike}
                  onReply={handleReply}
                  onReport={handleReportComment}
                />
              ))}
            </div>
          )}
        </div>
      </div>

      {relatedPosts.length > 0 && (
        <div style={{
          background: 'var(--color-bg-container)',
          borderRadius: '16px',
          border: '1px solid var(--color-border)',
          marginTop: 20,
          padding: '24px 28px',
        }}>
          <h3 style={{ color: 'var(--color-text-secondary)', fontSize: 18, fontWeight: 700, marginBottom: 20 }}>
            相关推荐
          </h3>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(2, 1fr)',
            gap: 16,
          }}>
            {relatedPosts.map((relPost) => (
              <div
                key={relPost.id}
                onClick={() => {
                  setPost(null)
                  setComments([])
                  history.push(`/post/${relPost.id}`)
                  window.scrollTo({ top: 0, behavior: 'smooth' })
                }}
                style={{
                  background: 'var(--color-bg-input)',
                  border: '1px solid var(--color-border)',
                  borderRadius: '12px',
                  overflow: 'hidden',
                  cursor: 'pointer',
                  transition: 'all 0.25s ease',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.2)'
                  e.currentTarget.style.transform = 'translateY(-2px)'
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = 'var(--color-border)'
                  e.currentTarget.style.transform = 'translateY(0)'
                }}
              >
                {relPost.coverImage && (
                  <div style={{ height: 120, overflow: 'hidden' }}>
                    <img src={relPost.coverImage} alt={relPost.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  </div>
                )}
                <div style={{ padding: '12px 14px' }}>
                  <div style={{
                    color: 'var(--color-text-secondary)',
                    fontSize: 13,
                    fontWeight: 600,
                    lineHeight: 1.4,
                    marginBottom: 6,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}>
                    {relPost.title}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, color: 'var(--color-text-tertiary)', fontSize: 11 }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                      <HeartOutlined style={{ fontSize: 10 }} />
                      {formatCount(relPost.likeCount)}
                    </span>
                    <span style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                      <MessageOutlined style={{ fontSize: 10 }} />
                      {formatCount(relPost.commentCount)}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {relatedLoading && (
        <div style={{
          background: 'var(--color-bg-container)',
          borderRadius: '16px',
          border: '1px solid var(--color-border)',
          marginTop: 20,
          padding: '24px 28px',
          textAlign: 'center',
        }}>
          <h3 style={{ color: 'var(--color-text-secondary)', fontSize: 18, fontWeight: 700, marginBottom: 20 }}>
            相关推荐
          </h3>
          <Spin size="small" />
        </div>
      )}

      {post && (
        <ShareModal
          visible={shareModalVisible}
          onClose={() => setShareModalVisible(false)}
          postTitle={post.title}
          postId={post.id}
        />
      )}

      <ReportModal
        visible={reportModalVisible}
        onClose={() => setReportModalVisible(false)}
        targetType={reportTarget.type}
        targetId={reportTarget.id}
      />

      <style>{`
        .post-content-dark h1,
        .post-content-dark h2,
        .post-content-dark h3,
        .post-content-dark h4,
        .post-content-dark h5,
        .post-content-dark h6 {
          color: #FFFFFF;
          margin: 20px 0 12px;
          font-weight: 700;
        }
        .post-content-dark h1 { font-size: 22px; }
        .post-content-dark h2 { font-size: 20px; }
        .post-content-dark h3 { font-size: 18px; }
        .post-content-dark p {
          margin: 8px 0;
        }
        .post-content-dark a {
          color: var(--color-primary);
          text-decoration: none;
        }
        .post-content-dark a:hover {
          text-decoration: underline;
        }
        .post-content-dark img {
          max-width: 100%;
          border-radius: 8px;
          margin: 12px 0;
        }
        .post-content-dark blockquote {
          border-left: 3px solid var(--color-primary);
          padding: 8px 16px;
          margin: 12px 0;
          background: rgba(var(--color-primary-rgb), 0.05);
          border-radius: 0 8px 8px 0;
          color: var(--color-text-secondary);
        }
        .post-content-dark code {
          background: rgba(var(--color-primary-rgb), 0.1);
          padding: 2px 6px;
          border-radius: 4px;
          font-size: 13px;
          color: var(--color-primary);
        }
        .post-content-dark pre {
          background: var(--color-bg-input);
          padding: 16px;
          border-radius: 8px;
          overflow-x: auto;
          margin: 12px 0;
          border: 1px solid var(--color-border);
        }
        .post-content-dark pre code {
          background: transparent;
          padding: 0;
          color: #C8D6E5;
        }
        .post-content-dark ul,
        .post-content-dark ol {
          padding-left: 24px;
          margin: 8px 0;
        }
        .post-content-dark li {
          margin: 4px 0;
        }
        .post-content-dark hr {
          border: none;
          border-top: 1px solid var(--color-border);
          margin: 20px 0;
        }
        .post-content-dark table {
          width: 100%;
          border-collapse: collapse;
          margin: 12px 0;
        }
        .post-content-dark th,
        .post-content-dark td {
          border: 1px solid var(--color-border);
          padding: 8px 12px;
          text-align: left;
        }
        .post-content-dark th {
          background: rgba(var(--color-primary-rgb), 0.05);
          color: #FFFFFF;
          font-weight: 600;
        }
        .ant-input {
          background: var(--color-bg-input) !important;
          border-color: var(--color-border) !important;
          color: #FFFFFF !important;
        }
        .ant-input:focus,
        .ant-input-focused {
          border-color: rgba(var(--color-primary-rgb), 0.4) !important;
          box-shadow: 0 0 0 2px rgba(var(--color-primary-rgb), 0.1) !important;
        }
        .ant-input::placeholder {
          color: var(--color-text-tertiary) !important;
        }
        .ant-spin-text { color: var(--color-text-secondary) !important; }
        .ant-empty-description { color: var(--color-text-tertiary) !important; }
        .ant-message-notice-content {
          background: var(--color-bg-container) !important;
          color: #FFFFFF !important;
          border: 1px solid var(--color-border) !important;
        }
      `}</style>
    </div>
  )
}
