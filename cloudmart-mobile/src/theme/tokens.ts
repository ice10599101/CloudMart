export interface ThemeTokens {
  colorPrimary: string
  colorPrimaryRgb: string
  colorPrimaryDark: string
  colorPrimaryGlow: string
  colorBgBase: string
  colorBgContainer: string
  colorBgElevated: string
  colorBgHeader: string
  colorBgInput: string
  colorText: string
  colorTextSecondary: string
  colorTextTertiary: string
  colorBorder: string
  colorAccentGold: string
  colorAccentGoldDark: string
  colorAccentPurple: string
  colorAccentGreen: string
  colorAccentRed: string
  colorAccentOrange: string
  colorGradientPrimary: string
  colorGradientGold: string
  colorGlowPrimary: string
  colorGlowPurple: string
  colorGlowGold: string
  colorAvatarRing: string
  shadowCard: string
  shadowGlow: string
}

export const OCEAN_THEME: ThemeTokens = {
  colorPrimary: '#00D4FF',
  colorPrimaryRgb: '0, 212, 255',
  colorPrimaryDark: '#0099CC',
  colorPrimaryGlow: 'rgba(0, 212, 255, 0.5)',
  colorBgBase: '#0B1220',
  colorBgContainer: '#152038',
  colorBgElevated: '#1A2845',
  colorBgHeader: 'rgba(11, 18, 32, 0.82)',
  colorBgInput: '#0E1829',
  colorText: '#FFFFFF',
  colorTextSecondary: '#8B9DC3',
  colorTextTertiary: '#5A6F8E',
  colorBorder: 'rgba(255, 255, 255, 0.08)',
  colorAccentGold: '#FFD700',
  colorAccentGoldDark: '#FFA500',
  colorAccentPurple: '#9370DB',
  colorAccentGreen: '#32CD32',
  colorAccentRed: '#FF4757',
  colorAccentOrange: '#FF6B35',
  colorGradientPrimary: 'linear-gradient(135deg, #00D4FF, #0099CC)',
  colorGradientGold: 'linear-gradient(90deg, #FFD700, #FFA500, #FF6B35)',
  colorGlowPrimary: 'rgba(0, 212, 255, 0.08)',
  colorGlowPurple: 'rgba(147, 112, 219, 0.06)',
  colorGlowGold: 'rgba(255, 215, 0, 0.15)',
  colorAvatarRing: 'conic-gradient(#00D4FF, #9370DB, #FFD700, #00D4FF)',
  shadowCard: '0 4px 24px rgba(0, 0, 0, 0.3)',
  shadowGlow: '0 0 30px rgba(0, 212, 255, 0.2)',
}

export const SAKURA_THEME: ThemeTokens = {
  colorPrimary: '#FF7EB3',
  colorPrimaryRgb: '255, 126, 179',
  colorPrimaryDark: '#E8609A',
  colorPrimaryGlow: 'rgba(255, 126, 179, 0.4)',
  colorBgBase: '#FFF5F8',
  colorBgContainer: '#FFFFFF',
  colorBgElevated: '#FFF0F5',
  colorBgHeader: 'rgba(255, 245, 248, 0.88)',
  colorBgInput: '#FFF0F5',
  colorText: '#2D1B2E',
  colorTextSecondary: '#8B6B8E',
  colorTextTertiary: '#B89BB5',
  colorBorder: 'rgba(255, 126, 179, 0.15)',
  colorAccentGold: '#F5A623',
  colorAccentGoldDark: '#E09500',
  colorAccentPurple: '#B06AB3',
  colorAccentGreen: '#4CAF50',
  colorAccentRed: '#E8456B',
  colorAccentOrange: '#FF8A65',
  colorGradientPrimary: 'linear-gradient(135deg, #FF7EB3, #FF5A8A)',
  colorGradientGold: 'linear-gradient(90deg, #F5A623, #E09500, #FF8A65)',
  colorGlowPrimary: 'rgba(255, 126, 179, 0.1)',
  colorGlowPurple: 'rgba(176, 106, 179, 0.08)',
  colorGlowGold: 'rgba(245, 166, 35, 0.12)',
  colorAvatarRing: 'conic-gradient(#FF7EB3, #B06AB3, #F5A623, #FF7EB3)',
  shadowCard: '0 4px 16px rgba(0, 0, 0, 0.08)',
  shadowGlow: '0 0 20px rgba(255, 126, 179, 0.15)',
}

export function getTokens(mode: 'ocean' | 'sakura'): ThemeTokens {
  return mode === 'ocean' ? OCEAN_THEME : SAKURA_THEME
}
