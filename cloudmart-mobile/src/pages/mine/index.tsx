import { useState, useEffect } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'
import { growthApi } from '@/api/growth'
import { ICON_BASE64 } from '@/components/Icon'
import { useThemeClass } from '@/composables/useThemeClass'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import CustomTabBar from '@/components/CustomTabBar'
import styles from './index.module.scss'

const ORDER_TABS = [
  { icon: '💳', name: '待付款', status: 0 },
  { icon: '📦', name: '待发货', status: 1 },
  { icon: '🚚', name: '待收货', status: 2 },
  { icon: '✅', name: '已完成', status: 3 },
  { icon: '↩️', name: '退款', status: 4 },
]

const MENU_ITEMS = [
  { icon: '🤖', name: 'AI 助手', path: '/pages/aiChat/index', color: 'var(--color-accent-purple)' },
  { icon: '🌟', name: '心愿助手', path: '/pages/aiAssistant/index', color: 'var(--color-accent-purple)' },
  { icon: '🏅', name: '我的徽章', path: '/pages/badgeWall/index', color: 'var(--color-accent-gold)' },
  { icon: '📝', name: '我的帖子', path: '/pages/collections/index?type=posts', color: 'var(--color-accent-green)' },
  { icon: '📋', name: '我的草稿', path: '/pages/collections/index?type=drafts', color: 'var(--color-primary)' },
  { icon: '👍', name: '我的点赞', path: '/pages/collections/index?type=liked', color: 'var(--color-accent-red)' },
  { icon: '💬', name: '我的回复', path: '/pages/collections/index?type=replies', color: 'var(--color-accent-orange)' },
  { icon: '📍', name: '收货地址', path: '/pages/address/index', color: 'var(--color-accent-gold)' },
  { icon: '⚙️', name: '设置', path: '/pages/settings/index', color: 'var(--color-text-tertiary)' },
]

