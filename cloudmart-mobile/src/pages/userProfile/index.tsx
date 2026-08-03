import { useState, useEffect, useCallback } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { communityApi } from '@/api/community'
import { useAuthStore } from '@/store/auth'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

const TABS = [
  { key: 'posts', label: '帖子' },
  { key: 'collections', label: '收藏' },
]

type TabKey = typeof TABS[number]['key']

const REPORT_REASONS = ['垃圾广告', '色情低俗', '违法违规', '侵权抄袭', '人身攻击', '虚假信息', '其他']

export default function UserProfilePage() {
  const userId = Number(Taro.getCurrentInstance().router?.params?.id)
  const { dataTheme, themeStyle } = useThemeClass()
  const { user: currentUser } = useAuthStore()

  const [profile, setProfile] = useState<any>(null)
  const [isFollowing, setIsFollowing] = useState(false)
  const [isBlocked, setIsBlocked] = useState(false)
  const [activeTab, setActiveTab] = useState<TabKey>('posts')
  const [posts, setPosts] = useState<any[]>([])
  const [collections, setCollections] = useState<any[]>([])
  const [loading, setLoading] = useState(false)

  const isOwnProfile = currentUser?.id === userId

  const loadProfile = useCallback(async () => {
    if (!userId) return
    try {
      const res = await communityApi.getUserProfile(userId)
      const data = res.data?.data
      setProfile(data)
      setIsFollowing(data?.isFollowing || false)
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

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.scrollContent}>
        {/* Profile Header */}
        <View className={styles.profileHeader}>
          <View className={styles.avatarRing}>
            {profile.avatar ? (
              <Image className={styles.avatar} src={profile.avatar} />
            ) : (
              <View className={styles.defaultAvatar}>
                <Text className={styles.defaultAvatarText}>{(profile.nickname || '?')[0]}</Text>
              </View>
            )}
          </View>
          <Text className={styles.nickname}>{profile.nickname || '用户'}</Text>
          {profile.signature && <Text className={styles.bio}>{profile.signature}</Text>}

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
        ) : collections.length > 0 ? (
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
        )}
      </ScrollView>
    </View>
  )
}
