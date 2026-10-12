import { create } from 'zustand'
import { history } from 'umi'
import { login as loginApi, logoutApi } from '@/api/auth'
import { getUserProfile, type UserProfile } from '@/api/user'

const STORAGE_KEYS = {
  accessToken: 'access_token',
  refreshToken: 'refresh_token',
}

interface AuthState {
  accessToken: string
  refreshToken: string
  isAuthenticated: boolean
  /** 冷启动 profile 补拉进行中：守卫页据此避免在 user 就绪前误跳登录页 */
  userLoading: boolean
  user: UserProfile | null
  login: (account: string, password: string, redirect?: string) => Promise<void>
  /** R23：服务端撤销当前会话（access sid + refresh 家族）后再清本地；失败也保证本地清理 */
  logout: () => Promise<void>
  fetchProfile: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: localStorage.getItem(STORAGE_KEYS.accessToken) || '',
  refreshToken: localStorage.getItem(STORAGE_KEYS.refreshToken) || '',
  isAuthenticated: !!localStorage.getItem(STORAGE_KEYS.accessToken),
  userLoading: !!localStorage.getItem(STORAGE_KEYS.accessToken),
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

  logout: async () => {
    // R23：先请求服务端撤销当前会话（access 立即失效 + refresh 家族吊销），
    // 无论网络/鉴权失败与否，finally 都清掉本地凭据并跳登录——
    // 网络失败时本地凭据仍删除（服务端 refresh 家族在到期前残留的极端情况由改密/登出全部设备兜底）
    try {
      await logoutApi()
    } catch {
      // 拦截器已处理提示；不阻断本地登出
    } finally {
      localStorage.removeItem(STORAGE_KEYS.accessToken)
      localStorage.removeItem(STORAGE_KEYS.refreshToken)
      set({ accessToken: '', refreshToken: '', isAuthenticated: false, user: null })
      history.push('/login')
    }
  },

  fetchProfile: async () => {
    try {
      const { data: response } = await getUserProfile()
      set({ user: response.data, userLoading: false })
    } catch {
      set({ userLoading: false })
    }
  },
}))

// token 持久化在 localStorage 而 user 不持久化，刷新页面后需补拉用户资料，
// 否则依赖 user 判断登录态的页面（发布心愿、同路人小队等）会误判未登录并跳转登录页。
// token 已过期时由 request 拦截器统一走 refresh/登出流程
if (useAuthStore.getState().isAuthenticated) {
  useAuthStore.getState().fetchProfile()
}
