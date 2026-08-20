import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, Modal, RefreshControl } from 'react-native'
import { useCallback, useEffect, useRef, useState } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors, FRUIT_COLORS } from '@/constants/wish-theme'
import WorldTree3D from '@/components/WorldTree3D'
import type { FruitType, TreeFruit, TreeSeason, TreeEnvironment, WorldTreeAggregation } from '@/types'

const FRUIT_LABELS: Record<FruitType, string> = {
  GLOW: '微光',
  RESONANCE: '共鸣',
  BLOOM: '绽放',
  SPARK: '星火',
}

const SEASON_LABELS: Record<TreeSeason, string> = {
  SPRING: '春',
  SUMMER: '夏',
  AUTUMN: '秋',
  WINTER: '冬',
}

const ENVIRONMENT_LABELS: Record<TreeEnvironment, string> = {
  SUNNY: '☀️ 晴',
  RAIN: '🌧️ 雨',
  RAINBOW: '🌈 虹',
}

/** 首屏全量拉取条数（公开接口，无 bounds） */
const FIRST_PAGE_SIZE = 100

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

/**
 * 世界树页面（Sprint 2.1，APP 端）：
 * 伪 3D 星图（PanResponder 拖拽旋转视角）+ 统计聚合 + 果实点选详情弹层。
 */
