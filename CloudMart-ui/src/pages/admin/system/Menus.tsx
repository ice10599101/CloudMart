import { useRef, useState, useEffect } from 'react'
import type { Key } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormDigit,
  ProFormSelect,
  ProFormTreeSelect,
  ProFormSwitch,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getMenuTree,
  createMenu,
  updateMenu,
  deleteMenu,
  updateMenuStatus,
} from '@/api/admin/system'
import type { ApiResponse } from '@/types/api'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'
import { convertToTreeSelect } from '@/utils/tree'
import type { TreeSelectNode } from '@/utils/tree'

interface MenuRecord {
  id: number
  parentId: number
  menuName: string
  path: string
  component: string
  icon: string
  menuType: string
  orderNum: number
  visible: number
  status: number
  perms: string
  children?: MenuRecord[]
}

// 图标选项与 AdminLayout MENU_ICON_MAP 的 icon key 体系一致（admin_menu.icon 存 key，布局层映射为组件）
const ICON_OPTIONS = [
  'dashboard',
  'setting',
  'shopping',
  'monitor',
  'tool',
  'user',
  'peoples',
  'tree-table',
  'dict',
  'edit',
  'log',
  'form',
  'logininfor',
  'goods',
  'list',
  'money',
  'time',
  'box',
  'message',
  'shopping-cart',
  'star',
  'heart',
  'comment',
  'trophy',
  'medal',
  'music',
  'flag',
  'tree',
  'lock',
  'shield',
  'safety',
  'fire',
  'video',
  'map',
  'adjust',
  'team',
  'robot',
  'schedule',
  'code',
  'car',
  'database',
  'upload',
  'crown',
  'stop',
  'alert',
  'tag',
  'rise',
  'unordered-list',
  'notification',
].map((key) => ({ label: key, value: key }))

// 后端 menuType 为 M-目录 / C-菜单 / F-按钮（与 admin_menu 表及侧边栏下发契约一致）
const MENU_TYPE_OPTIONS = [
  { label: '目录', value: 'M' },
  { label: '菜单', value: 'C' },
  { label: '按钮', value: 'F' },
]

const MENU_TYPE_MAP: Record<string, { label: string; color: string }> = {
  M: { label: '目录', color: 'blue' },
  C: { label: '菜单', color: 'green' },
  F: { label: '按钮', color: 'orange' },
}

