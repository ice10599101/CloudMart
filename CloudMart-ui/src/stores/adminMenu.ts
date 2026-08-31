import { create } from 'zustand'
import { getMenuTree } from '@/api/admin/system'

/**
 * admin_menu 表节点的运行时形态（后端 GET /admin/menus/tree 返回）。
 * menuType: M-目录 C-菜单 F-按钮；visible/status: 1 为显示/正常
 */
export interface AdminMenuNode {
  id: number
  menuName: string
  parentId: number
  orderNum: number
  path: string
  component?: string | null
  menuType: string
  visible: number
  status: number
  perms?: string | null
  icon?: string | null
  children?: AdminMenuNode[]
}

type MenuLoadStatus = 'idle' | 'loading' | 'ready' | 'error'

interface AdminMenuState {
  menuTree: AdminMenuNode[]
  status: MenuLoadStatus
  /** 登录后拉取一次全量菜单树；失败可重试，成功后本会话内缓存 */
  fetchMenus: () => Promise<void>
  /** 登出时清空，避免跨账号残留上一账号的菜单数据 */
  clear: () => void
}

export const useAdminMenuStore = create<AdminMenuState>((set, get) => ({
  menuTree: [],
  status: 'idle',

  fetchMenus: async () => {
    const { status } = get()
    if (status === 'loading' || status === 'ready') return
    set({ status: 'loading' })
    try {
      const { data: response } = await getMenuTree()
      set({ menuTree: response.data ?? [], status: 'ready' })
    } catch {
      set({ menuTree: [], status: 'error' })
    }
  },

  clear: () => set({ menuTree: [], status: 'idle' }),
}))

/** ProLayout 侧边栏节点；icon 为 admin_menu.icon 原始字符串，由布局层映射为图标组件 */
export interface LayoutMenuItem {
  path: string
  name: string
  iconKey?: string
  routes?: LayoutMenuItem[]
}

/**
 * admin_menu 树 → 侧边栏结构。
 * 过滤规则：按钮(F)不进侧边栏；停用(status≠1)/隐藏(visible≠1)不展示；
 * 挂了 perms 且当前管理员无权限的不展示；目录下无可见子项整组隐藏。
 */
export function toLayoutMenu(
  nodes: AdminMenuNode[],
  hasPermission: (perm: string) => boolean,
): LayoutMenuItem[] {
  const items: LayoutMenuItem[] = []
  for (const node of nodes) {
    if (node.menuType === 'F') continue
    if (Number(node.status) !== 1 || Number(node.visible) !== 1) continue

    const children = node.children ? toLayoutMenu(node.children, hasPermission) : []
    if (node.menuType === 'M' && children.length === 0) continue
    if (node.perms && !hasPermission(node.perms)) continue

    const path = node.path?.startsWith('/') ? node.path : node.path ? `/${node.path}` : ''
    items.push({
      path: path || `#${node.id}`,
      name: node.menuName,
      iconKey: node.icon && node.icon !== '#' ? node.icon : undefined,
      ...(node.menuType === 'M' ? { routes: children } : {}),
    })
  }
  return items
}