export default function WorldTreeScreen() {
  const insets = useSafeAreaInsets()
  const [loading, setLoading] = useState(true)
  const [aggregation, setAggregation] = useState<WorldTreeAggregation | null>(null)
  const [fruits, setFruits] = useState<TreeFruit[]>([])
  const [selectedFruit, setSelectedFruit] = useState<TreeFruit | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const fruitsMapRef = useRef<Map<number, TreeFruit>>(new Map())

  /** 合并果实（按 id 去重） */
  const mergeFruits = useCallback((list: TreeFruit[]) => {
    const map = fruitsMapRef.current
    list.forEach((fruit) => map.set(fruit.id, fruit))
    setFruits(Array.from(map.values()))
  }, [])

  const fetchData = useCallback(async () => {
    try {
      const [treeRes, fruitsRes] = await Promise.all([
        wishApi.getWorldTree(),
        wishApi.listTreeFruits({ pageSize: FIRST_PAGE_SIZE }),
      ])
      if (treeRes.data?.success && treeRes.data.data) {
        setAggregation(treeRes.data.data)
      }
      if (fruitsRes.data?.success && Array.isArray(fruitsRes.data.data)) {
        mergeFruits(fruitsRes.data.data)
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setLoading(false)
    }
  }, [mergeFruits])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  /** 下拉刷新：清空本地果实缓存后重拉（聚合与列表口径重新对齐） */
  const handleRefresh = useCallback(async () => {
    setRefreshing(true)
    fruitsMapRef.current.clear()
    await fetchData()
    setRefreshing(false)
  }, [fetchData])

  const goDetail = () => {
    if (!selectedFruit) return
    setSelectedFruit(null)
    router.push(`/wish-detail?id=${selectedFruit.id}`)
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
      {/* 顶栏 */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          paddingHorizontal: Spacing.lg,
          paddingVertical: Spacing.md,
        }}
      >
        <TouchableOpacity activeOpacity={0.7} onPress={() => router.back()} style={{ padding: Spacing.xs }}>
          <Text style={{ fontSize: FontSize.xl, color: WishColors.textSecondary }}>←</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.xxl, fontWeight: '700', color: WishColors.text, marginLeft: Spacing.sm }}>
          世界树
        </Text>
      </View>

      <ScrollView
        contentContainerStyle={{ paddingBottom: insets.bottom + 40 }}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            tintColor={WishColors.primary}
            colors={[WishColors.primary]}
          />
        }
      >
        {/* 统计条 */}
        {aggregation && (
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              marginHorizontal: Spacing.lg,
              padding: Spacing.lg,
              borderRadius: BorderRadius.lg,
              backgroundColor: WishColors.bgContainer,
            }}
          >
            <View style={{ flex: 1, alignItems: 'center' }}>
              <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: WishColors.text }}>
                {formatCount(aggregation.totalFruits)}
              </Text>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>果实</Text>
            </View>
            <View style={{ flex: 1, alignItems: 'center' }}>
              <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: WishColors.text }}>
                {formatCount(aggregation.totalBloom)}
              </Text>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>绽放</Text>
            </View>
            <View style={{ flex: 1, alignItems: 'center' }}>
              <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: WishColors.text }}>
                {formatCount(aggregation.totalLight)}
              </Text>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>星光</Text>
            </View>
            <View style={{ alignItems: 'flex-end', gap: Spacing.xs }}>
              <View
                style={{
                  paddingHorizontal: Spacing.md,
                  paddingVertical: 2,
                  borderRadius: BorderRadius.full,
                  borderWidth: 1,
                  borderColor: WishColors.border,
                }}
              >
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>
                  {SEASON_LABELS[aggregation.season]}
                </Text>
              </View>
              <View
                style={{
                  paddingHorizontal: Spacing.md,
                  paddingVertical: 2,
                  borderRadius: BorderRadius.full,
                  borderWidth: 1,
                  borderColor: WishColors.border,
                }}
              >
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>
                  {ENVIRONMENT_LABELS[aggregation.environment]}
                </Text>
              </View>
            </View>
          </View>
        )}

        {/* 3D 舞台 */}
        <View style={{ marginHorizontal: Spacing.xl, marginVertical: Spacing.lg }}>
          <WorldTree3D
            fruits={fruits}
            season={aggregation?.season ?? null}
            environment={aggregation?.environment ?? null}
            onFruitSelect={setSelectedFruit}
          />
          <Text
            style={{
              textAlign: 'center',
              marginTop: Spacing.sm,
              fontSize: FontSize.xs,
              color: WishColors.textTertiary,
            }}
          >
            拖动旋转视角 · 点击果实查看心愿
          </Text>
        </View>

        {/* 图例 */}
        <View
          style={{
            flexDirection: 'row',
            justifyContent: 'center',
            gap: Spacing.xxl,
            marginTop: Spacing.xs,
          }}
        >
          {(Object.keys(FRUIT_LABELS) as FruitType[]).map((type) => (
            <View key={type} style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
              <View
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: 4,
                  backgroundColor: FRUIT_COLORS[type],
                }}
              />
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>{FRUIT_LABELS[type]}</Text>
            </View>
          ))}
        </View>
      </ScrollView>

      {/* 选中果实详情弹层 */}
      <Modal
        visible={selectedFruit !== null}
        transparent
        animationType="slide"
        onRequestClose={() => setSelectedFruit(null)}
      >
        <TouchableOpacity
          activeOpacity={1}
          onPress={() => setSelectedFruit(null)}
          style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' }}
        >
          <TouchableOpacity
            activeOpacity={1}
            onPress={() => undefined}
            style={{
              backgroundColor: WishColors.bgContainer,
              borderTopLeftRadius: BorderRadius.xl,
              borderTopRightRadius: BorderRadius.xl,
              padding: Spacing.xxl,
              paddingBottom: insets.bottom + Spacing.xl,
            }}
          >
            {selectedFruit && (
              <>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
                  <View
                    style={{
                      width: 10,
                      height: 10,
                      borderRadius: 5,
                      backgroundColor: FRUIT_COLORS[selectedFruit.fruitType] || '#fff',
                    }}
                  />
                  <Text
                    style={{ flex: 1, fontSize: FontSize.lg, fontWeight: '600', color: WishColors.text }}
                    numberOfLines={2}
                  >
                    {selectedFruit.title}
                  </Text>
                </View>
                <View
                  style={{
                    flexDirection: 'row',
                    justifyContent: 'space-between',
                    marginTop: Spacing.md,
                  }}
                >
                  <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary }}>
                    {selectedFruit.authorNickname}
                  </Text>
                  <Text style={{ fontSize: FontSize.sm, color: WishColors.accentGold }}>
                    ✨ {formatCount(selectedFruit.lightCount)} 星光
                  </Text>
                </View>
                <View style={{ flexDirection: 'row', gap: Spacing.lg, marginTop: Spacing.xl }}>
                  <TouchableOpacity
                    activeOpacity={0.8}
                    onPress={() => setSelectedFruit(null)}
                    style={{
                      flex: 1,
                      alignItems: 'center',
                      paddingVertical: Spacing.md,
                      borderRadius: BorderRadius.full,
                      borderWidth: 1,
                      borderColor: WishColors.border,
                    }}
                  >
                    <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>关闭</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    activeOpacity={0.8}
                    onPress={goDetail}
                    style={{
                      flex: 1,
                      alignItems: 'center',
                      paddingVertical: Spacing.md,
                      borderRadius: BorderRadius.full,
                      backgroundColor: WishColors.primary,
                    }}
                  >
                    <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#fff' }}>查看详情</Text>
                  </TouchableOpacity>
                </View>
              </>
            )}
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </View>
  )
}
