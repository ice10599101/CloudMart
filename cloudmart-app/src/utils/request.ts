import axios, { InternalAxiosRequestConfig } from 'axios'
import Constants from 'expo-constants'
import { Platform } from 'react-native'
import { router } from 'expo-router'
import { storage } from '@/utils/storage'
import { useAuthStore } from '@/store/auth'
import type { ApiResponse } from '@/types'
import { BusinessError } from '@/utils/businessError'
import {
  WRITE_METHODS,
  hashFingerprint,
  decideIntent,
  intentTerminal,
  parseIntentEntry,
} from '@/utils/idempotency'

export { BusinessError, isBusinessError, errorMessage } from '@/utils/businessError'

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

// ==================== W03/FE-03/R25：持久化幂等意图键 ====================
// §8.5.2：同一意图（方法+路径+载荷+账号）的键持久化；网络重试/刷新/重启复用原键。
// 普通写意图收到终态响应即清除；资金/权益操作用 persistentIntent 创建持久意图，
// 不随本地 TTL 轮换，未知状态先查原单。

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
  if (!WRITE_METHODS.includes(method as (typeof WRITE_METHODS)[number])) {
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

interface IntentCarrier {
  _intentStoreKey?: string
  persistentIntent?: boolean
}

function intentCarrier(config: unknown): IntentCarrier {
  return config as IntentCarrier
}

function generateIntentKey(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
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
  if (WRITE_METHODS.includes(method as (typeof WRITE_METHODS)[number])) {
    // R25：调用方显式 X-Idempotency-Key 优先且不可覆盖——拦截器不重新生成、
    // 不登记存储、不参与终态清理（生命周期完全由调用方管理）
    if (!config.headers['X-Idempotency-Key']) {
      const storeKey = intentStoreKey(config)
      if (storeKey) {
        const decision = decideIntent(
          await storage.getItem(storeKey),
          Date.now(),
          intentCarrier(config).persistentIntent === true,
          generateIntentKey,
        )
        config.headers['X-Idempotency-Key'] = decision.key
        intentCarrier(config)._intentStoreKey = storeKey
        if (decision.action === 'create') {
          await storage.setItem(storeKey, JSON.stringify(decision.entry))
        }
      }
    }
  }

  return config
})

/** 刷新失败：清凭据 + 同步认证 store + 跳登录，等待者随后统一拒绝 */
async function handleRefreshFailure(): Promise<BusinessError> {
  await storage.multiRemove(['access_token', 'refresh_token'])
  useAuthStore.setState({ user: null, isLoggedIn: false })
  router.replace('/login')
  return new BusinessError('UNAUTHORIZED', '登录状态已失效，请重新登录', { status: 401 })
}

