import { createStore } from 'zustand'
import { useSyncExternalStore } from 'react'
import Taro from '@tarojs/taro'

type ThemeMode = 'ocean' | 'sakura'

interface ThemeState {
  mode: ThemeMode
  isDark: boolean
  toggleTheme: () => void
  setTheme: (mode: ThemeMode) => void
}

const themeStore = createStore<ThemeState>((set) => {
  const saved = (Taro.getStorageSync('theme_mode') as ThemeMode) || 'ocean'
  return {
    mode: saved,
    isDark: saved === 'ocean',
    toggleTheme: () =>
      set((state) => {
        const next = state.mode === 'ocean' ? 'sakura' : 'ocean'
        Taro.setStorageSync('theme_mode', next)
        return { mode: next, isDark: next === 'ocean' }
      }),
    setTheme: (mode: ThemeMode) => {
      Taro.setStorageSync('theme_mode', mode)
      set({ mode, isDark: mode === 'ocean' })
    },
  }
})

export function useThemeStore(): ThemeState {
  return useSyncExternalStore(themeStore.subscribe, themeStore.getState)
}
