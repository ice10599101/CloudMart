import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, Alert } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { orderApi } from '@/api/order'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { Order } from '@/types'

const STATUS_CONFIG: Record<number, { icon: string; label: string; colorKey: 'accentOrange' | 'accentRed' | 'accentGreen' | 'primary' | 'textTertiary' }> = {
  0: { icon: '💰', label: '待付款', colorKey: 'accentOrange' },
  1: { icon: '📦', label: '待发货', colorKey: 'primary' },
  2: { icon: '🚚', label: '待收货', colorKey: 'primary' },
  3: { icon: '✅', label: '已完成', colorKey: 'accentGreen' },
  4: { icon: '❌', label: '已取消', colorKey: 'textTertiary' },
}

function formatTime(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export default function OrderDetailPage() {
  const theme = useTheme()
  const { id } = useLocalSearchParams<{ id: string }>()

  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)

  const fetchOrder = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const res = await orderApi.getDetail(Number(id))
      const data = res.data as { data?: Order }
      if (data?.data) {
        setOrder(data.data)
      }
    } catch {
      Alert.alert('错误', '加载订单失败')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    fetchOrder()
  }, [fetchOrder])

  const handleCancel = () => {
    if (!order) return
    Alert.alert('确认取消', '确定要取消该订单吗？', [
      { text: '再想想', style: 'cancel' },
      {
        text: '确定取消',
        style: 'destructive',
        onPress: async () => {
          setActionLoading(true)
          try {
            await orderApi.cancel(order.id)
            Alert.alert('提示', '订单已取消')
            fetchOrder()
          } catch {
            Alert.alert('错误', '取消订单失败')
          } finally {
            setActionLoading(false)
          }
        },
      },
    ])
  }

  const handlePay = () => {
    if (!order) return
    router.push(`/checkout?orderId=${order.id}`)
  }

  const handleConfirmReceive = () => {
    if (!order) return
    Alert.alert('确认收货', '确认已收到商品？', [
      { text: '取消', style: 'cancel' },
      {
        text: '确定',
        onPress: async () => {
          setActionLoading(true)
          try {
            await orderApi.confirmReceive(order.id)
            Alert.alert('提示', '已确认收货')
            fetchOrder()
          } catch {
            Alert.alert('错误', '确认收货失败')
          } finally {
            setActionLoading(false)
          }
        },
      },
    ])
  }

  const handleRebuy = () => {
    router.push('/(tabs)/mall')
  }

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" color={theme.primary} />
        <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, marginTop: Spacing.lg }}>加载中...</Text>
      </View>
    )
  }

  if (!order) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, justifyContent: 'center', alignItems: 'center' }}>
        <Text style={{ fontSize: 48, marginBottom: Spacing.lg }}>😔</Text>
        <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>订单不存在</Text>
        <TouchableOpacity
          activeOpacity={0.7}
          onPress={router.back}
          style={{
            marginTop: Spacing.xl,
            paddingHorizontal: Spacing.xxl,
            paddingVertical: Spacing.md,
            borderRadius: BorderRadius.lg,
            backgroundColor: theme.primary,
          }}
        >
          <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>返回</Text>
        </TouchableOpacity>
      </View>
    )
  }

  const statusConfig = STATUS_CONFIG[order.status] ?? STATUS_CONFIG[4]
  const statusColor = theme[statusConfig.colorKey]
  const shippingFee: number = 0
  const discount = order.totalAmount - order.payAmount

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 100 }}>
        {/* Header */}
        <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: Spacing.lg, paddingTop: Spacing.xxxl, paddingBottom: Spacing.md }}>
          <TouchableOpacity activeOpacity={0.7} onPress={router.back} style={{ width: 36, height: 36, borderRadius: 18, backgroundColor: theme.bgElevated, justifyContent: 'center', alignItems: 'center' }}>
            <Text style={{ fontSize: 18, color: theme.text }}>←</Text>
          </TouchableOpacity>
          <Text style={{ flex: 1, textAlign: 'center', fontSize: FontSize.xl, fontWeight: '600', color: theme.text, marginRight: 36 }}>订单详情</Text>
        </View>

        {/* Status Section */}
        <View style={{
          marginHorizontal: Spacing.lg,
          marginBottom: Spacing.lg,
          backgroundColor: theme.bgContainer,
          borderRadius: BorderRadius.lg,
          padding: Spacing.xl,
          alignItems: 'center',
        }}>
          <Text style={{ fontSize: 40, marginBottom: Spacing.sm }}>{statusConfig.icon}</Text>
          <Text style={{ fontSize: FontSize.xxl, fontWeight: '700', color: statusColor }}>{statusConfig.label}</Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>
            订单号：{order.orderNo}
          </Text>
        </View>

        {/* Address Section */}
        <View style={{
          marginHorizontal: Spacing.lg,
          marginBottom: Spacing.lg,
          backgroundColor: theme.bgContainer,
          borderRadius: BorderRadius.lg,
          padding: Spacing.lg,
        }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: Spacing.sm }}>
            <Text style={{ fontSize: 16, marginRight: Spacing.xs }}>📍</Text>
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, fontWeight: '500' }}>收货信息</Text>
          </View>
          <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: Spacing.xs }}>
            <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text, marginRight: Spacing.md }}>
              {order.receiverName ?? '--'}
            </Text>
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>
              {order.receiverPhone ?? '--'}
            </Text>
          </View>
          <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, lineHeight: 20 }}>
            {order.receiverAddress ?? '暂无收货地址'}
          </Text>
        </View>

        {/* Order Items Section */}
        <View style={{ marginHorizontal: Spacing.lg, marginBottom: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, fontWeight: '500', marginBottom: Spacing.sm }}>
            商品清单 ({order.items.length})
          </Text>
          <View style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, overflow: 'hidden' }}>
            {order.items.map((item, index) => (
              <TouchableOpacity
                key={item.id}
                activeOpacity={0.7}
                onPress={() => router.push(`/product/${item.productId}`)}
                style={{
                  flexDirection: 'row',
                  padding: Spacing.lg,
                  borderBottomWidth: index < order.items.length - 1 ? 1 : 0,
                  borderBottomColor: theme.border,
                }}
              >
                <Image
                  source={{ uri: item.productImage }}
                  style={{ width: 80, height: 80, borderRadius: BorderRadius.md, backgroundColor: theme.bgElevated }}
                  resizeMode="cover"
                />
                <View style={{ flex: 1, marginLeft: Spacing.md, justifyContent: 'space-between' }}>
                  <Text
                    style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500', lineHeight: 20 }}
                    numberOfLines={2}
                    ellipsizeMode="tail"
                  >
                    {item.productName}
                  </Text>
                  {item.skuName ? (
                    <View style={{ backgroundColor: theme.bgInput, paddingHorizontal: Spacing.sm, paddingVertical: 2, borderRadius: BorderRadius.sm, alignSelf: 'flex-start', marginTop: 2 }}>
                      <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>{item.skuName}</Text>
                    </View>
                  ) : null}
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Spacing.xs }}>
                    <Text style={{ fontSize: FontSize.lg, color: theme.accentRed, fontWeight: '600' }}>
                      ¥{item.price.toFixed(2)}
                    </Text>
                    <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>
                      x{item.quantity}
                    </Text>
                  </View>
                </View>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Price Breakdown */}
        <View style={{
          marginHorizontal: Spacing.lg,
          marginBottom: Spacing.lg,
          backgroundColor: theme.bgContainer,
          borderRadius: BorderRadius.lg,
          padding: Spacing.lg,
        }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>商品合计</Text>
            <Text style={{ fontSize: FontSize.md, color: theme.text }}>¥{order.totalAmount.toFixed(2)}</Text>
          </View>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>运费</Text>
            <Text style={{ fontSize: FontSize.md, color: theme.accentGreen }}>
              {shippingFee === 0 ? '免运费' : `¥${shippingFee.toFixed(2)}`}
            </Text>
          </View>
          {discount > 0 && (
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
              <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>优惠</Text>
              <Text style={{ fontSize: FontSize.md, color: theme.primary }}>-¥{discount.toFixed(2)}</Text>
            </View>
          )}
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: Spacing.md, paddingTop: Spacing.md, borderTopWidth: 1, borderTopColor: theme.border }}>
            <Text style={{ fontSize: FontSize.lg, color: theme.text, fontWeight: '600' }}>实付金额</Text>
            <Text style={{ fontSize: FontSize.xxl, color: theme.accentRed, fontWeight: '700' }}>
              ¥{order.payAmount.toFixed(2)}
            </Text>
          </View>
        </View>

        {/* Order Info Section */}
        <View style={{
          marginHorizontal: Spacing.lg,
          marginBottom: Spacing.lg,
          backgroundColor: theme.bgContainer,
          borderRadius: BorderRadius.lg,
          padding: Spacing.lg,
        }}>
          <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, fontWeight: '500', marginBottom: Spacing.md }}>
            订单信息
          </Text>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
            <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>订单号</Text>
            <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>{order.orderNo}</Text>
          </View>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
            <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>下单时间</Text>
            <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>{formatTime(order.createdAt)}</Text>
          </View>
          {order.payTime && (
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
              <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>支付时间</Text>
              <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>{formatTime(order.payTime)}</Text>
            </View>
          )}
          {order.shipTime && (
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
              <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>发货时间</Text>
              <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>{formatTime(order.shipTime)}</Text>
            </View>
          )}
          {order.receiveTime && (
            <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
              <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>收货时间</Text>
              <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>{formatTime(order.receiveTime)}</Text>
            </View>
          )}
        </View>
      </ScrollView>

      {/* Bottom Action Bar */}
      {(order.status === 0 || order.status === 2 || order.status === 3) && (
        <View style={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'flex-end',
          paddingHorizontal: Spacing.lg,
          paddingVertical: Spacing.md,
          paddingBottom: Spacing.xl,
          backgroundColor: theme.bgContainer,
          borderTopWidth: 1,
          borderTopColor: theme.border,
          gap: Spacing.md,
        }}>
          {order.status === 0 && (
            <>
              <TouchableOpacity
                activeOpacity={0.7}
                onPress={actionLoading ? undefined : handleCancel}
                disabled={actionLoading}
                style={{
                  paddingHorizontal: Spacing.xl,
                  paddingVertical: Spacing.md,
                  borderRadius: BorderRadius.xl,
                  borderWidth: 1,
                  borderColor: theme.border,
                  backgroundColor: 'transparent',
                  opacity: actionLoading ? 0.5 : 1,
                }}
              >
                <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, fontWeight: '500' }}>取消订单</Text>
              </TouchableOpacity>
              <TouchableOpacity
                activeOpacity={0.8}
                onPress={actionLoading ? undefined : handlePay}
                disabled={actionLoading}
                style={{
                  paddingHorizontal: Spacing.xxl,
                  paddingVertical: Spacing.md,
                  borderRadius: BorderRadius.xl,
                  backgroundColor: theme.primary,
                  opacity: actionLoading ? 0.5 : 1,
                }}
              >
                <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>去支付</Text>
              </TouchableOpacity>
            </>
          )}
          {order.status === 2 && (
            <TouchableOpacity
              activeOpacity={0.8}
              onPress={actionLoading ? undefined : handleConfirmReceive}
              disabled={actionLoading}
              style={{
                paddingHorizontal: Spacing.xxl,
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.xl,
                backgroundColor: theme.primary,
                opacity: actionLoading ? 0.5 : 1,
              }}
            >
              <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>确认收货</Text>
            </TouchableOpacity>
          )}
          {order.status === 3 && (
            <TouchableOpacity
              activeOpacity={0.8}
              onPress={handleRebuy}
              style={{
                paddingHorizontal: Spacing.xxl,
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.xl,
                backgroundColor: theme.primary,
              }}
            >
              <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>再次购买</Text>
            </TouchableOpacity>
          )}
        </View>
      )}
    </View>
  )
}
