import { useState, useEffect, useCallback } from 'react'
import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, RefreshControl } from 'react-native'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { petApi } from '@/api/pet'
import type { PetWalletTransactionVO, PetWalletVO } from '@/api/pet'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

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
export default function PetWalletScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [wallet, setWallet] = useState<PetWalletVO | null>(null)
  const [walletUnavailable, setWalletUnavailable] = useState(false)
  const [transactions, setTransactions] = useState<PetWalletTransactionVO[]>([])
  const [filter, setFilter] = useState<'' | 'EARN' | 'SPEND' | 'REFUND'>('')
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [cursor, setCursor] = useState<string | number | null>(null)

  useEffect(() => {
    if (!isLoggedIn) router.replace('/login')
  }, [isLoggedIn])

  const loadWallet = useCallback(async () => {
    try {
      const res = await petApi.getWallet()
      if (res.data?.success && res.data.data) {
        setWallet(res.data.data)
        setWalletUnavailable(false)
      } else {
        setWalletUnavailable(true)
      }
    } catch {
      setWalletUnavailable(true)
    }
  }, [])

  const loadTransactions = useCallback(
    async (nextCursor?: string | number | null, reset = false) => {
      if (reset) setLoading(true)
      else setLoadingMore(true)
      try {
        const res = await petApi.listWalletTransactions({
          size: PAGE_SIZE,
          direction: filter || undefined,
          cursor: nextCursor ?? undefined,
        })
        if (res.data?.success) {
          const page = res.data.data ?? []
          setTransactions((prev) => (reset ? page : [...prev, ...page]))
          const meta = (res.data as { meta?: { nextCursor?: string | number | null } }).meta
          setCursor(meta?.nextCursor ?? null)
          setHasMore(Boolean(meta?.nextCursor))
        } else if (reset) {
          setTransactions([])
        }
      } catch {
        if (reset) setTransactions([])
      } finally {
        setLoading(false)
        setLoadingMore(false)
      }
    },
    [filter],
  )

  useEffect(() => {
    if (isLoggedIn) {
      void loadWallet()
      void loadTransactions(null, true)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn, filter])

  const renderItem = ({ item }: { item: PetWalletTransactionVO }) => {
    const spend = item.direction === 'SPEND'
    return (
      <View
        style={{
          flexDirection: 'row',
          justifyContent: 'space-between',
          alignItems: 'center',
          backgroundColor: WishColors.bgContainer,
          borderRadius: BorderRadius.lg,
          padding: Spacing.md,
          marginBottom: Spacing.sm,
        }}
      >
        <View style={{ flex: 1 }}>
          <Text style={{ fontSize: FontSize.sm, fontWeight: '500', color: WishColors.text }}>
            {bizLabel(item.bizType)}
          </Text>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>
            {new Date(item.occurredAt).toLocaleString('zh-CN')}
          </Text>
        </View>
        <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: spend ? '#ff6b6b' : '#52c41a' }}>
          {spend ? '-' : '+'}{item.amount}
        </Text>
      </View>
    )
  }

  const balanceText = walletUnavailable ? '暂不可用' : wallet ? String(wallet.balance) : '...'

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: Spacing.lg, paddingBottom: Spacing.sm }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.accentCyan }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>宠物币钱包</Text>
        <View style={{ width: 48 }} />
      </View>

      <View
        style={{
          margin: Spacing.lg,
          padding: Spacing.lg,
          borderRadius: BorderRadius.lg,
          backgroundColor: 'rgba(250, 204, 21, 0.12)',
          borderWidth: 1,
          borderColor: 'rgba(250, 204, 21, 0.35)',
        }}
      >
        <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>
          宠物币余额（{wallet?.currency ?? 'PET_COIN'}）
        </Text>
        <Text style={{ fontSize: 32, fontWeight: '700', color: '#facc15', marginTop: 4 }}>{balanceText}</Text>
        {wallet?.status === 'FROZEN' && (
          <Text style={{ fontSize: FontSize.xs, color: '#f87171', marginTop: 4 }}>
            已冻结：暂不能消费，仍可查看与退款，请联系客服
          </Text>
        )}
        <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 4 }}>
          宠物币与社区星光相互独立；历史社区星光请到星光流水查看
        </Text>
      </View>

      <View style={{ flexDirection: 'row', gap: Spacing.sm, paddingHorizontal: Spacing.lg, paddingBottom: Spacing.sm }}>
        {(['', 'EARN', 'SPEND', 'REFUND'] as const).map((f) => (
          <TouchableOpacity
            key={f || 'all'}
            activeOpacity={0.85}
            onPress={() => setFilter(f)}
            style={{
              paddingHorizontal: Spacing.md,
              paddingVertical: 6,
              borderRadius: BorderRadius.md,
              backgroundColor: filter === f ? 'rgba(0, 212, 255, 0.12)' : 'transparent',
              borderWidth: 1,
              borderColor: filter === f ? WishColors.accentCyan : WishColors.border,
            }}
          >
            <Text style={{ fontSize: FontSize.xs, color: filter === f ? WishColors.accentCyan : WishColors.textSecondary }}>
              {f === '' ? '全部' : f === 'EARN' ? '收入' : f === 'SPEND' ? '支出' : '退款'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <FlatList
        data={transactions}
        keyExtractor={(item) => String(item.transactionId)}
        renderItem={renderItem}
        contentContainerStyle={{ paddingHorizontal: Spacing.lg, paddingBottom: Spacing.xl }}
        onEndReachedThreshold={0.2}
        onEndReached={() => {
          if (hasMore && !loadingMore && !loading) loadTransactions(cursor, false)
        }}
        refreshControl={
          <RefreshControl
            refreshing={loading}
            onRefresh={() => {
              void loadWallet()
              void loadTransactions(null, true)
            }}
            tintColor={WishColors.accentCyan}
          />
        }
        ListEmptyComponent={
          !loading ? (
            <Text style={{ textAlign: 'center', color: WishColors.textTertiary, marginTop: Spacing.xl }}>
              还没有收支记录
            </Text>
          ) : (
            <ActivityIndicator color={WishColors.accentCyan} style={{ marginTop: Spacing.xl }} />
          )
        }
        ListFooterComponent={
          loadingMore ? (
            <ActivityIndicator color={WishColors.accentCyan} style={{ marginVertical: Spacing.md }} />
          ) : !hasMore && transactions.length > 0 ? (
            <Text style={{ textAlign: 'center', color: WishColors.textTertiary, fontSize: FontSize.xs, paddingVertical: Spacing.md }}>
              没有更多了
            </Text>
          ) : null
        }
      />
    </View>
  )
}
