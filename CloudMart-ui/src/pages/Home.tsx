import { useState, useEffect, useCallback, useRef } from 'react'
import { Spin, Empty, Avatar, Tag, Dropdown, Modal, App } from 'antd'
import type { MenuProps } from 'antd'
import {
  FireOutlined,
  HeartOutlined,
  HeartFilled,
  MessageOutlined,
  StarOutlined,
  StarFilled,
  ShareAltOutlined,
  PlayCircleOutlined,
  ShoppingOutlined,
  PlusOutlined,
  CompassOutlined,
  TeamOutlined,
  VideoCameraOutlined,
  ThunderboltOutlined,
  RiseOutlined,
  EyeOutlined,
  CloseOutlined,
  RightOutlined,
  AppstoreOutlined,
  EllipsisOutlined,
  DeleteOutlined,
  PictureOutlined,
  LoginOutlined,
} from '@ant-design/icons'
import { history } from 'umi'
import { getFeedPosts, getFollowingFeed, likePost, unlikePost, collectPost, uncollectPost, getTrendingTopics, getRecommendUsers, followUser, deletePost } from '@/api/community'
import { searchProducts } from '@/api/product'
import { useAuthStore } from '@/stores/auth'
import type { Post, HotTopic, RecommendUser, ProductSearchItem } from '@/types'
import styles from './Home.module.css'
import ShareModal from '@/components/ShareModal'

const FEED_TABS = [
  { key: 'recommend', label: '推荐', icon: <CompassOutlined /> },
  { key: 'follow', label: '关注', icon: <TeamOutlined /> },
  { key: 'hot', label: '热门', icon: <FireOutlined /> },
  { key: 'goods', label: '好物', icon: <ShoppingOutlined /> },
]

const TAG_COLORS = ['var(--color-primary)', '#FF6B6B', '#FFD700', '#7C4DFF', '#00E676', '#FF9100', '#E040FB', '#00BCD4']

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  return `${Math.floor(days / 30)}月前`
}