export default function Menus() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<MenuRecord | null>(null)
  const [defaultParentId, setDefaultParentId] = useState<number>(0)
  const [expandedRowKeys, setExpandedRowKeys] = useState<readonly Key[]>([])
  const [treeSelectData, setTreeSelectData] = useState<TreeSelectNode[]>([])

  // 仅用于弹窗"上级菜单"树选数据；表格数据由 ProTable request 直接拉取
  // 收集所有含子级的节点 id，作为受控展开键（defaultExpandAllRows 对异步首载不生效）
  function collectParentIds(nodes: MenuRecord[]): number[] {
    const ids: number[] = []
    for (const node of nodes) {
      if (node.children && node.children.length > 0) {
        ids.push(node.id)
        ids.push(...collectParentIds(node.children))
      }
    }
    return ids
  }

  async function loadMenuTree(): Promise<MenuRecord[]> {
    const { data: res } = await getMenuTree()
    const response = res as ApiResponse<MenuRecord[]>
    const tree = response.data ?? []
    setExpandedRowKeys(collectParentIds(tree))
    setTreeSelectData([
      { title: '顶级菜单', value: 0, key: 0, children: convertToTreeSelect(tree, 'menuName') },
    ])
    return tree
  }

  useEffect(() => {
    loadMenuTree().catch(() => setTreeSelectData([]))
  }, [])

  const handleSubmit = async (values: Record<string, any>) => {
    const payload = {
      ...values,
      visible: values.visible ? 1 : 0,
      status: values.status ? 1 : 0,
    }
    return confirmSubmit(async () => {
      if (editingRecord) {
        await updateMenu(editingRecord.id, payload)
        message.success('更新成功')
      } else {
        await createMenu(payload)
        message.success('创建成功')
      }
      setEditingRecord(null)
      loadMenuTree().catch(() => setTreeSelectData([]))
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteMenu(id)
    message.success('删除成功')
    loadMenuTree().catch(() => setTreeSelectData([]))
    actionRef.current?.reload()
  }

  const handleStatusChange = async (id: number, newStatus: number) => {
    try {
      await updateMenuStatus(id, { status: newStatus })
      message.success('状态更新成功')
      loadMenuTree().catch(() => setTreeSelectData([]))
      actionRef.current?.reload()
    } catch {
      message.error('状态更新失败')
    }
  }

  const columns: ProColumns<MenuRecord>[] = [
    { title: '菜单名称', dataIndex: 'menuName', width: 180 },
    {
      title: '图标',
      dataIndex: 'icon',
      width: 80,
      search: false,
      render: (_, record) => record.icon || '-',
    },
    {
      title: '类型',
      dataIndex: 'menuType',
      width: 80,
      render: (_, record) => {
        const typeInfo = MENU_TYPE_MAP[record.menuType] ?? { label: '未知', color: 'default' }
        return <Tag color={typeInfo.color}>{typeInfo.label}</Tag>
      },
    },
    { title: '路由地址', dataIndex: 'path', width: 180, search: false },
    { title: '组件路径', dataIndex: 'component', width: 180, search: false },
    { title: '权限标识', dataIndex: 'perms', width: 180, search: false },
    { title: '排序', dataIndex: 'orderNum', width: 80, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      search: false,
      render: (_, record) => (
        <Popconfirm
      title={`确定${Number(record.status) === 1 ? '停用' : '启用'}吗？`}
      onConfirm={() => handleStatusChange(record.id, Number(record.status) === 1 ? 0 : 1)}
    >
      <Switch checked={Number(record.status) === 1} size="small" />
    </Popconfirm>
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="add"
          type="link"
          size="small"
          onClick={() => {
            setEditingRecord(null)
            setDefaultParentId(record.id)
            setModalVisible(true)
          }}
        >
          新增子菜单
        </Button>,
        <Button
          key="edit"
          type="link"
          size="small"
          onClick={() => {
            setEditingRecord(record)
            setModalVisible(true)
          }}
        >
          编辑
        </Button>,
        <Popconfirm
          key="delete"
          title="确认删除该菜单？"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<MenuRecord>
        headerTitle="菜单管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1300 }}
        request={async () => {
          // 直接在 request 内请求，避免闭包捕获首帧空 state 导致表格永远"暂无数据"；
          // 增删改后通过 actionRef.reload() 触发重新拉取
          try {
            const tree = await loadMenuTree()
            return { data: tree, success: true }
          } catch {
            return { data: [], success: false }
          }
        }}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingRecord(null)
              setDefaultParentId(0)
              setModalVisible(true)
            }}
          >
            新增菜单
          </Button>,
        ]}
        columns={columns}
        pagination={false}
        search={false}
        expandable={{
          expandedRowKeys,
          onExpandedRowsChange: (keys) => setExpandedRowKeys(keys),
        }}
      />

      <ModalForm
        title={editingRecord ? '编辑菜单' : '新增菜单'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? {
                ...editingRecord,
                visible: Number(editingRecord.visible) === 1,
                status: Number(editingRecord.status) === 1,
              }
            : { visible: true, status: true, orderNum: 0, menuType: 'C', parentId: defaultParentId }
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={600}
      >
        <ProFormTreeSelect
          name="parentId"
          label="上级菜单"
          fieldProps={{
            treeData: treeSelectData,
            placeholder: '请选择上级菜单',
            treeDefaultExpandAll: true,
            allowClear: true,
          }}
        />
        <ProFormSelect
          name="menuType"
          label="菜单类型"
          options={MENU_TYPE_OPTIONS}
          rules={[{ required: true, message: '请选择菜单类型' }]}
        />
        <ProFormText
          name="menuName"
          label="菜单名称"
          placeholder="请输入菜单名称"
          rules={[{ required: true, message: '请输入菜单名称' }]}
        />
        <ProFormSelect
          name="icon"
          label="图标"
          options={ICON_OPTIONS}
          showSearch
          fieldProps={{ allowClear: true, placeholder: '请选择图标' }}
        />
        <ProFormText
          name="path"
          label="路由地址"
          placeholder="请输入路由地址"
        />
        <ProFormText
          name="component"
          label="组件路径"
          placeholder="请输入组件路径"
        />
        <ProFormText
          name="perms"
          label="权限标识"
          placeholder="如 system:user:list"
        />
        <ProFormDigit
          name="orderNum"
          label="排序"
          min={0}
          fieldProps={{ precision: 0 }}
        />
        <ProFormSwitch name="visible" label="是否可见" />
        <ProFormSwitch name="status" label="状态" />
      </ModalForm>
    </>
  )
}
