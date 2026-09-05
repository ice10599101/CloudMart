import axios from 'axios'
import type { ApiResponse } from '@/types/api'
import { message as staticMessage } from 'antd'
import { history } from 'umi'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { getAppMessage } from '@/utils/appMessage'

/** 拦截器是非组件环境，优先用 App 桥实例；桥未挂载时退回静态实例 */
function notify(kind: 'error' | 'warning', content: string) {
  const api = getAppMessage()
  if (api) {
    api[kind](content)
    return
  }
  staticMessage[kind](content)
}

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

/** 构造携带业务错误码的 Error（组件需按 code 区分 402/409/429 等场景） */
function toBusinessError(code: string, messageText: string): Error & { code: string } {
  const error = new Error(messageText) as Error & { code: string }
  error.code = code
  return error
}

// 已登录则一律附带身份头：公开接口带 token 无害（服务端忽略或用于个性化），
// 而心愿宇宙存在大量「路径公开、语义私有」的 GET（checkins/fulfillment/tree-hole 等），
// 若按前缀跳过会导致这些接口缺身份头而 401。token 过期由响应拦截器的刷新流程自愈。
request.interceptors.request.use(
  (config) => {
    const url = config.url ?? ''
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
          notify('warning', data.error?.message || '服务暂不可用')
          if (serviceUnavailableTimer) clearTimeout(serviceUnavailableTimer)
          serviceUnavailableTimer = setTimeout(() => SERVICE_UNAVAILABLE_CODES.clear(), 5000)
        }
        return Promise.reject(businessError)
      }
      notify('error', data.error?.message || '请求失败')
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
    notify('error', error.response?.data?.error?.message || '网络错误')
    return Promise.reject(error)
  },
)

export default request
