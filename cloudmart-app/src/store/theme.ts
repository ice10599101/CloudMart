import { create } from 'zustand'
import { storage } from '@/utils/storage'

type ThemeMode = 'ocean' | 'sakura'

interface ThemeState {
  mode: ThemeMode
  isDark: boolean
  toggleTheme: () => void
  setTheme: (mode: ThemeMode) => void
  hydrate: () => Promise<void>
}

export const useThemeStore = create<ThemeState>((set) => ({
  mode: 'ocean',
  isDark: true,

  hydrate: async () => {
    const saved = await storage.getItem('theme_mode')
    const mode = (saved as ThemeMode) || 'ocean'
    set({ mode, isDark: mode === 'ocean' })
  },

  toggleTheme: () =>
    set((state) => {
      const next = state.mode === 'ocean' ? 'sakura' : 'ocean'
      storage.setItem('theme_mode', next)
      return { mode: next, isDark: next === 'ocean' }
    }),

  setTheme: (mode: ThemeMode) => {
    storage.setItem('theme_mode', mode)
    set({ mode, isDark: mode === 'ocean' })
  },
}))
