import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormTextArea,
  ProFormSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm, Image } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getBrands,
  getBrand,
  createBrand,
  updateBrand,
  deleteBrand,
} from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import type { ApiResponse } from '@/types/api'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface BrandRecord {
  id: number
  name: string
  logo: string
  description: string
  status: number
  createdAt: string
  updatedAt: string
}

const BRAND_STATUS_MAP: Record<number, { text: string; color: string }> = {
  0: { text: '禁用', color: 'default' },
  1: { text: '正常', color: 'success' },
}

export default function Brands() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<BrandRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      const payload = { ...values }
      if (editingRecord) {
        await updateBrand(editingRecord.id, payload)
        message.success('更新成功')
      } else {
        await createBrand(payload)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const columns: ProColumns<BrandRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '品牌名称', dataIndex: 'name', width: 160 },
    {
      title: 'Logo',
      dataIndex: 'logo',
      width: 100,
      search: false,
      render: (_, record) =>
        record.logo ? (
          <Image src={record.logo} width={40} height={40} style={{ objectFit: 'contain' }} />
        ) : (
          '-'
        ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      width: 240,
      search: false,
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = BRAND_STATUS_MAP[record.status]
        return <Tag color={statusInfo?.color ?? 'default'}>{statusInfo?.text ?? '未知'}</Tag>
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="edit"
          type="link"
          size="small"
          onClick={async () => {
            const { data: res } = await getBrand(record.id)
            const response = res as ApiResponse<BrandRecord>
            const detail = response.data
            if (detail) {
              setEditingRecord(detail)
              setModalVisible(true)
            }
          }}
        >
          编辑
        </Button>,
        <Popconfirm
          key="toggle"
          title={record.status === 1 ? '确认禁用该品牌？' : '确认启用该品牌？'}
          onConfirm={async () => {
            await updateBrand(record.id, { status: record.status === 1 ? 0 : 1 })
            message.success(record.status === 1 ? '已禁用' : '已启用')
            actionRef.current?.reload()
          }}
        >
          <Button type="link" size="small" danger={record.status === 1}>
            {record.status === 1 ? '禁用' : '启用'}
          </Button>
        </Popconfirm>,
        <Popconfirm
          key="delete"
          title="确定删除吗？"
          onConfirm={async () => {
            await deleteBrand(record.id!)
            message.success('删除成功')
            actionRef.current?.reload()
          }}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<BrandRecord>
        headerTitle="品牌管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1100 }}
        request={async (params) => {
          return safeProTableRequest<BrandRecord>(() =>
            getBrands({
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
            新增品牌
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑品牌' : '新增品牌'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? { ...editingRecord }
            : { status: 1 }
        }
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={560}
      >
        <ProFormText
          name="name"
          label="品牌名称"
          placeholder="请输入品牌名称"
          rules={[{ required: true, message: '请输入品牌名称' }]}
        />
        <ProFormText
          name="logo"
          label="Logo URL"
          placeholder="请输入品牌 Logo 地址"
        />
        <ProFormTextArea
          name="description"
          label="品牌描述"
          placeholder="请输入品牌描述"
          fieldProps={{ rows: 3, maxLength: 500, showCount: true }}
        />
        <ProFormSelect
          name="status"
          label="状态"
          options={[
            { label: '正常', value: 1 },
            { label: '禁用', value: 0 },
          ]}
        />
      </ModalForm>
    </>
  )
}
