import { useState, useEffect } from 'react'
import { Spin, message, Modal, Input } from 'antd'
import {
  EnvironmentOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CarOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  UndoOutlined,
} from '@ant-design/icons'
import { history, useParams } from 'umi'
import { fetchOrderById, cancelOrder, confirmReceipt, requestRefund } from '@/api/order'
import { type Order, type OrderStatus, ORDER_STATUS_LABELS } from '@/types'

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

const STATUS_BANNER_STYLE: Record<string, { color: string; bg: string; icon: React.ReactNode }> = {
  PENDING_PAYMENT: { color: '#FFA940', bg: 'rgba(255,169,64,0.1)', icon: <ClockCircleOutlined /> },
  PAID: { color: 'var(--color-primary)', bg: 'rgba(var(--color-primary-rgb), 0.1)', icon: <CheckCircleOutlined /> },
  SHIPPED: { color: '#36CFC9', bg: 'rgba(54,207,201,0.1)', icon: <CarOutlined /> },
  COMPLETED: { color: '#52C41A', bg: 'rgba(82,196,26,0.1)', icon: <CheckCircleOutlined /> },
  CANCELLED: { color: 'var(--color-text-secondary)', bg: 'rgba(139,157,195,0.1)', icon: <CloseCircleOutlined /> },
  REFUNDING: { color: '#FF7A45', bg: 'rgba(255,122,69,0.1)', icon: <UndoOutlined /> },
  REFUNDED: { color: '#FF4D4F', bg: 'rgba(255,77,79,0.1)', icon: <ExclamationCircleOutlined /> },
}

const STEP_ITEMS = [
  { title: '提交订单', desc: '下单成功' },
  { title: '支付成功', desc: '等待发货' },
  { title: '已发货', desc: '运输中' },
  { title: '已完成', desc: '交易完成' },
]

function getStepIndex(status: OrderStatus): number {
  const map: Record<OrderStatus, number> = {
    PENDING_PAYMENT: 0,
    PAID: 1,
    SHIPPED: 2,
    COMPLETED: 3,
    CANCELLED: -1,
    REFUNDING: -1,
    REFUNDED: -1,
  }
  return map[status]
}

function ActionButton({
  label,
  onClick,
  variant = 'default',
}: {
  label: string
  onClick: () => void
  variant?: 'primary' | 'default' | 'danger'
}) {
  const isPrimary = variant === 'primary'
  const isDanger = variant === 'danger'

  return (
    <button
      onClick={onClick}
      style={{
        padding: '8px 24px',
        fontSize: 14,
        fontWeight: 600,
        color: isPrimary ? 'var(--color-bg-base)' : isDanger ? '#ff4d4f' : 'var(--color-text-secondary)',
        background: isPrimary
          ? 'var(--color-gradient-primary)'
          : 'transparent',
        border: isPrimary
          ? 'none'
          : `1px solid ${isDanger ? 'rgba(255,77,79,0.3)' : 'rgba(255,255,255,0.15)'}`,
        borderRadius: 8,
        cursor: 'pointer',
        transition: 'all 0.2s ease',
      }}
      onMouseEnter={(e) => {
        if (isPrimary) {
          e.currentTarget.style.boxShadow = '0 0 20px rgba(var(--color-primary-rgb), 0.4)'
        } else if (isDanger) {
          e.currentTarget.style.borderColor = 'rgba(255,77,79,0.6)'
          e.currentTarget.style.background = 'rgba(255,77,79,0.08)'
        } else {
          e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
          e.currentTarget.style.color = 'var(--color-primary)'
        }
      }}
      onMouseLeave={(e) => {
        if (isPrimary) {
          e.currentTarget.style.boxShadow = 'none'
        } else if (isDanger) {
          e.currentTarget.style.borderColor = 'rgba(255,77,79,0.3)'
          e.currentTarget.style.background = 'transparent'
        } else {
          e.currentTarget.style.borderColor = 'rgba(255,255,255,0.15)'
          e.currentTarget.style.color = 'var(--color-text-secondary)'
        }
      }}
    >
      {label}
    </button>
  )
}

