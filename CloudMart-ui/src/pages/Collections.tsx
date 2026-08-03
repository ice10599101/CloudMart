import { useState, useEffect, useCallback } from 'react'
import { history } from 'umi'
import { Spin, Empty } from 'antd'
import {
  ArrowLeftOutlined,
  HeartOutlined,
  MessageOutlined,
  StarOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import { getUserCollections, type CollectionPostItem } from '@/api/community'
import { useAuthStore } from '@/stores/auth'
import { formatCount, timeAgo } from '@/utils/format'

interface CollectionPost extends CollectionPostItem {
  collectedAt: string
}

function CollectionCard({ post }: { post: CollectionPost }) {
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
          position: 'relative',
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
          marginBottom: 10,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
        }}>
          {post.title}
        </h4>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
              <HeartOutlined /> {formatCount(post.likeCount)}
            </span>
            <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
              <MessageOutlined /> {formatCount(post.commentCount)}
            </span>
          </div>
          <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--color-text-tertiary)', fontSize: 11 }}>
            <StarOutlined style={{ color: '#FFD700' }} />
            {post.collectedAt ? timeAgo(post.collectedAt) : ''}
          </span>
        </div>
      </div>
    </div>
  )
}

export default function Collections() {
  const user = useAuthStore((s) => s.user)

  const [posts, setPosts] = useState<CollectionPost[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  const fetchCollections = useCallback(async (pageNum: number, append = false) => {
    if (!user?.id) return
    if (append) {
      setLoadingMore(true)
    } else {
      setLoading(true)
    }
    try {
      const { data: res } = await getUserCollections(user.id, pageNum, 20)
      const rawPosts = res.data ?? []
      const mapped: CollectionPost[] = rawPosts.map((p: CollectionPostItem) => ({
        ...p,
        collectedAt: p.collectedAt || p.createdAt || '',
      }))
      if (append) {
        setPosts((prev) => [...prev, ...mapped])
      } else {
        setPosts(mapped)
      }
      setHasMore(mapped.length >= 20)
    } catch {
      if (!append) setPosts([])
      setHasMore(false)
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [user?.id])

  useEffect(() => {
    setPage(1)
    setHasMore(true)
    fetchCollections(1)
  }, [fetchCollections])

  const handleLoadMore = useCallback(() => {
    const nextPage = page + 1
    setPage(nextPage)
    fetchCollections(nextPage, true)
  }, [page, fetchCollections])

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

          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 24 }}>
            <div style={{
              width: 52,
              height: 52,
              borderRadius: '14px',
              background: 'linear-gradient(135deg, rgba(255, 215, 0, 0.15), rgba(255, 215, 0, 0.05))',
              border: '1px solid rgba(255, 215, 0, 0.25)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}>
              <StarOutlined style={{ fontSize: 22, color: '#FFD700' }} />
            </div>
            <div>
              <h1 style={{
                color: 'var(--color-text-secondary)',
                fontSize: 22,
                fontWeight: 800,
                marginBottom: 2,
              }}>
                我的收藏
              </h1>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>
                共 {formatCount(posts.length)} 篇帖子
              </span>
            </div>
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 800, margin: '0 auto', padding: '24px 24px 80px' }}>
        {posts.length === 0 ? (
          <div style={{
            textAlign: 'center',
            padding: '80px 0',
          }}>
            <Empty
              description={
                <span style={{ color: '#5A6F88' }}>还没有收藏内容，去发现感兴趣的内容吧</span>
              }
            />
            <button
              type="button"
              onClick={() => history.push('/')}
              style={{
                marginTop: 16,
                padding: '10px 32px',
                border: 'none',
                borderRadius: '8px',
                background: 'var(--color-gradient-primary)',
                color: 'var(--color-bg-base)',
                fontSize: 14,
                fontWeight: 600,
                cursor: 'pointer',
                boxShadow: '0 2px 12px rgba(var(--color-primary-rgb), 0.3)',
                transition: 'all 0.2s',
              }}
            >
              去发现
            </button>
          </div>
        ) : (
          <>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: 16,
            }}>
              {posts.map((post) => (
                <CollectionCard key={post.id} post={post} />
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
      `}</style>
    </div>
  )
}
