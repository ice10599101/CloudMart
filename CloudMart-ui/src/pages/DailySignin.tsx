import { useCallback, useEffect, useMemo, useState } from 'react'
import { App, Button } from 'antd'
import Skeleton from '@/components/Skeleton'
import { CalendarOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons'
import { history } from 'umi'
import { dailySignin, getMyResources, getSigninCalendar } from '@/api/wish'
import type { DailySigninResult, LevelUpEvent, MyResourcesData } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import StarCountUp from '@/components/StarCountUp'
import LevelUpModal from '@/components/LevelUpModal'
import WishBGM from '@/components/WishBGM'
import styles from './DailySignin.module.css'

const WEEK_DAYS = ['日', '一', '二', '三', '四', '五', '六']

/** yyyy-MM（本地时区，与后端用户时区签到日历对齐） */
function formatMonth(year: number, month: number): string {
  return `${year}-${String(month).padStart(2, '0')}`
}

/** yyyy-MM-dd */
function formatDate(year: number, month: number, day: number): string {
  return `${formatMonth(year, month)}-${String(day).padStart(2, '0')}`
}

/**
 * 每日签到页（文档 2.6 / L1910）：签到按钮状态切换 + 签到日历已签到日点亮。
 *
 * <p>签到成功：星光 +5 数字滚动（StarCountUp）；响应携带 levelUp 事件时
 * 弹出等级提升庆祝弹窗（粒子炸裂）。重复签到（409 WISH_ALREADY_SIGNED_IN）
 * 由拦截器提示，页面自动刷新为已签到态。</p>
 */
export default function DailySignin() {
  const { message } = App.useApp()
  const { user, userLoading } = useAuthStore()

  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [loading, setLoading] = useState(true)
  const [signing, setSigning] = useState(false)
  const [signedToday, setSignedToday] = useState(false)
  const [consecutiveDays, setConsecutiveDays] = useState(0)
  const [totalDays, setTotalDays] = useState(0)
  const [signedDates, setSignedDates] = useState<Set<string>>(new Set())
  const [resources, setResources] = useState<MyResourcesData | null>(null)
  const [rewardDelta, setRewardDelta] = useState(0)
  const [levelUp, setLevelUp] = useState<LevelUpEvent | null>(null)

  const todayStr = useMemo(
    () => formatDate(now.getFullYear(), now.getMonth() + 1, now.getDate()),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  )

  const loadCalendar = useCallback(
    async (y: number, m: number) => {
      try {
        const res = await getSigninCalendar(formatMonth(y, m))
        if (res.data.success) {
          const data = res.data.data
          setSignedDates(new Set(data.signedDates))
          setConsecutiveDays(data.consecutiveDays)
          setTotalDays(data.totalDays)
          // 当月为当前月时，以日历判断今日是否已签到
          const isCurrentMonth =
            y === now.getFullYear() && m === now.getMonth() + 1
          setSignedToday(isCurrentMonth && data.signedDates.includes(todayStr))
        }
      } catch {
        // 错误已由 request 拦截器处理
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    },
    [todayStr],
  )

  const loadResources = useCallback(async () => {
    try {
      const res = await getMyResources()
      if (res.data.success) setResources(res.data.data)
    } catch {
      // 余额卡片加载失败不阻塞签到
    }
  }, [])

  useEffect(() => {
    if (!user && !userLoading) {
      message.warning('请先登录')
      history.push('/login?redirect=/wish/signin')
      return
    }
    if (!user) return
    setLoading(true)
    Promise.all([loadCalendar(year, month), loadResources()]).finally(() =>
      setLoading(false),
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, loadResources])

  const switchMonth = (direction: 1 | -1) => {
    let nextYear = year
    let nextMonth = month + direction
    if (nextMonth > 12) {
      nextYear += 1
      nextMonth = 1
    }
    if (nextMonth < 1) {
      nextYear -= 1
      nextMonth = 12
    }
    // 未来月份不可切换
    if (nextYear > now.getFullYear() || (nextYear === now.getFullYear() && nextMonth > now.getMonth() + 1)) {
      return
    }
    setYear(nextYear)
    setMonth(nextMonth)
    loadCalendar(nextYear, nextMonth)
  }

  const handleSignin = async () => {
    if (signedToday || signing) return
    setSigning(true)
    try {
      const res = await dailySignin()
      if (res.data.success) {
        const result: DailySigninResult = res.data.data
        setSignedToday(true)
        setConsecutiveDays(result.consecutiveDays)
        setRewardDelta(result.starlightReward)
        // 星光余额以 +5 滚动（后端余额快照即时刷新）
        setResources((prev) =>
          prev ? { ...prev, balance: prev.balance + result.starlightReward } : prev,
        )
        message.success(`签到成功，星光 +${result.starlightReward}`)
        if (result.levelUp) setLevelUp(result.levelUp)
        loadCalendar(year, month)
      }
    } catch (err) {
      // 重复签到（409 WISH_ALREADY_SIGNED_IN）：拦截器已提示，这里刷新为已签到态
      if ((err as { code?: string }).code === 'WISH_ALREADY_SIGNED_IN') {
        setSignedToday(true)
        loadCalendar(year, month)
      }
    } finally {
      setSigning(false)
    }
  }

  const calendarCells = useMemo(() => {
    const daysInMonth = new Date(year, month, 0).getDate()
    // 周日为首列的起始偏移（getDay: 0=周日）
    const startOffset = new Date(year, month - 1, 1).getDay()
    const cells: Array<{ day: number; dateStr: string }> = []
    for (let d = 1; d <= daysInMonth; d++) {
      cells.push({ day: d, dateStr: formatDate(year, month, d) })
    }
    return { startOffset, cells }
  }, [year, month])

  if (!user) return null

  if (loading) {
    return (
      <div className={`${styles.container} wish-universe-theme`}>
        <Skeleton variant="list" count={4} />
      </div>
    )
  }

  const isCurrentMonth = year === now.getFullYear() && month === now.getMonth() + 1
  const todayDay = isCurrentMonth ? now.getDate() : -1

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.header}>
        <h1 className={styles.pageTitle}>
          <CalendarOutlined style={{ color: '#FFD700' }} />
          每日签到
        </h1>
        <span className={styles.summary}>
          累计签到 {totalDays} 天 · 连续 {consecutiveDays} 天
        </span>
      </div>

      <div className={styles.signinCard}>
        <div className={styles.signinStats}>
          <div className={styles.statItem}>
            <span className={styles.statValue}>{consecutiveDays}</span>
            <span className={styles.statLabel}>连续签到（天）</span>
          </div>
          <div className={styles.statItem}>
            <span className={styles.statValue}>{totalDays}</span>
            <span className={styles.statLabel}>累计签到（天）</span>
          </div>
        </div>
        <Button
          type="primary"
          size="large"
          loading={signing}
          disabled={signedToday}
          onClick={handleSignin}
          style={
            signedToday
              ? undefined
              : {
                  background: 'linear-gradient(120deg, #FFD700, #FFA500)',
                  borderColor: '#FFD700',
                  color: '#0c1b3a',
                  fontWeight: 600,
                  minWidth: 200,
                  height: 46,
                }
          }
        >
          {signedToday ? '今日已签到 ✓' : '签到领星光 +5'}
        </Button>
        <div className={styles.rewardLine}>明日签到可获得星光 +5</div>
      </div>

      {resources && (
        <div className={styles.balanceCard}>
          <span style={{ color: 'rgba(255,255,255,0.6)', fontSize: 13 }}>当前星光余额</span>
          <StarCountUp
            value={resources.balance}
            delta={rewardDelta}
            className={styles.balanceValue}
          />
        </div>
      )}

      <div className={styles.calendarCard}>
        <div className={styles.calendarHeader}>
          <Button
            type="text"
            icon={<LeftOutlined />}
            aria-label="上一月"
            onClick={() => switchMonth(-1)}
          />
          <span className={styles.monthTitle}>
            {year} 年 {month} 月
          </span>
          <Button
            type="text"
            icon={<RightOutlined />}
            aria-label="下一月"
            disabled={isCurrentMonth}
            onClick={() => switchMonth(1)}
          />
        </div>
        <div className={styles.weekHeader}>
          {WEEK_DAYS.map((d) => (
            <span key={d} className={styles.weekDay}>
              {d}
            </span>
          ))}
        </div>
        <div className={styles.calendarGrid}>
          {Array.from({ length: calendarCells.startOffset }).map((_, i) => (
            <div key={`empty-${i}`} className={`${styles.dayCell} ${styles.dayCellEmpty}`} />
          ))}
          {calendarCells.cells.map(({ day, dateStr }) => {
            const isSigned = signedDates.has(dateStr)
            const isToday = day === todayDay
            const isFuture = isCurrentMonth && day > now.getDate()
            const cellClass = [
              styles.dayCell,
              isSigned ? styles.dayCellSigned : '',
              !isSigned && isToday ? styles.dayCellToday : '',
              !isSigned && isFuture ? styles.dayCellFuture : '',
            ]
              .filter(Boolean)
              .join(' ')
            return (
              <div key={dateStr} className={cellClass} title={dateStr}>
                {day}
              </div>
            )
          })}
        </div>
      </div>

      <div className={styles.rulesCard}>
        <div className={styles.rulesTitle}>签到规则</div>
        <div>· 每日签到获得星光 +5（按你的本地时区按日去重）</div>
        <div>· 连续签到天数在断签后重新计算，累计签到天数永久保留</div>
        <div>· 星光可用于点亮他人心愿、兑换虚拟资产（上限 5000）</div>
      </div>

      <LevelUpModal levelUp={levelUp} onClose={() => setLevelUp(null)} />
      <WishBGM />
    </div>
  )
}
