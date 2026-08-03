import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
  Image,
  Alert,
} from 'react-native'
import { useState, useEffect, useCallback, useRef } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { marketingApi } from '@/api/marketing'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

interface SeckillActivity {
  id: number
  name: string
  startTime: string
  endTime: string
  status: number
  products: SeckillProduct[]
}

interface SeckillProduct {
  id: number
  productId: number
  productName: string
  productImage: string
  originalPrice: number
  seckillPrice: number
  totalStock: number
  availableStock: number
  limitPerUser: number
}

function formatTimeRange(start: string, end: string): string {
  const s = new Date(start)
  const e = new Date(end)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(s.getHours())}:${pad(s.getMinutes())} - ${pad(e.getHours())}:${pad(e.getMinutes())}`
}

function getCountdown(endTime: string): string {
  const diff = new Date(endTime).getTime() - Date.now()
  if (diff <= 0) return '已结束'
  const hours = Math.floor(diff / 3600000)
  const minutes = Math.floor((diff % 3600000) / 60000)
  const seconds = Math.floor((diff % 60000) / 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
}

function SeckillProductCard({
  product,
  activityId,
  theme,
  onBuy,
  buying,
}: {
  product: SeckillProduct
  activityId: number
  theme: ReturnType<typeof useTheme>
  onBuy: (activityId: number, product: SeckillProduct) => void
  buying: boolean
}) {
  const soldCount = product.totalStock - product.availableStock
  const soldPercent = product.totalStock > 0 ? Math.min((soldCount / product.totalStock) * 100, 100) : 0
  const isSoldOut = product.availableStock <= 0

  return (
    <View
      style={{
        flexDirection: 'row',
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        borderWidth: 1,
        borderColor: theme.border,
        marginBottom: Spacing.md,
        ...theme.shadowCard,
      }}
    >
      <Image
        source={{ uri: product.productImage }}
        style={{ width: 120, height: 120 }}
        resizeMode="cover"
      />

      <View style={{ flex: 1, padding: Spacing.md, justifyContent: 'space-between' }}>
        <Text
          numberOfLines={2}
          style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text, lineHeight: 20 }}
        >
          {product.productName}
        </Text>

        <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: Spacing.sm, marginTop: Spacing.xs }}>
          <Text style={{ fontSize: FontSize.xxl, fontWeight: '800', color: theme.accentRed }}>
            ¥{product.seckillPrice}
          </Text>
          <Text
            style={{
              fontSize: FontSize.sm,
              color: theme.textTertiary,
              textDecorationLine: 'line-through',
            }}
          >
            ¥{product.originalPrice}
          </Text>
        </View>

        {/* Progress bar */}
        <View style={{ marginTop: Spacing.xs }}>
          <View
            style={{
              height: 14,
              backgroundColor: theme.bgInput,
              borderRadius: 7,
              overflow: 'hidden',
              justifyContent: 'center',
            }}
          >
            <View
              style={{
                height: '100%',
                width: `${soldPercent}%`,
                backgroundColor: theme.accentRed,
                borderRadius: 7,
                opacity: 0.85,
              }}
            />
            <Text
              style={{
                position: 'absolute',
                alignSelf: 'center',
                fontSize: 9,
                fontWeight: '700',
                color: '#FFFFFF',
              }}
            >
              {soldPercent >= 10 ? `已抢${Math.round(soldPercent)}%` : ''}
            </Text>
          </View>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>
            每人限购{product.limitPerUser}件
          </Text>
        </View>

        {/* Buy button */}
        <TouchableOpacity
          activeOpacity={0.7}
          disabled={isSoldOut || buying}
          onPress={() => onBuy(activityId, product)}
          style={{
            alignSelf: 'flex-end',
            paddingHorizontal: Spacing.lg,
            paddingVertical: Spacing.sm,
            borderRadius: BorderRadius.xl,
            backgroundColor: isSoldOut ? theme.bgInput : theme.accentRed,
            marginTop: Spacing.xs,
          }}
        >
          <Text
            style={{
              fontSize: FontSize.sm,
              fontWeight: '700',
              color: isSoldOut ? theme.textTertiary : '#FFFFFF',
            }}
          >
            {buying ? '抢购中...' : isSoldOut ? '已售罄' : '马上抢'}
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  )
}

export default function SeckillPage() {
  const theme = useTheme()
  const [activities, setActivities] = useState<SeckillActivity[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [buyingProductId, setBuyingProductId] = useState<number | null>(null)
  const [countdowns, setCountdowns] = useState<Record<number, string>>({})
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const loadActivities = useCallback(async () => {
    try {
      const res = await marketingApi.getSeckillActivities()
      const list = (res.data as any)?.data ?? res.data
      setActivities(Array.isArray(list) ? list : [])
    } catch {
      setActivities([])
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [])

  useEffect(() => {
    loadActivities()
  }, [loadActivities])

  // Countdown timer
  useEffect(() => {
    const updateCountdowns = () => {
      const next: Record<number, string> = {}
      for (const activity of activities) {
        next[activity.id] = getCountdown(activity.endTime)
      }
      setCountdowns(next)
    }

    updateCountdowns()
    timerRef.current = setInterval(updateCountdowns, 1000)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [activities])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    loadActivities()
  }, [loadActivities])

  const handleBuy = useCallback(
    async (activityId: number, product: SeckillProduct) => {
      Alert.alert('确认抢购', `确定以 ¥${product.seckillPrice} 秒杀「${product.productName}」？每人限购${product.limitPerUser}件`, [
        { text: '取消', style: 'cancel' },
        {
          text: '确定',
          style: 'default',
          onPress: async () => {
            if (buyingProductId !== null) return
            setBuyingProductId(product.id)
            try {
              const res = await marketingApi.executeSeckill({ activityId, seckillProductId: product.id })
              const orderId = (res.data as any)?.data?.orderId ?? (res.data as any)?.data?.id
              Alert.alert('抢购成功', '秒杀订单已创建', [
                {
                  text: '查看订单',
                  onPress: () => {
                    if (orderId) {
                      router.push(`/order-detail?id=${orderId}`)
                    }
                  },
                },
                { text: '留在当前页' },
              ])
            } catch {
              Alert.alert('抢购失败', '请稍后重试')
            } finally {
              setBuyingProductId(null)
            }
          },
        },
      ])
    },
    [buyingProductId],
  )

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView
        contentContainerStyle={{ paddingBottom: Spacing.xxxl }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.primary} />}
        showsVerticalScrollIndicator={false}
      >
        {/* Header */}
        <View
          style={{
            paddingHorizontal: Spacing.lg,
            paddingTop: Spacing.xl,
            paddingBottom: Spacing.md,
          }}
        >
          <Text style={{ fontSize: FontSize.xxl, fontWeight: '800', color: theme.text }}>
            ⚡ 限时秒杀
          </Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>
            手慢无，限时限量抢购
          </Text>
        </View>

        {activities.length === 0 ? (
          <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 2 }}>
            <Text style={{ fontSize: 48, marginBottom: Spacing.md, opacity: 0.3 }}>⚡</Text>
            <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无秒杀活动</Text>
            <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>
              下拉刷新查看最新活动
            </Text>
          </View>
        ) : (
          activities.map((activity) => (
            <View key={activity.id} style={{ marginBottom: Spacing.lg }}>
              {/* Activity header */}
              <View
                style={{
                  marginHorizontal: Spacing.lg,
                  backgroundColor: theme.bgContainer,
                  borderRadius: BorderRadius.lg,
                  padding: Spacing.md,
                  marginBottom: Spacing.sm,
                  borderLeftWidth: 4,
                  borderLeftColor: theme.accentRed,
                }}
              >
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                  <View style={{ flex: 1 }}>
                    <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text }}>
                      {activity.name}
                    </Text>
                    <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>
                      {formatTimeRange(activity.startTime, activity.endTime)}
                    </Text>
                  </View>
                  {countdowns[activity.id] && (
                    <View
                      style={{
                        paddingHorizontal: Spacing.md,
                        paddingVertical: Spacing.xs,
                        backgroundColor: `${theme.accentRed}20`,
                        borderRadius: BorderRadius.xl,
                      }}
                    >
                      <Text
                        style={{
                          fontSize: FontSize.sm,
                          fontWeight: '700',
                          color: theme.accentRed,
                          fontVariant: ['tabular-nums'],
                        }}
                      >
                        {countdowns[activity.id]}
                      </Text>
                    </View>
                  )}
                </View>
              </View>

              {/* Product list */}
              <View style={{ paddingHorizontal: Spacing.lg }}>
                {activity.products && activity.products.length > 0 ? (
                  activity.products.map((product) => (
                    <SeckillProductCard
                      key={product.id}
                      product={product}
                      activityId={activity.id}
                      theme={theme}
                      onBuy={handleBuy}
                      buying={buyingProductId === product.id}
                    />
                  ))
                ) : (
                  <View
                    style={{
                      alignItems: 'center',
                      paddingVertical: Spacing.xxl,
                      backgroundColor: theme.bgContainer,
                      borderRadius: BorderRadius.lg,
                    }}
                  >
                    <Text style={{ fontSize: FontSize.md, color: theme.textTertiary }}>
                      该场次暂无商品
                    </Text>
                  </View>
                )}
              </View>
            </View>
          ))
        )}
      </ScrollView>
    </View>
  )
}
