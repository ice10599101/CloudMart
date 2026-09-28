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
  sendRegisterCode: (email: string) => Promise<{ sent: boolean; devCode?: string; message?: string }>
  register: (nickname: string, email: string, password: string, code: string) => Promise<string | undefined>
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

  sendRegisterCode: async (email) => {
    const res = await authApi.sendRegisterCode(email)
    // sent=false 为后端如实回传（通道未配置/发送失败），交由页面用 message 提示
    return res.data.data
  },

  register: async (nickname, email, password, code) => {
    const res = await authApi.register({ nickname, email, password, code })
    // 返回专属小答号（对齐 Web 端注册成功展示）
    return (res.data as { data?: { username?: string } })?.data?.username
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
