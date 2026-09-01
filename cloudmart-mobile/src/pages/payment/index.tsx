import { useState, useEffect, useRef, useCallback } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { orderApi } from '@/api/order'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

type PaymentMethod = 'ALIPAY' | 'WECHAT' | 'BANK_CARD'
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
  items: OrderItem[]
  createdAt: string
}

interface PaymentInfo {
  status: string
  paymentMethod?: string
  paidAt?: string
}

const PAYMENT_METHODS: Array<{
  key: PaymentMethod
  name: string
  desc: string
  icon: string
  iconClass: string
}> = [
  { key: 'ALIPAY', name: '支付宝', desc: '推荐使用', icon: '💳', iconClass: styles.methodIconAlipay },
  { key: 'WECHAT', name: '微信支付', desc: '微信安全支付', icon: '💬', iconClass: styles.methodIconWechat },
  { key: 'BANK_CARD', name: '银行卡', desc: '储蓄卡/信用卡', icon: '🏦', iconClass: styles.methodIconBank },
]

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
  const [loading, setLoading] = useState(true)
  const [selectedMethod, setSelectedMethod] = useState<PaymentMethod>('ALIPAY')
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>('IDLE')
  const [countdown, setCountdown] = useState(0)
  const [paying, setPaying] = useState(false)
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const loadOrder = useCallback(async () => {
    if (!id) return
    try {
      setLoading(true)
      const res = await orderApi.getDetail(id)
      setOrder(res.data?.data as unknown as OrderDetail)
    } catch {
      Taro.showToast({ title: '加载失败', icon: 'none' })
    } finally {
      setLoading(false)
    }
  }, [id])

  const loadPaymentInfo = useCallback(async () => {
    if (!id) return
    try {
      const res = await orderApi.getPayment(id)
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

    pollTimerRef.current = setInterval(async () => {
      try {
        const res = await orderApi.getPayment(id)
        const info = res.data?.data as PaymentInfo
        setPaymentInfo(info)

        if (info?.status === 'SUCCESS') {
          setPaymentStatus('SUCCESS')
          if (pollTimerRef.current) {
            clearInterval(pollTimerRef.current)
            pollTimerRef.current = null
          }
        } else if (info?.status === 'FAILED') {
          setPaymentStatus('FAILED')
          if (pollTimerRef.current) {
            clearInterval(pollTimerRef.current)
            pollTimerRef.current = null
          }
        }
      } catch {
        // ignore poll errors
      }
    }, 3000)
  }, [id])

  // Cleanup polling on unmount
  useEffect(() => {
    return () => {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current)
        pollTimerRef.current = null
      }
    }
  }, [])

  const handlePay = () => {
    Taro.showModal({
      title: '确认支付',
      content: `确认使用${PAYMENT_METHODS.find((m) => m.key === selectedMethod)?.name}支付 ¥${order?.payAmount ?? '0.00'}？`,
      success: async (res) => {
        if (!res.confirm) return

        setPaying(true)
        try {
          await orderApi.pay(id, { paymentMethod: selectedMethod })
          setPaymentStatus('PENDING')
          startPolling()
        } catch (err: unknown) {
          const message = (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message
          Taro.showToast({ title: message || '支付发起失败', icon: 'none' })
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
            {PAYMENT_METHODS.map((method) => (
              <View
                key={method.key}
                className={`${styles.methodCard} ${selectedMethod === method.key ? styles.selected : ''}`}
                onClick={() => setSelectedMethod(method.key)}
              >
                <View className={`${styles.methodIcon} ${method.iconClass}`}>
                  <Text>{method.icon}</Text>
                </View>
                <View className={styles.methodInfo}>
                  <Text className={styles.methodName}>{method.name}</Text>
                  <Text className={styles.methodDesc}>{method.desc}</Text>
                </View>
                <View className={`${styles.methodCheck} ${selectedMethod === method.key ? styles.checked : ''}`}>
                  {selectedMethod === method.key && <Text className={styles.checkIcon}>✓</Text>}
                </View>
              </View>
            ))}
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
          className={`${styles.payBtn} ${isExpired || paying ? styles.disabled : ''}`}
          onClick={isExpired || paying ? undefined : handlePay}
        >
          <Text className={styles.payBtnText}>{paying ? '支付中...' : '确认支付'}</Text>
        </View>
      </View>
    </View>
  )
}
