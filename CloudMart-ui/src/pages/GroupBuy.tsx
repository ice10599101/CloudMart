import { useState, useEffect, useCallback } from 'react'
import {
  listGroupActivities,
  openGroup,
  getGroupOrders,
} from '@/api/marketing'
import type { GroupActivity, GroupOrder } from '@/api/marketing'

function GroupActivityCard({
  activity,
  onJoin,
  onToast,
}: {
  activity: GroupActivity
  onJoin: (activityId: number) => void
  onToast: (message: string, type: 'success' | 'error') => void
}) {
  const [joining, setJoining] = useState(false)
  const progressPercent = activity.targetNumber > 0
    ? Math.round((activity.currentGroups / activity.targetNumber) * 100)
    : 0
  const isActive = activity.status === 'ENABLED'

  const handleJoin = async () => {
    setJoining(true)
    try {
      await openGroup(activity.id)
      onToast('参团成功', 'success')
      onJoin(activity.id)
    } catch {
      onToast('参团失败', 'error')
    } finally {
      setJoining(false)
    }
  }

  const timeRemaining = () => {
    if (!activity.endTime) return null
    const end = new Date(activity.endTime).getTime()
    const diff = Math.max(0, Math.floor((end - Date.now()) / 1000))
    if (diff <= 0) return '已结束'
    const h = Math.floor(diff / 3600)
    const m = Math.floor((diff % 3600) / 60)
    return h > 0 ? `${h}时${m}分` : `${m}分钟`
  }

  return (
    <div style={{
      background: 'var(--color-bg-container)',
      border: '1px solid var(--color-border)',
      borderRadius: '16px',
      overflow: 'hidden',
      transition: 'all 0.3s ease',
    }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
        e.currentTarget.style.boxShadow = '0 8px 40px rgba(0, 0, 0, 0.4), 0 0 20px rgba(var(--color-primary-rgb), 0.08)'
        e.currentTarget.style.transform = 'translateY(-4px)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'var(--color-border)'
        e.currentTarget.style.boxShadow = 'none'
        e.currentTarget.style.transform = 'translateY(0)'
      }}
    >
      <div style={{
        height: 200,
        background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.06), rgba(0,153,204,0.12))',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        position: 'relative',
        overflow: 'hidden',
      }}>
        {activity.productId ? (
          <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--color-text-tertiary)', fontSize: 12 }}>
            商品 #{activity.productId}
          </div>
        ) : (
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="rgba(var(--color-primary-rgb), 0.4)" strokeWidth="1.5">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
            <circle cx="9" cy="7" r="4" />
            <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
            <path d="M16 3.13a4 4 0 0 1 0 7.75" />
          </svg>
        )}
        {isActive && (
          <div style={{
            position: 'absolute',
            top: 12,
            right: 12,
            padding: '4px 10px',
            borderRadius: '6px',
            background: 'rgba(var(--color-primary-rgb), 0.15)',
            border: '1px solid rgba(var(--color-primary-rgb), 0.3)',
            color: 'var(--color-primary)',
            fontSize: 12,
            fontWeight: 600,
          }}>
            拼团中
          </div>
        )}
      </div>

      <div style={{ padding: 20 }}>
        <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {activity.name}
        </div>

        <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 16 }}>
          <span style={{ fontSize: 28, fontWeight: 800, color: 'var(--color-primary)', textShadow: '0 0 20px rgba(var(--color-primary-rgb), 0.3)' }}>
            ¥{activity.groupPrice.toFixed(2)}
          </span>
          <span style={{ fontSize: 14, color: 'var(--color-text-tertiary)', textDecoration: 'line-through' }}>
            ¥{activity.originalPrice.toFixed(2)}
          </span>
        </div>

        <div style={{ marginBottom: 12 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
            <span style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>
              {activity.currentGroups}/{activity.targetNumber}人成团
            </span>
            {timeRemaining() && (
              <span style={{ fontSize: 12, color: '#FFA502' }}>{timeRemaining()}</span>
            )}
          </div>
          <div style={{
            height: 6,
            background: 'var(--color-border)',
            borderRadius: 3,
            overflow: 'hidden',
          }}>
            <div style={{
              height: '100%',
              width: `${progressPercent}%`,
              background: 'linear-gradient(90deg, var(--color-primary), var(--color-primary-dark))',
              borderRadius: 3,
              transition: 'width 0.3s ease',
              boxShadow: '0 0 10px rgba(var(--color-primary-rgb), 0.3)',
            }} />
          </div>
        </div>

        <button
          type="button"
          onClick={handleJoin}
          disabled={!isActive || joining}
          style={{
            width: '100%',
            padding: '12px 0',
            border: 'none',
            borderRadius: '10px',
            fontSize: 15,
            fontWeight: 700,
            cursor: !isActive || joining ? 'not-allowed' : 'pointer',
            background: !isActive
              ? 'var(--color-bg-elevated)'
              : 'var(--color-gradient-primary)',
            color: !isActive ? 'var(--color-text-tertiary)' : '#fff',
            boxShadow: !isActive ? 'none' : '0 4px 16px rgba(var(--color-primary-rgb), 0.3)',
            transition: 'all 0.3s ease',
            letterSpacing: 1,
          }}
        >
          {joining ? '参团中...' : isActive ? '参与拼团' : '已结束'}
        </button>
      </div>
    </div>
  )
}

