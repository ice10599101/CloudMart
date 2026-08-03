import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  getUsers,
  getUser,
  createUser,
  updateUser,
  deleteUser,
  updateUserStatus,
  resetPassword,
  assignRoles,
  getRoles,
  getRole,
  createRole,
  updateRole,
  deleteRole,
  assignRoleMenus,
  getRoleMenus,
  updateRoleDataScope,
  getMenuTree,
  createMenu,
  updateMenu,
  deleteMenu,
  getDeptTree,
  createDept,
  updateDept,
  deleteDept,
  getPosts,
  createPost,
  updatePost,
  deletePost,
  getDictTypes,
  createDictType,
  deleteDictType,
  refreshDictCache,
  getDictData,
  createDictData,
  getConfigs,
  createConfig,
  deleteConfig,
  refreshConfigCache,
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

describe('admin system API - User Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getUsers() calls GET /admin/users/page', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getUsers({ page: 1, size: 10 })
    expect(request.get).toHaveBeenCalledWith('/admin/users/page', { params: { page: 1, size: 10 } })
  })

  it('getUser() calls GET /admin/users/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getUser(1)
    expect(request.get).toHaveBeenCalledWith('/admin/users/1')
  })

  it('createUser() calls POST /admin/users', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createUser({ username: 'newuser' })
    expect(request.post).toHaveBeenCalledWith('/admin/users', { username: 'newuser' })
  })

  it('updateUser() calls PUT /admin/users/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateUser(1, { nickname: 'Updated' })
    expect(request.put).toHaveBeenCalledWith('/admin/users/1', { nickname: 'Updated' })
  })

  it('deleteUser() calls DELETE /admin/users/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteUser(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/users/1')
  })

  it('updateUserStatus() calls PUT /admin/users/:id/status', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateUserStatus(1, { status: 0 })
    expect(request.put).toHaveBeenCalledWith('/admin/users/1/status', { status: 0 })
  })

  it('resetPassword() calls PUT /admin/users/resetPassword', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await resetPassword({ userId: 1, newPassword: 'xxx' })
    expect(request.put).toHaveBeenCalledWith('/admin/users/resetPassword', { userId: 1, newPassword: 'xxx' })
  })

  it('assignRoles() calls PUT /admin/users/:id/roles', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await assignRoles(1, { roleIds: [1, 2] })
    expect(request.put).toHaveBeenCalledWith('/admin/users/1/roles', { roleIds: [1, 2] })
  })
})

describe('admin system API - Role Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getRoles() calls GET /admin/roles', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getRoles()
    expect(request.get).toHaveBeenCalledWith('/admin/roles', { params: undefined })
  })

  it('getRole() calls GET /admin/roles/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getRole(1)
    expect(request.get).toHaveBeenCalledWith('/admin/roles/1')
  })

  it('createRole() calls POST /admin/roles', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createRole({ name: 'editor' })
    expect(request.post).toHaveBeenCalledWith('/admin/roles', { name: 'editor' })
  })

  it('updateRole() calls PUT /admin/roles/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateRole(1, { name: 'admin' })
    expect(request.put).toHaveBeenCalledWith('/admin/roles/1', { name: 'admin' })
  })

  it('deleteRole() calls DELETE /admin/roles/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteRole(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/roles/1')
  })

  it('assignRoleMenus() calls PUT /admin/roles/menus', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await assignRoleMenus({ roleId: 1, menuIds: [1, 2, 3] })
    expect(request.put).toHaveBeenCalledWith('/admin/roles/menus', { roleId: 1, menuIds: [1, 2, 3] })
  })

  it('getRoleMenus() calls GET /admin/roles/:id/menus', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getRoleMenus(1)
    expect(request.get).toHaveBeenCalledWith('/admin/roles/1/menus')
  })

  it('updateRoleDataScope() calls PUT /admin/roles/:id/data-scope', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateRoleDataScope(1, { dataScope: 'ALL' })
    expect(request.put).toHaveBeenCalledWith('/admin/roles/1/data-scope', { dataScope: 'ALL' })
  })
})

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

describe('admin system API - Dept Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getDeptTree() calls GET /admin/depts/tree', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getDeptTree()
    expect(request.get).toHaveBeenCalledWith('/admin/depts/tree')
  })

  it('createDept() calls POST /admin/depts', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createDept({ name: 'Tech' })
    expect(request.post).toHaveBeenCalledWith('/admin/depts', { name: 'Tech' })
  })

  it('updateDept() calls PUT /admin/depts/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateDept(1, { name: 'Engineering' })
    expect(request.put).toHaveBeenCalledWith('/admin/depts/1', { name: 'Engineering' })
  })

  it('deleteDept() calls DELETE /admin/depts/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteDept(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/depts/1')
  })
})

describe('admin system API - Post Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getPosts() calls GET /admin/posts', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getPosts()
    expect(request.get).toHaveBeenCalledWith('/admin/posts', { params: undefined })
  })

  it('createPost() calls POST /admin/posts', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createPost({ name: 'CEO' })
    expect(request.post).toHaveBeenCalledWith('/admin/posts', { name: 'CEO' })
  })

  it('updatePost() calls PUT /admin/posts/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updatePost(1, { name: 'CTO' })
    expect(request.put).toHaveBeenCalledWith('/admin/posts/1', { name: 'CTO' })
  })

  it('deletePost() calls DELETE /admin/posts/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deletePost(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/posts/1')
  })
})

describe('admin system API - Dict Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getDictTypes() calls GET /admin/dict/types', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getDictTypes()
    expect(request.get).toHaveBeenCalledWith('/admin/dict/types', { params: undefined })
  })

  it('createDictType() calls POST /admin/dict/types', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createDictType({ name: 'status' })
    expect(request.post).toHaveBeenCalledWith('/admin/dict/types', { name: 'status' })
  })

  it('deleteDictType() calls DELETE /admin/dict/types/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteDictType(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/dict/types/1')
  })

  it('refreshDictCache() calls PUT /admin/dict/types/cache/refresh', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await refreshDictCache()
    expect(request.put).toHaveBeenCalledWith('/admin/dict/types/cache/refresh')
  })

  it('getDictData() calls GET /admin/dict/data/type/:type', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getDictData('status')
    expect(request.get).toHaveBeenCalledWith('/admin/dict/data/type/status', { params: undefined })
  })

  it('createDictData() calls POST /admin/dict/data', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createDictData({ label: 'Active', value: '1' })
    expect(request.post).toHaveBeenCalledWith('/admin/dict/data', { label: 'Active', value: '1' })
  })
})

describe('admin system API - Config Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getConfigs() calls GET /admin/configs', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getConfigs()
    expect(request.get).toHaveBeenCalledWith('/admin/configs', { params: undefined })
  })

  it('createConfig() calls POST /admin/configs', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createConfig({ key: 'site.name', value: 'CloudMart' })
    expect(request.post).toHaveBeenCalledWith('/admin/configs', { key: 'site.name', value: 'CloudMart' })
  })

  it('deleteConfig() calls DELETE /admin/configs/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteConfig(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/configs/1')
  })

  it('refreshConfigCache() calls PUT /admin/configs/cache/refresh', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await refreshConfigCache()
    expect(request.put).toHaveBeenCalledWith('/admin/configs/cache/refresh')
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
