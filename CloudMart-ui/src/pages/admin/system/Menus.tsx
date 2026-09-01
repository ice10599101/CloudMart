import { useRef, useState, useEffect } from 'react'
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
  menuType: number
  orderNum: number
  visible: number
  status: number
  perms: string
  children?: MenuRecord[]
}

const ICON_OPTIONS = [
  { label: 'DashboardOutlined', value: 'DashboardOutlined' },
  { label: 'SettingOutlined', value: 'SettingOutlined' },
  { label: 'UserOutlined', value: 'UserOutlined' },
  { label: 'TeamOutlined', value: 'TeamOutlined' },
  { label: 'UnorderedListOutlined', value: 'UnorderedListOutlined' },
  { label: 'TagOutlined', value: 'TagOutlined' },
  { label: 'NotificationOutlined', value: 'NotificationOutlined' },
  { label: 'ShoppingOutlined', value: 'ShoppingOutlined' },
  { label: 'AppstoreOutlined', value: 'AppstoreOutlined' },
  { label: 'MonitorOutlined', value: 'MonitorOutlined' },
  { label: 'DatabaseOutlined', value: 'DatabaseOutlined' },
  { label: 'ScheduleOutlined', value: 'ScheduleOutlined' },
  { label: 'CodeOutlined', value: 'CodeOutlined' },
  { label: 'ToolOutlined', value: 'ToolOutlined' },
  { label: 'ThunderboltOutlined', value: 'ThunderboltOutlined' },
  { label: 'ShoppingCartOutlined', value: 'ShoppingCartOutlined' },
  { label: 'CommentOutlined', value: 'CommentOutlined' },
  { label: 'InboxOutlined', value: 'InboxOutlined' },
  { label: 'PayCircleOutlined', value: 'PayCircleOutlined' },
  { label: 'VideoCameraOutlined', value: 'VideoCameraOutlined' },
  { label: 'StopOutlined', value: 'StopOutlined' },
  { label: 'RobotOutlined', value: 'RobotOutlined' },
  { label: 'RiseOutlined', value: 'RiseOutlined' },
]

const MENU_TYPE_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '目录', color: 'blue' },
  1: { label: '菜单', color: 'green' },
  2: { label: '按钮', color: 'orange' },
}

export default function Menus() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<MenuRecord | null>(null)
  const [menuTree, setMenuTree] = useState<MenuRecord[]>([])
  const [treeSelectData, setTreeSelectData] = useState<TreeSelectNode[]>([])

  async function fetchMenuTree() {
    const { data: res } = await getMenuTree()
    const response = res as ApiResponse<MenuRecord[]>
    const tree = response.data ?? []
    setMenuTree(tree)
    setTreeSelectData([
      { title: '顶级菜单', value: 0, key: 0, children: convertToTreeSelect(tree, 'menuName') },
    ])
  }

  useEffect(() => {
    fetchMenuTree()
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
      fetchMenuTree()
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteMenu(id)
    message.success('删除成功')
    fetchMenuTree()
    actionRef.current?.reload()
  }

  const handleStatusChange = async (id: number, newStatus: number) => {
    try {
      await updateMenuStatus(id, { status: newStatus })
      message.success('状态更新成功')
      fetchMenuTree()
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
      valueEnum: {
        0: { text: '目录' },
        1: { text: '菜单' },
        2: { text: '按钮' },
      },
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
          return {
            data: menuTree,
            success: true,
          }
        }}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingRecord(null)
              setModalVisible(true)
            }}
          >
            新增菜单
          </Button>,
        ]}
        columns={columns}
        pagination={false}
        search={false}
        expandable={{ defaultExpandAllRows: true }}
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
                visible: editingRecord.visible === 1,
                status: editingRecord.status === 1,
              }
            : { visible: true, status: true, orderNum: 0, menuType: 1, parentId: 0 }
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
          options={[
            { label: '目录', value: 0 },
            { label: '菜单', value: 1 },
            { label: '按钮', value: 2 },
          ]}
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
