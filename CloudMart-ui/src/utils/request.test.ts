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

  it('ADMIN-04：adminAuth 显式管理员域——后台直传 /file/assets 使用 admin token', () => {
    localStorage.setItem('admin_access_token', 'admin-token')
    localStorage.setItem('access_token', 'user-token')

    const config = runRequestInterceptor({ url: '/file/assets', method: 'post', adminAuth: true })

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

  it('success=false 且 silentError：不弹全局错误，仅透传业务码', async () => {
    const response = {
      data: { success: false, error: { code: 'RESOURCE_NOT_FOUND', message: '资源不存在' } },
      config: { silentError: true },
    }

    await expect(responseFulfilled(response)).rejects.toMatchObject({ code: 'RESOURCE_NOT_FOUND' })
    expect(message.error).not.toHaveBeenCalled()
  })

  it('success=true 正常放行', async () => {
    const response = { data: { success: true, data: { id: 1 } } }

    const result = await responseFulfilled(response)

    expect(result).toEqual(response)
  })
})

// ==================== FE-01：分域刷新状态机 ====================

/** 构造可被 jwtSubject 解析的假 JWT（payload.sub 可控） */
function fakeJwt(sub: string): string {
  const encode = (obj: unknown) => window.btoa(JSON.stringify(obj)).replace(/=/g, '')
  return `${encode({ alg: 'RS256' })}.${encode({ sub })}.sig`
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const responseRejected: (error: unknown) => Promise<unknown> = chains.response.handlers[0].rejected

describe('FE-01 身份域选择与 403 规则', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('admin 前缀之外，/job 同样归管理员域取 admin token', () => {
    localStorage.setItem('admin_access_token', 'admin-token')
    localStorage.setItem('access_token', 'user-token')

    const jobConfig = runRequestInterceptor({ url: '/job/list', method: 'get' })

    expect(jobConfig.headers.Authorization).toBe('Bearer admin-token')
  })

  it('403 携带业务码直接透传，不触发刷新流程', async () => {
    const error = {
      config: { url: '/admin/users', headers: {} },
      response: {
        status: 403,
        headers: { 'x-request-id': 'req-403' },
        data: { error: { code: 'FORBIDDEN', message: '没有操作权限' } },
      },
    }

    await expect(responseRejected(error)).rejects.toMatchObject({
      code: 'FORBIDDEN',
      status: 403,
      requestId: 'req-403',
    })
  })

  it('403 无业务码原样拒绝，同样不触发刷新', async () => {
    const error = {
      config: { url: '/admin/users', headers: {} },
      response: { status: 403, data: {} },
    }

    await expect(responseRejected(error)).rejects.toBe(error)
  })
})

describe('FE-01 意图键绑定稳定主体（T22）', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('token 刷新（同主体换令牌）后同一写操作复用原意图键', () => {
    vi.stubGlobal('crypto', { randomUUID: () => 'stable-intent-key' })
    localStorage.setItem('access_token', fakeJwt('42'))

    const before = runRequestInterceptor({ url: '/order/v2/orders', method: 'post', data: { quoteId: '1' } })

    // 模拟刷新：换了新 access token（签名段不同），但 sub 仍是 42
    const encode = (obj: unknown) => window.btoa(JSON.stringify(obj)).replace(/=/g, '')
    localStorage.setItem('access_token', `${encode({ alg: 'RS256' })}.${encode({ sub: '42' })}.refreshed-sig`)

    const after = runRequestInterceptor({ url: '/order/v2/orders', method: 'post', data: { quoteId: '1' } })

    expect(before.headers['X-Idempotency-Key']).toBe('stable-intent-key')
    expect(after.headers['X-Idempotency-Key']).toBe('stable-intent-key')
  })

  it('不同主体的同一写操作使用不同意图键', () => {
    vi.stubGlobal('crypto', { randomUUID: () => 'per-subject-key' })
    localStorage.setItem('access_token', fakeJwt('42'))

    const userA = runRequestInterceptor({ url: '/order/v2/orders', method: 'post', data: {} })

    localStorage.clear()
    localStorage.setItem('access_token', fakeJwt('43'))

    const userB = runRequestInterceptor({ url: '/order/v2/orders', method: 'post', data: {} })

    expect(userA.headers['X-Idempotency-Key']).toBe('per-subject-key')
    expect(userB.headers['X-Idempotency-Key']).toBe('per-subject-key')
    // 同键不同主体必须不同存储位（指纹含主体），互不覆盖
    expect(userA._intentStoreKey).not.toBe(userB._intentStoreKey)
  })
})

