import { create } from 'zustand'
import { history } from 'umi'
import { login as loginApi } from '@/api/auth'
import { getUserProfile, type UserProfile } from '@/api/user'

const STORAGE_KEYS = {
  accessToken: 'access_token',
  refreshToken: 'refresh_token',
}

interface AuthState {
  accessToken: string
  refreshToken: string
  isAuthenticated: boolean
  user: UserProfile | null
  login: (account: string, password: string, redirect?: string) => Promise<void>
  logout: () => void
  fetchProfile: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: localStorage.getItem(STORAGE_KEYS.accessToken) || '',
  refreshToken: localStorage.getItem(STORAGE_KEYS.refreshToken) || '',
  isAuthenticated: !!localStorage.getItem(STORAGE_KEYS.accessToken),
  user: null,

  login: async (account, password, redirect) => {
    const { data: response } = await loginApi({ account, password })
    const { accessToken, refreshToken } = response.data
    localStorage.setItem(STORAGE_KEYS.accessToken, accessToken)
    localStorage.setItem(STORAGE_KEYS.refreshToken, refreshToken)
    set({ accessToken, refreshToken, isAuthenticated: true })
    get().fetchProfile()
    history.push(redirect || '/')
  },

  logout: () => {
    localStorage.removeItem(STORAGE_KEYS.accessToken)
    localStorage.removeItem(STORAGE_KEYS.refreshToken)
    set({ accessToken: '', refreshToken: '', isAuthenticated: false, user: null })
    history.push('/login')
  },

  fetchProfile: async () => {
    try {
      const { data: response } = await getUserProfile()
      set({ user: response.data })
    } catch {
      // ignore
    }
  },
}))
