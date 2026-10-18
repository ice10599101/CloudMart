import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'
import { message as staticMessage } from 'antd'
import { history } from 'umi'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { useAuthStore } from '@/stores/auth'
import { getAppMessage } from '@/utils/appMessage'
import { getDeviceId } from '@/utils/deviceFingerprint'
import {
  WRITE_METHODS,
  hashFingerprint,
  decideIntent,
  intentTerminal,
  parseIntentEntry,
} from '@/utils/idempotency'

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

// ==================== FE-01：分身份域刷新状态机 ====================
// 用户域与管理员域各自独立维护 isRefreshing + 等待队列：一边 token 过期
// 触发刷新时，另一边的 401 互不阻塞、互不串 token（T21：两域并发 401 各刷一次）。

type AuthDomain = 'user' | 'admin'

interface RefreshDomainState {
  isRefreshing: boolean
  pendingRequests: Array<(token: string, failure?: Error) => void>
}

const refreshDomains: Record<AuthDomain, RefreshDomainState> = {
  user: { isRefreshing: false, pendingRequests: [] },
  admin: { isRefreshing: false, pendingRequests: [] },
}

const SERVICE_UNAVAILABLE_CODES = new Set<string>()
let serviceUnavailableTimer: ReturnType<typeof setTimeout> | null = null
let NETWORK_ERROR_NOTIFIED = false
let networkErrorTimer: ReturnType<typeof setTimeout> | null = null

function processPendingRequests(domain: AuthDomain, token: string) {
  refreshDomains[domain].pendingRequests.forEach((cb) => cb(token))
  refreshDomains[domain].pendingRequests = []
}

function rejectPendingRequests(domain: AuthDomain, cause: unknown) {
  const failure = cause instanceof Error ? cause : undefined
  refreshDomains[domain].pendingRequests.forEach((cb) => cb('', failure))
  refreshDomains[domain].pendingRequests = []
}

/** 管理员域判定：显式 adminAuth（后台直传接口）或路径前缀命中 */
function isAdminDomain(config: { url?: string; adminAuth?: boolean }): boolean {
  return config.adminAuth === true || isAdminRequest(config.url ?? '')
}

function isAdminRequest(url: string): boolean {
  // FE-01：/job 是管理后台页面使用的服务，必须归入管理员域取 admin token
  // T09：/gen 已随代码生成器退役删除，不再作为管理员域前缀
  return url.startsWith('/admin/') || url.startsWith('/auth/admin/') || url.startsWith('/job')
}

/** 解码 JWT payload 的 sub（稳定 subjectId），不引依赖；非法令牌返回空串 */
function jwtSubject(token: string | null | undefined): string {
  if (!token) return ''
  const parts = token.split('.')
  if (parts.length !== 3) return ''
  try {
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))) as {
      sub?: string
    }
    return payload.sub ?? ''
  } catch {
    return ''
  }
}

/** 构造携带业务错误码的 Error（组件需按 code 区分 402/409/429 等场景） */
function toBusinessError(
  code: string,
  messageText: string,
  extra?: { status?: number; requestId?: string },
): Error & { code: string; status?: number; requestId?: string } {
  const error = new Error(messageText) as Error & { code: string; status?: number; requestId?: string }
  error.code = code
  if (extra?.status !== undefined) error.status = extra.status
  if (extra?.requestId) error.requestId = extra.requestId
  return error
}

/** 从响应中提取 requestId（X-Request-Id）用于问题追踪 */
function requestIdOf(response: { headers?: Record<string, unknown> } | undefined): string | undefined {
  const headers = response?.headers
  if (!headers) return undefined
  const value = headers['x-request-id'] ?? headers['X-Request-Id']
  return typeof value === 'string' ? value : undefined
}

/** 请求级静默开关：可选型请求（如数据面板）失败时由组件自行兜底，不弹全局错误提示 */
declare module 'axios' {
  export interface AxiosRequestConfig {
    silentError?: boolean
  }
}

function isSilent(config: { silentError?: boolean } | undefined): boolean {
  return config?.silentError === true
}

