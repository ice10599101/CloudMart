/**
 * Petit Crème · Taro 组件库（法式奶油风，与 Web cream.module.css / Cream.tsx 同源）。
 *
 * 组件只负责"设计语言"：拱形卡/双线卡、挤出按钮、玻璃内凹状态条、眉标花饰、
 * 视口层弹层。数据与行为全部由页面注入。
 */
import { View, Text, ScrollView } from '@tarojs/components'
import type { CommonEvent } from '@tarojs/components'
import type { CSSProperties, ReactNode } from 'react'
import styles from './pet-cream.module.scss'

export interface CreamCardProps {
  /** arch 拱形主视觉 / menu 双线餐牌 / plain 光面 */
  variant?: 'arch' | 'menu' | 'plain'
  /** 眉标（法式小标签，如 MON PETIT） */
  label?: string
  title?: string
  subtitle?: string
  /** 强调色（果色），作用于描边与标题 */
  accent?: string
  children?: ReactNode
  className?: string
  style?: CSSProperties
}

export function CreamCard(props: CreamCardProps) {
  const { variant = 'menu', label, title, subtitle, accent, children, className, style } = props
  const shape = variant === 'arch' ? styles.cardArch : variant === 'menu' ? styles.cardMenu : ''
  return (
    <View className={`${styles.card} ${shape} ${className ?? ''}`} style={accent ? { ...style, borderColor: `${accent}66` } : style}>
      {label ? (
        <View className={styles.ribbon}>
          <Text className={styles.label} style={accent ? { color: accent } : undefined}>{label}</Text>
        </View>
      ) : null}
      {title ? <Text className={styles.title} style={accent ? { color: accent } : undefined}>{title}</Text> : null}
      {subtitle ? <Text className={styles.subtitle}>{subtitle}</Text> : null}
      {children}
    </View>
  )
}

export interface CreamButtonProps {
  children: ReactNode
  variant?: 'primary' | 'ghost'
  block?: boolean
  disabled?: boolean
  loading?: boolean
  style?: CSSProperties
  onClick?: (event: CommonEvent) => void
  onLongPress?: (event: CommonEvent) => void
}

export function CreamButton(props: CreamButtonProps) {
  const { children, variant = 'primary', block, disabled, loading, style, onClick, onLongPress } = props
  return (
    <View
      className={`${styles.btn} ${variant === 'ghost' ? styles.btnGhost : ''} ${block ? styles.btnBlock : ''}`}
      style={{ ...style, ...(disabled || loading ? { opacity: 0.55 } : null) }}
      onClick={disabled || loading ? undefined : onClick}
      onLongPress={disabled || loading ? undefined : onLongPress}
    >
      <Text>{loading ? '…' : children}</Text>
    </View>
  )
}

export interface CreamChipProps {
  color: string
  children: ReactNode
}

export function CreamChip(props: CreamChipProps) {
  const { color, children } = props
  return (
    <Text className={styles.chip} style={{ background: `${color}1F`, borderColor: `${color}66`, color }}>
      {children}
    </Text>
  )
}

export interface CreamStatBarProps {
  name: string
  value: number
  max: number
  color: string
  /** row 整行条 / cell 紧凑小格 */
  variant?: 'row' | 'cell'
}

export function CreamStatBar(props: CreamStatBarProps) {
  const { name, value, max, color, variant = 'row' } = props
  const percent = max > 0 ? Math.max(0, Math.min(100, Math.round((value / max) * 100))) : 0
  const fill = (
    <View className={styles.statFill} style={{ width: `${percent}%`, background: color }} />
  )
  if (variant === 'cell') {
    return (
      <View className={styles.statCell}>
        <Text className={styles.statCellName}>{name}</Text>
        <View className={styles.statCellTrack}>{fill}</View>
        <Text className={styles.statCellValue}>{percent}%</Text>
      </View>
    )
  }
  return (
    <View className={styles.statRow}>
      <Text className={styles.statName}>{name}</Text>
      <View className={styles.statTrack}>{fill}</View>
      <Text className={styles.statCellValue}>{percent}%</Text>
    </View>
  )
}

/** 法式花饰分隔线 */
export function CreamOrnament({ children }: { children: ReactNode }) {
  return <View className={styles.ornament}><Text>{children}</Text></View>
}

/** 招牌页头 */
export function CreamMasthead({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <View className={styles.masthead}>
      <Text className={styles.mastheadTitle}>{title}</Text>
      <Text className={styles.mastheadSub}>{subtitle}</Text>
      <View className={styles.mastheadRule} />
    </View>
  )
}

/**
 * 自绘开关：尺寸/颜色完全由皮肤控制（Taro H5 的 Switch 不吃 color prop 且默认过大）。
 */
export function CreamToggle({ on, disabled, onChange }: {
  on: boolean
  disabled?: boolean
  onChange?: (next: boolean) => void
}) {
  return (
    <View
      className={`${styles.toggle} ${on ? styles.toggleOn : ''}`}
      style={disabled ? { opacity: 0.5 } : undefined}
      onClick={disabled ? undefined : () => onChange?.(!on)}
    >
      <View className={styles.toggleKnob} />
    </View>
  )
}

/**
 * 视口层弹层：fixed 覆盖 + 内部 ScrollView 滚动。
 * H5 与微信小程序均可用（fixed 在小程序页面内相对视口定位）。
 */
export function CreamSheet({ title, onClose, children }: {
  title: string
  onClose: () => void
  children: ReactNode
}) {
  return (
    <View className={styles.sheetOverlay} onClick={onClose}>
      <View
        className={styles.sheet}
        onClick={(event: CommonEvent) => event.stopPropagation()}
      >
        <View className={styles.sheetHead}>
          <Text className={styles.sheetTitle}>{title}</Text>
          <Text className={styles.sheetClose} onClick={onClose}>×</Text>
        </View>
        <ScrollView scrollY className={styles.sheetBody} enhanced showScrollbar={false}>
          {children}
        </ScrollView>
      </View>
    </View>
  )
}
