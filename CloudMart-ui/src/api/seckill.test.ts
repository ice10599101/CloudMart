import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { listActivities, getActivity, listProductsByActivity, executeSeckill, getSeckillResult } from './seckill'

describe('seckill API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listActivities() calls GET /seckill/activities with status', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listActivities('ACTIVE')

    expect(request.get).toHaveBeenCalledWith('/seckill/activities', { params: { status: 'ACTIVE' } })
  })

  it('listActivities() calls GET /seckill/activities without status', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listActivities()

    expect(request.get).toHaveBeenCalledWith('/seckill/activities', { params: { status: undefined } })
  })

  it('getActivity() calls GET /seckill/activities/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getActivity(1)

    expect(request.get).toHaveBeenCalledWith('/seckill/activities/1')
  })

  it('listProductsByActivity() calls GET /seckill/products/activity/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listProductsByActivity(1)

    expect(request.get).toHaveBeenCalledWith('/seckill/products/activity/1')
  })

  it('executeSeckill() calls POST /seckill/execute with data', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await executeSeckill(1, 2)

    expect(request.post).toHaveBeenCalledWith('/seckill/execute', { activityId: 1, seckillProductId: 2 })
  })

  it('getSeckillResult() calls GET /seckill/result with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getSeckillResult(1, 2)

    expect(request.get).toHaveBeenCalledWith('/seckill/result', { params: { activityId: 1, seckillProductId: 2 } })
  })
})
