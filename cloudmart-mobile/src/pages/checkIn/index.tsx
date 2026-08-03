import { useState } from 'react'
import { View, Text } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import { growthApi } from '@/api/growth'
import styles from './index.module.scss'

interface LevelInfo {
  level: number
  exp: number
  nextLevelExp: number
  title: string
}

interface CalendarDay {
  day: number
  isCheckedIn: boolean
  isToday: boolean
  isFuture: boolean
}

const WEEK_DAYS = ['一', '二', '三', '四', '五', '六', '日']

export default function CheckInPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()

  const [isCheckedIn, setIsCheckedIn] = useState(false)
  const [continuousDays, setContinuousDays] = useState(0)
  const [levelInfo, setLevelInfo] = useState<LevelInfo | null>(null)
  const [calendarDays, setCalendarDays] = useState<CalendarDay[]>([])
  const [currentYear, setCurrentYear] = useState(new Date().getFullYear())
  const [currentMonth, setCurrentMonth] = useState(new Date().getMonth() + 1)
  const [checkingIn, setCheckingIn] = useState(false)
  const [expAnim, setExpAnim] = useState('')

  useDidShow(() => {
    loadData()
  })

  const loadData = async () => {
    try {
      const [statusRes, levelRes, continuousRes] = await Promise.all([
        growthApi.getCheckInStatus(),
        growthApi.getUserLevel(),
        growthApi.getContinuousDays(),
      ])
      const statusData = statusRes.data?.data
      const levelData = levelRes.data?.data
      const continuousData = continuousRes.data?.data

      setIsCheckedIn(statusData?.isCheckedIn || false)
      setLevelInfo(levelData ? { level: levelData.level, exp: levelData.exp, nextLevelExp: levelData.nextLevelExp, title: levelData.title } : null)
      setContinuousDays(continuousData || 0)
    } catch {
      // API unavailable
    }
    loadCalendar(currentYear, currentMonth)
  }

  const loadCalendar = async (year: number, month: number) => {
    try {
      const res = await growthApi.getCheckInCalendar(year, month)
      const checkedDays: number[] = (res.data?.data || []).map((v) => Number(v))

      const firstDay = new Date(year, month - 1, 1)
      const lastDay = new Date(year, month, 0)
      const daysInMonth = lastDay.getDate()
      const startWeekDay = firstDay.getDay() || 7

      const today = new Date()
      const isCurrentMonth = year === today.getFullYear() && month === today.getMonth() + 1

      const days: CalendarDay[] = []
      for (let d = 1; d <= daysInMonth; d++) {
        days.push({
          day: d,
          isCheckedIn: checkedDays.includes(d),
          isToday: isCurrentMonth && d === today.getDate(),
          isFuture: isCurrentMonth && d > today.getDate(),
        })
      }

      // Pad start of month with empty days
      for (let i = 1; i < startWeekDay; i++) {
        days.unshift({ day: 0, isCheckedIn: false, isToday: false, isFuture: false })
      }

      setCalendarDays(days)
    } catch {
      setCalendarDays([])
    }
  }

  const handlePrevMonth = () => {
    const prev = currentMonth === 1 ? { year: currentYear - 1, month: 12 } : { year: currentYear, month: currentMonth - 1 }
    setCurrentYear(prev.year)
    setCurrentMonth(prev.month)
    loadCalendar(prev.year, prev.month)
  }

  const handleNextMonth = () => {
    const now = new Date()
    const next = currentMonth === 12 ? { year: currentYear + 1, month: 1 } : { year: currentYear, month: currentMonth + 1 }
    if (next.year > now.getFullYear() || (next.year === now.getFullYear() && next.month > now.getMonth() + 1)) return
    setCurrentYear(next.year)
    setCurrentMonth(next.month)
    loadCalendar(next.year, next.month)
  }

  const handleCheckIn = async () => {
    if (isCheckedIn || checkingIn) return
    setCheckingIn(true)
    try {
      const res = await growthApi.checkIn()
      const exp = res.data?.data?.todayExp || 10
      setIsCheckedIn(true)
      setContinuousDays((prev) => prev + 1)
      setExpAnim(`+${exp} EXP`)
      setTimeout(() => setExpAnim(''), 2000)
      Taro.showToast({ title: '签到成功', icon: 'success' })
      loadData()
    } catch {
      Taro.showToast({ title: '签到失败', icon: 'none' })
    } finally {
      setCheckingIn(false)
    }
  }

  const expPercent = levelInfo ? Math.min((levelInfo.exp / levelInfo.nextLevelExp) * 100, 100) : 0
  const bonusExp = Math.min(continuousDays * 5, 50)

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      {/* Level Info Card */}
      {levelInfo && (
        <View className={styles.levelCard}>
          <View className={styles.levelHeader}>
            <View className={styles.levelBadge}>
              <Text className={styles.levelBadgeText}>Lv.{levelInfo.level}</Text>
            </View>
            <View className={styles.levelInfo}>
              <Text className={styles.levelTitle}>{levelInfo.title}</Text>
              <Text className={styles.levelExp}>{levelInfo.exp} / {levelInfo.nextLevelExp} EXP</Text>
            </View>
          </View>
          <View className={styles.expBar}>
            <View className={styles.expBarFill} style={{ width: `${expPercent}%` }} />
          </View>
        </View>
      )}

      {/* Check In Button */}
      <View className={styles.checkInCard}>
        <View className={styles.checkInInfo}>
          <Text className={styles.continuousDays}>连续签到 {continuousDays} 天</Text>
          <Text className={styles.bonusInfo}>今日可获得 {10 + bonusExp} EXP（基础10 + 连续奖励{bonusExp}）</Text>
        </View>
        <View
          className={`${styles.checkInBtn} ${isCheckedIn ? styles.checkedIn : ''} ${checkingIn ? styles.checking : ''}`}
          onClick={handleCheckIn}
        >
          <Text className={styles.checkInBtnText}>
            {checkingIn ? '签到中...' : isCheckedIn ? '已签到' : '立即签到'}
          </Text>
        </View>
        {expAnim && <Text className={styles.expAnim}>{expAnim}</Text>}
      </View>

      {/* Calendar */}
      <View className={styles.calendarCard}>
        <View className={styles.calendarHeader}>
          <View className={styles.monthNav} onClick={handlePrevMonth}>
            <Text className={styles.monthNavText}>‹</Text>
          </View>
          <Text className={styles.monthTitle}>{currentYear}年{currentMonth}月</Text>
          <View className={styles.monthNav} onClick={handleNextMonth}>
            <Text className={styles.monthNavText}>›</Text>
          </View>
        </View>

        <View className={styles.weekHeader}>
          {WEEK_DAYS.map((d) => (
            <Text key={d} className={styles.weekDay}>{d}</Text>
          ))}
        </View>

        <View className={styles.calendarGrid}>
          {calendarDays.map((day, index) => (
            <View key={index} className={`${styles.calendarDay} ${day.isToday ? styles.today : ''} ${day.isCheckedIn ? styles.checked : ''} ${day.isFuture ? styles.future : ''} ${day.day === 0 ? styles.empty : ''}`}>
              {day.day > 0 && (
                <>
                  <Text className={styles.dayText}>{day.day}</Text>
                  {day.isCheckedIn && <Text className={styles.checkMark}>✓</Text>}
                </>
              )}
            </View>
          ))}
        </View>
      </View>

      {/* Reward Rules */}
      <View className={styles.rulesCard}>
        <Text className={styles.rulesTitle}>签到奖励规则</Text>
        <Text className={styles.rulesText}>• 每日签到获得 10 EXP 基础经验</Text>
        <Text className={styles.rulesText}>• 连续签到每天额外 +5 EXP（上限 +50）</Text>
        <Text className={styles.rulesText}>• 断签后连续天数重置</Text>
      </View>
    </View>
  )
}