function SectionCard({ title, icon, children }: { title: string; icon?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div
      style={{
        background: 'var(--color-bg-container)',
        borderRadius: 16,
        border: '1px solid var(--color-border)',
        boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
        overflow: 'hidden',
        marginBottom: 20,
      }}
    >
      <div
        style={{
          padding: '18px 24px',
          borderBottom: '1px solid var(--color-border)',
          display: 'flex',
          alignItems: 'center',
          gap: 10,
        }}
      >
        {icon}
        <span style={{ color: 'var(--color-text-secondary)', fontSize: 16, fontWeight: 600 }}>{title}</span>
      </div>
      <div style={{ padding: '20px 24px' }}>{children}</div>
    </div>
  )
}

export default function OrderDetail() {
  const { id } = useParams<{ id: string }>()
  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(true)
  const [refundModalOpen, setRefundModalOpen] = useState(false)
  const [refundReason, setRefundReason] = useState('')

  const loadOrder = async () => {
    if (!id) return
    setLoading(true)
    try {
      const res = await fetchOrderById(id)
      setOrder(res.data.data)
    } catch {
      message.error('加载订单失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadOrder()
  }, [id])

  const handleCancel = async () => {
    if (!order) return
    try {
      await cancelOrder(order.id)
      message.success('订单已取消')
      loadOrder()
    } catch {
      message.error('操作失败')
    }
  }

  const handlePay = () => {
    if (!order) return
    history.push(`/payment/${order.id}`)
  }

  const handleConfirm = async () => {
    if (!order) return
    try {
      await confirmReceipt(order.id)
      message.success('已确认收货')
      loadOrder()
    } catch {
      message.error('操作失败')
    }
  }

  const handleRefund = async () => {
    if (!order || !refundReason.trim()) {
      message.warning('请填写退款原因')
      return
    }
    try {
      await requestRefund(order.id, refundReason.trim())
      message.success('退款申请已提交')
      setRefundModalOpen(false)
      setRefundReason('')
      loadOrder()
    } catch {
      message.error('操作失败')
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

  const currentStep = getStepIndex(order.status)
  const isCancelled = order.status === 'CANCELLED'
  const isRefunding = order.status === 'REFUNDING' || order.status === 'REFUNDED'
  const bannerStyle = STATUS_BANNER_STYLE[order.status] ?? STATUS_BANNER_STYLE.PENDING_PAYMENT

  const timeline = [
    { label: '创建订单', time: order.createdAt, active: true },
    ...(order.shippedAt ? [{ label: '已发货', time: order.shippedAt, active: true }] : []),
    ...(order.completedAt ? [{ label: '已完成', time: order.completedAt, active: true }] : []),
  ]

  return (
    <div style={{ ...cssVars, background: 'var(--color-bg-base)', minHeight: '100vh', padding: '32px 24px' }}>
      <div style={{ maxWidth: 900, margin: '0 auto' }}>
        <div
          style={{
            background: bannerStyle.bg,
            borderRadius: 16,
            border: `1px solid ${bannerStyle.color}33`,
            padding: '32px 32px 28px',
            marginBottom: 24,
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              position: 'absolute',
              top: '-30%',
              right: '-5%',
              width: 200,
              height: 200,
              borderRadius: '50%',
              background: `radial-gradient(circle, ${bannerStyle.color}15 0%, transparent 70%)`,
              filter: 'blur(40px)',
              pointerEvents: 'none',
            }}
          />
          <div style={{ position: 'relative' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <span style={{ fontSize: 28, color: bannerStyle.color }}>
                {bannerStyle.icon}
              </span>
              <span style={{ color: bannerStyle.color, fontSize: 24, fontWeight: 700 }}>
                {ORDER_STATUS_LABELS[order.status]}
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 14, fontFamily: 'monospace' }}>
                订单号：{order.orderNo}
              </span>
              <div style={{ display: 'flex', gap: 8 }}>
                {order.status === 'PENDING_PAYMENT' && (
                  <>
                    <ActionButton label="取消订单" onClick={handleCancel} variant="danger" />
                    <ActionButton label="去支付" onClick={handlePay} variant="primary" />
                  </>
                )}
                {order.status === 'SHIPPED' && (
                  <ActionButton label="确认收货" onClick={handleConfirm} variant="primary" />
                )}
                {order.status === 'PAID' && (
                  <ActionButton label="申请退款" onClick={() => setRefundModalOpen(true)} variant="danger" />
                )}
                {order.status === 'COMPLETED' && (
                  <ActionButton label="再次购买" onClick={() => history.push('/products')} variant="primary" />
                )}
              </div>
            </div>
          </div>
        </div>

        {!isCancelled && !isRefunding && currentStep >= 0 && (
          <SectionCard title="订单进度">
            <div style={{ display: 'flex', justifyContent: 'space-between', position: 'relative' }}>
              <div
                style={{
                  position: 'absolute',
                  top: 18,
                  left: '10%',
                  right: '10%',
                  height: 2,
                  background: 'var(--color-border)',
                }}
              />
              <div
                style={{
                  position: 'absolute',
                  top: 18,
                  left: '10%',
                  width: `${(currentStep / 3) * 80}%`,
                  height: 2,
                  background: 'linear-gradient(90deg, var(--color-primary), var(--color-primary-dark))',
                  transition: 'width 0.5s ease',
                }}
              />
              {STEP_ITEMS.map((step, index) => (
                <div
                  key={step.title}
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    position: 'relative',
                    zIndex: 1,
                    flex: 1,
                  }}
                >
                  <div
                    style={{
                      width: 36,
                      height: 36,
                      borderRadius: '50%',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      background: index <= currentStep
                        ? 'var(--color-gradient-primary)'
                        : 'var(--color-bg-footer)',
                      border: index <= currentStep
                        ? 'none'
                        : '2px solid rgba(255,255,255,0.15)',
                      color: index <= currentStep ? 'var(--color-bg-base)' : 'var(--color-text-tertiary)',
                      fontSize: 14,
                      fontWeight: 700,
                      marginBottom: 10,
                      boxShadow: index <= currentStep ? '0 0 16px rgba(var(--color-primary-rgb), 0.3)' : 'none',
                      transition: 'all 0.3s',
                    }}
                  >
                    {index + 1}
                  </div>
                  <span
                    style={{
                      color: index <= currentStep ? '#FFFFFF' : 'var(--color-text-tertiary)',
                      fontSize: 13,
                      fontWeight: index <= currentStep ? 600 : 400,
                      marginBottom: 2,
                    }}
                  >
                    {step.title}
                  </span>
                  <span style={{ color: 'var(--color-text-tertiary)', fontSize: 11 }}>{step.desc}</span>
                </div>
              ))}
            </div>
          </SectionCard>
        )}

        {order.receiverName && order.receiverAddress && (
          <SectionCard
            title="收货信息"
            icon={<EnvironmentOutlined style={{ color: 'var(--color-primary)', fontSize: 16 }} />}
          >
            <div style={{ display: 'grid', gridTemplateColumns: '80px 1fr', gap: '12px 16px' }}>
              <span style={{ color: 'var(--color-text-tertiary)', fontSize: 14 }}>收货人</span>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 14, fontWeight: 500 }}>{order.receiverName}</span>
              <span style={{ color: 'var(--color-text-tertiary)', fontSize: 14 }}>联系电话</span>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>{order.receiverPhone}</span>
              <span style={{ color: 'var(--color-text-tertiary)', fontSize: 14 }}>收货地址</span>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>
                {order.receiverAddress}
              </span>
            </div>
          </SectionCard>
        )}

        <SectionCard
          title="商品清单"
          icon={<CarOutlined style={{ color: 'var(--color-primary)', fontSize: 16 }} />}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
            {order.items.map((item, index) => (
              <div
                key={item.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 16,
                  padding: '16px 0',
                  borderBottom: index < order.items.length - 1 ? '1px solid var(--color-border)' : 'none',
                }}
              >
                <img
                  alt={item.productName}
                  src={item.skuImage}
                  style={{
                    width: 80,
                    height: 80,
                    objectFit: 'cover',
                    borderRadius: 8,
                    background: 'var(--color-bg-footer)',
                    cursor: 'pointer',
                    flexShrink: 0,
                  }}
                  onClick={() => history.push(`/products/${item.productId}`)}
                />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div
                    style={{
                      color: 'var(--color-text-secondary)',
                      fontSize: 15,
                      fontWeight: 500,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      cursor: 'pointer',
                      marginBottom: 4,
                    }}
                    onClick={() => history.push(`/products/${item.productId}`)}
                  >
                    {item.productName}
                  </div>
                  {item.skuAttributes && (
                    <div
                      style={{
                        display: 'inline-block',
                        padding: '1px 8px',
                        background: 'rgba(var(--color-primary-rgb), 0.08)',
                        borderRadius: 4,
                        color: 'var(--color-text-secondary)',
                        fontSize: 12,
                      }}
                    >
                      {item.skuAttributes}
                    </div>
                  )}
                </div>
                <div style={{ textAlign: 'right', flexShrink: 0, minWidth: 120 }}>
                  <div style={{ color: 'var(--color-primary)', fontWeight: 600, fontSize: 15 }}>
                    ¥{item.price.toFixed(2)}
                  </div>
                  <div style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>x{item.quantity}</div>
                </div>
              </div>
            ))}
          </div>
        </SectionCard>

        <div style={{ display: 'flex', gap: 20, alignItems: 'flex-start' }}>
          <div style={{ flex: 1 }}>
            <SectionCard
              title="支付信息"
              icon={<CheckCircleOutlined style={{ color: 'var(--color-primary)', fontSize: 16 }} />}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>商品总额</span>
                  <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>¥{order.totalAmount.toFixed(2)}</span>
                </div>
                {order.discountAmount > 0 && (
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>优惠</span>
                    <span style={{ color: 'var(--color-primary)', fontSize: 14 }}>-¥{order.discountAmount.toFixed(2)}</span>
                  </div>
                )}
                <div
                  style={{
                    height: 1,
                    background: 'var(--color-border)',
                    margin: '4px 0',
                  }}
                />
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                  <span style={{ color: 'var(--color-text-secondary)', fontSize: 16, fontWeight: 600 }}>实付金额</span>
                  <span style={{ color: 'var(--color-primary)', fontSize: 28, fontWeight: 800 }}>
                    ¥{order.payAmount.toFixed(2)}
                  </span>
                </div>
              </div>
            </SectionCard>
          </div>

          <div style={{ width: 300, flexShrink: 0 }}>
            <SectionCard
              title="订单时间线"
              icon={<ClockCircleOutlined style={{ color: 'var(--color-primary)', fontSize: 16 }} />}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
                {timeline.map((event, index) => (
                  <div
                    key={event.label}
                    style={{
                      display: 'flex',
                      gap: 12,
                      paddingBottom: index < timeline.length - 1 ? 20 : 0,
                      position: 'relative',
                    }}
                  >
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                      <div
                        style={{
                          width: 10,
                          height: 10,
                          borderRadius: '50%',
                          background: event.active
                            ? 'var(--color-gradient-primary)'
                            : 'var(--color-text-tertiary)',
                          flexShrink: 0,
                          marginTop: 4,
                          boxShadow: event.active ? '0 0 8px rgba(var(--color-primary-rgb), 0.4)' : 'none',
                        }}
                      />
                      {index < timeline.length - 1 && (
                        <div
                          style={{
                            width: 2,
                            flex: 1,
                            background: 'rgba(var(--color-primary-rgb), 0.2)',
                            marginTop: 4,
                          }}
                        />
                      )}
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ color: 'var(--color-text-secondary)', fontSize: 13, fontWeight: 500, marginBottom: 2 }}>
                        {event.label}
                      </div>
                      <div style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>{event.time}</div>
                    </div>
                  </div>
                ))}
              </div>
            </SectionCard>
          </div>
        </div>
      </div>

      <Modal
        title={
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 16, fontWeight: 600 }}>申请退款</div>
        }
        open={refundModalOpen}
        onOk={handleRefund}
        onCancel={() => {
          setRefundModalOpen(false)
          setRefundReason('')
        }}
        okText="提交"
        cancelText="取消"
        styles={{
          header: { background: 'var(--color-bg-container)', borderBottom: '1px solid var(--color-border)' },
          body: { background: 'var(--color-bg-container)' },
          footer: { background: 'var(--color-bg-container)', borderTop: '1px solid var(--color-border)' },
        }}
      >
        <div style={{ color: 'var(--color-text-secondary)', fontSize: 14, marginBottom: 12 }}>
          请填写退款原因：
        </div>
        <Input.TextArea
          rows={4}
          value={refundReason}
          onChange={(e) => setRefundReason(e.target.value)}
          placeholder="请输入退款原因"
          maxLength={200}
          showCount
          style={{
            background: 'var(--color-bg-footer)',
            borderColor: 'var(--color-border)',
            color: 'var(--color-text-secondary)',
            borderRadius: 8,
          }}
        />
      </Modal>
    </div>
  )
}
