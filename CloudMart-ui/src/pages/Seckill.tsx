import { useState, useEffect, useCallback, useRef } from 'react'
import { listActivities, listProductsByActivity, executeSeckill, getSeckillResult } from '@/api/seckill'
import type { SeckillActivity, SeckillProduct, SeckillResult, SeckillActivityStatus } from '@/types'

const STATUS_MAP: Record<SeckillActivityStatus, { label: string; color: string }> = {
  UPCOMING: { label: '即将开始', color: '#FFA502' },
  ONGOING: { label: '抢购中', color: '#FF4757' },
  ENDED: { label: '已结束', color: 'var(--color-text-tertiary)' },
}

function useCountdown(targetTime: string) {
  const [remaining, setRemaining] = useState(0)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    const update = () => {
      const target = new Date(targetTime).getTime()
      const diff = Math.max(0, Math.floor((target - Date.now()) / 1000))
      setRemaining(diff)
      if (diff <= 0 && timerRef.current) {
        clearInterval(timerRef.current)
        timerRef.current = null
      }
    }
    update()
    timerRef.current = setInterval(update, 1000)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [targetTime])

  const hours = Math.floor(remaining / 3600).toString().padStart(2, '0')
  const minutes = Math.floor((remaining % 3600) / 60).toString().padStart(2, '0')
  const seconds = (remaining % 60).toString().padStart(2, '0')

  return { remaining, hours, minutes, seconds, isExpired: remaining <= 0 }
}

function CountdownDigit({ value }: { value: string }) {
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: 52,
      height: 64,
      background: 'rgba(var(--color-primary-rgb), 0.1)',
      border: '1px solid rgba(var(--color-primary-rgb), 0.3)',
      borderRadius: '10px',
      fontSize: 32,
      fontWeight: 800,
      color: 'var(--color-primary)',
      textShadow: '0 0 20px rgba(var(--color-primary-rgb), 0.5)',
      fontFamily: 'var(--font-mono)',
    }}>
      {value}
    </span>
  )
}

function CountdownSeparator() {
  return (
    <span style={{
      fontSize: 32,
      fontWeight: 800,
      color: 'var(--color-primary)',
      margin: '0 6px',
      textShadow: '0 0 20px rgba(var(--color-primary-rgb), 0.5)',
      fontFamily: 'var(--font-mono)',
    }}>:</span>
  )
}

function HeroCountdown({ targetTime, prefix }: { targetTime: string; prefix: string }) {
  const { hours, minutes, seconds, isExpired } = useCountdown(targetTime)
  if (isExpired) return null
  return (
    <div style={{ marginTop: 32 }}>
      <div style={{ color: 'var(--color-text-secondary)', fontSize: 14, marginBottom: 12, letterSpacing: 2 }}>{prefix}</div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0 }}>
        <CountdownDigit value={hours[0]} />
        <CountdownDigit value={hours[1]} />
        <CountdownSeparator />
        <CountdownDigit value={minutes[0]} />
        <CountdownDigit value={minutes[1]} />
        <CountdownSeparator />
        <CountdownDigit value={seconds[0]} />
        <CountdownDigit value={seconds[1]} />
      </div>
    </div>
  )
}

