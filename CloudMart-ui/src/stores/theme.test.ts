import { describe, it, expect, vi, beforeEach } from 'vitest'

import { useThemeStore, type ThemeMode } from './theme'

describe('useThemeStore', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('initializes with ocean theme by default', () => {
    useThemeStore.setState({ mode: (localStorage.getItem('theme_mode') as ThemeMode) || 'ocean' })

    expect(useThemeStore.getState().mode).toBe('ocean')
  })

  it('initializes from localStorage', () => {
    localStorage.setItem('theme_mode', 'sakura')

    useThemeStore.setState({ mode: (localStorage.getItem('theme_mode') as ThemeMode) || 'ocean' })

    expect(useThemeStore.getState().mode).toBe('sakura')
  })

  it('setMode() updates mode and persists to localStorage', () => {
    useThemeStore.getState().setMode('sakura')

    expect(useThemeStore.getState().mode).toBe('sakura')
    expect(localStorage.getItem('theme_mode')).toBe('sakura')
  })

  it('setMode() can switch back to ocean', () => {
    useThemeStore.getState().setMode('sakura')
    useThemeStore.getState().setMode('ocean')

    expect(useThemeStore.getState().mode).toBe('ocean')
    expect(localStorage.getItem('theme_mode')).toBe('ocean')
  })

  it('toggleMode() switches from ocean to sakura', () => {
    useThemeStore.setState({ mode: 'ocean' })

    useThemeStore.getState().toggleMode()

    expect(useThemeStore.getState().mode).toBe('sakura')
    expect(localStorage.getItem('theme_mode')).toBe('sakura')
  })

  it('toggleMode() switches from sakura to ocean', () => {
    useThemeStore.setState({ mode: 'sakura' })

    useThemeStore.getState().toggleMode()

    expect(useThemeStore.getState().mode).toBe('ocean')
    expect(localStorage.getItem('theme_mode')).toBe('ocean')
  })

  it('toggleMode() persists to localStorage', () => {
    useThemeStore.setState({ mode: 'ocean' })
    useThemeStore.getState().toggleMode()

    expect(localStorage.getItem('theme_mode')).toBe('sakura')
  })
})