function PostCard({
  post,
  currentUserId,
  onLike,
  onCollect,
  onDelete,
  onShare,
  showGoodsIndicator,
  hotRank,
}: {
  post: Post
  currentUserId: number | null
  onLike: (id: number) => void
  onCollect: (id: number) => void
  onDelete: (id: number) => void
  onShare: (id: number, title: string) => void
  showGoodsIndicator?: boolean
  hotRank?: number
}) {
  const isVideo = post.type === 'VIDEO'
  const isProduct = post.type === 'PRODUCT'
  const isOwnPost = currentUserId !== null && post.userId === currentUserId

  const mediaIndicator = isVideo
    ? <span className={styles.mediaIndicator}><VideoCameraOutlined /> 视频</span>
    : post.coverImage
      ? <span className={styles.mediaIndicator}><PictureOutlined /> 图文</span>
      : null

  const deleteMenuItems: MenuProps['items'] = [
    {
      key: 'delete',
      label: '删除帖子',
      icon: <DeleteOutlined />,
      danger: true,
    },
  ]

  const handleDeleteClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'delete') {
      Modal.confirm({
        title: '确认删除',
        content: '删除后不可恢复，确定要删除这篇帖子吗？',
        okText: '删除',
        cancelText: '取消',
        okButtonProps: { danger: true },
        onOk: () => onDelete(post.id),
      })
    }
  }

  return (
    <div className={styles.postCard} onClick={() => history.push(`/post/${post.id}`)}>
      <div className={styles.postHeader}>
        <Avatar
          size={40}
          src={post.authorAvatar || undefined}
          style={{ background: 'var(--color-gradient-primary)', flexShrink: 0 }}
        >
          {post.authorNickname?.charAt(0) || '?'}
        </Avatar>
        <div className={styles.postAuthorInfo}>
          <div className={styles.postAuthorName}>
            {post.authorNickname || '未知用户'}
          </div>
          <div className={styles.postTime}>{timeAgo(post.createdAt)}</div>
        </div>
        <div className={styles.postHeaderRight}>
          {hotRank !== undefined && hotRank <= 3 && (
            <span style={{
              padding: '2px 8px',
              borderRadius: '4px',
              background: hotRank === 1
                ? 'linear-gradient(135deg, #FF6B6B, #EE4444)'
                : hotRank === 2
                  ? 'linear-gradient(135deg, #FFA502, #FF8C00)'
                  : 'linear-gradient(135deg, #FFD700, #FFC107)',
              color: 'var(--color-bg-base)',
              fontSize: 11,
              fontWeight: 700,
            }}>
              🔥 TOP{hotRank}
            </span>
          )}
          {mediaIndicator}
          {(isProduct || showGoodsIndicator) && (
            <Tag color="cyan" className={styles.productTag}>
              🛒 好物
            </Tag>
          )}
          {isOwnPost && (
            <Dropdown menu={{ items: deleteMenuItems, onClick: handleDeleteClick }} trigger={['click']}>
              <button
                type="button"
                className={styles.moreBtn}
                onClick={(e) => e.stopPropagation()}
              >
                <EllipsisOutlined />
              </button>
            </Dropdown>
          )}
        </div>
      </div>

      <div className={styles.postBody}>
        <h3 className={styles.postTitle}>{post.title}</h3>
        <p className={styles.postSummary}>{post.summary}</p>
        {post.coverImage && (
          <div className={`${styles.postCover} ${isVideo ? styles.postCoverVideo : ''}`}>
            <img src={post.coverImage} alt={post.title} />
            {isVideo && (
              <div className={styles.playOverlay}>
                <PlayCircleOutlined />
              </div>
            )}
            {isProduct && post.productPrice !== null && (
              <div className={styles.priceTag}>¥{post.productPrice}</div>
            )}
          </div>
        )}
      </div>

      <div className={styles.postTags}>
        {(post.tags ?? []).map((tag, index) => (
          <span
            key={tag.id}
            className={styles.tagChip}
            style={{ background: `${TAG_COLORS[index % TAG_COLORS.length]}18`, color: TAG_COLORS[index % TAG_COLORS.length], borderColor: `${TAG_COLORS[index % TAG_COLORS.length]}30` }}
          >
            #{tag.name}
          </span>
        ))}
      </div>

      <div className={styles.postActions}>
        <button
          type="button"
          className={`${styles.actionBtn} ${post.isLiked ? styles.actionBtnActive : ''}`}
          onClick={(e) => { e.stopPropagation(); onLike(post.id) }}
        >
          {post.isLiked ? <HeartFilled /> : <HeartOutlined />}
          <span>{formatCount(post.likeCount)}</span>
        </button>
        <button
          type="button"
          className={styles.actionBtn}
          onClick={(e) => { e.stopPropagation(); history.push(`/post/${post.id}`) }}
        >
          <MessageOutlined />
          <span>{formatCount(post.commentCount)}</span>
        </button>
        <button
          type="button"
          className={`${styles.actionBtn} ${post.isCollected ? styles.actionBtnActive : ''}`}
          onClick={(e) => { e.stopPropagation(); onCollect(post.id) }}
        >
          {post.isCollected ? <StarFilled /> : <StarOutlined />}
          <span>{formatCount(post.collectCount ?? 0)}</span>
        </button>
        <button type="button" className={styles.actionBtn} onClick={(e) => { e.stopPropagation(); onShare(post.id, post.title) }}>
          <ShareAltOutlined />
          <span>{formatCount(post.shareCount ?? 0)}</span>
        </button>
        <span className={styles.viewCount}>
          <EyeOutlined />
          <span>{formatCount(post.viewCount ?? 0)}</span>
        </span>
      </div>
    </div>
  )
}