describe('R25 显式幂等键优先且不可覆盖', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('调用方自带 X-Idempotency-Key 原样透传，拦截器不覆盖/不登记存储', () => {
    vi.stubGlobal('crypto', { randomUUID: () => 'should-not-be-used' })

    const config = runRequestInterceptor({
      url: '/wish/gifts/send',
      method: 'post',
      data: { giftId: 1 },
      headers: { 'X-Idempotency-Key': 'caller-explicit-key' },
    })

    expect(config.headers['X-Idempotency-Key']).toBe('caller-explicit-key')
    expect(config._intentStoreKey).toBeUndefined()
    // 仅设备指纹等非意图存储，不登记任何 idem:intent
    expect(Object.keys(localStorage).filter((k) => k.startsWith('idem:intent:'))).toHaveLength(0)
  })

  it('persistentIntent：超过 10 分钟后同指纹请求仍复用原键（未知状态先查原单）', () => {
    vi.stubGlobal('crypto', { randomUUID: () => 'durable-key' })
    const first = runRequestInterceptor({
      url: '/pet/chat',
      method: 'post',
      data: { message: 'hi' },
      persistentIntent: true,
    })
    expect(first.headers['X-Idempotency-Key']).toBe('durable-key')
    const storeKey = first._intentStoreKey as string

    // 模拟超过普通意图 TTL（不用 fake timers，直接改写条目的 createdAt）
    const raw = JSON.parse(localStorage.getItem(storeKey) as string)
    localStorage.setItem(storeKey, JSON.stringify({ ...raw, createdAt: raw.createdAt - 11 * 60 * 1000 }))

    vi.stubGlobal('crypto', { randomUUID: () => 'should-rotate' })
    const second = runRequestInterceptor({ url: '/pet/chat', method: 'post', data: { message: 'hi' } })
    expect(second.headers['X-Idempotency-Key']).toBe('durable-key')
  })

  it('同用户两次明确不同购买（不同载荷）得到不同键', () => {
    let seq = 0
    vi.stubGlobal('crypto', { randomUUID: () => `key-${seq++}` })

    const buyA = runRequestInterceptor({ url: '/order/v2/orders', method: 'post', data: { sku: 'A' } })
    const buyB = runRequestInterceptor({ url: '/order/v2/orders', method: 'post', data: { sku: 'B' } })

    expect(buyA.headers['X-Idempotency-Key']).not.toBe(buyB.headers['X-Idempotency-Key'])
    expect(buyA._intentStoreKey).not.toBe(buyB._intentStoreKey)
  })
})

describe('R25 瞬态业务错误保留意图键', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('409 WISH_OPERATION_IN_PROGRESS：拒绝但不清意图键', async () => {
    const storeKey = 'idem:intent:test'
    localStorage.setItem(storeKey, JSON.stringify({ key: 'wish-key', createdAt: Date.now() }))
    const error = {
      config: { _intentStoreKey: storeKey },
      response: {
        status: 409,
        headers: {},
        data: { success: false, error: { code: 'WISH_OPERATION_IN_PROGRESS', message: '处理中' } },
      },
    }

    await expect(responseRejected(error)).rejects.toMatchObject({ code: 'WISH_OPERATION_IN_PROGRESS' })
    expect(localStorage.getItem(storeKey)).not.toBeNull()
  })

  it('409 IDEMPOTENT_ERROR（通用切面重复请求）：同样保留意图键', async () => {
    const storeKey = 'idem:intent:test2'
    localStorage.setItem(storeKey, JSON.stringify({ key: 'idem-key', createdAt: Date.now() }))
    const error = {
      config: { _intentStoreKey: storeKey },
      response: {
        status: 409,
        headers: {},
        data: { success: false, error: { code: 'IDEMPOTENT_ERROR', message: '重复请求' } },
      },
    }

    await expect(responseRejected(error)).rejects.toMatchObject({ code: 'IDEMPOTENT_ERROR' })
    expect(localStorage.getItem(storeKey)).not.toBeNull()
  })

  it('400 确定性业务拒绝：终态清键', async () => {
    const storeKey = 'idem:intent:test3'
    localStorage.setItem(storeKey, JSON.stringify({ key: 'terminal-key', createdAt: Date.now() }))
    const response = {
      status: 400,
      data: { success: false, error: { code: 'VALIDATION_ERROR', message: '参数错误' } },
      config: { _intentStoreKey: storeKey },
    }

    await expect(responseFulfilled(response)).rejects.toMatchObject({ code: 'VALIDATION_ERROR' })
    expect(localStorage.getItem(storeKey)).toBeNull()
  })
})
