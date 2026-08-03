import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useNotificationStore } from './notification'

vi.mock('@/api/notification', () => ({
  getUnreadCount: vi.fn(),
  listNotifications: vi.fn(),
}))

vi.mock('umi', () => ({
  history: { push: vi.fn(), replace: vi.fn() },
}))

import { getUnreadCount } from '@/api/notification'

describe('useNotificationStore', () => {
  beforeEach(() => {
    useNotificationStore.setState({
      unreadCount: 0,
      ws: null,
      notifications: [],
    })
    vi.clearAllMocks()
  })

  it('initializes with zero unread count and empty notifications', () => {
    const state = useNotificationStore.getState()
    expect(state.unreadCount).toBe(0)
    expect(state.notifications).toEqual([])
    expect(state.ws).toBeNull()
  })

  it('fetchUnreadCount() updates unreadCount', async () => {
    vi.mocked(getUnreadCount).mockResolvedValue({
      data: { success: true, data: { count: 5 } },
    } as any)

    await useNotificationStore.getState().fetchUnreadCount()

    expect(useNotificationStore.getState().unreadCount).toBe(5)
  })

  it('fetchUnreadCount() defaults to 0 when data is null', async () => {
    vi.mocked(getUnreadCount).mockResolvedValue({
      data: { success: true, data: null },
    } as any)

    await useNotificationStore.getState().fetchUnreadCount()

    expect(useNotificationStore.getState().unreadCount).toBe(0)
  })

  it('fetchUnreadCount() handles error silently', async () => {
    vi.mocked(getUnreadCount).mockRejectedValue(new Error('Network error'))

    await expect(useNotificationStore.getState().fetchUnreadCount()).resolves.toBeUndefined()
    expect(useNotificationStore.getState().unreadCount).toBe(0)
  })

  it('incrementUnread() increases unreadCount by 1', () => {
    useNotificationStore.setState({ unreadCount: 3 })

    useNotificationStore.getState().incrementUnread()

    expect(useNotificationStore.getState().unreadCount).toBe(4)
  })

  it('addNotification() adds item and increments count', () => {
    const item = {
      id: 1,
      userId: 1,
      type: 'SYSTEM',
      title: 'Test',
      content: 'Test content',
      isRead: false,
      bizId: null,
      bizType: null,
      createdAt: '2025-01-01T00:00:00Z',
    }

    useNotificationStore.getState().addNotification(item)

    const state = useNotificationStore.getState()
    expect(state.notifications).toHaveLength(1)
    expect(state.notifications[0].id).toBe(1)
    expect(state.unreadCount).toBe(1)
  })

  it('resetUnread() sets unreadCount to 0', () => {
    useNotificationStore.setState({ unreadCount: 10 })

    useNotificationStore.getState().resetUnread()

    expect(useNotificationStore.getState().unreadCount).toBe(0)
  })

  it('setNotifications() replaces notifications list', () => {
    const items = [
      { id: 1, userId: 1, type: 'SYSTEM', title: 'A', content: 'A', isRead: true, bizId: null, bizType: null, createdAt: '2025-01-01' },
      { id: 2, userId: 1, type: 'ORDER', title: 'B', content: 'B', isRead: false, bizId: null, bizType: null, createdAt: '2025-01-02' },
    ]

    useNotificationStore.getState().setNotifications(items)

    expect(useNotificationStore.getState().notifications).toHaveLength(2)
    expect(useNotificationStore.getState().notifications[0].id).toBe(1)
  })
})
