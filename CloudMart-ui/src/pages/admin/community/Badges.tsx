import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormTextArea,
  ProFormDigit,
  ProFormSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Image, Popconfirm, Switch } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getAdminBadges,
  createAdminBadge,
  updateAdminBadge,
  deleteAdminBadge,
  grantBadge,
  updateBadgeStatus,
} from '@/api/admin/community'
import type { AdminBadgeRecord } from '@/api/admin/community'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

export default function Badges() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<AdminBadgeRecord | null>(null)
  const [grantModalVisible, setGrantModalVisible] = useState(false)
  const [grantingBadge, setGrantingBadge] = useState<AdminBadgeRecord | null>(null)
  const { confirmSubmit: confirmSubmit1, createHandleOpenChange: createHandleOpenChange1 } = useModalConfirm()
  const { confirmSubmit: confirmSubmit2, createHandleOpenChange: createHandleOpenChange2 } = useModalConfirm()

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit1(async () => {
      if (editingRecord) {
        await updateAdminBadge(editingRecord.id, values)
        message.success('更新成功')
      } else {
        await createAdminBadge(values)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteAdminBadge(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const handleGrant = async (values: Record<string, any>) => {
    if (!grantingBadge) return false
    return confirmSubmit2(async () => {
      await grantBadge(grantingBadge.id, values)
      message.success('授予成功')
      setGrantingBadge(null)
    })
  }

  const columns: ProColumns<AdminBadgeRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '名称', dataIndex: 'name', width: 140 },
    {
      title: '图标',
      dataIndex: 'icon',
      width: 80,
      search: false,
      render: (_, record) =>
        record.icon ? (
          record.icon.startsWith('http') ? (
            <Image src={record.icon} width={32} height={32} style={{ objectFit: 'cover' }} />
          ) : (
            <span style={{ fontSize: 24 }}>{record.icon}</span>
          )
        ) : (
          '-'
        ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      width: 200,
      ellipsis: true,
      search: false,
    },
    { title: '等级', dataIndex: 'level', width: 80, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Popconfirm
      title={`确定${Number(record.status) === 1 ? '停用' : '启用'}吗？`}
      onConfirm={async () => {
        try {
          await updateBadgeStatus(record.id, Number(record.status) === 1 ? 0 : 1)
          message.success('状态更新成功')
          actionRef.current?.reload()
        } catch {
          message.error('状态更新失败')
        }
      }}
    >
      <Switch checked={Number(record.status) === 1} size="small" />
    </Popconfirm>
      ),
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
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
          key="delete"
          title="确认删除该徽章？删除后不可恢复"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
        <Button
          key="grant"
          type="link"
          size="small"
          onClick={() => {
            setGrantingBadge(record)
            setGrantModalVisible(true)
          }}
        >
          授予
        </Button>,
      ],
    },
  ]

  return (
    <>
      <ProTable<AdminBadgeRecord>
        headerTitle="徽章管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1100 }}
        request={async (params) => {
          return safeProTableRequest<AdminBadgeRecord>(() =>
            getAdminBadges({
              page: params.current,
              pageSize: params.pageSize,
              name: params.name,
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
            新增徽章
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑徽章' : '新增徽章'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange1(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? {
                name: editingRecord.name,
                icon: editingRecord.icon,
                description: editingRecord.description,
                level: editingRecord.level,
                status: editingRecord.status,
              }
            : { status: 1, level: 1 }
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={480}
      >
        <ProFormText
          name="name"
          label="名称"
          placeholder="请输入徽章名称"
          rules={[{ required: true, message: '请输入徽章名称' }]}
        />
        <ProFormText
          name="icon"
          label="图标"
          placeholder="请输入图标URL或Emoji"
          rules={[{ required: true, message: '请输入图标' }]}
        />
        <ProFormTextArea
          name="description"
          label="描述"
          placeholder="请输入徽章描述"
          fieldProps={{ rows: 3 }}
          rules={[{ required: true, message: '请输入徽章描述' }]}
        />
        <ProFormDigit
          name="level"
          label="等级"
          min={1}
          max={99}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入等级' }]}
        />
        <ProFormSelect
          name="status"
          label="状态"
          options={[
            { label: '启用', value: 1 },
            { label: '禁用', value: 0 },
          ]}
          rules={[{ required: true, message: '请选择状态' }]}
        />
      </ModalForm>

      <ModalForm
        title={`授予徽章 - ${grantingBadge?.name ?? ''}`}
        open={grantModalVisible}
        onOpenChange={createHandleOpenChange2(setGrantModalVisible, () => setGrantingBadge(null))}
        onFinish={handleGrant}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={400}
      >
        <ProFormDigit
          name="userId"
          label="用户ID"
          min={1}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入用户ID' }]}
        />
      </ModalForm>
    </>
  )
}
