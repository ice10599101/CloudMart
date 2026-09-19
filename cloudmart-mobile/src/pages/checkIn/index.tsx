import { useState } from 'react'
import { View, Text } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import { growthApi } from '@/api/growth'
import type { ExpLog, LevelConfig } from '@/types'
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
  const [levelConfigs, setLevelConfigs] = useState<LevelConfig[]>([])
  const [expLogs, setExpLogs] = useState<ExpLog[]>([])
  const [expOpen, setExpOpen] = useState(false)
  const [avatarFrame, setAvatarFrameState] = useState('none')
  // 头像框方案（对齐 Web 端 AVATAR_FRAMES，Lv2+ 解锁）
  const AVATAR_FRAMES = [
    { key: 'none', label: '默认', color: 'transparent' },
    { key: 'gold', label: '金环', color: '#ffd700' },
    { key: 'purple', label: '紫晕', color: '#9370db' },
    { key: 'green', label: '翠光', color: '#2ed573' },
    { key: 'pink', label: '樱粉', color: '#ff7eb3' },
    { key: 'rainbow', label: '彩虹', color: '#00d4ff' },
  ]

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
    // 等级体系 + 经验变动 + 头像框（对齐 Web 端 UserCenter 面板数据源）
    growthApi.getLevelConfigs().then((res) => setLevelConfigs(res.data?.data || [])).catch(() => {})
    growthApi.getExpLogs({ page: 1, pageSize: 20 }).then((res) => setExpLogs(res.data?.data?.list || [])).catch(() => {})
    setAvatarFrameState(Taro.getStorageSync('avatar_frame') || 'none')
    loadCalendar(currentYear, currentMonth)
  }

  /** 头像框切换（Lv2+ 权益；本地持久化 + 后端同步，对齐 Web 端） */
  const applyAvatarFrame = (key: string) => {
    if ((levelInfo?.level ?? 0) < 2) {
      Taro.showToast({ title: 'Lv2 解锁自定义头像框', icon: 'none' })
      return
    }
    setAvatarFrameState(key)
    Taro.setStorageSync('avatar_frame', key)
    growthApi.setAvatarFrame(key).catch(() => {})
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

      {/* 星光流水入口（B12 P1：流水列表三端一致） */}
      <View style={{ display: 'flex', justifyContent: 'flex-end', padding: '0 24px 8px' }}>
        <Text
          style={{ fontSize: 13, color: '#4a90d9' }}
          onClick={() => Taro.navigateTo({ url: '/pages/starlightLog/index' })}
        >
          星光流水 →
        </Text>
      </View>

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

      {/* 等级体系（对齐 Web 端 UserCenter 等级卡） */}
      {levelConfigs.length > 0 && (
        <View className={styles.rulesCard}>
          <Text className={styles.rulesTitle}>等级体系</Text>
          {levelConfigs.map((config) => (
            <View
              key={config.level}
              className={`${styles.levelRow} ${levelInfo?.level === config.level ? styles.levelRowActive : ''}`}
            >
              <Text className={styles.levelBadge}>Lv{config.level}</Text>
              <Text className={styles.levelName}>{config.title}</Text>
              <Text className={styles.levelExp}>{config.minExp} EXP</Text>
              {levelInfo?.level === config.level && <Text className={styles.levelCurrent}>当前</Text>}
            </View>
          ))}
        </View>
      )}

      {/* 自定义头像框（Lv2+ 权益，对齐 Web 端） */}
      <View className={styles.rulesCard}>
        <Text className={styles.rulesTitle}>头像框 {levelInfo && levelInfo.level < 2 ? '（Lv2 解锁）' : ''}</Text>
        <View className={styles.frameRow}>
          {AVATAR_FRAMES.map((frame) => (
            <View
              key={frame.key}
              className={`${styles.frameChip} ${avatarFrame === frame.key ? styles.frameChipActive : ''}`}
              onClick={() => applyAvatarFrame(frame.key)}
            >
              <View className={styles.frameDot} style={{ borderColor: frame.color }} />
              <Text className={styles.frameLabel}>{frame.label}</Text>
            </View>
          ))}
        </View>
      </View>

      {/* 经验变动（对齐 Web 端经验变动折叠列表） */}
      <View className={styles.rulesCard}>
        <View className={styles.expHeader} onClick={() => setExpOpen(!expOpen)}>
          <Text className={styles.rulesTitle}>经验变动</Text>
          <Text className={styles.expToggle}>{expOpen ? '收起' : `展开(${expLogs.length})`}</Text>
        </View>
        {expOpen && (
          expLogs.length > 0 ? (
            expLogs.map((log) => (
              <View key={log.id} className={styles.expRow}>
                <Text className={styles.expDesc}>{log.description || log.source}</Text>
                <Text className={styles.expChange}>+{log.exp}</Text>
                <Text className={styles.expTime}>{log.createdAt?.slice(5, 10)}</Text>
              </View>
            ))
          ) : (
            <Text className={styles.expEmpty}>暂无经验变动</Text>
          )
        )}
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