// ==================== W03/FE-03/R25：持久化幂等意图键 ====================
// §8.5.2：用户确认一次操作生成一个键，连同 方法+路径+参数+载荷+账号 的指纹持久化；
// 网络重试、页面刷新、App 重启复用原键（服务端按 (user,endpoint,requestKey) 收敛）；
// 普通写意图收到终态响应即清除；资金/权益操作用 persistentIntent 创建持久意图，
// 不随本地 TTL 轮换，未知状态先查原单。

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 本请求意图键的存储位置（仅拦截器生成键时存在） */
    _intentStoreKey?: string
    /**
     * R25：持久意图——资金/权益操作由页面"确认一次"后置 true，
     * 意图键不因本地 TTL 过期轮换（未知结果先按原键查询终态）
     */
    persistentIntent?: boolean
    /**
     * ADMIN-04：显式管理员令牌域——后台直传 /file/assets 等非 /admin 前缀接口
     * 必须用管理员令牌（否则管理员单登录失败、双登录时资产误归用户）
     */
    adminAuth?: boolean
  }
}

/** @return 意图键存储位置；非写请求返回 null（调用方自带键不在此管理） */
function intentStoreKey(config: AxiosRequestConfig): string | null {
  const method = (config.method ?? 'GET').toUpperCase()
  if (!WRITE_METHODS.includes(method as (typeof WRITE_METHODS)[number])) {
    return null
  }
  // FE-01/T22：指纹绑定稳定主体（JWT sub）而非 Authorization 头——token 刷新后
  // 指纹不变，重试沿用原意图键；域内无令牌（匿名）时主体为空串，同样稳定
  const domain: AuthDomain = isAdminDomain(config) ? 'admin' : 'user'
  const token = localStorage.getItem(domain === 'admin' ? 'admin_access_token' : 'access_token')
  const subject = jwtSubject(token)
  const fingerprint = hashFingerprint(
    [
      method,
      config.url ?? '',
      config.params ? JSON.stringify(config.params) : '',
      config.data ? JSON.stringify(config.data) : '',
      domain,
      subject,
    ].join('|'),
  )
  return `idem:intent:${fingerprint}`
}

/** 终态响应（成功/业务拒绝）后清理托管意图：下次动作即新意图；显式键不经手 */
function clearIntent(config: AxiosRequestConfig | undefined): void {
  const storeKey = config?._intentStoreKey
  if (storeKey) {
    localStorage.removeItem(storeKey)
  }
}

// 已登录则一律附带身份头：公开接口带 token 无害（服务端忽略或用于个性化），
// 而心愿宇宙存在大量「路径公开、语义私有」的 GET（checkins/fulfillment/tree-hole 等），
// 若按前缀跳过会导致这些接口缺身份头而 401。token 过期由响应拦截器的刷新流程自愈。
request.interceptors.request.use(
  (config) => {
    const domain: AuthDomain = isAdminDomain(config) ? 'admin' : 'user'
    const tokenKey = domain === 'admin' ? 'admin_access_token' : 'access_token'
    const token = localStorage.getItem(tokenKey)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // GET 参数净化：ProTable 首载/未填搜索项会把 undefined/null 序列化进 query，
    // 经后端 Map<String,Object> 代理原样转发（like '%undefined%'）导致列表恒空
    if (config.params && typeof config.params === 'object') {
      config.params = Object.fromEntries(
        Object.entries(config.params).filter(
          ([, value]) => value !== undefined && value !== null && value !== '',
        ),
      )
    }

    // 设备指纹风控基线（规格 1188-1191）
    config.headers['X-Device-Id'] = getDeviceId()

    const method = (config.method ?? 'GET').toUpperCase()
    if (WRITE_METHODS.includes(method as (typeof WRITE_METHODS)[number])) {
      // R25：调用方显式 X-Idempotency-Key 优先且不可覆盖——拦截器不重新生成、
      // 不登记存储、不参与终态清理（生命周期完全由调用方管理）
      if (!config.headers['X-Idempotency-Key']) {
        const storeKey = intentStoreKey(config)
        if (storeKey) {
          const decision = decideIntent(
            localStorage.getItem(storeKey),
            Date.now(),
            config.persistentIntent === true,
            () => crypto.randomUUID(),
          )
          config.headers['X-Idempotency-Key'] = decision.key
          config._intentStoreKey = storeKey
          if (decision.action === 'create') {
            localStorage.setItem(storeKey, JSON.stringify(decision.entry))
          }
        }
      }
    }

    return config
  },
  (error) => Promise.reject(error),
)

