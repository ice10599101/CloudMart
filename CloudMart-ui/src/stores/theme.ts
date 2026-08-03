import { create } from 'zustand'

export type ThemeMode = 'ocean' | 'sakura'

interface ThemeState {
  mode: ThemeMode
  setMode: (mode: ThemeMode) => void
  toggleMode: () => void
}

const STORAGE_KEY = 'theme_mode'

export const useThemeStore = create<ThemeState>((set, get) => ({
  mode: (localStorage.getItem(STORAGE_KEY) as ThemeMode) || 'ocean',

  setMode: (mode: ThemeMode) => {
    localStorage.setItem(STORAGE_KEY, mode)
    set({ mode })
  },

  toggleMode: () => {
    const next = get().mode === 'ocean' ? 'sakura' : 'ocean'
    localStorage.setItem(STORAGE_KEY, next)
    set({ mode: next })
  },
}))
