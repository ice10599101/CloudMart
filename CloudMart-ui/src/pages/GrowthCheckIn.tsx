import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, Spin } from 'antd'
import { CalendarOutlined, LeftOutlined, RightOutlined, FireFilled } from '@ant-design/icons'
import {
  checkIn,
  getCheckInCalendar,
  getCheckInStatus,
  getContinuousDays,
} from '@/api/growth'
import { useAuthStore } from '@/stores/auth'
import { history } from 'umi'
import styles from './GrowthCheckIn.module.css'

/**
 * 社区签到中心（P1-18：社区成长体系补齐）。
 * 签到 +10 经验、连续奖励每日 +5（封顶 +50，后端口径）；DB 事实日历（跨月可翻）。
 * 与心愿每日签到（星光）为独立数据源，本页是社区经验签到入口。
 */

const WEEK_DAYS = ['日', '一', '二', '三', '四', '五', '六']

interface CalendarCell {
  day: number
  signed: boolean
  inMonth: boolean
  isToday: boolean
}

function buildMonthCells(year: number, month: number, signedDays: Set<number>): CalendarCell[][] {
  const first = new Date(year, month - 1, 1)
  const daysInMonth = new Date(year, month, 0).getDate()
  const today = new Date()
  const cells: CalendarCell[] = []
  for (let i = 0; i < first.getDay(); i += 1) {
    cells.push({ day: 0, signed: false, inMonth: false, isToday: false })
  }
  for (let d = 1; d <= daysInMonth; d += 1) {
    cells.push({
      day: d,
      signed: signedDays.has(d),
      inMonth: true,
      isToday: today.getFullYear() === year && today.getMonth() + 1 === month && today.getDate() === d,
    })
  }
  while (cells.length % 7 !== 0) {
    cells.push({ day: 0, signed: false, inMonth: false, isToday: false })
  }
  const rows: CalendarCell[][] = []
  for (let i = 0; i < cells.length; i += 7) {
    rows.push(cells.slice(i, i + 7))
  }
  return rows
}

export default function GrowthCheckIn() {
  const { message } = App.useApp()
  const { user } = useAuthStore()

  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [loading, setLoading] = useState(true)
  const [signing, setSigning] = useState(false)
  const [checkedToday, setCheckedToday] = useState(false)
  const [continuousDays, setContinuousDays] = useState(0)
  const [signedDays, setSignedDays] = useState<Set<number>>(new Set())
  const [lastReward, setLastReward] = useState<{ exp: number; continuous: number } | null>(null)

  const loadMonth = useCallback(async (y: number, m: number) => {
    setLoading(true)
    try {
      const [statusRes, calRes, contRes] = await Promise.all([
        getCheckInStatus(),
        getCheckInCalendar(y, m),
        getContinuousDays(),
      ])
      if (statusRes.data.success) setCheckedToday(statusRes.data.data === true)
      if (calRes.data.success) {
        setSignedDays(new Set((calRes.data.data ?? []).map((dateStr) => Number(dateStr.slice(-2)))))
      }
      if (contRes.data.success) setContinuousDays(contRes.data.data ?? 0)
    } catch {
      message.error('签到数据加载失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    if (user) loadMonth(year, month)
  }, [user, year, month, loadMonth])

  const handleCheckIn = async () => {
    if (signing) return
    setSigning(true)
    try {
      const res = await checkIn()
      if (res.data.success && res.data.data) {
        const result = res.data.data
        setCheckedToday(true)
        setContinuousDays(result.continuousDays)
        setLastReward({ exp: result.expReward, continuous: result.continuousDays })
        message.success(`签到成功！经验 +${result.expReward}（连续 ${result.continuousDays} 天）`)
        loadMonth(year, month)
      }
    } catch {
      // 拦截器已提示（409 今日已签到等）
    } finally {
      setSigning(false)
    }
  }

  const shiftMonth = (delta: number) => {
    let y = year
    let m = month + delta
    if (m < 1) { y -= 1; m = 12 }
    if (m > 12) { y += 1; m = 1 }
    setYear(y)
    setMonth(m)
  }

  if (!user) {
    return (
      <div className={styles.page}>
        <Card>
          <Button type="primary" onClick={() => history.push('/login')}>登录后查看签到</Button>
        </Card>
      </div>
    )
  }

  const rows = buildMonthCells(year, month, signedDays)

  return (
    <div className={styles.page}>
      <Card className={styles.heroCard}>
        <div className={styles.heroBody}>
          <div className={styles.heroInfo}>
            <h2 className={styles.heroTitle}>社区签到</h2>
            <p className={styles.heroDesc}>每日签到获得成长经验，连续签到有额外奖励（每日 +5，封顶 +50）</p>
          </div>
          <div className={styles.streakBox}>
            <FireFilled className={styles.streakIcon} />
            <div>
              <div className={styles.streakNumber}>{continuousDays}</div>
              <div className={styles.streakLabel}>连续签到（天）</div>
            </div>
          </div>
          <Button
            type="primary"
            size="large"
            className={styles.checkinBtn}
            disabled={checkedToday}
            loading={signing}
            onClick={handleCheckIn}
          >
            {checkedToday ? '今日已签到' : '立即签到'}
          </Button>
        </div>
        {lastReward && (
          <p className={styles.rewardTip}>本次签到经验 +{lastReward.exp}，已连续 {lastReward.continuous} 天</p>
        )}
      </Card>

      <Card
        title={<><CalendarOutlined /> 签到日历</>}
        extra={
          <div className={styles.monthNav}>
            <Button type="text" size="small" icon={<LeftOutlined />} onClick={() => shiftMonth(-1)} />
            <span className={styles.monthText}>{year} 年 {month} 月</span>
            <Button type="text" size="small" icon={<RightOutlined />} onClick={() => shiftMonth(1)} />
          </div>
        }
      >
        {loading ? (
          <div className={styles.loadingWrap}><Spin /></div>
        ) : (
          <table className={styles.calendar}>
            <thead>
              <tr>{WEEK_DAYS.map((d) => <th key={d}>{d}</th>)}</tr>
            </thead>
            <tbody>
              {rows.map((row, ri) => (
                <tr key={ri}>
                  {row.map((cell, ci) => (
                    <td key={ci} className={cell.signed ? styles.cellSigned : styles.cell}>
                      {cell.inMonth && (
                        <>
                          <span className={styles.dayNumber}>{cell.day}</span>
                          {cell.signed && <span className={styles.checkMark}>✓</span>}
                          {cell.isToday && <span className={styles.todayDot} />}
                        </>
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}
