import { create } from 'zustand'
import { storage } from '@/utils/storage'
import type { User } from '@/types'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { router } from 'expo-router'

interface AuthState {
  user: User | null
  isLoggedIn: boolean
  login: (account: string, password: string) => Promise<void>
  register: (nickname: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  fetchUser: () => Promise<void>
  updateUser: (user: Partial<User>) => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isLoggedIn: false,

  login: async (account, password) => {
    const res = await authApi.login({ account, password })
    const { accessToken, refreshToken } = res.data.data
    await storage.multiSet([
      ['access_token', accessToken],
      ['refresh_token', refreshToken],
    ])
    // Fetch user profile after login
    try {
      const profileRes = await userApi.getProfile()
      set({ isLoggedIn: true, user: profileRes.data.data })
    } catch {
      set({ isLoggedIn: true })
    }
  },

  register: async (nickname, email, password) => {
    await authApi.register({ nickname, email, password })
  },

  logout: async () => {
    try {
      await authApi.logout()
    } finally {
      await storage.multiRemove(['access_token', 'refresh_token'])
      set({ user: null, isLoggedIn: false })
      router.replace('/login')
    }
  },

  fetchUser: async () => {
    try {
      const res = await userApi.getProfile()
      set({ user: res.data.data, isLoggedIn: true })
    } catch {
      set({ user: null, isLoggedIn: false })
    }
  },

  updateUser: (partial) => {
    set((state) => ({
      user: state.user ? { ...state.user, ...partial } : null,
    }))
  },
}))