client.interceptors.response.use(
  (response) => {
    const errData = response.data as { success?: boolean; error?: { code?: string; message?: string } } | undefined
    if (errData?.success === false) {
      // R10/R25：瞬态错误码保留原键；业务拒绝统一转为 BusinessError（三端一致），
      // 页面不再依赖 res.data.success 守卫，杜绝"假成功"
      if (intentTerminal(response.status, errData.error?.code)) {
        void clearIntent(response.config)
      }
      return Promise.reject(
        new BusinessError(
          errData.error?.code || 'BIZ_ERROR',
          errData.error?.message || '请求失败',
          { status: response.status, requestId: String(response.headers?.['x-request-id'] ?? '') || undefined },
        ),
      )
    }
    void clearIntent(response.config)
    return response
  },
  async (error) => {
    // R10：终态按业务 outcome——瞬态（5xx/429/409处理中/结算中/限流/网络错误）保留原键
    const errStatus = error.response?.status as number | undefined
    const errEnvelope = error.response?.data as
      | { error?: { code?: string; message?: string } }
      | undefined
    const errCode = errEnvelope?.error?.code
    if (intentTerminal(errStatus, errCode)) {
      void clearIntent(error.config)
    }
    const originalRequest = error.config
    // FE-01：只有 401 触发刷新；403 是已认证但无权限，刷新无济于事
    if (errStatus === 401 && !originalRequest?._retry) {
      // 业务错误（携带 error.code，如 TOKEN_REUSE_DETECTED）直接透传，不触发刷新
      if (errCode && errCode !== 'UNAUTHORIZED') {
        return Promise.reject(
          new BusinessError(errCode, errEnvelope?.error?.message || '请求失败', {
            status: 401,
            requestId: String(error.response?.headers?.['x-request-id'] ?? '') || undefined,
          }),
        )
      }
      const refreshToken = await storage.getItem('refresh_token')
      if (!refreshToken) {
        return Promise.reject(await handleRefreshFailure())
      }

      if (isRefreshing) {
        // 入队前标记 _retry：重放若仍 401 直接终态，不再排队刷新（防循环）
        originalRequest._retry = true
        return new Promise((resolve, reject) => {
          pendingRequests.push((token: string) => {
            if (!token) {
              // 刷新失败：等待者以明确终态拒绝，绝不悬挂（T21）
              reject(new BusinessError('UNAUTHORIZED', '登录状态已失效，请重新登录', { status: 401 }))
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
      } catch (refreshError) {
        // 刷新请求本身断网/超时（无 HTTP 响应）：鉴权状态未知，不强制登出，
        // 按网络错误终态拒绝等待者；收到明确 4xx（refresh token 失效）才走登出
        if (!(refreshError as { response?: unknown })?.response) {
          const networkError = new BusinessError('NETWORK_ERROR', '网络错误，请检查网络连接')
          pendingRequests.forEach((cb) => cb(''))
          pendingRequests = []
          return Promise.reject(networkError)
        }
        const terminalError = await handleRefreshFailure()
        pendingRequests.forEach((cb) => cb(''))
        pendingRequests = []
        return Promise.reject(terminalError)
      } finally {
        isRefreshing = false
      }
    }

    // R25：携带错误信封的 4xx/5xx → BusinessError（透传 code/message/requestId）
    if (errStatus !== undefined) {
      if (errCode) {
        return Promise.reject(
          new BusinessError(errCode, errEnvelope?.error?.message || `请求失败（${errStatus}）`, {
            status: errStatus,
            requestId: String(error.response?.headers?.['x-request-id'] ?? '') || undefined,
          }),
        )
      }
      return Promise.reject(
        new BusinessError(`HTTP_${errStatus}`, `请求失败（${errStatus}）`, { status: errStatus }),
      )
    }
    // R25：断网/超时（无 response）→ 统一网络业务异常，结果未知，意图键保留
    return Promise.reject(new BusinessError('NETWORK_ERROR', '网络错误，请检查网络连接'))
  }
)

interface RequestConfig {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  data?: Record<string, unknown> | FormData
  header?: Record<string, string>
  timeout?: number
  /** GET 查询参数（axios params；W03 钱包游标分页等） */
  params?: Record<string, unknown>
  /**
   * R25：持久意图——资金/权益操作由页面"确认一次"后置 true。
   * 意图键不因本地 TTL 过期轮换；未知结果必须先按原键查询终态（见 getPersistedIntentKey）。
   */
  persistentIntent?: boolean
}

async function request<T = unknown>(config: RequestConfig): Promise<{ data: ApiResponse<T> }> {
  const res = await client.request<ApiResponse<T>>({
    url: config.url,
    method: config.method || 'GET',
    data: config.data,
    params: config.params,
    headers: config.header,
    timeout: config.timeout,
    persistentIntent: config.persistentIntent,
  } as unknown as InternalAxiosRequestConfig)
  return { data: res.data }
}

/**
 * R25：读取某写请求当前持久化的意图键（与拦截器同一指纹算法）。
 * 用于"扣费/发送后未知结果"场景按原键查询终态——不生成新键，不重复表达用户意图。
 * 持久意图无视 10 分钟 TTL；普通意图仅 TTL 内可恢复。
 */
export async function getPersistedIntentKey(
  method: string,
  url: string,
  data?: unknown,
  params?: Record<string, unknown>,
): Promise<string | null> {
  try {
    const storeKey = intentStoreKey({
      method,
      url,
      data,
      params,
      headers: { Authorization: `Bearer ${(await storage.getItem('access_token')) ?? ''}` },
    } as InternalAxiosRequestConfig)
    if (!storeKey) {
      return null
    }
    return parseIntentEntry(await storage.getItem(storeKey))?.key ?? null
  } catch {
    return null
  }
}

/** 终态确认后主动清除持久意图（下一次用户动作才是新意图） */
export async function clearPersistedIntent(
  method: string,
  url: string,
  data?: unknown,
  params?: Record<string, unknown>,
): Promise<void> {
  try {
    const storeKey = intentStoreKey({
      method,
      url,
      data,
      params,
      headers: { Authorization: `Bearer ${(await storage.getItem('access_token')) ?? ''}` },
    } as InternalAxiosRequestConfig)
    if (storeKey) {
      await storage.removeItem(storeKey)
    }
  } catch {
    // 存储不可用不影响主流程
  }
}

export default request
