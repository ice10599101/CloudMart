import { useState, useEffect, useCallback, useRef } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro, { useDidShow, usePullDownRefresh } from '@tarojs/taro'
import { marketingApi } from '@/api/marketing'
import { useThemeClass } from '@/composables/useThemeClass'
import type { GroupActivity } from '@/types'
import styles from './index.module.scss'

interface Countdown {
  hours: string
  minutes: string
  seconds: string
}

function calcCountdown(endTime: string): Countdown {
  const diff = new Date(endTime).getTime() - Date.now()
  if (diff <= 0) return { hours: '00', minutes: '00', seconds: '00' }
  const hours = String(Math.floor(diff / 3600000)).padStart(2, '0')
  const minutes = String(Math.floor((diff % 3600000) / 60000)).padStart(2, '0')
  const seconds = String(Math.floor((diff % 60000) / 1000)).padStart(2, '0')
  return { hours, minutes, seconds }
}

export default function GroupBuyPage() {
  const { dataTheme, themeStyle } = useThemeClass()

  const [activities, setActivities] = useState<GroupActivity[]>([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [countdowns, setCountdowns] = useState<Record<number, Countdown>>({})
  const [joiningId, setJoiningId] = useState<number | null>(null)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useDidShow(() => {
    loadActivities(1, true)
  })

  useEffect(() => {
    timerRef.current = setInterval(() => {
      setCountdowns((prev) => {
        const next: Record<number, Countdown> = {}
        let changed = false
        for (const act of activities) {
          const cd = calcCountdown(act.endTime)
          next[act.id] = cd
          if (!prev[act.id] || prev[act.id].seconds !== cd.seconds) changed = true
        }
        return changed ? next : prev
      })
    }, 1000)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [activities])

  usePullDownRefresh(() => {
    loadActivities(1, true).finally(() => Taro.stopPullDownRefresh())
  })

  const loadActivities = useCallback(async (pageNum: number, reset = false) => {
    if (loading) return
    setLoading(true)
    try {
      const res = await marketingApi.getGroupActivities({ page: pageNum, pageSize: 20 })
      const list: GroupActivity[] = res.data?.data?.list || []
      setActivities((prev) => (reset ? list : [...prev, ...list]))
      setHasMore(list.length >= 20)
      setPage(pageNum)
      // Initialize countdowns
      setCountdowns((prev) => {
        const next = { ...prev }
        for (const act of list) {
          next[act.id] = calcCountdown(act.endTime)
        }
        return next
      })
    } catch {
      if (reset) setActivities([])
    } finally {
      setLoading(false)
    }
  }, [loading])

  const handleLoadMore = () => {
    if (hasMore && !loading) loadActivities(page + 1)
  }

  const handleJoin = async (activity: GroupActivity) => {
    if (joiningId === activity.id) return
    const { confirm } = await Taro.showModal({
      title: '参与拼团',
      content: `确认参与「${activity.productName}」${activity.groupSize}人团？拼团价 ¥${activity.groupPrice}`,
    })
    if (!confirm) return

    setJoiningId(activity.id)
    try {
      const res = await marketingApi.joinGroup({ activityId: activity.id })
      if (res.data?.success) {
        Taro.showToast({ title: '参与成功', icon: 'success' })
        const orderNo = res.data?.data?.orderNo
        if (orderNo) {
          Taro.navigateTo({ url: `/pages/orderDetail/index?orderNo=${orderNo}` })
        } else {
          Taro.navigateTo({ url: `/pages/productDetail/index?id=${activity.productId}` })
        }
      } else {
        Taro.showToast({ title: res.data?.error?.message || '参与失败', icon: 'none' })
      }
    } catch {
      Taro.showToast({ title: '网络异常，请重试', icon: 'none' })
    } finally {
      setJoiningId(null)
    }
  }

  const formatPrice = (price: number) => price.toFixed(2)

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      {/* Header */}
      <View className={styles.header}>
        <Text className={styles.headerTitle}>👥 拼团专区</Text>
        <Text className={styles.headerSub}>超值拼团 限时开抢</Text>
      </View>

      {/* Activity List */}
      <ScrollView
        scrollY
        className={styles.scrollView}
        onScrollToLower={handleLoadMore}
      >
        {activities.length > 0 ? (
          <View className={styles.list}>
            {activities.map((activity) => {
              const cd = countdowns[activity.id] || { hours: '--', minutes: '--', seconds: '--' }
              const progress = Math.min((activity.currentCount / activity.groupSize) * 100, 100)
              const isEnded = activity.status === 2 || cd.hours === '00' && cd.minutes === '00' && cd.seconds === '00'

              return (
                <View key={activity.id} className={styles.card}>
                  {/* Product Image */}
                  <View className={styles.cardImageWrap}>
                    <Image
                      className={styles.cardImage}
                      src={activity.productImage}
                      mode="aspectFill"
                    />
                    {isEnded && <View className={styles.endedOverlay}><Text className={styles.endedText}>已结束</Text></View>}
                  </View>

                  {/* Product Info */}
                  <View className={styles.cardBody}>
                    <Text className={styles.productName}>{activity.productName}</Text>

                    {/* Price Row */}
                    <View className={styles.priceRow}>
                      <Text className={styles.groupPrice}>¥{formatPrice(activity.groupPrice)}</Text>
                      <Text className={styles.originalPrice}>¥{formatPrice(activity.originalPrice)}</Text>
                    </View>

                    {/* Group Info */}
                    <View className={styles.groupInfo}>
                      <Text className={styles.groupSizeTag}>{activity.groupSize}人团</Text>
                      <Text className={styles.participantCount}>已有{activity.currentCount}人参与</Text>
                    </View>

                    {/* Progress Bar */}
                    <View className={styles.progressBar}>
                      <View className={styles.progressFill} style={{ width: `${progress}%` }} />
                    </View>

                    {/* Bottom Row: Countdown + Button */}
                    <View className={styles.cardFooter}>
                      <View className={styles.countdownWrap}>
                        <Text className={styles.countdownLabel}>距结束</Text>
                        <View className={styles.countdownBlocks}>
                          <Text className={styles.countdownBlock}>{cd.hours}</Text>
                          <Text className={styles.countdownSep}>:</Text>
                          <Text className={styles.countdownBlock}>{cd.minutes}</Text>
                          <Text className={styles.countdownSep}>:</Text>
                          <Text className={styles.countdownBlock}>{cd.seconds}</Text>
                        </View>
                      </View>
                      <View
                        className={`${styles.joinBtn} ${isEnded ? styles.joinBtnDisabled : ''} ${joiningId === activity.id ? styles.joinBtnLoading : ''}`}
                        onClick={() => !isEnded && handleJoin(activity)}
                      >
                        <Text className={styles.joinBtnText}>
                          {joiningId === activity.id ? '参与中...' : isEnded ? '已结束' : '参与拼团'}
                        </Text>
                      </View>
                    </View>
                  </View>
                </View>
              )
            })}
          </View>
        ) : (
          !loading && (
            <View className={styles.empty}>
              <Text className={styles.emptyIcon}>👥</Text>
              <Text className={styles.emptyTitle}>暂无拼团活动</Text>
              <Text className={styles.emptyDesc}>精彩拼团即将上线，敬请期待</Text>
            </View>
          )
        )}

        {/* Loading */}
        {loading && (
          <View className={styles.loadingWrap}>
            <View className={styles.loadingDot} />
            <Text className={styles.loadingText}>加载中</Text>
          </View>
        )}

        {/* No More */}
        {!hasMore && activities.length > 0 && (
          <View className={styles.footer}>
            <View className={styles.footerLine} />
            <Text className={styles.footerText}>到底啦</Text>
            <View className={styles.footerLine} />
          </View>
        )}
      </ScrollView>
    </View>
  )
}
