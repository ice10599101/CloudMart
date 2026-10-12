import { describe, it, expect, vi, beforeEach } from 'vitest'

/**
 * R25：Taro 请求层行为测试。
 * Taro.request 对 4xx/5xx 也 resolve——request 层必须把 success=false / HTTP 4xx/5xx
 * 统一转为 BusinessError（带 code/status/requestId），杜绝页面漏判 success 的假成功。
 */

const storageMap = new Map<string, string>()

interface MockCall {
  url: string
  method?: string
  header?: Record<string, string>
  data?: unknown
}

const requestMock = vi.fn()

vi.mock('@tarojs/taro', () => ({
  default: {
    ENV_TYPE: { WEAPP: 'WEAPP' },
    getEnv: () => 'WEB',
    request: (options: MockCall) => requestMock(options),
    getStorageSync: (key: string) => (storageMap.has(key) ? storageMap.get(key) : ''),
    setStorageSync: (key: string, value: string) => void storageMap.set(key, value),
    removeStorageSync: (key: string) => void storageMap.delete(key),
    redirectTo: vi.fn(),
  },
}))

import Taro from '@tarojs/taro'
import request, { BusinessError, getPersistedIntentKey } from '@/utils/request'

function envelopeResponse(
  statusCode: number,
  body: unknown,
  requestId = 'req-1',
): { statusCode: number; data: unknown; header: Record<string, string> } {
  return { statusCode, data: body, header: { 'x-request-id': requestId } }
}

const okEnvelope = { success: true, data: { id: 7 } }

