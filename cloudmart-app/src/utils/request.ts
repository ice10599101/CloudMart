import axios, { InternalAxiosRequestConfig } from 'axios'
import Constants from 'expo-constants'
import { Platform } from 'react-native'
import { storage } from '@/utils/storage'
import type { ApiResponse } from '@/types'

/**
 * API 基址按运行环境解析：
 * - Web（metro dev server / expo web）：相对路径 /api，由 metro.config.js 代理转发到 Gateway
 * - Native + Expo Go dev：走 metro 代理（manifest2.extra.expoGoHostUri 为 Expo 连接的
 *   dev server 地址，手机只需可达电脑 8081——Expo Go 本身就依赖它；避免真机直连
 *   公网 8090 被网络拦截）
 * - Native 生产构建或显式配置：EXPO_PUBLIC_API_HOST 直连 Gateway
 *
 * 注意：RN 运行时全局存在 window（window === global），typeof window 判定 web
 * 在原生端恒为 true，必须用 Platform.OS 判定（曾因此导致真机全部请求走相对路径失败）
 */
function resolveApiBase(): string {
  if (Platform.OS === 'web') return '/api'
  const isDev = process.env.NODE_ENV !== 'production'
  const metroHost =
    Constants.expoConfig?.hostUri ??
    (Constants.manifest2 as { extra?: { expoGoHostUri?: string } } | undefined)?.extra?.expoGoHostUri
  if (isDev && metroHost) {
    return `http://${metroHost}/api`
  }
  return `${process.env.EXPO_PUBLIC_API_HOST || 'http://127.0.0.1'}:8090/api`
}

const API_BASE = resolveApiBase()
// 诊断日志：native 端输出到 metro 终端，用于确认真机实际请求基址
if (Platform.OS !== 'web') {
  console.log('[request] API_BASE =', API_BASE)
}

export { API_BASE }

const client = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

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

function intentStoreKey(config: InternalAxiosRequestConfig): string | null {
  const method = (config.method ?? 'GET').toUpperCase()
  if (!['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
    return null
  }
  const fingerprint = hashFingerprint(
    [
      method,
      config.url ?? '',
      config.params ? JSON.stringify(config.params) : '',
      config.data ? JSON.stringify(config.data) : '',
      config.headers?.Authorization ?? '',
    ].join('|'),
  )
  return `idem:intent:${fingerprint}`
}

function intentCarrier(config: unknown): { _intentStoreKey?: string } {
  return config as { _intentStoreKey?: string }
}

async function resolveIntentKey(storeKey: string): Promise<string> {
  try {
    const raw = await storage.getItem(storeKey)
    if (raw) {
      const entry = JSON.parse(raw) as IdempotencyIntent
      if (entry?.key && Date.now() - entry.createdAt < INTENT_TTL_MS) {
        return entry.key
      }
      await storage.removeItem(storeKey)
    }
  } catch {
    await storage.removeItem(storeKey)
  }
  const fresh = `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
  await storage.setItem(storeKey, JSON.stringify({ key: fresh, createdAt: Date.now() }))
  return fresh
}

async function clearIntent(config: unknown): Promise<void> {
  const storeKey = intentCarrier(config)?._intentStoreKey
  if (storeKey) {
    await storage.removeItem(storeKey)
  }
}

client.interceptors.request.use(async (config) => {
  const token = await storage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  const method = (config.method ?? 'GET').toUpperCase()
  if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
    // B04：只在调用方未提供幂等键时赋值——重试沿用同键
    if (!config.headers['X-Idempotency-Key']) {
      config.headers['X-Idempotency-Key'] = `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
    }
    // W03/FE-03：意图键持久化——断网重试/刷新/重启复用原键（服务端收敛同一笔）
    const storeKey = intentStoreKey(config)
    if (storeKey) {
      intentCarrier(config)._intentStoreKey = storeKey
      config.headers['X-Idempotency-Key'] = await resolveIntentKey(storeKey)
    }
  }

  return config
})

client.interceptors.response.use(
  (response) => {
    void clearIntent(response.config)
    return response
  },
  async (error) => {
    // 终态响应（HTTP<500，含业务拒绝）清理意图键；网络错误/5xx 保留原键供重试收敛
    if (error.response?.status && error.response.status < 500) {
      void clearIntent(error.config)
    }
    const originalRequest = error.config
    if (error.response?.status === 401 && !originalRequest._retry) {
      const refreshToken = await storage.getItem('refresh_token')
      if (!refreshToken) {
        await storage.multiRemove(['access_token', 'refresh_token'])
        return Promise.reject(error)
      }

      if (isRefreshing) {
        return new Promise((resolve) => {
          pendingRequests.push((token: string) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(client(originalRequest))
          })
        })
      }

      isRefreshing = true
      originalRequest._retry = true

      try {
        const res = await client.post('/auth/refresh', { refreshToken })
        const { accessToken: newAccessToken, refreshToken: newRefreshToken } = res.data.data
        await storage.multiSet([
          ['access_token', newAccessToken],
          ['refresh_token', newRefreshToken],
        ])
        pendingRequests.forEach((cb) => cb(newAccessToken))
        pendingRequests = []
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        return client(originalRequest)
      } catch {
        await storage.multiRemove(['access_token', 'refresh_token'])
        return Promise.reject(error)
      } finally {
        isRefreshing = false
      }
    }
    return Promise.reject(error)
  }
)

interface RequestConfig {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  data?: Record<string, unknown> | FormData
  header?: Record<string, string>
  /** GET 查询参数（axios params；W03 钱包游标分页等） */
  params?: Record<string, unknown>
}

async function request<T = unknown>(config: RequestConfig): Promise<{ data: ApiResponse<T> }> {
  const res = await client.request<ApiResponse<T>>({
    url: config.url,
    method: config.method || 'GET',
    data: config.data,
    params: config.params,
    headers: config.header,
  })
  return { data: res.data }
}

export default request
