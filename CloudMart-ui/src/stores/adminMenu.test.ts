import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/api/admin/system', () => ({
  getMenuTree: vi.fn(),
}))

import { useAdminMenuStore, toLayoutMenu } from './adminMenu'
import { getMenuTree } from '@/api/admin/system'
import type { AdminMenuNode } from './adminMenu'

function node(overrides: Partial<AdminMenuNode> & { id: number }): AdminMenuNode {
  return {
    menuName: `菜单${overrides.id}`,
    parentId: 0,
    orderNum: 0,
    path: `/page/${overrides.id}`,
    menuType: 'C',
    visible: 1,
    status: 1,
    perms: null,
    icon: null,
    ...overrides,
  }
}

const TREE: AdminMenuNode[] = [
  node({ id: 100, path: '/admin/dashboard', menuName: '工作台' }),
  {
    ...node({ id: 1000, path: '/admin/system', menuType: 'M', menuName: '系统管理', children: [] }),
    children: [
      node({ id: 1001, path: '/admin/system/users', perms: 'admin:user:list' }),
      node({ id: 1002, path: '/admin/system/hidden', visible: 0 }),
      node({ id: 1003, path: '/admin/system/disabled', status: 0 }),
      node({ id: 1004, path: '/admin/system/no-perm', perms: 'admin:role:list' }),
      {
        ...node({ id: 1010, path: '/admin/system/log', menuType: 'M', menuName: '日志管理' }),
        children: [node({ id: 1051, path: '/admin/system/oper-log', perms: 'admin:operlog:list' })],
      },
    ],
  },
  {
    ...node({ id: 9000, path: '/admin/empty', menuType: 'M', menuName: '空目录' }),
    children: [],
  },
]

describe('useAdminMenuStore', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAdminMenuStore.setState({ menuTree: [], status: 'idle' })
  })

  it('fetchMenus() 拉取成功后缓存菜单树并置 ready', async () => {
    vi.mocked(getMenuTree).mockResolvedValue({ data: { success: true, data: TREE } } as any)

    await useAdminMenuStore.getState().fetchMenus()

    expect(useAdminMenuStore.getState().status).toBe('ready')
    expect(useAdminMenuStore.getState().menuTree).toEqual(TREE)
  })

  it('fetchMenus() 失败置 error 且可重试', async () => {
    vi.mocked(getMenuTree).mockRejectedValueOnce(new Error('network'))
    await useAdminMenuStore.getState().fetchMenus()
    expect(useAdminMenuStore.getState().status).toBe('error')

    vi.mocked(getMenuTree).mockResolvedValue({ data: { success: true, data: TREE } } as any)
    await useAdminMenuStore.getState().fetchMenus()
    expect(useAdminMenuStore.getState().status).toBe('ready')
  })

  it('fetchMenus() 在 ready 状态下不重复请求', async () => {
    useAdminMenuStore.setState({ status: 'ready', menuTree: TREE })
    await useAdminMenuStore.getState().fetchMenus()
    expect(vi.mocked(getMenuTree)).not.toHaveBeenCalled()
  })

  it('clear() 重置为 idle', () => {
    useAdminMenuStore.setState({ status: 'ready', menuTree: TREE })
    useAdminMenuStore.getState().clear()
    expect(useAdminMenuStore.getState()).toMatchObject({ status: 'idle', menuTree: [] })
  })
})

describe('toLayoutMenu（侧边栏转换过滤规则）', () => {
  const allowAll = () => true

  it('过滤按钮/隐藏/停用/无权限菜单，空目录整组隐藏', () => {
    // admin:role:list 视为未授予，用于验证 perms 过滤
    const allowExceptRoleList = (perm: string) => perm !== 'admin:role:list'
    const items = toLayoutMenu(TREE, allowExceptRoleList)
    const names = JSON.stringify(items)

    expect(items.map((i) => i.path)).toContain('/admin/dashboard')
    const system = items.find((i) => i.path === '/admin/system')
    const childPaths = (system?.routes ?? []).map((r) => r.path)
    expect(childPaths).toEqual(['/admin/system/users', '/admin/system/log'])

    expect(names).not.toContain('/admin/system/hidden')
    expect(names).not.toContain('/admin/system/disabled')
    expect(names).not.toContain('/admin/system/no-perm')
    expect(names).not.toContain('/admin/empty')
  })

  it('有权限过滤时目录保留存活的子项', () => {
    const items = toLayoutMenu(TREE, (perm) => perm === 'admin:operlog:list')
    const system = items.find((i) => i.path === '/admin/system')
    const childPaths = (system?.routes ?? []).map((r) => r.path)
    // users 无 admin:user:list 权限被过滤；oper-log 权限满足所以日志目录保留
    expect(childPaths).toEqual(['/admin/system/log'])
  })

  it('无前导斜杠的 path 自动补全，icon=# 视为无图标', () => {
    const items = toLayoutMenu(
      [node({ id: 1, path: 'admin/business/wishes', icon: '#' }), node({ id: 2, path: '/x', icon: 'star' })],
      allowAll,
    )
    expect(items[0].path).toBe('/admin/business/wishes')
    expect(items[0].iconKey).toBeUndefined()
    expect(items[1].iconKey).toBe('star')
  })
})