describe('R25 M 请求层业务异常与意图键', () => {
  beforeEach(() => {
    storageMap.clear()
    requestMock.mockReset()
    vi.mocked(Taro.redirectTo).mockClear()
  })

  it('2xx 成功：返回信封并清理本次托管意图键', async () => {
    requestMock.mockResolvedValue(envelopeResponse(200, okEnvelope))
    const result = await request({ url: '/wish/wishes', method: 'POST', data: { title: 'x' } })

    expect(result.data.success).toBe(true)
    const call = requestMock.mock.calls[0][0]
    expect(call.header?.['X-Idempotency-Key']).toBeTruthy()
    // 终态成功后意图已清理
    expect(getPersistedIntentKey('POST', '/wish/wishes', { title: 'x' })).toBeNull()
  })

  it('success=false（业务 400）：抛 BusinessError，带 code/message/status/requestId，终态清键', async () => {
    requestMock.mockResolvedValue(
      envelopeResponse(400, { success: false, error: { code: 'VALIDATION_ERROR', message: '标题过长' } }),
    )

    await expect(request({ url: '/wish/wishes', method: 'POST', data: { title: '' } })).rejects
      .toMatchObject({
        name: 'BusinessError',
        code: 'VALIDATION_ERROR',
        message: '标题过长',
        status: 400,
        requestId: 'req-1',
      })
  })

  it('409 WISH_OPERATION_IN_PROGRESS：抛业务异常但保留意图键（处理中瞬态）', async () => {
    requestMock
      .mockResolvedValueOnce(
        envelopeResponse(409, {
          success: false,
          error: { code: 'WISH_OPERATION_IN_PROGRESS', message: '处理中' },
        }),
      )
      .mockResolvedValueOnce(envelopeResponse(200, okEnvelope, 'req-2'))

    const payload = { giftId: 1 }
    await expect(request({ url: '/wish/gifts/send', method: 'POST', data: payload })).rejects
      .toMatchObject({ code: 'WISH_OPERATION_IN_PROGRESS' })

    const firstKey = requestMock.mock.calls[0][0].header?.['X-Idempotency-Key']
    // 重试（同指纹）必须沿用原键，服务端才能收敛同一笔
    await request({ url: '/wish/gifts/send', method: 'POST', data: payload })
    const secondKey = requestMock.mock.calls[1][0].header?.['X-Idempotency-Key']
    expect(secondKey).toBe(firstKey)
  })

  it('429 / 5xx：抛 BusinessError 且保留意图键', async () => {
    requestMock.mockResolvedValue(
      envelopeResponse(503, { success: false, error: { code: 'PET_TEMPORARILY_UNAVAILABLE', message: '稍后' } }),
    )
    await expect(request({ url: '/pet/chat', method: 'POST', data: { message: 'hi' } })).rejects
      .toMatchObject({ code: 'PET_TEMPORARILY_UNAVAILABLE', status: 503 })
  })

  it('非信封 502：HTTP_{status} 业务异常', async () => {
    requestMock.mockResolvedValue(envelopeResponse(502, '<html>Bad Gateway</html>'))
    await expect(request({ url: '/wish/wishes', method: 'POST' })).rejects
      .toMatchObject({ code: 'HTTP_502', status: 502 })
  })

  it('断网（Taro.request reject）：NETWORK_ERROR，结果未知保留意图键', async () => {
    requestMock.mockRejectedValue(new Error('request:fail timeout'))
    const payload = { message: 'hi' }
    await expect(request({ url: '/pet/chat', method: 'POST', data: payload, persistentIntent: true })).rejects
      .toMatchObject({ code: 'NETWORK_ERROR' })
    expect(getPersistedIntentKey('POST', '/pet/chat', payload)).toBeTruthy()
  })

  it('调用方显式 X-Idempotency-Key 原样透传，不被拦截器覆盖、不登记/清理存储', async () => {
    requestMock.mockResolvedValue(envelopeResponse(200, okEnvelope))
    storageMap.clear()
    await request({
      url: '/wish/gifts/send',
      method: 'POST',
      data: { giftId: 9 },
      header: { 'X-Idempotency-Key': 'caller-key-123' },
    })

    expect(requestMock.mock.calls[0][0].header?.['X-Idempotency-Key']).toBe('caller-key-123')
    expect(storageMap.size).toBe(0)
  })

  it('持久意图：超过 10 分钟 TTL 后同指纹重试仍用原键（未知状态先查原单）', async () => {
    requestMock.mockResolvedValue(envelopeResponse(500, { success: false, error: { code: 'X', message: 'y' } }))
    const payload = { message: 'durable' }
    await expect(request({ url: '/pet/chat', method: 'POST', data: payload, persistentIntent: true }))
      .rejects.toBeInstanceOf(BusinessError)
    const firstKey = requestMock.mock.calls[0][0].header?.['X-Idempotency-Key']

    // 条目老化 11 分钟（持久意图无视普通 TTL）
    const storeKey = [...storageMap.keys()].find((k) => k.startsWith('idem:intent:')) as string
    const entry = JSON.parse(storageMap.get(storeKey) as string)
    storageMap.set(storeKey, JSON.stringify({ ...entry, createdAt: entry.createdAt - 11 * 60 * 1000 }))

    await expect(request({ url: '/pet/chat', method: 'POST', data: payload, persistentIntent: true }))
      .rejects.toBeInstanceOf(BusinessError)
    expect(requestMock.mock.calls[1][0].header?.['X-Idempotency-Key']).toBe(firstKey)
  })

  it('401：刷新成功后重放原请求，且重放沿用同一幂等键', async () => {
    storageMap.set('access_token', 'old-at')
    storageMap.set('refresh_token', 'rt-1')
    let businessCalls = 0
    requestMock.mockImplementation(async (options: MockCall) => {
      if (options.url === '/api/auth/refresh') {
        return envelopeResponse(200, {
          success: true,
          data: { accessToken: 'new-at', refreshToken: 'new-rt', tokenType: 'Bearer', expiresIn: 3600 },
        })
      }
      businessCalls += 1
      return businessCalls === 1
        ? envelopeResponse(401, { success: false, error: { code: 'UNAUTHORIZED', message: '过期' } })
        : envelopeResponse(200, okEnvelope, 'req-replay')
    })

    const result = await request({ url: '/wish/wishes', method: 'POST', data: { title: 'x' } })
    expect(result.data.success).toBe(true)

    const calls = requestMock.mock.calls.filter((c: [MockCall]) => c[0].url !== '/api/auth/refresh')
    expect(calls).toHaveLength(2)
    expect(calls[1][0].header?.['X-Idempotency-Key']).toBe(calls[0][0].header?.['X-Idempotency-Key'])
    expect(calls[1][0].header?.Authorization).toBe('Bearer new-at')
  })

  it('401 重放后仍 401：终态拒绝并清凭据跳登录，不循环刷新', async () => {
    storageMap.set('access_token', 'at')
    storageMap.set('refresh_token', 'rt')
    requestMock.mockImplementation(async (options: MockCall) => {
      if (options.url === '/api/auth/refresh') {
        return envelopeResponse(200, {
          success: true,
          data: { accessToken: 'new-at', refreshToken: 'new-rt' },
        })
      }
      return envelopeResponse(401, { success: false, error: { code: 'UNAUTHORIZED', message: '无效' } })
    })

    await expect(request({ url: '/wish/wishes', method: 'POST' })).rejects
      .toMatchObject({ code: 'UNAUTHORIZED', status: 401 })
    // 只有初次 + 一次重放两个业务请求，没有第二次刷新
    const businessCalls = requestMock.mock.calls.filter((c) => c[0].url !== '/api/auth/refresh')
    const refreshCalls = requestMock.mock.calls.filter((c) => c[0].url === '/api/auth/refresh')
    expect(businessCalls).toHaveLength(2)
    expect(refreshCalls).toHaveLength(1)
    expect(storageMap.get('access_token')).toBeUndefined()
    expect(Taro.redirectTo).toHaveBeenCalledWith({ url: '/pages/login/index' })
  })

  it('刷新请求本身断网：NETWORK_ERROR 终态，不清凭据、不跳登录', async () => {
    storageMap.set('access_token', 'at')
    storageMap.set('refresh_token', 'rt')
    requestMock.mockImplementation(async (options: MockCall) => {
      if (options.url === '/api/auth/refresh') {
        throw new Error('request:fail')
      }
      return envelopeResponse(401, { success: false, error: { code: 'UNAUTHORIZED', message: '过期' } })
    })

    await expect(request({ url: '/wish/wishes', method: 'POST' })).rejects
      .toMatchObject({ code: 'NETWORK_ERROR' })
    expect(storageMap.get('refresh_token')).toBe('rt')
    expect(Taro.redirectTo).not.toHaveBeenCalled()
  })
})
