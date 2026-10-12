import Taro from '@tarojs/taro'
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
let pendingRequests: Array<(token: string, failure?: BusinessError) => void> = []

interface RequestConfig {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  data?: Record<string, unknown>
  /** GET 查询参数（Taro.request data 在 GET 下自动拼 query） */
  params?: Record<string, unknown>
  header?: Record<string, string>
  /**
   * R25：持久意图——资金/权益操作由页面"确认一次"后置 true。
   * 意图键不因本地 TTL 过期轮换；未知结果必须先按原键查询终态（见 getPersistedIntentKey）。
   */
  persistentIntent?: boolean
  /** FE-01：刷新重放标记——重放请求若仍 401 直接终态，不再二次刷新（防循环） */
  _retried?: boolean
  /** 拦截器内部：本请求托管的意图键存储位置（调用方显式键时不存在） */
  _intentStoreKey?: string
}

// ==================== W03/FE-03/R25：持久化幂等意图键 ====================
// §8.5.2：同一意图（方法+路径+载荷+账号）的键持久化；网络重试/刷新/重启复用原键。
// 普通写意图收到终态响应即清除；资金/权益的持久意图不随 10 分钟 TTL 轮换。

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
  if (!WRITE_METHODS.includes(method as (typeof WRITE_METHODS)[number])) {
    return null
  }
  const fingerprint = hashFingerprint(
    [method, url, data ? JSON.stringify(data) : '', subjectFromToken(token)].join('|'),
  )
  return `idem:intent:${fingerprint}`
}

function generateIntentKey(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
}

/**
 * R25：读取某写请求当前持久化的意图键（与拦截器同一指纹算法）。
 * 用于"扣费/发送后未知结果"场景按原键查询终态（聊天 /pet/chat/requests/{key}、
 * 购买 /pet/purchase-requests/{key}）——不生成新键，不重复表达用户意图。
 * 持久意图无视 10 分钟 TTL；普通意图仅 TTL 内可恢复。
 */
export function getPersistedIntentKey(
  method: string,
  url: string,
  data?: unknown,
  token?: string,
): string | null {
  try {
    const storeKey = intentStoreKey(method, url, data, token ?? Taro.getStorageSync('access_token'))
    if (!storeKey) {
      return null
    }
    const entry = parseIntentEntry(Taro.getStorageSync(storeKey))
    return entry?.key ?? null
  } catch {
    return null
  }
}

/** 终态确认后主动清除持久意图（下一次用户动作才是新意图） */
export function clearPersistedIntent(method: string, url: string, data?: unknown): void {
  try {
    const storeKey = intentStoreKey(method, url, data, Taro.getStorageSync('access_token'))
    if (storeKey) {
      Taro.removeStorageSync(storeKey)
    }
  } catch {
    // 存储不可用不影响主流程
  }
}

/** 刷新失败：清凭据 + 跳登录，等待者随后统一拒绝（终态，不悬挂） */
function handleRefreshFailure(): BusinessError {
  Taro.removeStorageSync('access_token')
  Taro.removeStorageSync('refresh_token')
  Taro.redirectTo({ url: '/pages/login/index' })
  return new BusinessError('UNAUTHORIZED', '登录状态已失效，请重新登录', { status: 401 })
}

/** 从 Taro 响应头取 requestId（微信小程序响应头键名为小写） */
function requestIdOf(header: Taro.request.SuccessCallbackResult['header'] | undefined): string | undefined {
  if (!header) return undefined
  const value = header['x-request-id'] ?? header['X-Request-Id']
  return typeof value === 'string' ? value : undefined
}