function clearDomainCredentials(domain: AuthDomain) {
  if (domain === 'admin') {
    localStorage.removeItem('admin_access_token')
    localStorage.removeItem('admin_refresh_token')
    useAdminAuthStore.setState({ accessToken: '', refreshToken: '', adminInfo: null, permissions: [], roles: [] })
    history.replace('/admin/login')
    return
  }
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  // FE-01：用户域同步清 zustand store——依赖 isAuthenticated 的布局/WS 立即感知登出
  useAuthStore.setState({ accessToken: '', refreshToken: '', isAuthenticated: false, user: null })
  const currentPath = window.location.pathname
  if (currentPath !== '/login' && currentPath !== '/register') {
    sessionStorage.setItem('login_redirect', currentPath)
  }
  history.replace('/login')
}

request.interceptors.response.use(
  (response) => {
    const data = response.data as ApiResponse<unknown>
    if (data.success === false) {
      // R10：业务拒绝是终态（参数/权限/不存在等确定性失败）——清键；
      // 瞬态错误码（处理中/结算中/限流/503）保留原键供重试收敛
      const errorCode = data.error?.code ?? ''
      if (intentTerminal(response.status, errorCode)) {
        clearIntent(response.config)
      }
      const businessError = toBusinessError(errorCode, data.error?.message || '请求失败', {
        status: response.status,
        requestId: requestIdOf(response),
      })
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
      if (isSilent(response.config as { silentError?: boolean })) {
        return Promise.reject(businessError)
      }
      notify('error', data.error?.message || '请求失败')
      return Promise.reject(businessError)
    }
    return response
  },
  async (error) => {
    // R10：终态按业务 outcome——瞬态（5xx/429/409处理中/结算中/限流）保留原键
    const errorStatus = error.response?.status as number | undefined
    const errCode = (error.response?.data as { error?: { code?: string } } | undefined)?.error?.code
    if (intentTerminal(errorStatus, errCode)) {
      clearIntent(error.config)
    }
    // FE-01：只有 401 才触发刷新；403 是「已认证但无权限」，刷新无济于事（T21 规则）
    if (errorStatus === 403) {
      const errCode = error.response?.data?.error?.code as string | undefined
      if (errCode) {
        return Promise.reject(
          toBusinessError(errCode, error.response?.data?.error?.message || '请求失败', {
            status: 403,
            requestId: requestIdOf(error.response),
          }),
        )
      }
      return Promise.reject(error)
    }
    if (errorStatus === 401) {
      // 业务错误（携带 error.code，如 TOKEN_REUSE_DETECTED）直接透传，
      // 不触发 token refresh：refresh 后仍会失败，既浪费也会造成组件拿不到业务码
      const errCode = error.response?.data?.error?.code as string | undefined
      if (errCode && errCode !== 'UNAUTHORIZED') {
        return Promise.reject(
          toBusinessError(errCode, error.response?.data?.error?.message || '请求失败', {
            status: 401,
            requestId: requestIdOf(error.response),
          }),
        )
      }
      // 防循环：refresh 重放后仍 401 的请求不再触发下一轮 refresh，
      // 避免无限循环打爆认证接口限流并导致强制登出
      if ((error.config as { _authRetried?: boolean } | undefined)?._authRetried) {
        return Promise.reject(toBusinessError('UNAUTHORIZED', '登录状态已失效', { status: 401 }))
      }
      const domain: AuthDomain = isAdminDomain(error.config) ? 'admin' : 'user'
      const state = refreshDomains[domain]
      const accessTokenKey = domain === 'admin' ? 'admin_access_token' : 'access_token'
      const refreshTokenKey = domain === 'admin' ? 'admin_refresh_token' : 'refresh_token'
      const refreshTokenValue = localStorage.getItem(refreshTokenKey)

      if (refreshTokenValue) {
        if (state.isRefreshing) {
          // 入队前标记已重试：重放请求若仍 401 直接终态，不再进入下一轮刷新
          ;(error.config as { _authRetried?: boolean })._authRetried = true
          return new Promise((resolve) => {
            state.pendingRequests.push((token: string, failure?: Error) => {
              if (!token) {
                resolve(Promise.reject(failure ?? error))
                return
              }
              error.config.headers.Authorization = `Bearer ${token}`
              resolve(request(error.config))
            })
          })
        }
        state.isRefreshing = true
        try {
          const refreshUrl = domain === 'admin' ? '/api/auth/admin/refresh' : '/api/auth/refresh'
          const { data } = await axios.post(refreshUrl, {
            refreshToken: refreshTokenValue,
          })
          localStorage.setItem(accessTokenKey, data.data.accessToken)
          localStorage.setItem(refreshTokenKey, data.data.refreshToken)
          if (domain === 'admin') {
            useAdminAuthStore.setState({ accessToken: data.data.accessToken, refreshToken: data.data.refreshToken })
          } else {
            // 同步 zustand store：UserLayout 等依赖 accessToken 的 WS 连接才能用新 token 重连
            useAuthStore.setState({ accessToken: data.data.accessToken, refreshToken: data.data.refreshToken, isAuthenticated: true })
          }
          processPendingRequests(domain, data.data.accessToken)
          error.config.headers.Authorization = `Bearer ${data.data.accessToken}`
          ;(error.config as { _authRetried?: boolean })._authRetried = true
          return request(error.config)
        } catch (refreshError) {
          // R25：刷新请求本身断网（无 HTTP 响应）=鉴权状态未知，不杀会话；
          // 等待者以 NETWORK_ERROR 终态拒绝，网络恢复后凭原 refresh token 重试
          const networkDuringRefresh = !(refreshError as { response?: unknown })?.response
          if (networkDuringRefresh) {
            const networkError = toBusinessError('NETWORK_ERROR', '网络错误，请检查网络连接')
            state.pendingRequests.forEach((cb) => cb('', networkError))
            state.pendingRequests = []
            return Promise.reject(networkError)
          }
          // FE-01：刷新被明确拒绝（401/403）——清凭据与 store 并跳登录，等待者统一终态
          clearDomainCredentials(domain)
          rejectPendingRequests(domain, refreshError)
          return Promise.reject(
            toBusinessError('UNAUTHORIZED', '登录状态已失效，请重新登录', {
              status: 401,
            }),
          )
        } finally {
          state.isRefreshing = false
        }
      } else {
        clearDomainCredentials(domain)
        return Promise.reject(error)
      }
    }
    // R25：其余 4xx/5xx/网络错误统一规范化为业务异常（三端一致：code/status/requestId），
    // 页面不再需要 axios 的 err.response.data.error 形状
    const silent = isSilent(error.config as { silentError?: boolean } | undefined)
    if (error.response) {
      const envelopeError = error.response.data?.error as { code?: string; message?: string } | undefined
      const normalized = toBusinessError(
        envelopeError?.code || `HTTP_${errorStatus ?? 0}`,
        envelopeError?.message || '网络错误',
        { status: errorStatus, requestId: requestIdOf(error.response) },
      )
      if (!silent) {
        notify('error', normalized.message)
      }
      return Promise.reject(normalized)
    }
    if (!silent && !NETWORK_ERROR_NOTIFIED) {
      // 网络级失败（断网/代理抖动）常成批出现：5 秒窗口内去重，避免 toast 刷屏
      NETWORK_ERROR_NOTIFIED = true
      notify('error', '网络错误，请检查网络连接')
      if (networkErrorTimer) clearTimeout(networkErrorTimer)
      networkErrorTimer = setTimeout(() => {
        NETWORK_ERROR_NOTIFIED = false
      }, 5000)
    }
    return Promise.reject(toBusinessError('NETWORK_ERROR', '网络错误，请检查网络连接'))
  },
)

