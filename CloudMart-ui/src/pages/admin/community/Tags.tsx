import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Image, Popconfirm, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getAdminTags,
  createAdminTag,
  updateAdminTag,
  deleteAdminTag,
  updateTagStatus,
} from '@/api/admin/community'
import type { AdminTagRecord } from '@/api/admin/community'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

export default function Tags() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<AdminTagRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      if (editingRecord) {
        await updateAdminTag(editingRecord.id, values)
        message.success('更新成功')
      } else {
        await createAdminTag(values)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteAdminTag(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const columns: ProColumns<AdminTagRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '标签名', dataIndex: 'name', width: 140 },
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
    { title: '帖子数', dataIndex: 'postCount', width: 100, search: false },
    {
      title: '热门',
      dataIndex: 'isHot',
      width: 80,
      search: false,
      render: (_, record) =>
        record.isHot ? <Tag color="red">热门</Tag> : <Tag>否</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Popconfirm
      title={`确定${Number(record.status) === 1 ? '停用' : '启用'}吗？`}
      onConfirm={async () => {
        try {
          await updateTagStatus(record.id, Number(record.status) === 1 ? 0 : 1)
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
      width: 140,
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
          title="确认删除该标签？删除后不可恢复"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<AdminTagRecord>
        headerTitle="标签管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1000 }}
        request={async (params) => {
          return safeProTableRequest<AdminTagRecord>(() =>
            getAdminTags({
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
            新增标签
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑标签' : '新增标签'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? { name: editingRecord.name, icon: editingRecord.icon, status: editingRecord.status }
            : { status: 1 }
        }
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={480}
      >
        <ProFormText
          name="name"
          label="标签名"
          placeholder="请输入标签名"
          rules={[{ required: true, message: '请输入标签名' }]}
        />
        <ProFormText
          name="icon"
          label="图标"
          placeholder="请输入图标URL或Emoji"
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
    </>
  )
}
