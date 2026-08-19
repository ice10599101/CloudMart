import axios from 'axios'
import type { ApiResponse } from '@/types/api'
import { message } from 'antd'
import { history } from 'umi'
import { useAdminAuthStore } from '@/stores/adminAuth'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

const SERVICE_UNAVAILABLE_CODES = new Set<string>()
let serviceUnavailableTimer: ReturnType<typeof setTimeout> | null = null

function processPendingRequests(token: string) {
  pendingRequests.forEach((cb) => cb(token))
  pendingRequests = []
}

function isAdminRequest(url: string): boolean {
  return url.startsWith('/admin/') || url.startsWith('/auth/admin/')
}

const USER_PUBLIC_PATH_PREFIXES = [
  '/auth/login',
  '/auth/refresh',
  '/user/users/register',
  '/product/products/search',
  '/product/categories',
  '/product/reviews/product/',
  '/product/reviews/stats/',
  '/product/products/',
  '/coupon/coupon-templates',
  '/seckill/activities',
  '/seckill/products/activity/',
  '/live/rooms',
  '/marketing/group/activities',
  '/marketing/group/orders',
  '/payment/payments/callback',
  '/file/uploads/',
  '/community/users/recommend',
  '/community/posts/hot',
  '/community/posts/',
  '/community/topics/',
  '/wish/wishes',
  '/wish/categories',
  '/wish/home',
]

const ADMIN_PUBLIC_PATH_PREFIXES = [
  '/auth/admin/login',
  '/auth/admin/refresh',
]

// 依赖网关注入用户身份头的心愿接口（登录态）：
// /wish/wishes/my（我的心愿）、/wish/wishes/{id}/interactions、/wish/wishes/{id}/comments
const WISH_AUTH_REQUIRED_REGEX = /^\/wish\/wishes\/(my|\d+\/(interactions|comments))/

function isPublicPath(url: string): boolean {
  if (!url) return false
  if (WISH_AUTH_REQUIRED_REGEX.test(url)) return false
  const prefixes = isAdminRequest(url) ? ADMIN_PUBLIC_PATH_PREFIXES : USER_PUBLIC_PATH_PREFIXES
  for (const prefix of prefixes) {
    if (url.startsWith(prefix)) return true
  }
  if (!isAdminRequest(url) && /\/product\/products\/\d+/.test(url)) return true
  return false
}

/** 构造携带业务错误码的 Error（组件需按 code 区分 402/409/429 等场景） */
function toBusinessError(code: string, messageText: string): Error & { code: string } {
  const error = new Error(messageText) as Error & { code: string }
  error.code = code
  return error
}

request.interceptors.request.use(
  (config) => {
    const url = config.url ?? ''
    if (isPublicPath(url)) return config

    const tokenKey = isAdminRequest(url) ? 'admin_access_token' : 'access_token'
    const token = localStorage.getItem(tokenKey)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    const method = (config.method ?? 'GET').toUpperCase()
    if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
      config.headers['X-Idempotency-Key'] = crypto.randomUUID()
    }

    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => {
    const data = response.data as ApiResponse<unknown>
    if (data.success === false) {
      const errorCode = data.error?.code ?? ''
      const businessError = toBusinessError(errorCode, data.error?.message || '请求失败')
      if (errorCode === 'UNAUTHORIZED') {
        return Promise.reject(businessError)
      }
      if (errorCode.endsWith('_SERVICE_UNAVAILABLE')) {
        if (!SERVICE_UNAVAILABLE_CODES.has(errorCode)) {
          SERVICE_UNAVAILABLE_CODES.add(errorCode)
          message.warning(data.error?.message || '服务暂不可用')
          if (serviceUnavailableTimer) clearTimeout(serviceUnavailableTimer)
          serviceUnavailableTimer = setTimeout(() => SERVICE_UNAVAILABLE_CODES.clear(), 5000)
        }
        return Promise.reject(businessError)
      }
      message.error(data.error?.message || '请求失败')
      return Promise.reject(businessError)
    }
    return response
  },
  async (error) => {
    if (error.response?.status === 401 || error.response?.status === 403) {
      // 业务错误（携带 error.code，如 WISH_CONSENT_REQUIRED/WISH_NOT_AUTHOR）直接透传，
      // 不触发 token refresh：refresh 后仍会 403，既浪费也会造成组件拿不到业务码
      const errCode = error.response?.data?.error?.code as string | undefined
      if (errCode && errCode !== 'UNAUTHORIZED') {
        return Promise.reject(
          toBusinessError(errCode, error.response?.data?.error?.message || '请求失败'),
        )
      }
      const url = error.config.url ?? ''
      if (isPublicPath(url)) {
        return Promise.reject(error)
      }
      const admin = isAdminRequest(url)
      const refreshTokenKey = admin ? 'admin_refresh_token' : 'refresh_token'
      const accessTokenKey = admin ? 'admin_access_token' : 'access_token'
      const refreshTokenValue = localStorage.getItem(refreshTokenKey)

      if (refreshTokenValue) {
        if (isRefreshing) {
          return new Promise((resolve) => {
            pendingRequests.push((token: string) => {
              if (!token) {
                resolve(Promise.reject(error))
                return
              }
              error.config.headers.Authorization = `Bearer ${token}`
              resolve(request(error.config))
            })
          })
        }
        isRefreshing = true
        try {
          const refreshUrl = admin ? '/api/auth/admin/refresh' : '/api/auth/refresh'
          const { data } = await axios.post(refreshUrl, {
            refreshToken: refreshTokenValue,
          })
          localStorage.setItem(accessTokenKey, data.data.accessToken)
          localStorage.setItem(refreshTokenKey, data.data.refreshToken)
          processPendingRequests(data.data.accessToken)
          error.config.headers.Authorization = `Bearer ${data.data.accessToken}`
          return request(error.config)
        } catch {
          localStorage.removeItem(accessTokenKey)
          localStorage.removeItem(refreshTokenKey)
          if (admin) {
            useAdminAuthStore.setState({ accessToken: '', refreshToken: '', adminInfo: null, permissions: [], roles: [] })
          }
          pendingRequests.forEach((cb) => cb(''))
          pendingRequests = []
          if (admin) {
            history.replace('/admin/login')
          } else {
            const currentPath = window.location.pathname
            if (currentPath !== '/login' && currentPath !== '/register') {
              sessionStorage.setItem('login_redirect', currentPath)
            }
            history.replace('/login')
          }
          return Promise.reject(error)
        } finally {
          isRefreshing = false
        }
      } else {
        if (admin) {
          useAdminAuthStore.setState({ accessToken: '', refreshToken: '', adminInfo: null, permissions: [], roles: [] })
          history.replace('/admin/login')
        } else {
          const currentPath = window.location.pathname
          if (currentPath !== '/login' && currentPath !== '/register') {
            sessionStorage.setItem('login_redirect', currentPath)
          }
          history.replace('/login')
        }
        return Promise.reject(error)
      }
    }
    message.error(error.response?.data?.error?.message || '网络错误')
    return Promise.reject(error)
  },
)

export default request
