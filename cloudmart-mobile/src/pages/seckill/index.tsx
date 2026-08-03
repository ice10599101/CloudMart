import { useState, useEffect, useRef, useCallback } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { marketingApi } from '@/api/marketing'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

interface SeckillProduct {
  id: number
  productId: number
  productName: string
  productImage: string
  originalPrice: number
  seckillPrice: number
  totalStock: number
  availableStock: number
}

interface SeckillActivity {
  id: number
  name: string
  startTime: string
  endTime: string
  status: number
  products: SeckillProduct[]
}

interface CountdownResult {
  text: string
  isExpired: boolean
}

function computeCountdown(endTime: string): CountdownResult {
  const diff = new Date(endTime).getTime() - Date.now()
  if (diff <= 0) return { text: '已结束', isExpired: true }

  const hours = Math.floor(diff / 3_600_000)
  const minutes = Math.floor((diff % 3_600_000) / 60_000)
  const seconds = Math.floor((diff % 60_000) / 1_000)
  const pad = (n: number) => String(n).padStart(2, '0')

  return { text: `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`, isExpired: false }
}

export default function SeckillPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()

  const [activities, setActivities] = useState<SeckillActivity[]>([])
  const [loading, setLoading] = useState(false)
  const [countdowns, setCountdowns] = useState<Record<number, CountdownResult>>({})
  const [executingIds, setExecutingIds] = useState<Set<string>>(new Set())
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const loadActivities = useCallback(async () => {
    setLoading(true)
    try {
      const res = await marketingApi.getSeckillActivities({ page: 1, pageSize: 50 })
      const list: SeckillActivity[] = res.data?.data?.list || res.data?.data || []
      setActivities(list)
      updateCountdowns(list)
    } catch {
      setActivities([])
    } finally {
      setLoading(false)
    }
  }, [])

  const updateCountdowns = useCallback((list: SeckillActivity[]) => {
    const next: Record<number, CountdownResult> = {}
    list.forEach((act) => {
      next[act.id] = computeCountdown(act.endTime)
    })
    setCountdowns(next)
  }, [])

  useDidShow(() => {
    loadActivities()
  })

  useEffect(() => {
    timerRef.current = setInterval(() => {
      updateCountdowns(activities)
    }, 1_000)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [activities, updateCountdowns])

  const handleRefresh = async () => {
    await loadActivities()
    Taro.showToast({ title: '刷新成功', icon: 'success', duration: 1_000 })
  }

  const handleSeckill = async (activityId: number, product: SeckillProduct) => {
    const key = `${activityId}-${product.id}`
    if (executingIds.has(key)) return

    const { confirm } = await Taro.showModal({
      title: '确认秒杀',
      content: `确定要以 ¥${product.seckillPrice} 秒杀「${product.productName}」吗？`,
    })
    if (!confirm) return

    setExecutingIds((prev) => new Set(prev).add(key))
    try {
      const res = await marketingApi.executeSeckill({ activityId, seckillProductId: product.id })
      const orderNo = res.data?.data?.orderNo || res.data?.data
      Taro.showToast({ title: '秒杀成功', icon: 'success' })
      if (orderNo) {
        setTimeout(() => {
          Taro.navigateTo({ url: `/pages/orderDetail/index?orderNo=${orderNo}` })
        }, 1_500)
      } else {
        setTimeout(() => {
          Taro.navigateTo({ url: '/pages/orders/index' })
        }, 1_500)
      }
    } catch {
      Taro.showToast({ title: '秒杀失败，请重试', icon: 'none' })
    } finally {
      setExecutingIds((prev) => {
        const next = new Set(prev)
        next.delete(key)
        return next
      })
    }
  }

  const formatTimeRange = (start: string, end: string) => {
    const fmt = (iso: string) => {
      const d = new Date(iso)
      return `${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    }
    return `${fmt(start)} - ${fmt(end)}`
  }

  const renderProductCard = (activityId: number, product: SeckillProduct) => {
    const soldCount = product.totalStock - product.availableStock
    const soldPercent = product.totalStock > 0 ? Math.min((soldCount / product.totalStock) * 100, 100) : 100
    const isSoldOut = product.availableStock <= 0
    const key = `${activityId}-${product.id}`
    const isExecuting = executingIds.has(key)

    return (
      <View key={product.id} className={styles.productCard}>
        <Image className={styles.productImage} src={product.productImage} mode='aspectFill' />
        <View className={styles.productInfo}>
          <Text className={styles.productName}>{product.productName}</Text>
          <View className={styles.priceRow}>
            <Text className={styles.seckillPrice}>¥{product.seckillPrice}</Text>
            <Text className={styles.originalPrice}>¥{product.originalPrice}</Text>
          </View>
          <View className={styles.progressRow}>
            <View className={styles.progressBar}>
              <View className={styles.progressFill} style={{ width: `${soldPercent}%` }} />
            </View>
            <Text className={styles.progressText}>已抢{Math.round(soldPercent)}%</Text>
          </View>
          <View
            className={`${styles.grabBtn} ${isSoldOut ? styles.soldOut : ''} ${isExecuting ? styles.executing : ''}`}
            onClick={() => !isSoldOut && !isExecuting && handleSeckill(activityId, product)}
          >
            <Text className={styles.grabBtnText}>
              {isExecuting ? '抢购中...' : isSoldOut ? '已售罄' : '马上抢'}
            </Text>
          </View>
        </View>
      </View>
    )
  }

  const renderActivity = (activity: SeckillActivity) => {
    const countdown = countdowns[activity.id]
    const isExpired = countdown?.isExpired ?? true

    return (
      <View key={activity.id} className={styles.activitySection}>
        <View className={styles.activityHeader}>
          <View className={styles.activityTitleRow}>
            <Text className={styles.activityName}>{activity.name}</Text>
            {isExpired ? (
              <View className={styles.expiredTag}>
                <Text className={styles.expiredTagText}>已结束</Text>
              </View>
            ) : (
              <View className={styles.countdownTag}>
                <Text className={styles.countdownIcon}>⏱</Text>
                <Text className={styles.countdownText}>{countdown?.text || ''}</Text>
              </View>
            )}
          </View>
          <Text className={styles.timeRange}>{formatTimeRange(activity.startTime, activity.endTime)}</Text>
        </View>
        <View className={styles.productList}>
          {activity.products?.length > 0
            ? activity.products.map((p) => renderProductCard(activity.id, p))
            : (
              <View className={styles.noProducts}>
                <Text className={styles.noProductsText}>暂无秒杀商品</Text>
              </View>
            )}
        </View>
      </View>
    )
  }

  const renderEmpty = () => (
    <View className={styles.empty}>
      <Text className={styles.emptyIcon}>⚡</Text>
      <Text className={styles.emptyText}>暂无秒杀活动</Text>
      <Text className={styles.emptySubText}>精彩秒杀即将上线，敬请期待</Text>
    </View>
  )

  const renderLoading = () => (
    <View className={styles.loading}>
      <View className={styles.spinner} />
      <Text className={styles.loadingText}>加载中...</Text>
    </View>
  )

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <View className={styles.header}>
        <View className={styles.headerContent}>
          <Text className={styles.title}>⚡ 限时秒杀</Text>
          <Text className={styles.subtitle}>手快有，手慢无</Text>
        </View>
      </View>

      <ScrollView
        scrollY
        className={styles.scrollView}
        refresherEnabled
        refresherTriggered={loading}
        onRefresherRefresh={handleRefresh}
      >
        {loading && activities.length === 0
          ? renderLoading()
          : activities.length > 0
            ? activities.map(renderActivity)
            : renderEmpty()}
      </ScrollView>
    </View>
  )
}
