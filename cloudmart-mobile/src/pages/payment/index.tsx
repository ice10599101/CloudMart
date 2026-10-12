import { useState, useEffect, useRef, useCallback } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { orderApi } from '@/api/order'
import { paymentApi, newPaymentRequestKey, type PaymentChannel, type PaymentScene } from '@/api/payment'
import { errorMessage } from '@/utils/request'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

type PaymentStatus = 'IDLE' | 'PENDING' | 'SUCCESS' | 'FAILED'

interface OrderItem {
  id: number
  productId: number
  productName: string
  productImage: string
  skuName: string
  price: number
  quantity: number
}

interface OrderDetail {
  id: number
  orderNo: string
  status: number
  totalAmount: number
  shippingFee: number
  payAmount: number
  /** R11：零元单标记（false 时无需渠道支付） */
  paymentRequired?: boolean
  items: OrderItem[]
  createdAt: string
}

interface PaymentInfo {
  status: string
  paymentMethod?: string
  paidAt?: string
}

// R09：渠道列表唯一来自 GET /payment/channels——本字典仅提供端上展示素材（图标/描述），
// 未在后端白名单（凭据已配置）的渠道不渲染，禁止本地写死假按钮。
const PAY_METHOD_META: Record<string, { desc: string; icon: string; iconClass: string }> = {
  ALIPAY: { desc: '推荐使用', icon: '💳', iconClass: styles.methodIconAlipay },
  WECHAT: { desc: '微信安全支付', icon: '💬', iconClass: styles.methodIconWechat },
  BANK_CARD: { desc: '储蓄卡/信用卡', icon: '🏦', iconClass: styles.methodIconBank },
  MOCK: { desc: '测试环境专用', icon: '🧪', iconClass: styles.methodIconBank },
}

const FALLBACK_METHOD_META = { desc: '在线支付', icon: '🏦', iconClass: styles.methodIconBank }

/** R09：Taro H5 构建=H5 场景，微信小程序构建=MINIAPP 场景 */
const PAYMENT_SCENE: PaymentScene = Taro.getEnv() === Taro.ENV_TYPE.WEB ? 'H5' : 'MINIAPP'

const COUNTDOWN_SECONDS = 15 * 60

