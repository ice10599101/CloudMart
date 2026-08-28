import { useRef, useState, useEffect } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormDigit,
  ProFormSwitch,
  ProFormTreeSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Switch, Tag, TreeSelect } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getRoles,
  createRole,
  updateRole,
  deleteRole,
  updateRoleStatus,
  assignRoleMenus,
  getRoleMenus,
  getMenuTree,
} from '@/api/admin/system'
import type { ApiResponse } from '@/types/api'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'
import { convertToTreeSelect } from '@/utils/tree'

interface RoleRecord {
  id: number
  roleName: string
  roleKey: string
  roleSort: number
  status: number
  dataScope: number
  remark: string
  createdAt: string
}

interface MenuTreeNode {
  id: number
  menuName: string
  children?: MenuTreeNode[]
}

export default function Roles() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const { confirmSubmit: confirmSubmit1, createHandleOpenChange: createHandleOpenChange1 } = useModalConfirm()
  const { confirmSubmit: confirmSubmit2, createHandleOpenChange: createHandleOpenChange2 } = useModalConfirm()
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<RoleRecord | null>(null)
  const [menuTree, setMenuTree] = useState<MenuTreeNode[]>([])
  const [assignModalVisible, setAssignModalVisible] = useState(false)
  const [assigningRole, setAssigningRole] = useState<RoleRecord | null>(null)
  const [checkedMenuKeys, setCheckedMenuKeys] = useState<number[]>([])

  useEffect(() => {
    fetchMenuTree()
  }, [])

  async function fetchMenuTree() {
    const { data: res } = await getMenuTree()
    const response = res as ApiResponse<MenuTreeNode[]>
    setMenuTree(response.data ?? [])
  }

  const handleOpenAssign = async (record: RoleRecord) => {
    setAssigningRole(record)
    const { data: res } = await getRoleMenus(record.id)
    const response = res as ApiResponse<{ menuIds: number[] }>
    setCheckedMenuKeys(response.data?.menuIds ?? [])
    setAssignModalVisible(true)
  }

  const handleAssignSubmit = async () => {
    if (!assigningRole) return false
    return confirmSubmit2(async () => {
      await assignRoleMenus({ roleId: assigningRole.id, menuIds: checkedMenuKeys })
      message.success('权限分配成功')
      setAssigningRole(null)
    })
  }

  const handleSubmit = async (values: Record<string, any>) => {
    const payload = { ...values, status: values.status ? 1 : 0 }
    return confirmSubmit1(async () => {
      if (editingRecord) {
        await updateRole(editingRecord.id, payload)
        message.success('更新成功')
      } else {
        await createRole(payload)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteRole(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const handleStatusChange = async (id: number, newStatus: number) => {
    try {
      await updateRoleStatus(id, { status: newStatus })
      message.success('状态更新成功')
      actionRef.current?.reload()
    } catch {
      message.error('状态更新失败')
    }
  }

  const columns: ProColumns<RoleRecord>[] = [
    { title: '角色ID', dataIndex: 'id', width: 80, search: false },
    { title: '角色名称', dataIndex: 'roleName', width: 150 },
    { title: '权限字符', dataIndex: 'roleKey', width: 150 },
    { title: '排序', dataIndex: 'roleSort', width: 80, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Popconfirm
      title={`确定${Number(record.status) === 1 ? '停用' : '启用'}吗？`}
      onConfirm={() => handleStatusChange(record.id, Number(record.status) === 1 ? 0 : 1)}
    >
      <Switch checked={Number(record.status) === 1} size="small" />
    </Popconfirm>
      ),
    },
    { title: '备注', dataIndex: 'remark', width: 200, search: false, ellipsis: true },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 240,
      fixed: 'right',
      render: (_, record) => [
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
        <Button
          key="assign"
          type="link"
          size="small"
          onClick={() => handleOpenAssign(record)}
        >
          分配权限
        </Button>,
        <Popconfirm
          key="delete"
          title="确认删除该角色？"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<RoleRecord>
        headerTitle="角色管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1100 }}
        request={async (params) => {
          return safeProTableRequest<RoleRecord>(() =>
            getRoles({
              page: params.current,
              pageSize: params.pageSize,
              roleName: params.roleName,
              roleKey: params.roleKey,
              status: params.status,
            })
          )
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
            新增角色
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑角色' : '新增角色'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange1(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? { ...editingRecord, status: editingRecord.status === 1 }
            : { status: true, roleSort: 0 }
        }
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={520}
      >
        <ProFormText
          name="roleName"
          label="角色名称"
          placeholder="请输入角色名称"
          rules={[{ required: true, message: '请输入角色名称' }]}
        />
        <ProFormText
          name="roleKey"
          label="权限字符"
          placeholder="请输入权限字符"
          rules={[{ required: true, message: '请输入权限字符' }]}
        />
        <ProFormDigit
          name="roleSort"
          label="排序"
          min={0}
          fieldProps={{ precision: 0 }}
        />
        <ProFormSwitch name="status" label="状态" />
        <ProFormText
          name="remark"
          label="备注"
          placeholder="请输入备注"
        />
      </ModalForm>

      <ModalForm
        title={`分配权限 - ${assigningRole?.roleName ?? ''}`}
        open={assignModalVisible}
        onOpenChange={createHandleOpenChange2(setAssignModalVisible, () => setAssigningRole(null))}
        onFinish={handleAssignSubmit}
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={480}
      >
        <ProFormTreeSelect
          name="menuIds"
          label="菜单权限"
          fieldProps={{
            treeData: convertToTreeSelect(menuTree, 'menuName'),
            treeCheckable: true,
            showCheckedStrategy: TreeSelect.SHOW_ALL,
            placeholder: '请选择菜单权限',
            treeDefaultExpandAll: true,
            value: checkedMenuKeys,
            onChange: (val: number[]) => setCheckedMenuKeys(val),
          }}
        />
      </ModalForm>
    </>
  )
}
