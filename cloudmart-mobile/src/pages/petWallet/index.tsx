import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import { petApi } from '@/api/pet'
import type { PetWalletTransactionVO, PetWalletVO } from '@/api/pet'
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

  useEffect(() => {
    void loadWallet()
  }, [loadWallet])

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
    <View className={styles.page} style={{ paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title="宠物币钱包" back />
      <View className={styles.balanceCard}>
        <Text className={styles.balanceLabel}>宠物币余额（{wallet?.currency ?? 'PET_COIN'}）</Text>
        <Text className={styles.balanceValue}>{balanceText}</Text>
        {wallet?.status === 'FROZEN' && (
          <Text className={styles.balanceHint}>已冻结：暂不能消费，仍可查看与退款，请联系客服</Text>
        )}
        <Text className={styles.balanceHint}>
          宠物币与社区星光相互独立；历史社区星光请到星光流水查看
        </Text>
      </View>
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
                  style={{ color: spend ? '#ff6b6b' : '#52c41a' }}
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
    </View>
  )
}
