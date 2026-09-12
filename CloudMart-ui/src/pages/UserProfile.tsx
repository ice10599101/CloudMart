import { useState, useEffect, useCallback } from 'react'
import { useParams, history } from 'umi'
import { Empty, Avatar, Popconfirm } from 'antd'
import { message } from '@/utils/appMessage'
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
  CommentOutlined,
  IdcardOutlined,
  CalendarOutlined,
} from '@ant-design/icons'
import {
  getUserProfile as getCommunityProfile,
  getUserCommunityStats,
  getUserComments,
  getUserLikedPosts,
  followUser,
  unfollowUser,
  getUserPosts,
  getUserCollections,
  blockUser,
  unblockUser,
  checkBlockStatus,
} from '@/api/community'
import type { Post, UserCommunityStats, MyComment } from '@/api/community'
import { getUserPublicProfile } from '@/api/user'
import type { UserProfile } from '@/api/user'
import { createConversation } from '@/api/chat'
import { listWishes } from '@/api/wish'
import type { WishListItem } from '@/api/wish'
import { stripHtml } from '@/utils/format'
import RichText from '@/components/RichText'
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

function formatJoinDate(value: string | undefined): string {
  if (!value) return ''
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? '' : `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

function formatJoinedDays(value: string | undefined): number | null {
  if (!value) return null
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return null
  return Math.max(1, Math.ceil((Date.now() - d.getTime()) / 86400000))
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
  const preview = stripHtml(post.content).slice(0, 60)
  return (
    <div
      onClick={() => history.push(`/post/${post.id}`)}
      style={{
        background: 'var(--color-bg-container)',
        borderRadius: '10px',
        border: '1px solid var(--color-border)',
        overflow: 'hidden',
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        display: 'flex',
        flexDirection: 'column',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
        e.currentTarget.style.transform = 'translateY(-3px)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'var(--color-border)'
        e.currentTarget.style.transform = 'translateY(0)'
      }}
    >
      {/* 封面按需显示且限高：无图不再撑出大面积空占位 */}
      {post.coverImage && (
        <div style={{ height: 110, overflow: 'hidden', background: 'var(--color-bg-input)', flexShrink: 0 }}>
          <img
            src={post.coverImage}
            alt={post.title}
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            loading="lazy"
          />
        </div>
      )}
      <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 6, flex: 1 }}>
        <h4 style={{
          color: 'var(--color-text-secondary)',
          fontSize: 13,
          fontWeight: 600,
          lineHeight: 1.4,
          margin: 0,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}>
          {post.title}
        </h4>
        {preview && (
          <RichText
            content={post.content}
            clamp={2}
            variant="preview"
            style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}
          />
        )}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 'auto' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
            <HeartOutlined /> {formatCount(post.likeCount)}
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
            <CommentOutlined /> {formatCount(post.commentCount)}
          </span>
          <span style={{ marginLeft: 'auto', color: 'var(--color-text-tertiary)', fontSize: 11 }}>
            {post.createdAt ? new Date(post.createdAt).toLocaleDateString() : ''}
          </span>
        </div>
      </div>
    </div>
  )
}

/** 详细资料字段：仅展示用户已填写的项，未填不显示 */
function buildDetailFields(user: UserProfile): Array<{ label: string; value: string }> {
  const fields: Array<{ label: string; value: string }> = []
  const push = (label: string, value: string | undefined | null) => {
    const v = (value ?? '').trim()
    if (v) fields.push({ label, value: v })
  }
  const genderMap: Record<string, string> = { MALE: '男', FEMALE: '女', UNKNOWN: '保密', SECRET: '保密' }
  const genderRaw = (user.gender ?? '').trim()
  // 后端存枚举码，展示层转中文
  push('性别', genderRaw ? (genderMap[genderRaw.toUpperCase()] ?? genderRaw) : '')
  push('小答号', user.username)
  if ((user.birthday ?? '').trim()) {
    push('生日', user.constellation?.trim() ? `${user.birthday}（${user.constellation}）` : user.birthday)
  }
  push('职业', user.occupation)
  push('学校', user.school)
  push('所在地区', user.location)
  push('兴趣爱好', user.hobbies)
  const joined = formatJoinDate(user.createdAt)
  if (joined) fields.push({ label: '加入时间', value: joined })
  return fields
}

const WISH_STATUS_LABELS: Record<string, string> = {
  ACTIVE: '进行中',
  FULFILLING: '还愿中',
  FULFILLED: '已还愿',
}

const WISH_STATUS_COLORS: Record<string, { bg: string; text: string }> = {
  ACTIVE: { bg: 'rgba(var(--color-primary-rgb), 0.12)', text: 'var(--color-primary)' },
  FULFILLING: { bg: 'rgba(255, 165, 0, 0.12)', text: 'var(--color-accent-orange)' },
  FULFILLED: { bg: 'rgba(46, 213, 115, 0.12)', text: 'var(--color-accent-green)' },
}

/** TA 的心愿卡片：与帖子卡片同尺寸风格 */
function WishCard({ wish }: { wish: WishListItem }) {
  const statusLabel = WISH_STATUS_LABELS[wish.status] ?? wish.status
  const statusColor = WISH_STATUS_COLORS[wish.status] ?? WISH_STATUS_COLORS.ACTIVE
  return (
    <div
      onClick={() => history.push(`/wish/${wish.id}`)}
      style={{
        background: 'var(--color-bg-container)',
        borderRadius: '10px',
        border: '1px solid var(--color-border)',
        overflow: 'hidden',
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        display: 'flex',
        flexDirection: 'column',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
        e.currentTarget.style.transform = 'translateY(-3px)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'var(--color-border)'
        e.currentTarget.style.transform = 'translateY(0)'
      }}
    >
      {wish.mediaUrls && wish.mediaUrls.length > 0 && (
        <div style={{ height: 110, overflow: 'hidden', background: 'var(--color-bg-input)', flexShrink: 0 }}>
          <img src={wish.mediaUrls[0]} alt={wish.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} loading="lazy" />
        </div>
      )}
      <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 6, flex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{
            padding: '1px 8px',
            borderRadius: 6,
            fontSize: 11,
            fontWeight: 600,
            background: statusColor.bg,
            color: statusColor.text,
            flexShrink: 0,
          }}>
            {statusLabel}
          </span>
          <h4 style={{
            color: 'var(--color-text-secondary)',
            fontSize: 13,
            fontWeight: 600,
            lineHeight: 1.4,
            margin: 0,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}>
            {wish.title}
          </h4>
        </div>
        {wish.description && (
          <RichText
            content={wish.description}
            clamp={2}
            variant="preview"
            style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}
          />
        )}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 'auto' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
            🌟 {formatCount(wish.lightCount)}
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
            🙏 {formatCount(wish.blessCount)}
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
            💬 {formatCount(wish.commentCount)}
          </span>
          <span style={{ marginLeft: 'auto', color: 'var(--color-text-tertiary)', fontSize: 11 }}>
            {wish.createdAt ? new Date(wish.createdAt).toLocaleDateString() : ''}
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
  const [detail, setDetail] = useState<UserProfile | null>(null)
  const [communityStats, setCommunityStats] = useState<UserCommunityStats | null>(null)
  const [posts, setPosts] = useState<Post[]>([])
  const [collections, setCollections] = useState<Post[]>([])
  const [loading, setLoading] = useState(true)
  const [isFollowed, setIsFollowed] = useState(false)
  const [followLoading, setFollowLoading] = useState(false)
  const [chatLoading, setChatLoading] = useState(false)
  const [isBlocked, setIsBlocked] = useState(false)
  const [blockLoading, setBlockLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<'posts' | 'collections' | 'wishes' | 'comments' | 'liked'>('posts')
  const [wishes, setWishes] = useState<WishListItem[] | null>(null)
  const [userComments, setUserComments] = useState<MyComment[] | null>(null)
  const [likedPosts, setLikedPosts] = useState<Post[] | null>(null)

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

  // 详细资料（mall-user）；未登录/接口失败时静默隐藏，不影响主资料
  const fetchDetail = useCallback(async () => {
    if (!id) return
    try {
      const { data: res } = await getUserPublicProfile(id)
      setDetail(res.data ?? null)
    } catch {
      setDetail(null)
    }
  }, [id])

  const fetchStats = useCallback(async () => {
    if (!id) return
    try {
      const { data: res } = await getUserCommunityStats(id)
      setCommunityStats(res.data ?? null)
    } catch {
      setCommunityStats(null)
    }
  }, [id])

  const checkBlock = useCallback(async () => {
    if (!id || isOwnProfile) return
    try {
      const { data: res } = await checkBlockStatus(id)
      // 后端返回 { blocked: boolean }，取字段而非整体（对象恒为真值会导致按钮状态永远显示"取消拉黑"）
      setIsBlocked(res.data?.blocked ?? false)
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

  // TA 的心愿：仅公开心愿（服务端强制 visibility=PUBLIC）
  const fetchWishes = useCallback(async () => {
    if (!id) return
    try {
      const { data: res } = await listWishes({ userId: Number(id), pageSize: 50 })
      setWishes(res.data ?? [])
    } catch {
      setWishes([])
    }
  }, [id])

  const fetchUserComments = useCallback(async () => {
    if (!id) return
    try {
      const { data: res } = await getUserComments(id, 1, 50)
      setUserComments(res.data ?? [])
    } catch {
      setUserComments([])
    }
  }, [id])

  const fetchLikedPosts = useCallback(async () => {
    if (!id) return
    try {
      const { data: res } = await getUserLikedPosts(id, 1, 50)
      setLikedPosts(res.data ?? [])
    } catch {
      setLikedPosts([])
    }
  }, [id])

  useEffect(() => {
    fetchProfile()
    fetchPosts()
    fetchStats()
    fetchDetail()
    checkBlock()
  }, [fetchProfile, fetchPosts, fetchStats, fetchDetail, checkBlock])

  useEffect(() => {
    if (activeTab === 'collections' && collections.length === 0) {
      fetchCollections()
    }
  }, [activeTab, collections.length, fetchCollections])

  useEffect(() => {
    if (activeTab === 'wishes' && wishes === null) {
      fetchWishes()
    }
  }, [activeTab, wishes, fetchWishes])

  useEffect(() => {
    if (activeTab === 'comments' && userComments === null) {
      fetchUserComments()
    }
  }, [activeTab, userComments, fetchUserComments])

  useEffect(() => {
    if (activeTab === 'liked' && likedPosts === null) {
      fetchLikedPosts()
    }
  }, [activeTab, likedPosts, fetchLikedPosts])

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
      if (willFollow) {
        await followUser(userId)
      } else {
        await unfollowUser(userId)
      }
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
      if (willBlock) {
        await blockUser(id)
      } else {
        await unblockUser(id)
      }
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
    { label: '获赞总数', value: communityStats ? communityStats.likesReceived : null, icon: <HeartOutlined />, action: () => setActiveTab('posts') },
    { label: '粉丝', value: profile.followerCount, icon: <TeamOutlined />, action: () => history.push(`/user/${id}/following?tab=followers`) },
    { label: '关注', value: profile.followCount, icon: <UserAddOutlined />, action: () => history.push(`/user/${id}/following?tab=following`) },
  ]

  const detailFields = detail ? buildDetailFields(detail) : []
  const joinedDays = detail ? formatJoinedDays(detail.createdAt) : null

  // 面板可点击：TA的评论/TA赞过/获赞总数 跳转到对应列表
  const metricPanels: Array<{ label: string; value: number | null; icon: React.ReactNode; action?: () => void }> = [
    { label: '获赞总数', value: communityStats ? communityStats.likesReceived : null, icon: <HeartOutlined />, action: () => setActiveTab('posts') },
    { label: isOwnProfile ? '我的评论' : 'TA的评论', value: communityStats ? communityStats.commentsMade : null, icon: <CommentOutlined />, action: () => setActiveTab('comments') },
    { label: isOwnProfile ? '我赞过的' : 'TA赞过', value: communityStats ? communityStats.likesGiven : null, icon: <StarOutlined />, action: () => setActiveTab('liked') },
    { label: '加入天数', value: joinedDays, icon: <CalendarOutlined /> },
  ]

  const cardSectionStyle: React.CSSProperties = {
    background: 'var(--color-bg-container)',
    border: '1px solid var(--color-border)',
    borderRadius: 12,
    padding: '16px 18px',
    marginBottom: 16,
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
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
                <h1 style={{
                  color: 'var(--color-text-secondary)',
                  fontSize: 26,
                  fontWeight: 800,
                  margin: 0,
                  lineHeight: 1.3,
                }}>
                  {profile.nickname}
                </h1>
                {detail?.username && (
                  <span style={{
                    padding: '2px 10px',
                    borderRadius: 6,
                    background: 'rgba(var(--color-primary-rgb), 0.1)',
                    border: '1px solid rgba(var(--color-primary-rgb), 0.2)',
                    color: 'var(--color-text-tertiary)',
                    fontSize: 12,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                  }}>
                    <IdcardOutlined /> 小答号 {detail.username}
                  </span>
                )}
              </div>
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
                  {stat.value === null || stat.value === undefined ? '-' : formatCount(stat.value)}
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

      <div style={{ maxWidth: 800, margin: '0 auto', padding: '16px 24px 0' }}>
        {/* 数据面板：获赞 / TA的评论 / TA赞过 / 加入天数 */}
        <div style={cardSectionStyle}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
            {metricPanels.map((panel) => (
              <div
                key={panel.label}
                onClick={panel.action}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: 4,
                  padding: '10px 4px',
                  borderRadius: 10,
                  background: 'rgba(var(--color-primary-rgb), 0.04)',
                  cursor: panel.action ? 'pointer' : 'default',
                  transition: 'background 0.2s',
                }}
                onMouseEnter={(e) => {
                  if (panel.action) e.currentTarget.style.background = 'rgba(var(--color-primary-rgb), 0.1)'
                }}
                onMouseLeave={(e) => {
                  if (panel.action) e.currentTarget.style.background = 'rgba(var(--color-primary-rgb), 0.04)'
                }}
              >
                <span style={{ color: 'var(--color-primary)', fontSize: 18, fontWeight: 700 }}>
                  {panel.value === null || panel.value === undefined ? '-' : formatCount(panel.value)}
                </span>
                <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12, display: 'flex', alignItems: 'center', gap: 4 }}>
                  {panel.icon}
                  {panel.label}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* 详细资料：仅展示已填写项 */}
        {detail && (
          <div style={cardSectionStyle}>
            <h3 style={{ fontSize: 14, fontWeight: 700, color: 'var(--color-text-secondary)', margin: '0 0 12px' }}>
              详细资料
            </h3>
            {detailFields.length === 0 ? (
              <div style={{ color: 'var(--color-text-tertiary)', fontSize: 13, padding: '4px 0' }}>
                {isOwnProfile ? '你还没有填写详细资料，去编辑资料补充吧' : 'TA还没有填写详细资料'}
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '10px 20px' }}>
                {detailFields.map((field) => (
                  <div key={field.label} style={{ display: 'flex', gap: 8, fontSize: 13, minWidth: 0 }}>
                    <span style={{ color: 'var(--color-text-tertiary)', flexShrink: 0 }}>{field.label}</span>
                    <span style={{ color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {field.value}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {profile.badges && profile.badges.length > 0 && (
          <div style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            gap: 8,
            marginBottom: 16,
          }}>
            <TrophyOutlined style={{ color: '#FFD700', fontSize: 16, marginRight: 4 }} />
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
      </div>

      <div style={{ maxWidth: 800, margin: '0 auto', padding: '0 24px' }}>
        <div style={{
          display: 'flex',
          gap: 0,
          borderBottom: '1px solid var(--color-border)',
          marginBottom: 16,
        }}>
          {[
            { key: 'posts' as const, label: '帖子', icon: <FileTextOutlined /> },
            { key: 'collections' as const, label: '收藏', icon: <StarOutlined /> },
            { key: 'wishes' as const, label: isOwnProfile ? '我的心愿' : 'TA的心愿', icon: <span style={{ fontSize: 13 }}>🌟</span> },
            { key: 'comments' as const, label: isOwnProfile ? '我的评论' : 'TA的评论', icon: <CommentOutlined /> },
            { key: 'liked' as const, label: isOwnProfile ? '我赞过的' : 'TA赞过', icon: <HeartOutlined /> },
          ].map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              style={{
                padding: '12px 28px',
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
                gridTemplateColumns: 'repeat(auto-fill, minmax(230px, 1fr))',
                gap: 14,
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
                gridTemplateColumns: 'repeat(auto-fill, minmax(230px, 1fr))',
                gap: 14,
                paddingBottom: 80,
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

        {activeTab === 'wishes' && (
          <>
            {wishes === null ? (
              <div style={{ textAlign: 'center', padding: '40px 0' }}>
                <Skeleton variant="card" count={3} />
              </div>
            ) : wishes.length > 0 ? (
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(230px, 1fr))',
                gap: 14,
                paddingBottom: 80,
              }}>
                {wishes.map((wish) => (
                  <WishCard key={wish.id} wish={wish} />
                ))}
              </div>
            ) : (
              <div style={{
                textAlign: 'center',
                padding: '60px 0',
                color: 'var(--color-text-tertiary)',
                fontSize: 14,
              }}>
                {isOwnProfile ? '你还没有公开的心愿' : 'TA还没有公开的心愿'}
              </div>
            )}
          </>
        )}

        {activeTab === 'comments' && (
          <>
            {userComments === null ? (
              <div style={{ textAlign: 'center', padding: '40px 0' }}><Skeleton variant="list" count={3} /></div>
            ) : userComments.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12, paddingBottom: 80 }}>
                {userComments.map((comment) => (
                  <div
                    key={comment.id}
                    onClick={() => history.push(`/post/${comment.postId}`)}
                    style={{
                      background: 'var(--color-bg-container)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 10,
                      padding: '12px 14px',
                      cursor: 'pointer',
                      transition: 'border-color 0.2s',
                    }}
                    onMouseEnter={(e) => { e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)' }}
                    onMouseLeave={(e) => { e.currentTarget.style.borderColor = 'var(--color-border)' }}
                  >
                    <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: 6 }}>
                      {comment.postTitle || '原帖已删除'}
                    </div>
                    <RichText
                      content={comment.content}
                      clamp={2}
                      variant="preview"
                      style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginBottom: 6 }}
                    />
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
                        <HeartOutlined /> {formatCount(comment.likeCount)}
                      </span>
                      <span style={{ marginLeft: 'auto', color: 'var(--color-text-tertiary)', fontSize: 11 }}>
                        {comment.createdAt ? new Date(comment.createdAt).toLocaleDateString() : ''}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div style={{
                textAlign: 'center',
                padding: '60px 0',
                color: 'var(--color-text-tertiary)',
                fontSize: 14,
              }}>
                {isOwnProfile ? '你还没有发表过评论' : 'TA还没有发表过评论'}
              </div>
            )}
          </>
        )}

        {activeTab === 'liked' && (
          <>
            {likedPosts === null ? (
              <div style={{ textAlign: 'center', padding: '40px 0' }}><Skeleton variant="card" count={3} /></div>
            ) : likedPosts.length > 0 ? (
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(230px, 1fr))',
                gap: 14,
                paddingBottom: 80,
              }}>
                {likedPosts.map((post) => (
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
                {isOwnProfile ? '你还没有点赞过内容' : 'TA还没有点赞过内容'}
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
