import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, RefreshControl } from 'react-native'
import { useState, useEffect, useCallback, useMemo } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import type { DailySigninResult, LevelUpEvent, MyResourcesData } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import WishBGM from '@/components/WishBGM'
import StarCountUp from '@/components/StarCountUp'
import LevelUpModal from '@/components/LevelUpModal'
import { notifyLevelUp } from '@/utils/levelup-notifications'

const WEEK_DAYS = ['日', '一', '二', '三', '四', '五', '六']

/** yyyy-MM */
function formatMonth(year: number, month: number): string {
  return `${year}-${String(month).padStart(2, '0')}`
}

/** yyyy-MM-dd */
function formatDate(year: number, month: number, day: number): string {
  return `${formatMonth(year, month)}-${String(day).padStart(2, '0')}`
}

/**
 * 用户维度每日签到页（文档 2.6 / L1916：签到 +5 星光 + 签到日历）。
 *
 * 签到按钮状态切换、日历已签到日点亮、星光余额数字滚动（StarCountUp）、
 * 响应携带 levelUp 时弹庆祝弹窗（粒子炸裂）+ 本地推送（见 LevelUpModal）。
 */
export default function DailySigninScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const now = new Date()

  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
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

  useEffect(() => {
    if (!isLoggedIn) {
      router.replace('/login')
    }
  }, [isLoggedIn])

  const loadCalendar = useCallback(async (y: number, m: number) => {
    try {
      const res = await wishApi.getSigninCalendar(formatMonth(y, m))
      if (res.data?.success) {
        const data = res.data.data
        setSignedDates(new Set(data.signedDates))
        setConsecutiveDays(data.consecutiveDays)
        setTotalDays(data.totalDays)
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
      if (res.data?.success) setResources(res.data.data)
    } catch {
      // 余额卡片加载失败不阻塞签到
    }
  }, [])

  const loadAll = useCallback(async () => {
    await Promise.all([loadCalendar(year, month), loadResources()])
    setLoading(false)
    setRefreshing(false)
  }, [year, month, loadCalendar, loadResources])

  useEffect(() => {
    if (isLoggedIn) loadAll()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn])

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    await loadAll()
  }, [loadAll])

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
      if (res.data?.success) {
        const result: DailySigninResult = res.data.data
        setSignedToday(true)
        setConsecutiveDays(result.consecutiveDays)
        setRewardDelta(result.starlightReward)
        setResources((prev) =>
          prev ? { ...prev, balance: prev.balance + result.starlightReward } : prev,
        )
        if (result.levelUp) {
          setLevelUp(result.levelUp)
          // 文档 L1917/L1923：APP 等级提升推送本地通知
          void notifyLevelUp(result.levelUp)
        }
        loadCalendar(year, month)
      }
    } catch (err) {
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
    const startOffset = new Date(year, month - 1, 1).getDay()
    const cells: Array<{ day: number; dateStr: string }> = []
    for (let d = 1; d <= daysInMonth; d++) {
      cells.push({ day: d, dateStr: formatDate(year, month, d) })
    }
    return { startOffset, cells }
  }, [year, month])

  const isCurrentMonth = year === now.getFullYear() && month === now.getMonth() + 1
  const todayDay = isCurrentMonth ? now.getDate() : -1

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: Spacing.md, borderBottomWidth: 1, borderBottomColor: WishColors.border }}>
          <TouchableOpacity onPress={() => router.back()} accessibilityLabel="返回" accessibilityRole="button">
            <Text style={{ fontSize: FontSize.lg, color: WishColors.textSecondary }}>‹ 返回</Text>
          </TouchableOpacity>
          <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>每日签到</Text>
          <View style={{ width: 44 }} />
        </View>
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color={WishColors.primary} />
        </View>
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: Spacing.md, borderBottomWidth: 1, borderBottomColor: WishColors.border }}>
        <TouchableOpacity onPress={() => router.back()} accessibilityLabel="返回" accessibilityRole="button">
          <Text style={{ fontSize: FontSize.lg, color: WishColors.textSecondary }}>‹ 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>每日签到</Text>
        <View style={{ width: 44 }} />
      </View>

      <ScrollView
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={WishColors.primary} />}
        contentContainerStyle={{ paddingHorizontal: Spacing.md, paddingTop: Spacing.md, paddingBottom: insets.bottom + 24 }}
      >
        {/* 签到卡片 */}
        <View
          style={{
            padding: Spacing.xl,
            borderRadius: BorderRadius.xl,
            backgroundColor: 'rgba(255, 215, 0, 0.08)',
            borderWidth: 1,
            borderColor: 'rgba(255, 215, 0, 0.3)',
            alignItems: 'center',
          }}
        >
          <View style={{ flexDirection: 'row', gap: Spacing.xxl, marginBottom: Spacing.lg }}>
            <View style={{ alignItems: 'center' }}>
              <Text style={{ fontSize: 32, fontWeight: '700', color: '#ffd700' }}>{consecutiveDays}</Text>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 4 }}>连续签到（天）</Text>
            </View>
            <View style={{ width: 1, height: 48, backgroundColor: 'rgba(255,255,255,0.12)' }} />
            <View style={{ alignItems: 'center' }}>
              <Text style={{ fontSize: 32, fontWeight: '700', color: '#ffd700' }}>{totalDays}</Text>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 4 }}>累计签到（天）</Text>
            </View>
          </View>
          <TouchableOpacity
            activeOpacity={0.8}
            disabled={signedToday || signing}
            onPress={handleSignin}
            accessibilityLabel={signedToday ? '今日已签到' : '签到领星光'}
            accessibilityRole="button"
            style={{
              minWidth: 200,
              paddingVertical: 12,
              paddingHorizontal: Spacing.xl,
              borderRadius: BorderRadius.full,
              alignItems: 'center',
              backgroundColor: signedToday ? 'rgba(255,255,255,0.08)' : '#ffd700',
              borderWidth: signedToday ? 1 : 0,
              borderColor: 'rgba(255,255,255,0.2)',
            }}
          >
            <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: signedToday ? 'rgba(255,255,255,0.7)' : '#0c1b3a' }}>
              {signing ? '签到中...' : signedToday ? '今日已签到 ✓' : '签到领星光 +5'}
            </Text>
          </TouchableOpacity>
          <Text style={{ marginTop: Spacing.md, fontSize: FontSize.xs, color: WishColors.textTertiary }}>
            明日签到可获得星光 +5
          </Text>
        </View>

        {/* 星光余额 */}
        {resources && (
          <View
            style={{
              marginTop: Spacing.md,
              padding: Spacing.lg,
              borderRadius: BorderRadius.lg,
              backgroundColor: 'rgba(255,255,255,0.04)',
              borderWidth: 1,
              borderColor: 'rgba(255,255,255,0.1)',
            }}
          >
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>当前星光余额</Text>
            <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: Spacing.xs, marginTop: Spacing.xs }}>
              <Text style={{ fontSize: 22 }}>⭐</Text>
              <StarCountUp value={resources.balance} delta={rewardDelta} />
            </View>
            <View style={{ flexDirection: 'row', gap: Spacing.lg, marginTop: Spacing.sm }}>
              <Text style={{ fontSize: FontSize.xs, color: '#52c41a' }}>今日 +{resources.todayEarned}</Text>
              <Text style={{ fontSize: FontSize.xs, color: '#ff7875' }}>支出 -{resources.todaySpent}</Text>
            </View>
          </View>
        )}

        {/* 签到日历 */}
        <View
          style={{
            marginTop: Spacing.md,
            padding: Spacing.lg,
            borderRadius: BorderRadius.lg,
            backgroundColor: 'rgba(255,255,255,0.04)',
            borderWidth: 1,
            borderColor: 'rgba(255,255,255,0.1)',
          }}
        >
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <TouchableOpacity
              onPress={() => switchMonth(-1)}
              accessibilityLabel="上一月"
              accessibilityRole="button"
              style={{ width: 32, height: 32, borderRadius: 16, backgroundColor: 'rgba(255,255,255,0.06)', alignItems: 'center', justifyContent: 'center' }}
            >
              <Text style={{ fontSize: 18, color: WishColors.textSecondary }}>‹</Text>
            </TouchableOpacity>
            <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text }}>
              {year} 年 {month} 月
            </Text>
            <TouchableOpacity
              onPress={() => isCurrentMonth ? undefined : switchMonth(1)}
              disabled={isCurrentMonth}
              accessibilityLabel="下一月"
              accessibilityRole="button"
              style={{ width: 32, height: 32, borderRadius: 16, backgroundColor: isCurrentMonth ? 'rgba(255,255,255,0.02)' : 'rgba(255,255,255,0.06)', alignItems: 'center', justifyContent: 'center', opacity: isCurrentMonth ? 0.3 : 1 }}
            >
              <Text style={{ fontSize: 18, color: WishColors.textSecondary }}>›</Text>
            </TouchableOpacity>
          </View>
          <View style={{ flexDirection: 'row', marginTop: Spacing.md }}>
            {WEEK_DAYS.map((d) => (
              <Text key={d} style={{ flex: 1, textAlign: 'center', fontSize: FontSize.xs, color: WishColors.textTertiary }}>
                {d}
              </Text>
            ))}
          </View>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', marginTop: Spacing.xs }}>
            {Array.from({ length: calendarCells.startOffset }).map((_, i) => (
              <View key={`empty-${i}`} style={{ width: '14.2857%', height: 44 }} />
            ))}
            {calendarCells.cells.map(({ day, dateStr }) => {
              const isSigned = signedDates.has(dateStr)
              const isToday = day === todayDay
              const isFuture = isCurrentMonth && day > now.getDate()
              return (
                <View
                  key={dateStr}
                  style={{
                    width: '14.2857%',
                    height: 44,
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderRadius: 8,
                    marginVertical: 2,
                    backgroundColor: isSigned ? 'rgba(255,215,0,0.2)' : 'transparent',
                    borderWidth: !isSigned && isToday ? 1 : 0,
                    borderColor: 'rgba(0,212,255,0.6)',
                  }}
                >
                  <Text
                    style={{
                      fontSize: FontSize.sm,
                      color: isSigned ? '#ffd700' : isFuture ? 'rgba(255,255,255,0.25)' : WishColors.text,
                      fontWeight: isSigned ? '700' : '400',
                    }}
                  >
                    {day}
                  </Text>
                </View>
              )
            })}
          </View>
        </View>

        {/* 签到规则 */}
        <View
          style={{
            marginTop: Spacing.md,
            padding: Spacing.lg,
            borderRadius: BorderRadius.lg,
            backgroundColor: 'rgba(255,255,255,0.03)',
            borderWidth: 1,
            borderColor: 'rgba(255,255,255,0.15)',
            borderStyle: 'dashed',
          }}
        >
          <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: 'rgba(255,255,255,0.85)' }}>签到规则</Text>
          <Text style={{ marginTop: Spacing.xs, fontSize: FontSize.xs, color: WishColors.textTertiary }}>
            · 每日签到获得星光 +5（按你的本地时区按日去重）{'\n'}
            · 连续签到天数在断签后重新计算，累计签到天数永久保留{'\n'}
            · 星光可用于点亮他人心愿、兑换虚拟资产（上限 5000）
          </Text>
        </View>
      </ScrollView>

      <LevelUpModal levelUp={levelUp} onClose={() => setLevelUp(null)} />
      <WishBGM />
    </View>
  )
}
