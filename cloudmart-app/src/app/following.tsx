import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, RefreshControl } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { communityApi } from '@/api/community'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { UserBasic } from '@/types'

type TabKey = 'following' | 'followers'

const TAB_LIST: { key: TabKey; label: string }[] = [
  { key: 'following', label: '关注' },
  { key: 'followers', label: '粉丝' },
]

interface FollowUser extends UserBasic {
  isFollowing?: boolean
}

export default function FollowingScreen() {
  const theme = useTheme()
  const { type, userId: userIdParam } = useLocalSearchParams<{ type?: string; userId?: string }>()
  const { user: currentUser } = useAuthStore()

  const targetUserId = userIdParam ? Number(userIdParam) : currentUser?.id
  const isOwnList = targetUserId === currentUser?.id

  const initialTab: TabKey = type === 'followers' ? 'followers' : 'following'
  const [activeTab, setActiveTab] = useState<TabKey>(initialTab)
  const [followingList, setFollowingList] = useState<FollowUser[]>([])
  const [followersList, setFollowersList] = useState<FollowUser[]>([])
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)

  const fetchFollowing = useCallback(async () => {
    if (!targetUserId) return
    setLoading(true)
    try {
      const res = await communityApi.getUserFollowing(targetUserId, { page: 1, pageSize: 50 })
      setFollowingList(res.data?.data?.list || res.data?.data || [])
    } catch {
      setFollowingList([])
    } finally {
      setLoading(false)
    }
  }, [targetUserId])

  const fetchFollowers = useCallback(async () => {
    if (!targetUserId) return
    setLoading(true)
    try {
      const res = await communityApi.getUserFollowers(targetUserId, { page: 1, pageSize: 50 })
      setFollowersList(res.data?.data?.list || res.data?.data || [])
    } catch {
      setFollowersList([])
    } finally {
      setLoading(false)
    }
  }, [targetUserId])

  useEffect(() => {
    if (activeTab === 'following') fetchFollowing()
    else fetchFollowers()
  }, [activeTab, fetchFollowing, fetchFollowers])

  const handleRefresh = async () => {
    setRefreshing(true)
    if (activeTab === 'following') await fetchFollowing()
    else await fetchFollowers()
    setRefreshing(false)
  }

  const handleFollowToggle = async (user: FollowUser) => {
    const wasFollowing = user.isFollowing ?? false
    try {
      if (wasFollowing) {
        await communityApi.unfollowUser(user.id)
      } else {
        await communityApi.followUser(user.id)
      }
      const updateFn = (item: FollowUser) =>
        item.id === user.id ? { ...item, isFollowing: !wasFollowing } : item
      setFollowingList((prev) => prev.map(updateFn))
      setFollowersList((prev) => prev.map(updateFn))
    } catch {
      // 操作失败静默处理
    }
  }

  const navigateToProfile = (userId: number) => {
    router.push(`/user-profile?id=${userId}`)
  }

  const renderUserRow = (user: FollowUser) => {
    const isFollowing = user.isFollowing ?? false
    const isSelf = user.id === currentUser?.id

    return (
      <TouchableOpacity
        key={user.id}
        activeOpacity={0.7}
        onPress={() => navigateToProfile(user.id)}
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          paddingVertical: Spacing.md,
          paddingHorizontal: Spacing.lg,
          backgroundColor: theme.bgContainer,
          borderBottomWidth: 1,
          borderBottomColor: theme.border,
        }}
      >
        {/* Avatar */}
        <View
          style={{
            width: 48,
            height: 48,
            borderRadius: BorderRadius.full,
            overflow: 'hidden',
            borderWidth: 1,
            borderColor: theme.border,
          }}
        >
          {user.avatar ? (
            <Image source={{ uri: user.avatar }} style={{ width: '100%', height: '100%', resizeMode: 'cover' }} />
          ) : (
            <View
              style={{
                width: '100%',
                height: '100%',
                backgroundColor: theme.primaryGlow,
                justifyContent: 'center',
                alignItems: 'center',
              }}
            >
              <Text style={{ fontSize: FontSize.xl, color: theme.primary, fontWeight: '700' }}>
                {(user.nickname || '?')[0]}
              </Text>
            </View>
          )}
        </View>

        {/* Info */}
        <View style={{ flex: 1, marginLeft: Spacing.md }}>
          <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text }} numberOfLines={1}>
            {user.nickname || '用户'}
          </Text>
          {user.signature ? (
            <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: 2 }} numberOfLines={1}>
              {user.signature}
            </Text>
          ) : null}
        </View>

        {/* Follow Button */}
        {!isSelf && (
          <TouchableOpacity
            onPress={() => handleFollowToggle(user)}
            style={{
              paddingHorizontal: Spacing.lg,
              paddingVertical: Spacing.sm,
              borderRadius: BorderRadius.xl,
              backgroundColor: isFollowing ? 'transparent' : theme.primary,
              borderWidth: isFollowing ? 1 : 0,
              borderColor: theme.border,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Text
              style={{
                fontSize: FontSize.sm,
                fontWeight: '600',
                color: isFollowing ? theme.textSecondary : '#FFFFFF',
              }}
            >
              {isFollowing ? '已关注' : '关注'}
            </Text>
          </TouchableOpacity>
        )}
      </TouchableOpacity>
    )
  }

  const renderEmpty = (text: string) => (
    <View style={{ alignItems: 'center', paddingTop: 100 }}>
      <Text style={{ fontSize: 48, marginBottom: Spacing.lg, opacity: 0.3 }}>👤</Text>
      <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>{text}</Text>
    </View>
  )

  const currentList = activeTab === 'following' ? followingList : followersList

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      {/* Tab Bar */}
      <View
        style={{
          flexDirection: 'row',
          backgroundColor: theme.bgContainer,
          borderBottomWidth: 1,
          borderBottomColor: theme.border,
        }}
      >
        {TAB_LIST.map((tab) => {
          const isActive = activeTab === tab.key
          return (
            <TouchableOpacity
              key={tab.key}
              activeOpacity={0.7}
              onPress={() => setActiveTab(tab.key)}
              style={{
                flex: 1,
                alignItems: 'center',
                paddingVertical: Spacing.md,
                position: 'relative',
              }}
            >
              <Text
                style={{
                  fontSize: FontSize.lg,
                  color: isActive ? theme.primary : theme.textTertiary,
                  fontWeight: isActive ? '600' : '400',
                }}
              >
                {tab.label}
              </Text>
              {isActive && (
                <View
                  style={{
                    position: 'absolute',
                    bottom: 0,
                    width: 20,
                    height: 3,
                    borderRadius: 1.5,
                    backgroundColor: theme.primary,
                  }}
                />
              )}
            </TouchableOpacity>
          )
        })}
      </View>

      {/* Content */}
      {loading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color={theme.primary} />
        </View>
      ) : (
        <ScrollView
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} tintColor={theme.primary} />
          }
          contentContainerStyle={{ paddingBottom: 40 }}
        >
          {currentList.length > 0
            ? currentList.map(renderUserRow)
            : renderEmpty(activeTab === 'following' ? '暂无关注' : '暂无粉丝')}
        </ScrollView>
      )}
    </View>
  )
}
