import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import type { MyResourcesData } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import StarCountUp from '@/components/StarCountUp'
import type { MyWishListItem, WishStatus, FruitType } from '@/types'
import styles from './index.module.scss'

const PAGE_SIZE = 20

const STATUS_FILTERS: { label: string; value: '' | WishStatus }[] = [
  { label: '全部', value: '' },
  { label: '进行中', value: 'ACTIVE' },
  { label: '还愿中', value: 'FULFILLING' },
  { label: '已还愿', value: 'FULFILLED' },
]

const FRUIT_LABELS: Record<FruitType, string> = {
  GLOW: '微光',
  RESONANCE: '共鸣',
  BLOOM: '绽放',
  SPARK: '星火',
}

const FRUIT_COLORS: Record<FruitType, string> = {
  GLOW: '#00d4ff',
  RESONANCE: '#9370db',
  BLOOM: '#ff6b6b',
  SPARK: '#ffd700',
}

export default function MyWishesPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [wishes, setWishes] = useState<MyWishListItem[]>([])
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [resources, setResources] = useState<MyResourcesData | null>(null)

  const fetchWishes = useCallback(async (reset: boolean) => {
    if (!isLoggedIn) {
      setLoading(false)
      return
    }
    if (reset) {
      setLoading(true)
      setCursor(null)
    } else {
      setLoadingMore(true)
    }

    try {
      const res = await wishApi.listMyWishes({
        status: (statusFilter || undefined) as WishStatus | undefined,
        cursor: reset ? undefined : cursor ?? undefined,
        pageSize: PAGE_SIZE,
      })

      if (res.data.success) {
        const newItems = res.data.data
        setWishes(prev => reset ? newItems : [...prev, ...newItems])
        const meta = res.data.meta
        setCursor(meta?.nextCursor ?? null)
        setHasMore(meta?.hasMore ?? false)
      }
    } catch {
      // 错误已由 request 处理
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [isLoggedIn, statusFilter, cursor])

  useEffect(() => {
    if (!isLoggedIn) {
      Taro.redirectTo({ url: '/pages/login/index' })
      return
    }
    fetchWishes(true)
  }, [isLoggedIn, statusFilter])

  // 星光余额概览（文档 L1915：移动三端星光余额展示）
  useEffect(() => {
    if (!isLoggedIn) return
    wishApi.getMyResources()
      .then(res => {
        if (res.data.success) setResources(res.data.data)
      })
      .catch(() => {
        // 余额卡片加载失败不阻塞心愿列表
      })
  }, [isLoggedIn])

  const onScrollToLower = () => {
    if (hasMore && !loading && !loadingMore) {
      fetchWishes(false)
    }
  }

  // 注销状态（A1）：PENDING 时显示撤回入口
  const [deletionPending, setDeletionPending] = useState(false)

  useEffect(() => {
    if (!isLoggedIn) return
    wishApi.getAccountDeletionStatus()
      .then((res) => {
        if (res.data.success && res.data.data) setDeletionPending(res.data.data.status === 'PENDING')
      })
      .catch(() => undefined)
  }, [isLoggedIn])

  /** 撤回注销申请 */
  const handleCancelDeletion = async () => {
    const res = await Taro.showModal({
      title: '撤回注销申请',
      content: '撤回后账号恢复正常，确定撤回吗？',
      confirmText: '撤回',
    })
    if (!res.confirm) return
    try {
      const r = await wishApi.cancelAccountDeletion()
      if (r.data.success) {
        setDeletionPending(false)
        Taro.showToast({ title: '已撤回，账号恢复正常', icon: 'none' })
      }
    } catch (err) {
      const errNode = err as { data?: { error?: { message?: string } } }
      Taro.showToast({ title: errNode?.data?.error?.message || '撤回失败', icon: 'none' })
    }
  }

  /** 账号注销（A1 合规）：发送验证码 → editable 弹窗输入 → 申请（30 天宽限期） */
  const handleDeletion = async () => {
    try {
      await wishApi.sendDeletionCode()
    } catch {
      Taro.showToast({ title: '验证码发送失败', icon: 'none' })
      return
    }
    // editable/placeholderText 微信端运行时支持，Taro 类型滞后故断言
    const input = await Taro.showModal({
      title: '申请注销账号',
      content: '验证码已发送（30 天宽限期，期间可撤回）。请输入 6 位验证码：',
      editable: true,
      placeholderText: '6 位验证码',
      confirmText: '下一步',
    } as Taro.showModal.Option & { editable: boolean; placeholderText: string })
    if (!input.confirm) return
    const code = ((input as { content?: string }).content || '').trim()
    const applyRes = await Taro.showModal({
      title: '确认申请注销？',
      content: '提交后进入 30 天宽限期，到期将清除心愿等个人数据。是否提交？',
      cancelText: '取消申请',
      confirmText: '提交申请',
    })
    if (!applyRes.confirm) return
    try {
      const res = await wishApi.applyAccountDeletion(code, undefined)
      if (res.data.success) {
        Taro.showToast({ title: '注销申请已提交，30 天内可撤回', icon: 'none' })
      }
    } catch (err) {
      const errNode = err as { data?: { error?: { message?: string } } }
      Taro.showToast({ title: errNode?.data?.error?.message || '申请失败', icon: 'none' })
    }
  }

  const handleDelete = async (id: number) => {
    const res = await Taro.showModal({
      title: '确认删除',
      content: '删除后不可恢复，确定删除吗？',
    })
    if (!res.confirm) return

    try {
      const result = await wishApi.deleteWish(id)
      if (result.data.success) {
        Taro.showToast({ title: '已删除', icon: 'success' })
        setWishes(prev => prev.filter(w => w.id !== id))
      }
    } catch {
      // 错误已由 request 处理
    }
  }

  if (loading) {
    return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}px`, minHeight: '100vh' }}>
        <CustomNavBar title='我的心愿' back />
        <View className={styles.loading}>
          <View className={styles.spinner} />
        </View>
      </View>
    )
  }

  return (
    <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}px`, minHeight: '100vh' }}>
      <CustomNavBar title='我的心愿' back />

      {/* 星光余额卡片（文档 L1915：移动三端星光余额展示 + 数字滚动动效） */}
      {resources && (
        <View className={styles.starlightCard}>
          <View className={styles.starlightLeft}>
            <Text className={styles.starlightIcon}>⭐</Text>
            <StarCountUp value={resources.balance} className={styles.starlightValue} />
            <Text className={styles.starlightLabel}>星光余额</Text>
          </View>
          <View className={styles.starlightToday}>
            <Text className={styles.starlightEarn}>今日 +{resources.todayEarned}</Text>
            <Text className={styles.starlightSpend}>支出 -{resources.todaySpent}</Text>
          </View>
        </View>
      )}

      {/* 状态筛选 */}
      <ScrollView scrollX className={styles.filterBar}>
        {STATUS_FILTERS.map(filter => (
          <View
            key={filter.value}
            className={`${styles.filterChip} ${statusFilter === filter.value ? styles.filterChipActive : ''}`}
            onClick={() => setStatusFilter(filter.value)}
          >
            <Text>{filter.label}</Text>
          </View>
        ))}
      </ScrollView>

      {/* 快捷入口（对齐 WEB 端：每日签到/我的收藏/虚拟工坊/我的徽章） */}
      <View className={styles.quickNav}>
        <View
          className={styles.quickNavBtn}
          onClick={() => Taro.navigateTo({ url: '/pages/dailySignin/index' })}
        >
          <Text className={styles.quickNavIcon}>📅</Text>
          <Text className={styles.quickNavText}>每日签到</Text>
        </View>
        <View
          className={styles.quickNavBtn}
          onClick={() => Taro.navigateTo({ url: '/pages/wishCollections/index' })}
        >
          <Text className={styles.quickNavIcon}>📖</Text>
          <Text className={styles.quickNavText}>我的收藏</Text>
        </View>
        <View
          className={styles.quickNavBtn}
          onClick={() => Taro.navigateTo({ url: '/pages/workshop/index' })}
        >
          <Text className={styles.quickNavIcon}>🎁</Text>
          <Text className={styles.quickNavText}>虚拟工坊</Text>
        </View>
        <View
          className={styles.quickNavBtn}
          onClick={() => Taro.navigateTo({ url: '/pages/badgeWall/index' })}
        >
          <Text className={styles.quickNavIcon}>🏆</Text>
          <Text className={styles.quickNavText}>我的徽章</Text>
        </View>
      </View>

      {/* 新建按钮 */}
      <View className={styles.toolbar}>
        <View
          className={styles.createBtn}
          onClick={() => Taro.navigateTo({ url: '/pages/wishCreate/index' })}
        >
          <Text className={styles.createBtnText}>+ 新建心愿</Text>
        </View>
      </View>

      {wishes.length === 0 ? (
        <View className={styles.empty}>
          <Text className={styles.emptyIcon}>🌟</Text>
          <Text className={styles.emptyText}>还没有心愿，点击上方「新建心愿」许下第一个吧</Text>
        </View>
      ) : (
        <ScrollView
          scrollY
          className={styles.scroll}
          onScrollToLower={onScrollToLower}
          lowerThreshold={100}
        >
          {wishes.map(wish => (
            <View
              key={wish.id}
              className={styles.wishCard}
              onClick={() => Taro.navigateTo({ url: `/pages/wishDetail/index?id=${wish.id}` })}
            >
              <View className={styles.cardLeft}>
                <Text className={styles.fruitTag} style={{ background: FRUIT_COLORS[wish.fruitType] }}>
                  {FRUIT_LABELS[wish.fruitType]}
                </Text>
                <View className={styles.cardInfo}>
                  <Text className={styles.cardTitle}>{wish.title}</Text>
                  <Text className={styles.cardDate}>
                    {new Date(wish.createdAt).toLocaleDateString('zh-CN')}
                  </Text>
                </View>
              </View>
              <View className={styles.cardRight}>
                <View className={styles.progressWrap}>
                  <View className={styles.progressBg}>
                    <View
                      className={styles.progressBar}
                      style={{ width: `${wish.progress}%`, background: FRUIT_COLORS[wish.fruitType] }}
                    />
                  </View>
                  <Text className={styles.progressText}>{wish.progress}%</Text>
                </View>
                <View
                  className={styles.deleteBtn}
                  onClick={(e) => { e.stopPropagation(); handleDelete(wish.id) }}
                >
                  <Text className={styles.deleteText}>删除</Text>
                </View>
              </View>
            </View>
          ))}

          {loadingMore && (
            <View className={styles.loadingMore}>
              <View className={styles.spinnerSmall} />
              <Text className={styles.loadingText}>加载中...</Text>
            </View>
          )}

          {!hasMore && wishes.length > 0 && (
            <View className={styles.endText}>
              <Text>已经到底啦~</Text>
            </View>
          )}
          <View style={{ padding: '24rpx', textAlign: 'center' }}>
            <Text
              style={{ fontSize: 24, color: '#4a90d9', marginRight: 24 }}
              onClick={() => Taro.navigateTo({ url: '/pages/dataExport/index' })}
            >
              数据导出
            </Text>
            <Text
              style={{ fontSize: 24, color: deletionPending ? '#ffa940' : '#ff6b6b' }}
              onClick={() => {
                if (deletionPending) {
                  void handleCancelDeletion()
                  return
                }
                Taro.showModal({
                  title: '注销账号',
                  content: '申请后进入 30 天宽限期（期间可撤回），到期将清除心愿等个人数据。继续吗？',
                  success: (r) => { if (r.confirm) void handleDeletion() },
                })
              }}
            >
              {deletionPending ? '⚠ 注销宽限期中（点击撤回）' : '注销账号'}
            </Text>
          </View>
          <View style={{ height: '120rpx' }} />
        </ScrollView>
      )}
      <WishBGM />
    </View>
  )
}
