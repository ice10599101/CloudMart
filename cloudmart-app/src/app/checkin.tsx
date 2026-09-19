import { View, Text, ScrollView, TouchableOpacity, Animated, Alert } from 'react-native'
import { useState, useEffect, useRef } from 'react'
import { useTheme } from '@/hooks/use-theme-context'
import { growthApi } from '@/api/growth'
import { useAuthStore } from '@/store/auth'
import { storage } from '@/utils/storage'
import type { ExpLog, LevelConfig } from '@/types'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

interface LevelInfo {
  level: number
  exp: number
  nextLevelExp: number
  title: string
}

const WEEK_DAYS = ['一', '二', '三', '四', '五', '六', '日']

export default function CheckInPage() {
  const theme = useTheme()
  const [isCheckedIn, setIsCheckedIn] = useState(false)
  const [continuousDays, setContinuousDays] = useState(0)
  const [levelInfo, setLevelInfo] = useState<LevelInfo | null>(null)
  const [checkedDays, setCheckedDays] = useState<number[]>([])
  const [currentYear, setCurrentYear] = useState(new Date().getFullYear())
  const [currentMonth, setCurrentMonth] = useState(new Date().getMonth() + 1)
  const [checkingIn, setCheckingIn] = useState(false)
  const [expAnim, setExpAnim] = useState('')
  const [levelConfigs, setLevelConfigs] = useState<LevelConfig[]>([])
  const [expLogs, setExpLogs] = useState<ExpLog[]>([])
  const [expOpen, setExpOpen] = useState(false)
  const [avatarFrame, setAvatarFrameState] = useState('none')
  const { user } = useAuthStore()
  // 头像框方案（对齐 Web 端 AVATAR_FRAMES，Lv2+ 解锁）
  const AVATAR_FRAMES = [
    { key: 'none', label: '默认', color: 'transparent' },
    { key: 'gold', label: '金环', color: '#ffd700' },
    { key: 'purple', label: '紫晕', color: '#9370db' },
    { key: 'green', label: '翠光', color: '#2ed573' },
    { key: 'pink', label: '樱粉', color: '#ff7eb3' },
    { key: 'rainbow', label: '彩虹', color: '#00d4ff' },
  ]
  const fadeAnim = useRef(new Animated.Value(1)).current

  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    try {
      const [statusRes, levelRes, continuousRes] = await Promise.all([
        growthApi.getCheckInStatus(),
        growthApi.getUserLevel(),
        growthApi.getContinuousDays(),
      ])
      setIsCheckedIn(statusRes.data?.data?.isCheckedIn || false)
      const ld = levelRes.data?.data
      if (ld) setLevelInfo({ level: ld.level, exp: ld.exp, nextLevelExp: ld.nextLevelExp, title: ld.title })
      setContinuousDays(continuousRes.data?.data || 0)

      // 等级体系 + 经验变动 + 头像框（对齐 Web 端 UserCenter 面板数据源）
      growthApi.getLevelConfigs().then((res) => setLevelConfigs((res.data as { data?: LevelConfig[] })?.data ?? [])).catch(() => {})
      growthApi.getExpLogs({ page: 1, pageSize: 20 }).then((res) => setExpLogs((res.data as { data?: { list?: ExpLog[] } })?.data?.list ?? [])).catch(() => {})
    } catch {
      // API unavailable
    }
    loadCalendar(currentYear, currentMonth)
  }

  useEffect(() => {
    void (async () => {
      const stored = await storage.getItem('avatar_frame')
      setAvatarFrameState(stored || 'none')
    })()
  }, [])

  /** 头像框切换（Lv2+ 权益；本地持久化 + 后端同步，对齐 Web 端） */
  const applyAvatarFrame = async (key: string) => {
    if ((levelInfo?.level ?? 0) < 2) {
      Alert.alert('提示', 'Lv2 解锁自定义头像框')
      return
    }
    setAvatarFrameState(key)
    void storage.setItem('avatar_frame', key)
    void growthApi.setAvatarFrame(key).catch(() => {})
  }

  const loadCalendar = async (year: number, month: number) => {
    try {
      const res = await growthApi.getCheckInCalendar(year, month)
      const days = res.data?.data || []
      setCheckedDays(days.map((d) => Number(d)).filter((d) => !Number.isNaN(d)))
    } catch {
      setCheckedDays([])
    }
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
      Animated.sequence([
        Animated.timing(fadeAnim, { toValue: 0, duration: 2000, useNativeDriver: true }),
      ]).start(() => { setExpAnim(''); fadeAnim.setValue(1) })
      loadData()
    } catch {
      // Failed
    } finally {
      setCheckingIn(false)
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

  const expPercent = levelInfo ? Math.min((levelInfo.exp / levelInfo.nextLevelExp) * 100, 100) : 0
  const bonusExp = Math.min(continuousDays * 5, 50)
  const today = new Date().getDate()
  const daysInMonth = new Date(currentYear, currentMonth, 0).getDate()
  const firstDayOfWeek = new Date(currentYear, currentMonth - 1, 1).getDay() || 7

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView contentContainerStyle={{ paddingBottom: Spacing.xxxl }}>
        {/* Level Info Card */}
        {levelInfo && (
          <View style={{ marginHorizontal: Spacing.lg, marginTop: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.lg }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.md, marginBottom: Spacing.md }}>
              <View style={{ paddingHorizontal: Spacing.md, paddingVertical: Spacing.xs, backgroundColor: '#FFD700', borderRadius: BorderRadius.xl }}>
                <Text style={{ fontSize: FontSize.md, fontWeight: '800', color: theme.bgBase }}>Lv.{levelInfo.level}</Text>
              </View>
              <View style={{ flex: 1 }}>
                <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text }}>{levelInfo.title}</Text>
                <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: 2 }}>{levelInfo.exp} / {levelInfo.nextLevelExp} EXP</Text>
              </View>
            </View>
            <View style={{ height: 6, backgroundColor: theme.border, borderRadius: 3, overflow: 'hidden' }}>
              <View style={{ height: '100%', width: `${expPercent}%`, backgroundColor: '#FFD700', borderRadius: 3 }} />
            </View>
          </View>
        )}

        {/* Check In Button */}
        <View style={{ marginHorizontal: Spacing.lg, marginTop: Spacing.md, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.xxl, alignItems: 'center' }}>
          <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: theme.text }}>连续签到 {continuousDays} 天</Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>
            今日可获得 {10 + bonusExp} EXP（基础10 + 连续奖励{bonusExp}）
          </Text>
          <TouchableOpacity
            onPress={handleCheckIn}
            disabled={isCheckedIn || checkingIn}
            style={{
              width: 120, height: 120, borderRadius: 60,
              backgroundColor: isCheckedIn ? theme.border : theme.primary,
              justifyContent: 'center', alignItems: 'center', marginTop: Spacing.xl,
              opacity: checkingIn ? 0.7 : 1,
            }}
          >
            <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: '#FFFFFF' }}>
              {checkingIn ? '签到中...' : isCheckedIn ? '已签到' : '立即签到'}
            </Text>
          </TouchableOpacity>
          {expAnim ? (
            <Animated.Text style={{ position: 'absolute', top: '50%', fontSize: FontSize.xxl, fontWeight: '800', color: '#FFD700', opacity: fadeAnim }}>
              {expAnim}
            </Animated.Text>
          ) : null}
        </View>

        {/* Calendar */}
        <View style={{ marginHorizontal: Spacing.lg, marginTop: Spacing.md, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.lg }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: Spacing.lg }}>
            <TouchableOpacity onPress={handlePrevMonth} style={{ width: 32, height: 32, borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, justifyContent: 'center', alignItems: 'center' }}>
              <Text style={{ fontSize: FontSize.lg, color: theme.text }}>‹</Text>
            </TouchableOpacity>
            <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text }}>{currentYear}年{currentMonth}月</Text>
            <TouchableOpacity onPress={handleNextMonth} style={{ width: 32, height: 32, borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, justifyContent: 'center', alignItems: 'center' }}>
              <Text style={{ fontSize: FontSize.lg, color: theme.text }}>›</Text>
            </TouchableOpacity>
          </View>

          {/* Week header */}
          <View style={{ flexDirection: 'row', marginBottom: Spacing.sm }}>
            {WEEK_DAYS.map((d) => (
              <Text key={d} style={{ flex: 1, textAlign: 'center', fontSize: FontSize.sm, color: theme.textTertiary, fontWeight: '500' }}>{d}</Text>
            ))}
          </View>

          {/* Days grid */}
          <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
            {/* Empty cells for start of month */}
            {Array.from({ length: firstDayOfWeek - 1 }).map((_, i) => (
              <View key={`empty-${i}`} style={{ width: `${100 / 7}%`, aspectRatio: 1 }} />
            ))}
            {Array.from({ length: daysInMonth }).map((_, i) => {
              const day = i + 1
              const isChecked = checkedDays.includes(day)
              const isToday = currentYear === new Date().getFullYear() && currentMonth === new Date().getMonth() + 1 && day === today
              const isFuture = currentYear === new Date().getFullYear() && currentMonth === new Date().getMonth() + 1 && day > today
              return (
                <View key={day} style={{
                  width: `${100 / 7}%`, aspectRatio: 1,
                  justifyContent: 'center', alignItems: 'center',
                  borderRadius: BorderRadius.md,
                  borderWidth: isToday ? 2 : 0,
                  borderColor: isToday ? theme.primary : 'transparent',
                  backgroundColor: isChecked ? `${theme.primary}15` : 'transparent',
                  opacity: isFuture ? 0.3 : 1,
                }}>
                  <Text style={{ fontSize: FontSize.sm, color: theme.text }}>{day}</Text>
                  {isChecked && <Text style={{ fontSize: 8, color: '#32CD32', fontWeight: '700' }}>✓</Text>}
                </View>
              )
            })}
          </View>
        </View>

        {/* Rules */}
        <View style={{ marginHorizontal: Spacing.lg, marginTop: Spacing.md, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text, marginBottom: Spacing.md }}>签到奖励规则</Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, lineHeight: 24 }}>• 每日签到获得 10 EXP 基础经验</Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, lineHeight: 24 }}>• 连续签到每天额外 +5 EXP（上限 +50）</Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, lineHeight: 24 }}>• 断签后连续天数重置</Text>
        </View>
      </ScrollView>
    </View>
  )
}