function SeckillProductCard({
  product,
  activityStatus,
  activityId,
  onSeckillSuccess,
  onToast,
}: {
  product: SeckillProduct
  activityStatus: SeckillActivityStatus
  activityId: number
  onSeckillSuccess: (result: SeckillResult) => void
  onToast: (message: string, type: 'success' | 'error') => void
}) {
  const [seckilling, setSeckilling] = useState(false)
  const [result, setResult] = useState<SeckillResult | null>(null)
  const [showResult, setShowResult] = useState(false)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const soldPercent = product.totalStock > 0
    ? Math.round(((product.totalStock - product.availableStock) / product.totalStock) * 100)
    : 100

  const isOngoing = activityStatus === 'ONGOING'
  const isUpcoming = activityStatus === 'UPCOMING'
  const isEnded = activityStatus === 'ENDED'
  const isSoldOut = product.availableStock <= 0

  const handleSeckill = async () => {
    setSeckilling(true)
    setResult(null)
    try {
      const { data: res } = await executeSeckill(activityId, product.id)
      const seckillResult = res.data
      setResult(seckillResult)

      if (seckillResult.status === 'PENDING') {
        pollRef.current = setInterval(async () => {
          try {
            const { data: pollRes } = await getSeckillResult(activityId, product.id)
            const pollResult = pollRes.data
            setResult(pollResult)
            if (pollResult.status !== 'PENDING') {
              if (pollRef.current) clearInterval(pollRef.current)
              if (pollResult.status === 'SUCCESS') {
                onSeckillSuccess(pollResult)
                onToast('抢购成功！', 'success')
              } else {
                onToast(pollResult.message || '抢购失败', 'error')
              }
              setShowResult(true)
            }
          } catch {
            if (pollRef.current) clearInterval(pollRef.current)
          }
        }, 2000)
      } else if (seckillResult.status === 'SUCCESS') {
        onSeckillSuccess(seckillResult)
        onToast('抢购成功！', 'success')
        setShowResult(true)
      } else {
        onToast(seckillResult.message || '抢购失败', 'error')
        setShowResult(true)
      }
    } catch {
      onToast('抢购请求失败', 'error')
    } finally {
      setSeckilling(false)
    }
  }

  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
    }
  }, [])

  const getButtonText = () => {
    if (isUpcoming) return '即将开始'
    if (isEnded || isSoldOut) return '已售罄'
    if (seckilling) return '抢购中...'
    if (result?.status === 'PENDING') return '排队中...'
    return '立即抢购'
  }

  const isButtonDisabled = isUpcoming || isEnded || isSoldOut || seckilling || result?.status === 'PENDING'

  return (
    <>
      <div style={{
        background: 'var(--color-bg-container)',
        border: '1px solid var(--color-border)',
        borderRadius: '16px',
        overflow: 'hidden',
        transition: 'all 0.3s ease',
        cursor: isButtonDisabled ? 'default' : 'pointer',
      }}
        onMouseEnter={(e) => {
          if (!isButtonDisabled) {
            e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
            e.currentTarget.style.boxShadow = '0 8px 40px rgba(0, 0, 0, 0.4), 0 0 20px rgba(var(--color-primary-rgb), 0.08)'
            e.currentTarget.style.transform = 'translateY(-4px)'
          }
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.borderColor = 'var(--color-border)'
          e.currentTarget.style.boxShadow = 'none'
          e.currentTarget.style.transform = 'translateY(0)'
        }}
      >
        <div style={{
          height: 200,
          background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.08), rgba(0,153,204,0.15))',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          position: 'relative',
          overflow: 'hidden',
        }}>
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="rgba(var(--color-primary-rgb), 0.4)" strokeWidth="1.5">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
          </svg>
          {isOngoing && !isSoldOut && (
            <div style={{
              position: 'absolute',
              top: 12,
              right: 12,
              background: 'rgba(255,71,87,0.9)',
              color: '#fff',
              padding: '4px 10px',
              borderRadius: '6px',
              fontSize: 12,
              fontWeight: 700,
              animation: 'pulse 2s infinite',
            }}>
              HOT
            </div>
          )}
        </div>

        <div style={{ padding: 20 }}>
          <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {`商品SKU-${product.skuId}`}
          </div>

          <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 16 }}>
            <span style={{ fontSize: 28, fontWeight: 800, color: 'var(--color-primary)', textShadow: '0 0 20px rgba(var(--color-primary-rgb), 0.3)' }}>
              ¥{product.seckillPrice.toFixed(2)}
            </span>
            <span style={{ fontSize: 14, color: 'var(--color-text-tertiary)', textDecoration: 'line-through' }}>
              ¥{product.originalPrice.toFixed(2)}
            </span>
          </div>

          <div style={{ marginBottom: 8 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
              <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>已抢{soldPercent}%</span>
              <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>限购{product.perUserLimit}件</span>
            </div>
            <div style={{
              height: 6,
              background: 'var(--color-border)',
              borderRadius: 3,
              overflow: 'hidden',
            }}>
              <div style={{
                height: '100%',
                width: `${soldPercent}%`,
                background: soldPercent >= 90
                  ? 'linear-gradient(90deg, #FF4757, #FF6B81)'
                  : 'linear-gradient(90deg, var(--color-primary), var(--color-primary-dark))',
                borderRadius: 3,
                transition: 'width 0.3s ease',
                boxShadow: soldPercent >= 90
                  ? '0 0 10px rgba(255,71,87,0.4)'
                  : '0 0 10px rgba(var(--color-primary-rgb), 0.3)',
              }} />
            </div>
          </div>

          <button
            type="button"
            onClick={handleSeckill}
            disabled={isButtonDisabled}
            style={{
              width: '100%',
              padding: '12px 0',
              border: 'none',
              borderRadius: '10px',
              fontSize: 15,
              fontWeight: 700,
              cursor: isButtonDisabled ? 'not-allowed' : 'pointer',
              background: isButtonDisabled
                ? 'var(--color-bg-elevated)'
                : 'var(--color-gradient-primary)',
              color: isButtonDisabled
                ? 'var(--color-text-tertiary)'
                : '#fff',
              boxShadow: isButtonDisabled
                ? 'none'
                : '0 4px 16px rgba(var(--color-primary-rgb), 0.3)',
              transition: 'all 0.3s ease',
              letterSpacing: 1,
            }}
          >
            {getButtonText()}
          </button>
        </div>
      </div>

      {showResult && result && result.status !== 'PENDING' && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0,0,0,0.7)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            backdropFilter: 'blur(4px)',
          }}
          onClick={() => setShowResult(false)}
        >
          <div
            style={{
              background: 'var(--color-bg-container)',
              border: '1px solid var(--color-border)',
              borderRadius: '16px',
              padding: 40,
              minWidth: 360,
              textAlign: 'center',
              animation: 'slideUp 0.3s ease',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{
              fontSize: 48,
              marginBottom: 16,
            }}>
              {result.status === 'SUCCESS' ? '🎉' : '😔'}
            </div>
            <div style={{
              fontSize: 22,
              fontWeight: 700,
              color: result.status === 'SUCCESS' ? 'var(--color-primary)' : '#FF4757',
              marginBottom: 12,
            }}>
              {result.status === 'SUCCESS' ? '抢购成功' : '抢购失败'}
            </div>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: 14, marginBottom: 24 }}>
              {result.message}
            </div>
            <button
              type="button"
              onClick={() => setShowResult(false)}
              style={{
                padding: '10px 40px',
                border: 'none',
                borderRadius: '10px',
                background: 'var(--color-gradient-primary)',
                color: '#fff',
                fontSize: 14,
                fontWeight: 600,
                cursor: 'pointer',
                boxShadow: '0 4px 16px rgba(var(--color-primary-rgb), 0.3)',
              }}
            >
              关闭
            </button>
          </div>
        </div>
      )}
    </>
  )
}

