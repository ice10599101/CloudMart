import { create } from 'zustand'
import { adminLogin, adminRefreshToken, adminLogout, getAdminProfile } from '@/api/admin/auth'
import type { AdminInfo } from '@/types'
import { history } from 'umi'
import { useAdminMenuStore } from '@/stores/adminMenu'

const STORAGE_KEYS = {
  accessToken: 'admin_access_token',
  refreshToken: 'admin_refresh_token',
} as const

interface AdminAuthState {
  accessToken: string
  refreshToken: string
  adminInfo: AdminInfo | null
  permissions: string[]
  roles: string[]
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  refreshTokenAction: () => Promise<string>
  fetchProfile: () => Promise<void>
  hasPermission: (permission: string) => boolean
}

export const useAdminAuthStore = create<AdminAuthState>((set, get) => ({
  accessToken: localStorage.getItem(STORAGE_KEYS.accessToken) || '',
  refreshToken: localStorage.getItem(STORAGE_KEYS.refreshToken) || '',
  adminInfo: null,
  permissions: [],
  roles: [],

  login: async (username, password) => {
    const { data: response } = await adminLogin({ account: username, password })
    const { accessToken, refreshToken } = response.data
    localStorage.setItem(STORAGE_KEYS.accessToken, accessToken)
    localStorage.setItem(STORAGE_KEYS.refreshToken, refreshToken)
    set({ accessToken, refreshToken })
  },

  logout: () => {
    adminLogout().catch(() => {})
    localStorage.removeItem(STORAGE_KEYS.accessToken)
    localStorage.removeItem(STORAGE_KEYS.refreshToken)
    // 菜单树与账号权限绑定，换账号登录必须重新拉取
    useAdminMenuStore.getState().clear()
    set({
      accessToken: '',
      refreshToken: '',
      adminInfo: null,
      permissions: [],
      roles: [],
    })
    history.push('/admin/login')
  },

  refreshTokenAction: async () => {
    const { refreshToken } = get()
    if (!refreshToken) {
      get().logout()
      throw new Error('No admin refresh token available')
    }
    const { data: response } = await adminRefreshToken({ refreshToken })
    const { accessToken: newAccessToken, refreshToken: newRefreshToken } = response.data
    localStorage.setItem(STORAGE_KEYS.accessToken, newAccessToken)
    localStorage.setItem(STORAGE_KEYS.refreshToken, newRefreshToken)
    set({ accessToken: newAccessToken, refreshToken: newRefreshToken })
    return newAccessToken
  },

  fetchProfile: async () => {
    try {
      const { data: response } = await getAdminProfile()
      const profile = response.data
      set({
        adminInfo: {
          id: profile.id,
          username: profile.username,
          avatar: profile.avatar,
          permissions: profile.permissions ?? [],
          roles: (profile.roles ?? []).map((r: string) => ({ roleKey: r, roleName: r })),
        },
        permissions: profile.permissions ?? [],
        roles: (profile.roles ?? []) as string[],
      })
    } catch {
      set({ adminInfo: null, permissions: [], roles: [] })
      history.replace('/admin/login')
    }
  },

  hasPermission: (permission) => {
    const { permissions } = get()
    if (permissions.includes('*:*:*')) return true
    return permissions.includes(permission)
  },
}))
