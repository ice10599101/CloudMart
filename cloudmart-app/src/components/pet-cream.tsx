/**
 * Petit Crème · RN 组件库（法式奶油风，与 Web cream.module.css / Taro 版同源）。
 * 组件只负责设计语言；数据与行为由页面注入。尺寸 ≈ Web px × 1.1。
 */
import { useState } from 'react'
import { StyleSheet, View, Text, ScrollView, TouchableOpacity, Modal } from 'react-native'
import type { ReactNode } from 'react'
import { PetCreamTheme as T, PetCreamSemantic as S } from '@/constants/pet-cream'

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#FFFDF8',
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(200, 155, 90, 0.35)',
    padding: 16,
    shadowColor: '#4A3F35',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.08,
    shadowRadius: 12,
    elevation: 2,
    overflow: 'hidden',
  },
  cardHighlight: {
    position: 'absolute',
    top: 0,
    left: 8,
    right: 8,
    height: 1,
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
  },
  cardInnerLine: {
    position: 'absolute',
    top: 4,
    left: 4,
    right: 4,
    bottom: 4,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: 'rgba(231, 220, 203, 0.85)',
    pointerEvents: 'none',
  },
  cardArch: {
    borderTopLeftRadius: 60,
    borderTopRightRadius: 60,
    paddingTop: 24,
    paddingHorizontal: 22,
    paddingBottom: 14,
  },
  ribbon: { flexDirection: 'row', alignItems: 'center', marginBottom: 10, gap: 8 },
  ribbonLine: { flex: 1, height: 1, backgroundColor: 'rgba(200, 155, 90, 0.35)' },
  label: { fontSize: 11, letterSpacing: 1.5, color: '#A97C50' },
  title: { fontSize: 18, fontWeight: '600', color: '#4A3F35' },
  subtitle: { fontSize: 13, color: '#7A6A5C' },

  masthead: { alignItems: 'center', paddingVertical: 6 },
  mastheadTitle: { fontSize: 24, fontWeight: '600', letterSpacing: 1, color: '#4A3F35' },
  mastheadSub: { fontSize: 11, letterSpacing: 3, color: '#A97C50', marginTop: 4 },
  mastheadRule: {
    width: 180, height: 2, marginTop: 10,
    borderTopWidth: 1, borderTopColor: 'rgba(200, 155, 90, 0.55)',
    borderBottomWidth: 1, borderBottomColor: 'rgba(231, 220, 203, 0.9)',
  },

  btn: {
    minHeight: 40,
    paddingHorizontal: 18,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#C89B5A',
    backgroundColor: '#FFFDF8',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
    shadowColor: '#A97C50',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.45,
    shadowRadius: 0,
    elevation: 3,
    overflow: 'hidden',
  },
  btnHighlight: {
    position: 'absolute',
    top: 0,
    left: 10,
    right: 10,
    height: 1,
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
  },
  btnPressed: {
    transform: [{ translateY: 2 }],
    shadowOpacity: 0.2,
    elevation: 1,
  },
  btnGhost: { backgroundColor: 'transparent', shadowOpacity: 0, elevation: 0, borderColor: 'rgba(200, 155, 90, 0.55)' },
  btnText: { fontSize: 13, fontWeight: '500', color: '#4A3F35' },
  btnGhostText: { color: '#7A6A5C', fontWeight: '400' },

  chip: { paddingHorizontal: 10, paddingVertical: 3, borderRadius: 999, borderWidth: 1, fontSize: 11, overflow: 'hidden', alignSelf: 'flex-start' },

  statsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  statCell: { flex: 1, minWidth: 0, gap: 3 },
  statCellName: { fontSize: 11, color: '#7A6A5C' },
  statCellTrack: { height: 8, borderRadius: 999, backgroundColor: '#F2E3CE', overflow: 'hidden' },
  statCellValue: { fontSize: 11, color: '#4A3F35' },

  menuGroup: { marginBottom: 14 },
  menuGroupLabel: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 8 },
  menuGroupLine: { flex: 1, height: 1, backgroundColor: 'rgba(200, 155, 90, 0.3)' },
  menuGroupTitle: { fontSize: 11, letterSpacing: 1.5, color: '#A97C50' },
  menuGroupHint: { fontSize: 10, color: '#9C8D7E' },
  menuGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  menuItem: {
    width: 74,
    alignItems: 'center',
    gap: 4,
    paddingVertical: 12,
    paddingHorizontal: 4,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(200, 155, 90, 0.4)',
    backgroundColor: '#FFFDF8',
    shadowColor: '#A97C50',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.35,
    shadowRadius: 0,
    elevation: 2,
  },
  menuItemEmoji: { fontSize: 24 },
  menuItemLabel: { fontSize: 12, color: '#4A3F35' },

  overlay: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: '#FDF6EC', paddingTop: 12 } as const,
  panelHead: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingBottom: 10, borderBottomWidth: 1, borderBottomColor: '#E7DCCB',
  },
  panelTitle: { fontSize: 18, fontWeight: '600', color: '#4A3F35' },
  panelClose: { fontSize: 22, color: '#7A6A5C', paddingHorizontal: 8 },
  panelBody: { flex: 1, paddingHorizontal: 16, paddingTop: 12 },

  /* ---------- 任务进度条 / 奖励徽章 / 汇总行（页面级复用） ---------- */
  summaryRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  questProgressRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  questTrack: { flex: 1, height: 8, borderRadius: 999, backgroundColor: '#F2E3CE', overflow: 'hidden' },
  questFill: { height: '100%', borderRadius: 999, backgroundColor: '#C89B5A' },
  questProgressText: { fontSize: 10, color: '#9C8D7E' },
  rewardRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, alignItems: 'center' },
  rowActions: { flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', gap: 6, flexShrink: 0 },
  rewardChip: {
    paddingHorizontal: 10, paddingVertical: 2, borderRadius: 999,
    backgroundColor: 'rgba(224, 164, 92, 0.14)', color: '#A97C50', fontSize: 11,
    overflow: 'hidden', alignSelf: 'flex-start',
  },
  tile: {
    width: 104,
    alignItems: 'center',
    padding: 12,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(200, 155, 90, 0.4)',
    backgroundColor: '#FFFDF8',
    gap: 2,
  },
  bubbleMine: {
    alignSelf: 'flex-end',
    maxWidth: '85%',
    padding: 10,
    backgroundColor: '#C89B5A',
    borderTopLeftRadius: 14,
    borderTopRightRadius: 14,
    borderBottomRightRadius: 4,
    borderBottomLeftRadius: 14,
  },
  bubblePet: {
    alignSelf: 'flex-start',
    maxWidth: '85%',
    padding: 10,
    backgroundColor: '#FFFDF8',
    borderWidth: 1,
    borderColor: 'rgba(200, 155, 90, 0.4)',
    borderTopLeftRadius: 14,
    borderTopRightRadius: 14,
    borderBottomLeftRadius: 4,
    borderBottomRightRadius: 14,
  },
  bubbleMineText: { color: '#FFFDF8', fontSize: 13 },
  bubblePetText: { color: '#4A3F35', fontSize: 13 },
  questCard: {
    backgroundColor: '#FFFDF8',
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(200, 155, 90, 0.35)',
    padding: 12,
    gap: 6,
  },
})

