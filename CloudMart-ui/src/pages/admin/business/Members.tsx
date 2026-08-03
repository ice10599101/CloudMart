import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Switch, Tag } from 'antd'
import {
  getMembers,
  updateMember,
  updateMemberStatus,
} from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface MemberRecord {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  gender: number
  birthday: string
  level: number
  points: number
  balance: number
  status: number
  lastLoginTime: string
  createdAt: string
  updatedAt: string
}

const LEVEL_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '普通会员', color: 'default' },
  1: { label: '银牌会员', color: 'processing' },
  2: { label: '金牌会员', color: 'warning' },
  3: { label: '钻石会员', color: 'purple' },
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
      const payload = {
        nickname: values.nickname,
        email: values.email,
        gender: values.gender,
        birthday: values.birthday,
      }
      await updateMember(editingRecord.id, payload)
      message.success('更新成功')
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const columns: ProColumns<MemberRecord>[] = [
    { title: '会员ID', dataIndex: 'id', width: 80, search: false },
    { title: '小答号', dataIndex: 'username', width: 120 },
    { title: '昵称', dataIndex: 'nickname', width: 120, search: false },
    {
      title: '等级',
      dataIndex: 'level',
      width: 100,
      search: false,
      render: (_, record) => {
        const levelInfo = LEVEL_MAP[record.level] ?? { label: '未知', color: 'default' }
        return <Tag color={levelInfo.color}>{levelInfo.label}</Tag>
      },
    },
    {
      title: '积分',
      dataIndex: 'points',
      width: 80,
      search: false,
    },
    {
      title: '余额',
      dataIndex: 'balance',
      width: 100,
      search: false,
      render: (_, record) => `¥${Number(record.balance).toFixed(2)}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Popconfirm
          title={Number(record.status) === 1 ? '确认禁用该会员？' : '确认启用该会员？'}
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
        headerTitle="会员管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1200 }}
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
        title="编辑会员"
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? { ...editingRecord }
            : {}
        }
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
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
          name="email"
          label="邮箱"
          placeholder="请输入邮箱"
          rules={[{ type: 'email', message: '请输入正确的邮箱' }]}
        />
        <ProFormSelect
          name="gender"
          label="性别"
          options={[
            { label: '未知', value: 0 },
            { label: '男', value: 1 },
            { label: '女', value: 2 },
          ]}
        />
        <ProFormText
          name="birthday"
          label="生日"
          placeholder="如：2000-01-01"
        />
      </ModalForm>
    </>
  )
}
