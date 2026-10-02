import { OceanTheme } from './theme'

/**
 * Petit Crème 法式奶油风 · 宠物模块专属皮肤（方案 C）。
 *
 * 键与 theme.ts 的主题对象对齐（spread OceanTheme 后只覆写视觉值），
 * 供宠物相关页面把 `useTheme()` 的结果合并为本皮肤：
 * `const colors = { ...useTheme(), ...PetCreamTheme }`。
 * 其余页面不受影响（不做全局替换）。
 */
export const PetCreamTheme = {
  ...OceanTheme,
  isDark: false,

  // Primary：黄铜
  primary: '#C89B5A',
  primaryRgb: '200, 155, 90',
  primaryDark: '#A97C50',
  primaryGlow: 'rgba(200, 155, 90, 0.3)',

  // Backgrounds：奶油底色阶
  bgBase: '#F9EFE1',
  bgPage: '#FDF6EC',
  bgContainer: '#FFFDF8',
  bgElevated: '#FFFDF8',
  bgHeader: 'rgba(253, 246, 236, 0.92)',
  bgInput: 'rgba(255, 253, 248, 0.85)',

  // Text：咖啡色系
  text: '#4A3F35',
  textSecondary: '#7A6A5C',
  textTertiary: '#9C8D7E',

  // Border：黄铜描边
  border: 'rgba(200, 155, 90, 0.35)',

  // Accents：玫瑰/鼠尾草/雾蓝/焦糖
  accentGold: '#E0A45C',
  accentPurple: '#A8BDD0',
  accentGreen: '#9CAF88',
  accentRed: '#E8B4B8',
  accentOrange: '#E0A45C',

  // Gradients
  gradientPrimaryStart: '#C89B5A',
  gradientPrimaryEnd: '#E8B4B8',
  gradientHeroStart: '#FDF6EC',
  gradientHeroEnd: '#F2E3CE',

  // Glow
  glowPrimary: 'rgba(200, 155, 90, 0.22)',
  glowPurple: 'rgba(168, 189, 208, 0.22)',
}

// 补充语义 token（页面内原硬编码色的常量化收编）
export const PetCreamSemantic = {
  // 五维状态条（与 Web STAT_TONE / Taro PET_CREAM_STAT_TONE 同源）
  statHp: '#D98A8A',
  statHunger: '#E0A45C',
  statHappiness: '#E8A7AC',
  statEnergy: '#93AC7F',
  statClean: '#9CB6CC',
  danger: '#D98A8A',
  success: '#9CAF88',
  warning: '#E0A45C',
  genderFemale: '#E8B4B8',
  genderMale: '#A8BDD0',
  brassSoftBorder: 'rgba(200, 155, 90, 0.4)',
  brassSoftBg: 'rgba(200, 155, 90, 0.08)',
  meHighlight: 'rgba(224, 164, 92, 0.2)',
  unreadHighlight: 'rgba(224, 164, 92, 0.16)',
} as const
