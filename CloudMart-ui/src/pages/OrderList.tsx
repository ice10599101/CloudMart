import { useState, useEffect } from 'react'
import { Spin, message } from 'antd'
import { history, useSearchParams } from 'umi'
import { fetchOrders, cancelOrder, confirmReceipt } from '@/api/order'
import { type Order, type OrderStatus, ORDER_STATUS_LABELS } from '@/types'
import {
  UnorderedListOutlined,
  RightOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'

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

const TAB_ITEMS: { key: string; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'PENDING_PAYMENT', label: '待付款' },
  { key: 'PAID', label: '已付款' },
  { key: 'SHIPPED', label: '已发货' },
  { key: 'COMPLETED', label: '已完成' },
  { key: 'CANCELLED', label: '已取消' },
]

const STATUS_STYLE_MAP: Record<string, { color: string; bg: string }> = {
  PENDING_PAYMENT: { color: '#FFA940', bg: 'rgba(255,169,64,0.12)' },
  PAID: { color: 'var(--color-primary)', bg: 'rgba(var(--color-primary-rgb), 0.12)' },
  SHIPPED: { color: '#36CFC9', bg: 'rgba(54,207,201,0.12)' },
  COMPLETED: { color: '#52C41A', bg: 'rgba(82,196,26,0.12)' },
  CANCELLED: { color: 'var(--color-text-secondary)', bg: 'rgba(139,157,195,0.12)' },
  REFUNDING: { color: '#FF7A45', bg: 'rgba(255,122,69,0.12)' },
  REFUNDED: { color: '#FF4D4F', bg: 'rgba(255,77,79,0.12)' },
}

function StatusTag({ status }: { status: OrderStatus }) {
  const style = STATUS_STYLE_MAP[status] ?? { color: 'var(--color-text-secondary)', bg: 'rgba(139,157,195,0.12)' }
  return (
    <span
      style={{
        padding: '3px 12px',
        borderRadius: 20,
        background: style.bg,
        color: style.color,
        fontSize: 12,
        fontWeight: 600,
        letterSpacing: 0.5,
      }}
    >
      {ORDER_STATUS_LABELS[status]}
    </span>
  )
}

