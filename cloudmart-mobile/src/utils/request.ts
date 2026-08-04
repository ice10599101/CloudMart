import Taro from '@tarojs/taro'
import type { ApiResponse } from '@/types'

// H5 uses /api proxy, Mini Program uses full URL from env
const IS_WEAPP = Taro.getEnv() === Taro.ENV_TYPE.WEAPP
const API_BASE = IS_WEAPP
  ? `${process.env.TARO_APP_API_HOST || 'http://127.0.0.1'}:8090/api`
  : '/api'


let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

interface RequestConfig {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  data?: Record<string, unknown>
  header?: Record<string, string>
}

async function request<T = unknown>(config: RequestConfig): Promise<{ data: ApiResponse<T> }> {
  const token = Taro.getStorageSync('access_token')
  const header: Record<string, string> = {
    'Content-Type': 'application/json',
    ...config.header,
  }
  if (token) {
    header.Authorization = `Bearer ${token}`
  }

  const method = (config.method || 'GET').toUpperCase()
  if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
    header['X-Idempotency-Key'] = `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
  }

  try {
    const res = await Taro.request({
      url: `${API_BASE}${config.url}`,
      method: config.method || 'GET',
      data: config.data,
      header,
      timeout: 15000,
    })
    return { data: res.data as ApiResponse<T> }
  } catch (error: any) {
    if (error?.statusCode === 401) {
      return handleRefresh<T>(config)
    }
    throw error
  }
}

async function handleRefresh<T>(originalConfig: RequestConfig): Promise<{ data: ApiResponse<T> }> {
  const refreshToken = Taro.getStorageSync('refresh_token')
  if (!refreshToken) {
    Taro.removeStorageSync('access_token')
    Taro.removeStorageSync('refresh_token')
    Taro.redirectTo({ url: '/pages/login/index' })
    return Promise.reject(new Error('No refresh token'))
  }

  if (isRefreshing) {
    return new Promise((resolve) => {
      pendingRequests.push((token: string) => {
        originalConfig.header = originalConfig.header || {}
        originalConfig.header.Authorization = `Bearer ${token}`
        resolve(request<T>(originalConfig))
      })
    })
  }

  isRefreshing = true

  try {
    const res = await Taro.request({
      url: `${API_BASE}/auth/refresh`,
      method: 'POST',
      data: { refreshToken },
      header: { 'Content-Type': 'application/json' },
    })
    const { accessToken, refreshToken: newRefreshToken } = res.data.data
    Taro.setStorageSync('access_token', accessToken)
    Taro.setStorageSync('refresh_token', newRefreshToken)

    pendingRequests.forEach((cb) => cb(accessToken))
    pendingRequests = []

    originalConfig.header = originalConfig.header || {}
    originalConfig.header.Authorization = `Bearer ${accessToken}`
    return request<T>(originalConfig)
  } catch {
    Taro.removeStorageSync('access_token')
    Taro.removeStorageSync('refresh_token')
    Taro.redirectTo({ url: '/pages/login/index' })
    return Promise.reject(new Error('Refresh token expired'))
  } finally {
    isRefreshing = false
  }
}

export default request
