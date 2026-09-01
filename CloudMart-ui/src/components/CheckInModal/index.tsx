import { useState, useEffect, useCallback } from 'react'
import { Modal, Progress, message } from 'antd'
import { CheckCircleFilled, CalendarOutlined, FireOutlined, CloseOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons'
import { checkIn, getCheckInStatus, getUserLevel, getCheckInCalendar, getContinuousDays } from '@/api/growth'
import type { UserLevelInfo, CheckInResult } from '@/api/growth'

interface CheckInModalProps {
  visible: boolean
  onClose: () => void
}

const WEEK_DAYS = ['一', '二', '三', '四', '五', '六', '日']

function getCalendarGrid(year: number, month: number): (number | null)[] {
  const firstDay = new Date(year, month - 1, 1)
  const lastDay = new Date(year, month, 0)
  const startWeekday = firstDay.getDay() === 0 ? 6 : firstDay.getDay() - 1
  const totalDays = lastDay.getDate()
  const grid: (number | null)[] = Array(startWeekday).fill(null)
  for (let d = 1; d <= totalDays; d++) {
    grid.push(d)
  }
  return grid
}

function calcBonusExp(continuousDays: number): number {
  const bonusPerDay = 5
  const maxBonus = 50
  return Math.min((continuousDays - 1) * bonusPerDay, maxBonus)
}

export default function CheckInModal({ visible, onClose }: CheckInModalProps) {
  const [levelInfo, setLevelInfo] = useState<UserLevelInfo | null>(null)
  const [checkedIn, setCheckedIn] = useState(false)
  const [continuousDays, setContinuousDays] = useState(0)
  const [loading, setLoading] = useState(false)
  const [rewardVisible, setRewardVisible] = useState(false)
  const [rewardExp, setRewardExp] = useState(0)
  const [calendarYear, setCalendarYear] = useState(new Date().getFullYear())
  const [calendarMonth, setCalendarMonth] = useState(new Date().getMonth() + 1)
  const [checkedDates, setCheckedDates] = useState<Set<string>>(new Set())

  const fetchData = useCallback(async () => {
    try {
      const [levelRes, statusRes, continuousRes] = await Promise.all([
        getUserLevel(),
        getCheckInStatus(),
        getContinuousDays(),
      ])
      if (levelRes.data.data) setLevelInfo(levelRes.data.data)
      if (statusRes.data.data !== null && statusRes.data.data !== undefined) setCheckedIn(statusRes.data.data)
      if (continuousRes.data.data !== null && continuousRes.data.data !== undefined) setContinuousDays(continuousRes.data.data)
    } catch {
      setLevelInfo(null)
    }
  }, [])

  const fetchCalendar = useCallback(async (year: number, month: number) => {
    try {
      const res = await getCheckInCalendar(year, month)
      const dates: string[] = res.data.data ?? []
      setCheckedDates(new Set(dates))
    } catch {
      setCheckedDates(new Set())
    }
  }, [])

  useEffect(() => {
    if (visible) {
      fetchData()
      setRewardVisible(false)
      const now = new Date()
      setCalendarYear(now.getFullYear())
      setCalendarMonth(now.getMonth() + 1)
      fetchCalendar(now.getFullYear(), now.getMonth() + 1)
    }
  }, [visible, fetchData, fetchCalendar])

  const handlePrevMonth = () => {
    let y = calendarYear
    let m = calendarMonth - 1
    if (m < 1) {
      m = 12
      y -= 1
    }
    setCalendarYear(y)
    setCalendarMonth(m)
    fetchCalendar(y, m)
  }

  const handleNextMonth = () => {
    let y = calendarYear
    let m = calendarMonth + 1
    if (m > 12) {
      m = 1
      y += 1
    }
    setCalendarYear(y)
    setCalendarMonth(m)
    fetchCalendar(y, m)
  }

  const handleCheckIn = async () => {
    setLoading(true)
    try {
      const { data: res } = await checkIn()
      const result: CheckInResult = res.data
      setCheckedIn(true)
      setContinuousDays(result.continuousDays)
      setRewardExp(result.expReward)
      setRewardVisible(true)
      if (result.levelTitle && levelInfo) {
        setLevelInfo({
          ...levelInfo,
          level: result.currentLevel,
          levelTitle: result.levelTitle,
          levelIcon: result.levelIcon,
          totalExp: result.totalExp,
        })
      }
      const now = new Date()
      fetchCalendar(now.getFullYear(), now.getMonth() + 1)
    } catch {
      message.error('签到失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  const bonusExp = calcBonusExp(continuousDays)
  const baseExp = 10
  const today = new Date()
  const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  const calendarGrid = getCalendarGrid(calendarYear, calendarMonth)
  const isCurrentMonth = calendarYear === today.getFullYear() && calendarMonth === today.getMonth() + 1

  return (
    <Modal
      open={visible}
      onCancel={onClose}
      footer={null}
      width={440}
      centered
      closable={false}
      styles={{
        body: {
          background: 'var(--color-bg-container)',
          border: '1px solid var(--color-border)',
          borderRadius: 16,
          padding: 0,
          overflow: 'hidden',
        },
        mask: { background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(4px)' },
      }}
    >
      <div style={{ position: 'relative' }}>
        <div
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            height: 120,
            background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.12) 0%, rgba(0,153,204,0.08) 100%)',
            pointerEvents: 'none',
          }}
        />

        <button
          type="button"
          onClick={onClose}
          style={{
            position: 'absolute',
            top: 16,
            right: 16,
            background: 'var(--color-border)',
            border: 'none',
            borderRadius: '50%',
            width: 32,
            height: 32,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            color: 'var(--color-text-secondary)',
            fontSize: 14,
            zIndex: 1,
            transition: 'all 0.2s ease',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = 'rgba(255,255,255,0.12)'
            e.currentTarget.style.color = '#FFFFFF'
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = 'var(--color-border)'
            e.currentTarget.style.color = 'var(--color-text-secondary)'
          }}
        >
          <CloseOutlined />
        </button>

        <div style={{ padding: '32px 28px 28px', position: 'relative' }}>
          <h2
            style={{
              fontSize: 22,
              fontWeight: 800,
              color: 'var(--color-text-secondary)',
              margin: '0 0 24px',
              textAlign: 'center',
            }}
          >
            每日签到
          </h2>

          {levelInfo && (
            <div
              style={{
                background: 'rgba(var(--color-primary-rgb), 0.12)',
                border: '1px solid rgba(var(--color-primary-rgb), 0.12)',
                borderRadius: 12,
                padding: '16px 20px',
                marginBottom: 24,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
                <span style={{ fontSize: 28 }}>{levelInfo.levelIcon || '⭐'}</span>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--color-text-secondary)' }}>
                    Lv.{levelInfo.level} {levelInfo.levelTitle}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: 2 }}>
                    {levelInfo.nextLevelTitle
                      ? `距 ${levelInfo.nextLevelTitle} 还需 ${levelInfo.nextLevelExp - levelInfo.totalExp} 经验`
                      : '已达最高等级'}
                  </div>
                </div>
              </div>
              <Progress
                percent={Math.round(levelInfo.expProgress * 100)}
                showInfo={false}
                strokeColor={{ from: 'var(--color-primary)', to: 'var(--color-primary-dark)' }}
                trailColor="var(--color-border)"
                size="small"
              />
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  marginTop: 6,
                  fontSize: 11,
                  color: 'var(--color-text-tertiary)',
                }}
              >
                <span>{levelInfo.totalExp} EXP</span>
                <span>{levelInfo.nextLevelExp || 'MAX'}</span>
              </div>
            </div>
          )}

          <div style={{ marginBottom: 24 }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                marginBottom: 14,
              }}
            >
              <CalendarOutlined style={{ color: 'var(--color-primary)', fontSize: 15 }} />
              <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text-secondary)' }}>签到日历</span>
              <span
                style={{
                  marginLeft: 'auto',
                  fontSize: 13,
                  color: 'var(--color-text-secondary)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                }}
              >
                <FireOutlined style={{ color: '#FF6B35' }} />
                已连续 {continuousDays} 天
              </span>
            </div>

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: 10,
              }}
            >
              <button
                type="button"
                onClick={handlePrevMonth}
                style={{
                  background: 'var(--color-border)',
                  border: 'none',
                  borderRadius: 6,
                  width: 28,
                  height: 28,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  color: 'var(--color-text-secondary)',
                  fontSize: 12,
                }}
              >
                <LeftOutlined />
              </button>
              <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text-secondary)' }}>
                {calendarYear}年{calendarMonth}月
              </span>
              <button
                type="button"
                onClick={handleNextMonth}
                style={{
                  background: 'var(--color-border)',
                  border: 'none',
                  borderRadius: 6,
                  width: 28,
                  height: 28,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  color: 'var(--color-text-secondary)',
                  fontSize: 12,
                }}
              >
                <RightOutlined />
              </button>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 4 }}>
              {WEEK_DAYS.map((day) => (
                <div
                  key={day}
                  style={{
                    textAlign: 'center',
                    fontSize: 11,
                    color: 'var(--color-text-tertiary)',
                    padding: '4px 0',
                  }}
                >
                  {day}
                </div>
              ))}
              {calendarGrid.map((day, index) => {
                if (day === null) {
                  return <div key={`empty-${index}`} style={{ height: 36 }} />
                }
                const dateStr = `${calendarYear}-${String(calendarMonth).padStart(2, '0')}-${String(day).padStart(2, '0')}`
                const isChecked = checkedDates.has(dateStr)
                const isToday = isCurrentMonth && dateStr === todayStr
                const isFuture = isCurrentMonth && dateStr > todayStr

                return (
                  <div
                    key={dateStr}
                    style={{
                      height: 36,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <div
                      style={{
                        width: 32,
                        height: 32,
                        borderRadius: '50%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: isChecked
                          ? 'var(--color-gradient-primary)'
                          : isToday
                            ? 'rgba(var(--color-primary-rgb), 0.12)'
                            : 'transparent',
                        border: isToday
                          ? '2px solid rgba(var(--color-primary-rgb), 0.12)'
                          : '1px solid transparent',
                        transition: 'all 0.3s ease',
                      }}
                    >
                      {isChecked ? (
                        <CheckCircleFilled style={{ color: 'var(--color-text-secondary)', fontSize: 14 }} />
                      ) : (
                        <span
                          style={{
                            fontSize: 12,
                            color: isFuture ? 'rgba(255,255,255,0.15)' : isToday ? 'var(--color-primary)' : 'var(--color-text-tertiary)',
                          }}
                        >
                          {day}
                        </span>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          <div
            style={{
              textAlign: 'center',
              marginBottom: 20,
              fontSize: 13,
              color: 'var(--color-text-secondary)',
            }}
          >
            签到可获得 <span style={{ color: 'var(--color-primary)', fontWeight: 600 }}>+{baseExp}</span> 经验值
            {bonusExp > 0 && (
              <span>
                {' '}
                (连续{continuousDays}天额外{' '}
                <span style={{ color: '#FF6B35', fontWeight: 600 }}>+{bonusExp}</span>)
              </span>
            )}
          </div>

          <button
            type="button"
            onClick={handleCheckIn}
            disabled={checkedIn || loading}
            style={{
              width: '100%',
              padding: '14px 0',
              border: 'none',
              borderRadius: 12,
              background: checkedIn
                ? 'var(--color-border)'
                : 'var(--color-gradient-primary)',
              color: checkedIn ? 'var(--color-text-secondary)' : 'var(--color-bg-base)',
              fontSize: 16,
              fontWeight: 700,
              cursor: checkedIn || loading ? 'not-allowed' : 'pointer',
              boxShadow: checkedIn ? 'none' : '0 4px 20px rgba(var(--color-primary-rgb), 0.12)',
              transition: 'all 0.3s ease',
              position: 'relative',
              overflow: 'hidden',
            }}
          >
            {loading ? '签到中...' : checkedIn ? '已签到 ✓' : '签到'}
          </button>

          {rewardVisible && (
            <div
              style={{
                position: 'absolute',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                pointerEvents: 'none',
                animation: 'rewardFloat 1.5s ease-out forwards',
              }}
            >
              <div
                style={{
                  fontSize: 36,
                  fontWeight: 800,
                  color: 'var(--color-primary)',
                  textShadow: '0 0 20px rgba(var(--color-primary-rgb), 0.12)',
                  whiteSpace: 'nowrap',
                }}
              >
                +{rewardExp} EXP
              </div>
            </div>
          )}
        </div>
      </div>

      <style>{`
        @keyframes rewardFloat {
          0% { opacity: 1; transform: translate(-50%, -50%) scale(0.5); }
          30% { opacity: 1; transform: translate(-50%, -70%) scale(1.2); }
          100% { opacity: 0; transform: translate(-50%, -120%) scale(1); }
        }
      `}</style>
    </Modal>
  )
}
