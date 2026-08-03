import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, Alert } from 'react-native'
import { useState, useEffect, useCallback, useRef } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { orderApi } from '@/api/order'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { Order } from '@/types'

type PaymentMethod = 'ALIPAY' | 'WECHAT' | 'BANK_CARD'
type PaymentStatus = 'IDLE' | 'PENDING' | 'SUCCESS' | 'FAILED'

const PAYMENT_METHODS: { key: PaymentMethod; label: string; icon: string }[] = [
  { key: 'ALIPAY', label: '支付宝', icon: '💳' },
  { key: 'WECHAT', label: '微信支付', icon: '💬' },
  { key: 'BANK_CARD', label: '银行卡', icon: '🏦' },
]

const COUNTDOWN_SECONDS = 15 * 60

export default function PaymentPage() {
  const theme = useTheme()
  const { id } = useLocalSearchParams<{ id: string }>()

  const [order, setOrder] = useState<Order | null>(null)
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('ALIPAY')
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>('IDLE')
  const [loading, setLoading] = useState(true)
  const [paying, setPaying] = useState(false)
  const [countdown, setCountdown] = useState(0)
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const fetchOrder = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const res = await orderApi.getDetail(Number(id))
      const data = res.data as { data?: Order }
      if (data?.data) {
        setOrder(data.data)
        const createdAt = new Date(data.data.createdAt).getTime()
        const elapsed = Math.floor((Date.now() - createdAt) / 1000)
        const remaining = Math.max(0, COUNTDOWN_SECONDS - elapsed)
        setCountdown(remaining)
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

  useEffect(() => {
    if (countdown <= 0) return
    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timer)
          return 0
        }
        return prev - 1
      })
    }, 1000)
    return () => clearInterval(timer)
  }, [countdown > 0])

  const startPolling = useCallback(() => {
    if (pollTimerRef.current) clearInterval(pollTimerRef.current)
    pollTimerRef.current = setInterval(async () => {
      try {
        const res = await orderApi.getPayment(Number(id))
        const paymentData = res.data as { data?: { status: string } }
        const status = paymentData?.data?.status
        if (status === 'SUCCESS') {
          setPaymentStatus('SUCCESS')
          stopPolling()
        } else if (status === 'FAILED') {
          setPaymentStatus('FAILED')
          stopPolling()
        }
      } catch {
        setPaymentStatus('FAILED')
        stopPolling()
      }
    }, 3000)
  }, [id])

  const stopPolling = useCallback(() => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current)
      pollTimerRef.current = null
    }
  }, [])

  useEffect(() => {
    return () => stopPolling()
  }, [stopPolling])

  const handlePay = () => {
    if (!order) return
    Alert.alert('确认支付', `使用${PAYMENT_METHODS.find((m) => m.key === paymentMethod)?.label}支付 ¥${order.payAmount.toFixed(2)}？`, [
      { text: '取消', style: 'cancel' },
      {
        text: '确定支付',
        onPress: async () => {
          setPaying(true)
          try {
            await orderApi.pay(Number(id), { paymentMethod })
            setPaymentStatus('PENDING')
            startPolling()
          } catch {
            Alert.alert('错误', '支付请求失败，请重试')
          } finally {
            setPaying(false)
          }
        },
      },
    ])
  }

  const handleRetry = () => {
    setPaymentStatus('IDLE')
    stopPolling()
  }

  const formatCountdown = (seconds: number): string => {
    const m = Math.floor(seconds / 60)
    const s = seconds % 60
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
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

  if (paymentStatus === 'SUCCESS') {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, justifyContent: 'center', alignItems: 'center', paddingHorizontal: Spacing.xxl }}>
        <Text style={{ fontSize: 64, marginBottom: Spacing.lg }}>✅</Text>
        <Text style={{ fontSize: FontSize.xxl, fontWeight: '700', color: theme.accentGreen, marginBottom: Spacing.sm }}>支付成功</Text>
        <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary, marginBottom: Spacing.xxxl }}>
          ¥{order.payAmount.toFixed(2)}
        </Text>
        <View style={{ flexDirection: 'row', gap: Spacing.lg }}>
          <TouchableOpacity
            activeOpacity={0.7}
            onPress={() => router.replace(`/order-detail?id=${order.id}`)}
            style={{
              paddingHorizontal: Spacing.xxl,
              paddingVertical: Spacing.md,
              borderRadius: BorderRadius.xl,
              borderWidth: 1,
              borderColor: theme.primary,
              backgroundColor: 'transparent',
            }}
          >
            <Text style={{ fontSize: FontSize.md, color: theme.primary, fontWeight: '600' }}>查看订单</Text>
          </TouchableOpacity>
          <TouchableOpacity
            activeOpacity={0.8}
            onPress={() => router.replace('/(tabs)/mall')}
            style={{
              paddingHorizontal: Spacing.xxl,
              paddingVertical: Spacing.md,
              borderRadius: BorderRadius.xl,
              backgroundColor: theme.primary,
            }}
          >
            <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>返回首页</Text>
          </TouchableOpacity>
        </View>
      </View>
    )
  }

  if (paymentStatus === 'FAILED') {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, justifyContent: 'center', alignItems: 'center', paddingHorizontal: Spacing.xxl }}>
        <Text style={{ fontSize: 64, marginBottom: Spacing.lg }}>❌</Text>
        <Text style={{ fontSize: FontSize.xxl, fontWeight: '700', color: theme.accentRed, marginBottom: Spacing.sm }}>支付失败</Text>
        <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, marginBottom: Spacing.xxxl }}>
          请重新尝试支付
        </Text>
        <TouchableOpacity
          activeOpacity={0.8}
          onPress={handleRetry}
          style={{
            paddingHorizontal: Spacing.xxl,
            paddingVertical: Spacing.md,
            borderRadius: BorderRadius.xl,
            backgroundColor: theme.primary,
          }}
        >
          <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>重新支付</Text>
        </TouchableOpacity>
      </View>
    )
  }

  if (paymentStatus === 'PENDING') {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" color={theme.primary} />
        <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary, marginTop: Spacing.lg, fontWeight: '500' }}>
          支付处理中...
        </Text>
        <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.sm }}>
          请稍候，正在确认支付结果
        </Text>
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 100 }}>
        {/* Header */}
        <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: Spacing.lg, paddingTop: Spacing.xxxl, paddingBottom: Spacing.md }}>
          <TouchableOpacity activeOpacity={0.7} onPress={router.back} style={{ width: 36, height: 36, borderRadius: 18, backgroundColor: theme.bgElevated, justifyContent: 'center', alignItems: 'center' }}>
            <Text style={{ fontSize: 18, color: theme.text }}>←</Text>
          </TouchableOpacity>
          <Text style={{ flex: 1, textAlign: 'center', fontSize: FontSize.xl, fontWeight: '600', color: theme.text, marginRight: 36 }}>收银台</Text>
        </View>

        {/* Countdown */}
        {countdown > 0 && (
          <View style={{ marginHorizontal: Spacing.lg, marginBottom: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.lg, alignItems: 'center' }}>
            <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginBottom: Spacing.xs }}>支付剩余时间</Text>
            <Text style={{ fontSize: FontSize.xxl, fontWeight: '700', color: countdown < 300 ? theme.accentRed : theme.primary }}>
              {formatCountdown(countdown)}
            </Text>
          </View>
        )}
        {countdown === 0 && (
          <View style={{ marginHorizontal: Spacing.lg, marginBottom: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.lg, alignItems: 'center' }}>
            <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.accentRed }}>订单已超时</Text>
            <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>请重新下单</Text>
          </View>
        )}

        {/* Order Summary */}
        <View style={{ marginHorizontal: Spacing.lg, marginBottom: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.lg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: Spacing.md }}>
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, fontWeight: '500' }}>订单信息</Text>
            <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>{order.orderNo}</Text>
          </View>

          {order.items.map((item, index) => (
            <View
              key={item.id}
              style={{
                flexDirection: 'row',
                paddingVertical: Spacing.sm,
                borderBottomWidth: index < order.items.length - 1 ? 1 : 0,
                borderBottomColor: theme.border,
              }}
            >
              <Image
                source={{ uri: item.productImage }}
                style={{ width: 56, height: 56, borderRadius: BorderRadius.md, backgroundColor: theme.bgElevated }}
                resizeMode="cover"
              />
              <View style={{ flex: 1, marginLeft: Spacing.md, justifyContent: 'center' }}>
                <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500' }} numberOfLines={1} ellipsizeMode="tail">
                  {item.productName}
                </Text>
                {item.skuName ? (
                  <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: 2 }}>{item.skuName}</Text>
                ) : null}
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: Spacing.xs }}>
                  <Text style={{ fontSize: FontSize.md, color: theme.accentRed, fontWeight: '600' }}>¥{item.price.toFixed(2)}</Text>
                  <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>x{item.quantity}</Text>
                </View>
              </View>
            </View>
          ))}

          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: Spacing.md, paddingTop: Spacing.md, borderTopWidth: 1, borderTopColor: theme.border }}>
            <Text style={{ fontSize: FontSize.lg, color: theme.text, fontWeight: '600' }}>合计</Text>
            <Text style={{ fontSize: FontSize.xxl, color: theme.accentRed, fontWeight: '700' }}>
              ¥{order.payAmount.toFixed(2)}
            </Text>
          </View>
        </View>

        {/* Payment Methods */}
        <View style={{ marginHorizontal: Spacing.lg, marginBottom: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, fontWeight: '500', marginBottom: Spacing.sm }}>
            选择支付方式
          </Text>
          <View style={{ gap: Spacing.md }}>
            {PAYMENT_METHODS.map((method) => {
              const isSelected = paymentMethod === method.key
              return (
                <TouchableOpacity
                  key={method.key}
                  activeOpacity={0.7}
                  onPress={() => setPaymentMethod(method.key)}
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    backgroundColor: theme.bgContainer,
                    borderRadius: BorderRadius.lg,
                    padding: Spacing.lg,
                    borderWidth: 2,
                    borderColor: isSelected ? theme.primary : 'transparent',
                  }}
                >
                  <Text style={{ fontSize: 28, marginRight: Spacing.md }}>{method.icon}</Text>
                  <Text style={{ flex: 1, fontSize: FontSize.lg, color: theme.text, fontWeight: '500' }}>
                    {method.label}
                  </Text>
                  <View style={{
                    width: 22,
                    height: 22,
                    borderRadius: 11,
                    borderWidth: 2,
                    borderColor: isSelected ? theme.primary : theme.border,
                    backgroundColor: isSelected ? theme.primary : 'transparent',
                    justifyContent: 'center',
                    alignItems: 'center',
                  }}>
                    {isSelected && <Text style={{ fontSize: 12, color: '#FFFFFF' }}>✓</Text>}
                  </View>
                </TouchableOpacity>
              )
            })}
          </View>
        </View>
      </ScrollView>

      {/* Bottom Fixed Pay Button */}
      <View style={{
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingHorizontal: Spacing.lg,
        paddingVertical: Spacing.md,
        paddingBottom: Spacing.xl,
        backgroundColor: theme.bgContainer,
        borderTopWidth: 1,
        borderTopColor: theme.border,
      }}>
        <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 4 }}>
          <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500' }}>应付：</Text>
          <Text style={{ fontSize: FontSize.xxl, color: theme.accentRed, fontWeight: '700' }}>
            ¥{order.payAmount.toFixed(2)}
          </Text>
        </View>
        <TouchableOpacity
          onPress={paying || countdown === 0 ? undefined : handlePay}
          activeOpacity={0.8}
          disabled={paying || countdown === 0}
          style={{
            paddingHorizontal: Spacing.xxl,
            paddingVertical: Spacing.md,
            borderRadius: BorderRadius.xl,
            backgroundColor: theme.primary,
            opacity: paying || countdown === 0 ? 0.5 : 1,
            minWidth: 140,
            alignItems: 'center',
          }}
        >
          {paying ? (
            <ActivityIndicator size="small" color="#FFFFFF" />
          ) : (
            <Text style={{ fontSize: FontSize.lg, color: '#FFFFFF', fontWeight: '600' }}>确认支付</Text>
          )}
        </TouchableOpacity>
      </View>
    </View>
  )
}
