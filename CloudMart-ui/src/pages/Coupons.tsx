import { useState, useEffect, useCallback } from 'react'
import { listTemplates, listUserCoupons, claimCoupon } from '@/api/coupon'
import type { CouponTemplate, UserCoupon, CouponType, UserCouponStatus } from '@/types'

const COUPON_TYPE_CONFIG: Record<CouponType, { label: string; gradient: string; accent: string }> = {
  AMOUNT_OFF: { label: '满减', gradient: 'var(--color-gradient-primary)', accent: 'var(--color-primary)' },
  PERCENT_OFF: { label: '折扣', gradient: 'linear-gradient(135deg, #FF6B6B 0%, #FF8E53 100%)', accent: '#FF6B6B' },
}

const USER_COUPON_STATUS_MAP: Record<UserCouponStatus, { label: string; color: string; bg: string }> = {
  UNUSED: { label: '未使用', color: '#2ED573', bg: 'rgba(46,213,115,0.15)' },
  USED: { label: '已使用', color: 'var(--color-text-tertiary)', bg: 'var(--color-border)' },
  EXPIRED: { label: '已过期', color: 'var(--color-text-tertiary)', bg: 'var(--color-border)' },
}

function formatDiscount(couponType: CouponType, discountAmount?: number, discountRate?: number): string {
  if (couponType === 'AMOUNT_OFF') {
    return `¥${discountAmount}`
  }
  const offPercent = Math.round((1 - (discountRate ?? 1)) * 100)
  return `${offPercent > 0 ? offPercent / 10 : discountRate}折`
}

