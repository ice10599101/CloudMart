import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Switch } from 'antd'
import {
  getMembers,
  updateMember,
  updateMemberStatus,
} from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

// 与后端契约对齐（mall-admin GET /business/members → mall-user UserDTO），
// 此前页面展示的 等级/积分/余额 后端契约中不存在，属虚构列已移除
interface MemberRecord {
  id: number
  username: string
  nickname: string
  email: string | null
  phone: string | null
  avatar: string | null
  status: number
  createdAt: string
}

export default function Members() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<MemberRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleStatusChange = async (record: MemberRecord, newStatus: number) => {
    try {
      await updateMemberStatus(record.id, { status: newStatus })
      message.success('状态更新成功')
      actionRef.current?.reload()
    } catch {
      message.error('状态更新失败')
    }
  }

  const handleSubmit = async (values: Record<string, any>) => {
    if (!editingRecord) return false
    return confirmSubmit(async () => {
      // 编辑契约仅支持 昵称/手机号/邮箱（AdminUpdateUserRequest）
      const payload = {
        nickname: values.nickname,
        phone: values.phone,
        email: values.email,
      }
      await updateMember(editingRecord.id, payload)
      message.success('更新成功')
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const columns: ProColumns<MemberRecord>[] = [
    { title: '用户ID', dataIndex: 'id', width: 80, search: false },
    { title: '小答号', dataIndex: 'username', width: 120 },
    { title: '昵称', dataIndex: 'nickname', width: 140, search: false, ellipsis: true },
    { title: '邮箱', dataIndex: 'email', width: 220, search: false, ellipsis: true, render: (_, record) => record.email || '-' },
    { title: '手机号', dataIndex: 'phone', width: 140, search: false, render: (_, record) => record.phone || '-' },
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
    { title: '注册时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
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
      ],
    },
  ]

  return (
    <>
      <ProTable<MemberRecord>
        headerTitle="用户管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1000 }}
        request={async (params) => {
          return safeProTableRequest<MemberRecord>(() =>
            getMembers({
              page: params.current,
              pageSize: params.pageSize,
              username: params.username,
              status: params.status,
            })
          )
        }}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title="编辑用户"
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? { ...editingRecord }
            : {}
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={520}
      >
        <ProFormText
          name="username"
          label="小答号"
          disabled
        />
        <ProFormText
          name="nickname"
          label="昵称"
          placeholder="请输入昵称"
        />
        <ProFormText
          name="phone"
          label="手机号"
          placeholder="请输入手机号"
          rules={[{ pattern: /^1\d{10}$/, message: '请输入正确的手机号' }]}
        />
        <ProFormText
          name="email"
          label="邮箱"
          placeholder="请输入邮箱"
          rules={[{ type: 'email', message: '请输入正确的邮箱' }]}
        />
      </ModalForm>
    </>
  )
}
