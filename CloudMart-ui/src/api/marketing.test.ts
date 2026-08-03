import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  listGroupActivities, getGroupActivity, joinGroup,
  openGroup, getGroupOrders, getGroupOrder, calculateDiscount,
} from './marketing'

describe('marketing API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listGroupActivities() calls GET /marketing/group/activities with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listGroupActivities(2, 5)

    expect(request.get).toHaveBeenCalledWith('/marketing/group/activities', { params: { page: 2, size: 5 } })
  })

  it('getGroupActivity() calls GET /marketing/group/activities/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getGroupActivity(1)

    expect(request.get).toHaveBeenCalledWith('/marketing/group/activities/1')
  })

  it('joinGroup() calls POST /marketing/group/join with data', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await joinGroup({ activityId: 1, groupOrderId: 10 })

    expect(request.post).toHaveBeenCalledWith('/marketing/group/join', { activityId: 1, groupOrderId: 10 })
  })

  it('openGroup() calls POST /marketing/group/join with activityId only', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await openGroup(1)

    expect(request.post).toHaveBeenCalledWith('/marketing/group/join', { activityId: 1 })
  })

  it('getGroupOrders() calls GET /marketing/group/orders with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getGroupOrders(1, 2, 10)

    expect(request.get).toHaveBeenCalledWith('/marketing/group/orders', { params: { activityId: 1, page: 2, size: 10 } })
  })

  it('getGroupOrder() calls GET /marketing/group/orders/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getGroupOrder(5)

    expect(request.get).toHaveBeenCalledWith('/marketing/group/orders/5')
  })

  it('calculateDiscount() calls POST /marketing/tiered/calculate with data', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await calculateDiscount({ productId: 1, quantity: 2, totalAmount: 199.98 })

    expect(request.post).toHaveBeenCalledWith('/marketing/tiered/calculate', { productId: 1, quantity: 2, totalAmount: 199.98 })
  })
})
