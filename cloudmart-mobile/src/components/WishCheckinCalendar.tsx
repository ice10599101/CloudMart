import { useEffect, useMemo, useState } from 'react'
import { View, Text } from '@tarojs/components'
import type { CommonEventFunction } from '@tarojs/components'
import { wishApi } from '@/api/wish'

const WEEK = ['日', '一', '二', '三', '四', '五', '六']

/**
 * 心愿打卡日历（Sprint 1.3 验收，移动端）：
 * 当月已打卡日点亮 + 今日描边；按心愿维度（GET /wish/wishes/{id}/checkins）。
 */
export default function WishCheckinCalendar({ wishId, accentColor }: { wishId: string; accentColor: string }) {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [lit, setLit] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true
    const mm = String(month).padStart(2, '0')
    setLoading(true)
    wishApi
      .getWishCheckinCalendar(wishId, `${year}-${mm}`)
      .then((res) => {
        if (alive && res.data.success) setLit(new Set(res.data.data?.dates ?? []))
      })
      .catch(() => {
        if (alive) setLit(new Set())
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [wishId, year, month])

  const cells = useMemo(() => {
    const first = new Date(year, month - 1, 1)
    const days = new Date(year, month, 0).getDate()
    const blanks = Array.from({ length: first.getDay() }, () => null)
    const dayList = Array.from({ length: days }, (_, i) => new Date(year, month - 1, i + 1))
    return [...blanks, ...dayList]
  }, [year, month])

  const shift = (delta: number) => {
    const d = new Date(year, month - 1 + delta, 1)
    setYear(d.getFullYear())
    setMonth(d.getMonth() + 1)
  }

  const todayKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`

  // 横滑切月（H5/小程序手势）：横向位移超过 40px 触发
  const touchStartX = { current: 0 }
  const handleTouchStart: CommonEventFunction = (e) => {
    const touches = (e as unknown as { touches?: { clientX: number }[] }).touches
    touchStartX.current = touches?.[0]?.clientX ?? 0
  }
  const handleTouchEnd: CommonEventFunction = (e) => {
    const changed = (e as unknown as { changedTouches?: { clientX: number }[] }).changedTouches
    const dx = (changed?.[0]?.clientX ?? 0) - touchStartX.current
    if (Math.abs(dx) > 40) shift(dx < 0 ? 1 : -1)
    touchStartX.current = 0
  }

  return (
    <View style={styles.wrap} onTouchStart={handleTouchStart} onTouchEnd={handleTouchEnd}>
      <View style={styles.navRow}>
        <Text style={styles.navBtn} onClick={() => shift(-1)}>‹</Text>
        <Text style={styles.monthLabel}>{year}年{month}月</Text>
        <Text style={styles.navBtn} onClick={() => shift(1)}>›</Text>
      </View>
      <View style={styles.grid}>
        {WEEK.map((w) => (
          <Text key={w} style={styles.weekHead}>{w}</Text>
        ))}
        {cells.map((day, idx) => {
          if (day === null) return <View key={`b${idx}`} />
          const key = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
          const isLit = lit.has(key)
          const isToday = key === todayKey
          return (
            <View
              key={key}
              style={{
                ...styles.cell,
                background: isLit ? accentColor : 'transparent',
                borderColor: isToday ? '#4a90d9' : 'rgba(255,255,255,0.15)',
                opacity: isLit ? 1 : 0.7,
              }}
            >
              <Text style={{ color: isLit ? '#fff' : 'inherit', fontSize: 22 }}>{day.getDate()}</Text>
            </View>
          )
        })}
      </View>
      {!loading && lit.size === 0 && <Text style={styles.emptyHint}>本月还没有打卡记录</Text>}
    </View>
  )
}

const styles = {
  wrap: {
    marginTop: '16px',
  },
  navRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '10px',
  },
  navBtn: {
    fontSize: '30px',
    padding: '4px 20px',
    opacity: 0.7,
  },
  monthLabel: {
    fontSize: '26px',
    fontWeight: '600',
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(7, 1fr)',
    gap: '8px',
  },
  weekHead: {
    textAlign: 'center' as const,
    fontSize: '20px',
    opacity: 0.55,
  },
  cell: {
    aspectRatio: '1',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: '12px',
    border: '1px solid rgba(255,255,255,0.15)',
  },
  emptyHint: {
    fontSize: '20px',
    opacity: 0.5,
    textAlign: 'center' as const,
    marginTop: '12px',
  },
}
