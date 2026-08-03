import Taro, { useDidShow } from '@tarojs/taro'
import { useThemeStore } from '@/store/theme'

type ThemeMode = 'ocean' | 'sakura'

const IS_WEAPP = Taro.getEnv() === Taro.ENV_TYPE.WEAPP

/**
 * Sakura theme CSS variables as inline style object for the root View element.
 * In WeChat mini-program, CSS variables defined via `[data-theme]` selector on a View
 * don't reliably inherit to children. Setting them as inline `style` on the root View
 * ensures proper cascading to all descendants.
 */
const SAKURA_STYLE: Record<string, string> = {
  '--color-primary': '#FF4D6A',
  '--color-primary-rgb': '255,77,106',
  '--color-primary-dark': '#E63950',
  '--color-primary-glow': 'rgba(255,77,106,0.35)',
  '--color-bg-base': '#FFF5F6',
  '--color-bg-container': 'rgba(255,255,255,0.85)',
  '--color-bg-elevated': 'rgba(255,255,255,0.95)',
  '--color-bg-header': 'rgba(255,245,246,0.88)',
  '--color-bg-input': 'rgba(255,240,242,0.6)',
  '--color-text': '#1D2129',
  '--color-text-secondary': '#4E5969',
  '--color-text-tertiary': '#86909C',
  '--color-border': 'rgba(0,0,0,0.08)',
  '--color-accent-gold': '#FFA500',
  '--color-accent-purple': '#9370DB',
  '--color-accent-green': '#00B42A',
  '--color-accent-red': '#F53F3F',
  '--color-accent-orange': '#FF7D00',
  '--color-gradient-primary': 'linear-gradient(135deg,#FF4D6A 0%,#FF8FA3 100%)',
  '--color-gradient-hero': 'linear-gradient(135deg,#FF4D6A 0%,#FF8FA3 50%,#FFB3C6 100%)',
  '--color-gradient-gold': 'linear-gradient(135deg,#FFD700 0%,#FFA500 100%)',
  '--color-glow-primary': 'rgba(255,77,106,0.25)',
  '--color-glow-purple': 'rgba(147,112,219,0.25)',
  '--color-glow-gold': 'rgba(255,215,0,0.25)',
  '--color-avatar-ring': 'linear-gradient(135deg,#FF4D6A,#FF8FA3,#FFD700)',
  '--shadow-card': '0 2px 12px rgba(0,0,0,0.08)',
  '--shadow-glow': '0 0 16px rgba(255,77,106,0.25),0 2px 12px rgba(0,0,0,0.08)',
}

/**
 * Returns theme props for the page root View.
 * Syncs Zustand store from Taro storage on each useDidShow,
 * so all components using useThemeStore on the same page stay in sync.
 *
 * Usage:
 *   const { dataTheme, themeStyle } = useThemeClass()
 *   <View data-theme={dataTheme} className={styles.page} style={{ ...themeStyle, paddingTop: '...' }}>
 */
export function useThemeClass() {
  const { mode, setTheme } = useThemeStore()

  useDidShow(() => {
    const latest = (Taro.getStorageSync('theme_mode') as ThemeMode) || 'ocean'
    if (latest !== mode) {
      setTheme(latest)
    }
  })

  const isSakura = mode === 'sakura'

  const themeStyle = IS_WEAPP && isSakura
    ? { ...SAKURA_STYLE, backgroundColor: '#FFF5F6' }
    : IS_WEAPP
      ? { backgroundColor: '#0B1220' }
      : {}

  return { dataTheme: mode, themeStyle }
}
