import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, Image, RefreshControl } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import type { MyLevelData } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import WishBGM from '@/components/WishBGM'
import type { BadgeRarity, BadgeWallItem } from '@/types'

const RARITY_META: Record<BadgeRarity, { label: string; color: string; emoji: string }> = {
  COMMON: { label: '普通', color: '#8a94a6', emoji: '🏅' },
  RARE: { label: '稀有', color: '#00d4ff', emoji: '💠' },
  EPIC: { label: '史诗', color: '#9370db', emoji: '💜' },
  LEGENDARY: { label: '传说', color: '#ffd700', emoji: '👑' },
}

export default function BadgeWallScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [badges, setBadges] = useState<BadgeWallItem[]>([])
  const [levelData, setLevelData] = useState<MyLevelData | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

  useEffect(() => {
    if (!isLoggedIn) {
      router.replace('/login')
    }
  }, [isLoggedIn])

  const loadBadges = useCallback(async () => {
    try {
      const [badgesRes, levelRes] = await Promise.all([
        wishApi.getMyBadges(),
        wishApi.getMyLevel(),
      ])
      if (badgesRes.data?.success) {
        setBadges(badgesRes.data.data)
      }
      if (levelRes.data?.success) {
        setLevelData(levelRes.data.data)
      }
    } catch {
      // 错误已由 request 处理
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (isLoggedIn) loadBadges()
  }, [isLoggedIn, loadBadges])

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    await loadBadges()
    setRefreshing(false)
  }, [loadBadges])

  const earnedCount = badges.filter((badge) => badge.earned).length

  const renderItem = ({ item }: { item: BadgeWallItem }) => {
    const rarity = RARITY_META[item.rarity] ?? RARITY_META.COMMON
    return (
      <View
        style={{
          position: 'relative',
          width: '48.5%',
          borderRadius: BorderRadius.lg,
          padding: Spacing.lg,
          alignItems: 'center',
          backgroundColor: item.earned ? 'rgba(255,215,0,0.06)' : 'rgba(255,255,255,0.03)',
          borderWidth: 1,
          borderColor: item.earned ? 'rgba(255,215,0,0.35)' : 'rgba(255,255,255,0.15)',
          borderStyle: item.earned ? 'solid' : 'dashed',
          opacity: item.earned ? 1 : 0.75,
        }}
      >
        {!item.earned && (
          <Text style={{ position: 'absolute', top: 8, right: 10, fontSize: 12, opacity: 0.7 }}>🔒</Text>
        )}
        <View
          style={{
            width: 56,
            height: 56,
            borderRadius: 28,
            justifyContent: 'center',
            alignItems: 'center',
            marginBottom: Spacing.sm,
            backgroundColor: item.earned ? 'rgba(255,215,0,0.15)' : 'rgba(255,255,255,0.05)',
          }}
        >
          {item.icon && item.icon.startsWith('http') ? (
            <Image source={{ uri: item.icon }} style={{ width: 40, height: 40 }} resizeMode="contain" />
          ) : (
            <Text style={{ fontSize: 26, opacity: item.earned ? 1 : 0.55 }}>{rarity.emoji}</Text>
          )}
        </View>
        <Text
          style={{
            fontSize: FontSize.sm,
            fontWeight: '600',
            color: item.earned ? WishColors.text : WishColors.textTertiary,
            textAlign: 'center',
          }}
          numberOfLines={1}
        >
          {item.name}
        </Text>
        <Text
          style={{
            marginTop: 4,
            fontSize: FontSize.xs,
            color: WishColors.textTertiary,
            textAlign: 'center',
            minHeight: 30,
            lineHeight: 15,
          }}
          numberOfLines={2}
        >
          {item.description || item.condition?.description || ''}
        </Text>
        <Text
          style={{
            marginTop: Spacing.sm,
            fontSize: 11,
            paddingHorizontal: 10,
            paddingVertical: 2,
            borderRadius: 999,
            borderWidth: 1,
            color: rarity.color,
            borderColor: rarity.color,
            opacity: item.earned ? 1 : 0.6,
          }}
        >
          {rarity.label}
        </Text>
        {item.earned ? (
          item.earnedAt ? (
            <Text style={{ marginTop: Spacing.sm, fontSize: 11, color: 'rgba(255,215,0,0.8)' }}>
              {new Date(item.earnedAt).toLocaleDateString('zh-CN')} 点亮
            </Text>
          ) : null
        ) : item.progress ? (
          <View style={{ marginTop: Spacing.sm, width: '100%', alignItems: 'center' }}>
            <View
              style={{
                width: '90%',
                height: 5,
                borderRadius: 3,
                backgroundColor: 'rgba(255,255,255,0.08)',
                overflow: 'hidden',
              }}
            >
              <View
                style={{
                  width: `${Math.min(Math.max(item.progress.percentage, 0), 100)}%`,
                  height: '100%',
                  borderRadius: 3,
                  backgroundColor: WishColors.accentCyan,
                }}
              />
            </View>
            <Text style={{ marginTop: 4, fontSize: 11, color: WishColors.textTertiary }}>
              {item.progress.current}/{item.progress.threshold}
            </Text>
          </View>
        ) : null}
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: Spacing.md,
          borderBottomWidth: 1,
          borderBottomColor: WishColors.border,
        }}
      >
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.lg, color: WishColors.textSecondary }}>‹ 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>我的徽章</Text>
        <View style={{ width: 44 }} />
      </View>

      {loading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color={WishColors.primary} />
        </View>
      ) : (
        <FlatList
          data={badges}
          keyExtractor={(item) => String(item.badgeId)}
          renderItem={renderItem}
          numColumns={2}
          columnWrapperStyle={{ justifyContent: 'space-between', paddingHorizontal: Spacing.md, marginBottom: Spacing.md }}
          contentContainerStyle={{ paddingTop: Spacing.md, paddingBottom: insets.bottom + 24 }}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={WishColors.primary} />}
          ListHeaderComponent={
            <View style={{ paddingHorizontal: Spacing.md, marginBottom: Spacing.md }}>
              <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>🏅 我的徽章墙</Text>
              <Text style={{ marginTop: 4, fontSize: FontSize.sm, color: WishColors.textTertiary }}>
                已点亮 {earnedCount} / {badges.length} 枚徽章
              </Text>

              {/* 等级与晋级进度（文档 L1916：心愿殿堂式等级展示） */}
              {levelData && (
                <View
                  style={{
                    marginTop: Spacing.md,
                    padding: Spacing.lg,
                    borderRadius: BorderRadius.lg,
                    backgroundColor: 'rgba(255, 215, 0, 0.08)',
                    borderWidth: 1,
                    borderColor: 'rgba(255, 215, 0, 0.3)',
                  }}
                >
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.md }}>
                    <View
                      style={{
                        paddingVertical: 8,
                        paddingHorizontal: Spacing.md,
                        borderRadius: BorderRadius.md,
                        backgroundColor: 'rgba(255, 215, 0, 0.2)',
                        borderWidth: 1,
                        borderColor: 'rgba(255, 215, 0, 0.55)',
                      }}
                    >
                      <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: '#ffd700' }}>
                        Lv.{levelData.level}
                      </Text>
                    </View>
                    <View style={{ flex: 1 }}>
                      <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text }}>
                        {levelData.levelTitle}
                      </Text>
                      <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>
                        {levelData.nextLevel
                          ? `距 Lv.${levelData.nextLevel} ${levelData.nextLevelTitle}`
                          : '已达最高等级 ✨'}
                      </Text>
                    </View>
                  </View>
                  {levelData.nextLevelRequirements.map((req) => (
                    <View key={req.metric} style={{ marginTop: Spacing.md }}>
                      <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 }}>
                        <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>{req.label}</Text>
                        <Text style={{ fontSize: FontSize.xs, color: '#ffd700' }}>
                          {req.current}/{req.threshold}
                        </Text>
                      </View>
                      <View
                        style={{
                          height: 6,
                          borderRadius: 3,
                          backgroundColor: 'rgba(255, 255, 255, 0.08)',
                          overflow: 'hidden',
                        }}
                      >
                        <View
                          style={{
                            width: `${Math.min(Math.max(req.percentage, 0), 100)}%`,
                            height: '100%',
                            borderRadius: 3,
                            backgroundColor: '#00d4ff',
                          }}
                        />
                      </View>
                    </View>
                  ))}
                </View>
              )}
            </View>
          }
          ListEmptyComponent={
            <View style={{ alignItems: 'center', padding: Spacing.xl * 2 }}>
              <Text style={{ fontSize: 48, opacity: 0.3 }}>🌟</Text>
              <Text style={{ fontSize: FontSize.md, color: WishColors.textTertiary, marginTop: Spacing.md, textAlign: 'center' }}>
                暂无徽章定义{'\n'}许下第一个心愿即可点亮第一枚
              </Text>
            </View>
          }
        />
      )}

      <WishBGM />
    </View>
  )
}