function SmallCountdown({ targetTime }: { targetTime: string }) {
  const { hours, minutes, seconds, isExpired } = useCountdown(targetTime)
  if (isExpired) return null
  return (
    <div style={{ fontSize: 12, color: 'var(--color-primary)', marginTop: 6, fontFamily: 'var(--font-mono)' }}>
      {hours}:{minutes}:{seconds}
    </div>
  )
}

export default function SeckillPage() {
  const [activities, setActivities] = useState<SeckillActivity[]>([])
  const [selectedActivity, setSelectedActivity] = useState<SeckillActivity | null>(null)
  const [products, setProducts] = useState<SeckillProduct[]>([])
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null)

  useEffect(() => {
    if (toast) {
      const timer = setTimeout(() => setToast(null), 3000)
      return () => clearTimeout(timer)
    }
  }, [toast])

  const fetchActivities = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await listActivities()
      const list = res.data ?? []
      setActivities(list)
      const ongoing = list.find((a) => a.status === 'ONGOING')
      if (ongoing) {
        setSelectedActivity(ongoing)
      } else if (list.length > 0) {
        setSelectedActivity(list[0])
      }
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchProducts = useCallback(async (activityId: number) => {
    setLoading(true)
    try {
      const { data: res } = await listProductsByActivity(activityId)
      setProducts(res.data ?? [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchActivities()
  }, [fetchActivities])

  useEffect(() => {
    if (selectedActivity) {
      fetchProducts(selectedActivity.id)
    }
  }, [selectedActivity, fetchProducts])

  const handleSeckillSuccess = () => {
    if (selectedActivity) {
      fetchProducts(selectedActivity.id)
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
        padding: '80px 24px 64px',
        textAlign: 'center',
        position: 'relative',
        overflow: 'hidden',
      }}>
        <div style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: 600,
          height: 400,
          background: 'radial-gradient(ellipse at center, rgba(var(--color-primary-rgb), 0.08) 0%, transparent 70%)',
          borderRadius: '50%',
          filter: 'blur(60px)',
        }} />

        <div style={{ position: 'relative' }}>
          <div style={{
            fontSize: 14,
            color: 'var(--color-primary)',
            letterSpacing: 4,
            textTransform: 'uppercase',
            marginBottom: 16,
            fontWeight: 600,
          }}>
            Flash Sale
          </div>
          <h1 style={{
            fontSize: 52,
            fontWeight: 900,
            color: 'var(--color-text-secondary)',
            marginBottom: 8,
            lineHeight: 1.1,
          }}>
            限时<span style={{ color: 'var(--color-primary)', textShadow: '0 0 30px rgba(var(--color-primary-rgb), 0.4)' }}>秒杀</span>
          </h1>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 16, marginTop: 8 }}>
            超值好物，手慢无
          </div>

          {selectedActivity?.status === 'ONGOING' && (
            <HeroCountdown targetTime={selectedActivity.endTime} prefix="距结束" />
          )}
          {selectedActivity?.status === 'UPCOMING' && (
            <HeroCountdown targetTime={selectedActivity.startTime} prefix="距开始" />
          )}
        </div>
      </div>

      <div style={{ maxWidth: 1280, margin: '0 auto', padding: '0 24px' }}>
        {loading && activities.length === 0 ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
            <div style={{
              width: 40,
              height: 40,
              border: '3px solid var(--color-border)',
              borderTopColor: 'var(--color-primary)',
              borderRadius: '50%',
              animation: 'spin 0.8s linear infinite',
            }} />
          </div>
        ) : activities.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '80px 0' }}>
            <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }}>⚡</div>
            <div style={{ color: 'var(--color-text-tertiary)', fontSize: 15 }}>暂无秒杀活动</div>
          </div>
        ) : (
          <>
            <div style={{
              display: 'flex',
              gap: 12,
              overflowX: 'auto',
              padding: '32px 0 24px',
              scrollbarWidth: 'none',
            }}>
              {activities.map((activity) => {
                const config = STATUS_MAP[activity.status]
                const isSelected = selectedActivity?.id === activity.id
                return (
                  <button
                    key={activity.id}
                    type="button"
                    onClick={() => setSelectedActivity(activity)}
                    style={{
                      flexShrink: 0,
                      padding: '16px 28px',
                      border: '1px solid',
                      borderColor: isSelected ? 'rgba(var(--color-primary-rgb), 0.4)' : 'var(--color-border)',
                      borderRadius: '10px',
                      background: isSelected
                        ? 'rgba(var(--color-primary-rgb), 0.1)'
                        : 'var(--color-bg-container)',
                      color: 'var(--color-text-secondary)',
                      cursor: 'pointer',
                      transition: 'all 0.3s ease',
                      boxShadow: isSelected ? '0 0 20px rgba(var(--color-primary-rgb), 0.1)' : 'none',
                      minWidth: 180,
                      textAlign: 'left',
                    }}
                  >
                    <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 8 }}>{activity.name}</div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span style={{
                        padding: '2px 8px',
                        borderRadius: '6px',
                        background: `${config.color}20`,
                        color: config.color,
                        fontSize: 12,
                        fontWeight: 600,
                      }}>
                        {config.label}
                      </span>
                    </div>
                    {activity.status === 'UPCOMING' && (
                      <SmallCountdown targetTime={activity.startTime} />
                    )}
                    {activity.status === 'ONGOING' && (
                      <SmallCountdown targetTime={activity.endTime} />
                    )}
                  </button>
                )
              })}
            </div>

            {selectedActivity && (
              <>
                <div style={{ marginBottom: 24, padding: '0 0 8px', borderBottom: '1px solid var(--color-border)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <span style={{ fontSize: 22, fontWeight: 700, color: 'var(--color-text-secondary)' }}>
                      {selectedActivity.name}
                    </span>
                    <span style={{
                      padding: '3px 10px',
                      borderRadius: '6px',
                      background: `${STATUS_MAP[selectedActivity.status].color}20`,
                      color: STATUS_MAP[selectedActivity.status].color,
                      fontSize: 12,
                      fontWeight: 600,
                    }}>
                      {STATUS_MAP[selectedActivity.status].label}
                    </span>
                  </div>
                  {selectedActivity.description && (
                    <div style={{ color: 'var(--color-text-secondary)', fontSize: 14, marginTop: 8 }}>
                      {selectedActivity.description}
                    </div>
                  )}
                </div>

                {products.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '80px 0' }}>
                    <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }}>📦</div>
                    <div style={{ color: 'var(--color-text-tertiary)', fontSize: 15 }}>暂无秒杀商品</div>
                  </div>
                ) : (
                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
                    gap: 24,
                    paddingBottom: 80,
                  }}>
                    {products.map((product) => (
                      <SeckillProductCard
                        key={product.id}
                        product={product}
                        activityStatus={selectedActivity.status}
                        activityId={selectedActivity.id}
                        onSeckillSuccess={handleSeckillSuccess}
                        onToast={(msg, type) => setToast({ message: msg, type })}
                      />
                    ))}
                  </div>
                )}
              </>
            )}
          </>
        )}
      </div>
    </div>
  )
}
