import { useState, useEffect, useCallback } from 'react'
import { PET_CREAM_STYLE } from '@/styles/petCream'
import { View, Text, ScrollView } from '@tarojs/components'
import { petApi } from '@/api/pet'
import type { PetPurchaseOrder, PetWalletTransactionVO, PetWalletVO } from '@/api/pet'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

const PAGE_SIZE = 20

const BIZ_TYPE_LABELS: Record<string, string> = {
  PURCHASE: '购买',
  ACTIVITY_REWARD: '活动奖励',
  CLAIM_WORK: '打工工资',
  CLAIM_STUDY: '读书奖励',
  BATTLE_REWARD: '对战奖励',
  BOTTLE_REWARD: '捞瓶奖励',
  QUEST_CLAIM: '任务奖励',
  QUEST_CHEST: '宝箱奖励',
  CAREER_CLAIM: '职业工资',
  CAREER_PROMOTE: '晋升',
  EVENT_CLAIM: '活动奖励',
  EVENT_ALT: '活动替代奖励',
  COOP_REWARD_ALT: '合作奖励',
  ONBOARDING_GIFT: '新手礼物',
  EVOLVE: '进化',
  FURNITURE_BUY: '家具购买',
  REFUND: '退款',
  ADJUSTMENT: '调账',
}

function bizLabel(bizType: string): string {
  return BIZ_TYPE_LABELS[bizType] ?? bizType
}

/**
 * 宠物币钱包（W03/§7.8）：余额、收支明细、方向筛选、游标加载更多。
 * 查看流水无副作用；余额不可用显示"暂不可用"而非 0；冻结提示仍可读账与退款。
 */
