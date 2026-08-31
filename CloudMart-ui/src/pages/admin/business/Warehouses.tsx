import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormTextArea,
  ProFormSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getWarehouses,
  createWarehouse,
  updateWarehouse,
  deleteWarehouse,
} from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface WarehouseRecord {
  id: number
  name: string
  address: string
  contact: string
  phone: string
  status: number
  createdAt: string
  updatedAt: string
}

export default function Warehouses() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<WarehouseRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      const payload = { ...values }
      if (editingRecord) {
        await updateWarehouse(editingRecord.id, payload)
        message.success('更新成功')
      } else {
        await createWarehouse(payload)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteWarehouse(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const columns: ProColumns<WarehouseRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '仓库名称', dataIndex: 'name', width: 160 },
    {
      title: '地址',
      dataIndex: 'address',
      width: 240,
      search: false,
      ellipsis: true,
    },
    { title: '联系人', dataIndex: 'contact', width: 100, search: false },
    { title: '联系电话', dataIndex: 'phone', width: 140, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Tag color={record.status === 1 ? 'success' : 'default'}>
          {record.status === 1 ? '正常' : '停用'}
        </Tag>
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
          key="toggle"
          title={record.status === 1 ? '确认停用该仓库？' : '确认启用该仓库？'}
          onConfirm={async () => {
            await updateWarehouse(record.id, { status: record.status === 1 ? 0 : 1 })
            message.success(record.status === 1 ? '已停用' : '已启用')
            actionRef.current?.reload()
          }}
        >
          <Button type="link" size="small" danger={record.status === 1}>
            {record.status === 1 ? '停用' : '启用'}
          </Button>
        </Popconfirm>,
        <Popconfirm
          key="delete"
          title="确认删除该仓库？删除后不可恢复"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<WarehouseRecord>
        headerTitle="仓库管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1200 }}
        request={async (params) => {
          return safeProTableRequest<WarehouseRecord>(() =>
            getWarehouses({
              page: params.current,
              pageSize: params.pageSize,
              name: params.name,
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
            新增仓库
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑仓库' : '新增仓库'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? { ...editingRecord }
            : { status: 1 }
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={560}
      >
        <ProFormText
          name="name"
          label="仓库名称"
          placeholder="请输入仓库名称"
          rules={[{ required: true, message: '请输入仓库名称' }]}
        />
        <ProFormTextArea
          name="address"
          label="仓库地址"
          placeholder="请输入仓库地址"
          rules={[{ required: true, message: '请输入仓库地址' }]}
          fieldProps={{ rows: 2, maxLength: 200, showCount: true }}
        />
        <ProFormText
          name="contact"
          label="联系人"
          placeholder="请输入联系人"
          rules={[{ required: true, message: '请输入联系人' }]}
        />
        <ProFormText
          name="phone"
          label="联系电话"
          placeholder="请输入联系电话"
          rules={[
            { required: true, message: '请输入联系电话' },
            { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号码' },
          ]}
        />
        <ProFormSelect
          name="status"
          label="状态"
          options={[
            { label: '正常', value: 1 },
            { label: '停用', value: 0 },
          ]}
        />
      </ModalForm>
    </>
  )
}