function SidebarTopics({ topics }: { topics: HotTopic[] }) {
  return (
    <div className={styles.sidebarCard}>
      <div className={styles.sidebarCardHeader}>
        <FireOutlined style={{ color: '#FF6B6B' }} />
        <span>热门话题</span>
      </div>
      <div className={styles.topicList}>
        {topics.map((topic, index) => (
          <div key={topic.id} className={styles.topicItem}>
            <span className={`${styles.topicRank} ${index < 3 ? styles.topicRankHot : ''}`}>
              {index + 1}
            </span>
            <div className={styles.topicInfo}>
              <span className={styles.topicName}>
                #{topic.name}
                {topic.isHot && <FireOutlined style={{ color: '#FF6B6B', fontSize: 12, marginLeft: 4 }} />}
              </span>
              <span className={styles.topicCount}>{formatCount(topic.postCount)}参与</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function SidebarUsers({ users, onFollow }: { users: RecommendUser[]; onFollow: (userId: number) => void }) {
  return (
    <div className={styles.sidebarCard}>
      <div className={styles.sidebarCardHeader}>
        <TeamOutlined style={{ color: 'var(--color-primary)' }} />
        <span>推荐关注</span>
      </div>
      <div className={styles.userList}>
        {users.map((user) => (
          <div key={user.userId} className={styles.userItem}>
            <Avatar
              size={36}
              src={user.avatar || undefined}
              style={{ background: 'var(--color-gradient-primary)', flexShrink: 0 }}
            >
              {user.nickname[0]}
            </Avatar>
            <div className={styles.userInfo}>
              <div className={styles.userName}>
                {user.nickname}
                <span className={styles.userLevel}>{formatCount(user.followerCount)}粉丝</span>
              </div>
              <div className={styles.userBio}>{formatCount(user.postCount)}篇内容</div>
            </div>
            <button
              type="button"
              className={`${styles.followBtn} ${user.isFollowed ? styles.followBtnActive : ''}`}
              onClick={() => onFollow(user.userId)}
            >
              {user.isFollowed ? '已关注' : '关注'}
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}

function SidebarHotProducts({ products }: { products: ProductSearchItem[] }) {
  if (products.length === 0) return null

  return (
    <div className={styles.sidebarCard}>
      <div className={styles.sidebarCardHeader}>
        <RiseOutlined style={{ color: '#FFD700' }} />
        <span>热销好物</span>
      </div>
      <div className={styles.hotProductList}>
        {products.slice(0, 5).map((product, index) => (
          <div
            key={product.id}
            className={styles.hotProductItem}
            onClick={() => history.push(`/products/${product.id}`)}
          >
            <span className={`${styles.hotProductRank} ${index < 3 ? styles.topicRankHot : ''}`}>
              {index + 1}
            </span>
            <div className={styles.hotProductInfo}>
              <span className={styles.hotProductName}>{product.name}</span>
              <span className={styles.hotProductPrice}>¥{(product.price ?? 0).toFixed(2)}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function FollowLoginPrompt() {
  return (
    <div className={styles.loginPrompt}>
      <div className={styles.loginPromptIcon}>
        <LoginOutlined />
      </div>
      <h3 className={styles.loginPromptTitle}>登录后查看关注动态</h3>
      <p className={styles.loginPromptDesc}>关注你喜欢的创作者，获取最新动态推送</p>
      <button
        type="button"
        className={styles.loginPromptBtn}
        onClick={() => history.push('/login')}
      >
        立即登录
      </button>
    </div>
  )
}

function FollowEmptyState() {
  return (
    <div className={styles.loginPrompt}>
      <div className={styles.loginPromptIcon}>
        <TeamOutlined />
      </div>
      <h3 className={styles.loginPromptTitle}>还没有关注任何人</h3>
      <p className={styles.loginPromptDesc}>去发现感兴趣的用户吧</p>
      <button
        type="button"
        className={styles.loginPromptBtn}
        onClick={() => history.push('/')}
      >
        去发现
      </button>
    </div>
  )
}

function SkeletonPostCard() {
  return (
    <div className={styles.postCard} style={{ pointerEvents: 'none' }}>
      <div className={styles.postHeader}>
        <div style={{ width: 40, height: 40, borderRadius: '50%', background: 'var(--color-border)' }} />
        <div style={{ flex: 1 }}>
          <div style={{ height: 14, width: '40%', borderRadius: 4, background: 'var(--color-border)', marginBottom: 6 }} />
          <div style={{ height: 10, width: '25%', borderRadius: 4, background: 'var(--color-border)' }} />
        </div>
      </div>
      <div style={{ height: 18, width: '85%', borderRadius: 4, background: 'var(--color-border)', marginBottom: 8 }} />
      <div style={{ height: 14, width: '65%', borderRadius: 4, background: 'var(--color-border)', marginBottom: 14 }} />
      <div style={{ height: 180, borderRadius: 10, background: 'var(--color-border)', marginBottom: 14 }} />
      <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
        <div style={{ height: 22, width: 60, borderRadius: 12, background: 'var(--color-border)' }} />
        <div style={{ height: 22, width: 72, borderRadius: 12, background: 'var(--color-border)' }} />
      </div>
      <div style={{ display: 'flex', gap: 4, paddingTop: 12, borderTop: '1px solid var(--color-border)' }}>
        <div style={{ height: 20, width: 60, borderRadius: 6, background: 'var(--color-border)' }} />
        <div style={{ height: 20, width: 60, borderRadius: 6, background: 'var(--color-border)' }} />
        <div style={{ height: 20, width: 60, borderRadius: 6, background: 'var(--color-border)' }} />
      </div>
    </div>
  )
}

export default function Home() {
  const { message } = App.useApp()
  const { isAuthenticated, user } = useAuthStore()
  const currentUserId = user?.id ?? null
  const [activeTab, setActiveTab] = useState('recommend')
  const [posts, setPosts] = useState<Post[]>([])
  const [topics, setTopics] = useState<HotTopic[]>([])
  const [recommendUsers, setRecommendUsers] = useState<RecommendUser[]>([])
  const [hotProducts, setHotProducts] = useState<ProductSearchItem[]>([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [sidebarVisible, setSidebarVisible] = useState(false)
  const [shareModalVisible, setShareModalVisible] = useState(false)
  const [sharePostId, setSharePostId] = useState(0)
  const [sharePostTitle, setSharePostTitle] = useState('')
  const containerRef = useRef<HTMLDivElement>(null)

  const fetchPosts = useCallback(async (tab: string, pageNum: number, append = false) => {
    if (tab === 'follow' && !isAuthenticated) return
    if (tab === 'profile') return

    const isLoadMore = append
    if (isLoadMore) {
      setLoadingMore(true)
    } else {
      setLoading(true)
    }

    try {
      const fetchTab = tab === 'hot' || tab === 'goods' ? 'recommend' : tab
      const res = fetchTab === 'follow'
        ? await getFollowingFeed(pageNum, 20)
        : await getFeedPosts(fetchTab, pageNum, 20)
      let newPosts = (res.data.data ?? []) as unknown as Post[]

      if (tab === 'hot') {
        newPosts = [...newPosts].sort((a, b) => {
          const scoreA = a.likeCount * 3 + a.commentCount * 2 + (a.collectCount ?? 0) + (a.viewCount ?? 0) * 0.01
          const scoreB = b.likeCount * 3 + b.commentCount * 2 + (b.collectCount ?? 0) + (b.viewCount ?? 0) * 0.01
          return scoreB - scoreA
        })
      }

      if (tab === 'goods') {
        newPosts = newPosts.filter((p) => p.productId !== null)
      }

      if (append) {
        setPosts((prev) => [...prev, ...newPosts])
      } else {
        setPosts(newPosts)
      }
      setHasMore(newPosts.length >= 20)
    } catch {
      if (!append) {
        setPosts([])
      }
      setHasMore(false)
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [isAuthenticated])

  useEffect(() => {
    setPage(1)
    setHasMore(true)
    fetchPosts(activeTab, 1)
  }, [activeTab, fetchPosts])

  useEffect(() => {
    async function fetchSidebarData() {
      try {
        const [topicRes, userRes, prodRes] = await Promise.allSettled([
          getTrendingTopics(10),
          getRecommendUsers(6),
          searchProducts({ page: 1, size: 5, sort: 'sales_desc' }),
        ])
        if (topicRes.status === 'fulfilled' && topicRes.value.data.data?.length) {
          setTopics(topicRes.value.data.data as unknown as HotTopic[])
        }
        if (userRes.status === 'fulfilled' && userRes.value.data.data?.length) {
          setRecommendUsers(userRes.value.data.data as unknown as RecommendUser[])
        }
        if (prodRes.status === 'fulfilled' && prodRes.value.data.data?.products?.length) {
          setHotProducts(prodRes.value.data.data.products)
        }
      } catch {
        // API failed, keep existing state
      }
    }
    fetchSidebarData()
  }, [])

  const handleLoadMore = useCallback(() => {
    const nextPage = page + 1
    setPage(nextPage)
    fetchPosts(activeTab, nextPage, true)
  }, [page, activeTab, fetchPosts])

  const handleLike = useCallback((postId: number) => {
    setPosts((prev) =>
      prev.map((p) => {
        if (p.id !== postId) return p
        const willLike = !p.isLiked
        try {
          if (willLike) {
            likePost(postId)
          } else {
            unlikePost(postId)
          }
        } catch { /* optimistic */ }
        return {
          ...p,
          isLiked: willLike,
          likeCount: willLike ? p.likeCount + 1 : p.likeCount - 1,
        }
      }),
    )
  }, [])

  const handleCollect = useCallback((postId: number) => {
    setPosts((prev) =>
      prev.map((p) => {
        if (p.id !== postId) return p
        const willCollect = !p.isCollected
        try {
          if (willCollect) {
            collectPost(postId)
          } else {
            uncollectPost(postId)
          }
        } catch { /* optimistic */ }
        return {
          ...p,
          isCollected: willCollect,
          collectCount: willCollect ? (p.collectCount ?? 0) + 1 : (p.collectCount ?? 0) - 1,
        }
      }),
    )
  }, [])

  const handleDelete = useCallback(async (postId: number) => {
    try {
      await deletePost(postId)
      setPosts((prev) => prev.filter((p) => p.id !== postId))
      message.success('帖子已删除')
    } catch {
      message.error('删除失败，请重试')
    }
  }, [message])

  const handleFollow = useCallback((userId: number) => {
    setRecommendUsers((prev) =>
      prev.map((u) => {
        if (u.userId !== userId) return u
        const willFollow = !u.isFollowed
        try {
          if (willFollow) {
            followUser(userId)
          }
        } catch { /* optimistic */ }
        return { ...u, isFollowed: willFollow }
      }),
    )
  }, [])

  const handleShare = useCallback((postId: number, title: string) => {
    setSharePostId(postId)
    setSharePostTitle(title)
    setShareModalVisible(true)
  }, [])

  const showFollowLoginPrompt = activeTab === 'follow' && !isAuthenticated
  const showFollowEmpty = activeTab === 'follow' && isAuthenticated && !loading && posts.length === 0

  return (
    <div className={styles.page} ref={containerRef}>
      <div className={styles.mainContent}>
        <div className={styles.feedColumn}>
          <div className={styles.feedTabs}>
            {FEED_TABS.map((tab) => (
              <button
                key={tab.key}
                type="button"
                className={`${styles.feedTab} ${activeTab === tab.key ? styles.feedTabActive : ''}`}
                onClick={() => {
                  setActiveTab(tab.key)
                }}
              >
                {tab.icon}
                <span>{tab.label}</span>
              </button>
            ))}
          </div>

          {showFollowLoginPrompt ? (
            <FollowLoginPrompt />
          ) : showFollowEmpty ? (
            <FollowEmptyState />
          ) : loading ? (
            <div className={styles.postList}>
              {Array.from({ length: 4 }).map((_, i) => (
                <SkeletonPostCard key={i} />
              ))}
            </div>
          ) : posts.length === 0 ? (
            <div className={styles.emptyWrap}>
              <Empty description="暂无内容，快去发现吧" />
            </div>
          ) : (
            <>
              <div className={styles.postList}>
                {posts.map((post, index) => (
                  <PostCard
                    key={post.id}
                    post={post}
                    currentUserId={currentUserId}
                    onLike={handleLike}
                    onCollect={handleCollect}
                    onDelete={handleDelete}
                    onShare={handleShare}
                    showGoodsIndicator={activeTab === 'goods'}
                    hotRank={activeTab === 'hot' ? index + 1 : undefined}
                  />
                ))}
              </div>
              {hasMore && (
                <div className={styles.loadMoreWrap}>
                  <button
                    type="button"
                    className={styles.loadMoreBtn}
                    onClick={handleLoadMore}
                    disabled={loadingMore}
                  >
                    {loadingMore ? <Spin size="small" /> : '加载更多'}
                  </button>
                </div>
              )}
            </>
          )}
        </div>

        <div className={styles.sidebarColumn}>
          <SidebarTopics topics={topics} />
          <SidebarUsers users={recommendUsers} onFollow={handleFollow} />
          <SidebarHotProducts products={hotProducts} />

          <div className={styles.sidebarCard}>
            <div className={styles.sidebarLinks}>
              <a onClick={() => history.push('/products')}>
                <AppstoreOutlined /> 商城
              </a>
              <a onClick={() => history.push('/shop/seckill')}>
                <ThunderboltOutlined /> 秒杀
              </a>
              <a onClick={() => history.push('/live')}>
                <VideoCameraOutlined /> 直播
              </a>
              <a onClick={() => history.push('/ai-chat')}>
                <EyeOutlined /> AI助手
              </a>
            </div>
          </div>
        </div>
      </div>

      <button
        type="button"
        className={styles.fabButton}
        onClick={() => {
          if (!isAuthenticated) {
            history.push('/login?redirect=/publish')
          } else {
            history.push('/publish')
          }
        }}
        title="发布内容"
      >
        <PlusOutlined />
      </button>

      <button
        type="button"
        className={`${styles.sidebarToggleButton} ${sidebarVisible ? styles.sidebarToggleHidden : ''}`}
        onClick={() => setSidebarVisible(!sidebarVisible)}
      >
        {sidebarVisible ? <CloseOutlined /> : <RightOutlined />}
      </button>

      {sidebarVisible && (
        <div className={styles.mobileSidebar}>
          <SidebarTopics topics={topics} />
          <SidebarUsers users={recommendUsers} onFollow={handleFollow} />
          <SidebarHotProducts products={hotProducts} />
        </div>
      )}

      <ShareModal
        visible={shareModalVisible}
        onClose={() => setShareModalVisible(false)}
        postTitle={sharePostTitle}
        postId={sharePostId}
      />
    </div>
  )
}
