import { createContext, useContext } from 'react'
import { useThemeStore } from '@/store/theme'
import type { AppTheme } from '@/constants/theme'

const ThemeContext = createContext<AppTheme | null>(null)

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const mode = useThemeStore((s) => s.mode)
  const { themes } = require('@/constants/theme')
  const theme = themes[mode]

  return <ThemeContext.Provider value={theme}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const theme = useContext(ThemeContext)
  if (!theme) throw new Error('useTheme must be used within ThemeProvider')
  return theme
}
