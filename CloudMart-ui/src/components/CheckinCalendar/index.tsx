import { useEffect, useMemo, useState } from 'react'
import { Button } from 'antd'
import { LeftOutlined, RightOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { getWishCheckinCalendar } from '@/api/wish'

/**
 * 心愿打卡日历（Sprint 1.3 验收，四AB WEB P0-1）：
 * 当月已打卡日点亮、今日特殊描边；按心愿维度（GET /wish/wishes/{id}/checkins）。
 * 仅作者可见（后端 403 契约），由父组件守卫。
 */

export interface CheckinCalendarProps {
    wishId: number | string
    /** 果实主题色（点亮日背景） */
    accentColor: string
}

const WEEK_HEADERS = ['日', '一', '二', '三', '四', '五', '六']

export default function CheckinCalendar({ wishId, accentColor }: CheckinCalendarProps) {
    const [month, setMonth] = useState<Dayjs>(dayjs())
    const [litDates, setLitDates] = useState<Set<string>>(new Set())
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        let alive = true
        setLoading(true)
        getWishCheckinCalendar(wishId, month.format('YYYY-MM'))
            .then((res) => {
                if (alive && res.data.success) {
                    setLitDates(new Set(res.data.data?.dates ?? []))
                }
            })
            .catch(() => {
                if (alive) setLitDates(new Set())
            })
            .finally(() => {
                if (alive) setLoading(false)
            })
        return () => {
            alive = false
        }
    }, [wishId, month])

    // 当月网格：前置空位 + 日期
    const cells = useMemo(() => {
        const first = month.startOf('month')
        const daysInMonth = month.daysInMonth()
        const blanks = Array.from({ length: first.day() }, (_, i) => null)
        const days = Array.from({ length: daysInMonth }, (_, i) => first.add(i, 'day'))
        return [...blanks, ...days]
    }, [month])

    const today = dayjs().format('YYYY-MM-DD')

    return (
        <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
                <Button size="small" type="text" icon={<LeftOutlined />} aria-label="上一月"
                        onClick={() => setMonth((m) => m.subtract(1, 'month'))} />
                <span style={{ fontWeight: 600, fontSize: 14 }}>{month.format('YYYY年MM月')}</span>
                <Button size="small" type="text" icon={<RightOutlined />} aria-label="下一月"
                        disabled={month.isAfter(dayjs(), 'month')}
                        onClick={() => setMonth((m) => m.add(1, 'month'))} />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 4 }}>
                {WEEK_HEADERS.map((w) => (
                    <div key={w} style={{ textAlign: 'center', fontSize: 12, color: 'var(--color-text-tertiary)', padding: '4px 0' }}>
                        {w}
                    </div>
                ))}
                {cells.map((day, idx) => {
                    if (day === null) return <div key={`blank-${idx}`} />
                    const key = day.format('YYYY-MM-DD')
                    const lit = litDates.has(key)
                    const isToday = key === today
                    return (
                        <div
                            key={key}
                            aria-label={lit ? `${key} 已打卡` : key}
                            style={{
                                aspectRatio: '1',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                borderRadius: 8,
                                fontSize: 13,
                                background: lit ? accentColor : 'transparent',
                                color: lit ? '#ffffff' : 'var(--color-text-secondary)',
                                fontWeight: lit ? 600 : 400,
                                border: isToday ? '2px solid var(--color-primary)' : '1px solid var(--color-border)',
                                opacity: lit ? 1 : 0.7,
                            }}
                        >
                            {day.date()}
                            {lit && <span style={{ fontSize: 9, marginLeft: 2 }}>✓</span>}
                        </div>
                    )
                })}
            </div>
            {!loading && litDates.size === 0 && (
                <div style={{ textAlign: 'center', fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 8 }}>
                    本月还没有打卡记录
                </div>
            )}
        </div>
    )
}
