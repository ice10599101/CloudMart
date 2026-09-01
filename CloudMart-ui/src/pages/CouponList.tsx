import { useState, useEffect, useCallback } from 'react'
import { listTemplates, listUserCoupons, claimCoupon } from '@/api/coupon'
import type { CouponTemplate, UserCoupon, CouponType, UserCouponStatus } from '@/types'

const COUPON_TYPE_CONFIG: Record<CouponType, { label: string; accent: string }> = {
  AMOUNT_OFF: { label: '满减', accent: 'var(--color-primary)' },
  PERCENT_OFF: { label: '折扣', accent: '#2ED573' },
}

const USER_COUPON_TABS: { key: UserCouponStatus | 'ALL'; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'UNUSED', label: '未使用' },
  { key: 'USED', label: '已使用' },
  { key: 'EXPIRED', label: '已过期' },
]

function formatDiscount(couponType: CouponType, discountAmount?: number, discountRate?: number): string {
  if (couponType === 'AMOUNT_OFF') {
    return `¥${discountAmount}`
  }
  return `${((1 - (discountRate ?? 1)) * 100).toFixed(0)}折`
}

function CouponTemplateCard({ template, onClaim }: { template: CouponTemplate; onClaim: (id: number) => Promise<void> }) {
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
    <div style={{
      background: 'var(--color-bg-container)',
      border: '1px solid var(--color-border)',
      borderLeft: `3px solid ${typeConfig.accent}`,
      borderRadius: '10px',
      overflow: 'hidden',
      transition: 'all 0.3s ease',
      display: 'flex',
    }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
        e.currentTarget.style.boxShadow = '0 8px 40px rgba(0, 0, 0, 0.4), 0 0 20px rgba(var(--color-primary-rgb), 0.08)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'var(--color-border)'
        e.currentTarget.style.boxShadow = 'none'
      }}
    >
      <div style={{
        width: 130,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
        background: `${typeConfig.accent}08`,
        flexShrink: 0,
      }}>
        <span style={{ fontSize: 32, fontWeight: 900, color: typeConfig.accent, textShadow: `0 0 20px ${typeConfig.accent}40` }}>
          {formatDiscount(template.type, template.discountAmount, template.discountRate)}
        </span>
        <span style={{
          padding: '2px 8px',
          borderRadius: '6px',
          background: `${typeConfig.accent}15`,
          color: typeConfig.accent,
          fontSize: 12,
          fontWeight: 600,
          marginTop: 8,
        }}>
          {typeConfig.label}
        </span>
      </div>

      <div style={{ flex: 1, padding: '20px 24px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
        <div>
          <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 6 }}>
            {template.name}
          </div>
          <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', marginBottom: 4 }}>
            满¥{template.thresholdAmount}可用
          </div>
          {template.validityType === 'FIXED_DATE' && template.endTime && (
            <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
              有效期至 {new Date(template.endTime).toLocaleDateString()}
            </div>
          )}
          {template.validityType === 'FIXED_DAYS' && template.validDays && (
            <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
              领取后{template.validDays}天内有效
            </div>
          )}
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 12 }}>
          <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
            剩余 {template.remainingQuantity}/{template.totalQuantity}
          </span>
          <button
            type="button"
            onClick={handleClaim}
            disabled={!canClaim || claiming}
            style={{
              padding: '6px 20px',
              border: 'none',
              borderRadius: '6px',
              fontSize: 13,
              fontWeight: 600,
              cursor: !canClaim || claiming ? 'not-allowed' : 'pointer',
              background: canClaim
                ? 'var(--color-gradient-primary)'
                : 'var(--color-bg-elevated)',
              color: canClaim ? '#fff' : 'var(--color-text-tertiary)',
              boxShadow: canClaim ? '0 2px 12px rgba(var(--color-primary-rgb), 0.25)' : 'none',
              transition: 'all 0.3s ease',
            }}
          >
            {claiming ? '领取中...' : canClaim ? '领取' : '已领完'}
          </button>
        </div>
      </div>
    </div>
  )
}

function UserCouponCard({ coupon }: { coupon: UserCoupon }) {
  const typeConfig = COUPON_TYPE_CONFIG[coupon.templateType]
  const isUsed = coupon.status === 'USED'
  const isExpired = coupon.status === 'EXPIRED'
  const isDisabled = isUsed || isExpired

  return (
    <div style={{
      background: 'var(--color-bg-container)',
      border: '1px solid var(--color-border)',
      borderLeft: `3px solid ${isDisabled ? 'var(--color-text-tertiary)' : typeConfig.accent}`,
      borderRadius: '10px',
      overflow: 'hidden',
      transition: 'all 0.3s ease',
      opacity: isDisabled ? 0.6 : 1,
      display: 'flex',
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
      <div style={{
        width: 130,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
        background: isDisabled ? 'var(--color-border)' : `${typeConfig.accent}08`,
        flexShrink: 0,
      }}>
        <span style={{ fontSize: 32, fontWeight: 900, color: isDisabled ? 'var(--color-text-tertiary)' : typeConfig.accent }}>
          {formatDiscount(coupon.templateType, coupon.discountAmount, coupon.discountRate)}
        </span>
        <span style={{
          padding: '2px 8px',
          borderRadius: '6px',
          background: isDisabled ? 'var(--color-border)' : `${typeConfig.accent}15`,
          color: isDisabled ? 'var(--color-text-tertiary)' : typeConfig.accent,
          fontSize: 12,
          fontWeight: 600,
          marginTop: 8,
        }}>
          {typeConfig.label}
        </span>
      </div>

      <div style={{ flex: 1, padding: '20px 24px' }}>
        <div style={{
          fontSize: 16,
          fontWeight: 600,
          color: 'var(--color-text-secondary)',
          marginBottom: 6,
          textDecoration: isDisabled ? 'line-through' : 'none',
        }}>
          {coupon.templateName}
        </div>
        <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', marginBottom: 4 }}>
          满¥{coupon.thresholdAmount}可用
        </div>
        {coupon.expiredAt && (
          <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginBottom: 8 }}>
            有效期至 {new Date(coupon.expiredAt).toLocaleDateString()}
          </div>
        )}
        <div>
          {isUsed && (
            <span style={{ padding: '2px 8px', borderRadius: '6px', background: 'var(--color-border)', color: 'var(--color-text-tertiary)', fontSize: 12, fontWeight: 600 }}>已使用</span>
          )}
          {isExpired && (
            <span style={{ padding: '2px 8px', borderRadius: '6px', background: 'var(--color-border)', color: 'var(--color-text-tertiary)', fontSize: 12, fontWeight: 600 }}>已过期</span>
          )}
          {!isDisabled && (
            <span style={{ padding: '2px 8px', borderRadius: '6px', background: 'rgba(46,213,115,0.15)', color: '#2ED573', fontSize: 12, fontWeight: 600 }}>可使用</span>
          )}
        </div>
      </div>
    </div>
  )
}

