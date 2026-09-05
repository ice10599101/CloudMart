import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

vi.mock('antd', () => ({
  message: { error: vi.fn(), warning: vi.fn() },
}))

vi.mock('umi', () => ({
  history: { push: vi.fn(), replace: vi.fn() },
}))

vi.mock('@/stores/adminAuth', () => ({
  useAdminAuthStore: {
    setState: vi.fn(),
    getState: vi.fn(() => ({ accessToken: '', refreshToken: '', adminInfo: null, permissions: [], roles: [] })),
  },
}))

import request from './request'
import { message } from 'antd'

// 直接调用真实 axios 实例的拦截器处理函数，避免在测试里复制拦截器逻辑造成与源码脱节
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const chains = request.interceptors as any
const requestFulfilled: (config: Record<string, unknown>) => Record<string, unknown> =
  chains.request.handlers[0].fulfilled
const responseFulfilled: (response: unknown) => Promise<unknown> =
  chains.response.handlers[0].fulfilled

interface MockConfig {
  headers: Record<string, string>
  [key: string]: unknown
}

function runRequestInterceptor(config: Partial<MockConfig>): MockConfig {
  return requestFulfilled({ headers: {}, ...config }) as MockConfig
}

describe('request 请求拦截器（真实实例）', () => {
  const originalCrypto = globalThis.crypto

  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  afterEach(() => {
    // 只恢复 crypto，不能用 vi.unstubAllGlobals()——会连带清掉 test-setup.ts 注入的 localStorage mock
    Object.defineProperty(globalThis, 'crypto', { value: originalCrypto, configurable: true })
  })

  it('非公开路径 GET 携带用户 token', () => {
    localStorage.setItem('access_token', 'user-token')

    const config = runRequestInterceptor({ url: '/order/orders', method: 'get' })

    expect(config.headers.Authorization).toBe('Bearer user-token')
  })

  it('admin 路径使用 admin_access_token', () => {
    localStorage.setItem('admin_access_token', 'admin-token')
    localStorage.setItem('access_token', 'user-token')

    const config = runRequestInterceptor({ url: '/admin/users', method: 'get' })

    expect(config.headers.Authorization).toBe('Bearer admin-token')
  })

  it('已登录时公开路径 GET 也携带 token（BUG-A 回归：路径公开语义私有的心愿接口需身份头）', () => {
    localStorage.setItem('access_token', 'user-token')

    const config = runRequestInterceptor({ url: '/wish/wishes', method: 'get' })

    expect(config.headers.Authorization).toBe('Bearer user-token')
  })

  it('公开路径写操作（发布心愿 POST /wish/wishes）必须携带 token 与幂等键', () => {
    // 回归用例：此前公开路径跳过所有身份头，导致已登录用户发布心愿报 UNAUTHORIZED
    vi.stubGlobal('crypto', { randomUUID: () => 'fixed-uuid' })
    localStorage.setItem('access_token', 'user-token')

    const config = runRequestInterceptor({ url: '/wish/wishes', method: 'post' })

    expect(config.headers.Authorization).toBe('Bearer user-token')
    expect(config.headers['X-Idempotency-Key']).toBe('fixed-uuid')
  })

  it('登录态专属心愿接口（/wish/wishes/my）即使 GET 也携带 token', () => {
    localStorage.setItem('access_token', 'user-token')

    const config = runRequestInterceptor({ url: '/wish/wishes/my', method: 'get' })

    expect(config.headers.Authorization).toBe('Bearer user-token')
  })

  it('未登录时公开路径 POST 不携带 token（匿名允许失败由后端判定）', () => {
    vi.stubGlobal('crypto', { randomUUID: () => 'fixed-uuid' })

    const config = runRequestInterceptor({ url: '/wish/wishes', method: 'post' })

    expect(config.headers.Authorization).toBeUndefined()
    expect(config.headers['X-Idempotency-Key']).toBe('fixed-uuid')
  })

  it('作者私有 GET（/wish/wishes/{id}/checkins）携带 token（BUG-A 回归）', () => {
    localStorage.setItem('access_token', 'user-token')

    const config = runRequestInterceptor({
      url: '/wish/wishes/2096298677604798465/checkins',
      method: 'get',
    })

    expect(config.headers.Authorization).toBe('Bearer user-token')
  })
})

describe('request 响应拦截器（信封处理，真实实例）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('success=false 且 UNAUTHORIZED：透传业务码，不弹全局错误', async () => {
    const response = { data: { success: false, error: { code: 'UNAUTHORIZED', message: '请先登录' } } }

    await expect(responseFulfilled(response)).rejects.toMatchObject({ code: 'UNAUTHORIZED' })
    expect(message.error).not.toHaveBeenCalled()
  })

  it('success=false 普通业务错误：弹全局错误并透传业务码', async () => {
    const response = { data: { success: false, error: { code: 'WISH_VALIDATION_ERROR', message: '标题过长' } } }

    await expect(responseFulfilled(response)).rejects.toMatchObject({ code: 'WISH_VALIDATION_ERROR' })
    expect(message.error).toHaveBeenCalledWith('标题过长')
  })

  it('success=true 正常放行', async () => {
    const response = { data: { success: true, data: { id: 1 } } }

    const result = await responseFulfilled(response)

    expect(result).toEqual(response)
  })
})
