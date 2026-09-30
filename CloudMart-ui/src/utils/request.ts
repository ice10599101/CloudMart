import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'
import { message as staticMessage } from 'antd'
import { history } from 'umi'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { useAuthStore } from '@/stores/auth'
import { getAppMessage } from '@/utils/appMessage'
import { getDeviceId } from '@/utils/deviceFingerprint'

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
  pendingRequests: Array<(token: string) => void>
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
  refreshDomains[domain].pendingRequests.forEach((cb) => cb(''))
  refreshDomains[domain].pendingRequests = []
  // 空 token 回调已让等待者以原错误收尾；保留 cause 供调用方日志使用
  void cause
}

function isAdminRequest(url: string): boolean {
  // FE-01：/job、/gen 是管理后台页面使用的服务，必须归入管理员域取 admin token
  return (
    url.startsWith('/admin/') ||
    url.startsWith('/auth/admin/') ||
    url.startsWith('/job') ||
    url.startsWith('/gen')
  )
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

// ==================== W03/FE-03：持久化幂等意图键 ====================
// §8.5.2：用户确认一次操作生成一个键，连同 方法+路径+参数+载荷+账号 的指纹持久化；
// 网络重试、页面刷新、App 重启复用原键（服务端按 (user,endpoint,requestKey) 收敛）；
// 收到终态响应（HTTP<500，含业务拒绝）即清除——下一次用户动作就是新意图。

interface IdempotencyIntent {
  key: string
  createdAt: number
}

const INTENT_TTL_MS = 10 * 60 * 1000

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 本请求意图键的存储位置（仅拦截器生成键时存在） */
    _intentStoreKey?: string
  }
}

function hashFingerprint(input: string): string {
  let hash = 0x811c9dc5
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i)
    hash = Math.imul(hash, 0x01000193)
  }
  return `${(hash >>> 0).toString(16)}-${input.length.toString(36)}`
}

/** @return 意图键存储位置；非写请求返回 null（调用方自带键不在此管理） */
function intentStoreKey(config: AxiosRequestConfig): string | null {
  const method = (config.method ?? 'GET').toUpperCase()
  if (!['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
    return null
  }
  // FE-01/T22：指纹绑定稳定主体（JWT sub）而非 Authorization 头——token 刷新后
  // 指纹不变，重试沿用原意图键；域内无令牌（匿名）时主体为空串，同样稳定
  const domain: AuthDomain = isAdminRequest(config.url ?? '') ? 'admin' : 'user'
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

/** 复用 TTL 内的意图键，否则生成新键并持久化 */
function resolveIntentKey(storeKey: string): string {
  try {
    const raw = localStorage.getItem(storeKey)
    if (raw) {
      const entry = JSON.parse(raw) as IdempotencyIntent
      if (entry?.key && Date.now() - entry.createdAt < INTENT_TTL_MS) {
        return entry.key
      }
      localStorage.removeItem(storeKey)
    }
  } catch {
    localStorage.removeItem(storeKey)
  }
  const fresh = crypto.randomUUID()
  localStorage.setItem(storeKey, JSON.stringify({ key: fresh, createdAt: Date.now() }))
  return fresh
}

/** 终态响应（成功/业务拒绝）后清理意图：下次动作即新意图 */
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
    const url = config.url ?? ''
    const domain: AuthDomain = isAdminRequest(url) ? 'admin' : 'user'
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
    if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
      // B04：拦截器只在调用方未提供幂等键时赋值——一次用户动作一个键，
      // 双击/令牌刷新/断网重试沿用同键（调用方覆盖此头即可复用）
      if (!config.headers['X-Idempotency-Key']) {
        config.headers['X-Idempotency-Key'] = crypto.randomUUID()
      }
      // W03/FE-03：意图键持久化——断网重试/刷新/重启复用原键（服务端收敛同一笔）
      const storeKey = intentStoreKey(config)
      if (storeKey) {
        config._intentStoreKey = storeKey
        config.headers['X-Idempotency-Key'] = resolveIntentKey(storeKey)
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
    clearIntent(response.config)
    const data = response.data as ApiResponse<unknown>
    if (data.success === false) {
      const errorCode = data.error?.code ?? ''
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
    // 终态响应（HTTP<500，含业务拒绝）清理意图键；网络错误/5xx 保留原键供重试收敛
    const errorStatus = error.response?.status
    if (errorStatus && errorStatus < 500) {
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
      const domain: AuthDomain = isAdminRequest(error.config.url ?? '') ? 'admin' : 'user'
      const state = refreshDomains[domain]
      const accessTokenKey = domain === 'admin' ? 'admin_access_token' : 'access_token'
      const refreshTokenKey = domain === 'admin' ? 'admin_refresh_token' : 'refresh_token'
      const refreshTokenValue = localStorage.getItem(refreshTokenKey)

      if (refreshTokenValue) {
        if (state.isRefreshing) {
          // 入队前标记已重试：重放请求若仍 401 直接终态，不再进入下一轮刷新
          ;(error.config as { _authRetried?: boolean })._authRetried = true
          return new Promise((resolve) => {
            state.pendingRequests.push((token: string) => {
              if (!token) {
                resolve(Promise.reject(error))
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
          // FE-01：刷新失败统一拒绝所有等待者、清凭据与 store 并跳登录——
          // 任何等待中的请求都不允许悬挂（T21：refresh 401/403/500/断网均有终态）
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
    if (!isSilent(error.config as { silentError?: boolean } | undefined)) {
      // 网络级失败（断网/代理抖动）常成批出现：5 秒窗口内去重，避免 toast 刷屏
      if (error.response) {
        notify('error', error.response.data?.error?.message || '网络错误')
      } else if (!NETWORK_ERROR_NOTIFIED) {
        NETWORK_ERROR_NOTIFIED = true
        notify('error', '网络错误，请检查网络连接')
        if (networkErrorTimer) clearTimeout(networkErrorTimer)
        networkErrorTimer = setTimeout(() => {
          NETWORK_ERROR_NOTIFIED = false
        }, 5000)
      }
    }
    return Promise.reject(error)
  },
)

export default request
