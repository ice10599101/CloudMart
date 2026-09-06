import { useState, useEffect, useCallback } from 'react'
import { useParams, history } from 'umi'
import { Spin, Empty, Avatar } from 'antd'
import { message } from '@/utils/appMessage'
import {
  ArrowLeftOutlined,
  HeartOutlined,
  EyeOutlined,
  FileTextOutlined,
  TagOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import { getPostsByTopic, getHotTopics, getTagDetail, subscribeTag, unsubscribeTag, checkTagSubscription } from '@/api/community'
import type { Post, HotTopic } from '@/api/community'

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

function PostCard({ post }: { post: Post }) {
  return (
    <div
      onClick={() => history.push(`/post/${post.id}`)}
      style={{
        background: 'var(--color-bg-container)',
        borderRadius: '12px',
        border: '1px solid var(--color-border)',
        overflow: 'hidden',
        cursor: 'pointer',
        transition: 'all 0.3s ease',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
        e.currentTarget.style.transform = 'translateY(-4px)'
        e.currentTarget.style.boxShadow = '0 8px 32px rgba(0, 0, 0, 0.3), 0 0 20px rgba(var(--color-primary-rgb), 0.08)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'var(--color-border)'
        e.currentTarget.style.transform = 'translateY(0)'
        e.currentTarget.style.boxShadow = 'none'
      }}
    >
      {post.coverImage && (
        <div style={{
          width: '100%',
          aspectRatio: '4/3',
          overflow: 'hidden',
          background: 'var(--color-bg-input)',
        }}>
          <img
            src={post.coverImage}
            alt={post.title}
            style={{
              width: '100%',
              height: '100%',
              objectFit: 'cover',
              transition: 'transform 0.3s ease',
            }}
            onMouseEnter={(e) => { e.currentTarget.style.transform = 'scale(1.05)' }}
            onMouseLeave={(e) => { e.currentTarget.style.transform = 'scale(1)' }}
          />
        </div>
      )}
      {!post.coverImage && (
        <div style={{
          width: '100%',
          aspectRatio: '4/3',
          background: 'linear-gradient(135deg, var(--color-bg-input) 0%, var(--color-bg-container) 50%, var(--color-bg-elevated) 100%)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}>
          <FileTextOutlined style={{ fontSize: 32, color: 'rgba(var(--color-primary-rgb), 0.2)' }} />
        </div>
      )}
      <div style={{ padding: '12px 14px' }}>
        <h4 style={{
          color: 'var(--color-text-secondary)',
          fontSize: 14,
          fontWeight: 600,
          lineHeight: 1.4,
          marginBottom: 8,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
        }}>
          {post.title}
        </h4>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Avatar
              size={20}
              src={post.authorAvatar || undefined}
              style={{ background: 'var(--color-gradient-primary)', flexShrink: 0 }}
            >
              {post.authorNickname?.charAt(0) || '?'}
            </Avatar>
            <span style={{ color: 'var(--color-text-secondary)', fontSize: 12, maxWidth: 80, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {post.authorNickname}
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
              <HeartOutlined /> {formatCount(post.likeCount)}
            </span>
            <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
              <EyeOutlined /> {formatCount(post.viewCount)}
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function TopicDetail() {
  const { id } = useParams<{ id: string }>()
  const tagId = id ?? ''

  const [tagInfo, setTagInfo] = useState<HotTopic | null>(null)
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [isFollowed, setIsFollowed] = useState(false)

  const fetchTagInfo = useCallback(async () => {
    if (!tagId) return
    try {
      // 优先取话题详情接口（热榜可能不含该话题导致名称回退为占位符）
      const { data: res } = await getTagDetail(tagId)
      if (res.data?.id) {
        setTagInfo(res.data)
        return
      }
      const { data: hotRes } = await getHotTopics()
      const found = (hotRes.data ?? []).find((t) => String(t.id) === tagId)
      setTagInfo(found ?? { id: tagId, name: `话题#${tagId}`, icon: '', postCount: 0, isHot: false })
    } catch {
      setTagInfo({ id: tagId, name: `话题#${tagId}`, icon: '', postCount: 0, isHot: false })
    }
  }, [tagId])

  const fetchPosts = useCallback(async (pageNum: number, append = false) => {
    if (!tagId) return
    if (append) {
      setLoadingMore(true)
    } else {
      setLoading(true)
    }
    try {
      const { data: res } = await getPostsByTopic(tagId, pageNum, 20)
      const newPosts = res.data ?? []
      if (append) {
        setPosts((prev) => [...prev, ...newPosts])
      } else {
        setPosts(newPosts)
      }
      setHasMore(newPosts.length >= 20)
    } catch {
      if (!append) setPosts([])
      setHasMore(false)
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [tagId])

  useEffect(() => {
    fetchTagInfo()
    setPage(1)
    setHasMore(true)
    fetchPosts(1)
    checkTagSubscription(tagId).then((res) => {
      const subscribed = res.data.data ?? false
      setIsFollowed(subscribed)
    }).catch(() => setIsFollowed(false))
  }, [fetchTagInfo, fetchPosts, tagId])

  const handleLoadMore = useCallback(() => {
    const nextPage = page + 1
    setPage(nextPage)
    fetchPosts(nextPage, true)
  }, [page, fetchPosts])

  const handleFollowTopic = useCallback(async () => {
    try {
      if (isFollowed) {
        await unsubscribeTag(tagId)
        setIsFollowed(false)
        message.success('已取消关注')
      } else {
        await subscribeTag(tagId)
        setIsFollowed(true)
        message.success('关注成功')
      }
    } catch {
      message.error(isFollowed ? '取消关注失败' : '关注失败')
    }
  }, [isFollowed, tagId])

  if (loading) {
    return (
      <div style={{
        background: 'var(--color-bg-base)',
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}>
        <Spin size="large" />
      </div>
    )
  }

  return (
    <div style={{ background: 'var(--color-bg-base)', minHeight: '100vh' }}>
      <div style={{
        background: 'var(--color-gradient-hero)',
        padding: '24px 24px 0',
        position: 'relative',
        overflow: 'hidden',
      }}>
        <div style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: 500,
          height: 300,
          background: 'radial-gradient(ellipse at center, rgba(var(--color-primary-rgb), 0.06) 0%, transparent 70%)',
          borderRadius: '50%',
          filter: 'blur(60px)',
        }} />

        <div style={{ maxWidth: 800, margin: '0 auto', position: 'relative' }}>
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
              marginBottom: 20,
              transition: 'color 0.2s',
            }}
            onMouseEnter={(e) => { e.currentTarget.style.color = 'var(--color-primary)' }}
            onMouseLeave={(e) => { e.currentTarget.style.color = 'var(--color-text-secondary)' }}
          >
            <ArrowLeftOutlined /> 返回
          </button>

          <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 }}>
            <div style={{
              width: 56,
              height: 56,
              borderRadius: '14px',
              background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.15), rgba(var(--color-primary-rgb), 0.05))',
              border: '1px solid rgba(var(--color-primary-rgb), 0.2)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}>
              {tagInfo?.icon ? (
                <span style={{ fontSize: 24 }}>{tagInfo.icon}</span>
              ) : (
                <TagOutlined style={{ fontSize: 24, color: 'var(--color-primary)' }} />
              )}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <h1 style={{
                color: 'var(--color-text-secondary)',
                fontSize: 24,
                fontWeight: 800,
                marginBottom: 4,
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}>
                #{tagInfo?.name || `话题#${tagId}`}
                {tagInfo?.isHot && (
                  <span style={{
                    padding: '2px 8px',
                    borderRadius: '4px',
                    background: 'rgba(255, 107, 107, 0.15)',
                    border: '1px solid rgba(255, 107, 107, 0.25)',
                    color: '#FF6B6B',
                    fontSize: 11,
                    fontWeight: 600,
                  }}>
                    热门
                  </span>
                )}
              </h1>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>
                {formatCount(tagInfo?.postCount ?? 0)} 篇帖子
              </span>
            </div>
            <button
              type="button"
              onClick={handleFollowTopic}
              style={{
                padding: '8px 24px',
                border: isFollowed ? '1px solid var(--color-border)' : 'none',
                borderRadius: '8px',
                background: isFollowed
                  ? 'transparent'
                  : 'var(--color-gradient-primary)',
                color: isFollowed ? 'var(--color-text-secondary)' : 'var(--color-bg-base)',
                fontSize: 14,
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.2s',
                boxShadow: isFollowed ? 'none' : '0 2px 12px rgba(var(--color-primary-rgb), 0.3)',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                flexShrink: 0,
              }}
            >
              {isFollowed ? '已关注' : <><PlusOutlined /> 关注话题</>}
            </button>
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 800, margin: '0 auto', padding: '24px 24px 80px' }}>
        {posts.length === 0 ? (
          <div style={{
            textAlign: 'center',
            padding: '80px 0',
          }}>
            <Empty description={<span style={{ color: 'var(--color-text-tertiary)' }}>该话题下暂无帖子</span>} />
          </div>
        ) : (
          <>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: 16,
            }}>
              {posts.map((post) => (
                <PostCard key={post.id} post={post} />
              ))}
            </div>
            {hasMore && (
              <div style={{ textAlign: 'center', padding: '32px 0' }}>
                <button
                  type="button"
                  onClick={handleLoadMore}
                  disabled={loadingMore}
                  style={{
                    padding: '10px 40px',
                    border: '1px solid var(--color-border)',
                    borderRadius: '8px',
                    background: 'transparent',
                    color: 'var(--color-text-secondary)',
                    fontSize: 14,
                    cursor: loadingMore ? 'not-allowed' : 'pointer',
                    transition: 'all 0.2s',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    margin: '0 auto',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
                    e.currentTarget.style.color = 'var(--color-primary)'
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = 'var(--color-border)'
                    e.currentTarget.style.color = 'var(--color-text-secondary)'
                  }}
                >
                  {loadingMore ? <Spin size="small" /> : '加载更多'}
                </button>
              </div>
            )}
          </>
        )}
      </div>

      <style>{`
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
