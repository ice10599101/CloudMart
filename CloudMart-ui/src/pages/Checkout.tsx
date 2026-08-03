import { useState, useEffect } from 'react'
import { Spin, message } from 'antd'
import {
  EnvironmentOutlined,
  ShoppingCartOutlined,
  TagOutlined,
  CheckCircleOutlined,
  FileTextOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import { history, useSearchParams } from 'umi'
import { useCartStore } from '@/stores/cart'
import { listAddresses, getDefaultAddress } from '@/api/user'
import { listUserCoupons } from '@/api/coupon'
import { createOrder } from '@/api/order'
import type { ShippingAddress, UserCoupon, CreateOrderRequest } from '@/types'

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

const STEPS = [
  { label: '确认信息', icon: <FileTextOutlined /> },
  { label: '提交订单', icon: <ShoppingCartOutlined /> },
  { label: '支付', icon: <CheckCircleOutlined /> },
]

function StepIndicator({ current }: { current: number }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 0,
        marginBottom: 40,
      }}
    >
      {STEPS.map((step, index) => (
        <div key={step.label} style={{ display: 'flex', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: index <= current
                  ? 'var(--color-gradient-primary)'
                  : 'var(--color-bg-footer)',
                border: index <= current
                  ? 'none'
                  : '1px solid rgba(255,255,255,0.15)',
                color: index <= current ? 'var(--color-bg-base)' : 'var(--color-text-tertiary)',
                fontSize: 14,
                fontWeight: 700,
                transition: 'all 0.3s',
                boxShadow: index <= current ? '0 0 16px rgba(var(--color-primary-rgb), 0.3)' : 'none',
              }}
            >
              {index + 1}
            </div>
            <span
              style={{
                color: index <= current ? '#FFFFFF' : 'var(--color-text-tertiary)',
                fontSize: 14,
                fontWeight: index <= current ? 600 : 400,
              }}
            >
              {step.label}
            </span>
          </div>
          {index < STEPS.length - 1 && (
            <div
              style={{
                width: 80,
                height: 2,
                background: index < current
                  ? 'linear-gradient(90deg, var(--color-primary), var(--color-primary-dark))'
                  : 'var(--color-border)',
                margin: '0 16px',
                borderRadius: 1,
              }}
            />
          )}
        </div>
      ))}
    </div>
  )
}

