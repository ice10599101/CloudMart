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
    // 登录态守卫页以 user 是否存在判断登录，必须等 profile 拉取完成后再跳转，
    // 否则目标页会误判未登录并弹回登录页（表现为需要登录两次）
    await get().fetchProfile()
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

// token 持久化在 localStorage 而 user 不持久化，刷新页面后需补拉用户资料，
// 否则依赖 user 判断登录态的页面（发布心愿、同路人小队等）会误判未登录并跳转登录页。
// token 已过期时由 request 拦截器统一走 refresh/登出流程
if (useAuthStore.getState().isAuthenticated) {
  useAuthStore.getState().fetchProfile()
}
