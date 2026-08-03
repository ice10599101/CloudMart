import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  Image,
  ActivityIndicator,
  Alert,
  RefreshControl,
  ScrollView,
} from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { orderApi } from '@/api/order'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { Order, OrderItem } from '@/types'

const PAGE_SIZE = 10

const TAB_LIST = [
  { label: '全部', status: -1 },
  { label: '待付款', status: 0 },
  { label: '待发货', status: 1 },
  { label: '待收货', status: 2 },
  { label: '已完成', status: 3 },
] as const

type StatusColorKey = 'accentOrange' | 'accentPurple' | 'primary' | 'accentGreen'

const STATUS_COLOR_MAP: Record<number, StatusColorKey> = {
  0: 'accentOrange',
  1: 'accentPurple',
  2: 'primary',
  3: 'accentGreen',
}

function getStatusColor(theme: ReturnType<typeof useTheme>, status: number): string {
  const key = STATUS_COLOR_MAP[status]
  return key ? theme[key] : theme.textTertiary
}

function OrderItemRow({ item, theme }: { item: OrderItem; theme: ReturnType<typeof useTheme> }) {
  return (
    <View style={{ flexDirection: 'row', marginBottom: Spacing.sm }}>
      {item.productImage ? (
        <Image
          source={{ uri: item.productImage }}
          style={{ width: 72, height: 72, borderRadius: BorderRadius.md, resizeMode: 'cover' }}
        />
      ) : (
        <View
          style={{
            width: 72,
            height: 72,
            borderRadius: BorderRadius.md,
            backgroundColor: theme.bgInput,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Text style={{ fontSize: 24, opacity: 0.3 }}>📦</Text>
        </View>
      )}

      <View style={{ flex: 1, marginLeft: Spacing.md, justifyContent: 'space-between' }}>
        <Text numberOfLines={2} style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500', lineHeight: 20 }}>
          {item.productName}
        </Text>
        {item.skuName ? (
          <Text numberOfLines={1} style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>
            {item.skuName}
          </Text>
        ) : null}
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <Text style={{ fontSize: FontSize.md, fontWeight: 'bold', color: theme.accentRed }}>
            ¥{item.price.toFixed(2)}
          </Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>x{item.quantity}</Text>
        </View>
      </View>
    </View>
  )
}

function OrderCard({
  order,
  theme,
  onCancel,
  onConfirmReceive,
  onRebuy,
}: {
  order: Order
  theme: ReturnType<typeof useTheme>
  onCancel: (id: number) => void
  onConfirmReceive: (id: number) => void
  onRebuy: (id: number) => void
}) {
  const statusColor = getStatusColor(theme, order.status)

  const renderActions = () => {
    const buttons: { label: string; type: 'default' | 'primary'; onPress: () => void }[] = []

    if (order.status === 0) {
      buttons.push({ label: '取消订单', type: 'default', onPress: () => onCancel(order.id) })
      buttons.push({ label: '去支付', type: 'primary', onPress: () => router.push(`/order-detail?id=${order.id}`) })
    } else if (order.status === 2) {
      buttons.push({ label: '确认收货', type: 'primary', onPress: () => onConfirmReceive(order.id) })
    } else if (order.status === 3) {
      buttons.push({ label: '再次购买', type: 'primary', onPress: () => onRebuy(order.id) })
    }

    if (buttons.length === 0) return null

    return (
      <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.sm, marginTop: Spacing.md }}>
        {buttons.map((btn) => (
          <TouchableOpacity
            key={btn.label}
            activeOpacity={0.7}
            onPress={btn.onPress}
            style={{
              paddingHorizontal: Spacing.lg,
              paddingVertical: Spacing.sm,
              borderRadius: BorderRadius.xl,
              borderWidth: 1,
              borderColor: btn.type === 'primary' ? theme.primary : theme.border,
              backgroundColor: btn.type === 'primary' ? theme.primary : 'transparent',
            }}
          >
            <Text
              style={{
                fontSize: FontSize.sm,
                fontWeight: '600',
                color: btn.type === 'primary' ? '#FFFFFF' : theme.textSecondary,
              }}
            >
              {btn.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    )
  }

  return (
    <TouchableOpacity
      activeOpacity={0.7}
      onPress={() => router.push(`/order-detail?id=${order.id}`)}
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
      {/* Header: order number + status badge */}
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: Spacing.md }}>
        <Text numberOfLines={1} style={{ flex: 1, fontSize: FontSize.sm, color: theme.textTertiary, marginRight: Spacing.sm }}>
          订单号：{order.orderNo}
        </Text>
        <View
          style={{
            paddingHorizontal: Spacing.md,
            paddingVertical: Spacing.xs,
            borderRadius: BorderRadius.full,
            backgroundColor: statusColor,
          }}
        >
          <Text style={{ fontSize: FontSize.xs, color: '#FFFFFF', fontWeight: '600' }}>{order.statusText}</Text>
        </View>
      </View>

      {/* Order items */}
      {order.items.map((item) => (
        <OrderItemRow key={item.id} item={item} theme={theme} />
      ))}

      {/* Footer: total amount + order time */}
      <View
        style={{
          flexDirection: 'row',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginTop: Spacing.sm,
          paddingTop: Spacing.md,
          borderTopWidth: 1,
          borderTopColor: theme.border,
        }}
      >
        <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>
          {new Date(order.createdAt).toLocaleString()}
        </Text>
        <View style={{ flexDirection: 'row', alignItems: 'baseline' }}>
          <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>合计：</Text>
          <Text style={{ fontSize: FontSize.lg, fontWeight: 'bold', color: theme.accentRed }}>
            ¥{order.payAmount.toFixed(2)}
          </Text>
        </View>
      </View>

      {/* Action buttons */}
      {renderActions()}
    </TouchableOpacity>
  )
}

export default function OrdersScreen() {
  const theme = useTheme()
  const { status: statusParam } = useLocalSearchParams<{ status?: string }>()

  const initialStatus = statusParam ? Number(statusParam) : -1
  const [activeTab, setActiveTab] = useState(initialStatus)
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  const loadOrders = useCallback(
    async (pageNum: number, reset = false) => {
      if (loading) return
      setLoading(true)
      try {
        const params: { status?: number; page: number; pageSize: number } = {
          page: pageNum,
          pageSize: PAGE_SIZE,
        }
        if (activeTab !== -1) {
          params.status = activeTab
        }
        const res = await orderApi.getList(params)
        const newOrders: Order[] = (res.data as any)?.data?.list || []
        setOrders(reset ? newOrders : [...orders, ...newOrders])
        setHasMore(newOrders.length >= PAGE_SIZE)
        setPage(pageNum)
      } catch {
        if (reset) setOrders([])
      } finally {
        setLoading(false)
        setRefreshing(false)
      }
    },
    [loading, orders, activeTab],
  )

  useEffect(() => {
    loadOrders(1, true)
  }, [activeTab])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    loadOrders(1, true)
  }, [loadOrders])

  const handleLoadMore = useCallback(() => {
    if (hasMore && !loading) {
      loadOrders(page + 1)
    }
  }, [hasMore, loading, page, loadOrders])

  const handleTabChange = useCallback((status: number) => {
    setActiveTab(status)
    setOrders([])
    setPage(1)
    setHasMore(true)
  }, [])

  const handleCancel = useCallback(
    (id: number) => {
      Alert.alert('取消订单', '确定要取消该订单吗？', [
        { text: '再想想', style: 'cancel' },
        {
          text: '确定取消',
          style: 'destructive',
          onPress: async () => {
            try {
              await orderApi.cancel(id)
              setOrders((prev) => prev.filter((o) => o.id !== id))
            } catch {
              Alert.alert('错误', '取消订单失败，请稍后重试')
            }
          },
        },
      ])
    },
    [],
  )

  const handleConfirmReceive = useCallback(
    (id: number) => {
      Alert.alert('确认收货', '确认已收到商品吗？', [
        { text: '取消', style: 'cancel' },
        {
          text: '确认',
          onPress: async () => {
            try {
              await orderApi.confirmReceive(id)
              onRefresh()
            } catch {
              Alert.alert('错误', '确认收货失败，请稍后重试')
            }
          },
        },
      ])
    },
    [onRefresh],
  )

  const handleRebuy = useCallback(
    (id: number) => {
      router.push(`/order-detail?id=${id}`)
    },
    [],
  )

  const renderOrderItem = ({ item }: { item: Order }) => (
    <OrderCard
      order={item}
      theme={theme}
      onCancel={handleCancel}
      onConfirmReceive={handleConfirmReceive}
      onRebuy={handleRebuy}
    />
  )

  const renderEmpty = () => (
    <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 3 }}>
      <Text style={{ fontSize: 48, marginBottom: Spacing.lg, opacity: 0.3 }}>📋</Text>
      <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无订单</Text>
      <TouchableOpacity
        activeOpacity={0.7}
        onPress={() => router.push('/(tabs)/mall')}
        style={{
          marginTop: Spacing.xl,
          paddingHorizontal: Spacing.xxl,
          paddingVertical: Spacing.md,
          borderRadius: BorderRadius.xl,
          backgroundColor: theme.primary,
        }}
      >
        <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#FFFFFF' }}>去逛逛</Text>
      </TouchableOpacity>
    </View>
  )

  const renderFooter = () => {
    if (loading && !refreshing) {
      return <ActivityIndicator color={theme.primary} style={{ marginVertical: Spacing.xl }} />
    }
    if (!hasMore && orders.length > 0) {
      return (
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingVertical: Spacing.xl }}>
          <View style={{ flex: 1, height: 1, backgroundColor: theme.border, marginHorizontal: Spacing.lg }} />
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>到底啦</Text>
          <View style={{ flex: 1, height: 1, backgroundColor: theme.border, marginHorizontal: Spacing.lg }} />
        </View>
      )
    }
    return null
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      {/* Tab bar */}
      <View style={{ backgroundColor: theme.bgBase }}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ paddingHorizontal: Spacing.lg, paddingVertical: Spacing.md }}
        >
          {TAB_LIST.map((tab) => {
            const isActive = activeTab === tab.status
            return (
              <TouchableOpacity
                key={tab.status}
                activeOpacity={0.7}
                onPress={() => handleTabChange(tab.status)}
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

      {/* Order list */}
      <FlatList
        data={orders}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={{ paddingHorizontal: Spacing.lg, paddingBottom: Spacing.xxl }}
        renderItem={renderOrderItem}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.primary} />}
        onEndReached={handleLoadMore}
        onEndReachedThreshold={0.3}
        ListEmptyComponent={!loading ? renderEmpty : null}
        ListFooterComponent={renderFooter}
      />
    </View>
  )
}
