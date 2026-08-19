import { View, Text, ScrollView, TouchableOpacity, Image, RefreshControl } from 'react-native'
import { useState, useCallback } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

function MenuRow({ icon, label, onPress, theme }: { icon: string; label: string; onPress: () => void; theme: ReturnType<typeof useTheme> }) {
  return (
    <TouchableOpacity
      activeOpacity={0.7}
      onPress={onPress}
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: theme.bgContainer,
        paddingHorizontal: Spacing.lg,
        paddingVertical: Spacing.xl,
        borderBottomWidth: 1,
        borderBottomColor: theme.border,
      }}
    >
      <Text style={{ fontSize: 20, marginRight: Spacing.lg }}>{icon}</Text>
      <Text style={{ flex: 1, fontSize: FontSize.lg, color: theme.text }}>{label}</Text>
      <Text style={{ fontSize: FontSize.lg, color: theme.textTertiary }}>›</Text>
    </TouchableOpacity>
  )
}

export default function MinePage() {
  const theme = useTheme()
  const { user, isLoggedIn, logout, fetchUser } = useAuthStore()
  const { mode, toggleTheme } = useThemeStore()
  const [refreshing, setRefreshing] = useState(false)

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    if (isLoggedIn) await fetchUser()
    setRefreshing(false)
  }, [isLoggedIn, fetchUser])

  const handleLogout = async () => {
    await logout()
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.primary} />}
        contentContainerStyle={{ paddingBottom: Spacing.xxxl }}
      >
        {/* Hero Section */}
        <View style={{
          borderRadius: BorderRadius.xl,
          padding: Spacing.xxl,
          margin: Spacing.lg,
          marginBottom: Spacing.lg,
          backgroundColor: theme.bgContainer,
          ...theme.shadowGlow,
        }}>
          {isLoggedIn && user ? (
            <View style={{ alignItems: 'center' }}>
              {user.avatar ? (
                <Image source={{ uri: user.avatar }} style={{ width: 72, height: 72, borderRadius: 36, marginBottom: Spacing.lg }} />
              ) : (
                <View style={{
                  width: 72, height: 72, borderRadius: 36,
                  backgroundColor: theme.primaryGlow, justifyContent: 'center', alignItems: 'center',
                  marginBottom: Spacing.lg,
                }}>
                  <Text style={{ fontSize: 28, color: theme.primary }}>{user.nickname?.[0] || '?'}</Text>
                </View>
              )}
              <Text style={{ fontSize: FontSize.xxl, fontWeight: 'bold', color: theme.text }}>{user.nickname}</Text>
              {user.signature ? (
                <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, marginTop: Spacing.xs, textAlign: 'center' }}>
                  {user.signature}
                </Text>
              ) : null}
              <View style={{ flexDirection: 'row', marginTop: Spacing.lg, gap: Spacing.xxxl }}>
                <TouchableOpacity style={{ alignItems: 'center' }}>
                  <Text style={{ fontSize: FontSize.xxl, fontWeight: 'bold', color: theme.text }}>{user.postCount || 0}</Text>
                  <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>帖子</Text>
                </TouchableOpacity>
                <TouchableOpacity style={{ alignItems: 'center' }}>
                  <Text style={{ fontSize: FontSize.xxl, fontWeight: 'bold', color: theme.text }}>{user.followerCount || 0}</Text>
                  <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>粉丝</Text>
                </TouchableOpacity>
                <TouchableOpacity style={{ alignItems: 'center' }}>
                  <Text style={{ fontSize: FontSize.xxl, fontWeight: 'bold', color: theme.text }}>{user.followingCount || 0}</Text>
                  <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>关注</Text>
                </TouchableOpacity>
              </View>
              <TouchableOpacity
                onPress={() => router.push('/profile')}
                style={{
                  marginTop: Spacing.lg,
                  paddingHorizontal: Spacing.xl,
                  paddingVertical: Spacing.sm,
                  borderWidth: 1,
                  borderColor: theme.primary,
                  borderRadius: BorderRadius.xl,
                }}
              >
                <Text style={{ fontSize: FontSize.sm, color: theme.primary, fontWeight: '500' }}>编辑资料</Text>
              </TouchableOpacity>
            </View>
          ) : (
            <View style={{ alignItems: 'center' }}>
              <View style={{
                width: 72, height: 72, borderRadius: 36,
                backgroundColor: theme.primaryGlow, justifyContent: 'center', alignItems: 'center',
                marginBottom: Spacing.lg,
              }}>
                <Text style={{ fontSize: 28 }}>👤</Text>
              </View>
              <TouchableOpacity
                onPress={() => router.push('/login')}
                style={{
                  backgroundColor: theme.primary,
                  borderRadius: BorderRadius.lg,
                  paddingHorizontal: Spacing.xxl,
                  paddingVertical: Spacing.md,
                }}
              >
                <Text style={{ color: '#FFFFFF', fontSize: FontSize.lg, fontWeight: '600' }}>登录 / 注册</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>

        {/* Menu Groups */}
        <View style={{ marginHorizontal: Spacing.lg, borderRadius: BorderRadius.lg, overflow: 'hidden', marginBottom: Spacing.lg }}>
          <MenuRow icon="🛒" label="我的订单" onPress={() => { if (!isLoggedIn) { router.push('/login'); return } router.push('/orders') }} theme={theme} />
          <MenuRow icon="💝" label="心愿单" onPress={() => { if (!isLoggedIn) { router.push('/login'); return } router.push('/wishlist') }} theme={theme} />
          <MenuRow icon="🏅" label="我的徽章" onPress={() => { if (!isLoggedIn) { router.push('/login'); return } router.push('/badge-wall') }} theme={theme} />
          <MenuRow icon="❤️" label="我的收藏" onPress={() => { if (!isLoggedIn) { router.push('/login'); return } router.push('/collections') }} theme={theme} />
          <MenuRow icon="📝" label="我的帖子" onPress={() => { if (!isLoggedIn) { router.push('/login'); return } router.push('/collections?type=posts') }} theme={theme} />
          <MenuRow icon="📋" label="我的草稿" onPress={() => { if (!isLoggedIn) { router.push('/login'); return } router.push('/collections?type=drafts') }} theme={theme} />
          <MenuRow icon="👍" label="我的点赞" onPress={() => { if (!isLoggedIn) { router.push('/login'); return } router.push('/collections?type=liked') }} theme={theme} />
          <MenuRow icon="💬" label="我的回复" onPress={() => { if (!isLoggedIn) { router.push('/login'); return } router.push('/collections?type=replies') }} theme={theme} />
        </View>

        <View style={{ marginHorizontal: Spacing.lg, borderRadius: BorderRadius.lg, overflow: 'hidden', marginBottom: Spacing.lg }}>
          <MenuRow icon="🎫" label="优惠券" onPress={() => { if (!isLoggedIn) { router.push('/login'); return } router.push('/coupons') }} theme={theme} />
          <MenuRow icon="⚡" label="限时秒杀" onPress={() => router.push('/seckill')} theme={theme} />
          <MenuRow icon="👥" label="拼团专区" onPress={() => router.push('/group-buy')} theme={theme} />
          <MenuRow icon="📺" label="直播" onPress={() => router.push('/live')} theme={theme} />
          <MenuRow icon="📍" label="收货地址" onPress={() => { if (!isLoggedIn) { router.push('/login'); return } router.push('/address') }} theme={theme} />
          <MenuRow icon="⭐" label="签到" onPress={() => { if (!isLoggedIn) { router.push('/login'); return } router.push('/checkin') }} theme={theme} />
        </View>

        <View style={{ marginHorizontal: Spacing.lg, borderRadius: BorderRadius.lg, overflow: 'hidden', marginBottom: Spacing.lg }}>
          <MenuRow icon="⚙️" label="设置" onPress={() => router.push('/settings')} theme={theme} />
          <MenuRow icon={mode === 'ocean' ? '🌸' : '🌊'} label={mode === 'ocean' ? '切换樱花主题' : '切换深海主题'} onPress={toggleTheme} theme={theme} />
        </View>

        {/* Logout */}
        {isLoggedIn && (
          <TouchableOpacity
            onPress={handleLogout}
            style={{
              backgroundColor: theme.accentRed,
              borderRadius: BorderRadius.lg,
              padding: Spacing.xl,
              alignItems: 'center',
              marginHorizontal: Spacing.lg,
            }}
          >
            <Text style={{ color: '#FFFFFF', fontSize: FontSize.lg, fontWeight: '600' }}>退出登录</Text>
          </TouchableOpacity>
        )}
      </ScrollView>
    </View>
  )
}
