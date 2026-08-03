import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, history } from 'umi'
import { Spin, message } from 'antd'
import {
  AlipayCircleOutlined,
  WechatOutlined,
  CreditCardOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  SafetyCertificateOutlined,
  UndoOutlined,
} from '@ant-design/icons'
import { createPayment, getPaymentByOrderId } from '@/api/payment'
import { fetchOrderById } from '@/api/order'
import { type Payment, type PaymentStatus, type Order, PAYMENT_STATUS_LABELS } from '@/types'

const cssVars = {
  '--color-bg-primary': 'var(--color-bg-base)',
  '--color-bg-secondary': 'var(--color-bg-footer)',
  '--color-bg-card': 'var(--color-bg-container)',
  '--color-bg-hover': 'var(--color-bg-elevated)',
  '--color-accent': 'var(--color-primary)',
  '--color-accent-hover': '#33DDFF',
  '--color-accent-dim': 'rgba(var(--color-primary-rgb), 0.15)',
  '--color-accent-glow': 'rgba(var(--color-primary-rgb), 0.3)',
  '--color-text-primary': '#FFFFFF',
  '--color-text-secondary': 'var(--color-text-secondary)',
  '--color-text-tertiary': 'var(--color-text-tertiary)',
  '--color-border': 'var(--color-border)',
  '--color-border-hover': 'rgba(var(--color-primary-rgb), 0.3)',
  '--gradient-accent': 'var(--color-gradient-primary)',
  '--shadow-card': '0 4px 24px rgba(0,0,0,0.3)',
  '--shadow-hover': '0 8px 40px rgba(0,0,0,0.4), 0 0 20px rgba(var(--color-primary-rgb), 0.08)',
  '--radius-sm': '6px',
  '--radius-md': '10px',
  '--radius-lg': '16px',
} as React.CSSProperties

const PAY_METHODS = [
  {
    value: 'ALIPAY',
    label: '支付宝',
    icon: <AlipayCircleOutlined style={{ fontSize: 28, color: '#1677ff' }} />,
    desc: '推荐使用',
  },
  {
    value: 'WECHAT',
    label: '微信支付',
    icon: <WechatOutlined style={{ fontSize: 28, color: '#07c160' }} />,
    desc: '快捷支付',
  },
  {
    value: 'BANK_CARD',
    label: '银行卡',
    icon: <CreditCardOutlined style={{ fontSize: 28, color: 'var(--color-primary)' }} />,
    desc: '储蓄卡/信用卡',
  },
] as const

const COUNTDOWN_SECONDS = 15 * 60

function formatTime(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60).toString().padStart(2, '0')
  const s = (totalSeconds % 60).toString().padStart(2, '0')
  return `${m}:${s}`
}