function ActionButton({
  label,
  onClick,
  variant = 'default',
  loading = false,
}: {
  label: string
  onClick: () => void
  variant?: 'primary' | 'default' | 'danger'
  loading?: boolean
}) {
  const isPrimary = variant === 'primary'
  const isDanger = variant === 'danger'

  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        padding: '6px 18px',
        fontSize: 13,
        fontWeight: 600,
        color: isPrimary ? 'var(--color-bg-base)' : isDanger ? '#ff4d4f' : 'var(--color-text-secondary)',
        background: isPrimary
          ? 'var(--color-gradient-primary)'
          : 'transparent',
        border: isPrimary
          ? 'none'
          : `1px solid ${isDanger ? 'rgba(255,77,79,0.3)' : 'rgba(255,255,255,0.15)'}`,
        borderRadius: 6,
        cursor: loading ? 'wait' : 'pointer',
        transition: 'all 0.2s ease',
        letterSpacing: 0.5,
      }}
      onMouseEnter={(e) => {
        if (isPrimary) {
          e.currentTarget.style.boxShadow = '0 0 16px rgba(var(--color-primary-rgb), 0.4)'
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
      {loading ? '处理中...' : label}
    </button>
  )
}

function OrderCard({ order, onAction }: { order: Order; onAction: () => void }) {
  const [actionLoading, setActionLoading] = useState(false)

  const handleCancel = async () => {
    setActionLoading(true)
    try {
      await cancelOrder(order.id)
      message.success('订单已取消')
      onAction()
    } catch {
      message.error('操作失败')
    } finally {
      setActionLoading(false)
    }
  }

  const handleConfirm = async () => {
    setActionLoading(true)
    try {
      await confirmReceipt(order.id)
      message.success('已确认收货')
      onAction()
    } catch {
      message.error('操作失败')
    } finally {
      setActionLoading(false)
    }
  }

  const handlePay = () => {
    history.push(`/payment/${order.id}`)
  }

  return (
    <div
      style={{
        background: 'var(--color-bg-container)',
        borderRadius: 16,
        border: '1px solid var(--color-border)',
        boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
        overflow: 'hidden',
        transition: 'all 0.3s ease',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.15)'
        e.currentTarget.style.boxShadow = '0 8px 40px rgba(0,0,0,0.4), 0 0 20px rgba(var(--color-primary-rgb), 0.08)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'var(--color-border)'
        e.currentTarget.style.boxShadow = '0 4px 24px rgba(0,0,0,0.3)'
      }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '16px 24px',
          borderBottom: '1px solid var(--color-border)',
          background: 'rgba(0,0,0,0.15)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <span style={{ color: 'var(--color-text-tertiary)', fontSize: 13, fontFamily: 'monospace' }}>
            {order.orderNo}
          </span>
          <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>
            {order.createdAt}
          </span>
        </div>
        <StatusTag status={order.status} />
      </div>

      <div
        style={{
          padding: '20px 24px',
          display: 'flex',
          gap: 16,
          cursor: 'pointer',
        }}
        onClick={() => history.push(`/orders/${order.id}`)}
      >
        <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
          {order.items.slice(0, 4).map((item) => (
            <img
              key={item.id}
              alt={item.productName}
              src={item.skuImage}
              style={{
                width: 72,
                height: 72,
                objectFit: 'cover',
                borderRadius: 8,
                background: 'var(--color-bg-footer)',
              }}
            />
          ))}
          {order.items.length > 4 && (
            <div
              style={{
                width: 72,
                height: 72,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: 'var(--color-bg-footer)',
                borderRadius: 8,
                color: 'var(--color-text-tertiary)',
                fontSize: 13,
                fontWeight: 600,
              }}
            >
              +{order.items.length - 4}
            </div>
          )}
        </div>

        <div style={{ flex: 1, minWidth: 0 }}>
          {order.items.slice(0, 2).map((item) => (
            <div
              key={item.id}
              style={{
                color: 'var(--color-text-secondary)',
                fontSize: 14,
                fontWeight: 500,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                marginBottom: 4,
              }}
            >
              {item.productName}
              <span style={{ color: 'var(--color-text-tertiary)', marginLeft: 8, fontSize: 12 }}>x{item.quantity}</span>
            </div>
          ))}
          {order.items.length > 2 && (
            <div style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>
              等{order.items.length}件商品
            </div>
          )}
        </div>

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            color: 'var(--color-text-tertiary)',
            flexShrink: 0,
          }}
        >
          <RightOutlined style={{ fontSize: 12 }} />
        </div>
      </div>

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '16px 24px',
          borderTop: '1px solid var(--color-border)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
          <span style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>
            共 {order.items.reduce((s, i) => s + i.quantity, 0)} 件商品
          </span>
          <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>|</span>
          <span style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>合计</span>
          <span style={{ color: 'var(--color-primary)', fontSize: 20, fontWeight: 800 }}>
            ¥{order.payAmount.toFixed(2)}
          </span>
        </div>

        <div style={{ display: 'flex', gap: 8 }}>
          {order.status === 'PENDING_PAYMENT' && (
            <>
              <ActionButton label="取消订单" onClick={handleCancel} variant="danger" loading={actionLoading} />
              <ActionButton label="去支付" onClick={handlePay} variant="primary" />
            </>
          )}
          {order.status === 'SHIPPED' && (
            <ActionButton label="确认收货" onClick={handleConfirm} variant="primary" loading={actionLoading} />
          )}
          <ActionButton label="查看详情" onClick={() => history.push(`/orders/${order.id}`)} />
        </div>
      </div>
    </div>
  )
}

function EmptyOrders() {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '80px 24px',
        background: 'var(--color-bg-container)',
        borderRadius: 16,
        border: '1px solid var(--color-border)',
      }}
    >
      <div
        style={{
          width: 100,
          height: 100,
          borderRadius: '50%',
          background: 'rgba(var(--color-primary-rgb), 0.08)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: 20,
        }}
      >
        <ExclamationCircleOutlined style={{ fontSize: 40, color: 'rgba(var(--color-primary-rgb), 0.3)' }} />
      </div>
      <div style={{ color: 'var(--color-text-secondary)', fontSize: 16, fontWeight: 500, marginBottom: 8 }}>
        暂无订单
      </div>
      <div style={{ color: 'var(--color-text-tertiary)', fontSize: 14, marginBottom: 28 }}>
        去发现心仪的好物吧
      </div>
      <button
        type="button"
        onClick={() => history.push('/products')}
        style={{
          padding: '10px 32px',
          fontSize: 14,
          fontWeight: 600,
          color: 'var(--color-bg-base)',
          background: 'var(--color-gradient-primary)',
          border: 'none',
          borderRadius: 50,
          cursor: 'pointer',
          boxShadow: '0 0 20px rgba(var(--color-primary-rgb), 0.3)',
          transition: 'all 0.3s ease',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.boxShadow = '0 0 32px rgba(var(--color-primary-rgb), 0.5)'
          e.currentTarget.style.transform = 'translateY(-2px)'
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.boxShadow = '0 0 20px rgba(var(--color-primary-rgb), 0.3)'
          e.currentTarget.style.transform = 'translateY(0)'
        }}
      >
        去逛逛
      </button>
    </div>
  )
}

