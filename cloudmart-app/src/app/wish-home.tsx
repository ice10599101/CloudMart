import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator } from 'react-native'
import { useState, useEffect } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors, FRUIT_LABELS, FRUIT_COLORS, formatCount } from '@/constants/wish-theme'
import type { HomeAggregation } from '@/types'

export default function WishHomeScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [loading, setLoading] = useState(true)
  const [data, setData] = useState<HomeAggregation | null>(null)

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await wishApi.getHome()
        if (res.data?.success) {
          setData(res.data.data)
        }
      } catch {
        // 错误已由 request 拦截器处理
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [])

  const goCreate = () => {
    if (!isLoggedIn) {
      router.push('/login')
      return
    }
    router.push('/wish-create')
  }

  if (loading) {
    return (
      <View
        style={{
          flex: 1,
          backgroundColor: WishColors.bgBase,
          justifyContent: 'center',
          alignItems: 'center',
          paddingTop: insets.top,
        }}
      >
        <ActivityIndicator size="large" color={WishColors.primary} />
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <ScrollView contentContainerStyle={{ paddingBottom: insets.bottom + 120 }}>
        {/* 顶部 Banner */}
        <View
          style={{
            margin: Spacing.md,
            padding: Spacing.xl,
            borderRadius: BorderRadius.xl,
            backgroundColor: WishColors.bgElevated,
          }}
        >
          <Text style={{ fontSize: 28, fontWeight: '800', color: WishColors.text }}>心愿宇宙</Text>
          <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginTop: Spacing.xs }}>
            每一个心愿，都是一颗种子
          </Text>
          <TouchableOpacity
            activeOpacity={0.8}
            onPress={goCreate}
            style={{
              alignSelf: 'flex-start',
              marginTop: Spacing.md,
              paddingHorizontal: Spacing.xl,
              paddingVertical: Spacing.sm,
              borderRadius: 24,
              backgroundColor: WishColors.primary,
            }}
          >
            <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#fff' }}>✨ 去许愿</Text>
          </TouchableOpacity>
        </View>

        {/* 世界树入口（Sprint 2.1） */}
        <TouchableOpacity
          activeOpacity={0.8}
          onPress={() => router.push('/world-tree')}
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginHorizontal: Spacing.md,
            padding: Spacing.lg,
            borderRadius: BorderRadius.xl,
            backgroundColor: 'rgba(15, 52, 96, 0.6)',
            borderWidth: 1,
            borderColor: 'rgba(61, 220, 151, 0.35)',
          }}
        >
          <View>
            <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: '#3ddc97' }}>🌳 世界生命树</Text>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 4 }}>
              每颗果实都是一个公开心愿 · 拖动旋转探索
            </Text>
          </View>
          <Text style={{ fontSize: FontSize.xl, color: WishColors.textTertiary }}>→</Text>
        </TouchableOpacity>

        {/* 我的心愿摘要 */}
        <SectionTitle title="我的心愿" actionLabel="全部" onAction={() => router.push('/my-wishes')} />
        {data && data.myWishes.length > 0 ? (
          data.myWishes.map((item) => (
            <TouchableOpacity
              key={item.wishId}
              activeOpacity={0.8}
              onPress={() => router.push(`/wish-detail?id=${item.wishId}`)}
              style={{
                marginHorizontal: Spacing.md,
                marginBottom: Spacing.sm,
                padding: Spacing.md,
                borderRadius: BorderRadius.lg,
                backgroundColor: WishColors.bgContainer,
                borderWidth: 1,
                borderColor: WishColors.border,
              }}
            >
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, flex: 1 }} numberOfLines={1}>
                  {item.title}
                </Text>
                <Text style={{ fontSize: FontSize.xs, color: FRUIT_COLORS[item.fruitType] || WishColors.accentCyan, marginLeft: Spacing.sm }}>
                  {FRUIT_LABELS[item.fruitType] || item.fruitType}
                </Text>
              </View>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: Spacing.xs }}>
                进度 {item.progress}%
              </Text>
            </TouchableOpacity>
          ))
        ) : (
          <EmptyHint text={isLoggedIn ? '还没有心愿，去种下第一颗种子吧' : '登录后开启你的心愿之旅'} />
        )}

        {/* 今日推荐 */}
        <SectionTitle title="今日推荐" />
        {data && data.todayRecommend.length > 0 ? (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: Spacing.md }}>
            {data.todayRecommend.map((item) => (
              <TouchableOpacity
                key={item.wishId}
                activeOpacity={0.8}
                onPress={() => router.push(`/wish-detail?id=${item.wishId}`)}
                style={{
                  width: 160,
                  marginRight: Spacing.md,
                  borderRadius: BorderRadius.lg,
                  backgroundColor: WishColors.bgContainer,
                  borderWidth: 1,
                  borderColor: WishColors.border,
                  overflow: 'hidden',
                }}
              >
                {item.coverUrl ? (
                  <Image source={{ uri: item.coverUrl }} style={{ width: '100%', height: 110, resizeMode: 'cover' }} />
                ) : (
                  <View
                    style={{
                      width: '100%',
                      height: 110,
                      backgroundColor: WishColors.bgElevated,
                      justifyContent: 'center',
                      alignItems: 'center',
                    }}
                  >
                    <Text style={{ fontSize: 30, opacity: 0.4 }}>
                      {item.fruitType === 'SPARK' ? '⭐' : '🌱'}
                    </Text>
                  </View>
                )}
                <View style={{ padding: Spacing.sm }}>
                  <Text style={{ fontSize: FontSize.sm, color: WishColors.text, fontWeight: '600' }} numberOfLines={2}>
                    {item.title}
                  </Text>
                  <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 4 }}>
                    {item.authorNickname} · ✨ {formatCount(item.supportCount)}
                  </Text>
                </View>
              </TouchableOpacity>
            ))}
          </ScrollView>
        ) : (
          <EmptyHint text="暂无推荐心愿" />
        )}

        {/* 热门共鸣 */}
        <SectionTitle title="热门共鸣" actionLabel="更多" onAction={() => router.push('/wish-square')} />
        {data && data.hotResonance.length > 0 ? (
          data.hotResonance.map((item, index) => (
            <TouchableOpacity
              key={item.wishId}
              activeOpacity={0.8}
              onPress={() => router.push(`/wish-detail?id=${item.wishId}`)}
              style={{
                marginHorizontal: Spacing.md,
                marginBottom: Spacing.sm,
                padding: Spacing.md,
                borderRadius: BorderRadius.lg,
                backgroundColor: WishColors.bgContainer,
                borderWidth: 1,
                borderColor: WishColors.border,
                flexDirection: 'row',
                alignItems: 'center',
              }}
            >
              <Text
                style={{
                  width: 28,
                  fontSize: FontSize.lg,
                  fontWeight: '800',
                  color: index < 3 ? WishColors.accentGold : WishColors.textTertiary,
                }}
              >
                {index + 1}
              </Text>
              <Text style={{ fontSize: FontSize.sm, color: WishColors.text, flex: 1, marginLeft: Spacing.sm }} numberOfLines={1}>
                {item.title}
              </Text>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.accentCyan, marginLeft: Spacing.sm }}>
                ✨ {formatCount(item.supportCount)}
              </Text>
            </TouchableOpacity>
          ))
        ) : (
          <EmptyHint text="暂无热门心愿" />
        )}
      </ScrollView>

      {/* 悬浮许愿按钮 */}
      <TouchableOpacity
        activeOpacity={0.85}
        onPress={goCreate}
        style={{
          position: 'absolute',
          right: Spacing.lg,
          bottom: insets.bottom + 24,
          width: 56,
          height: 56,
          borderRadius: 28,
          backgroundColor: WishColors.primary,
          justifyContent: 'center',
          alignItems: 'center',
          shadowColor: WishColors.primary,
          shadowOffset: { width: 0, height: 4 },
          shadowOpacity: 0.4,
          shadowRadius: 8,
          elevation: 6,
        }}
      >
        <Text style={{ fontSize: 26, color: '#fff' }}>✨</Text>
      </TouchableOpacity>
    </View>
  )
}

function SectionTitle({ title, actionLabel, onAction }: { title: string; actionLabel?: string; onAction?: () => void }) {
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginTop: Spacing.lg,
        marginBottom: Spacing.sm,
        paddingHorizontal: Spacing.md,
      }}
    >
      <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>{title}</Text>
      {actionLabel && onAction ? (
        <TouchableOpacity onPress={onAction}>
          <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan }}>{actionLabel} ›</Text>
        </TouchableOpacity>
      ) : null}
    </View>
  )
}

function EmptyHint({ text }: { text: string }) {
  return (
    <View style={{ padding: Spacing.xl, alignItems: 'center' }}>
      <Text style={{ fontSize: 32, opacity: 0.3 }}>🌌</Text>
      <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary, marginTop: Spacing.sm }}>{text}</Text>
    </View>
  )
}
