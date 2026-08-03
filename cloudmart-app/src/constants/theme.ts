import { Platform } from 'react-native'

export type ThemeMode = 'ocean' | 'sakura'

// Ocean Theme (Default - Dark Blue)
export const OceanTheme = {
  mode: 'ocean' as ThemeMode,
  isDark: true,

  // Primary
  primary: '#00D4FF',
  primaryRgb: '0, 212, 255',
  primaryDark: '#0099CC',
  primaryGlow: 'rgba(0, 212, 255, 0.4)',

  // Backgrounds
  bgBase: '#0B1220',
  bgPage: '#0B1220',
  bgContainer: 'rgba(20, 35, 60, 0.85)',
  bgElevated: 'rgba(30, 50, 80, 0.9)',
  bgHeader: 'rgba(11, 18, 32, 0.88)',
  bgInput: 'rgba(20, 35, 60, 0.6)',

  // Text
  text: '#FFFFFF',
  textSecondary: 'rgba(255, 255, 255, 0.7)',
  textTertiary: 'rgba(255, 255, 255, 0.45)',

  // Border
  border: 'rgba(255, 255, 255, 0.1)',

  // Accents
  accentGold: '#FFD700',
  accentPurple: '#9370DB',
  accentGreen: '#32CD32',
  accentRed: '#FF4757',
  accentOrange: '#FFA500',

  // Gradients (for StyleSheet, use start/end colors)
  gradientPrimaryStart: '#00D4FF',
  gradientPrimaryEnd: '#9370DB',
  gradientHeroStart: '#0A1628',
  gradientHeroEnd: '#132D52',

  // Glow
  glowPrimary: 'rgba(0, 212, 255, 0.3)',
  glowPurple: 'rgba(147, 112, 219, 0.3)',

  // Shadows
  shadowCard: {
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 24,
    elevation: 8,
  },
  shadowGlow: {
    shadowColor: '#00D4FF',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.5,
    shadowRadius: 20,
    elevation: 10,
  },

  // Tab bar
  tabBarBg: 'rgba(11, 18, 32, 0.95)',
  tabBarActive: '#00D4FF',
  tabBarInactive: 'rgba(255, 255, 255, 0.45)',
}

// Sakura Theme (Light Pink)
export const SakuraTheme = {
  mode: 'sakura' as ThemeMode,
  isDark: false,

  primary: '#FF4D6A',
  primaryRgb: '255, 77, 106',
  primaryDark: '#E63950',
  primaryGlow: 'rgba(255, 77, 106, 0.35)',

  bgBase: '#FFF5F6',
  bgPage: '#FFF5F6',
  bgContainer: 'rgba(255, 255, 255, 0.85)',
  bgElevated: 'rgba(255, 255, 255, 0.95)',
  bgHeader: 'rgba(255, 245, 246, 0.88)',
  bgInput: 'rgba(255, 240, 242, 0.6)',

  text: '#1D2129',
  textSecondary: '#4E5969',
  textTertiary: '#86909C',

  border: 'rgba(0, 0, 0, 0.08)',

  accentGold: '#FFA500',
  accentPurple: '#9370DB',
  accentGreen: '#00B42A',
  accentRed: '#F53F3F',
  accentOrange: '#FF7D00',

  gradientPrimaryStart: '#FF4D6A',
  gradientPrimaryEnd: '#FF8FA3',
  gradientHeroStart: '#FF4D6A',
  gradientHeroEnd: '#FFB3C6',

  glowPrimary: 'rgba(255, 77, 106, 0.25)',
  glowPurple: 'rgba(147, 112, 219, 0.25)',

  shadowCard: {
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 12,
    elevation: 4,
  },
  shadowGlow: {
    shadowColor: '#FF4D6A',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 16,
    elevation: 6,
  },

  tabBarBg: 'rgba(255, 245, 246, 0.95)',
  tabBarActive: '#FF4D6A',
  tabBarInactive: '#86909C',
}

export type AppTheme = typeof OceanTheme

export const themes: Record<ThemeMode, AppTheme> = {
  ocean: OceanTheme,
  sakura: SakuraTheme,
}

export const Spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 24,
  xxxl: 32,
} as const

export const BorderRadius = {
  xs: 4,
  sm: 6,
  md: 10,
  lg: 14,
  xl: 20,
  full: 9999,
} as const

export const FontSize = {
  xs: 10,
  sm: 12,
  md: 14,
  lg: 16,
  xl: 18,
  xxl: 22,
  xxxl: 28,
  hero: 36,
} as const

export const BottomTabInset = Platform.select({ ios: 50, android: 80 }) ?? 0
export const MaxContentWidth = 800
