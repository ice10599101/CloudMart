import { createStore } from 'zustand'
import { useSyncExternalStore } from 'react'
import Taro from '@tarojs/taro'
import type { User } from '@/types'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'

interface AuthState {
  user: User | null
  isLoggedIn: boolean
  login: (account: string, password: string) => Promise<void>
  register: (nickname: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  fetchUser: () => Promise<void>
  updateUser: (user: Partial<User>) => void
}

const authStore = createStore<AuthState>((set) => ({
  user: null,
  isLoggedIn: !!Taro.getStorageSync('access_token'),

  login: async (account, password) => {
    const res = await authApi.login({ account, password })
    const { accessToken, refreshToken } = res.data.data
    Taro.setStorageSync('access_token', accessToken)
    Taro.setStorageSync('refresh_token', refreshToken)
    set({ isLoggedIn: true })
  },

  register: async (nickname, email, password) => {
    await authApi.register({ nickname, email, password })
  },

  logout: async () => {
    try {
      await authApi.logout()
    } finally {
      Taro.removeStorageSync('access_token')
      Taro.removeStorageSync('refresh_token')
      set({ user: null, isLoggedIn: false })
      Taro.redirectTo({ url: '/pages/login/index' })
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

export function useAuthStore(): AuthState {
  return useSyncExternalStore(authStore.subscribe, authStore.getState)
}

export { authStore }