function businessErrorFromEnvelope(
  statusCode: number,
  body: ApiResponse<unknown> | undefined,
  requestId: string | undefined,
): BusinessError {
  const code = body?.error?.code || `HTTP_${statusCode}`
  const message = body?.error?.message || `请求失败（${statusCode}）`
  return new BusinessError(code, message, { status: statusCode, requestId })
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
  if (WRITE_METHODS.includes(method as (typeof WRITE_METHODS)[number])) {
    // R25：调用方显式 X-Idempotency-Key 优先且不可覆盖——
    // 拦截器不重新生成、不登记存储、不参与终态清理（生命周期由调用方管理）
    if (!header['X-Idempotency-Key']) {
      const storeKey = intentStoreKey(method, config.url, config.data, token)
      if (storeKey) {
        const decision = decideIntent(
          Taro.getStorageSync(storeKey),
          Date.now(),
          config.persistentIntent === true,
          generateIntentKey,
        )
        header['X-Idempotency-Key'] = decision.key
        config._intentStoreKey = storeKey
        if (decision.action === 'create') {
          Taro.setStorageSync(storeKey, JSON.stringify(decision.entry))
        }
      }
    }
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
    // R25：断网/超时=结果未知，保留原意图键（intentTerminal(undefined,*)=false）
    throw new BusinessError('NETWORK_ERROR', '网络错误，请检查网络连接')
  }

  const requestId = requestIdOf(res.header)
  const envelope = res.data as ApiResponse<unknown> | undefined

  // 401 在业务错误判定之前处理：触发刷新重放（重放复用同一意图键）
  if (res.statusCode === 401) {
    if (config._retried) {
      // 重放后仍 401：终态拒绝，防刷新循环打爆认证接口
      throw handleRefreshFailure()
    }
    return handleRefresh<T>(config)
  }

  const errorCode = envelope?.error?.code
  const terminal = intentTerminal(res.statusCode, errorCode)
  if (config._intentStoreKey && terminal) {
    Taro.removeStorageSync(config._intentStoreKey)
  }

  // R25：业务拒绝（信封 success=false）→ 业务异常，页面在 catch 按 code/message 处理，
  // 不再允许"返回失败信封 + 页面漏判"的假成功
  if (envelope?.success === false) {
    throw new BusinessError(
      errorCode || 'BIZ_ERROR',
      envelope.error?.message || '请求失败',
      { status: res.statusCode, requestId },
    )
  }

  // R25：非信封的 4xx/5xx（网关/鉴权层裸错误等）同样抛业务异常
  if (res.statusCode >= 400) {
    throw businessErrorFromEnvelope(res.statusCode, envelope, requestId)
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
      pendingRequests.push((token: string, failure?: BusinessError) => {
        if (!token) {
          reject(failure ?? handleRefreshFailure())
          return
        }
        originalConfig.header = originalConfig.header || {}
        originalConfig.header.Authorization = `Bearer ${token}`
        resolve(request<T>(originalConfig))
      })
    })
  }

  isRefreshing = true

  let res: Awaited<ReturnType<typeof Taro.request>>
  try {
    res = await Taro.request({
      url: `${API_BASE}/auth/refresh`,
      method: 'POST',
      data: { refreshToken },
      header: { 'Content-Type': 'application/json' },
    })
  } catch {
    // 刷新请求本身断网/超时：鉴权状态未知，按网络错误终态拒绝（不强制登出）
    throw new BusinessError('NETWORK_ERROR', '网络错误，请检查网络连接')
  }
  try {
    const refreshPayload = res.data as
      | ApiResponse<{ accessToken: string; refreshToken: string }>
      | undefined
    // FE-01：刷新接口也可能返回 401（refresh token 失效）——响应路径必须检查
    if (res.statusCode !== 200 || !refreshPayload?.data) {
      throw handleRefreshFailure()
    }
    const { accessToken, refreshToken: newRefreshToken } = refreshPayload.data
    Taro.setStorageSync('access_token', accessToken)
    Taro.setStorageSync('refresh_token', newRefreshToken)

    pendingRequests.forEach((cb) => cb(accessToken))
    pendingRequests = []

    originalConfig.header = originalConfig.header || {}
    originalConfig.header.Authorization = `Bearer ${accessToken}`
    originalConfig._retried = true
    return request<T>(originalConfig)
  } catch (error) {
    // R25：刷新接口网络失败不能当鉴权失败强制登出——网络恢复后原 refresh token 仍有效；
    // 等待者以 NETWORK_ERROR 终态拒绝（不悬置、不清凭据、不跳登录）
    if (error instanceof BusinessError && error.code === 'NETWORK_ERROR') {
      pendingRequests.forEach((cb) => cb('', error))
      pendingRequests = []
      throw error
    }
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
