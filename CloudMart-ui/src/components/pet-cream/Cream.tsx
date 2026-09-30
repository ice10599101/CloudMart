/**
 * 法式奶油风基础组件（Petit Crème）。
 *
 * 立体规则：承托件一律带挤出底边与暖调投影，凹陷件走玻璃内阴影，
 * 舞台件用透视地台把 3D 宠物"坐"进纸面——与 Cocos 里的水果宠物保持同一套体积语言。
 */
import type { ButtonHTMLAttributes, CSSProperties, ReactNode } from 'react'
import styles from './cream.module.css'

/** 五果主题色（与 Cocos FRUIT_SPECS 果体主色同源；旧动物码为迁移过渡期兼容） */
export const FRUIT_ACCENT: Record<string, string> = {
    STRAWBERRY: '#E85D7A',
    ORANGE: '#F08A1F',
    WATERMELON: '#4E9A34',
    BLUEBERRY: '#5A7CC4',
    DRAGONFRUIT: '#E93B72',
    CAT: '#E85D7A',
    DOG: '#F08A1F',
    RABBIT: '#4E9A34',
    FOX: '#5A7CC4',
    PANDA: '#E93B72',
}

/** 五果身份符号 */
export const SPECIES_EMOJI: Record<string, string> = {
    STRAWBERRY: '🍓',
    ORANGE: '🍊',
    WATERMELON: '🍉',
    BLUEBERRY: '🫐',
    DRAGONFRUIT: '🐉',
    CAT: '🍓',
    DOG: '🍊',
    RABBIT: '🍉',
    FOX: '🫐',
    PANDA: '🐉',
}

/** 五维状态的奶油调语义色（生命/饱食/心情/精力/清洁） */
export const STAT_TONE: Record<string, string> = {
    hp: '#D98A8A',
    hunger: '#E0A45C',
    happiness: '#E8A7AC',
    energy: '#93AC7F',
    cleanliness: '#9CB6CC',
}

export function fruitAccent(species: string | undefined): string {
    return FRUIT_ACCENT[species ?? ''] ?? '#A97C50'
}

interface CreamCardProps {
    /** arch 拱形主视觉 / menu 双线餐牌 / plain 光面 */
    variant?: 'arch' | 'menu' | 'plain'
    /** 眉标（法式小标签，如 MON PETIT） */
    label?: string
    title?: string
    subtitle?: string
    /** 强调色（果色），作用于标题与描边 */
    accent?: string
    children?: ReactNode
    className?: string
    style?: CSSProperties
}

export function CreamCard(props: CreamCardProps) {
    const { variant = 'menu', label, title, subtitle, accent, children, className, style } = props
    const shape = variant === 'arch' ? styles.arch : variant === 'menu' ? styles.menu : ''
    return (
        <section
            className={`${styles.card} ${shape} ${className ?? ''}`}
            style={{ ...style, ...(accent ? { borderColor: `${accent}66` } : null) }}
        >
            {label ? (
                <div className={styles.ribbon}>
                    <span className={styles.label} style={{ color: accent ?? undefined }}>{label}</span>
                </div>
            ) : null}
            {title ? (
                <h3 className={styles.title} style={{ color: accent ?? undefined }}>{title}</h3>
            ) : null}
            {subtitle ? <p className={styles.subtitle}>{subtitle}</p> : null}
            {children}
        </section>
    )
}

interface CreamButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
    variant?: 'primary' | 'ghost'
    block?: boolean
    loading?: boolean
}

export function CreamButton(props: CreamButtonProps) {
    const { variant = 'primary', block, loading, className, children, disabled, ...rest } = props
    const look = variant === 'ghost' ? styles.btnGhost : ''
    return (
        <button
            type="button"
            className={`${styles.btn} ${look} ${block ? styles.btnBlock : ''} ${className ?? ''}`}
            disabled={disabled || loading}
            {...rest}
        >
            {loading ? '…' : children}
        </button>
    )
}

interface CreamStatBarProps {
    name: string
    value: number
    max: number
    color: string
}

export function CreamStatBar(props: CreamStatBarProps) {
    const { name, value, max, color } = props
    const percent = max > 0 ? Math.max(0, Math.min(100, Math.round((value / max) * 100))) : 0
    return (
        <div className={styles.statRow}>
            <span className={styles.statName}>{name}</span>
            <div
                className={styles.statTrack}
                role="progressbar"
                aria-label={name}
                aria-valuenow={percent}
                aria-valuemin={0}
                aria-valuemax={100}
            >
                <div className={styles.statFill} style={{ width: `${percent}%`, background: color }} />
            </div>
            <span className={styles.statValue}>{percent}%</span>
        </div>
    )
}

interface CreamChipProps {
    color: string
    children: ReactNode
}

export function CreamChip(props: CreamChipProps) {
    const { color, children } = props
    return (
        <span
            className={styles.chip}
            style={{ background: `${color}1F`, borderColor: `${color}66`, color }}
        >
            {children}
        </span>
    )
}

/** 法式花饰分隔线 */
export function CreamOrnament({ children }: { children: ReactNode }) {
    return <div className={styles.ornament}>{children}</div>
}

/** 立体舞台：透视画框 + 地台暖影，承载 Cocos 3D 宠物 */
export function CreamStage({ children, height }: { children: ReactNode; height?: number }) {
    return (
        <div className={styles.stage}>
            <div className={styles.stageFrame}>
                <div className={styles.stageInner} style={height ? { minHeight: height } : undefined}>
                    {children}
                    <div className={styles.pedestal} />
                </div>
            </div>
        </div>
    )
}

export const creamStyles = styles
