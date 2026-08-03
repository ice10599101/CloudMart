import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { listNotifications, getUnreadCount, markAsRead, markAllAsRead } from './notification'

describe('notification API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listNotifications() calls GET /notification/notifications with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listNotifications(1, 20, 'SYSTEM')

    expect(request.get).toHaveBeenCalledWith('/notification/notifications', {
      params: { page: 1, pageSize: 20, type: 'SYSTEM' },
    })
  })

  it('listNotifications() calls GET without type when empty', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listNotifications(1, 20, '')

    expect(request.get).toHaveBeenCalledWith('/notification/notifications', {
      params: { page: 1, pageSize: 20, type: undefined },
    })
  })

  it('getUnreadCount() calls GET /notification/notifications/unread-count', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getUnreadCount()

    expect(request.get).toHaveBeenCalledWith('/notification/notifications/unread-count')
  })

  it('markAsRead() calls PUT /notification/notifications/:id/read', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await markAsRead(42)

    expect(request.put).toHaveBeenCalledWith('/notification/notifications/42/read')
  })

  it('markAllAsRead() calls PUT /notification/notifications/read-all', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await markAllAsRead()

    expect(request.put).toHaveBeenCalledWith('/notification/notifications/read-all')
  })
})
