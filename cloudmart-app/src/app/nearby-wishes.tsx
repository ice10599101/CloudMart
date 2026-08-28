import { View, Text, FlatList, TouchableOpacity, ActivityIndicator } from 'react-native'
import { useCallback, useEffect, useState } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import type { NearbyWish } from '@/types'

/**
 * 附近心愿（Sprint 3.1 APP 端）：列表模式（同一 API 契约，数据与三端一致）。
 * 地图渲染需 react-native-amap3d（原生依赖 + 高德 Key），待配置后升级
 * 地图模式（偏差留档进度文件四V·5）；本页为无依赖可用形态。
 */
export default function NearbyWishesScreen() {
  const insets = useSafeAreaInsets()
  const [wishes, setWishes] = useState<NearbyWish[]>([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      // 定位依赖 expo-location 权限链路，本页先走服务端默认城市兜底（空坐标语义）
      const res = await wishApi.getMapWishes({ radius: 5000 })
      if (res.data.success) setWishes(res.data.data ?? [])
    } catch {
      // 静默空态
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      {/* 顶栏 */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingHorizontal: Spacing.md,
          paddingVertical: Spacing.sm,
          borderBottomWidth: 1,
          borderBottomColor: WishColors.border,
        }}
      >
        <TouchableOpacity onPress={() => router.back()} accessibilityLabel="返回">
          <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: WishColors.text }}>附近的心愿</Text>
        <TouchableOpacity accessibilityLabel="刷新" onPress={load}>
          <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan }}>刷新</Text>
        </TouchableOpacity>
      </View>

      <Text
        style={{
          paddingHorizontal: Spacing.md,
          paddingVertical: Spacing.sm,
          fontSize: FontSize.xs,
          color: WishColors.textTertiary,
        }}
      >
        坐标经 geohash 模糊化（约 150m 网格 + 偏移），仅展示公开心愿 · 定位失败时展示默认城市
      </Text>

      <FlatList
        data={wishes}
        keyExtractor={(item) => String(item.wishId)}
        renderItem={({ item }) => (
          <TouchableOpacity
            activeOpacity={0.8}
            onPress={() => router.push(`/wish-detail?id=${item.wishId}`)}
            style={{
              marginHorizontal: Spacing.md,
              marginBottom: Spacing.sm,
              backgroundColor: WishColors.bgContainer,
              borderWidth: 1,
              borderColor: WishColors.border,
              borderRadius: BorderRadius.xl,
              padding: Spacing.md,
            }}
          >
            <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text }}>
              「{item.title}」
            </Text>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 4 }}>
              距离约 {item.distance}m · {item.lightCount} 次点亮
            </Text>
          </TouchableOpacity>
        )}
        ListEmptyComponent={
          loading ? (
            <ActivityIndicator color={WishColors.primary} style={{ marginVertical: Spacing.xxl }} />
          ) : (
            <Text style={{ textAlign: 'center', color: WishColors.textTertiary, padding: Spacing.lg, fontSize: FontSize.sm }}>
              附近暂无公开心愿
            </Text>
          )
        }
        contentContainerStyle={{ paddingVertical: Spacing.sm, paddingBottom: insets.bottom + Spacing.xl }}
      />
    </View>
  )
}
