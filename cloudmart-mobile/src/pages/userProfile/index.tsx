import { useState, useEffect, useCallback } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { communityApi } from '@/api/community'
import { userApi } from '@/api/user'
import DecoratedAvatar from '@/components/DecoratedAvatar'
import { wishApi } from '@/api/wish'
import { petApi, type PetPublicCard } from '@/api/pet'
import { useAuthStore } from '@/store/auth'
import { useThemeClass } from '@/composables/useThemeClass'
import type { WishListItem, User } from '@/types'
import styles from './index.module.scss'

// 对齐 Web 端 UserProfile：帖子 / 收藏 / TA的心愿 / TA的评论
const TABS = [
  { key: 'posts', label: '帖子' },
  { key: 'collections', label: '收藏' },
  { key: 'wishes', label: 'TA的心愿' },
  { key: 'comments', label: 'TA的评论' },
]

type TabKey = typeof TABS[number]['key']

const REPORT_REASONS = ['垃圾广告', '色情低俗', '违法违规', '侵权抄袭', '人身攻击', '虚假信息', '其他']

interface UserCommentItem {
  id: number
  postId?: number
  content: string
  createdAt: string
  user?: { id: number; nickname: string; avatar: string }
}

export default function UserProfilePage() {
  const userId = Taro.getCurrentInstance().router?.params?.id || Taro.getCurrentInstance().router?.params?.userId || ''
  const { dataTheme, themeStyle } = useThemeClass()
  const { user: currentUser } = useAuthStore()

  const [profile, setProfile] = useState<any>(null)
  const [isFollowing, setIsFollowing] = useState(false)
  const [isBlocked, setIsBlocked] = useState(false)
  const [activeTab, setActiveTab] = useState<TabKey>('posts')
  const [posts, setPosts] = useState<any[]>([])
  const [collections, setCollections] = useState<any[]>([])
  const [wishes, setWishes] = useState<WishListItem[]>([])
  const [comments, setComments] = useState<UserCommentItem[]>([])
  const [loading, setLoading] = useState(false)

  const isOwnProfile = String(currentUser?.id ?? '') === userId

  const [publicInfo, setPublicInfo] = useState<User | null>(null)
  /** TA的宠物卡片（未公开/无宠物即隐藏，Fail-Open 不影响主页其它内容；原文档 §80） */
  const [petCard, setPetCard] = useState<PetPublicCard | null>(null)

  const loadProfile = useCallback(async () => {
    if (!userId) return
    try {
      const res = await communityApi.getUserProfile(userId)
      const data = res.data?.data
      setProfile(data)
      // TA的宠物卡片：宠物未公开/无宠物 404 → 隐藏卡片（Fail-Open）
      petApi.getPublicCard(userId).then((r) => setPetCard(r.data?.data ?? null)).catch(() => setPetCard(null))
      // 公开资料（后端按隐私过滤字段）：加入时间/详细资料卡数据源（对齐 Web 端）
      userApi
        .getPublicProfile(userId)
        .then((r) => setPublicInfo(r.data?.data ?? null))
        .catch(() => {})
      setIsFollowing(data?.isFollowing || false)
      setIsBlocked(data?.isBlocked || false)
    } catch {
      // API unavailable
    }
  }, [userId])

  useEffect(() => {
    loadProfile()
  }, [loadProfile])

  useEffect(() => {
    if (activeTab === 'posts') loadPosts()
    else if (activeTab === 'collections') loadCollections()
    else if (activeTab === 'wishes') loadWishes()
    else if (activeTab === 'comments') loadComments()
  }, [activeTab])

  const loadPosts = async () => {
    if (!userId) return
    setLoading(true)
    try {
      const res = await communityApi.getUserPosts(userId, { page: 1, pageSize: 20 })
      setPosts(res.data?.data?.list || res.data?.data || [])
    } catch {
      setPosts([])
    } finally {
      setLoading(false)
    }
  }

  const loadCollections = async () => {
    if (!userId) return
    setLoading(true)
    try {
      const res = await communityApi.getUserCollections(userId, { page: 1, pageSize: 20 })
      setCollections(res.data?.data?.list || res.data?.data || [])
    } catch {
      setCollections([])
    } finally {
      setLoading(false)
    }
  }

  /** TA 的心愿（服务端强制仅公开心愿，对齐 Web 端） */
  const loadWishes = async () => {
    if (!userId) return
    setLoading(true)
    try {
      const res = await wishApi.listWishes({ userId: Number(userId), pageSize: 30 })
      setWishes(res.data?.data || [])
    } catch {
      setWishes([])
    } finally {
      setLoading(false)
    }
  }

  /** TA 的评论（对齐 Web 端 UserProfile 评论 Tab） */
  const loadComments = async () => {
    if (!userId) return
    setLoading(true)
    try {
      const res = await communityApi.getUserComments(userId, { page: 1, pageSize: 20 })
      setComments(res.data?.data?.list || [])
    } catch {
      setComments([])
    } finally {
      setLoading(false)
    }
  }

  const handleFollow = async () => {
    try {
      if (isFollowing) {
        await communityApi.unfollowUser(userId)
      } else {
        await communityApi.followUser(userId)
      }
      setIsFollowing(!isFollowing)
      setProfile((prev: any) => ({
        ...prev,
        followerCount: isFollowing ? (prev.followerCount || 0) - 1 : (prev.followerCount || 0) + 1,
      }))
    } catch {
      Taro.showToast({ title: '操作失败', icon: 'none' })
    }
  }

  const handleBlock = async () => {
    const res = await Taro.showModal({
      title: isBlocked ? '取消拉黑' : '确认拉黑',
      content: isBlocked ? '确定要取消拉黑该用户吗？' : '拉黑后将不再看到该用户的内容，确定拉黑吗？',
    })
    if (!res.confirm) return
    try {
      if (isBlocked) {
        await communityApi.unblockUser(userId)
      } else {
        await communityApi.blockUser(userId)
      }
      setIsBlocked(!isBlocked)
      Taro.showToast({ title: isBlocked ? '已取消拉黑' : '已拉黑', icon: 'success' })
    } catch {
      Taro.showToast({ title: '操作失败', icon: 'none' })
    }
  }

  const handleReport = async () => {
    const { tapIndex } = await Taro.showActionSheet({
      itemList: REPORT_REASONS,
    })
    const reason = REPORT_REASONS[tapIndex]
    try {
      await communityApi.report({ targetType: 'USER', targetId: userId, reason })
      Taro.showToast({ title: '举报成功', icon: 'success' })
    } catch {
      Taro.showToast({ title: '举报失败', icon: 'none' })
    }
  }

  const handleMessage = () => {
    Taro.navigateTo({ url: `/pages/chat/index?userId=${userId}` })
  }

  const formatCount = (count: number) => {
    if (count >= 10000) return `${(count / 10000).toFixed(1)}w`
    if (count >= 1000) return `${(count / 1000).toFixed(1)}k`
    return String(count)
  }

  if (!profile) {
    return (
      <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
        <View className={styles.loading}>
          <View className={styles.spinner} />
        </View>
      </View>
    )
  }

  const FRUIT_LABELS: Record<string, string> = { GLOW: '🌱 微光', RESONANCE: '💫 共鸣', BLOOM: '🌸 绽放', SPARK: '⭐ 星火' }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.scrollContent}>
        {/* TA 的宠物（原文档 §80：社区宠物与个人主页融合） */}
        {petCard && (
          <View
            className={styles.petCard}
            onClick={() => Taro.navigateTo({ url: '/pages/pet/index' })}
          >
            <Text className={styles.petCardEmoji}>🐾</Text>
            <View className={styles.petCardBody}>
              <Text className={styles.petCardName}>TA 的宠物 · {petCard.name}</Text>
              <Text className={styles.petCardMeta}>
                Lv.{petCard.level} · {petCard.growthStage} · 成就 {petCard.achievementCount} 枚
              </Text>
            </View>
            <Text className={styles.petCardLink}>看看TA的宠物 ›</Text>
          </View>
        )}
        {/* Profile Header */}
        <View className={styles.profileHeader}>
          <View className={styles.avatarRing}>
            <DecoratedAvatar
              src={profile.avatar || publicInfo?.avatar}
              userId={userId}
              size={76}
              fallbackText={(profile.nickname || '?')[0]}
            />
          </View>
          <Text className={styles.nickname}>{profile.nickname || '用户'}</Text>
          {profile.signature && <Text className={styles.bio}>{profile.signature}</Text>}
          {publicInfo?.createdAt && (
            <Text className={styles.joinedText}>
              加入 {new Date(publicInfo.createdAt).toLocaleDateString('zh-CN')} · 已加入{' '}
              {Math.max(1, Math.ceil((Date.now() - new Date(publicInfo.createdAt).getTime()) / 86_400_000))} 天
            </Text>
          )}
          {(() => {
            const info = publicInfo
            if (!info) return null
            const fields: Array<[string, string | undefined]> = [
              ['性别', info.gender === 'MALE' ? '男' : info.gender === 'FEMALE' ? '女' : undefined],
              ['星座', info.constellation],
              ['职业', info.occupation],
              ['学校', info.school],
              ['地区', info.location],
              ['爱好', info.hobbies],
            ]
            const visible = fields.filter(([, v]) => v)
            if (visible.length === 0) return null
            return (
              <View className={styles.detailCard}>
                {visible.map(([label, value]) => (
                  <Text key={label} className={styles.detailLine}>
                    {label}：{value}
                  </Text>
                ))}
              </View>
            )
          })()}

          {/* Badges */}
          {profile.badges?.length > 0 && (
            <View className={styles.badges}>
              {profile.badges.map((badge: any, idx: number) => (
                <View key={badge.id} className={styles.badge} style={{ backgroundColor: `rgba(var(--color-primary-rgb), ${0.08 + idx * 0.02})` }}>
                  <Text className={styles.badgeIcon}>{badge.icon}</Text>
                  <Text className={styles.badgeName}>{badge.name}</Text>
                </View>
              ))}
            </View>
          )}
        </View>

        {/* Stats */}
        <View className={styles.stats}>
          <View className={styles.statItem} onClick={() => setActiveTab('posts')}>
            <Text className={styles.statValue}>{formatCount(profile.postCount || 0)}</Text>
            <Text className={styles.statLabel}>帖子</Text>
          </View>
          <View className={styles.statItem}>
            <Text className={styles.statValue}>{formatCount(profile.followerCount || 0)}</Text>
            <Text className={styles.statLabel}>粉丝</Text>
          </View>
          <View className={styles.statItem}>
            <Text className={styles.statValue}>{formatCount(profile.followCount || 0)}</Text>
            <Text className={styles.statLabel}>关注</Text>
          </View>
          <View className={styles.statItem} onClick={() => setActiveTab('collections')}>
            <Text className={styles.statValue}>{formatCount(profile.collectCount || 0)}</Text>
            <Text className={styles.statLabel}>收藏</Text>
          </View>
        </View>

        {/* Action Buttons */}
        {!isOwnProfile && (
          <View className={styles.actions}>
            <View
              className={`${styles.followBtn} ${isFollowing ? styles.followingBtn : ''}`}
              onClick={handleFollow}
            >
              <Text className={isFollowing ? styles.followingText : styles.followBtnText}>
                {isFollowing ? '已关注' : '+ 关注'}
              </Text>
            </View>
            <View className={styles.messageBtn} onClick={handleMessage}>
              <Text className={styles.messageBtnText}>💬 私信</Text>
            </View>
            <View className={styles.moreBtn} onClick={() => {
              Taro.showActionSheet({
                itemList: [isBlocked ? '取消拉黑' : '拉黑', '举报'],
              }).then((res) => {
                if (res.tapIndex === 0) handleBlock()
                else if (res.tapIndex === 1) handleReport()
              }).catch(() => {})
            }}>
              <Text className={styles.moreBtnText}>⋯</Text>
            </View>
          </View>
        )}

        {/* Content Tabs */}
        <View className={styles.tabs}>
          {TABS.map((tab) => (
            <View
              key={tab.key}
              className={`${styles.tab} ${activeTab === tab.key ? styles.tabActive : ''}`}
              onClick={() => setActiveTab(tab.key)}
            >
              <Text className={activeTab === tab.key ? styles.tabTextActive : styles.tabText}>
                {tab.label}
              </Text>
            </View>
          ))}
        </View>

        {/* Content */}
        {loading ? (
          <View className={styles.loadingContent}>
            <View className={styles.spinner} />
          </View>
        ) : activeTab === 'posts' ? (
          posts.length > 0 ? (
            <View className={styles.postGrid}>
              {posts.map((post: any) => (
                <View key={post.id} className={styles.postCard} onClick={() => Taro.navigateTo({ url: `/pages/postDetail/index?id=${post.id}` })}>
                  {post.coverImage ? (
                    <Image className={styles.postCover} src={post.coverImage} mode='aspectFill' />
                  ) : (
                    <View className={styles.postCoverPlaceholder}>
                      <Text className={styles.placeholderIcon}>📝</Text>
                    </View>
                  )}
                  <View className={styles.postInfo}>
                    <Text className={styles.postTitle}>{post.title}</Text>
                    <View className={styles.postMeta}>
                      <Text className={styles.metaItem}>❤️ {post.likeCount || 0}</Text>
                      <Text className={styles.metaItem}>👁 {post.viewCount || 0}</Text>
                    </View>
                  </View>
                </View>
              ))}
            </View>
          ) : (
            <View className={styles.empty}>
              <Text className={styles.emptyText}>暂无帖子</Text>
            </View>
          )
        ) : activeTab === 'collections' ? (
          collections.length > 0 ? (
            <View className={styles.postGrid}>
              {collections.map((item: any) => (
                <View key={item.id} className={styles.postCard} onClick={() => Taro.navigateTo({ url: `/pages/postDetail/index?id=${item.id}` })}>
                  {item.coverImage ? (
                    <Image className={styles.postCover} src={item.coverImage} mode='aspectFill' />
                  ) : (
                    <View className={styles.postCoverPlaceholder}>
                      <Text className={styles.placeholderIcon}>⭐</Text>
                    </View>
                  )}
                  <View className={styles.postInfo}>
                    <Text className={styles.postTitle}>{item.title}</Text>
                  </View>
                </View>
              ))}
            </View>
          ) : (
            <View className={styles.empty}>
              <Text className={styles.emptyText}>暂无收藏</Text>
            </View>
          )
        ) : activeTab === 'wishes' ? (
          wishes.length > 0 ? (
            <View className={styles.wishList}>
              {wishes.map((wish) => (
                <View key={wish.wishId ?? wish.id} className={styles.wishCard} onClick={() => Taro.navigateTo({ url: `/pages/wishDetail/index?id=${wish.wishId ?? wish.id}` })}>
                  <View className={styles.wishCardHeader}>
                    <Text className={styles.wishFruit}>{FRUIT_LABELS[wish.fruitType] ?? '🌱 微光'}</Text>
                    <Text className={styles.wishStatus}>{wish.status === 'ACTIVE' ? '进行中' : wish.status === 'FULFILLED' ? '已还愿' : wish.status === 'FULFILLING' ? '还愿中' : ''}</Text>
                  </View>
                  <Text className={styles.wishTitle}>{wish.title}</Text>
                  {wish.authorNickname && <Text className={styles.wishAuthor}>by {wish.authorNickname}</Text>}
                </View>
              ))}
            </View>
          ) : (
            <View className={styles.empty}>
              <Text className={styles.emptyText}>暂无公开心愿</Text>
            </View>
          )
        ) : comments.length > 0 ? (
          <View className={styles.commentList}>
            {comments.map((comment) => (
              <View
                key={comment.id}
                className={styles.commentRow}
                onClick={() => comment.postId && Taro.navigateTo({ url: `/pages/postDetail/index?id=${comment.postId}` })}
              >
                <Text className={styles.commentContent}>{comment.content}</Text>
                <Text className={styles.commentDate}>{comment.createdAt?.slice(0, 10)}</Text>
              </View>
            ))}
          </View>
        ) : (
          <View className={styles.empty}>
            <Text className={styles.emptyText}>暂无评论</Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}
