import { useCallback, useMemo, useState } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import type { DailySigninResult, LevelUpEvent, MyResourcesData } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import StarCountUp from '@/components/StarCountUp'
import LevelUpModal from '@/components/LevelUpModal'
import styles from './index.module.scss'

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
 * 用户维度每日签到页（文档 2.6 / L1915：签到 +5 星光 + 签到日历）。
 *
 * 与心愿打卡（checkIn 成长体系）独立：签到按钮状态切换、日历已签到日点亮、
 * 星光余额数字滚动（StarCountUp）、响应携带 levelUp 时弹庆祝弹窗（粒子炸裂）。
 * 重复签到（409 WISH_ALREADY_SIGNED_IN）由 request 拦截提示，页面刷新为已签到态。
 */
export default function DailySigninPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const now = new Date()

  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
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

  const loadCalendar = useCallback(async (y: number, m: number) => {
    try {
      const res = await wishApi.getSigninCalendar(formatMonth(y, m))
      if (res.data.success) {
        const data = res.data.data
        setSignedDates(new Set(data.signedDates))
        setConsecutiveDays(data.consecutiveDays)
        setTotalDays(data.totalDays)
        // 当月为当前月时，以日历判断今日是否已签到
        const isCurrentMonth = y === now.getFullYear() && m === now.getMonth() + 1
        setSignedToday(isCurrentMonth && data.signedDates.includes(todayStr))
      }
    } catch {
      // 错误已由 request 处理
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [todayStr])

  const loadResources = useCallback(async () => {
    try {
      const res = await wishApi.getMyResources()
      if (res.data.success) setResources(res.data.data)
    } catch {
      // 余额卡片加载失败不阻塞签到
    }
  }, [])

  useDidShow(() => {
    if (!isLoggedIn) {
      Taro.redirectTo({ url: '/pages/login/index' })
      return
    }
    Promise.all([loadCalendar(year, month), loadResources()])
  })

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
      const res = await wishApi.dailySignin()
      if (res.data.success) {
        const result: DailySigninResult = res.data.data
        setSignedToday(true)
        setConsecutiveDays(result.consecutiveDays)
        setRewardDelta(result.starlightReward)
        // 星光余额以 +5 滚动（后端余额快照即时刷新）
        setResources((prev) =>
          prev ? { ...prev, balance: prev.balance + result.starlightReward } : prev,
        )
        Taro.showToast({ title: `签到成功，星光 +${result.starlightReward}`, icon: 'none' })
        if (result.levelUp) setLevelUp(result.levelUp)
        loadCalendar(year, month)
      }
    } catch (err) {
      // 重复签到（409 WISH_ALREADY_SIGNED_IN）：拦截器已提示，这里刷新为已签到态
      const code = (err as { data?: { error?: { code?: string } } })?.data?.error?.code
      if (code === 'WISH_ALREADY_SIGNED_IN') {
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

  const isCurrentMonth = year === now.getFullYear() && month === now.getMonth() + 1
  const todayDay = isCurrentMonth ? now.getDate() : -1

  return (
    <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}px`, minHeight: '100vh' }}>
      <CustomNavBar title='每日签到' back />

      <ScrollView scrollY className={styles.scroll}>
        {/* 签到卡片 */}
        <View className={styles.signinCard}>
          <View className={styles.signinStats}>
            <View className={styles.statItem}>
              <Text className={styles.statValue}>{consecutiveDays}</Text>
              <Text className={styles.statLabel}>连续签到（天）</Text>
            </View>
            <View className={styles.statDivider} />
            <View className={styles.statItem}>
              <Text className={styles.statValue}>{totalDays}</Text>
              <Text className={styles.statLabel}>累计签到（天）</Text>
            </View>
          </View>
          <View
            className={`${styles.signinBtn} ${signedToday ? styles.signinBtnDone : ''}`}
            onClick={handleSignin}
          >
            <Text className={styles.signinBtnText}>
              {signing ? '签到中...' : signedToday ? '今日已签到 ✓' : '签到领星光 +5'}
            </Text>
          </View>
          <Text className={styles.rewardLine}>明日签到可获得星光 +5</Text>
        </View>

        {/* 星光余额（数字滚动） */}
        {resources && (
          <View className={styles.balanceCard}>
            <Text className={styles.balanceLabel}>当前星光余额</Text>
            <View className={styles.balanceRow}>
              <Text className={styles.balanceIcon}>⭐</Text>
              <StarCountUp value={resources.balance} delta={rewardDelta} className={styles.balanceValue} />
            </View>
            <View className={styles.balanceToday}>
              <Text className={styles.balanceEarn}>今日 +{resources.todayEarned}</Text>
              <Text className={styles.balanceSpend}>支出 -{resources.todaySpent}</Text>
            </View>
          </View>
        )}

        {/* 签到日历 */}
        <View className={styles.calendarCard}>
          <View className={styles.calendarHeader}>
            <View className={styles.monthNav} onClick={() => switchMonth(-1)}>
              <Text className={styles.monthNavText}>‹</Text>
            </View>
            <Text className={styles.monthTitle}>{year} 年 {month} 月</Text>
            <View
              className={`${styles.monthNav} ${isCurrentMonth ? styles.monthNavDisabled : ''}`}
              onClick={() => isCurrentMonth ? undefined : switchMonth(1)}
            >
              <Text className={styles.monthNavText}>›</Text>
            </View>
          </View>
          <View className={styles.weekHeader}>
            {WEEK_DAYS.map((d) => (
              <Text key={d} className={styles.weekDay}>{d}</Text>
            ))}
          </View>
          <View className={styles.calendarGrid}>
            {Array.from({ length: calendarCells.startOffset }).map((_, i) => (
              <View key={`empty-${i}`} className={`${styles.dayCell} ${styles.dayCellEmpty}`} />
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
              ].filter(Boolean).join(' ')
              return (
                <View key={dateStr} className={cellClass}>
                  <Text className={styles.dayText}>{day}</Text>
                </View>
              )
            })}
          </View>
        </View>

        {/* 签到规则 */}
        <View className={styles.rulesCard}>
          <Text className={styles.rulesTitle}>签到规则</Text>
          <Text className={styles.rulesText}>· 每日签到获得星光 +5（按你的本地时区按日去重）</Text>
          <Text className={styles.rulesText}>· 连续签到天数在断签后重新计算，累计签到天数永久保留</Text>
          <Text className={styles.rulesText}>· 星光可用于点亮他人心愿、兑换虚拟资产（上限 5000）</Text>
        </View>
      </ScrollView>

      <LevelUpModal levelUp={levelUp} onClose={() => setLevelUp(null)} />
      <WishBGM />
    </View>
  )
}
