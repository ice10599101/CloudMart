import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * §11/R26：RN 网络适配层测试运行器——mock 请求层，
 * 验证 petApiV2 新契约（§7.2）的 URL/方法/载荷构造与响应透传。
 */
const requestMock = vi.fn(async (config: { url: string; method?: string; data?: unknown }) => ({
  data: { success: true, data: { url: config.url, method: config.method ?? 'GET', echo: config.data } } as never,
}))

vi.mock('@/utils/request', () => ({
  default: (config: unknown) => requestMock(config as never),
}))

import { petApiV2 } from '@/api/pet'

describe('petApiV2 RN 网络适配（批次A-C2 新契约）', () => {
  beforeEach(() => {
    requestMock.mockClear()
  })

  it('bootstrap：petId 缺省不带 query', async () => {
    await petApiV2.getBootstrap()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/pet/bootstrap' }))
  })

  it('activity-center：携带 petId query', async () => {
    await petApiV2.getActivityCenter('42')
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/pet/activity-center?petId=42' }))
  })

  it('claim-batch：POST 载荷为 activityIds 数组', async () => {
    await petApiV2.claimActivitiesBatch(['a', 'b'])
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/pet/activities/claim-batch',
      method: 'POST',
      data: { activityIds: ['a', 'b'] },
    }))
  })

  it('相册 PATCH：caption/visibility/expectedVersion 载荷', async () => {
    await petApiV2.updateAlbumAsset(1, 2, { caption: '看日落', visibility: 'PUBLIC', expectedVersion: 4 })
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/pet/pets/1/album/2',
      method: 'PATCH',
      data: { caption: '看日落', visibility: 'PUBLIC', expectedVersion: 4 },
    }))
  })

  it('events status 过滤：值进 query', async () => {
    await petApiV2.listEventsByStatus('CLAIMABLE')
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/pet/events?status=CLAIMABLE' }))
  })

  it('任务集 chest：setId 编码进路径', async () => {
    await petApiV2.claimQuestChestInSet('2026-10-05')
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/pet/daily-quest-sets/2026-10-05/chest/claim',
      method: 'POST',
    }))
  })

  it('我的举报：GET /pet/reports/mine', async () => {
    await petApiV2.listMyReports()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/pet/reports/mine' }))
  })
})
