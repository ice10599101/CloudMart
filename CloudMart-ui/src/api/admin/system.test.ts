import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  getMenuTree,
  createMenu,
  updateMenu,
  deleteMenu,
  getNotices,
  createNotice,
  getOperLogs,
  cleanOperLogs,
  getLoginLogs,
  cleanLoginLogs,
  getDashboardStats,
  getRecentOrders,
  getSalesTrend,
} from './system'

describe('admin system API - Menu Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getMenuTree() calls GET /admin/menus/tree', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getMenuTree()
    expect(request.get).toHaveBeenCalledWith('/admin/menus/tree')
  })

  it('createMenu() calls POST /admin/menus', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createMenu({ name: 'Dashboard' })
    expect(request.post).toHaveBeenCalledWith('/admin/menus', { name: 'Dashboard' })
  })

  it('updateMenu() calls PUT /admin/menus/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateMenu(1, { name: 'Home' })
    expect(request.put).toHaveBeenCalledWith('/admin/menus/1', { name: 'Home' })
  })

  it('deleteMenu() calls DELETE /admin/menus/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteMenu(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/menus/1')
  })
})

describe('admin system API - Notice & Logs', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getNotices() calls GET /admin/notices/page', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getNotices({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/notices/page', { params: { page: 1 } })
  })

  it('createNotice() calls POST /admin/notices', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createNotice({ title: 'Notice' })
    expect(request.post).toHaveBeenCalledWith('/admin/notices', { title: 'Notice' })
  })

  it('getOperLogs() calls GET /admin/logs/oper/page', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getOperLogs()
    expect(request.get).toHaveBeenCalledWith('/admin/logs/oper/page', { params: undefined })
  })

  it('cleanOperLogs() calls DELETE /admin/logs/oper/clean', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await cleanOperLogs()
    expect(request.delete).toHaveBeenCalledWith('/admin/logs/oper/clean')
  })

  it('getLoginLogs() calls GET /admin/logs/login/page', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getLoginLogs()
    expect(request.get).toHaveBeenCalledWith('/admin/logs/login/page', { params: undefined })
  })

  it('cleanLoginLogs() calls DELETE /admin/logs/login/clean', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await cleanLoginLogs()
    expect(request.delete).toHaveBeenCalledWith('/admin/logs/login/clean')
  })
})

describe('admin system API - Dashboard', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getDashboardStats() calls GET /admin/dashboard/stats', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getDashboardStats()
    expect(request.get).toHaveBeenCalledWith('/admin/dashboard/stats')
  })

  it('getRecentOrders() calls GET /admin/dashboard/recent-orders', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getRecentOrders({ pageSize: 5 })
    expect(request.get).toHaveBeenCalledWith('/admin/dashboard/recent-orders', { params: { pageSize: 5 } })
  })

  it('getSalesTrend() calls GET /admin/dashboard/sales-trend', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getSalesTrend({ days: 7 })
    expect(request.get).toHaveBeenCalledWith('/admin/dashboard/sales-trend', { params: { days: 7 } })
  })
})
