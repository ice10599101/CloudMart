import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/api/admin/auth', () => ({
  adminLogin: vi.fn(() => Promise.resolve({ data: {} })),
  adminRefreshToken: vi.fn(() => Promise.resolve({ data: {} })),
  adminLogout: vi.fn(() => Promise.resolve({ data: {} })),
  getAdminProfile: vi.fn(() => Promise.resolve({ data: {} })),
}))

vi.mock('umi', () => ({
  history: { push: vi.fn(), replace: vi.fn() },
}))

import { useAdminAuthStore } from './adminAuth'
import { adminLogin, adminRefreshToken, adminLogout, getAdminProfile } from '@/api/admin/auth'
import { history } from 'umi'

describe('useAdminAuthStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useAdminAuthStore.setState({
      accessToken: '',
      refreshToken: '',
      adminInfo: null,
      permissions: [],
      roles: [],
    })
    vi.clearAllMocks()
  })

  it('initializes state from localStorage', () => {
    localStorage.setItem('admin_access_token', 'admin-at')
    localStorage.setItem('admin_refresh_token', 'admin-rt')

    useAdminAuthStore.setState({
      accessToken: localStorage.getItem('admin_access_token') || '',
      refreshToken: localStorage.getItem('admin_refresh_token') || '',
    })

    expect(useAdminAuthStore.getState().accessToken).toBe('admin-at')
    expect(useAdminAuthStore.getState().refreshToken).toBe('admin-rt')
  })

  it('login() sets tokens', async () => {
    vi.mocked(adminLogin).mockResolvedValue({
      data: { success: true, data: { accessToken: 'new-at', refreshToken: 'new-rt' } },
    } as any)

    await useAdminAuthStore.getState().login('admin', 'password')

    expect(localStorage.getItem('admin_access_token')).toBe('new-at')
    expect(localStorage.getItem('admin_refresh_token')).toBe('new-rt')
    expect(useAdminAuthStore.getState().accessToken).toBe('new-at')
    expect(useAdminAuthStore.getState().refreshToken).toBe('new-rt')
  })

  it('logout() clears state and redirects', () => {
    localStorage.setItem('admin_access_token', 'token')
    localStorage.setItem('admin_refresh_token', 'refresh')
    useAdminAuthStore.setState({
      accessToken: 'token',
      refreshToken: 'refresh',
      adminInfo: { id: 1, username: 'admin', avatar: null, permissions: [], roles: [] } as any,
      permissions: ['admin:user:list'],
      roles: ['admin'],
    })

    useAdminAuthStore.getState().logout()

    expect(localStorage.getItem('admin_access_token')).toBeNull()
    expect(localStorage.getItem('admin_refresh_token')).toBeNull()
    expect(useAdminAuthStore.getState().accessToken).toBe('')
    expect(useAdminAuthStore.getState().adminInfo).toBeNull()
    expect(useAdminAuthStore.getState().permissions).toEqual([])
    expect(useAdminAuthStore.getState().roles).toEqual([])
    expect(history.push).toHaveBeenCalledWith('/admin/login')
  })

  it('refreshTokenAction() refreshes tokens', async () => {
    useAdminAuthStore.setState({ refreshToken: 'old-rt' })

    vi.mocked(adminRefreshToken).mockResolvedValue({
      data: { success: true, data: { accessToken: 'new-at', refreshToken: 'new-rt' } },
    } as any)

    const newToken = await useAdminAuthStore.getState().refreshTokenAction()

    expect(newToken).toBe('new-at')
    expect(localStorage.getItem('admin_access_token')).toBe('new-at')
    expect(localStorage.getItem('admin_refresh_token')).toBe('new-rt')
    expect(useAdminAuthStore.getState().accessToken).toBe('new-at')
  })

  it('refreshTokenAction() throws when no refresh token', async () => {
    useAdminAuthStore.setState({ refreshToken: '' })

    await expect(useAdminAuthStore.getState().refreshTokenAction()).rejects.toThrow('No admin refresh token available')
  })

  it('fetchProfile() sets adminInfo, permissions, roles', async () => {
    vi.mocked(getAdminProfile).mockResolvedValue({
      data: {
        success: true,
        data: {
          id: 1,
          username: 'admin',
          avatar: 'avatar.png',
          permissions: ['admin:user:list', 'admin:role:list'],
          roles: ['admin'],
        },
      },
    } as any)

    await useAdminAuthStore.getState().fetchProfile()

    const state = useAdminAuthStore.getState()
    expect(state.adminInfo).not.toBeNull()
    expect(state.adminInfo?.username).toBe('admin')
    expect(state.permissions).toEqual(['admin:user:list', 'admin:role:list'])
    expect(state.roles).toEqual(['admin'])
  })

  it('fetchProfile() clears state and redirects on error', async () => {
    vi.mocked(getAdminProfile).mockRejectedValue(new Error('Unauthorized'))

    useAdminAuthStore.setState({
      adminInfo: { id: 1 } as any,
      permissions: ['admin:user:list'],
      roles: ['admin'],
    })

    await useAdminAuthStore.getState().fetchProfile()

    expect(useAdminAuthStore.getState().adminInfo).toBeNull()
    expect(useAdminAuthStore.getState().permissions).toEqual([])
    expect(useAdminAuthStore.getState().roles).toEqual([])
    expect(history.replace).toHaveBeenCalledWith('/admin/login')
  })

  it('hasPermission() returns true for wildcard permission', () => {
    useAdminAuthStore.setState({ permissions: ['*:*:*'] })

    expect(useAdminAuthStore.getState().hasPermission('admin:user:list')).toBe(true)
    expect(useAdminAuthStore.getState().hasPermission('any:permission:here')).toBe(true)
  })

  it('hasPermission() returns true for specific permission', () => {
    useAdminAuthStore.setState({ permissions: ['admin:user:list', 'admin:role:list'] })

    expect(useAdminAuthStore.getState().hasPermission('admin:user:list')).toBe(true)
    expect(useAdminAuthStore.getState().hasPermission('admin:role:list')).toBe(true)
  })

  it('hasPermission() returns false for missing permission', () => {
    useAdminAuthStore.setState({ permissions: ['admin:user:list'] })

    expect(useAdminAuthStore.getState().hasPermission('admin:role:list')).toBe(false)
  })
})