/**
 * PET-23/PET-12/R25：读取某写请求当前持久化的意图幂等键（与拦截器同一指纹算法）。
 * 用于"扣费/发送后未知结果"场景按原键查询终态（聊天 /pet/chat/requests/{requestKey}、
 * 购买 /pet/purchase-requests/{requestKey}）——不生成新键，不重复表达用户意图。
 * 持久意图无视 10 分钟 TTL；普通意图仅 TTL 内可恢复。
 *
 * @return 在途意图键；无记录返回 null
 */
export function getPersistedIntentKey(
  method: string,
  url: string,
  data?: unknown,
  params?: Record<string, unknown>,
): string | null {
  try {
    const storeKey = intentStoreKey({ method, url, data, params })
    if (!storeKey) {
      return null
    }
    return parseIntentEntry(localStorage.getItem(storeKey))?.key ?? null
  } catch {
    return null
  }
}

/** 终态确认后主动清除持久意图（下一次用户动作才是新意图） */
export function clearPersistedIntent(
  method: string,
  url: string,
  data?: unknown,
  params?: Record<string, unknown>,
): void {
  try {
    const storeKey = intentStoreKey({ method, url, data, params })
    if (storeKey) {
      localStorage.removeItem(storeKey)
    }
  } catch {
    // 存储不可用不影响主流程
  }
}

export default request
