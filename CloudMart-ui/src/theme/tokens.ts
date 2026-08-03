import type { ThemeMode } from '@/stores/theme'

export interface ThemeTokens {
  colorPrimary: string
  colorPrimaryRgb: string
  colorPrimaryDark: string
  colorPrimaryGlow: string
  colorBgBase: string
  colorBgContainer: string
  colorBgElevated: string
  colorBgHeader: string
  colorBgFooter: string
  colorBgInput: string
  colorBorder: string
  colorText: string
  colorTextSecondary: string
  colorTextTertiary: string
  colorAccentGold: string
  colorAccentGoldDark: string
  colorAccentPurple: string
  colorAccentGreen: string
  colorAccentRed: string
  colorAccentOrange: string
  colorGradientPrimary: string
  colorGradientHero: string
  colorGradientGold: string
  colorGlowPrimary: string
  colorGlowPurple: string
  colorGlowGold: string
  colorAvatarRing1: string
  colorAvatarRing2: string
  colorAvatarRing3: string
  colorBadgeBg: string
  colorStatCardBg: string
  colorCardTopBar: string
  colorCardTopBarAlt: string
  colorCardTopBarPurple: string
  colorCardTopBarGold: string
  isDark: boolean
}

const OCEAN_THEME: ThemeTokens = {
  colorPrimary: '#00D4FF',
  colorPrimaryRgb: '0, 212, 255',
  colorPrimaryDark: '#0099CC',
  colorPrimaryGlow: 'rgba(0, 212, 255, 0.5)',
  colorBgBase: '#0B1220',
  colorBgContainer: '#152038',
  colorBgElevated: '#1A2845',
  colorBgHeader: 'rgba(11, 18, 32, 0.82)',
  colorBgFooter: '#111B2E',
  colorBgInput: '#0E1829',
  colorBorder: 'rgba(255, 255, 255, 0.08)',
  colorText: '#FFFFFF',
  colorTextSecondary: '#8B9DC3',
  colorTextTertiary: '#5A6F8E',
  colorAccentGold: '#FFD700',
  colorAccentGoldDark: '#FFA500',
  colorAccentPurple: '#9370DB',
  colorAccentGreen: '#32CD32',
  colorAccentRed: '#FF4757',
  colorAccentOrange: '#FF6B35',
  colorGradientPrimary: 'linear-gradient(135deg, #00D4FF, #0099CC)',
  colorGradientHero: 'linear-gradient(135deg, #0B1220 0%, #1A2744 40%, #0D1A2E 70%, #0B1220 100%)',
  colorGradientGold: 'linear-gradient(90deg, #FFD700, #FFA500, #FF6B35)',
  colorGlowPrimary: 'rgba(0, 212, 255, 0.08)',
  colorGlowPurple: 'rgba(147, 112, 219, 0.06)',
  colorGlowGold: 'rgba(255, 215, 0, 0.15)',
  colorAvatarRing1: '#00D4FF',
  colorAvatarRing2: '#9370DB',
  colorAvatarRing3: '#FFD700',
  colorBadgeBg: 'rgba(0, 212, 255, 0.12)',
  colorStatCardBg: 'rgba(0, 212, 255, 0.15)',
  colorCardTopBar: 'linear-gradient(90deg, #FFD700, #FFA500, #FF6B35)',
  colorCardTopBarAlt: 'linear-gradient(90deg, #00D4FF, #0099CC)',
  colorCardTopBarPurple: 'linear-gradient(90deg, #9370DB, #6A0DAD)',
  colorCardTopBarGold: 'linear-gradient(90deg, #FFD700, #FF6B35)',
  isDark: true,
}

