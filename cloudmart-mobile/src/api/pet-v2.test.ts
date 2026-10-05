import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * §11/R26：小程序网络适配层测试运行器——mock 请求层，
 * 验证 petApiV2 新契约（§7.2）的 URL/方法/载荷构造与响应透传。
 * Taro 依赖在 vi.mock 中桩化（node 环境无原生 Taro 运行时）。
 */
const requestMock = vi.fn(async (config: { url: string; method?: string; data?: unknown }) => ({
  data: { success: true, data: { url: config.url, method: config.method ?? 'GET', echo: config.data } } as never,
}))

vi.mock('@tarojs/taro', () => ({
  default: {
    getEnv: () => 'WEAPP',
    request: vi.fn(),
    getStorageSync: vi.fn(() => ''),
    setStorageSync: vi.fn(),
    removeStorageSync: vi.fn(),
  },
  ENV_TYPE: { WEAPP: 'WEAPP' },
}))

vi.mock('@/utils/request', () => ({
  default: (config: unknown) => requestMock(config as never),
}))

import { petApiV2 } from '@/api/pet'

describe('petApiV2 小程序网络适配（批次A-C2 新契约）', () => {
  beforeEach(() => {
    requestMock.mockClear()
  })

  it('bootstrap：petId 缺省不带 query', async () => {
    await petApiV2.getBootstrap()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/pet/bootstrap' }))
  })

  it('bootstrap：携带 petId（Taro 惯例 GET 走 data）', async () => {
    await petApiV2.getBootstrap(42)
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/pet/bootstrap',
      data: { petId: 42 },
    }))
  })

  it('claim-batch：POST 载荷为 activityIds 数组', async () => {
    await petApiV2.claimActivitiesBatch([1, 2])
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/pet/activities/claim-batch',
      method: 'POST',
      data: { activityIds: [1, 2] },
    }))
  })

  it('日记可见性 PATCH：载荷含 visibility/expectedVersion', async () => {
    await petApiV2.updateDiaryVisibility(7, 9, { visibility: 'OWNER_ONLY', expectedVersion: 3 })
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/pet/pets/7/diary/9',
      method: 'PATCH',
      data: { visibility: 'OWNER_ONLY', expectedVersion: 3 },
    }))
  })

  it('期次领取：occurrenceId 进路径且为 POST', async () => {
    await petApiV2.claimEventOccurrence('2106998087863005186')
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/pet/event-occurrences/2106998087863005186/claim',
      method: 'POST',
    }))
  })

  it('任务集路由：setId/questId 均编码', async () => {
    await petApiV2.claimQuestInSet('2026-10-05', 'daily_feed')
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/pet/daily-quest-sets/2026-10-05/quests/daily_feed/claim',
      method: 'POST',
    }))
  })

  it('聊天请求状态：键经 encodeURIComponent', async () => {
    await petApiV2.getChatRequestStatus('key with space')
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/pet/chat/requests/key%20with%20space',
    }))
  })
})