function PaymentMethodCard({
  method,
  selected,
  onSelect,
}: {
  method: typeof PAY_METHODS[number]
  selected: boolean
  onSelect: () => void
}) {
  return (
    <div
      onClick={onSelect}
      style={{
        padding: '20px 24px',
        background: selected ? 'rgba(var(--color-primary-rgb), 0.06)' : 'var(--color-bg-container)',
        borderRadius: 12,
        border: `1px solid ${selected ? 'rgba(var(--color-primary-rgb), 0.4)' : 'var(--color-border)'}`,
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        boxShadow: selected ? '0 0 20px rgba(var(--color-primary-rgb), 0.1)' : 'none',
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        position: 'relative',
      }}
    >
      {selected && (
        <div
          style={{
            position: 'absolute',
            top: 12,
            right: 12,
            width: 22,
            height: 22,
            borderRadius: '50%',
            background: 'var(--color-gradient-primary)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <CheckCircleOutlined style={{ fontSize: 13, color: 'var(--color-bg-base)' }} />
        </div>
      )}
      <div style={{ flexShrink: 0 }}>{method.icon}</div>
      <div>
        <div style={{ color: 'var(--color-text-secondary)', fontSize: 16, fontWeight: 600, marginBottom: 2 }}>
          {method.label}
        </div>
        <div style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>{method.desc}</div>
      </div>
    </div>
  )
}

function PaymentResult({
  status,
  orderNo,
  paymentStatusLabel,
}: {
  status: PaymentStatus
  orderNo: string
  paymentStatusLabel: string
}) {
  const iconMap: Record<PaymentStatus, React.ReactNode> = {
    SUCCESS: <CheckCircleOutlined style={{ fontSize: 64, color: '#52C41A' }} />,
    FAILED: <CloseCircleOutlined style={{ fontSize: 64, color: '#FF4D4F' }} />,
    REFUNDED: <ExclamationCircleOutlined style={{ fontSize: 64, color: '#FFA940' }} />,
    REFUNDING: <UndoOutlined style={{ fontSize: 64, color: '#FF7A45' }} />,
    PENDING: <ClockCircleOutlined style={{ fontSize: 64, color: 'var(--color-text-secondary)' }} />,
  }

  const titleMap: Record<PaymentStatus, string> = {
    SUCCESS: '支付成功',
    FAILED: '支付失败',
    REFUNDED: '已退款',
    REFUNDING: '退款中',
    PENDING: '',
  }

  const colorMap: Record<PaymentStatus, string> = {
    SUCCESS: '#52C41A',
    FAILED: '#FF4D4F',
    REFUNDED: '#FFA940',
    REFUNDING: '#FF7A45',
    PENDING: 'var(--color-text-secondary)',
  }

  return (
    <div
      style={{
        maxWidth: 500,
        margin: '60px auto',
        textAlign: 'center',
      }}
    >
      <div
        style={{
          background: 'var(--color-bg-container)',
          borderRadius: 16,
          border: '1px solid var(--color-border)',
          boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
          padding: '60px 40px',
        }}
      >
        <div style={{ marginBottom: 24 }}>{iconMap[status]}</div>
        <h2
          style={{
            color: colorMap[status],
            fontSize: 28,
            fontWeight: 700,
            margin: '0 0 12px',
          }}
        >
          {titleMap[status]}
        </h2>
        <div style={{ color: 'var(--color-text-secondary)', fontSize: 14, marginBottom: 40 }}>
          订单号：{orderNo} | 支付状态：{paymentStatusLabel}
        </div>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
          <button
            onClick={() => history.push('/orders')}
            style={{
              padding: '10px 32px',
              fontSize: 14,
              fontWeight: 600,
              color: 'var(--color-text-secondary)',
              background: 'transparent',
              border: '1px solid rgba(255,255,255,0.15)',
              borderRadius: 8,
              cursor: 'pointer',
              transition: 'all 0.2s',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
              e.currentTarget.style.color = 'var(--color-primary)'
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = 'rgba(255,255,255,0.15)'
              e.currentTarget.style.color = 'var(--color-text-secondary)'
            }}
          >
            查看订单
          </button>
          <button
            onClick={() => history.push('/')}
            style={{
              padding: '10px 32px',
              fontSize: 14,
              fontWeight: 600,
              color: 'var(--color-bg-base)',
              background: 'var(--color-gradient-primary)',
              border: 'none',
              borderRadius: 8,
              cursor: 'pointer',
              boxShadow: '0 0 20px rgba(var(--color-primary-rgb), 0.3)',
              transition: 'all 0.2s',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.boxShadow = '0 0 32px rgba(var(--color-primary-rgb), 0.5)'
              e.currentTarget.style.transform = 'translateY(-1px)'
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.boxShadow = '0 0 20px rgba(var(--color-primary-rgb), 0.3)'
              e.currentTarget.style.transform = 'translateY(0)'
            }}
          >
            返回首页
          </button>
        </div>
      </div>
    </div>
  )
}

export default function PaymentPage() {
  const { id } = useParams<{ id: string }>()
  const orderId = Number(id)

  const [order, setOrder] = useState<Order | null>(null)
  const [payment, setPayment] = useState<Payment | null>(null)
  const [payMethod, setPayMethod] = useState<string>('ALIPAY')
  const [loading, setLoading] = useState(true)
  const [paying, setPaying] = useState(false)
  const [remaining, setRemaining] = useState(COUNTDOWN_SECONDS)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const { data: orderRes } = await fetchOrderById(orderId)
      setOrder(orderRes.data)
      try {
        const { data: paymentRes } = await getPaymentByOrderId(orderId)
        setPayment(paymentRes.data)
      } catch {
        setPayment(null)
      }
    } catch {
      message.error('加载订单失败')
    } finally {
      setLoading(false)
    }
  }, [orderId])

  useEffect(() => {
    loadData()
  }, [loadData])

  useEffect(() => {
    if (payment?.status && payment.status !== 'PENDING') return
    timerRef.current = setInterval(() => {
      setRemaining((prev) => {
        if (prev <= 1) {
          if (timerRef.current) clearInterval(timerRef.current)
          return 0
        }
        return prev - 1
      })
    }, 1000)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [payment?.status])

  const handlePay = async () => {
    if (!order) return
    setPaying(true)
    try {
      const { data: res } = await createPayment({
        orderId: order.id,
        amount: order.payAmount,
        payMethod,
      })
      setPayment(res.data)
      message.success('支付请求已提交')
      const pollTimer = setInterval(async () => {
        try {
          const { data: pollRes } = await getPaymentByOrderId(orderId)
          setPayment(pollRes.data)
          if (pollRes.data.status !== 'PENDING') {
            clearInterval(pollTimer)
          }
        } catch {
          clearInterval(pollTimer)
        }
      }, 3000)
    } catch {
      message.error('支付失败，请重试')
    } finally {
      setPaying(false)
    }
  }

  if (loading) {
    return (
      <div
        style={{
          ...cssVars,
          background: 'var(--color-bg-base)',
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Spin size="large" />
      </div>
    )
  }

  if (!order) {
    return (
      <div
        style={{
          ...cssVars,
          background: 'var(--color-bg-base)',
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <ExclamationCircleOutlined style={{ fontSize: 48, color: 'rgba(var(--color-primary-rgb), 0.3)', marginBottom: 16 }} />
        <div style={{ color: 'var(--color-text-secondary)', fontSize: 18, marginBottom: 24 }}>订单不存在</div>
        <button
          onClick={() => history.push('/orders')}
          style={{
            padding: '10px 32px',
            background: 'var(--color-gradient-primary)',
            color: 'var(--color-bg-base)',
            border: 'none',
            borderRadius: 8,
            fontSize: 14,
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          返回订单列表
        </button>
      </div>
    )
  }

  if (payment && payment.status !== 'PENDING') {
    return (
      <div style={{ ...cssVars, background: 'var(--color-bg-base)', minHeight: '100vh', padding: '32px 24px' }}>
        <PaymentResult
          status={payment.status}
          orderNo={order.orderNo}
          paymentStatusLabel={PAYMENT_STATUS_LABELS[payment.status]}
        />
      </div>
    )
  }

  const isExpired = remaining <= 0

  return (
    <div style={{ ...cssVars, background: 'var(--color-bg-base)', minHeight: '100vh', padding: '32px 24px' }}>
      <div style={{ maxWidth: 600, margin: '0 auto' }}>
        <div
          style={{
            background: 'var(--color-bg-container)',
            borderRadius: 16,
            border: '1px solid var(--color-border)',
            boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
            overflow: 'hidden',
            marginBottom: 24,
          }}
        >
          <div
            style={{
              padding: '24px 32px',
              borderBottom: '1px solid var(--color-border)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <div>
              <h2 style={{ margin: '0 0 4px', fontSize: 22, fontWeight: 700, color: 'var(--color-text-secondary)' }}>
                订单支付
              </h2>
              <span style={{ color: 'var(--color-text-tertiary)', fontSize: 13, fontFamily: 'monospace' }}>
                {order.orderNo}
              </span>
            </div>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '6px 14px',
                background: isExpired ? 'rgba(255,77,79,0.1)' : 'rgba(255,169,64,0.1)',
                borderRadius: 20,
              }}
            >
              <ClockCircleOutlined style={{ color: isExpired ? '#FF4D4F' : '#FFA940', fontSize: 14 }} />
              <span style={{ color: isExpired ? '#FF4D4F' : '#FFA940', fontSize: 13, fontWeight: 600, fontFamily: 'monospace' }}>
                {isExpired ? '已超时' : formatTime(remaining)}
              </span>
            </div>
          </div>

          <div style={{ padding: '24px 32px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>商品</span>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 14, maxWidth: 300, textAlign: 'right', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {order.items.map((item) => item.productName).join('、')}
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>订单金额</span>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>¥{order.totalAmount.toFixed(2)}</span>
            </div>
            {order.discountAmount > 0 && (
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>优惠金额</span>
                <span style={{ color: 'var(--color-primary)', fontSize: 14 }}>-¥{order.discountAmount.toFixed(2)}</span>
              </div>
            )}

            <div
              style={{
                height: 1,
                background: 'var(--color-border)',
                margin: '16px -32px',
              }}
            />

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 16, fontWeight: 600 }}>应付金额</span>
              <span style={{ color: 'var(--color-primary)', fontSize: 36, fontWeight: 800 }}>
                ¥{order.payAmount.toFixed(2)}
              </span>
            </div>
          </div>
        </div>

        <div
          style={{
            background: 'var(--color-bg-container)',
            borderRadius: 16,
            border: '1px solid var(--color-border)',
            boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
            overflow: 'hidden',
            marginBottom: 24,
          }}
        >
          <div
            style={{
              padding: '20px 32px',
              borderBottom: '1px solid var(--color-border)',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
          >
            <SafetyCertificateOutlined style={{ color: 'var(--color-primary)', fontSize: 16 }} />
            <span style={{ color: 'var(--color-text-secondary)', fontSize: 16, fontWeight: 600 }}>选择支付方式</span>
          </div>

          <div style={{ padding: '20px 32px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            {PAY_METHODS.map((method) => (
              <PaymentMethodCard
                key={method.value}
                method={method}
                selected={payMethod === method.value}
                onSelect={() => setPayMethod(method.value)}
              />
            ))}
          </div>
        </div>

        <button
          onClick={handlePay}
          disabled={paying || isExpired}
          style={{
            width: '100%',
            padding: '16px 0',
            fontSize: 18,
            fontWeight: 700,
            color: paying || isExpired ? 'var(--color-text-tertiary)' : 'var(--color-bg-base)',
            background: paying || isExpired
              ? 'var(--color-bg-footer)'
              : 'var(--color-gradient-primary)',
            border: 'none',
            borderRadius: 12,
            cursor: paying || isExpired ? 'not-allowed' : 'pointer',
            boxShadow: paying || isExpired ? 'none' : '0 0 30px rgba(var(--color-primary-rgb), 0.3)',
            transition: 'all 0.3s ease',
            letterSpacing: 2,
          }}
          onMouseEnter={(e) => {
            if (!paying && !isExpired) {
              e.currentTarget.style.boxShadow = '0 0 40px rgba(var(--color-primary-rgb), 0.5)'
              e.currentTarget.style.transform = 'translateY(-2px)'
            }
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.boxShadow = paying || isExpired
              ? 'none'
              : '0 0 30px rgba(var(--color-primary-rgb), 0.3)'
            e.currentTarget.style.transform = 'translateY(0)'
          }}
        >
          {paying ? '支付中...' : isExpired ? '支付超时' : `确认支付 ¥${order.payAmount.toFixed(2)}`}
        </button>

        <div
          style={{
            textAlign: 'center',
            marginTop: 16,
            color: 'var(--color-text-tertiary)',
            fontSize: 12,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 4,
          }}
        >
          <SafetyCertificateOutlined style={{ fontSize: 12 }} />
          <span>支付环境安全，请放心支付</span>
        </div>
      </div>
    </div>
  )
}