export default function MinePage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { user, isLoggedIn, fetchUser, logout } = useAuthStore()
  const { mode, toggleTheme } = useThemeStore()
  const [isCheckedIn, setIsCheckedIn] = useState(false)

  useEffect(() => {
    if (isLoggedIn) {
      fetchUser()
      loadCheckInStatus()
    }
  }, [isLoggedIn])

  const loadCheckInStatus = async () => {
    try {
      const res = await growthApi.getCheckInStatus()
      setIsCheckedIn(res.data?.data?.isCheckedIn || false)
    } catch {
      // API unavailable
    }
  }

  const handleLogin = () => {
    Taro.navigateTo({ url: '/pages/login/index' })
  }

  const handleLogout = async () => {
    const res = await Taro.showModal({ title: '提示', content: '确定要退出登录吗？' })
    if (res.confirm) {
      await logout()
    }
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={{ ...themeStyle, paddingTop: `${statusBarHeight + navBarHeight}px` }}>
      <CustomNavBar title="CloudMart" />
<ScrollView scrollY className={styles.scrollContent}>
        {/* Hero Section */}
        <View className={styles.hero}>
          <View className={styles.heroGlow} />
          {isLoggedIn && user ? (
            <View className={styles.profileSection}>
              <View className={styles.avatarRing}>
                <View className={styles.avatarRingInner} />
                <Image className={styles.avatar} src={user.avatar} />
              </View>
              <View className={styles.userInfo}>
                <View className={styles.nameRow}>
                  <Text className={styles.nickname}>{user.nickname}</Text>
                  {user.level && (
                    <View className={styles.levelBadge}>
                      <Text className={styles.levelText}>Lv.{user.level}</Text>
                    </View>
                  )}
                </View>
                {user.signature && <Text className={styles.signature}>{user.signature}</Text>}
              </View>
              <View className={styles.editBtn} onClick={() => Taro.navigateTo({ url: '/pages/profile/index' })}>
                <Text className={styles.editBtnText}>编辑资料</Text>
              </View>
            </View>
          ) : (
            <View className={styles.loginSection}>
              <View className={styles.avatarRing}>
                <View className={styles.avatarRingInner} />
                <View className={styles.defaultAvatar}>
                  <Image src={ICON_BASE64['user-placeholder'].default} style={{ width: '40px', height: '40px' }} mode='aspectFit' />
                </View>
              </View>
              <View className={styles.loginBtn} onClick={handleLogin}>
                <Text className={styles.loginBtnText}>登录 / 注册</Text>
              </View>
            </View>
          )}
        </View>

        {/* Stats Row */}
        {isLoggedIn && (
          <View className={styles.statsRow}>
            <View className={styles.statItem} onClick={() => Taro.navigateTo({ url: '/pages/following/index?type=following' })}>
              <Text className={styles.statNumber}>{user?.followingCount || 0}</Text>
              <Text className={styles.statLabel}>关注</Text>
            </View>
            <View className={styles.statDivider} />
            <View className={styles.statItem} onClick={() => Taro.navigateTo({ url: '/pages/following/index?type=followers' })}>
              <Text className={styles.statNumber}>{user?.followerCount || 0}</Text>
              <Text className={styles.statLabel}>粉丝</Text>
            </View>
            <View className={styles.statDivider} />
            <View className={styles.statItem} onClick={() => Taro.navigateTo({ url: '/pages/collections/index?type=liked' })}>
              <Text className={styles.statNumber}>{user?.likeCount || 0}</Text>
              <Text className={styles.statLabel}>获赞</Text>
            </View>
          </View>
        )}

        {/* Quick Actions */}
        <View className={styles.quickGrid}>
          <View className={styles.quickCard} onClick={() => Taro.navigateTo({ url: '/pages/orders/index' })}>
            <Text className={styles.quickIcon}>📋</Text>
            <Text className={styles.quickName}>我的订单</Text>
          </View>
          <View className={styles.quickCard} onClick={() => Taro.navigateTo({ url: '/pages/cart/index' })}>
            <Text className={styles.quickIcon}>🛒</Text>
            <Text className={styles.quickName}>购物车</Text>
          </View>
          <View className={styles.quickCard} onClick={() => Taro.navigateTo({ url: '/pages/wishlist/index' })}>
            <Text className={styles.quickIcon}>❤️</Text>
            <Text className={styles.quickName}>收藏夹</Text>
          </View>
          <View className={styles.quickCard} onClick={() => Taro.navigateTo({ url: '/pages/coupons/index' })}>
            <Text className={styles.quickIcon}>🎫</Text>
            <Text className={styles.quickName}>优惠券</Text>
          </View>
          <View className={styles.quickCard} onClick={() => Taro.navigateTo({ url: '/pages/checkIn/index' })}>
            <Text className={styles.quickIcon}>{isCheckedIn ? '✅' : '📅'}</Text>
            <Text className={styles.quickName}>{isCheckedIn ? '已签到' : '签到'}</Text>
          </View>
        </View>

        {/* Order Section */}
        <View className={styles.section}>
          <View className={styles.sectionHeader}>
            <Text className={styles.sectionTitle}>我的订单</Text>
            <Text className={styles.sectionAction} onClick={() => Taro.navigateTo({ url: '/pages/orders/index' })}>查看全部 ›</Text>
          </View>
          <View className={styles.orderTabs}>
            {ORDER_TABS.map((tab) => (
              <View key={tab.status} className={styles.orderTab} onClick={() => Taro.navigateTo({ url: `/pages/orders/index?status=${tab.status}` })}>
                <Text className={styles.orderTabIcon}>{tab.icon}</Text>
                <Text className={styles.orderTabName}>{tab.name}</Text>
              </View>
            ))}
          </View>
        </View>

        {/* Menu */}
        <View className={styles.section}>
          {MENU_ITEMS.map((item) => (
            <View
              key={item.name}
              className={styles.menuItem}
              onClick={() => Taro.navigateTo({ url: item.path })}
            >
              <View className={styles.menuIconWrap} style={{ backgroundColor: `${item.color}20` }}>
                <Text>{item.icon}</Text>
              </View>
              <Text className={styles.menuName}>{item.name}</Text>
              <Text className={styles.menuArrow}>›</Text>
            </View>
          ))}
        </View>

        {/* Theme Toggle & Logout */}
        <View className={styles.bottomSection}>
          <View className={styles.themeCard} onClick={toggleTheme}>
            <Text className={styles.themeIcon}>{mode === 'ocean' ? '🌸' : '🌊'}</Text>
            <Text className={styles.themeLabel}>{mode === 'ocean' ? '切换樱花主题' : '切换深海主题'}</Text>
          </View>
          {isLoggedIn && (
            <View className={styles.logoutBtn} onClick={handleLogout}>
              <Text className={styles.logoutText}>退出登录</Text>
            </View>
          )}
        </View>
      </ScrollView>
      <CustomTabBar />
    </View>
  )
}
