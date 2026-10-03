import Taro from '@tarojs/taro'
import type { ApiResponse } from '@/types'

/**
 * FE-01：API 基址解析——小程序端 TARO_APP_API_HOST 接受完整基址（scheme+host[:port]，
 * 支持正式 HTTPS 域名），不再硬拼 :8090；生产构建缺失时启动即抛错（fail-fast），
 * 绝不静默回退到 127.0.0.1。H5 走 /api 代理。
 */
const IS_WEAPP = Taro.getEnv() === Taro.ENV_TYPE.WEAPP

function resolveApiBase(): string {
  if (!IS_WEAPP) return '/api'
  const configured = process.env.TARO_APP_API_HOST?.trim()
  if (configured) {
    return `${configured.replace(/\/+$/, '')}/api`
  }
  if (process.env.NODE_ENV === 'production') {
    throw new Error('[request] 小程序生产构建必须注入 TARO_APP_API_HOST（完整 API 基址）')
  }
  return 'http://127.0.0.1:8090/api'
}

export const API_BASE = resolveApiBase()

let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

interface RequestConfig {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  data?: Record<string, unknown>
  /** GET 查询参数（Taro.request data 在 GET 下自动拼 query） */
  params?: Record<string, unknown>
  header?: Record<string, string>
  /** FE-01：刷新重放标记——重放请求若仍 401 直接终态，不再二次刷新（防循环） */
  _retried?: boolean
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

/** 小程序运行时无 atob 的兜底解码（仅用于 JWT payload，ASCII 安全） */
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

/** FE-01/T22：从 Bearer 令牌解出稳定主体（sub）；指纹不随 token 轮换变化 */
function subjectFromToken(token: string): string {
  const parts = token.split('.')
  if (parts.length !== 3) return ''
  try {
    const payload = JSON.parse(decodeBase64(parts[1])) as { sub?: string }
    return payload.sub ?? ''
  } catch {
    return ''
  }
}

function intentStoreKey(method: string, url: string, data: unknown, token: string): string | null {
  if (!['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
    return null
  }
  const fingerprint = hashFingerprint(
    [method, url, data ? JSON.stringify(data) : '', subjectFromToken(token)].join('|'),
  )
  return `idem:intent:${fingerprint}`
}

/**
 * R10：意图键终态判定（按业务 outcome 而非 HTTP<500）——
 * 409 处理中 / 429 / 401 可刷新 / 5xx / 网络错误是瞬态，清键会让重试换新键。
 */
const TRANSIENT_ERROR_CODES = new Set([
  'PET_REQUEST_IN_PROGRESS', 'PET_SETTLEMENT_PENDING', 'PET_TEMPORARILY_UNAVAILABLE',
  'PET_FEATURE_DISABLED', 'PET_RATE_LIMITED', 'PET_QUOTA_EXCEEDED', 'UNAUTHORIZED',
])

function intentTerminal(statusCode: number, errorCode: string | undefined): boolean {
  if (statusCode >= 500 || statusCode === 429) {
    return false
  }
  if (TRANSIENT_ERROR_CODES.has(errorCode ?? '')) {
    return false
  }
  return true
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

/** 刷新失败：清凭据 + 跳登录，等待者随后统一拒绝（终态，不悬挂） */
function handleRefreshFailure(): Error {
  Taro.removeStorageSync('access_token')
  Taro.removeStorageSync('refresh_token')
  Taro.redirectTo({ url: '/pages/login/index' })
  return new Error('登录状态已失效，请重新登录')
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

  // FE-01：method 缺省即 GET——此前用 config.method === 'GET' 判定会漏拼 params
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

  // Taro.request 对 4xx/5xx 仍 resolve（仅网络层失败才 reject）——
  // FE-01：必须在成功路径检查 statusCode，401 触发刷新，而不是只依赖 catch 分支
  let res: Awaited<ReturnType<typeof Taro.request>>
  try {
    res = await Taro.request({
      url: `${API_BASE}${config.url}`,
      method: method as NonNullable<RequestConfig['method']>,
      data: method === 'GET' ? (config.params ?? config.data) : config.data,
      header,
      timeout: 15000,
    })
  } catch (error) {
    // 个别平台非 2xx 走 reject：同样按 401 刷新处理
    const statusCode = (error as { statusCode?: number })?.statusCode
    if (statusCode === 401) {
      return handleRefresh<T>(config)
    }
    throw error
  }

  // R10：终态按业务 outcome——瞬态（5xx/429/409处理中/结算中/限流）保留原键供重试收敛
  if (storeKey) {
    const errCode = (res.data as { error?: { code?: string } } | undefined)?.error?.code
    if (intentTerminal(res.statusCode, errCode)) {
      Taro.removeStorageSync(storeKey)
    }
  }

  if (res.statusCode === 401) {
    if (config._retried) {
      // 重放后仍 401：终态拒绝，防刷新循环打爆认证接口
      throw new Error('登录状态已失效，请重新登录')
    }
    return handleRefresh<T>(config)
  }
  return { data: res.data as ApiResponse<T> }
}

async function handleRefresh<T>(originalConfig: RequestConfig): Promise<{ data: ApiResponse<T> }> {
  const refreshToken = Taro.getStorageSync('refresh_token')
  if (!refreshToken) {
    throw handleRefreshFailure()
  }

  if (isRefreshing) {
    // 入队前标记：重放若仍 401 直接终态，不再排队刷新
    originalConfig._retried = true
    return new Promise((resolve, reject) => {
      pendingRequests.push((token: string) => {
        if (!token) {
          reject(new Error('登录状态已失效，请重新登录'))
          return
        }
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
    // FE-01：刷新接口也可能返回 401（refresh token 失效）——响应路径必须检查
    if (res.statusCode !== 200 || !res.data?.data) {
      throw handleRefreshFailure()
    }
    const { accessToken, refreshToken: newRefreshToken } = res.data.data
    Taro.setStorageSync('access_token', accessToken)
    Taro.setStorageSync('refresh_token', newRefreshToken)

    pendingRequests.forEach((cb) => cb(accessToken))
    pendingRequests = []

    originalConfig.header = originalConfig.header || {}
    originalConfig.header.Authorization = `Bearer ${accessToken}`
    originalConfig._retried = true
    return request<T>(originalConfig)
  } catch {
    const terminalError = handleRefreshFailure()
    // FE-01：刷新失败统一拒绝所有等待者——任何请求都不允许悬挂（T21）
    pendingRequests.forEach((cb) => cb(''))
    pendingRequests = []
    throw terminalError
  } finally {
    isRefreshing = false
  }
}

export default request
