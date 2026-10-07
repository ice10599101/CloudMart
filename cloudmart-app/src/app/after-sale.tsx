import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, RefreshControl, ScrollView } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import {
  pageMyAfterSales,
  AFTER_SALE_STATUS_TEXT,
  AFTER_SALE_TYPE_TEXT,
  type AfterSaleCase,
} from '@/api/after-sale'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

const PAGE_SIZE = 10

const TAB_LIST = [
  { label: '全部', value: undefined },
  { label: '待处理', value: 'PENDING' },
  { label: '已同意', value: 'APPROVED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已退款', value: 'REFUNDED' },
  { label: '已关闭', value: 'CLOSED' },
] as const

type StatusColorKey = 'accentOrange' | 'primary' | 'accentRed' | 'accentGreen'

const STATUS_COLOR_MAP: Record<string, StatusColorKey> = {
  PENDING: 'accentOrange',
  APPROVED: 'primary',
  REJECTED: 'accentRed',
  REFUNDED: 'accentGreen',
}

export default function AfterSaleScreen() {
  const theme = useTheme()
  const { status: statusParam } = useLocalSearchParams<{ status?: string }>()

  const initialIndex = (() => {
    if (!statusParam) return 0
    const idx = TAB_LIST.findIndex((t) => t.value === statusParam)
    return idx >= 0 ? idx : 0
  })()

  const [activeTab, setActiveTab] = useState(initialIndex)
  const [cases, setCases] = useState<AfterSaleCase[]>([])
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  const loadCases = useCallback(
    async (pageNum: number, reset = false) => {
      if (loading) return
      setLoading(true)
      try {
        const status = TAB_LIST[activeTab]?.value
        const params: { page: number; pageSize: number; status?: string } = { page: pageNum, pageSize: PAGE_SIZE }
        if (status) params.status = status
        const res = await pageMyAfterSales(params)
        const list: AfterSaleCase[] =
          (res.data as unknown as { data?: { records?: AfterSaleCase[] } })?.data?.records || []
        setCases(reset ? list : [...cases, ...list])
        setHasMore(list.length >= PAGE_SIZE)
        setPage(pageNum)
      } catch {
        if (reset) setCases([])
      } finally {
        setLoading(false)
        setRefreshing(false)
      }
    },
    [loading, cases, activeTab],
  )

  useEffect(() => {
    loadCases(1, true)
  }, [activeTab])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    loadCases(1, true)
  }, [loadCases])

  const handleLoadMore = useCallback(() => {
    if (hasMore && !loading) loadCases(page + 1)
  }, [hasMore, loading, page, loadCases])

  const renderCaseCard = ({ item }: { item: AfterSaleCase }) => {
    const colorKey = STATUS_COLOR_MAP[item.status]
    const statusColor = colorKey ? theme[colorKey] : theme.textTertiary
    return (
      <TouchableOpacity
        activeOpacity={0.7}
        onPress={() => router.push(`/after-sale/${item.id}`)}
        style={{
          backgroundColor: theme.bgContainer,
          borderRadius: BorderRadius.lg,
          borderWidth: 1,
          borderColor: theme.border,
          padding: Spacing.lg,
          marginBottom: Spacing.md,
          ...theme.shadowCard,
        }}
      >
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: Spacing.sm }}>
          <Text numberOfLines={1} style={{ flex: 1, fontSize: FontSize.sm, color: theme.textTertiary, marginRight: Spacing.sm }}>
            售后单号：{item.caseNo}
          </Text>
          <Text style={{ fontSize: FontSize.sm, color: statusColor, fontWeight: '600' }}>
            {AFTER_SALE_STATUS_TEXT[item.status] || item.status}
          </Text>
        </View>
        <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500', marginBottom: Spacing.xs }}>
          {AFTER_SALE_TYPE_TEXT[item.type] || item.type}
          {item.quantity > 0 ? ` · ${item.quantity} 件` : ' · 整单'}
        </Text>
        <Text numberOfLines={2} style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginBottom: Spacing.md }}>
          {item.reason}
        </Text>
        <View
          style={{
            flexDirection: 'row',
            justifyContent: 'space-between',
            alignItems: 'center',
            paddingTop: Spacing.md,
            borderTopWidth: 1,
            borderTopColor: theme.border,
          }}
        >
          <Text numberOfLines={1} style={{ flex: 1, fontSize: FontSize.xs, color: theme.textTertiary }}>
            订单 {item.orderNo || item.orderId}
          </Text>
          {item.refundAmount != null && (
            <Text style={{ fontSize: FontSize.sm, color: theme.accentRed, fontWeight: '600', marginLeft: Spacing.sm }}>
              退款 ¥{item.refundAmount}
            </Text>
          )}
        </View>
      </TouchableOpacity>
    )
  }

  const renderEmpty = () => (
    <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 3 }}>
      <Text style={{ fontSize: 48, marginBottom: Spacing.lg, opacity: 0.3 }}>🛠️</Text>
      <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无售后记录</Text>
    </View>
  )

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <View style={{ backgroundColor: theme.bgBase }}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ paddingHorizontal: Spacing.lg, paddingVertical: Spacing.md }}
        >
          {TAB_LIST.map((tab, i) => {
            const isActive = activeTab === i
            return (
              <TouchableOpacity
                key={tab.label}
                activeOpacity={0.7}
                onPress={() => {
                  setActiveTab(i)
                  setCases([])
                  setPage(1)
                  setHasMore(true)
                }}
                style={{
                  paddingHorizontal: Spacing.lg,
                  paddingVertical: Spacing.sm,
                  borderRadius: BorderRadius.xl,
                  backgroundColor: isActive ? theme.primary : theme.bgInput,
                  marginRight: Spacing.sm,
                }}
              >
                <Text
                  style={{
                    fontSize: FontSize.md,
                    fontWeight: isActive ? '600' : '400',
                    color: isActive ? '#FFFFFF' : theme.textSecondary,
                  }}
                >
                  {tab.label}
                </Text>
              </TouchableOpacity>
            )
          })}
        </ScrollView>
      </View>

      <FlatList
        data={cases}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={{ paddingHorizontal: Spacing.lg, paddingBottom: Spacing.xxl }}
        renderItem={renderCaseCard}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.primary} />}
        onEndReached={handleLoadMore}
        onEndReachedThreshold={0.3}
        ListEmptyComponent={!loading ? renderEmpty : null}
        ListFooterComponent={loading && !refreshing ? <ActivityIndicator color={theme.primary} style={{ marginVertical: Spacing.xl }} /> : null}
      />
    </View>
  )
}
