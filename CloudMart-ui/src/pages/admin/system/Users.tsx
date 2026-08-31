import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormSelect,
  ProFormDigit,
  ProFormSwitch,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Switch, Tag } from 'antd'
import { PlusOutlined, DownloadOutlined } from '@ant-design/icons'
import {
  getUsers,
  createUser,
  updateUser,
  deleteUser,
  updateUserStatus,
  resetPassword,
} from '@/api/admin/system'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface UserRecord {
  id: number
  username: string
  nickname: string
  email: string
  status: number
  deptId: number
  deptName: string
  postIds: number[]
  roleIds: number[]
  createdAt: string
  updatedAt: string
}

export default function Users() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<UserRecord | null>(null)

  const handleStatusChange = async (record: UserRecord, newStatus: number) => {
    try {
      await updateUserStatus(record.id, { status: newStatus })
      message.success('状态更新成功')
      actionRef.current?.reload()
    } catch {
      message.error('状态更新失败')
    }
  }

  const handleResetPassword = async (record: UserRecord) => {
    await resetPassword({ userId: record.id })
    message.success('密码已重置为默认密码')
  }

  const handleDelete = async (id: number) => {
    await deleteUser(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      if (editingRecord) {
        await updateUser(editingRecord.id, values)
        message.success('更新成功')
      } else {
        await createUser(values)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const columns: ProColumns<UserRecord>[] = [
    { title: '用户ID', dataIndex: 'id', width: 80, search: false },
    { title: '小答号', dataIndex: 'username', width: 120 },
    { title: '昵称', dataIndex: 'nickname', width: 120, search: false },
    { title: '邮箱', dataIndex: 'email', width: 180, search: false },
    {
      title: '部门',
      dataIndex: 'deptName',
      width: 120,
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Popconfirm
          title={Number(record.status) === 1 ? '确认禁用该用户？' : '确认启用该用户？'}
          onConfirm={() => handleStatusChange(record, Number(record.status) === 1 ? 0 : 1)}
        >
          <Switch
            checked={Number(record.status) === 1}
            checkedChildren="正常"
            unCheckedChildren="禁用"
          />
        </Popconfirm>
      ),
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 260,
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
        <Popconfirm
          key="reset"
          title="确认重置该用户密码？"
          onConfirm={() => handleResetPassword(record)}
        >
          <Button type="link" size="small">重置密码</Button>
        </Popconfirm>,
        <Popconfirm
          key="delete"
          title="确认删除该用户？"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<UserRecord>
        headerTitle="用户管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1200 }}
        request={async (params) => {
          return safeProTableRequest<UserRecord>(() =>
            getUsers({
              page: params.current,
              pageSize: params.pageSize,
              username: params.username,
              status: params.status,
            })
          )
        }}
        toolBarRender={() => [
          <Button
            key="export"
            icon={<DownloadOutlined />}
            onClick={async () => {
              try {
                const res = await getUsers({ page: 1, pageSize: 1000 })
                const data = (res.data as { data?: UserRecord[] }).data ?? []
                if (data.length === 0) {
                  message.warning('暂无数据可导出')
                  return
                }
                const bom = '\uFEFF'
                const headers = ['ID', '用户名', '昵称', '邮箱', '状态', '部门', '创建时间']
                const rows = data.map((r) => [
                  String(r.id), r.username ?? '', r.nickname ?? '', r.email ?? '',
                  r.status === 1 ? '正常' : '禁用', r.deptName ?? '', r.createdAt ?? '',
                ])
                const csv = [headers.join(','), ...rows.map((row) => row.map((c) => `"${c.replace(/"/g, '""')}"`).join(','))].join('\n')
                const blob = new Blob([bom + csv], { type: 'text/csv;charset=utf-8;' })
                const link = document.createElement('a')
                link.href = URL.createObjectURL(blob)
                link.download = '用户数据.csv'
                link.click()
                URL.revokeObjectURL(link.href)
                message.success('导出成功')
              } catch {
                message.error('导出失败')
              }
            }}
          >
            导出CSV
          </Button>,
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingRecord(null)
              setModalVisible(true)
            }}
          >
            新增用户
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑用户' : '新增用户'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? { ...editingRecord, status: editingRecord.status === 1 }
            : { status: true }
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={520}
      >
        <ProFormText
          name="username"
          label="小答号"
          disabled={!!editingRecord}
          rules={[{ required: true, message: '请输入小答号' }]}
        />
        {!editingRecord && (
          <ProFormText.Password
            name="password"
            label="密码"
            placeholder="请输入密码"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, message: '密码至少6位' },
            ]}
          />
        )}
        <ProFormText
          name="nickname"
          label="昵称"
          placeholder="请输入昵称"
        />
        <ProFormText
          name="email"
          label="邮箱"
          placeholder="请输入邮箱"
          rules={[{ type: 'email', message: '请输入正确的邮箱' }]}
        />
        <ProFormSwitch name="status" label="状态" />
      </ModalForm>
    </>
  )
}
