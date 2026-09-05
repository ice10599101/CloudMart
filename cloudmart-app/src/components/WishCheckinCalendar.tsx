import { useEffect, useMemo, useState } from 'react'
import { View, Text, TouchableOpacity } from 'react-native'
import dayjs from 'dayjs'
import { wishApi } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

/**
 * 心愿打卡日历（Sprint 1.3 验收，APP 端）：当月已打卡日点亮 + 今日描边。
 */
export default function WishCheckinCalendar({ wishId, accentColor }: { wishId: string; accentColor: string }) {
  const now = dayjs()
  const [month, setMonth] = useState(dayjs().startOf('month'))
  const [lit, setLit] = useState<Set<string>>(new Set())

  useEffect(() => {
    let alive = true
    wishApi
      .getWishCheckinCalendar(wishId, month.format('YYYY-MM'))
      .then((res) => {
        if (alive && res.data?.success) setLit(new Set(res.data.data?.dates ?? []))
      })
      .catch(() => {
        if (alive) setLit(new Set())
      })
    return () => {
      alive = false
    }
  }, [wishId, month])

  const cells = useMemo(() => {
    const first = month.startOf('month')
    const blanks = Array.from({ length: first.day() }, () => null)
    const days = Array.from({ length: month.daysInMonth() }, (_, i) => first.add(i, 'day'))
    return [...blanks, ...days]
  }, [month])

  const today = dayjs().format('YYYY-MM-DD')

  return (
    <View style={{ marginTop: Spacing.md }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
        <TouchableOpacity onPress={() => setMonth((m) => m.subtract(1, 'month'))}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>‹</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text }}>
          {month.format('YYYY年M月')}
        </Text>
        <TouchableOpacity
          onPress={() => setMonth((m) => m.add(1, 'month'))}
          disabled={month.isAfter(dayjs(), 'month')}
        >
          <Text style={{ fontSize: FontSize.md, color: month.isAfter(dayjs(), 'month') ? WishColors.textTertiary : WishColors.textSecondary }}>›</Text>
        </TouchableOpacity>
      </View>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 4 }}>
        {['日', '一', '二', '三', '四', '五', '六'].map((w) => (
          <View key={w} style={{ width: `${100 / 7 - 1}%`, alignItems: 'center', paddingVertical: 2 }}>
            <Text style={{ fontSize: 10, color: WishColors.textTertiary }}>{w}</Text>
          </View>
        ))}
        {cells.map((day, idx) => {
          if (day === null) return <View key={`b${idx}`} style={{ width: `${100 / 7 - 1}%` }} />
          const key = day.format('YYYY-MM-DD')
          const isLit = lit.has(key)
          const isToday = key === today
          return (
            <View
              key={key}
              style={{
                width: `${100 / 7 - 1}%`,
                aspectRatio: 1,
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: 8,
                backgroundColor: isLit ? accentColor : 'transparent',
                borderWidth: isToday ? 2 : 1,
                borderColor: isToday ? WishColors.accentCyan : WishColors.border,
                opacity: isLit ? 1 : 0.7,
              }}
            >
              <Text style={{ fontSize: FontSize.xs, color: isLit ? '#ffffff' : WishColors.textSecondary }}>
                {day.date()}
              </Text>
            </View>
          )
        })}
      </View>
      {lit.size === 0 && (
        <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: Spacing.xs }}>
          本月还没有打卡记录
        </Text>
      )}
    </View>
  )
}
