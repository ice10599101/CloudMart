import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, Alert, TextInput, Modal } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import * as ImagePicker from 'expo-image-picker'
import { useTheme } from '@/hooks/use-theme-context'
import { orderApi } from '@/api/order'
import {
  applyAfterSale,
  listOrderAfterSales,
  AFTER_SALE_STATUS_TEXT,
  AFTER_SALE_TYPE_TEXT,
  type AfterSaleCase,
} from '@/api/after-sale'
import { fileApi } from '@/api/file'
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
  const [refundOpen, setRefundOpen] = useState(false)
  const [refundReason, setRefundReason] = useState('')

  // T11/P0-2：售后案件（PAID/SHIPPED 可申请；本单已有案件列表）
  const [afterSaleCases, setAfterSaleCases] = useState<AfterSaleCase[]>([])
  const [afterSaleOpen, setAfterSaleOpen] = useState(false)
  const [afterSaleType, setAfterSaleType] = useState<'REFUND_ONLY' | 'RETURN_REFUND'>('REFUND_ONLY')
  const [afterSaleItemId, setAfterSaleItemId] = useState<number | null>(null)
  const [afterSaleQuantity, setAfterSaleQuantity] = useState(1)
  const [afterSaleReason, setAfterSaleReason] = useState('')
  const [afterSaleFileIds, setAfterSaleFileIds] = useState<string[]>([])
  const [afterSaleSubmitting, setAfterSaleSubmitting] = useState(false)

  const fetchOrder = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const res = await orderApi.getDetail(id)
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

  const fetchAfterSaleCases = useCallback(async () => {
    if (!id) return
    try {
      const res = await listOrderAfterSales(id)
      setAfterSaleCases((res.data as unknown as { data?: AfterSaleCase[] })?.data ?? [])
    } catch {
      setAfterSaleCases([])
    }
  }, [id])

  useEffect(() => {
    fetchOrder()
    fetchAfterSaleCases()
  }, [fetchOrder, fetchAfterSaleCases])

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

  /** 去支付 → 收银台（修复原 checkout?orderId 断链；对齐 Web 端 /payment/:id） */
  const handlePay = () => {
    if (!order) return
    router.push(`/payment?id=${order.id}`)
  }

  /** 申请退款（对齐 Web 端：已付款/已发货可申请，必填原因） */
  const submitRefund = async () => {
    if (!order) return
    if (!refundReason.trim()) {
      Alert.alert('提示', '请填写退款原因')
      return
    }
    setActionLoading(true)
    try {
      await orderApi.refund(order.id, refundReason.trim())
      setRefundOpen(false)
      setRefundReason('')
      Alert.alert('提示', '退款申请已提交')
      fetchOrder()
    } catch {
      Alert.alert('错误', '退款申请失败')
    } finally {
      setActionLoading(false)
    }
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
    const first = order?.items?.[0]
    if (first?.productId) {
      router.push(`/product/${first.productId}`)
    } else {
      router.push('/(tabs)/mall')
    }
  }

  // ==================== T11/P0-2：申请售后 ====================

  /** 打开弹窗并重置表单；已发货默认退货退款，待发货仅退款（未发货无货可退） */
  const openAfterSale = () => {
    setAfterSaleType(order?.status === 2 ? 'RETURN_REFUND' : 'REFUND_ONLY')
    setAfterSaleItemId(null)
    setAfterSaleQuantity(1)
    setAfterSaleReason('')
    setAfterSaleFileIds([])
    setAfterSaleOpen(true)
  }

  const selectAfterSaleType = (type: 'REFUND_ONLY' | 'RETURN_REFUND') => {
    setAfterSaleType(type)
    // 已发货整单退货退款后端首期不开放（需按商品项拆分）
    if (type === 'RETURN_REFUND' && afterSaleItemId == null) {
      const firstItem = order?.items?.[0]
      if (firstItem) {
        setAfterSaleItemId(firstItem.id)
        setAfterSaleQuantity(firstItem.quantity)
      }
    }
  }

  const selectAfterSaleItem = (itemId: number | null) => {
    setAfterSaleItemId(itemId)
    if (itemId != null) {
      const item = order?.items?.find((it) => it.id === itemId)
      setAfterSaleQuantity(item?.quantity ?? 1)
    }
  }

  const chooseAttachment = async () => {
    if (afterSaleFileIds.length >= 3) {
      Alert.alert('提示', '最多上传 3 张凭证')
      return
    }
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 })
    if (result.canceled || !result.assets?.[0]) return
    const asset = result.assets[0]
    try {
      const form = new FormData()
      form.append('file', { uri: asset.uri, name: 'after-sale.jpg', type: 'image/jpeg' } as unknown as Blob)
      // 售后凭证为用户私密附件：显式 PRIVATE（S01 可见性分域）
      const { data: up } = await fileApi.upload(form, 'PRIVATE')
      const fileId = up?.data?.fileId
      if (!fileId) {
        Alert.alert('上传未成功', up?.error?.message ?? '请稍后再试')
        return
      }
      setAfterSaleFileIds((prev) => [...prev, fileId])
    } catch {
      Alert.alert('错误', '凭证上传失败')
    }
  }

  const submitAfterSale = async () => {
    if (!order) return
    if (!afterSaleReason.trim()) {
      Alert.alert('提示', '请填写售后原因')
      return
    }
    // 已发货整单退货退款后端不开放，前端先行拦截
    if (afterSaleType === 'RETURN_REFUND' && afterSaleItemId == null) {
      Alert.alert('提示', '已发货订单请按商品项申请退货退款')
      return
    }
    setAfterSaleSubmitting(true)
    try {
      await applyAfterSale(order.id, {
        type: afterSaleType,
        reason: afterSaleReason.trim(),
        itemId: afterSaleItemId ?? undefined,
        quantity: afterSaleItemId != null ? afterSaleQuantity : undefined,
        attachmentFileIds: afterSaleFileIds.length > 0 ? JSON.stringify(afterSaleFileIds) : undefined,
      })
      setAfterSaleOpen(false)
      Alert.alert('提示', '售后申请已提交')
      fetchAfterSaleCases()
      fetchOrder()
    } catch {
      Alert.alert('错误', '售后申请失败')
    } finally {
      setAfterSaleSubmitting(false)
    }
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

        {/* 状态进度条（对齐 Web 端：提交订单→支付成功→已发货→已完成） */}
        {order.status !== 4 && (
          <View style={{
            marginHorizontal: Spacing.lg,
            marginBottom: Spacing.lg,
            backgroundColor: theme.bgContainer,
            borderRadius: BorderRadius.lg,
            padding: Spacing.lg,
            flexDirection: 'row',
            justifyContent: 'space-between',
          }}>
            {([
              { key: 'created', label: '提交订单', done: true },
              { key: 'paid', label: '支付成功', done: !!order.payTime },
              { key: 'shipped', label: '已发货', done: !!order.shipTime },
              { key: 'completed', label: '已完成', done: !!order.receiveTime },
            ] as const).map((step, i) => (
              <View key={step.key} style={{ alignItems: 'center', flex: 1 }}>
                <View style={{
                  width: 24,
                  height: 24,
                  borderRadius: 12,
                  backgroundColor: step.done ? theme.primary : theme.border,
                  justifyContent: 'center',
                  alignItems: 'center',
                }}>
                  <Text style={{ fontSize: 12, color: '#FFFFFF' }}>{step.done ? '✓' : i + 1}</Text>
                </View>
                <Text style={{ fontSize: FontSize.xs, color: step.done ? theme.primary : theme.textTertiary, marginTop: 4 }}>
                  {step.label}
                </Text>
              </View>
            ))}
          </View>
        )}

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
          {order.refundedAmount != null && order.refundedAmount > 0 && (
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', marginTop: 8 }}>
              <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>
                已退金额{order.refundStatus === 'PARTIAL' ? '（部分退款）' : '（全额退款）'}
              </Text>
              <Text style={{ fontSize: FontSize.lg, color: theme.accentRed, fontWeight: '700' }}>
                ¥{order.refundedAmount.toFixed(2)}
              </Text>
            </View>
          )}
        </View>

        {/* T11/P0-2：售后案件（本单已有申请与进度） */}
        {afterSaleCases.length > 0 && (
          <View style={{
            marginHorizontal: Spacing.lg,
            marginBottom: Spacing.lg,
            backgroundColor: theme.bgContainer,
            borderRadius: BorderRadius.lg,
            padding: Spacing.lg,
          }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: Spacing.md }}>
              <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '600' }}>售后进度</Text>
              <TouchableOpacity activeOpacity={0.7} onPress={() => router.push('/after-sale')}>
                <Text style={{ fontSize: FontSize.sm, color: theme.primary }}>我的售后 ›</Text>
              </TouchableOpacity>
            </View>
            {afterSaleCases.map((c) => (
              <TouchableOpacity
                key={c.id}
                activeOpacity={0.7}
                onPress={() => router.push(`/after-sale/${c.id}`)}
                style={{
                  flexDirection: 'row',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  paddingVertical: Spacing.sm,
                  borderTopWidth: 1,
                  borderTopColor: theme.border,
                }}
              >
                <View style={{ flex: 1, marginRight: Spacing.sm }}>
                  <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500' }}>
                    {AFTER_SALE_TYPE_TEXT[c.type] || c.type}{c.quantity > 0 ? ` · ${c.quantity} 件` : ''}
                  </Text>
                  <Text numberOfLines={1} style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: 2 }}>{c.reason}</Text>
                </View>
                <Text style={{ fontSize: FontSize.sm, color: theme.accentOrange, fontWeight: '600' }}>
                  {AFTER_SALE_STATUS_TEXT[c.status] || c.status}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        )}

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
      {(order.status === 0 || order.status === 1 || order.status === 2 || order.status === 3) && (
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
          {order.status === 1 && (
            <TouchableOpacity
              activeOpacity={0.7}
              onPress={actionLoading ? undefined : () => setRefundOpen(true)}
              disabled={actionLoading}
              style={{
                paddingHorizontal: Spacing.xl,
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.xl,
                borderWidth: 1,
                borderColor: theme.border,
                opacity: actionLoading ? 0.5 : 1,
              }}
            >
              <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, fontWeight: '500' }}>申请退款</Text>
            </TouchableOpacity>
          )}
          {(order.status === 1 || order.status === 2) && (
            <TouchableOpacity
              activeOpacity={0.7}
              onPress={actionLoading ? undefined : openAfterSale}
              disabled={actionLoading}
              style={{
                paddingHorizontal: Spacing.xl,
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.xl,
                borderWidth: 1,
                borderColor: theme.border,
                opacity: actionLoading ? 0.5 : 1,
              }}
            >
              <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, fontWeight: '500' }}>申请售后</Text>
            </TouchableOpacity>
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

      {/* 申请退款弹窗（必填原因，对齐 Web 端） */}
      <Modal visible={refundOpen} transparent animationType="slide" onRequestClose={() => !actionLoading && setRefundOpen(false)}>
        <TouchableOpacity activeOpacity={1} onPress={() => !actionLoading && setRefundOpen(false)} style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' }}>
          <TouchableOpacity activeOpacity={1} style={{ backgroundColor: theme.bgContainer, borderTopLeftRadius: BorderRadius.xl, borderTopRightRadius: BorderRadius.xl, padding: Spacing.xl }}>
            <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text }}>申请退款</Text>
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: Spacing.xs }}>说明退款原因，提交后由平台审核处理</Text>
            <TextInput
              value={refundReason}
              onChangeText={setRefundReason}
              placeholder="请填写退款原因（必填）"
              placeholderTextColor={theme.textTertiary}
              maxLength={200}
              multiline
              style={{
                marginTop: Spacing.md,
                borderWidth: 1,
                borderColor: theme.border,
                borderRadius: BorderRadius.md,
                paddingHorizontal: Spacing.md,
                paddingVertical: Spacing.sm,
                color: theme.text,
                fontSize: FontSize.sm,
                minHeight: 80,
                textAlignVertical: 'top',
                backgroundColor: theme.bgBase,
              }}
            />
            <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.md, marginTop: Spacing.lg }}>
              <TouchableOpacity
                activeOpacity={0.7}
                onPress={() => setRefundOpen(false)}
                style={{ paddingHorizontal: Spacing.xl, paddingVertical: Spacing.sm, borderRadius: BorderRadius.xl, borderWidth: 1, borderColor: theme.border }}
              >
                <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity
                activeOpacity={0.8}
                onPress={actionLoading ? undefined : submitRefund}
                disabled={actionLoading}
                style={{ paddingHorizontal: Spacing.xl, paddingVertical: Spacing.sm, borderRadius: BorderRadius.xl, backgroundColor: theme.primary, opacity: actionLoading ? 0.6 : 1 }}
              >
                <Text style={{ fontSize: FontSize.sm, color: '#FFFFFF', fontWeight: '600' }}>{actionLoading ? '提交中...' : '提交申请'}</Text>
              </TouchableOpacity>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
      {/* T11/P0-2：申请售后弹窗（类型/商品项/数量/原因/凭证，规则对齐后端） */}
      <Modal visible={afterSaleOpen} transparent animationType="slide" onRequestClose={() => !afterSaleSubmitting && setAfterSaleOpen(false)}>
        <TouchableOpacity
          activeOpacity={1}
          onPress={() => !afterSaleSubmitting && setAfterSaleOpen(false)}
          style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' }}
        >
          <TouchableOpacity activeOpacity={1} style={{ backgroundColor: theme.bgContainer, borderTopLeftRadius: BorderRadius.xl, borderTopRightRadius: BorderRadius.xl, padding: Spacing.xl }}>
            <ScrollView showsVerticalScrollIndicator={false} style={{ maxHeight: 480 }}>
              <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text }}>申请售后</Text>
              <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: Spacing.xs }}>提交后由平台审核受理，进度可在「我的售后」查看</Text>

              <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500', marginTop: Spacing.lg, marginBottom: Spacing.sm }}>售后类型</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
                {(['REFUND_ONLY', ...(order?.status === 2 ? (['RETURN_REFUND'] as const) : [])] as const).map((t) => (
                  <TouchableOpacity
                    key={t}
                    activeOpacity={0.7}
                    onPress={() => selectAfterSaleType(t)}
                    style={{
                      paddingHorizontal: Spacing.lg,
                      paddingVertical: Spacing.sm,
                      borderRadius: BorderRadius.full,
                      borderWidth: 1,
                      borderColor: afterSaleType === t ? theme.primary : theme.border,
                      backgroundColor: afterSaleType === t ? theme.primary : 'transparent',
                    }}
                  >
                    <Text style={{ fontSize: FontSize.sm, color: afterSaleType === t ? '#FFFFFF' : theme.textSecondary }}>
                      {AFTER_SALE_TYPE_TEXT[t]}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>

              <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500', marginTop: Spacing.lg, marginBottom: Spacing.sm }}>售后商品</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
                <TouchableOpacity
                  activeOpacity={0.7}
                  onPress={() => afterSaleType === 'REFUND_ONLY' && selectAfterSaleItem(null)}
                  disabled={afterSaleType === 'RETURN_REFUND'}
                  style={{
                    paddingHorizontal: Spacing.lg,
                    paddingVertical: Spacing.sm,
                    borderRadius: BorderRadius.full,
                    borderWidth: 1,
                    borderColor: afterSaleItemId === null ? theme.primary : theme.border,
                    backgroundColor: afterSaleItemId === null ? theme.primary : 'transparent',
                    opacity: afterSaleType === 'RETURN_REFUND' ? 0.4 : 1,
                  }}
                >
                  <Text style={{ fontSize: FontSize.sm, color: afterSaleItemId === null ? '#FFFFFF' : theme.textSecondary }}>整单</Text>
                </TouchableOpacity>
                {order?.items.map((item) => (
                  <TouchableOpacity
                    key={item.id}
                    activeOpacity={0.7}
                    onPress={() => selectAfterSaleItem(item.id)}
                    style={{
                      paddingHorizontal: Spacing.lg,
                      paddingVertical: Spacing.sm,
                      borderRadius: BorderRadius.full,
                      borderWidth: 1,
                      borderColor: afterSaleItemId === item.id ? theme.primary : theme.border,
                      backgroundColor: afterSaleItemId === item.id ? theme.primary : 'transparent',
                      maxWidth: 180,
                    }}
                  >
                    <Text numberOfLines={1} style={{ fontSize: FontSize.sm, color: afterSaleItemId === item.id ? '#FFFFFF' : theme.textSecondary }}>
                      {item.productName}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>

              {afterSaleItemId != null && (
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Spacing.lg }}>
                  <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500' }}>售后数量</Text>
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.md }}>
                    <TouchableOpacity
                      activeOpacity={0.7}
                      onPress={() => setAfterSaleQuantity((q) => Math.max(1, q - 1))}
                      style={{ width: 32, height: 32, borderRadius: BorderRadius.sm, borderWidth: 1, borderColor: theme.border, alignItems: 'center', justifyContent: 'center' }}
                    >
                      <Text style={{ fontSize: FontSize.lg, color: theme.text }}>−</Text>
                    </TouchableOpacity>
                    <Text style={{ fontSize: FontSize.md, color: theme.text, minWidth: 28, textAlign: 'center' }}>{afterSaleQuantity}</Text>
                    <TouchableOpacity
                      activeOpacity={0.7}
                      onPress={() => {
                        const item = order?.items.find((it) => it.id === afterSaleItemId)
                        setAfterSaleQuantity((q) => Math.min(item?.quantity ?? 1, q + 1))
                      }}
                      style={{ width: 32, height: 32, borderRadius: BorderRadius.sm, borderWidth: 1, borderColor: theme.border, alignItems: 'center', justifyContent: 'center' }}
                    >
                      <Text style={{ fontSize: FontSize.lg, color: theme.text }}>＋</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              )}

              <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500', marginTop: Spacing.lg, marginBottom: Spacing.sm }}>售后原因（必填）</Text>
              <TextInput
                value={afterSaleReason}
                onChangeText={setAfterSaleReason}
                placeholder="请描述售后原因"
                placeholderTextColor={theme.textTertiary}
                maxLength={200}
                multiline
                style={{
                  borderWidth: 1,
                  borderColor: theme.border,
                  borderRadius: BorderRadius.md,
                  paddingHorizontal: Spacing.md,
                  paddingVertical: Spacing.sm,
                  color: theme.text,
                  fontSize: FontSize.sm,
                  minHeight: 80,
                  textAlignVertical: 'top',
                  backgroundColor: theme.bgBase,
                }}
              />

              <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500', marginTop: Spacing.lg, marginBottom: Spacing.sm }}>凭证图片（选填，最多 3 张）</Text>
              <TouchableOpacity
                activeOpacity={0.7}
                onPress={chooseAttachment}
                style={{
                  alignSelf: 'flex-start',
                  paddingHorizontal: Spacing.lg,
                  paddingVertical: Spacing.sm,
                  borderRadius: BorderRadius.md,
                  borderWidth: 1,
                  borderColor: theme.border,
                  borderStyle: 'dashed',
                }}
              >
                <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>
                  {afterSaleFileIds.length > 0 ? `已上传 ${afterSaleFileIds.length} 张 · 继续添加` : '＋ 选择图片'}
                </Text>
              </TouchableOpacity>
            </ScrollView>

            <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.md, marginTop: Spacing.lg }}>
              <TouchableOpacity
                activeOpacity={0.7}
                onPress={() => setAfterSaleOpen(false)}
                style={{ paddingHorizontal: Spacing.xl, paddingVertical: Spacing.sm, borderRadius: BorderRadius.xl, borderWidth: 1, borderColor: theme.border }}
              >
                <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity
                activeOpacity={0.8}
                onPress={afterSaleSubmitting ? undefined : submitAfterSale}
                disabled={afterSaleSubmitting}
                style={{ paddingHorizontal: Spacing.xl, paddingVertical: Spacing.sm, borderRadius: BorderRadius.xl, backgroundColor: theme.primary, opacity: afterSaleSubmitting ? 0.6 : 1 }}
              >
                <Text style={{ fontSize: FontSize.sm, color: '#FFFFFF', fontWeight: '600' }}>{afterSaleSubmitting ? '提交中...' : '提交申请'}</Text>
              </TouchableOpacity>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </View>
  )
}