export default function CouponListPage() {
  const [activeTab, setActiveTab] = useState<'available' | 'mine'>('available')
  const [templates, setTemplates] = useState<CouponTemplate[]>([])
  const [userCoupons, setUserCoupons] = useState<UserCoupon[]>([])
  const [userCouponStatus, setUserCouponStatus] = useState<UserCouponStatus | 'ALL'>('ALL')
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
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchUserCoupons = useCallback(async (status?: string) => {
    setLoading(true)
    try {
      const { data: res } = await listUserCoupons({ status: status === 'ALL' ? undefined : status })
      setUserCoupons(res.data ?? [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (activeTab === 'available') {
      fetchTemplates()
    } else {
      fetchUserCoupons(userCouponStatus)
    }
  }, [activeTab, userCouponStatus, fetchTemplates, fetchUserCoupons])

  const handleClaim = async (templateId: number) => {
    try {
      await claimCoupon(templateId)
      setToast({ message: '领取成功', type: 'success' })
      fetchTemplates()
    } catch {
      setToast({ message: '领取失败', type: 'error' })
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--color-bg-base)' }}>
      {toast && (
        <div style={{
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
          animation: 'fadeIn 0.3s ease',
        }}>
          {toast.message}
        </div>
      )}

      <div style={{
        background: 'var(--color-gradient-hero)',
        padding: '64px 24px 48px',
        textAlign: 'center',
        position: 'relative',
        overflow: 'hidden',
      }}>
        <div style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: 500,
          height: 350,
          background: 'radial-gradient(ellipse at center, rgba(46,213,115,0.06) 0%, transparent 70%)',
          borderRadius: '50%',
          filter: 'blur(60px)',
        }} />
        <div style={{ position: 'relative' }}>
          <div style={{ fontSize: 14, color: '#2ED573', letterSpacing: 4, marginBottom: 16, fontWeight: 600 }}>
            Coupon Center
          </div>
          <h1 style={{ fontSize: 44, fontWeight: 900, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
            优惠券<span style={{ color: '#2ED573' }}>中心</span>
          </h1>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 16 }}>
            领券立享优惠
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 900, margin: '0 auto', padding: '0 24px' }}>
        <div style={{
          display: 'flex',
          gap: 0,
          borderBottom: '1px solid var(--color-border)',
          marginBottom: 32,
        }}>
          {([
            { key: 'available' as const, label: '可领取' },
            { key: 'mine' as const, label: '我的优惠券' },
          ]).map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              style={{
                padding: '16px 32px',
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
            <div style={{
              width: 40,
              height: 40,
              border: '3px solid var(--color-border)',
              borderTopColor: '#2ED573',
              borderRadius: '50%',
              animation: 'spin 0.8s linear infinite',
            }} />
          </div>
        ) : activeTab === 'available' ? (
          templates.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '80px 0' }}>
              <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }}>🎫</div>
              <div style={{ color: 'var(--color-text-tertiary)', fontSize: 15 }}>暂无可领取的优惠券</div>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16, paddingBottom: 80 }}>
              {templates.map((template) => (
                <CouponTemplateCard key={template.id} template={template} onClaim={handleClaim} />
              ))}
            </div>
          )
        ) : (
          <>
            <div style={{
              display: 'flex',
              gap: 0,
              marginBottom: 24,
              borderBottom: '1px solid var(--color-border)',
            }}>
              {USER_COUPON_TABS.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setUserCouponStatus(tab.key)}
                  style={{
                    padding: '10px 20px',
                    border: 'none',
                    background: 'transparent',
                    color: userCouponStatus === tab.key ? 'var(--color-primary)' : 'var(--color-text-tertiary)',
                    fontSize: 13,
                    fontWeight: userCouponStatus === tab.key ? 600 : 400,
                    cursor: 'pointer',
                    transition: 'color 0.3s ease',
                    borderBottom: userCouponStatus === tab.key ? '2px solid var(--color-primary)' : '2px solid transparent',
                  }}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {userCoupons.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '80px 0' }}>
                <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }}>🎫</div>
                <div style={{ color: 'var(--color-text-tertiary)', fontSize: 15 }}>暂无优惠券</div>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16, paddingBottom: 80 }}>
                {userCoupons.map((coupon) => (
                  <UserCouponCard key={coupon.id} coupon={coupon} />
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
