import { useState, useEffect, useCallback } from 'react'
import { useParams, history } from 'umi'
import { Spin, Empty, Avatar, message, Popconfirm } from 'antd'
import Skeleton from '@/components/Skeleton'
import {
  ArrowLeftOutlined,
  HeartOutlined,
  EyeOutlined,
  UserAddOutlined,
  TeamOutlined,
  FileTextOutlined,
  StarOutlined,
  TrophyOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons'
import {
  getUserProfile as getCommunityProfile,
  followUser,
  unfollowUser,
  getUserPosts,
  getUserCollections,
  blockUser,
  unblockUser,
  checkBlockStatus,
} from '@/api/community'
import { createConversation } from '@/api/chat'
import type { Post } from '@/api/community'
import { useAuthStore } from '@/stores/auth'

interface CommunityUserProfile {
  userId: number
  nickname: string
  avatar: string
  signature: string
  postCount: number
  followCount: number
  followerCount: number
  collectCount: number
  badges: Array<{ id: number; name: string; icon: string; description: string }>
  isFollowed: boolean
}

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

const BADGE_COLORS: Record<string, { bg: string; border: string; text: string }> = {
  default: { bg: 'rgba(var(--color-primary-rgb), 0.12)', border: 'rgba(var(--color-primary-rgb), 0.25)', text: 'var(--color-primary)' },
  gold: { bg: 'rgba(255, 215, 0, 0.12)', border: 'rgba(255, 215, 0, 0.25)', text: '#FFD700' },
  purple: { bg: 'rgba(160, 120, 255, 0.12)', border: 'rgba(160, 120, 255, 0.25)', text: '#A078FF' },
  red: { bg: 'rgba(255, 107, 107, 0.12)', border: 'rgba(255, 107, 107, 0.25)', text: '#FF6B6B' },
  green: { bg: 'rgba(46, 213, 115, 0.12)', border: 'rgba(46, 213, 115, 0.25)', text: '#2ED573' },
}

function getBadgeColor(index: number) {
  const keys = Object.keys(BADGE_COLORS)
  return BADGE_COLORS[keys[index % keys.length]] ?? BADGE_COLORS.default
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
  )
}

