import Taro from '@tarojs/taro'
import type { ApiResponse } from '@/types'

// H5 uses /api proxy, Mini Program uses full URL from env
const IS_WEAPP = Taro.getEnv() === Taro.ENV_TYPE.WEAPP
export const API_BASE = IS_WEAPP
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

// ==================== W03/FE-03：持久化幂等意图键 ====================
// §8.5.2：同一意图（方法+路径+载荷+账号）的键持久化；网络重试/刷新/重启复用原键，
// 收到终态响应（HTTP<500，含业务拒绝）即清除——下一次用户动作就是新意图。

const INTENT_TTL_MS = 10 * 60 * 1000

interface IdempotencyIntent {
  key: string
  createdAt: number
}

function hashFingerprint(input: string): string {
  let hash = 0x811c9dc5
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i)
    hash = Math.imul(hash, 0x01000193)
  }
  return `${(hash >>> 0).toString(16)}-${input.length.toString(36)}`
}

function intentStoreKey(method: string, url: string, data: unknown, token: string): string | null {
  if (!['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
    return null
  }
  const fingerprint = hashFingerprint(
    [method, url, data ? JSON.stringify(data) : '', token].join('|'),
  )
  return `idem:intent:${fingerprint}`
}

function resolveIntentKey(storeKey: string): string {
  try {
    const raw = Taro.getStorageSync(storeKey)
    if (raw) {
      const entry = JSON.parse(raw) as IdempotencyIntent
      if (entry?.key && Date.now() - entry.createdAt < INTENT_TTL_MS) {
        return entry.key
      }
      Taro.removeStorageSync(storeKey)
    }
  } catch {
    Taro.removeStorageSync(storeKey)
  }
  const fresh = `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
  Taro.setStorageSync(storeKey, JSON.stringify({ key: fresh, createdAt: Date.now() }))
  return fresh
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
    // B04：只在调用方未提供幂等键时赋值——重试沿用同键
    if (!header['X-Idempotency-Key']) {
      header['X-Idempotency-Key'] = `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
    }
  }
  // W03/FE-03：意图键持久化（含 401 刷新后的重放——重放复用 TTL 内同键）
  const storeKey = intentStoreKey(method, config.url, config.data, token)
  if (storeKey) {
    header['X-Idempotency-Key'] = resolveIntentKey(storeKey)
  }

  try {
    const res = await Taro.request({
      url: `${API_BASE}${config.url}`,
      method: config.method || 'GET',
      data: config.data,
      header,
      timeout: 15000,
    })
    // 终态响应（HTTP<500，含业务拒绝）清理意图；5xx 保留原键供重试收敛
    if (storeKey && res.statusCode < 500) {
      Taro.removeStorageSync(storeKey)
    }
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
