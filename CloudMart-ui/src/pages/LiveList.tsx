import { useState, useEffect, useCallback } from 'react'
import { history } from 'umi'
import { listLiveRooms } from '@/api/live'
import type { LiveRoom } from '@/api/live'
import { formatCount } from '@/utils/format'

function LiveRoomCard({ room }: { room: LiveRoom }) {
  const isLive = room.status === 'LIVE'

  return (
    <div
      style={{
        background: 'var(--color-bg-container)',
        border: '1px solid var(--color-border)',
        borderRadius: '16px',
        overflow: 'hidden',
        transition: 'all 0.3s ease',
        cursor: isLive ? 'pointer' : 'default',
      }}
      onClick={() => {
        if (isLive) history.push(`/live/${room.id}`)
      }}
      onMouseEnter={(e) => {
        if (isLive) {
          e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
          e.currentTarget.style.boxShadow = '0 8px 40px rgba(0, 0, 0, 0.4), 0 0 20px rgba(var(--color-primary-rgb), 0.08)'
          e.currentTarget.style.transform = 'translateY(-6px)'
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
        background: `linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.05), rgba(0,153,204,0.1))`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        position: 'relative',
        overflow: 'hidden',
      }}>
        {room.coverImage ? (
          <img src={room.coverImage} alt={room.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        ) : (
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="rgba(var(--color-primary-rgb), 0.3)" strokeWidth="1.5">
            <path d="M23 7l-7 5 7 5V7z" />
            <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
          </svg>
        )}

        {isLive && (
          <div style={{
            position: 'absolute',
            top: 12,
            left: 12,
            display: 'flex',
            alignItems: 'center',
            gap: 5,
            padding: '4px 10px',
            borderRadius: '6px',
            background: 'rgba(255,71,87,0.9)',
            backdropFilter: 'blur(8px)',
          }}>
            <span style={{
              width: 6,
              height: 6,
              borderRadius: '50%',
              background: 'var(--color-bg-container)',
              animation: 'pulse 1.5s infinite',
            }} />
            <span style={{ color: '#fff', fontSize: 11, fontWeight: 700, letterSpacing: 1 }}>直播中</span>
          </div>
        )}

        {room.status === 'UPCOMING' && (
          <div style={{
            position: 'absolute',
            top: 12,
            left: 12,
            padding: '4px 10px',
            borderRadius: '6px',
            background: 'rgba(255,165,2,0.9)',
            color: '#fff',
            fontSize: 11,
            fontWeight: 700,
          }}>
            预告
          </div>
        )}

        {isLive && (
          <div style={{
            position: 'absolute',
            top: 12,
            right: 12,
            display: 'flex',
            alignItems: 'center',
            gap: 4,
            padding: '4px 10px',
            borderRadius: 12,
            background: 'rgba(0,0,0,0.6)',
            backdropFilter: 'blur(8px)',
          }}>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
            <span style={{ color: 'var(--color-text-secondary)', fontSize: 11, fontWeight: 500 }}>{formatCount(room.currentViewers)}</span>
          </div>
        )}

        <div style={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          height: 60,
          background: 'linear-gradient(transparent, rgba(11,18,32,0.9))',
        }} />
      </div>

      <div style={{ padding: '14px 16px 16px' }}>
        <div style={{
          fontSize: 14,
          fontWeight: 600,
          color: 'var(--color-text-secondary)',
          marginBottom: 10,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}>
          {room.title}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{
            width: 28,
            height: 28,
            borderRadius: '50%',
            background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.2), rgba(0,153,204,0.3))',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            border: '1.5px solid rgba(var(--color-primary-rgb), 0.3)',
            flexShrink: 0,
          }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
          </div>
          <span style={{ color: 'var(--color-text-secondary)', fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{room.anchorName}</span>

          {room.productId ? (
            <span style={{
              marginLeft: 'auto',
              padding: '2px 8px',
              borderRadius: '4px',
              background: 'rgba(var(--color-primary-rgb), 0.1)',
              border: '1px solid rgba(var(--color-primary-rgb), 0.2)',
              color: 'var(--color-primary)',
              fontSize: 11,
              fontWeight: 500,
              flexShrink: 0,
            }}>
              🛒 带货
            </span>
          ) : null}
        </div>

        {room.startTime && (
          <div style={{ color: 'var(--color-text-tertiary)', fontSize: 11, marginTop: 8 }}>
            {isLive ? '开播于' : '预计开播'} {new Date(room.startTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}
          </div>
        )}
      </div>
    </div>
  )
}

function SkeletonCard() {
  return (
    <div style={{
      background: 'var(--color-bg-container)',
      border: '1px solid var(--color-border)',
      borderRadius: '16px',
      overflow: 'hidden',
    }}>
      <div style={{
        height: 200,
        background: 'linear-gradient(90deg, var(--color-border) 25%, var(--color-border) 50%, var(--color-border) 75%)',
        backgroundSize: '200% 100%',
        animation: 'shimmer 1.5s infinite',
      }} />
      <div style={{ padding: '14px 16px 16px' }}>
        <div style={{
          height: 14,
          width: '80%',
          borderRadius: 4,
          background: 'var(--color-border)',
          marginBottom: 10,
        }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ width: 28, height: 28, borderRadius: '50%', background: 'var(--color-border)' }} />
          <div style={{ height: 12, width: '50%', borderRadius: 4, background: 'var(--color-border)' }} />
        </div>
      </div>
    </div>
  )
}

export default function LiveListPage() {
  const [rooms, setRooms] = useState<LiveRoom[]>([])
  const [loading, setLoading] = useState(false)

  const fetchRooms = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await listLiveRooms(1, 50)
      setRooms(res.data?.records ?? [])
    } catch {
      setRooms([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchRooms()
  }, [fetchRooms])

  const liveRooms = rooms.filter((r) => r.status === 'LIVE')
  const upcomingRooms = rooms.filter((r) => r.status === 'UPCOMING')
  const liveCount = liveRooms.length

  return (
    <div style={{ minHeight: '100vh', background: 'var(--color-bg-base)' }}>
      <style>{`
        @keyframes shimmer {
          0% { background-position: 200% 0; }
          100% { background-position: -200% 0; }
        }
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.4; }
        }
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}</style>

      <div style={{
        background: 'var(--color-gradient-hero)',
        padding: '64px 24px 36px',
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
          background: 'radial-gradient(ellipse at center, rgba(255,71,87,0.06) 0%, transparent 70%)',
          borderRadius: '50%',
          filter: 'blur(60px)',
        }} />
        <div style={{ position: 'relative' }}>
          <div style={{ fontSize: 13, color: '#FF4757', letterSpacing: 4, marginBottom: 12, fontWeight: 600 }}>
            Live Streaming
          </div>
          <h1 style={{ fontSize: 40, fontWeight: 900, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
            直播<span style={{ color: '#FF4757', textShadow: '0 0 30px rgba(255,71,87,0.4)' }}>广场</span>
          </h1>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, color: 'var(--color-text-secondary)', fontSize: 15 }}>
            <span style={{
              width: 8,
              height: 8,
              borderRadius: '50%',
              background: '#FF4757',
              animation: 'pulse 1.5s infinite',
              display: 'inline-block',
            }} />
            当前 <span style={{ color: '#FF4757', fontWeight: 700 }}>{liveCount}</span> 场直播中
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 1280, margin: '0 auto', padding: '0 24px' }}>
        {loading ? (
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(2, 1fr)',
            gap: 20,
            paddingTop: 8,
          }}>
            {Array.from({ length: 6 }).map((_, i) => (
              <SkeletonCard key={i} />
            ))}
          </div>
        ) : rooms.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '80px 0' }}>
            <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }}>📹</div>
            <div style={{ color: 'var(--color-text-tertiary)', fontSize: 15 }}>暂无直播</div>
          </div>
        ) : (
          <>
            {liveRooms.length > 0 && (
              <div style={{ marginBottom: 40 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 20, paddingTop: 16 }}>
                  <span style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: '#FF4757',
                    animation: 'pulse 1.5s infinite',
                  }} />
                  <span style={{ fontSize: 18, fontWeight: 700, color: 'var(--color-text-secondary)' }}>
                    正在直播
                  </span>
                  <span style={{ color: 'var(--color-text-tertiary)', fontSize: 13 }}>
                    {liveRooms.length}场
                  </span>
                </div>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(2, 1fr)',
                  gap: 20,
                }}>
                  {liveRooms.map((room) => (
                    <LiveRoomCard key={room.id} room={room} />
                  ))}
                </div>
              </div>
            )}

            {upcomingRooms.length > 0 && (
              <div style={{ marginBottom: 48 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 20 }}>
                  <span style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: '#FFA502',
                  }} />
                  <span style={{ fontSize: 18, fontWeight: 700, color: 'var(--color-text-secondary)' }}>
                    即将开播
                  </span>
                  <span style={{ color: 'var(--color-text-tertiary)', fontSize: 13 }}>
                    {upcomingRooms.length}场
                  </span>
                </div>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(2, 1fr)',
                  gap: 20,
                }}>
                  {upcomingRooms.map((room) => (
                    <LiveRoomCard key={room.id} room={room} />
                  ))}
                </div>
              </div>
            )}

            {liveRooms.length === 0 && upcomingRooms.length === 0 && (
              <div style={{ textAlign: 'center', padding: '80px 0' }}>
                <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }}>📹</div>
                <div style={{ color: 'var(--color-text-tertiary)', fontSize: 15 }}>暂无直播安排</div>
              </div>
            )}
          </>
        )}
      </div>

      <style>{`
        @media (min-width: 768px) {
          .live-grid {
            grid-template-columns: repeat(3, 1fr) !important;
          }
        }
      `}</style>
    </div>
  )
}