export default function UserProfile() {
  const { id } = useParams<{ id: string }>()
  const currentUser = useAuthStore((s) => s.user)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  const [profile, setProfile] = useState<CommunityUserProfile | null>(null)
  const [posts, setPosts] = useState<Post[]>([])
  const [collections, setCollections] = useState<Post[]>([])
  const [loading, setLoading] = useState(true)
  const [isFollowed, setIsFollowed] = useState(false)
  const [followLoading, setFollowLoading] = useState(false)
  const [chatLoading, setChatLoading] = useState(false)
  const [isBlocked, setIsBlocked] = useState(false)
  const [blockLoading, setBlockLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<'posts' | 'collections'>('posts')

  const isOwnProfile = String(currentUser?.id ?? '') === (id ?? '')

  const fetchProfile = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const { data: res } = await getCommunityProfile(id)
      const profileData = res.data
      setProfile({
        userId: profileData.id,
        nickname: profileData.nickname,
        avatar: profileData.avatar,
        signature: profileData.signature || '',
        postCount: profileData.postCount,
        followCount: profileData.followCount,
        followerCount: profileData.followerCount,
        collectCount: profileData.collectCount,
        badges: profileData.badges,
        isFollowed: profileData.isFollowed ?? false,
      })
      setIsFollowed(profileData.isFollowed ?? false)
    } catch {
      setProfile(null)
    } finally {
      setLoading(false)
    }
  }, [id])

  const checkBlock = useCallback(async () => {
    if (!id || isOwnProfile) return
    try {
      const { data: res } = await checkBlockStatus(id)
      setIsBlocked(res.data ?? false)
    } catch {
      setIsBlocked(false)
    }
  }, [id, isOwnProfile])

  const fetchPosts = useCallback(async () => {
    if (!id) return
    try {
      const { data: res } = await getUserPosts(id, 1, 20)
      setPosts(res.data ?? [])
    } catch {
      setPosts([])
    }
  }, [id])

  const fetchCollections = useCallback(async () => {
    if (!id) return
    try {
      const { data: res } = await getUserCollections(id, 1, 20)
      setCollections(res.data ?? [])
    } catch {
      setCollections([])
    }
  }, [id])

  useEffect(() => {
    fetchProfile()
    fetchPosts()
    checkBlock()
  }, [fetchProfile, fetchPosts, checkBlock])

  useEffect(() => {
    if (activeTab === 'collections' && collections.length === 0) {
      fetchCollections()
    }
  }, [activeTab, collections.length, fetchCollections])

  const handleToggleFollow = useCallback(async () => {
    if (!isAuthenticated) {
      message.warning('请先登录')
      return
    }
    const userId = id ?? ''
    setFollowLoading(true)
    const willFollow = !isFollowed
    setIsFollowed(willFollow)
    setProfile((prev) => prev ? {
      ...prev,
      followerCount: willFollow ? prev.followerCount + 1 : Math.max(0, prev.followerCount - 1),
    } : prev)
    try {
      willFollow ? await followUser(userId) : await unfollowUser(userId)
    } catch {
      setIsFollowed(!willFollow)
      setProfile((prev) => prev ? {
        ...prev,
        followerCount: !willFollow ? prev.followerCount + 1 : Math.max(0, prev.followerCount - 1),
      } : prev)
    } finally {
      setFollowLoading(false)
    }
  }, [id, isFollowed, isAuthenticated])

  const handleStartChat = useCallback(async () => {
    if (!isAuthenticated) {
      message.warning('请先登录')
      return
    }
    if (!id) return
    setChatLoading(true)
    try {
      const { data: res } = await createConversation(id)
      const conv = res.data
      if (conv) {
        history.push(`/chat/${conv.id}`)
      }
    } catch {
      message.error('创建对话失败，请稍后重试')
    } finally {
      setChatLoading(false)
    }
  }, [id, isAuthenticated])

  const handleToggleBlock = useCallback(async () => {
    if (!isAuthenticated) {
      message.warning('请先登录')
      return
    }
    if (!id) return
    setBlockLoading(true)
    const willBlock = !isBlocked
    try {
      willBlock ? await blockUser(id) : await unblockUser(id)
      setIsBlocked(willBlock)
      message.success(willBlock ? '已拉黑' : '已取消拉黑')
    } catch {
      message.error('操作失败')
    } finally {
      setBlockLoading(false)
    }
  }, [id, isBlocked, isAuthenticated])

  if (loading) {
    return (
      <div style={{
        background: 'var(--color-bg-base)',
        minHeight: '100vh',
        padding: '20px',
      }}>
        <Skeleton variant="profile" count={6} />
      </div>
    )
  }

  if (!profile) {
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
        <Empty description={<span style={{ color: 'var(--color-text-tertiary)' }}>用户不存在</span>} />
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

  const stats = [
    { label: '帖子', value: profile.postCount, icon: <FileTextOutlined />, action: () => setActiveTab('posts') },
    { label: '粉丝', value: profile.followerCount, icon: <TeamOutlined />, action: () => history.push(`/user/${id}/following?tab=followers`) },
    { label: '关注', value: profile.followCount, icon: <UserAddOutlined />, action: () => history.push(`/user/${id}/following?tab=following`) },
    { label: '收藏', value: profile.collectCount, icon: <StarOutlined />, action: () => setActiveTab('collections') },
  ]

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

          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 24, marginBottom: 24 }}>
            <Avatar
              size={88}
              src={profile.avatar || undefined}
              style={{
                background: 'var(--color-gradient-primary)',
                flexShrink: 0,
                border: '3px solid rgba(var(--color-primary-rgb), 0.3)',
                boxShadow: '0 4px 24px rgba(var(--color-primary-rgb), 0.2)',
              }}
            >
              {profile.nickname?.charAt(0) || '?'}
            </Avatar>
            <div style={{ flex: 1, minWidth: 0 }}>
              <h1 style={{
                color: 'var(--color-text-secondary)',
                fontSize: 26,
                fontWeight: 800,
                marginBottom: 6,
                lineHeight: 1.3,
              }}>
                {profile.nickname}
              </h1>
              {profile.signature && (
                <p style={{
                  color: 'var(--color-text-secondary)',
                  fontSize: 14,
                  lineHeight: 1.6,
                  marginBottom: 12,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  display: '-webkit-box',
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: 'vertical',
                }}>
                  {profile.signature}
                </p>
              )}
              {!isOwnProfile && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <button
                    type="button"
                    onClick={handleToggleFollow}
                    disabled={followLoading}
                    style={{
                      padding: '8px 28px',
                      border: isFollowed ? '1px solid var(--color-border)' : 'none',
                      borderRadius: '8px',
                      background: isFollowed
                        ? 'transparent'
                        : 'var(--color-gradient-primary)',
                      color: isFollowed ? 'var(--color-text-secondary)' : 'var(--color-bg-base)',
                      fontSize: 14,
                      fontWeight: 600,
                      cursor: followLoading ? 'not-allowed' : 'pointer',
                      transition: 'all 0.2s',
                      boxShadow: isFollowed ? 'none' : '0 2px 12px rgba(var(--color-primary-rgb), 0.3)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                    }}
                  >
                    {isFollowed ? '已关注' : '+ 关注'}
                  </button>
                  <button
                    type="button"
                    onClick={handleStartChat}
                    disabled={chatLoading}
                    style={{
                      padding: '8px 20px',
                      border: '1px solid rgba(var(--color-primary-rgb), 0.3)',
                      borderRadius: '8px',
                      background: 'transparent',
                      color: 'var(--color-primary)',
                      fontSize: 14,
                      fontWeight: 600,
                      cursor: chatLoading ? 'not-allowed' : 'pointer',
                      transition: 'all 0.2s',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                    }}
                  >
                    <SendOutlined />
                    发私信
                  </button>
                  <Popconfirm
                    title={isBlocked ? '确认取消拉黑该用户？' : '确认拉黑该用户？拉黑后将无法看到对方内容'}
                    onConfirm={handleToggleBlock}
                    okText="确认"
                    cancelText="取消"
                  >
                    <button
                      type="button"
                      disabled={blockLoading}
                      style={{
                        padding: '8px 20px',
                        border: isBlocked ? '1px solid var(--color-border)' : '1px solid rgba(255, 107, 107, 0.3)',
                        borderRadius: '8px',
                        background: 'transparent',
                        color: isBlocked ? 'var(--color-text-tertiary)' : '#FF6B6B',
                        fontSize: 14,
                        fontWeight: 600,
                        cursor: blockLoading ? 'not-allowed' : 'pointer',
                        transition: 'all 0.2s',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 6,
                      }}
                    >
                      <StopOutlined />
                      {isBlocked ? '取消拉黑' : '拉黑'}
                    </button>
                  </Popconfirm>
                </div>
              )}
              {isOwnProfile && (
                <button
                  type="button"
                  onClick={() => history.push('/profile')}
                  style={{
                    padding: '8px 28px',
                    border: '1px solid var(--color-border)',
                    borderRadius: '8px',
                    background: 'transparent',
                    color: 'var(--color-text-secondary)',
                    fontSize: 14,
                    fontWeight: 600,
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                  }}
                >
                  编辑资料
                </button>
              )}
            </div>
          </div>

          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 0,
            padding: '20px 0',
            borderTop: '1px solid var(--color-border)',
          }}>
            {stats.map((stat, index) => (
              <div
                key={stat.label}
                onClick={stat.action}
                style={{
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: 6,
                  position: 'relative',
                  cursor: 'pointer',
                  transition: 'transform 0.2s',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = 'scale(1.05)'
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = 'scale(1)'
                }}
              >
                <span style={{
                  color: 'var(--color-primary)',
                  fontSize: 22,
                  fontWeight: 700,
                  letterSpacing: '-0.5px',
                }}>
                  {formatCount(stat.value)}
                </span>
                <span style={{
                  color: 'var(--color-text-secondary)',
                  fontSize: 13,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                }}>
                  {stat.icon}
                  {stat.label}
                </span>
                {index < stats.length - 1 && (
                  <div style={{
                    position: 'absolute',
                    right: 0,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    width: 1,
                    height: 32,
                    background: 'var(--color-border)',
                  }} />
                )}
              </div>
            ))}
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 800, margin: '0 auto', padding: '0 24px' }}>
        {profile.badges && profile.badges.length > 0 && (
          <div style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 8,
            padding: '20px 0',
            borderBottom: '1px solid var(--color-border)',
          }}>
            <TrophyOutlined style={{ color: '#FFD700', fontSize: 16, marginRight: 4, marginTop: 4 }} />
            {profile.badges.map((badge, index) => {
              const colorSet = getBadgeColor(index)
              return (
                <span
                  key={badge.id}
                  title={badge.description}
                  style={{
                    padding: '4px 12px',
                    borderRadius: '6px',
                    background: colorSet.bg,
                    border: `1px solid ${colorSet.border}`,
                    color: colorSet.text,
                    fontSize: 12,
                    fontWeight: 500,
                    cursor: 'default',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                  }}
                >
                  {badge.icon && <span>{badge.icon}</span>}
                  {badge.name}
                </span>
              )
            })}
          </div>
        )}

        <div style={{
          display: 'flex',
          gap: 0,
          borderBottom: '1px solid var(--color-border)',
          marginBottom: 24,
        }}>
          {[
            { key: 'posts' as const, label: '帖子', icon: <FileTextOutlined /> },
            { key: 'collections' as const, label: '收藏', icon: <StarOutlined /> },
          ].map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              style={{
                padding: '16px 28px',
                border: 'none',
                background: 'transparent',
                color: activeTab === tab.key ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                fontSize: 14,
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

        {activeTab === 'posts' && (
          <>
            {posts.length === 0 ? (
              <div style={{
                textAlign: 'center',
                padding: '60px 0',
                color: 'var(--color-text-tertiary)',
                fontSize: 14,
              }}>
                暂无帖子
              </div>
            ) : (
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(2, 1fr)',
                gap: 16,
                paddingBottom: 80,
              }}>
                {posts.map((post) => (
                  <PostCard key={post.id} post={post} />
                ))}
              </div>
            )}
          </>
        )}

        {activeTab === 'collections' && (
          <>
            {collections.length > 0 ? (
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
                gap: 16,
              }}>
                {collections.map((post) => (
                  <PostCard key={post.id} post={post} />
                ))}
              </div>
            ) : (
              <div style={{
                textAlign: 'center',
                padding: '60px 0',
                color: 'var(--color-text-tertiary)',
                fontSize: 14,
              }}>
                {isOwnProfile ? '你还没有收藏内容' : '暂无公开收藏'}
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