const SAKURA_THEME: ThemeTokens = {
  colorPrimary: '#FF7EB3',
  colorPrimaryRgb: '255, 126, 179',
  colorPrimaryDark: '#E8609A',
  colorPrimaryGlow: 'rgba(255, 126, 179, 0.4)',
  colorBgBase: '#FFF5F8',
  colorBgContainer: '#FFFFFF',
  colorBgElevated: '#FFF0F5',
  colorBgHeader: 'rgba(255, 245, 248, 0.88)',
  colorBgFooter: '#FFF0F5',
  colorBgInput: '#FFF0F5',
  colorBorder: 'rgba(255, 126, 179, 0.15)',
  colorText: '#2D1B2E',
  colorTextSecondary: '#8B6B8E',
  colorTextTertiary: '#B89BB5',
  colorAccentGold: '#F5A623',
  colorAccentGoldDark: '#E09500',
  colorAccentPurple: '#B06AB3',
  colorAccentGreen: '#4CAF50',
  colorAccentRed: '#E8456B',
  colorAccentOrange: '#FF8A65',
  colorGradientPrimary: 'linear-gradient(135deg, #FF7EB3, #FF5A8A)',
  colorGradientHero: 'linear-gradient(135deg, #FFF5F8 0%, #FFE8F0 40%, #FFF0F5 70%, #FFF5F8 100%)',
  colorGradientGold: 'linear-gradient(90deg, #F5A623, #E09500, #FF8A65)',
  colorGlowPrimary: 'rgba(255, 126, 179, 0.1)',
  colorGlowPurple: 'rgba(176, 106, 179, 0.08)',
  colorGlowGold: 'rgba(245, 166, 35, 0.12)',
  colorAvatarRing1: '#FF7EB3',
  colorAvatarRing2: '#B06AB3',
  colorAvatarRing3: '#F5A623',
  colorBadgeBg: 'rgba(255, 126, 179, 0.12)',
  colorStatCardBg: 'rgba(255, 126, 179, 0.1)',
  colorCardTopBar: 'linear-gradient(90deg, #F5A623, #E09500, #FF8A65)',
  colorCardTopBarAlt: 'linear-gradient(90deg, #FF7EB3, #FF5A8A)',
  colorCardTopBarPurple: 'linear-gradient(90deg, #B06AB3, #8E24AA)',
  colorCardTopBarGold: 'linear-gradient(90deg, #F5A623, #FF8A65)',
  isDark: false,
}

const THEMES: Record<ThemeMode, ThemeTokens> = {
  ocean: OCEAN_THEME,
  sakura: SAKURA_THEME,
}

export function getThemeTokens(mode: ThemeMode): ThemeTokens {
  return THEMES[mode]
}

export function applyCssVariables(tokens: ThemeTokens): void {
  const root = document.documentElement
  root.style.setProperty('--color-primary', tokens.colorPrimary)
  root.style.setProperty('--color-primary-rgb', tokens.colorPrimaryRgb)
  root.style.setProperty('--color-primary-dark', tokens.colorPrimaryDark)
  root.style.setProperty('--color-primary-glow', tokens.colorPrimaryGlow)
  root.style.setProperty('--color-bg-base', tokens.colorBgBase)
  root.style.setProperty('--color-bg-container', tokens.colorBgContainer)
  root.style.setProperty('--color-bg-elevated', tokens.colorBgElevated)
  root.style.setProperty('--color-bg-header', tokens.colorBgHeader)
  root.style.setProperty('--color-bg-footer', tokens.colorBgFooter)
  root.style.setProperty('--color-bg-input', tokens.colorBgInput)
  root.style.setProperty('--color-border', tokens.colorBorder)
  root.style.setProperty('--color-text', tokens.colorText)
  root.style.setProperty('--color-text-secondary', tokens.colorTextSecondary)
  root.style.setProperty('--color-text-tertiary', tokens.colorTextTertiary)
  root.style.setProperty('--color-accent-gold', tokens.colorAccentGold)
  root.style.setProperty('--color-accent-gold-dark', tokens.colorAccentGoldDark)
  root.style.setProperty('--color-accent-purple', tokens.colorAccentPurple)
  root.style.setProperty('--color-accent-green', tokens.colorAccentGreen)
  root.style.setProperty('--color-accent-red', tokens.colorAccentRed)
  root.style.setProperty('--color-accent-orange', tokens.colorAccentOrange)
  root.style.setProperty('--color-gradient-primary', tokens.colorGradientPrimary)
  root.style.setProperty('--color-gradient-hero', tokens.colorGradientHero)
  root.style.setProperty('--color-gradient-gold', tokens.colorGradientGold)
  root.style.setProperty('--color-glow-primary', tokens.colorGlowPrimary)
  root.style.setProperty('--color-glow-purple', tokens.colorGlowPurple)
  root.style.setProperty('--color-glow-gold', tokens.colorGlowGold)
  root.style.setProperty('--color-avatar-ring1', tokens.colorAvatarRing1)
  root.style.setProperty('--color-avatar-ring2', tokens.colorAvatarRing2)
  root.style.setProperty('--color-avatar-ring3', tokens.colorAvatarRing3)
  root.style.setProperty('--color-badge-bg', tokens.colorBadgeBg)
  root.style.setProperty('--color-stat-card-bg', tokens.colorStatCardBg)
  root.style.setProperty('--color-card-top-bar', tokens.colorCardTopBar)
  root.style.setProperty('--color-card-top-bar-alt', tokens.colorCardTopBarAlt)
  root.style.setProperty('--color-card-top-bar-purple', tokens.colorCardTopBarPurple)
  root.style.setProperty('--color-card-top-bar-gold', tokens.colorCardTopBarGold)
}
