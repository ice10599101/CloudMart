/**
 * 心愿宇宙独立星空主题（APP 端）
 * 与主站主题隔离：深空底色 #1a1a2e / 辅色 #e94560 / 强调 #0f3460
 */

export const WishColors = {
  bgBase: '#1a1a2e',
  bgContainer: '#16213e',
  bgElevated: '#0f3460',
  primary: '#e94560',
  primaryDark: '#c81d3e',
  accentCyan: '#00d4ff',
  accentPurple: '#9370db',
  accentGold: '#ffd700',
  text: '#ffffff',
  textSecondary: 'rgba(255,255,255,0.65)',
  textTertiary: 'rgba(255,255,255,0.4)',
  border: 'rgba(255,255,255,0.08)',
} as const

export const FRUIT_LABELS: Record<string, string> = {
  GLOW: '微光',
  RESONANCE: '共鸣',
  BLOOM: '绽放',
  SPARK: '星火',
}

export const FRUIT_COLORS: Record<string, string> = {
  GLOW: '#00d4ff',
  RESONANCE: '#9370db',
  BLOOM: '#ff6b6b',
  SPARK: '#ffd700',
}

export const WISH_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '进行中',
  OVERDUE: '已过期',
  FULFILLING: '还愿中',
  FULFILLED: '已还愿',
  ARCHIVED: '已归档',
}

export function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}
