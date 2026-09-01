import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, Alert } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { communityApi } from '@/api/community'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

const TABS = [
  { key: 'posts', label: '帖子' },
  { key: 'collections', label: '收藏' },
]

const REPORT_REASONS = ['垃圾广告', '色情低俗', '违法违规', '侵权抄袭', '人身攻击', '虚假信息', '其他']

export default function UserProfileScreen() {
  const theme = useTheme()
  const { id } = useLocalSearchParams<{ id: string }>()
  const userId = id ?? ''
  const { user: currentUser } = useAuthStore()

  const [profile, setProfile] = useState<any>(null)
  const [isFollowing, setIsFollowing] = useState(false)
  const [isBlocked, setIsBlocked] = useState(false)
  const [activeTab, setActiveTab] = useState<'posts' | 'collections'>('posts')
  const [posts, setPosts] = useState<any[]>([])
  const [collections, setCollections] = useState<any[]>([])
  const [loading, setLoading] = useState(false)

  const isOwnProfile = String(currentUser?.id ?? '') === userId

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

  useEffect(() => { loadProfile() }, [loadProfile])

  useEffect(() => {
    if (activeTab === 'posts') loadPosts()
    else loadCollections()
  }, [activeTab])

  const loadPosts = async () => {
    if (!userId) return
    setLoading(true)
    try {
      const res = await communityApi.getUserPosts(userId, { page: 1, pageSize: 20 })
      setPosts(res.data?.data?.list || res.data?.data || [])
    } catch { setPosts([]) }
    finally { setLoading(false) }
  }

  const loadCollections = async () => {
    if (!userId) return
    setLoading(true)
    try {
      const res = await communityApi.getUserCollections(userId, { page: 1, pageSize: 20 })
      setCollections(res.data?.data?.list || res.data?.data || [])
    } catch { setCollections([]) }
    finally { setLoading(false) }
  }

  const handleFollow = async () => {
    try {
      if (isFollowing) await communityApi.unfollowUser(userId)
      else await communityApi.followUser(userId)
      setIsFollowing(!isFollowing)
      setProfile((prev: any) => ({
        ...prev,
        followerCount: isFollowing ? (prev.followerCount || 0) - 1 : (prev.followerCount || 0) + 1,
      }))
    } catch {
      Alert.alert('错误', '操作失败')
    }
  }

  const handleBlock = () => {
    Alert.alert(isBlocked ? '取消拉黑' : '确认拉黑', isBlocked ? '确定要取消拉黑该用户吗？' : '拉黑后将不再看到该用户的内容，确定拉黑吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: isBlocked ? '取消拉黑' : '拉黑',
        style: isBlocked ? 'default' : 'destructive',
        onPress: async () => {
          try {
            if (isBlocked) await communityApi.unblockUser(userId)
            else await communityApi.blockUser(userId)
            setIsBlocked(!isBlocked)
          } catch { Alert.alert('错误', '操作失败') }
        },
      },
    ])
  }

  const handleReport = () => {
    Alert.alert('举报用户', '请选择举报原因', REPORT_REASONS.map((r) => ({ text: r })), {
      cancelable: true,
      onDismiss: () => {},
    })
    // Use ActionSheet alternative for RN
  }

  const handleMessage = () => {
    router.push(`/chat?userId=${userId}`)
  }

  const handleMoreActions = () => {
    Alert.alert('更多操作', '', [
      { text: '取消', style: 'cancel' },
      { text: isBlocked ? '取消拉黑' : '拉黑', onPress: handleBlock },
      { text: '举报', style: 'destructive', onPress: () => {
        Alert.alert('举报原因', '请选择', REPORT_REASONS.map((r) => ({ text: r })) as any, {
          cancelable: true,
          onDismiss: () => {},
        })
      }},
    ])
  }

  const formatCount = (count: number) => {
    if (count >= 10000) return `${(count / 10000).toFixed(1)}w`
    if (count >= 1000) return `${(count / 1000).toFixed(1)}k`
    return String(count)
  }

  if (!profile) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    )
  }

  const renderPostCard = (post: any) => (
    <TouchableOpacity
      key={post.id}
      activeOpacity={0.7}
      onPress={() => router.push(`/post-detail?id=${post.id}`)}
      style={{
        width: '48%',
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        borderWidth: 1,
        borderColor: theme.border,
      }}
    >
      {post.coverImage ? (
        <Image source={{ uri: post.coverImage }} style={{ width: '100%', height: 120, resizeMode: 'cover' }} />
      ) : (
        <View style={{ width: '100%', height: 120, backgroundColor: theme.primaryGlow, justifyContent: 'center', alignItems: 'center' }}>
          <Text style={{ fontSize: 32, opacity: 0.3 }}>📝</Text>
        </View>
      )}
      <View style={{ padding: Spacing.sm }}>
        <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: theme.text }} numberOfLines={2}>{post.title}</Text>
        <View style={{ flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.xs }}>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>❤️ {post.likeCount || 0}</Text>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>👁 {post.viewCount || 0}</Text>
        </View>
      </View>
    </TouchableOpacity>
  )

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView contentContainerStyle={{ paddingBottom: 40 }}>
        {/* Header */}
        <View style={{ backgroundColor: theme.bgContainer, alignItems: 'center', padding: Spacing.xxl }}>
          <View style={{ width: 80, height: 80, borderRadius: 40, borderWidth: 2, borderColor: theme.primary + '4D', overflow: 'hidden', marginBottom: Spacing.md }}>
            {profile.avatar ? (
              <Image source={{ uri: profile.avatar }} style={{ width: '100%', height: '100%', resizeMode: 'cover' }} />
            ) : (
              <View style={{ width: '100%', height: '100%', backgroundColor: theme.primaryGlow, justifyContent: 'center', alignItems: 'center' }}>
                <Text style={{ fontSize: 28, color: theme.primary, fontWeight: '700' }}>{(profile.nickname || '?')[0]}</Text>
              </View>
            )}
          </View>
          <Text style={{ fontSize: FontSize.xxl, fontWeight: 'bold', color: theme.text }}>{profile.nickname || '用户'}</Text>
          {profile.signature && (
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, marginTop: Spacing.xs, textAlign: 'center' }} numberOfLines={2}>
              {profile.signature}
            </Text>
          )}

          {/* Badges */}
          {profile.badges?.length > 0 && (
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: Spacing.md, justifyContent: 'center' }}>
              {profile.badges.map((badge: any) => (
                <View key={badge.id} style={{ flexDirection: 'row', alignItems: 'center', gap: 2, paddingHorizontal: Spacing.md, paddingVertical: 2, borderRadius: BorderRadius.xl, backgroundColor: theme.primaryGlow, borderWidth: 1, borderColor: theme.primary + '33' }}>
                  <Text style={{ fontSize: 12 }}>{badge.icon}</Text>
                  <Text style={{ fontSize: FontSize.xs, color: theme.primary, fontWeight: '500' }}>{badge.name}</Text>
                </View>
              ))}
            </View>
          )}
        </View>

        {/* Stats */}
        <View style={{ flexDirection: 'row', backgroundColor: theme.bgContainer, paddingVertical: Spacing.lg, borderTopWidth: 1, borderTopColor: theme.border }}>
          {[
            { label: '帖子', value: profile.postCount || 0, onPress: () => setActiveTab('posts') },
            { label: '粉丝', value: profile.followerCount || 0 },
            { label: '关注', value: profile.followCount || 0 },
            { label: '收藏', value: profile.collectCount || 0, onPress: () => setActiveTab('collections') },
          ].map((stat) => (
            <TouchableOpacity key={stat.label} style={{ flex: 1, alignItems: 'center' }} onPress={stat.onPress}>
              <Text style={{ fontSize: FontSize.xl, fontWeight: 'bold', color: theme.text }}>{formatCount(stat.value)}</Text>
              <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: 2 }}>{stat.label}</Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Action Buttons */}
        {!isOwnProfile && (
          <View style={{ flexDirection: 'row', gap: Spacing.md, padding: Spacing.lg, backgroundColor: theme.bgContainer, borderTopWidth: 1, borderTopColor: theme.border }}>
            <TouchableOpacity
              onPress={handleFollow}
              style={{
                flex: 1,
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.xl,
                backgroundColor: isFollowing ? 'transparent' : theme.primary,
                borderWidth: isFollowing ? 1 : 0,
                borderColor: theme.border,
                alignItems: 'center',
              }}
            >
              <Text style={{ color: isFollowing ? theme.textSecondary : '#FFFFFF', fontWeight: '600' }}>
                {isFollowing ? '已关注' : '+ 关注'}
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={handleMessage}
              style={{
                paddingHorizontal: Spacing.xl,
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.xl,
                borderWidth: 1,
                borderColor: theme.border,
                alignItems: 'center',
              }}
            >
              <Text style={{ color: theme.textSecondary, fontWeight: '500' }}>💬 私信</Text>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={handleMoreActions}
              style={{
                width: 48,
                height: 48,
                borderRadius: BorderRadius.xl,
                borderWidth: 1,
                borderColor: theme.border,
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Text style={{ color: theme.textSecondary, fontSize: 20 }}>⋯</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* Tabs */}
        <View style={{ flexDirection: 'row', backgroundColor: theme.bgContainer, borderBottomWidth: 1, borderBottomColor: theme.border }}>
          {TABS.map((tab) => {
            const isActive = activeTab === tab.key
            return (
              <TouchableOpacity
                key={tab.key}
                onPress={() => setActiveTab(tab.key as any)}
                style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.md, position: 'relative' }}
              >
                <Text style={{ fontSize: FontSize.md, color: isActive ? theme.primary : theme.textTertiary, fontWeight: isActive ? '600' : '400' }}>
                  {tab.label}
                </Text>
                {isActive && (
                  <View style={{ position: 'absolute', bottom: 0, width: 20, height: 3, borderRadius: 1.5, backgroundColor: theme.primary }} />
                )}
              </TouchableOpacity>
            )
          })}
        </View>

        {/* Content */}
        <View style={{ padding: Spacing.md }}>
          {loading ? (
            <View style={{ alignItems: 'center', padding: 40 }}>
              <ActivityIndicator size="large" color={theme.primary} />
            </View>
          ) : activeTab === 'posts' ? (
            posts.length > 0 ? (
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.md }}>{posts.map(renderPostCard)}</View>
            ) : (
              <View style={{ alignItems: 'center', padding: 60 }}>
                <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无帖子</Text>
              </View>
            )
          ) : collections.length > 0 ? (
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.md }}>{collections.map(renderPostCard)}</View>
          ) : (
            <View style={{ alignItems: 'center', padding: 60 }}>
              <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无收藏</Text>
            </View>
          )}
        </View>
      </ScrollView>
    </View>
  )
}