function CouponTemplateCard({
  template,
  onClaim,
}: {
  template: CouponTemplate
  onClaim: (id: number) => Promise<void>
}) {
  const [claiming, setClaiming] = useState(false)
  const typeConfig = COUPON_TYPE_CONFIG[template.type]
  const canClaim = template.remainingQuantity > 0 && template.status === 'ENABLED'

  const handleClaim = async () => {
    setClaiming(true)
    try {
      await onClaim(template.id)
    } finally {
      setClaiming(false)
    }
  }

  return (
    <div
      style={{
        background: 'var(--color-bg-container)',
        border: '1px solid var(--color-border)',
        borderRadius: '12px',
        overflow: 'hidden',
        transition: 'all 0.3s ease',
        boxShadow: '0 4px 24px rgba(0, 0, 0, 0.3)',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
        e.currentTarget.style.boxShadow = '0 8px 40px rgba(0, 0, 0, 0.4), 0 0 20px rgba(var(--color-primary-rgb), 0.08)'
        e.currentTarget.style.transform = 'translateY(-2px)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'var(--color-border)'
        e.currentTarget.style.boxShadow = '0 4px 24px rgba(0, 0, 0, 0.3)'
        e.currentTarget.style.transform = 'translateY(0)'
      }}
    >
      <div style={{ display: 'flex' }}>
        <div
          style={{
            width: 110,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '20px 12px',
            background: typeConfig.gradient,
            flexShrink: 0,
            position: 'relative',
          }}
        >
          <span style={{ fontSize: 28, fontWeight: 900, color: 'var(--color-text-secondary)', textShadow: '0 2px 8px rgba(0,0,0,0.2)' }}>
            {formatDiscount(template.type, template.discountAmount, template.discountRate)}
          </span>
          <span
            style={{
              padding: '2px 8px',
              borderRadius: '4px',
              background: 'rgba(255,255,255,0.25)',
              color: 'var(--color-text-secondary)',
              fontSize: 11,
              fontWeight: 600,
              marginTop: 6,
            }}
          >
            {typeConfig.label}
          </span>
        </div>

        <div style={{ flex: 1, padding: '16px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 6, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {template.name}
            </div>
            <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginBottom: 4 }}>
              满¥{template.thresholdAmount}可用
            </div>
            {template.validityType === 'FIXED_DATE' && template.endTime && (
              <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>
                {template.startTime && template.startTime !== template.endTime
                  ? `${template.startTime.slice(0, 10)} ~ ${template.endTime.slice(0, 10)}`
                  : `有效期至 ${template.endTime.slice(0, 10)}`}
              </div>
            )}
            {template.validityType === 'FIXED_DAYS' && template.validDays && (
              <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>
                领取后{template.validDays}天内有效
              </div>
            )}
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 10 }}>
            <span style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>
              剩余 {template.remainingQuantity}
            </span>
            <button
              onClick={handleClaim}
              disabled={!canClaim || claiming}
              style={{
                padding: '5px 16px',
                border: 'none',
                borderRadius: '6px',
                fontSize: 12,
                fontWeight: 600,
                cursor: !canClaim || claiming ? 'not-allowed' : 'pointer',
                background: canClaim
                  ? 'var(--color-gradient-primary)'
                  : 'var(--color-bg-elevated)',
                color: canClaim ? '#fff' : 'var(--color-text-tertiary)',
                boxShadow: canClaim ? '0 2px 8px rgba(var(--color-primary-rgb), 0.25)' : 'none',
                transition: 'all 0.3s ease',
              }}
            >
              {claiming ? '领取中...' : canClaim ? '立即领取' : '已领完'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function UserCouponCard({ coupon }: { coupon: UserCoupon }) {
  const typeConfig = COUPON_TYPE_CONFIG[coupon.templateType]
  const statusConfig = USER_COUPON_STATUS_MAP[coupon.status]
  const isDisabled = coupon.status === 'USED' || coupon.status === 'EXPIRED'

  return (
    <div
      style={{
        background: 'var(--color-bg-container)',
        border: '1px solid var(--color-border)',
        borderRadius: '12px',
        overflow: 'hidden',
        transition: 'all 0.3s ease',
        boxShadow: '0 4px 24px rgba(0, 0, 0, 0.3)',
        opacity: isDisabled ? 0.6 : 1,
      }}
      onMouseEnter={(e) => {
        if (!isDisabled) {
          e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
        }
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'var(--color-border)'
      }}
    >
      <div style={{ display: 'flex' }}>
        <div
          style={{
            width: 110,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '20px 12px',
            background: isDisabled ? 'linear-gradient(135deg, #2A3550 0%, var(--color-bg-elevated) 100%)' : typeConfig.gradient,
            flexShrink: 0,
          }}
        >
          <span style={{ fontSize: 28, fontWeight: 900, color: isDisabled ? 'var(--color-text-tertiary)' : '#FFFFFF' }}>
            {formatDiscount(coupon.templateType, coupon.discountAmount, coupon.discountRate)}
          </span>
          <span
            style={{
              padding: '2px 8px',
              borderRadius: '4px',
              background: isDisabled ? 'var(--color-border)' : 'rgba(255,255,255,0.25)',
              color: isDisabled ? 'var(--color-text-tertiary)' : '#FFFFFF',
              fontSize: 11,
              fontWeight: 600,
              marginTop: 6,
            }}
          >
            {typeConfig.label}
          </span>
        </div>

        <div style={{ flex: 1, padding: '16px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <div
              style={{
                fontSize: 15,
                fontWeight: 600,
                color: 'var(--color-text-secondary)',
                marginBottom: 6,
                textDecoration: isDisabled ? 'line-through' : 'none',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {coupon.templateName}
            </div>
            <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginBottom: 4 }}>
              满¥{coupon.thresholdAmount}可用
            </div>
            {coupon.expiredAt && (
              <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>
                有效期至 {coupon.expiredAt.slice(0, 10)}
              </div>
            )}
          </div>
          <div style={{ marginTop: 10 }}>
            <span
              style={{
                padding: '2px 10px',
                borderRadius: '4px',
                background: statusConfig.bg,
                color: statusConfig.color,
                fontSize: 11,
                fontWeight: 600,
              }}
            >
              {statusConfig.label}
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function Coupons() {
  const [activeTab, setActiveTab] = useState<'available' | 'mine'>('available')
  const [templates, setTemplates] = useState<CouponTemplate[]>([])
  const [userCoupons, setUserCoupons] = useState<UserCoupon[]>([])
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null)

  useEffect(() => {
    if (toast) {
      const timer = setTimeout(() => setToast(null), 3000)
      return () => clearTimeout(timer)
    }
  }, [toast])

  const fetchTemplates = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await listTemplates({ status: 'ENABLED' })
      setTemplates(res.data ?? [])
    } catch {
      setTemplates([])
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchUserCoupons = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await listUserCoupons()
      setUserCoupons(res.data ?? [])
    } catch {
      setUserCoupons([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (activeTab === 'available') {
      fetchTemplates()
    } else {
      fetchUserCoupons()
    }
  }, [activeTab, fetchTemplates, fetchUserCoupons])

  const handleClaim = async (templateId: number) => {
    try {
      await claimCoupon(templateId)
      setToast({ message: '领取成功！', type: 'success' })
      fetchTemplates()
    } catch {
      setToast({ message: '领取失败，请稍后重试', type: 'error' })
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--color-bg-base)' }}>
      {toast && (
        <div
          style={{
            position: 'fixed',
            top: 24,
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 9999,
            padding: '12px 32px',
            borderRadius: '10px',
            background: toast.type === 'success' ? 'rgba(var(--color-primary-rgb), 0.15)' : 'rgba(255,71,87,0.15)',
            border: `1px solid ${toast.type === 'success' ? 'rgba(var(--color-primary-rgb), 0.3)' : 'rgba(255,71,87,0.3)'}`,
            color: toast.type === 'success' ? 'var(--color-primary)' : '#FF4757',
            backdropFilter: 'blur(12px)',
            fontSize: 14,
            fontWeight: 500,
          }}
        >
          {toast.message}
        </div>
      )}

      <div
        style={{
          background: 'var(--color-gradient-hero)',
          padding: '56px 24px 40px',
          textAlign: 'center',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            width: 500,
            height: 350,
            background: 'radial-gradient(ellipse at center, rgba(var(--color-primary-rgb), 0.06) 0%, transparent 70%)',
            borderRadius: '50%',
            filter: 'blur(60px)',
          }}
        />
        <div style={{ position: 'relative' }}>
          <div style={{ fontSize: 14, color: 'var(--color-primary)', letterSpacing: 4, marginBottom: 16, fontWeight: 600 }}>
            Coupon Center
          </div>
          <h1 style={{ fontSize: 40, fontWeight: 900, color: 'var(--color-text-secondary)', marginBottom: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 12 }}>
            <span style={{ fontSize: 36 }}>🎁</span>
            领券<span style={{ color: 'var(--color-primary)' }}>中心</span>
          </h1>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 16 }}>
            领券立享优惠，省钱购物更开心
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 960, margin: '0 auto', padding: '0 24px' }}>
        <div
          style={{
            display: 'flex',
            gap: 0,
            borderBottom: '1px solid var(--color-border)',
            marginBottom: 28,
          }}
        >
          {([
            { key: 'available' as const, label: '可领取' },
            { key: 'mine' as const, label: '我的优惠券' },
          ]).map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              style={{
                padding: '14px 32px',
                border: 'none',
                background: 'transparent',
                color: activeTab === tab.key ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                fontSize: 15,
                fontWeight: activeTab === tab.key ? 700 : 400,
                cursor: 'pointer',
                transition: 'color 0.3s ease',
                borderBottom: activeTab === tab.key ? '2px solid var(--color-primary)' : '2px solid transparent',
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {loading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
            <div
              style={{
                width: 40,
                height: 40,
                border: '3px solid var(--color-border)',
                borderTopColor: 'var(--color-primary)',
                borderRadius: '50%',
                animation: 'spin 0.8s linear infinite',
              }}
            />
          </div>
        ) : activeTab === 'available' ? (
          templates.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '80px 0' }}>
              <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }}>🎫</div>
              <div style={{ color: 'var(--color-text-tertiary)', fontSize: 15 }}>暂无可领取的优惠券</div>
            </div>
          ) : (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(2, 1fr)',
                gap: 16,
                paddingBottom: 80,
              }}
            >
              {templates.map((template) => (
                <CouponTemplateCard key={template.id} template={template} onClaim={handleClaim} />
              ))}
            </div>
          )
        ) : userCoupons.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '80px 0' }}>
            <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }}>🎫</div>
            <div style={{ color: 'var(--color-text-tertiary)', fontSize: 15 }}>暂无优惠券</div>
          </div>
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: 16,
              paddingBottom: 80,
            }}
          >
            {userCoupons.map((coupon) => (
              <UserCouponCard key={coupon.id} coupon={coupon} />
            ))}
          </div>
        )}
      </div>

      <style>{`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
        @media (max-width: 640px) {
          div[style*="grid-template-columns: repeat(2"] {
            grid-template-columns: 1fr !important;
          }
        }
      `}</style>
    </div>
  )
}
