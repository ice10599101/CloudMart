import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { createAdminFence, listAdminFences } from './wish'

/**
 * BASE-01 验收契约：温暖地图围栏以字符串携带雪花心愿 ID，
 * 列表→创建→（服务端解析为 Long）全程字节一致，禁止 Number 转换导致精度损失。
 */
describe('admin wish warm-map fences（BASE-01 大 ID 契约）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('createAdminFence() 大 ID wishId 字符串原样提交（9007199254740993 不指向错误心愿）', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await createAdminFence({
      name: '测试围栏',
      wishId: '9007199254740993',
      centerLat: 39.9,
      centerLng: 116.4,
      radiusM: 500,
    })

    expect(request.post).toHaveBeenCalledWith('/admin/wish/warm-map/fences', {
      name: '测试围栏',
      wishId: '9007199254740993',
      centerLat: 39.9,
      centerLng: 116.4,
      radiusM: 500,
    })
  })

  it('listAdminFences() 大 ID 过滤参数字符串透传', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await listAdminFences({ wishId: '9007199254740993' })

    expect(request.get).toHaveBeenCalledWith('/admin/wish/warm-map/fences', {
      params: { wishId: '9007199254740993' },
    })
  })
})
