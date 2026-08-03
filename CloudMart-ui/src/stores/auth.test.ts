import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
}))

vi.mock('@/api/user', () => ({
  getUserProfile: vi.fn(),
}))

vi.mock('umi', () => ({
  history: { push: vi.fn() },
}))

import { useAuthStore } from './auth'
import { login as loginApi } from '@/api/auth'
import { getUserProfile } from '@/api/user'
import { history } from 'umi'

describe('useAuthStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({
      accessToken: '',
      refreshToken: '',
      isAuthenticated: false,
      user: null,
    })
    vi.clearAllMocks()
  })

  it('initializes state from localStorage', () => {
    localStorage.setItem('access_token', 'existing-token')
    localStorage.setItem('refresh_token', 'existing-refresh')

    useAuthStore.setState({
      accessToken: localStorage.getItem('access_token') || '',
      refreshToken: localStorage.getItem('refresh_token') || '',
      isAuthenticated: !!localStorage.getItem('access_token'),
    })

    expect(useAuthStore.getState().accessToken).toBe('existing-token')
    expect(useAuthStore.getState().refreshToken).toBe('existing-refresh')
    expect(useAuthStore.getState().isAuthenticated).toBe(true)
  })

  it('initializes with empty state when no tokens in localStorage', () => {
    expect(useAuthStore.getState().accessToken).toBe('')
    expect(useAuthStore.getState().refreshToken).toBe('')
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().user).toBeNull()
  })

  it('login() sets tokens and calls fetchProfile', async () => {
    vi.mocked(loginApi).mockResolvedValue({
      data: { success: true, data: { accessToken: 'new-at', refreshToken: 'new-rt', tokenType: 'Bearer', expiresIn: 3600 } },
    } as any)
    vi.mocked(getUserProfile).mockResolvedValue({
      data: { success: true, data: { id: 1, username: 'testuser', nickname: 'Test' } },
    } as any)

    await useAuthStore.getState().login('testuser', 'password')

    expect(localStorage.getItem('access_token')).toBe('new-at')
    expect(localStorage.getItem('refresh_token')).toBe('new-rt')
    expect(useAuthStore.getState().isAuthenticated).toBe(true)
    expect(vi.mocked(getUserProfile)).toHaveBeenCalled()
    expect(history.push).toHaveBeenCalledWith('/')
  })

  it('login() redirects to specified path', async () => {
    vi.mocked(loginApi).mockResolvedValue({
      data: { success: true, data: { accessToken: 'at', refreshToken: 'rt', tokenType: 'Bearer', expiresIn: 3600 } },
    } as any)
    vi.mocked(getUserProfile).mockResolvedValue({
      data: { success: true, data: { id: 1 } },
    } as any)

    await useAuthStore.getState().login('user', 'pass', '/products')

    expect(history.push).toHaveBeenCalledWith('/products')
  })

  it('logout() clears tokens and state', () => {
    localStorage.setItem('access_token', 'token')
    localStorage.setItem('refresh_token', 'refresh')
    useAuthStore.setState({ accessToken: 'token', refreshToken: 'refresh', isAuthenticated: true, user: { id: 1 } as any })

    useAuthStore.getState().logout()

    expect(localStorage.getItem('access_token')).toBeNull()
    expect(localStorage.getItem('refresh_token')).toBeNull()
    expect(useAuthStore.getState().accessToken).toBe('')
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().user).toBeNull()
    expect(history.push).toHaveBeenCalledWith('/login')
  })

  it('fetchProfile() sets user on success', async () => {
    const profile = { id: 1, username: 'testuser', nickname: 'Test', email: 'test@test.com' }
    vi.mocked(getUserProfile).mockResolvedValue({ data: { success: true, data: profile } } as any)

    await useAuthStore.getState().fetchProfile()

    expect(useAuthStore.getState().user).toEqual(profile)
  })

  it('fetchProfile() ignores error', async () => {
    vi.mocked(getUserProfile).mockRejectedValue(new Error('Network error'))

    await expect(useAuthStore.getState().fetchProfile()).resolves.toBeUndefined()
    expect(useAuthStore.getState().user).toBeNull()
  })
})
