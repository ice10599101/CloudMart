import { useEffect, useRef, useState } from 'react'
import type { StageHudSnapshot } from './stageEngine'
import { ACTION_BUTTONS, NAV_BUTTONS, SPECIES_EMOJI, STATE_ROWS } from './theme'
import styles from './stageHud.module.css'

/**
 * 舞台 HUD（宠物名牌 / 状态条 / 动作按钮 / 功能导航 / 对话气泡）。
 *
 * 交互原则（对应"交互没有反馈"的问题）：
 *  - 数值不跳变：目标值变化后由 rAF 逐帧插值（useSmoothValue），全程可见过渡；
 *  - 数值有反馈：增长/下降时数字脉冲高亮（绿涨红跌），玩家能感知到"刚刚发生了什么"；
 *  - 按钮三态：悬停抬升 / 按下回弹 / 请求中禁用并带光圈呼吸；
 *  - 卡片入场：铭牌与状态卡错峰入场，避免整体"啪"地出现。
 */

interface StageHudProps {
    snapshot: StageHudSnapshot | null
    bubble: { id: number; text: string } | null
    onIntent: (intent: string) => void
}

/** 单值平滑：把目标值逐帧插值成显示值（速度 speed 越大越快） */
function useSmoothValue(target: number, speed = 6): number {
    const [value, setValue] = useState(target)
    const currentRef = useRef(target)
    const targetRef = useRef(target)
    targetRef.current = target

    useEffect(() => {
        let raf = 0
        let last = performance.now()
        const step = (now: number): void => {
            const dt = Math.min((now - last) / 1000, 0.1)
            last = now
            const goal = targetRef.current
            const diff = goal - currentRef.current
            if (Math.abs(diff) < 0.25) {
                if (currentRef.current !== goal) {
                    currentRef.current = goal
                    setValue(goal)
                }
            } else {
                currentRef.current += diff * Math.min(1, dt * speed)
                setValue(currentRef.current)
            }
            raf = requestAnimationFrame(step)
        }
        raf = requestAnimationFrame(step)
        return () => cancelAnimationFrame(raf)
    }, [speed])

    return value
}

/** 数值变化脉冲：增长 → 绿闪，下降 → 红闪 */
function useValuePulse(value: number): 'up' | 'down' | null {
    const prevRef = useRef(value)
    const [pulse, setPulse] = useState<'up' | 'down' | null>(null)
    useEffect(() => {
        const previous = prevRef.current
        prevRef.current = value
        if (value === previous) {
            return
        }
        setPulse(value > previous ? 'up' : 'down')
        const timer = window.setTimeout(() => setPulse(null), 520)
        return () => window.clearTimeout(timer)
    }, [value])
    return pulse
}

interface StateBarProps {
    icon: string
    label: string
    color: string
    value: number
    max: number
}

function StateBar({ icon, label, color, value, max }: StateBarProps) {
    const smooth = useSmoothValue(value)
    const pulse = useValuePulse(Math.round(value))
    const ratio = max > 0 ? Math.max(0, Math.min(1, smooth / max)) : 0
    return (
        <div className={styles.stateRow}>
            <span className={styles.stateIcon}>{icon}</span>
            <span className={styles.stateLabel}>{label}</span>
            <div className={styles.stateTrack}>
                <div className={styles.stateFill} style={{ width: `${ratio * 100}%`, background: `linear-gradient(90deg, ${color}BB, ${color})` }} />
            </div>
            <span className={`${styles.stateValue} ${pulse === 'up' ? styles.stateValueUp : ''} ${pulse === 'down' ? styles.stateValueDown : ''}`}>
                {Math.round(smooth)}
            </span>
        </div>
    )
}

export default function StageHud({ snapshot, bubble, onIntent }: StageHudProps) {
    const expFillRef = useRef<HTMLDivElement>(null)
    const expSmooth = useSmoothValue((snapshot?.expPercent ?? 0) * 100, 5)

    useEffect(() => {
        if (expFillRef.current) {
            expFillRef.current.style.width = `${Math.max(0, Math.min(100, expSmooth))}%`
        }
    }, [expSmooth])

    if (!snapshot) {
        return <div className={styles.hud} />
    }

    const busy = snapshot.busyIntent
    return (
        <div className={styles.hud}>
            {/* 顶部铭牌 */}
            <div className={styles.nameplate}>
                <div className={styles.badge}>{snapshot.level}</div>
                <div className={styles.identity}>
                    <span className={styles.name}>
                        {SPECIES_EMOJI[snapshot.species] || '🐾'} {snapshot.name}
                        <span
                            className={snapshot.gender === 'FEMALE' ? styles.genderFemale : styles.genderMale}
                            aria-label={snapshot.gender === 'FEMALE' ? '女' : '男'}
                        >
                            {snapshot.gender === 'FEMALE' ? '♀' : '♂'}
                        </span>
                    </span>
                    <span className={styles.status}>
                        {snapshot.statusLabel}{snapshot.growthLabel ? ` · ${snapshot.growthLabel}` : ''}
                    </span>
                </div>
                <div className={styles.expTrack}>
                    <div ref={expFillRef} className={styles.expFill} />
                </div>
            </div>

            {/* 左侧状态卡 */}
            <div className={styles.stateCard}>
                {STATE_ROWS.map((row) => {
                    switch (row.key) {
                        case 'hp':
                            return <StateBar key={row.key} icon={row.icon} label={row.label} color={row.color}
                                value={snapshot.hp} max={snapshot.maxHp > 0 ? snapshot.maxHp : 100} />
                        case 'hunger':
                            return <StateBar key={row.key} icon={row.icon} label={row.label} color={row.color}
                                value={snapshot.hunger} max={100} />
                        case 'happiness':
                            return <StateBar key={row.key} icon={row.icon} label={row.label} color={row.color}
                                value={snapshot.happiness} max={100} />
                        case 'energy':
                            return <StateBar key={row.key} icon={row.icon} label={row.label} color={row.color}
                                value={snapshot.energy} max={100} />
                        default:
                            return <StateBar key={row.key} icon={row.icon} label={row.label} color={row.color}
                                value={snapshot.cleanliness} max={100} />
                    }
                })}
            </div>

            {/* 对话气泡 */}
            {bubble && (
                <div key={bubble.id} className={styles.bubble}>
                    {bubble.text}
                </div>
            )}

            {/* 底部动作按钮 */}
            <div className={styles.actionDock}>
                {ACTION_BUTTONS.map((action) => (
                    <button
                        key={action.intent}
                        type="button"
                        className={styles.actionButton}
                        style={{ background: action.gradient }}
                        disabled={busy === action.intent}
                        aria-label={action.label}
                        onClick={() => onIntent(action.intent)}
                    >
                        <span aria-hidden>{action.icon}</span>
                        <span className={styles.actionLabel}>{action.label}</span>
                    </button>
                ))}
            </div>

            {/* 功能导航 */}
            <div className={styles.navDock}>
                {NAV_BUTTONS.map((nav) => (
                    <button
                        key={nav.intent}
                        type="button"
                        className={styles.navButton}
                        onClick={() => onIntent(nav.intent)}
                    >
                        <span aria-hidden>{nav.icon}</span>
                        {nav.label}
                    </button>
                ))}
            </div>
        </div>
    )
}
