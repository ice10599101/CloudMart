import { useEffect, useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Modal, Popconfirm, Tag, Tree, Typography, Input } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  listAdminRoles,
  getAdminRole,
  createAdminRole,
  updateAdminRole,
  deleteAdminRole,
  getAdminRoleMenus,
  assignAdminRoleMenus,
  getMenuTree,
  type AdminRoleRow,
} from '@/api/admin/system'
import { useMessage } from '@/utils/useMessage'

/**
 * T24 多运营：角色管理（role CRUD + 菜单/权限点树勾选分配）。
 * 权限以接口动作拆分（@RequiresPermission 点位来自菜单 perms），
 * 不以"有菜单"代替接口鉴权；角色删除前由后端校验是否已分配用户。
 */

const { Paragraph, Text } = Typography

interface MenuTreeNode {
  id: number
  menuName: string
  children?: MenuTreeNode[]
  perms?: string | null
  menuType?: string
}

export default function Roles() {
  const actionRef = useRef<ActionType>(null)
  const message = useMessage()
  const [modalVisible, setModalVisible] = useState(false)
  const [editing, setEditing] = useState<AdminRoleRow | null>(null)
  const [menuTree, setMenuTree] = useState<MenuTreeNode[]>([])
  const [checkedKeys, setCheckedKeys] = useState<number[]>([])
  const [menuOpen, setMenuOpen] = useState<AdminRoleRow | null>(null)
  const [roleMenuChecked, setRoleMenuChecked] = useState<number[]>([])
  const [createOpen, setCreateOpen] = useState(false)
  const [newRole, setNewRole] = useState({ roleName: '', roleKey: '', remark: '' })
  const [saving, setSaving] = useState(false)

  const reload = () => actionRef.current?.reload()

  const loadMenuTree = async () => {
    const res = await getMenuTree()
    setMenuTree((res.data.data ?? []) as MenuTreeNode[])
  }

  const toAntTreeData = (nodes: MenuTreeNode[]): any[] =>
    nodes.map((n) => ({
      key: n.id,
      title: n.menuType === 'F' ? `${n.menuName}（${n.perms ?? ''}）` : n.menuName,
      children: n.children ? toAntTreeData(n.children) : undefined,
    }))

  useEffect(() => {
    void loadMenuTree()
  }, [])

  const openEdit = async (row: AdminRoleRow) => {
    setEditing(row)
    setModalVisible(true)
  }

  const openMenus = async (row: AdminRoleRow) => {
    setMenuOpen(row)
    const [menusRes, treeRes] = await Promise.all([getAdminRoleMenus(row.id), getMenuTree()])
    setRoleMenuChecked((menusRes.data.data ?? []).map(Number))
    setMenuTree((treeRes.data.data ?? []) as MenuTreeNode[])
  }

  const handleSaveMenus = async () => {
    if (!menuOpen) return
    await assignAdminRoleMenus({ roleId: menuOpen.id, menuIds: roleMenuChecked })
    message.success('菜单权限已保存（撤权后该角色账号旧会话经强退链即时失效）')
    setMenuOpen(null)
    reload()
  }

  const columns: ProColumns<AdminRoleRow>[] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '角色名', dataIndex: 'roleName', width: 140 },
    {
      title: '权限字符',
      dataIndex: 'roleKey',
      width: 160,
      render: (v) => <Tag color="geekblue">{String(v ?? '')}</Tag>,
    },
    { title: '排序', dataIndex: 'roleSort', width: 70 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_, row) => (row.status === 1 ? <Tag color="green">正常</Tag> : <Tag color="red">停用</Tag>),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: 'right',
      render: (_, row) => [
        <Button key="menus" type="link" size="small" onClick={() => openMenus(row)}>菜单权限</Button>,
        <Button key="edit" type="link" size="small" onClick={() => openEdit(row)}>编辑</Button>,
        <Popconfirm key="delete" title="删除前校验：角色已分配用户则拒绝" onConfirm={async () => {
          await deleteAdminRole(row.id)
          message.success('角色已删除')
          reload()
        }}>
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ].filter(Boolean),
    },
  ]

  return (
    <>
      <ProTable<AdminRoleRow>
        headerTitle="角色管理（T24 多运营）"
        actionRef={actionRef}
        rowKey="id"
        search={false}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            新增角色
          </Button>,
        ]}
        request={async () => {
          const res = await listAdminRoles()
          return { data: res.data.data ?? [], success: true, total: (res.data.data ?? []).length }
        }}
        columns={columns}
        pagination={false}
        scroll={{ x: 900 }}
      />

      {/* 编辑角色基础信息 */}
      <Modal
        title={`编辑角色：${editing?.roleName ?? ''}`}
        open={modalVisible && editing !== null}
        onCancel={() => setModalVisible(false)}
        footer={null}
        destroyOnClose
      >
        <EditRoleForm
          role={editing!}
          onDone={() => { setModalVisible(false); reload() }}
        />
      </Modal>

      {/* 菜单/权限点树分配 */}
      <Modal
        title={menuOpen ? `菜单权限：${menuOpen.roleName}` : '菜单权限'}
        open={menuOpen !== null}
        onOk={handleSaveMenus}
        onCancel={() => setMenuOpen(null)}
        okText="保存"
        cancelText="取消"
        width={560}
      >
        <Paragraph type="secondary">
          勾选该角色可见菜单与可执行动作（F 节点为接口权限点）。保存后已登录账号需重新登录生效。
        </Paragraph>
        <Tree
          checkable
          defaultExpandAll
          checkedKeys={roleMenuChecked}
          onCheck={(checked) => setRoleMenuChecked(checked as number[])}
          treeData={toAntTreeData(menuTree)}
        />
      </Modal>

      {/* 新增角色 */}
      <Modal
        title="新增角色"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        footer={null}
        destroyOnClose
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <Input placeholder="角色名（如：客服运营）" value={newRole.roleName} onChange={(e) => setNewRole({ ...newRole, roleName: e.target.value })} />
          <Input placeholder="权限字符（唯一，如 customer_service）" value={newRole.roleKey} onChange={(e) => setNewRole({ ...newRole, roleKey: e.target.value })} />
          <Input placeholder="备注（职责边界说明）" value={newRole.remark} onChange={(e) => setNewRole({ ...newRole, remark: e.target.value })} />
          <Text type="secondary">创建后在列表"菜单权限"中勾选该角色的能力范围。</Text>
          <Button
            type="primary"
            loading={saving}
            onClick={async () => {
              if (!newRole.roleName.trim() || !newRole.roleKey.trim()) {
                message.warning('角色名与权限字符必填')
                return
              }
              setSaving(true)
              try {
                await createAdminRole({ ...newRole, roleSort: 20, status: 1 })
                message.success('角色已创建')
                setCreateOpen(false)
                setNewRole({ roleName: '', roleKey: '', remark: '' })
                reload()
              } finally {
                setSaving(false)
              }
            }}
          >
            创建
          </Button>
        </div>
      </Modal>
    </>
  )
}

function EditRoleForm({ role, onDone }: { role: AdminRoleRow; onDone: () => void }) {
  const message = useMessage()
  const [roleName, setRoleName] = useState(role.roleName)
  const [remark, setRemark] = useState(role.remark ?? '')
  const [saving, setSaving] = useState(false)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <Input value={roleName} onChange={(e) => setRoleName(e.target.value)} placeholder="角色名" />
      <Input value={role.roleKey} disabled placeholder="权限字符（创建后不可改）" />
      <Input value={remark} onChange={(e) => setRemark(e.target.value)} placeholder="备注" />
      <Button
        type="primary"
        loading={saving}
        onClick={async () => {
          setSaving(true)
          try {
            await updateAdminRole(role.id, { roleName, remark })
            message.success('角色已更新')
            onDone()
          } finally {
            setSaving(false)
          }
        }}
      >
        保存
      </Button>
    </div>
  )
}
