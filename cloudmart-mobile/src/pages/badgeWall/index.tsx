import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import type { BadgeWallItem, BadgeRarity } from '@/types'
import styles from './index.module.scss'

const RARITY_META: Record<BadgeRarity, { label: string; color: string; emoji: string }> = {
  COMMON: { label: '普通', color: '#8a94a6', emoji: '🏅' },
  RARE: { label: '稀有', color: '#00d4ff', emoji: '💠' },
  EPIC: { label: '史诗', color: '#9370db', emoji: '💜' },
  LEGENDARY: { label: '传说', color: '#ffd700', emoji: '👑' },
}

export default function BadgeWallPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [badges, setBadges] = useState<BadgeWallItem[]>([])

  const fetchBadges = useCallback(async () => {
    try {
      const res = await wishApi.getMyBadges()
      if (res.data.success) {
        setBadges(res.data.data)
      }
    } catch {
      // 错误已由 request 处理
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!isLoggedIn) {
      Taro.redirectTo({ url: '/pages/login/index' })
      return
    }
    fetchBadges()
  }, [isLoggedIn, fetchBadges])

  const earnedCount = badges.filter((badge) => badge.earned).length

  const renderBadgeIcon = (badge: BadgeWallItem, rarity: { emoji: string }) =>
    badge.icon && badge.icon.startsWith('http') ? (
      <Image className={styles.badgeImg} src={badge.icon} mode='aspectFit' />
    ) : (
      <Text className={styles.badgeEmoji}>{rarity.emoji}</Text>
    )

  return (
    <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
      <CustomNavBar title='我的徽章' back />

      {loading ? (
        <View className={styles.loading}>
          <View className={styles.spinner} />
        </View>
      ) : (
        <ScrollView scrollY className={styles.scroll}>
          <View className={styles.header}>
            <Text className={styles.pageTitle}>🏅 我的徽章墙</Text>
            <Text className={styles.summary}>已点亮 {earnedCount} / {badges.length} 枚徽章</Text>
          </View>

          {badges.length === 0 ? (
            <View className={styles.empty}>
              <Text className={styles.emptyIcon}>🌟</Text>
              <Text className={styles.emptyText}>暂无徽章定义，许下第一个心愿即可点亮第一枚</Text>
            </View>
          ) : (
            <View className={styles.grid}>
              {badges.map((badge) => {
                const rarity = RARITY_META[badge.rarity] ?? RARITY_META.COMMON
                return (
                  <View
                    key={badge.badgeId}
                    className={`${styles.badgeCard} ${badge.earned ? styles.badgeCardEarned : styles.badgeCardLocked}`}
                  >
                    {!badge.earned && <Text className={styles.lockMark}>🔒</Text>}
                    <View
                      className={`${styles.badgeIcon} ${badge.earned ? styles.badgeIconEarned : styles.badgeIconLocked}`}
                    >
                      {renderBadgeIcon(badge, rarity)}
                    </View>
                    <Text className={`${styles.badgeName} ${!badge.earned ? styles.textLocked : ''}`}>
                      {badge.name}
                    </Text>
                    <Text className={`${styles.badgeDesc} ${!badge.earned ? styles.textLocked : ''}`}>
                      {badge.description || badge.condition?.description || ''}
                    </Text>
                    <Text className={styles.rarityTag} style={{ color: rarity.color, borderColor: rarity.color }}>
                      {rarity.label}
                    </Text>
                    {badge.earned ? (
                      badge.earnedAt && (
                        <Text className={styles.earnedAt}>
                          {new Date(badge.earnedAt).toLocaleDateString('zh-CN')} 点亮
                        </Text>
                      )
                    ) : (
                      badge.progress && (
                        <View className={styles.progressRow}>
                          <View className={styles.progressBg}>
                            <View
                              className={styles.progressBar}
                              style={{ width: `${Math.min(badge.progress.percentage, 100)}%` }}
                            />
                          </View>
                          <Text className={styles.progressText}>
                            {badge.progress.current}/{badge.progress.threshold}
                          </Text>
                        </View>
                      )
                    )}
                  </View>
                )
              })}
            </View>
          )}
          <View style={{ height: '120rpx' }} />
        </ScrollView>
      )}
      <WishBGM />
    </View>
  )
}