export default function PetWalletPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [wallet, setWallet] = useState<PetWalletVO | null>(null)
  const [walletUnavailable, setWalletUnavailable] = useState(false)
  const [transactions, setTransactions] = useState<PetWalletTransactionVO[]>([])
  const [filter, setFilter] = useState<'' | 'EARN' | 'SPEND' | 'REFUND'>('')
  const [cursor, setCursor] = useState<string | number | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [listError, setListError] = useState(false)
  // P1-16：购买订单 Tab（R02 统一购买流水，keyset 翻页）
  const [mainTab, setMainTab] = useState<'tx' | 'orders'>('tx')
  const [orders, setOrders] = useState<PetPurchaseOrder[]>([])
  const [orderCursor, setOrderCursor] = useState<string | null>(null)
  const [orderHasMore, setOrderHasMore] = useState(false)
  const [ordersLoading, setOrdersLoading] = useState(false)
  const [ordersLoadingMore, setOrdersLoadingMore] = useState(false)
  const [ordersError, setOrdersError] = useState(false)

  const loadWallet = useCallback(async () => {
    setWalletUnavailable(false)
    try {
      const res = await petApi.getWallet()
      if (res.data.success && res.data.data) {
        setWallet(res.data.data)
      } else {
        setWalletUnavailable(true)
      }
    } catch {
      setWalletUnavailable(true)
    }
  }, [])

  const loadTransactions = useCallback(
    async (reset: boolean) => {
      if (reset) {
        setLoading(true)
        setCursor(null)
      } else {
        setLoadingMore(true)
      }
      setListError(false)
      try {
        const res = await petApi.listWalletTransactions({
          size: PAGE_SIZE,
          direction: filter || undefined,
          cursor: reset ? undefined : (cursor ?? undefined),
        })
        if (res.data.success && res.data.data) {
          const page = res.data.data
          setTransactions((prev) => (reset ? page : [...prev, ...page]))
          const meta = (res.data as { meta?: { nextCursor?: string | number | null } }).meta
          setCursor(meta?.nextCursor ?? null)
          setHasMore(Boolean(meta?.nextCursor))
        } else {
          setListError(true)
        }
      } catch {
        setListError(true)
      } finally {
        setLoading(false)
        setLoadingMore(false)
      }
    },
    [filter, cursor],
  )

  const loadOrders = useCallback(async (reset: boolean) => {
    if (reset) {
      setOrdersLoading(true)
      setOrderCursor(null)
    } else {
      setOrdersLoadingMore(true)
    }
    setOrdersError(false)
    try {
      const res = await petApi.listPurchaseOrders({
        size: PAGE_SIZE,
        cursor: reset ? undefined : (orderCursor ?? undefined),
      })
      if (res.data.success && res.data.data) {
        const page = res.data.data
        setOrders((prev) => (reset ? page.items : [...prev, ...page.items]))
        setOrderCursor(page.nextCursor)
        setOrderHasMore(page.hasMore)
      } else {
        setOrdersError(true)
      }
    } catch {
      setOrdersError(true)
    } finally {
      setOrdersLoading(false)
      setOrdersLoadingMore(false)
    }
  }, [orderCursor])

  useEffect(() => {
    void loadWallet()
  }, [loadWallet])

  useEffect(() => {
    if (mainTab === 'orders' && orders.length === 0 && !ordersError) {
      void loadOrders(true)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mainTab])

  useEffect(() => {
    void loadTransactions(true)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter])

  const handleLoadMore = async () => {
    if (!hasMore || loadingMore) return
    await loadTransactions(false)
  }

  const balanceText = walletUnavailable
    ? '暂不可用'
    : wallet
      ? String(wallet.balance)
      : '加载中...'

  return (
    <View className={styles.page} style={{ ...PET_CREAM_STYLE, paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title="宠物币钱包" back />
      <View className={styles.balanceCard}>
        <Text className={styles.balanceLabel}>宠物币余额（{wallet?.currency ?? 'PET_COIN'}）</Text>
        <Text className={styles.balanceValue}>{balanceText}</Text>
        {wallet?.status === 'FROZEN' && (
          <Text className={styles.balanceHint}>已冻结：暂不能消费，仍可查看与退款，请联系客服</Text>
        )}
        <Text className={styles.balanceHint}>
          宠物币是宠物模块独立货币；社区活动请到对应页面查看
        </Text>
      </View>
      {/* P1-16：明细/订单双 Tab */}
      <View className={styles.mainTabs}>
        <Text
          className={`${styles.mainTab} ${mainTab === 'tx' ? styles.mainTabActive : ''}`}
          onClick={() => setMainTab('tx')}
        >
          收支明细
        </Text>
        <Text
          className={`${styles.mainTab} ${mainTab === 'orders' ? styles.mainTabActive : ''}`}
          onClick={() => setMainTab('orders')}
        >
          购买订单
        </Text>
      </View>
      {mainTab === 'tx' ? (
      <ScrollView className={styles.list} scrollY onScrollToLower={handleLoadMore}>
        {!isLoggedIn ? (
          <View className={styles.empty}><Text>请先登录</Text></View>
        ) : listError ? (
          <View className={styles.empty} onClick={() => loadTransactions(true)}>
            <Text>明细加载失败，点击重试</Text>
          </View>
        ) : loading ? (
          <View className={styles.empty}><Text>加载中...</Text></View>
        ) : transactions.length === 0 ? (
          <View className={styles.empty}><Text>还没有收支记录</Text></View>
        ) : (
          transactions.map((tx) => {
            const spend = tx.direction === 'SPEND'
            return (
              <View key={String(tx.transactionId)} className={styles.logCard}>
                <View className={styles.logLeft}>
                  <Text className={styles.logSource}>{bizLabel(tx.bizType)}</Text>
                  <Text className={styles.logTime}>
                    {new Date(tx.occurredAt).toLocaleString('zh-CN')}
                  </Text>
                </View>
                <Text
                  className={styles.logDelta}
                  style={{ color: spend ? 'var(--pet-danger, #D98A8A)' : 'var(--color-success, #9CAF88)' }}
                >
                  {spend ? '-' : '+'}{tx.amount}
                </Text>
              </View>
            )
          })
        )}
        {loadingMore && <View className={styles.empty}><Text>加载更多...</Text></View>}
        {hasMore && !loadingMore && (
          <View className={styles.empty} onClick={handleLoadMore}><Text>加载更多</Text></View>
        )}
        {!hasMore && transactions.length > 0 && (
          <View className={styles.empty}><Text>没有更多了</Text></View>
        )}
      </ScrollView>
      ) : (
      <ScrollView className={styles.list} scrollY onScrollToLower={() => orderHasMore && !ordersLoadingMore && loadOrders(false)}>
        {!isLoggedIn ? (
          <View className={styles.empty}><Text>请先登录</Text></View>
        ) : ordersError ? (
          <View className={styles.empty} onClick={() => loadOrders(true)}>
            <Text>订单加载失败，点击重试</Text>
          </View>
        ) : ordersLoading ? (
          <View className={styles.empty}><Text>加载中...</Text></View>
        ) : orders.length === 0 ? (
          <View className={styles.empty}><Text>还没有购买记录</Text></View>
        ) : (
          orders.map((order) => (
            <View key={order.orderId} className={styles.logCard}>
              <View className={styles.logLeft}>
                <Text className={styles.logSource}>
                  {order.itemType === 'PET' ? '宠物' : order.itemType === 'FOOD' ? '食物' : order.itemType === 'ITEM' ? '道具' : order.itemType}
                  {' '}× {order.quantity}
                </Text>
                <Text className={styles.logTime}>
                  {order.completedAt ? new Date(order.completedAt).toLocaleString('zh-CN') : '处理中'}
                  {' · '}{order.orderId.slice(-8)}
                </Text>
              </View>
              <View className={styles.logRightCol}>
                <Text className={styles.logDelta} style={{ color: 'var(--pet-danger, #D98A8A)' }}>
                  -{order.totalAmount}
                </Text>
                <Text className={styles.orderStatus}>{order.status}</Text>
              </View>
            </View>
          ))
        )}
        {ordersLoadingMore && <View className={styles.empty}><Text>加载更多...</Text></View>}
        {orderHasMore && !ordersLoadingMore && (
          <View className={styles.empty} onClick={() => loadOrders(false)}><Text>加载更多</Text></View>
        )}
        {!orderHasMore && orders.length > 0 && (
          <View className={styles.empty}><Text>没有更多了</Text></View>
        )}
      </ScrollView>
      )}
      {mainTab === 'tx' && (
      <View className={styles.filterBar}>
        {([['', '全部'], ['EARN', '收入'], ['SPEND', '支出'], ['REFUND', '退款']] as const).map(
          ([value, label]) => (
            <Text
              key={value}
              className={`${styles.filterItem} ${filter === value ? styles.filterActive : ''}`}
              onClick={() => {
                setFilter(value as '' | 'EARN' | 'SPEND' | 'REFUND')
                loadTransactions(true)
              }}
            >
              {label}
            </Text>
          ),
        )}
      </View>
      )}
    </View>
  )
}