function GroupOrderCard({ order }: { order: GroupOrder }) {
  const isPending = order.status === 'PENDING' || order.status === 'WAITING'
  const isCompleted = order.status === 'COMPLETED' || order.status === 'SUCCESS'
  const progressPercent = order.targetNumber > 0
    ? Math.round((order.currentNumber / order.targetNumber) * 100)
    : 0

  const statusConfig = isPending
    ? { label: '拼团中', color: '#FFA502' }
    : isCompleted
      ? { label: '拼团成功', color: '#2ED573' }
      : { label: '拼团失败', color: 'var(--color-text-tertiary)' }

  return (
    <div style={{
      background: 'var(--color-bg-container)',
      border: '1px solid var(--color-border)',
      borderRadius: '10px',
      padding: 20,
      marginBottom: 12,
      transition: 'all 0.3s ease',
    }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'var(--color-border)'
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
            <span style={{ color: 'var(--color-text-secondary)', fontSize: 14, fontWeight: 600 }}>
              团长：用户#{order.leaderUserId}
            </span>
          </div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 13, marginBottom: 8 }}>
            {order.currentNumber}/{order.targetNumber}人成团
          </div>
          <div style={{
            height: 4,
            background: 'var(--color-border)',
            borderRadius: 2,
            overflow: 'hidden',
            maxWidth: 200,
          }}>
            <div style={{
              height: '100%',
              width: `${progressPercent}%`,
              background: 'linear-gradient(90deg, var(--color-primary), var(--color-primary-dark))',
              borderRadius: 2,
            }} />
          </div>
        </div>
        <span style={{
          padding: '4px 12px',
          borderRadius: '6px',
          background: `${statusConfig.color}15`,
          color: statusConfig.color,
          fontSize: 13,
          fontWeight: 600,
        }}>
          {statusConfig.label}
        </span>
      </div>
    </div>
  )
}

export default function GroupBuyPage() {
  const [activities, setActivities] = useState<GroupActivity[]>([])
  const [groupOrders, setGroupOrders] = useState<GroupOrder[]>([])
  const [loading, setLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<'activities' | 'orders'>('activities')
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
      const { data: res } = await listGroupActivities(1, 20)
      setActivities(res.data?.records ?? [])
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchGroupOrders = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await getGroupOrders(undefined, 1, 20)
      setGroupOrders(res.data?.records ?? [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (activeTab === 'activities') {
      fetchActivities()
    } else {
      fetchGroupOrders()
    }
  }, [activeTab, fetchActivities, fetchGroupOrders])

  const handleJoin = () => {
    fetchActivities()
    fetchGroupOrders()
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
          background: 'radial-gradient(ellipse at center, rgba(var(--color-primary-rgb), 0.06) 0%, transparent 70%)',
          borderRadius: '50%',
          filter: 'blur(60px)',
        }} />
        <div style={{ position: 'relative' }}>
          <div style={{ fontSize: 14, color: 'var(--color-primary)', letterSpacing: 4, marginBottom: 16, fontWeight: 600 }}>
            Group Buy
          </div>
          <h1 style={{ fontSize: 44, fontWeight: 900, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
            拼团<span style={{ color: 'var(--color-primary)' }}>专区</span>
          </h1>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 16 }}>
            一起拼，更优惠
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 1280, margin: '0 auto', padding: '0 24px' }}>
        <div style={{
          display: 'flex',
          gap: 0,
          borderBottom: '1px solid var(--color-border)',
          marginBottom: 32,
        }}>
          {([
            { key: 'activities' as const, label: '拼团活动' },
            { key: 'orders' as const, label: '我的拼团' },
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
                position: 'relative',
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
              borderTopColor: 'var(--color-primary)',
              borderRadius: '50%',
              animation: 'spin 0.8s linear infinite',
            }} />
          </div>
        ) : activeTab === 'activities' ? (
          activities.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '80px 0' }}>
              <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }}>👥</div>
              <div style={{ color: 'var(--color-text-tertiary)', fontSize: 15 }}>暂无拼团活动</div>
            </div>
          ) : (
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
              gap: 24,
              paddingBottom: 80,
            }}>
              {activities.map((activity) => (
                <GroupActivityCard
                  key={activity.id}
                  activity={activity}
                  onJoin={handleJoin}
                  onToast={(msg, type) => setToast({ message: msg, type })}
                />
              ))}
            </div>
          )
        ) : groupOrders.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '80px 0' }}>
            <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }}>📋</div>
            <div style={{ color: 'var(--color-text-tertiary)', fontSize: 15 }}>暂无拼团订单</div>
          </div>
        ) : (
          <div style={{ paddingBottom: 80 }}>
            {groupOrders.map((order) => (
              <GroupOrderCard key={order.id} order={order} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