function formatCountdown(seconds: number): string {
  if (seconds <= 0) return '00:00'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export default function PaymentPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()

  const id = Taro.getCurrentInstance().router?.params?.id || ''
  const [order, setOrder] = useState<OrderDetail | null>(null)
  const [, setPaymentInfo] = useState<PaymentInfo | null>(null)
  // R09：可用渠道唯一来自 GET /payment/channels（空=暂不可支付，不展示假按钮）
  const [channels, setChannels] = useState<PaymentChannel[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedMethod, setSelectedMethod] = useState<string>('')
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>('IDLE')
  const [countdown, setCountdown] = useState(0)
  const [paying, setPaying] = useState(false)
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const pollCancelledRef = useRef(false)
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const loadOrder = useCallback(async () => {
    if (!id) return
    try {
      setLoading(true)
      const [orderRes, channelsRes] = await Promise.all([
        orderApi.getDetail(id),
        // R09：渠道查询失败按"暂不可支付"处理，绝不回退本地写死列表
        paymentApi.getChannels(PAYMENT_SCENE).catch(() => null),
      ])
      setOrder(orderRes.data?.data as unknown as OrderDetail)
      const availableChannels = channelsRes?.data?.data ?? []
      setChannels(availableChannels)
      setSelectedMethod((prev) => {
        const stillAvailable = availableChannels.find((c) => c.channel === prev && !c.maintenance)
        if (stillAvailable) return prev
        return availableChannels.find((c) => !c.maintenance)?.channel ?? ''
      })
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '加载失败'), icon: 'none' })
    } finally {
      setLoading(false)
    }
  }, [id])

  const loadPaymentInfo = useCallback(async () => {
    if (!id) return
    try {
      // T01：收银台状态唯一真值源为 payment_attempt 台账（旧 order 侧支付视图已删除）
      const res = await paymentApi.getByOrder(id)
      setPaymentInfo(res.data?.data as PaymentInfo)
    } catch {
      // payment info may not exist yet
    }
  }, [id])

  useEffect(() => {
    loadOrder()
  }, [loadOrder])

  // Countdown timer
  useEffect(() => {
    if (!order?.createdAt) return

    const createdTime = new Date(order.createdAt).getTime()
    const expireTime = createdTime + COUNTDOWN_SECONDS * 1000

    const updateCountdown = () => {
      const remaining = Math.max(0, Math.floor((expireTime - Date.now()) / 1000))
      setCountdown(remaining)
      if (remaining <= 0 && countdownRef.current) {
        clearInterval(countdownRef.current)
        countdownRef.current = null
      }
    }

    updateCountdown()
    countdownRef.current = setInterval(updateCountdown, 1000)

    return () => {
      if (countdownRef.current) {
        clearInterval(countdownRef.current)
        countdownRef.current = null
      }
    }
  }, [order?.createdAt])

  // Poll payment status
  const startPolling = useCallback(() => {
    if (pollTimerRef.current) clearInterval(pollTimerRef.current)
    pollTimerRef.current = null
    // FE-03：串行轮询（await 查单→延迟→再查，不重叠）；查询失败退避续查不误报；
    // cancelled ref 取消（类型安全，卸载/终态退出）。按发起链路打各自真值源。
    pollCancelledRef.current = false
    const loop = async () => {
      for (let attempt = 0; attempt < 100 && !pollCancelledRef.current; attempt++) {
        try {
          // T01：真值源为 payment-attempts/order/{id}（唯一支付链路）
          const res = await paymentApi.getByOrder(id)
          const status = res.data?.data?.status
          if (pollCancelledRef.current) return
          if (status === 'SUCCESS') {
            setPaymentStatus('SUCCESS')
            return
          }
          if (status === 'FAILED' || status === 'CLOSED') {
            // 终态失败：允许用户重新发起，不误报也不无限轮询
            setPaymentStatus('FAILED')
            return
          }
        } catch {
          // 退避：弱网恢复后继续查单
        }
        await new Promise((r) => {
          setTimeout(r, 3000)
        })
      }
    }
    void loop()
  }, [id])

  // Cleanup polling on unmount
  useEffect(() => {
    return () => {
      pollCancelledRef.current = true
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current)
        pollTimerRef.current = null
      }
    }
  }, [])

  const selectedChannel = channels.find((c) => c.channel === selectedMethod && !c.maintenance) ?? null
  const hasSelectableChannel = channels.some((c) => !c.maintenance)

  const handlePay = () => {
    // R11：零元单建单即内部结算完成，收银台不发起收费渠道支付（fail-closed，防深链误入）
    if (order?.paymentRequired === false) {
      Taro.showToast({ title: '该订单无需支付（已结算）', icon: 'none' })
      setTimeout(() => Taro.redirectTo({ url: `/pages/orderDetail/index?id=${id}` }), 800)
      return
    }
    // R09：只允许提交服务端返回的、非维护中的渠道
    if (!selectedChannel) {
      Taro.showToast({ title: '当前暂无可支付渠道', icon: 'none' })
      return
    }
    Taro.showModal({
      title: '确认支付',
      content: `确认使用${selectedChannel.name}支付 ¥${order?.payAmount ?? '0.00'}？`,
      success: async (res) => {
        if (!res.confirm) return

        setPaying(true)
        try {
          // T01/R09：唯一支付链路——只提交 orderId/channel/scene/requestKey，金额服务端判定；
          // requestKey 每次主动发起重新生成（换渠道=新意图，重发同键）；
          // H5 REDIRECT 才带回跳地址（浏览器环境，服务端白名单校验）
          const h5ReturnUrl =
            PAYMENT_SCENE === 'H5' && selectedChannel.action === 'REDIRECT' && typeof window !== 'undefined'
              ? window.location.href
              : undefined
          const attemptRes = await paymentApi.createAttempt({
            orderId: id,
            channel: selectedChannel.channel,
            scene: PAYMENT_SCENE,
            requestKey: newPaymentRequestKey(),
            returnUrl: h5ReturnUrl,
          })
          if (attemptRes.data?.data?.replacedPrevious) {
            Taro.showToast({ title: '已切换支付渠道，原尝试已关闭', icon: 'none' })
          }
          const mockCallback = attemptRes.data?.data?.mockCallback
          if (mockCallback) {
            // MOCK 渠道（测试环境）：代渠道提交签名回调，入账结果以轮询查单为准
            await paymentApi.submitMockCallback(mockCallback)
          }
          setPaymentStatus('PENDING')
          startPolling()
        } catch (err) {
          Taro.showToast({ title: errorMessage(err, '支付发起失败'), icon: 'none' })
          setPaymentStatus('FAILED')
        } finally {
          setPaying(false)
        }
      },
    })
  }

  const handleViewOrder = () => {
    Taro.redirectTo({ url: `/pages/orderDetail/index?id=${id}` })
  }

  const handleBackHome = () => {
    Taro.switchTab({ url: '/pages/home/index' })
  }

  const handleRetry = () => {
    setPaymentStatus('IDLE')
    setPaymentInfo(null)
    loadPaymentInfo()
  }

  // ========== Loading ==========
  if (loading) {
    return (
      <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
        <View className={styles.loading}>
          <View className={styles.spinner} />
          <Text className={styles.loadingText}>加载中</Text>
        </View>
      </View>
    )
  }

  if (!order) {
    return (
      <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
        <View className={styles.loading}>
          <Text className={styles.resultIcon}>📋</Text>
          <Text className={styles.resultTitle}>订单不存在</Text>
        </View>
      </View>
    )
  }

  // ========== Payment Result: SUCCESS ==========
  if (paymentStatus === 'SUCCESS') {
    return (
      <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
        <View className={styles.resultContainer}>
          <Text className={styles.resultIcon}>✅</Text>
          <Text className={styles.resultTitle}>支付成功</Text>
          <Text className={styles.resultSubtitle}>感谢您的购买</Text>
          <View className={styles.resultActions}>
            <View className={styles.resultBtnSecondary} onClick={handleBackHome}>
              <Text className={styles.resultBtnSecondaryText}>返回首页</Text>
            </View>
            <View className={styles.resultBtnPrimary} onClick={handleViewOrder}>
              <Text className={styles.resultBtnPrimaryText}>查看订单</Text>
            </View>
          </View>
        </View>
      </View>
    )
  }

  // ========== Payment Result: FAILED ==========
  if (paymentStatus === 'FAILED') {
    return (
      <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
        <View className={styles.resultContainer}>
          <Text className={styles.resultIcon}>❌</Text>
          <Text className={styles.resultTitle}>支付失败</Text>
          <Text className={styles.resultSubtitle}>请检查支付方式后重试</Text>
          <View className={styles.resultActions}>
            <View className={styles.resultBtnSecondary} onClick={handleBackHome}>
              <Text className={styles.resultBtnSecondaryText}>返回首页</Text>
            </View>
            <View className={styles.resultBtnPrimary} onClick={handleRetry}>
              <Text className={styles.resultBtnPrimaryText}>重新支付</Text>
            </View>
          </View>
        </View>
      </View>
    )
  }

  // ========== Payment Result: PENDING ==========
  if (paymentStatus === 'PENDING') {
    return (
      <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
        <View className={styles.pendingContainer}>
          <View className={styles.pendingSpinner} />
          <Text className={styles.pendingText}>支付处理中...</Text>
          <Text className={styles.pendingHint}>请稍候，正在确认支付结果</Text>
        </View>
      </View>
    )
  }

  // ========== Main Payment Page ==========
  const isExpired = countdown <= 0

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.content}>
        {/* 倒计时区域 */}
        <View className={styles.countdownSection}>
          <Text className={styles.countdownIcon}>⏰</Text>
          <Text className={styles.countdownText}>
            {isExpired ? '支付已超时' : '等待支付'}
          </Text>
          {!isExpired ? (
            <Text className={styles.countdownTimer}>
              剩余时间 {formatCountdown(countdown)}
            </Text>
          ) : (
            <Text className={styles.countdownExpired}>
              订单已超时，请重新下单
            </Text>
          )}
        </View>

        {/* 订单摘要 */}
        <View className={styles.section}>
          <View className={styles.sectionHeader}>
            <Text className={styles.sectionTitle}>订单摘要</Text>
          </View>
          <View className={styles.orderInfo}>
            <View className={styles.infoRow}>
              <Text className={styles.infoLabel}>订单号</Text>
              <Text className={styles.infoValue}>{order.orderNo}</Text>
            </View>
          </View>
          {order.items.map((item) => (
            <View key={item.id} className={styles.orderItem}>
              <Image className={styles.itemImage} src={item.productImage} mode='aspectFill' />
              <View className={styles.itemInfo}>
                <Text className={styles.itemName}>{item.productName}</Text>
                <Text className={styles.itemSku}>{item.skuName}</Text>
                <View className={styles.itemBottom}>
                  <Text className={styles.itemPrice}>¥{item.price}</Text>
                  <Text className={styles.itemQuantity}>x{item.quantity}</Text>
                </View>
              </View>
            </View>
          ))}
          <View className={styles.totalRow}>
            <Text className={styles.totalLabel}>合计</Text>
            <Text className={styles.totalAmount}>¥{order.payAmount}</Text>
          </View>
        </View>

        {/* 支付方式 */}
        <View className={styles.section}>
          <View className={styles.sectionHeader}>
            <Text className={styles.sectionTitle}>支付方式</Text>
          </View>
          <View className={styles.methodList}>
            {channels.length === 0 ? (
              <View className={styles.methodCard}>
                <View className={styles.methodInfo}>
                  <Text className={styles.methodName}>当前环境暂无可支付渠道</Text>
                  <Text className={styles.methodDesc}>支付通道尚未开放或正在维护，请稍后再试或联系客服</Text>
                </View>
              </View>
            ) : (
              channels.map((channel) => {
                const meta = PAY_METHOD_META[channel.channel] ?? FALLBACK_METHOD_META
                const isSelected = selectedMethod === channel.channel
                return (
                  <View
                    key={channel.channel}
                    className={`${styles.methodCard} ${isSelected ? styles.selected : ''}`}
                    onClick={channel.maintenance ? undefined : () => setSelectedMethod(channel.channel)}
                    style={channel.maintenance ? { opacity: 0.5 } : undefined}
                  >
                    <View className={`${styles.methodIcon} ${meta.iconClass}`}>
                      <Text>{meta.icon}</Text>
                    </View>
                    <View className={styles.methodInfo}>
                      <Text className={styles.methodName}>
                        {channel.name}{channel.maintenance ? '（维护中）' : ''}
                      </Text>
                      <Text className={styles.methodDesc}>{meta.desc}</Text>
                    </View>
                    <View className={`${styles.methodCheck} ${isSelected ? styles.checked : ''}`}>
                      {isSelected && <Text className={styles.checkIcon}>✓</Text>}
                    </View>
                  </View>
                )
              })
            )}
          </View>
        </View>
      </ScrollView>

      {/* 底部支付按钮 */}
      <View className={styles.bottomBar}>
        <View className={styles.payAmountInfo}>
          <Text className={styles.payAmountLabel}>支付金额</Text>
          <Text className={styles.payAmount}>¥{order.payAmount}</Text>
        </View>
        <View
          className={`${styles.payBtn} ${isExpired || paying || !hasSelectableChannel ? styles.disabled : ''}`}
          onClick={isExpired || paying || !hasSelectableChannel ? undefined : handlePay}
        >
          <Text className={styles.payBtnText}>
            {paying
              ? '支付中...'
              : isExpired
                ? '订单已超时'
                : hasSelectableChannel
                  ? '确认支付'
                  : '暂无可支付渠道'}
          </Text>
        </View>
      </View>
    </View>
  )
}
