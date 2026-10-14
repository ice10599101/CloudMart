import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, Alert } from 'react-native'
import { useState, useEffect, useCallback, useRef } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { orderApi } from '@/api/order'
import { paymentApi, newPaymentRequestKey, type PaymentChannel } from '@/api/payment'
import { errorMessage } from '@/utils/request'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { Order } from '@/types'

type PaymentStatus = 'IDLE' | 'PENDING' | 'SUCCESS' | 'FAILED'

// R09：渠道列表唯一来自 GET /payment/channels?scene=APP——本字典仅提供端上展示素材，
// 未在后端白名单（凭据已配置）的渠道不渲染，禁止本地写死假按钮。
const PAY_METHOD_META: Record<string, { icon: string }> = {
  ALIPAY: { icon: '💳' },
  WECHAT: { icon: '💬' },
  BANK_CARD: { icon: '🏦' },
  MOCK: { icon: '🧪' },
}

const FALLBACK_METHOD_META = { icon: '🏦' }

const COUNTDOWN_SECONDS = 15 * 60

export default function PaymentPage() {
  const theme = useTheme()
  const { id } = useLocalSearchParams<{ id: string }>()

  const [order, setOrder] = useState<Order | null>(null)
  // R09：可用渠道唯一来自 GET /payment/channels?scene=APP（空=暂不可支付，不展示假按钮）
  const [channels, setChannels] = useState<PaymentChannel[]>([])
  const [paymentMethod, setPaymentMethod] = useState<string>('')
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>('IDLE')
  const [loading, setLoading] = useState(true)
  const [paying, setPaying] = useState(false)
  const [countdown, setCountdown] = useState(0)
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const fetchOrder = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      // R09：渠道可用性并行加载；查询失败按"暂不可支付"处理，绝不回退本地写死列表
      const [res, channelsRes] = await Promise.all([
        orderApi.getDetail(id),
        paymentApi.getChannels('APP').catch(() => null),
      ])
      const data = res.data as { data?: Order }
      if (data?.data) {
        setOrder(data.data)
        const createdAt = new Date(data.data.createdAt).getTime()
        const elapsed = Math.floor((Date.now() - createdAt) / 1000)
        const remaining = Math.max(0, COUNTDOWN_SECONDS - elapsed)
        setCountdown(remaining)
      }
      const availableChannels = channelsRes?.data?.data ?? []
      setChannels(availableChannels)
      setPaymentMethod((prev) => {
        const stillAvailable = availableChannels.find((c) => c.channel === prev && !c.maintenance)
        if (stillAvailable) return prev
        return availableChannels.find((c) => !c.maintenance)?.channel ?? ''
      })
    } catch (error) {
      Alert.alert('错误', errorMessage(error, '加载订单失败'))
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    async function run() {
      await fetchOrder()
    }
    void run()
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

  const stopPolling = useCallback(() => {
    const timer = pollTimerRef.current as unknown as { unsubscribe?: () => void } | null
    if (timer?.unsubscribe) {
      timer.unsubscribe()
    }
    pollTimerRef.current = null
  }, [])

  // FE-03：串行轮询（await 查单→延迟→再查，不重叠）；查询失败退避续查不误报
  // FAILED（原弱网一次失败即宣判失败）；限次防无限轮询。
  // T01：真值源为 payment-attempts/order/{id}（ID 保持字符串，禁止 Number(id) 精度损失）
  const startPolling = useCallback(() => {
    if (pollTimerRef.current) clearInterval(pollTimerRef.current)
    let cancelled = false
    const loop = async () => {
      for (let attempt = 0; attempt < 100 && !cancelled; attempt++) {
        try {
          const res = await paymentApi.getByOrder(id)
          if (cancelled) return
          const status = res.data?.data?.status
          if (status === 'SUCCESS') {
            setPaymentStatus('SUCCESS')
            return
          }
          if (status === 'FAILED' || status === 'CLOSED') {
            setPaymentStatus('FAILED')
            return
          }
          // PENDING/RECONCILING：继续轮询直到终态
        } catch {
          // 退避：弱网恢复后继续查单，成功状态只能来自查单结果
        }
        await new Promise((r) => setTimeout(r, 3000))
      }
      if (!cancelled) stopPolling()
    }
    void loop()
    pollTimerRef.current = { unsubscribe: () => { cancelled = true } } as unknown as ReturnType<typeof setInterval>
  }, [id, stopPolling])

  useEffect(() => {
    return () => stopPolling()
  }, [stopPolling])

  const selectedChannel = channels.find((c) => c.channel === paymentMethod && !c.maintenance) ?? null
  const hasSelectableChannel = channels.some((c) => !c.maintenance)

  const handlePay = () => {
    if (!order) return
    // R11：零元单建单即内部结算完成，收银台不发起收费渠道支付（fail-closed，防深链误入）
    if (order.paymentRequired === false) {
      Alert.alert('无需支付', '该订单为全额优惠零元单，已结算完成', [
        { text: '查看订单', onPress: () => router.replace(`/order-detail?id=${id}`) },
      ])
      return
    }
    // R09：只允许提交服务端返回的、非维护中的渠道
    if (!selectedChannel) {
      Alert.alert('暂不可支付', '当前环境暂无可支付渠道，请稍后再试或联系客服')
      return
    }
    Alert.alert('确认支付', `使用${selectedChannel.name}支付 ¥${order.payAmount.toFixed(2)}？`, [
      { text: '取消', style: 'cancel' },
      {
        text: '确定支付',
        onPress: async () => {
          setPaying(true)
          try {
            // T01/R09：唯一支付链路——只提交 orderId/channel/scene/requestKey，金额服务端判定；
            // requestKey 每次主动发起重新生成（换渠道=新意图，重发同键）；APP 走 SDK/NONE，无回跳地址
            const res = await paymentApi.createAttempt({
              orderId: id,
              channel: selectedChannel.channel,
              scene: 'APP',
              requestKey: newPaymentRequestKey(),
            })
            if (res.data?.data?.replacedPrevious) {
              Alert.alert('已切换支付渠道', '原支付尝试已关闭，将使用新渠道继续支付')
            }
            const mockCallback = res.data?.data?.mockCallback
            if (mockCallback) {
              // MOCK 渠道（测试环境）代渠道提交签名回调，入账结果以轮询查单为准
              await paymentApi.submitMockCallback(mockCallback)
            }
            setPaymentStatus('PENDING')
            startPolling()
          } catch (error) {
            Alert.alert('错误', errorMessage(error, '支付请求失败，请重试'))
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
            {channels.length === 0 ? (
              <View style={{
                backgroundColor: theme.bgContainer,
                borderRadius: BorderRadius.lg,
                padding: Spacing.lg,
                borderWidth: 1,
                borderColor: theme.border,
              }}>
                <Text style={{ fontSize: FontSize.lg, color: theme.text, fontWeight: '500', marginBottom: 4 }}>
                  当前环境暂无可支付渠道
                </Text>
                <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>
                  支付通道尚未开放或正在维护，请稍后再试或联系客服
                </Text>
              </View>
            ) : (
              channels.map((channel) => {
                const isSelected = paymentMethod === channel.channel
                const meta = PAY_METHOD_META[channel.channel] ?? FALLBACK_METHOD_META
                return (
                  <TouchableOpacity
                    key={channel.channel}
                    activeOpacity={0.7}
                    disabled={channel.maintenance}
                    onPress={() => setPaymentMethod(channel.channel)}
                    style={{
                      flexDirection: 'row',
                      alignItems: 'center',
                      backgroundColor: theme.bgContainer,
                      borderRadius: BorderRadius.lg,
                      padding: Spacing.lg,
                      borderWidth: 2,
                      borderColor: isSelected ? theme.primary : 'transparent',
                      opacity: channel.maintenance ? 0.5 : 1,
                    }}
                  >
                    <Text style={{ fontSize: 28, marginRight: Spacing.md }}>{meta.icon}</Text>
                    <Text style={{ flex: 1, fontSize: FontSize.lg, color: theme.text, fontWeight: '500' }}>
                      {channel.name}{channel.maintenance ? '（维护中）' : ''}
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
              })
            )}
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
          onPress={paying || countdown === 0 || !hasSelectableChannel ? undefined : handlePay}
          activeOpacity={0.8}
          disabled={paying || countdown === 0 || !hasSelectableChannel}
          style={{
            paddingHorizontal: Spacing.xxl,
            paddingVertical: Spacing.md,
            borderRadius: BorderRadius.xl,
            backgroundColor: theme.primary,
            opacity: paying || countdown === 0 || !hasSelectableChannel ? 0.5 : 1,
            minWidth: 140,
            alignItems: 'center',
          }}
        >
          {paying ? (
            <ActivityIndicator size="small" color="#FFFFFF" />
          ) : (
            <Text style={{ fontSize: FontSize.lg, color: '#FFFFFF', fontWeight: '600' }}>
              {countdown === 0 ? '订单已超时' : hasSelectableChannel ? '确认支付' : '暂无可支付渠道'}
            </Text>
          )}
        </TouchableOpacity>
      </View>
    </View>
  )
}
