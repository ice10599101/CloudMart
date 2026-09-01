import { useState, useEffect, useCallback } from 'react'
import { useParams, useSearchParams, history } from 'umi'
import { Spin, Empty, Avatar, message } from 'antd'
import {
  ArrowLeftOutlined,
  TeamOutlined,
  UserAddOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import { getFollowers, getFollowingList, followUser, unfollowUser, type FollowUserRawItem } from '@/api/community'
import { useAuthStore } from '@/stores/auth'

interface FollowUser {
  id: number
  nickname: string
  avatar: string
  signature: string
  isFollowed: boolean
  isMutual: boolean
}

export default function Following() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const userId = id ?? ''
  const currentUser = useAuthStore((s) => s.user)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const isOwnProfile = String(currentUser?.id ?? '') === userId

  const initialTab = searchParams.get('tab') === 'followers' ? 'followers' : 'following'
  const [activeTab, setActiveTab] = useState<'following' | 'followers'>(initialTab)
  const [users, setUsers] = useState<FollowUser[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  const fetchUsers = useCallback(async (tab: 'following' | 'followers', pageNum: number, append = false) => {
    if (!userId) return
    if (append) {
      setLoadingMore(true)
    } else {
      setLoading(true)
    }
    try {
      const fetchFn = tab === 'followers' ? getFollowers : getFollowingList
      const { data: res } = await fetchFn(userId, pageNum, 20)
      const rawUsers = res.data ?? []
      const mapped: FollowUser[] = rawUsers.map((u: FollowUserRawItem) => ({
        id: u.id ?? u.userId,
        nickname: u.nickname || `用户#${u.id ?? u.userId}`,
        avatar: u.avatar || '',
        signature: u.signature || '',
        isFollowed: u.isFollowed ?? false,
        isMutual: u.isMutual ?? false,
      }))
      if (append) {
        setUsers((prev) => [...prev, ...mapped])
      } else {
        setUsers(mapped)
      }
      setHasMore(mapped.length >= 20)
    } catch {
      if (!append) setUsers([])
      setHasMore(false)
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [userId])

  useEffect(() => {
    setPage(1)
    setHasMore(true)
    fetchUsers(activeTab, 1)
  }, [activeTab, fetchUsers])

  const handleLoadMore = useCallback(() => {
    const nextPage = page + 1
    setPage(nextPage)
    fetchUsers(activeTab, nextPage, true)
  }, [page, activeTab, fetchUsers])

  const handleToggleFollow = useCallback(async (targetUserId: number, currentlyFollowed: boolean) => {
    if (!isAuthenticated) {
      message.warning('请先登录')
      return
    }
    const willFollow = !currentlyFollowed
    setUsers((prev) =>
      prev.map((u) =>
        u.id === targetUserId
          ? { ...u, isFollowed: willFollow }
          : u,
      ),
    )
    try {
      willFollow ? await followUser(targetUserId) : await unfollowUser(targetUserId)
    } catch {
      setUsers((prev) =>
        prev.map((u) =>
          u.id === targetUserId
            ? { ...u, isFollowed: !willFollow }
            : u,
        ),
      )
    }
  }, [isAuthenticated])

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

          <div style={{
            display: 'flex',
            gap: 0,
            borderBottom: '1px solid var(--color-border)',
          }}>
            {([
              { key: 'following' as const, label: '关注', icon: <UserAddOutlined /> },
              { key: 'followers' as const, label: '粉丝', icon: <TeamOutlined /> },
            ]).map((tab) => (
              <button
                key={tab.key}
                type="button"
                onClick={() => setActiveTab(tab.key)}
                style={{
                  padding: '16px 32px',
                  border: 'none',
                  background: 'transparent',
                  color: activeTab === tab.key ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                  fontSize: 15,
                  fontWeight: activeTab === tab.key ? 700 : 400,
                  cursor: 'pointer',
                  transition: 'all 0.3s ease',
                  borderBottom: activeTab === tab.key ? '2px solid var(--color-primary)' : '2px solid transparent',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                }}
              >
                {tab.icon}
                {tab.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 800, margin: '0 auto', padding: '16px 24px 80px' }}>
        {users.length === 0 ? (
          <div style={{
            textAlign: 'center',
            padding: '80px 0',
          }}>
            <Empty description={
              <span style={{ color: '#5A6F88' }}>
                {activeTab === 'following' ? '还没有关注任何人' : '还没有粉丝'}
              </span>
            } />
          </div>
        ) : (
          <>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {users.map((user) => (
                <div
                  key={user.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 14,
                    padding: '14px 16px',
                    background: 'var(--color-bg-container)',
                    borderRadius: '12px',
                    border: '1px solid var(--color-border)',
                    transition: 'all 0.2s ease',
                    cursor: 'pointer',
                  }}
                  onClick={() => history.push(`/user/${user.id}`)}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.2)'
                    e.currentTarget.style.background = 'rgba(21, 32, 56, 0.8)'
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = 'var(--color-border)'
                    e.currentTarget.style.background = 'var(--color-bg-container)'
                  }}
                >
                  <Avatar
                    size={44}
                    src={user.avatar || undefined}
                    style={{
                      background: 'var(--color-gradient-primary)',
                      flexShrink: 0,
                      border: '2px solid rgba(var(--color-primary-rgb), 0.2)',
                    }}
                  >
                    {user.nickname?.charAt(0) || '?'}
                  </Avatar>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      marginBottom: 2,
                    }}>
                      <span style={{
                        color: 'var(--color-text-secondary)',
                        fontSize: 14,
                        fontWeight: 600,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}>
                        {user.nickname}
                      </span>
                      {isOwnProfile && user.isMutual && (
                        <span style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 3,
                          padding: '1px 6px',
                          borderRadius: '4px',
                          background: 'rgba(var(--color-primary-rgb), 0.1)',
                          border: '1px solid rgba(var(--color-primary-rgb), 0.2)',
                          color: 'var(--color-primary)',
                          fontSize: 11,
                          fontWeight: 500,
                          flexShrink: 0,
                        }}>
                          <SwapOutlined style={{ fontSize: 10 }} />
                          互相关注
                        </span>
                      )}
                    </div>
                    {user.signature && (
                      <p style={{
                        color: 'var(--color-text-secondary)',
                        fontSize: 12,
                        lineHeight: 1.4,
                        margin: 0,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}>
                        {user.signature}
                      </p>
                    )}
                  </div>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation()
                      handleToggleFollow(user.id, user.isFollowed)
                    }}
                    style={{
                      padding: '6px 20px',
                      border: user.isFollowed ? '1px solid var(--color-border)' : 'none',
                      borderRadius: '6px',
                      background: user.isFollowed
                        ? 'transparent'
                        : 'var(--color-gradient-primary)',
                      color: user.isFollowed ? 'var(--color-text-secondary)' : 'var(--color-bg-base)',
                      fontSize: 13,
                      fontWeight: 600,
                      cursor: 'pointer',
                      transition: 'all 0.2s',
                      flexShrink: 0,
                    }}
                  >
                    {user.isFollowed ? '已关注' : '关注'}
                  </button>
                </div>
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
