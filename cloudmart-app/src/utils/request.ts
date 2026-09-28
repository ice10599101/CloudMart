import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import Constants from 'expo-constants'
import { Platform } from 'react-native'
import { router } from 'expo-router'
import { storage } from '@/utils/storage'
import { useAuthStore } from '@/store/auth'
import type { ApiResponse } from '@/types'

/**
 * API 基址按运行环境解析（FE-01）：
 * - Web（metro dev server / expo web）：相对路径 /api，由 metro.config.js 代理转发到 Gateway
 * - Native + Expo Go dev：走 metro 代理（手机只需可达 dev server，避免真机直连公网被拦）
 * - Native 生产构建：必须注入 EXPO_PUBLIC_API_HOST 完整基址（scheme+host[:port]），
 *   缺失时启动即抛错（fail-fast），绝不静默回退到手机自身 127.0.0.1；支持完整
 *   HTTPS 域名，不再硬拼 :8090
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
  const configured = process.env.EXPO_PUBLIC_API_HOST?.trim()
  if (configured) {
    return `${configured.replace(/\/+$/, '')}/api`
  }
  if (!isDev) {
    throw new Error('[request] 生产构建必须注入 EXPO_PUBLIC_API_HOST（完整 HTTPS API 基址）')
  }
  return 'http://127.0.0.1:8090/api'
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

/**
 * FE-01：刷新走独立 client——不带 401 拦截器。否则刷新自身 401 时会再次进入
 * 本文件的刷新分支，把自己排进等待队列形成自等待死锁（此前缺陷）。
 */
const refreshClient = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

let isRefreshing = false
let pendingRequests: ((token: string) => void)[] = []

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

/** Hermes 无全局 atob 的兜底解码（仅用于 JWT payload，ASCII 安全） */
function decodeBase64(input: string): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
  const normalized = input.replace(/-/g, '+').replace(/_/g, '/').replace(/=+$/, '')
  let output = ''
  let buffer = 0
  let bits = 0
  for (const ch of normalized) {
    const value = chars.indexOf(ch)
    if (value < 0) continue
    buffer = (buffer << 6) | value
    bits += 6
    if (bits >= 8) {
      bits -= 8
      output += String.fromCharCode((buffer >> bits) & 0xff)
    }
  }
  return output
}

/** FE-01/T22：从已附带的 Bearer 令牌解出稳定主体（sub）；指纹不随 token 轮换变化 */
function subjectFromAuthorization(authorization: unknown): string {
  if (typeof authorization !== 'string' || !authorization.startsWith('Bearer ')) {
    return ''
  }
  const parts = authorization.slice('Bearer '.length).split('.')
  if (parts.length !== 3) return ''
  try {
    const payload = JSON.parse(decodeBase64(parts[1])) as { sub?: string }
    return payload.sub ?? ''
  } catch {
    return ''
  }
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
      subjectFromAuthorization(config.headers?.Authorization),
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

/** 刷新失败：清凭据 + 同步认证 store + 跳登录，等待者随后统一拒绝 */
async function handleRefreshFailure() {
  await storage.multiRemove(['access_token', 'refresh_token'])
  useAuthStore.setState({ user: null, isLoggedIn: false })
  router.replace('/login')
}

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
    // FE-01：只有 401 触发刷新；403 是已认证但无权限，刷新无济于事
    if (error.response?.status === 401 && !originalRequest._retry) {
      const refreshToken = await storage.getItem('refresh_token')
      if (!refreshToken) {
        await handleRefreshFailure()
        return Promise.reject(error)
      }

      if (isRefreshing) {
        // 入队前标记 _retry：重放若仍 401 直接终态，不再排队刷新（防循环）
        originalRequest._retry = true
        return new Promise((resolve) => {
          pendingRequests.push((token: string) => {
            if (!token) {
              // 刷新失败：等待者以明确终态拒绝，绝不悬挂（T21）
              resolve(
                Promise.reject(
                  new AxiosError('登录状态已失效，请重新登录', 'UNAUTHORIZED', originalRequest),
                ),
              )
              return
            }
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(client(originalRequest))
          })
        })
      }

      isRefreshing = true
      originalRequest._retry = true

      try {
        // FE-01：refreshClient 无拦截器——刷新自身 401 直接走 catch 分支
        const res = await refreshClient.post('/auth/refresh', { refreshToken })
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
        await handleRefreshFailure()
        pendingRequests.forEach((cb) => cb(''))
        pendingRequests = []
        return Promise.reject(
          new AxiosError('登录状态已失效，请重新登录', 'UNAUTHORIZED', originalRequest),
        )
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