function AddressCard({
  address,
  selected,
  onSelect,
}: {
  address: ShippingAddress
  selected: boolean
  onSelect: () => void
}) {
  return (
    <div
      onClick={onSelect}
      style={{
        padding: '16px 20px',
        background: selected ? 'rgba(var(--color-primary-rgb), 0.08)' : 'var(--color-bg-container)',
        borderRadius: 10,
        border: `1px solid ${selected ? 'rgba(var(--color-primary-rgb), 0.4)' : 'var(--color-border)'}`,
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        boxShadow: selected ? '0 0 20px rgba(var(--color-primary-rgb), 0.1)' : 'none',
        position: 'relative',
      }}
    >
      {selected && (
        <div
          style={{
            position: 'absolute',
            top: 8,
            right: 12,
            width: 20,
            height: 20,
            borderRadius: '50%',
            background: 'var(--color-gradient-primary)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <CheckCircleOutlined style={{ fontSize: 12, color: 'var(--color-bg-base)' }} />
        </div>
      )}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
        <span style={{ color: 'var(--color-text-secondary)', fontSize: 15, fontWeight: 600 }}>
          {address.receiverName}
        </span>
        <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>{address.receiverPhone}</span>
        {address.isDefault && (
          <span
            style={{
              padding: '1px 8px',
              background: 'rgba(var(--color-primary-rgb), 0.15)',
              borderRadius: 4,
              color: 'var(--color-primary)',
              fontSize: 11,
              fontWeight: 600,
            }}
          >
            默认
          </span>
        )}
      </div>
      <div style={{ color: 'var(--color-text-secondary)', fontSize: 13, lineHeight: 1.6 }}>
        {address.province}{address.city}{address.district}{address.detailAddress}
      </div>
    </div>
  )
}

function CouponSelector({
  coupons,
  totalPrice,
  selectedCouponId,
  onChange,
}: {
  coupons: UserCoupon[]
  totalPrice: number
  selectedCouponId: number | null
  onChange: (id: number | null) => void
}) {
  const [open, setOpen] = useState(false)
  const availableCoupons = coupons.filter((c) => totalPrice >= c.thresholdAmount)

  const selectedCoupon = coupons.find((c) => c.id === selectedCouponId)

  return (
    <div style={{ position: 'relative' }}>
      <div
        onClick={() => availableCoupons.length > 0 && setOpen(!open)}
        style={{
          padding: '12px 16px',
          background: 'var(--color-bg-footer)',
          borderRadius: 8,
          border: '1px solid var(--color-border)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          cursor: availableCoupons.length > 0 ? 'pointer' : 'default',
          transition: 'all 0.2s',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <TagOutlined style={{ color: 'var(--color-primary)', fontSize: 14 }} />
          <span style={{ color: selectedCoupon ? '#FFFFFF' : 'var(--color-text-tertiary)', fontSize: 14 }}>
            {selectedCoupon
              ? `${selectedCoupon.templateName} - 满${selectedCoupon.thresholdAmount}减${selectedCoupon.discountAmount ?? `${selectedCoupon.discountRate}%`}`
              : availableCoupons.length > 0
                ? `选择优惠券 (${availableCoupons.length}张可用)`
                : '暂无可用优惠券'}
          </span>
        </div>
        {selectedCoupon && (
          <span
            onClick={(e) => {
              e.stopPropagation()
              onChange(null)
            }}
            style={{ color: 'var(--color-text-tertiary)', fontSize: 12, cursor: 'pointer' }}
          >
            取消
          </span>
        )}
      </div>

      {open && availableCoupons.length > 0 && (
        <div
          style={{
            position: 'absolute',
            top: '100%',
            left: 0,
            right: 0,
            zIndex: 50,
            marginTop: 4,
            background: 'var(--color-bg-container)',
            borderRadius: 10,
            border: '1px solid rgba(var(--color-primary-rgb), 0.2)',
            boxShadow: '0 8px 40px rgba(0,0,0,0.5)',
            overflow: 'hidden',
          }}
        >
          {availableCoupons.map((coupon) => (
            <div
              key={coupon.id}
              onClick={() => {
                onChange(coupon.id)
                setOpen(false)
              }}
              style={{
                padding: '12px 16px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                cursor: 'pointer',
                background: coupon.id === selectedCouponId ? 'rgba(var(--color-primary-rgb), 0.1)' : 'transparent',
                borderBottom: '1px solid var(--color-border)',
                transition: 'background 0.2s',
              }}
              onMouseEnter={(e) => {
                if (coupon.id !== selectedCouponId) {
                  e.currentTarget.style.background = 'var(--color-bg-elevated)'
                }
              }}
              onMouseLeave={(e) => {
                if (coupon.id !== selectedCouponId) {
                  e.currentTarget.style.background = 'transparent'
                }
              }}
            >
              <div>
                <div style={{ color: 'var(--color-text-secondary)', fontSize: 13, fontWeight: 500, marginBottom: 2 }}>
                  {coupon.templateName}
                </div>
                <div style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>
                  满{coupon.thresholdAmount}减{coupon.discountAmount ?? `${coupon.discountRate}%`}
                </div>
              </div>
              {coupon.id === selectedCouponId && (
                <CheckCircleOutlined style={{ color: 'var(--color-primary)', fontSize: 16 }} />
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default function Checkout() {
  const [searchParams] = useSearchParams()
  const { items, fetchCart } = useCartStore()
  const [addresses, setAddresses] = useState<ShippingAddress[]>([])
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null)
  const [coupons, setCoupons] = useState<UserCoupon[]>([])
  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [loading, setLoading] = useState(true)

  const skuIdsParam = searchParams.get('skuIds') || ''
  const selectedSkuIds = skuIdsParam
    .split(',')
    .filter(Boolean)
    .map(Number)

  const checkedItems = selectedSkuIds.length
    ? items.filter((item) => selectedSkuIds.includes(item.skuId))
    : items

  useEffect(() => {
    async function fetchData() {
      setLoading(true)
      try {
        const [addrRes, defaultAddrRes, couponRes] = await Promise.all([
          listAddresses(),
          getDefaultAddress().catch(() => ({ data: { data: null } })),
          listUserCoupons({ status: 'UNUSED' }),
        ])
        const addrList = addrRes.data.data ?? []
        setAddresses(addrList)
        const defaultAddr = defaultAddrRes.data.data
        if (defaultAddr) {
          setSelectedAddressId(defaultAddr.id)
        } else if (addrList.length > 0) {
          const defaultItem = addrList.find((a) => a.isDefault)
          setSelectedAddressId(defaultItem?.id ?? addrList[0].id)
        }
        setCoupons(couponRes.data.data ?? [])
      } catch {
        message.error('加载数据失败')
      } finally {
        setLoading(false)
      }
    }
    fetchCart()
    fetchData()
  }, [fetchCart])

  const totalPrice = checkedItems.reduce(
    (sum, item) => sum + item.price * item.quantity,
    0
  )

  const selectedCoupon = coupons.find((c) => c.id === selectedCouponId)
  let discountAmount = 0
  if (selectedCoupon) {
    if (totalPrice >= selectedCoupon.thresholdAmount) {
      if (selectedCoupon.templateType === 'AMOUNT_OFF' && selectedCoupon.discountAmount) {
        discountAmount = selectedCoupon.discountAmount
      } else if (selectedCoupon.templateType === 'PERCENT_OFF' && selectedCoupon.discountRate) {
        discountAmount = totalPrice * (1 - selectedCoupon.discountRate / 100)
      }
    }
  }

  const freightAmount = 0
  const payAmount = Math.max(0, totalPrice + freightAmount - discountAmount)

  const handleSubmit = async () => {
    if (!selectedAddressId) {
      message.warning('请选择收货地址')
      return
    }
    if (checkedItems.length === 0) {
      message.warning('没有需要结算的商品')
      return
    }

    const selectedAddress = addresses.find((a) => a.id === selectedAddressId)
    if (!selectedAddress) {
      message.warning('收货地址不存在')
      return
    }

    setSubmitting(true)
    try {
      const payload: CreateOrderRequest = {
        requestId: crypto.randomUUID(),
        items: checkedItems.map((item) => ({
          productId: item.productId,
          skuId: item.skuId,
          quantity: item.quantity,
          productName: item.productName,
          skuImage: item.skuImage,
          skuAttributes: item.skuAttributes,
          price: item.price,
        })),
        receiverName: selectedAddress.receiverName,
        receiverPhone: selectedAddress.receiverPhone,
        receiverAddress: `${selectedAddress.province}${selectedAddress.city}${selectedAddress.district}${selectedAddress.detailAddress}`,
        couponId: selectedCouponId ?? undefined,
      }
      const orderRes = await createOrder(payload)
      message.success('订单创建成功')
      history.push(`/payment/${orderRes.data.data.id}`)
    } catch {
      message.error('订单创建失败')
    } finally {
      setSubmitting(false)
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

  return (
    <div style={{ ...cssVars, background: 'var(--color-bg-base)', minHeight: '100vh', padding: '32px 24px' }}>
      <div style={{ maxWidth: 1100, margin: '0 auto' }}>
        <h1 style={{ margin: '0 0 8px', fontSize: 28, fontWeight: 700, color: 'var(--color-text-secondary)' }}>
          确认订单
        </h1>
        <p style={{ margin: '0 0 32px', color: 'var(--color-text-tertiary)', fontSize: 14 }}>
          请确认您的订单信息
        </p>

        <StepIndicator current={0} />

        <div style={{ display: 'flex', gap: 24, alignItems: 'flex-start' }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div
              style={{
                background: 'var(--color-bg-container)',
                borderRadius: 16,
                border: '1px solid var(--color-border)',
                boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
                marginBottom: 20,
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  padding: '20px 24px',
                  borderBottom: '1px solid var(--color-border)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <EnvironmentOutlined style={{ color: 'var(--color-primary)', fontSize: 16 }} />
                  <span style={{ color: 'var(--color-text-secondary)', fontSize: 16, fontWeight: 600 }}>
                    收货地址
                  </span>
                </div>
                <button
                  onClick={() => history.push('/profile')}
                  style={{
                    background: 'none',
                    border: '1px solid rgba(255,255,255,0.15)',
                    borderRadius: 6,
                    color: 'var(--color-text-secondary)',
                    fontSize: 12,
                    padding: '4px 12px',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
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
                  <PlusOutlined style={{ fontSize: 10 }} />
                  新增地址
                </button>
              </div>

              <div style={{ padding: '16px 24px' }}>
                {addresses.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '32px 0' }}>
                    <div style={{ color: 'var(--color-text-tertiary)', fontSize: 14, marginBottom: 16 }}>
                      暂无收货地址
                    </div>
                    <button
                      onClick={() => history.push('/profile')}
                      style={{
                        padding: '8px 24px',
                        background: 'var(--color-gradient-primary)',
                        color: 'var(--color-bg-base)',
                        border: 'none',
                        borderRadius: 6,
                        fontSize: 13,
                        fontWeight: 600,
                        cursor: 'pointer',
                      }}
                    >
                      添加地址
                    </button>
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                    {addresses.map((addr) => (
                      <AddressCard
                        key={addr.id}
                        address={addr}
                        selected={addr.id === selectedAddressId}
                        onSelect={() => setSelectedAddressId(addr.id)}
                      />
                    ))}
                  </div>
                )}
              </div>
            </div>

            <div
              style={{
                background: 'var(--color-bg-container)',
                borderRadius: 16,
                border: '1px solid var(--color-border)',
                boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
                marginBottom: 20,
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  padding: '20px 24px',
                  borderBottom: '1px solid var(--color-border)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                }}
              >
                <ShoppingCartOutlined style={{ color: 'var(--color-primary)', fontSize: 16 }} />
                <span style={{ color: 'var(--color-text-secondary)', fontSize: 16, fontWeight: 600 }}>
                  商品清单
                </span>
                <span style={{ color: 'var(--color-text-tertiary)', fontSize: 13 }}>
                  {checkedItems.length} 件商品
                </span>
              </div>

              <div style={{ padding: '8px 24px' }}>
                {checkedItems.map((item) => (
                  <div
                    key={item.skuId}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 16,
                      padding: '16px 0',
                      borderBottom: '1px solid var(--color-border)',
                    }}
                  >
                    <img
                      alt={item.productName}
                      src={item.skuImage}
                      style={{
                        width: 72,
                        height: 72,
                        objectFit: 'cover',
                        borderRadius: 8,
                        background: 'var(--color-bg-footer)',
                        flexShrink: 0,
                      }}
                    />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div
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
                    <div style={{ textAlign: 'right', flexShrink: 0 }}>
                      <div style={{ color: 'var(--color-primary)', fontWeight: 600, fontSize: 15 }}>
                        ¥{item.price.toFixed(2)}
                      </div>
                      <div style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>x{item.quantity}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div
              style={{
                background: 'var(--color-bg-container)',
                borderRadius: 16,
                border: '1px solid var(--color-border)',
                boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  padding: '20px 24px',
                  borderBottom: '1px solid var(--color-border)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                }}
              >
                <TagOutlined style={{ color: 'var(--color-primary)', fontSize: 16 }} />
                <span style={{ color: 'var(--color-text-secondary)', fontSize: 16, fontWeight: 600 }}>
                  优惠券
                </span>
              </div>
              <div style={{ padding: '16px 24px' }}>
                <CouponSelector
                  coupons={coupons}
                  totalPrice={totalPrice}
                  selectedCouponId={selectedCouponId}
                  onChange={setSelectedCouponId}
                />
              </div>
            </div>
          </div>

          <div
            style={{
              width: 340,
              flexShrink: 0,
              position: 'sticky',
              top: 80,
            }}
          >
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
                  padding: '20px 24px',
                  borderBottom: '1px solid var(--color-border)',
                }}
              >
                <span style={{ color: 'var(--color-text-secondary)', fontSize: 18, fontWeight: 600 }}>
                  订单汇总
                </span>
              </div>

              <div style={{ padding: '20px 24px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                  <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>商品金额</span>
                  <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>¥{totalPrice.toFixed(2)}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                  <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>运费</span>
                  <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>
                    {freightAmount > 0 ? `¥${freightAmount.toFixed(2)}` : '免运费'}
                  </span>
                </div>
                {discountAmount > 0 && (
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                    <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>优惠</span>
                    <span style={{ color: 'var(--color-primary)', fontSize: 14 }}>
                      -¥{discountAmount.toFixed(2)}
                    </span>
                  </div>
                )}

                <div
                  style={{
                    height: 1,
                    background: 'var(--color-border)',
                    margin: '16px -24px',
                  }}
                />

                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'baseline',
                    marginBottom: 24,
                  }}
                >
                  <span style={{ color: 'var(--color-text-secondary)', fontSize: 16, fontWeight: 600 }}>应付金额</span>
                  <span style={{ color: 'var(--color-primary)', fontSize: 28, fontWeight: 800 }}>
                    ¥{payAmount.toFixed(2)}
                  </span>
                </div>

                <button
                  onClick={handleSubmit}
                  disabled={submitting || checkedItems.length === 0 || !selectedAddressId}
                  style={{
                    width: '100%',
                    padding: '14px 0',
                    fontSize: 16,
                    fontWeight: 700,
                    color: submitting || checkedItems.length === 0 || !selectedAddressId
                      ? 'var(--color-text-tertiary)'
                      : 'var(--color-bg-base)',
                    background: submitting || checkedItems.length === 0 || !selectedAddressId
                      ? 'var(--color-bg-footer)'
                      : 'var(--color-gradient-primary)',
                    border: 'none',
                    borderRadius: 10,
                    cursor: submitting || checkedItems.length === 0 || !selectedAddressId
                      ? 'not-allowed'
                      : 'pointer',
                    boxShadow: submitting || checkedItems.length === 0 || !selectedAddressId
                      ? 'none'
                      : '0 0 24px rgba(var(--color-primary-rgb), 0.3)',
                    transition: 'all 0.3s ease',
                    letterSpacing: 2,
                  }}
                  onMouseEnter={(e) => {
                    if (!submitting && checkedItems.length > 0 && selectedAddressId) {
                      e.currentTarget.style.boxShadow = '0 0 36px rgba(var(--color-primary-rgb), 0.5)'
                      e.currentTarget.style.transform = 'translateY(-1px)'
                    }
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.boxShadow = submitting || checkedItems.length === 0 || !selectedAddressId
                      ? 'none'
                      : '0 0 24px rgba(var(--color-primary-rgb), 0.3)'
                    e.currentTarget.style.transform = 'translateY(0)'
                  }}
                >
                  {submitting ? '提交中...' : '提交订单'}
                </button>
              </div>
            </div>

          </div>
        </div>
      </div>
    </div>
  )
}