/** 页面级可直接引用的奶油样式表（面板内部排版复用） */
export const petCreamStyles = styles

export interface CreamCardProps {
  variant?: 'arch' | 'menu'
  label?: string
  title?: string
  subtitle?: string
  accent?: string
  children?: ReactNode
  style?: object
}

export function CreamCard(props: CreamCardProps) {
  const { variant = 'menu', label, title, subtitle, accent, children, style } = props
  return (
    <View
      style={[
        styles.card,
        variant === 'arch' ? styles.cardArch : null,
        accent ? { borderColor: `${accent}66` } : null,
        style,
      ]}
    >
      <View style={styles.cardHighlight} pointerEvents="none" />
      {variant === 'menu' ? <View style={styles.cardInnerLine} pointerEvents="none" /> : null}
      {label ? (
        <View style={styles.ribbon}>
          <Text style={[styles.label, accent ? { color: accent } : null]}>{label}</Text>
          <View style={styles.ribbonLine} />
        </View>
      ) : null}
      {title ? <Text style={[styles.title, accent ? { color: accent } : null]}>{title}</Text> : null}
      {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      {children}
    </View>
  )
}

export interface CreamButtonProps {
  children: ReactNode
  variant?: 'primary' | 'ghost'
  disabled?: boolean
  style?: object
  onPress?: () => void
  onLongPress?: () => void
}

export function CreamButton(props: CreamButtonProps) {
  const { children, variant = 'primary', disabled, style, onPress, onLongPress } = props
  const [pressed, setPressed] = useState(false)
  return (
    <TouchableOpacity
      activeOpacity={1}
      disabled={disabled}
      style={[
        styles.btn,
        variant === 'ghost' ? styles.btnGhost : null,
        pressed && !disabled ? styles.btnPressed : null,
        disabled ? { opacity: 0.55 } : null,
        style,
      ]}
      onPress={disabled ? undefined : onPress}
      onLongPress={disabled ? undefined : onLongPress}
      onPressIn={disabled ? undefined : () => setPressed(true)}
      onPressOut={disabled ? undefined : () => setPressed(false)}
    >
      {variant !== 'ghost' ? <View style={styles.btnHighlight} pointerEvents="none" /> : null}
      <Text style={[styles.btnText, variant === 'ghost' ? styles.btnGhostText : null]}>{children}</Text>
    </TouchableOpacity>
  )
}

export function CreamChip({ color, children }: { color: string; children: ReactNode }) {
  return (
    <Text style={[styles.chip, { backgroundColor: `${color}1F`, borderColor: `${color}66`, color }]}>
      {children}
    </Text>
  )
}

export function CreamStatBar({ name, value, max, color }: { name: string; value: number; max: number; color: string }) {
  const percent = max > 0 ? Math.max(0, Math.min(100, Math.round((value / max) * 100))) : 0
  return (
    <View style={styles.statCell}>
      <Text style={styles.statCellName}>{name}</Text>
      <View style={styles.statCellTrack}>
        <View style={{ width: `${percent}%`, backgroundColor: color, borderRadius: 999 }} />
      </View>
      <Text style={styles.statCellValue}>{`${percent}%`}</Text>
    </View>
  )
}

export function CreamMasthead({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <View style={styles.masthead}>
      <Text style={styles.mastheadTitle}>{title}</Text>
      <Text style={styles.mastheadSub}>{subtitle}</Text>
      <View style={styles.mastheadRule} />
    </View>
  )
}

/** 分组功能宫格（对齐 Taro/Web） */
export function CreamMenuGrid({ groups, items, active, onSelect, badgeOf }: {
  groups: Array<{ label: string; hint: string; keys: string[] }>
  items: Record<string, { emoji: string; label: string }>
  active: string | null
  badgeOf?: (key: string) => number
  onSelect: (key: string) => void
}) {
  return (
    <View>
      {groups.map((group) => (
        <View key={group.label} style={styles.menuGroup}>
          <View style={styles.menuGroupLabel}>
            <Text style={styles.menuGroupTitle}>{`❀ ${group.label}`}</Text>
            <Text style={styles.menuGroupHint}>{group.hint}</Text>
            <View style={styles.menuGroupLine} />
          </View>
          <View style={styles.menuGrid}>
            {group.keys.map((key) => {
              const meta = items[key]
              if (!meta) return null
              const badge = badgeOf?.(key) ?? 0
              return (
                <TouchableOpacity key={key} activeOpacity={0.7} style={styles.menuItem} onPress={() => onSelect(key)}>
                  <Text style={styles.menuItemEmoji}>{meta.emoji}</Text>
                  <Text style={styles.menuItemLabel}>{badge > 0 ? `${meta.label}(${badge})` : meta.label}</Text>
                </TouchableOpacity>
              )
            })}
          </View>
        </View>
      ))}
    </View>
  )
}

/** 全屏奶油面板层（RN Modal：树内任意位置声明，原生层级呈现） */
export function CreamPanel({ title, visible, onClose, children }: {
  title: string
  visible: boolean
  onClose: () => void
  children: ReactNode
}) {
  return (
    <Modal visible={visible} animationType="slide" transparent={false} onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.panelHead}>
          <Text style={styles.panelTitle}>{`❀ ${title}`}</Text>
          <Text style={styles.panelClose} onPress={onClose}>×</Text>
        </View>
        <ScrollView style={styles.panelBody} contentContainerStyle={{ paddingBottom: 24 }} showsVerticalScrollIndicator={false}>
          {children}
        </ScrollView>
      </View>
    </Modal>
  )
}

export { S as PetCreamSemanticColors, T as PetCreamThemeColors }