export default function OrderList() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [orders, setOrders] = useState<Order[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)

  const activeTab = searchParams.get('status') || 'all'
  const page = Number(searchParams.get('page')) || 1
  const pageSize = 10

  const loadOrders = async () => {
    setLoading(true)
    try {
      const status = activeTab !== 'all' ? (activeTab as OrderStatus) : undefined
      const res = await fetchOrders({ status, page, size: pageSize })
      setOrders(res.data.data ?? [])
      setTotal(res.data.meta?.total ?? 0)
    } catch {
      message.error('加载订单失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadOrders()
  }, [activeTab, page])

  const handleTabChange = (key: string) => {
    const params = new URLSearchParams()
    if (key !== 'all') params.set('status', key)
    params.set('page', '1')
    setSearchParams(params)
  }

  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams)
    params.set('page', String(newPage))
    setSearchParams(params)
  }

  const totalPages = Math.ceil(total / pageSize)

  return (
    <div style={{ ...cssVars, background: 'var(--color-bg-base)', minHeight: '100vh', padding: '32px 24px' }}>
      <div style={{ maxWidth: 1000, margin: '0 auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 32 }}>
          <UnorderedListOutlined style={{ fontSize: 24, color: 'var(--color-primary)' }} />
          <h1 style={{ margin: 0, fontSize: 28, fontWeight: 700, color: 'var(--color-text-secondary)' }}>
            我的订单
          </h1>
        </div>

        <div
          style={{
            display: 'flex',
            gap: 4,
            marginBottom: 24,
            padding: '4px',
            background: 'var(--color-bg-footer)',
            borderRadius: 10,
            border: '1px solid var(--color-border)',
          }}
        >
          {TAB_ITEMS.map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => handleTabChange(tab.key)}
              style={{
                flex: 1,
                padding: '10px 0',
                fontSize: 14,
                fontWeight: activeTab === tab.key ? 600 : 400,
                color: activeTab === tab.key ? 'var(--color-bg-base)' : 'var(--color-text-secondary)',
                background: activeTab === tab.key
                  ? 'var(--color-gradient-primary)'
                  : 'transparent',
                border: 'none',
                borderRadius: 8,
                cursor: 'pointer',
                transition: 'all 0.3s ease',
                boxShadow: activeTab === tab.key ? '0 0 16px rgba(var(--color-primary-rgb), 0.3)' : 'none',
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <Spin spinning={loading}>
          {orders.length === 0 && !loading ? (
            <EmptyOrders />
          ) : (
            <>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                {orders.map((order) => (
                  <OrderCard key={order.id} order={order} onAction={loadOrders} />
                ))}
              </div>

              {totalPages > 1 && (
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center',
                    gap: 8,
                    marginTop: 32,
                  }}
                >
                  <button
                    type="button"
                    onClick={() => handlePageChange(page - 1)}
                    disabled={page <= 1}
                    style={{
                      padding: '8px 16px',
                      background: 'var(--color-bg-container)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 8,
                      color: page <= 1 ? 'var(--color-text-tertiary)' : 'var(--color-text-secondary)',
                      cursor: page <= 1 ? 'not-allowed' : 'pointer',
                      fontSize: 13,
                      transition: 'all 0.2s',
                    }}
                  >
                    上一页
                  </button>

                  {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
                    let pageNum: number
                    if (totalPages <= 5) {
                      pageNum = i + 1
                    } else if (page <= 3) {
                      pageNum = i + 1
                    } else if (page >= totalPages - 2) {
                      pageNum = totalPages - 4 + i
                    } else {
                      pageNum = page - 2 + i
                    }
                    return (
                      <button
                        key={pageNum}
                        type="button"
                        onClick={() => handlePageChange(pageNum)}
                        style={{
                          width: 36,
                          height: 36,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          background: page === pageNum
                            ? 'var(--color-gradient-primary)'
                            : 'var(--color-bg-container)',
                          border: page === pageNum
                            ? 'none'
                            : '1px solid var(--color-border)',
                          borderRadius: 8,
                          color: page === pageNum ? 'var(--color-bg-base)' : 'var(--color-text-secondary)',
                          fontSize: 13,
                          fontWeight: page === pageNum ? 700 : 400,
                          cursor: 'pointer',
                          transition: 'all 0.2s',
                          boxShadow: page === pageNum ? '0 0 12px rgba(var(--color-primary-rgb), 0.3)' : 'none',
                        }}
                      >
                        {pageNum}
                      </button>
                    )
                  })}

                  <button
                    type="button"
                    onClick={() => handlePageChange(page + 1)}
                    disabled={page >= totalPages}
                    style={{
                      padding: '8px 16px',
                      background: 'var(--color-bg-container)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 8,
                      color: page >= totalPages ? 'var(--color-text-tertiary)' : 'var(--color-text-secondary)',
                      cursor: page >= totalPages ? 'not-allowed' : 'pointer',
                      fontSize: 13,
                      transition: 'all 0.2s',
                    }}
                  >
                    下一页
                  </button>
                </div>
              )}
            </>
          )}
        </Spin>
      </div>
    </div>
  )
}
