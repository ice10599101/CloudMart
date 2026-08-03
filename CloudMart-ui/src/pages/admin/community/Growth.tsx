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
import { Button, Popconfirm, Switch } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getAdminGrowthLevelConfigs,
  createAdminGrowthLevelConfig,
  updateAdminGrowthLevelConfig,
  deleteAdminGrowthLevelConfig,
  updateGrowthLevelStatus,
} from '@/api/admin/community'
import type { AdminGrowthLevelConfig } from '@/api/admin/community'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

export default function Growth() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<AdminGrowthLevelConfig | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      if (editingRecord) {
        await updateAdminGrowthLevelConfig(editingRecord.id, values)
        message.success('更新成功')
      } else {
        await createAdminGrowthLevelConfig(values)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteAdminGrowthLevelConfig(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const columns: ProColumns<AdminGrowthLevelConfig>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '等级', dataIndex: 'level', width: 80, search: false },
    { title: '称号', dataIndex: 'title', width: 140 },
    {
      title: '最低经验值',
      dataIndex: 'minExp',
      width: 120,
      search: false,
      render: (_, record) => <span style={{ color: 'var(--color-primary)', fontWeight: 600 }}>{record.minExp}</span>,
    },
    {
      title: '图标',
      dataIndex: 'icon',
      width: 80,
      search: false,
      render: (_, record) =>
        record.icon ? (
          record.icon.startsWith('http') ? (
            <img src={record.icon} alt="" style={{ width: 28, height: 28, objectFit: 'cover' }} />
          ) : (
            <span style={{ fontSize: 22 }}>{record.icon}</span>
          )
        ) : (
          '-'
        ),
    },
    {
      title: '权益',
      dataIndex: 'benefits',
      width: 200,
      ellipsis: true,
      search: false,
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
          await updateGrowthLevelStatus(record.id, Number(record.status) === 1 ? 0 : 1)
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
      width: 160,
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
          title="确认删除该等级配置？删除后不可恢复"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<AdminGrowthLevelConfig>
        headerTitle="成长配置"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1100 }}
        request={async (params) => {
          return safeProTableRequest<AdminGrowthLevelConfig>(() =>
            getAdminGrowthLevelConfigs({
              page: params.current,
              pageSize: params.pageSize,
              title: params.title,
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
            新增等级
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑等级配置' : '新增等级配置'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? {
                level: editingRecord.level,
                title: editingRecord.title,
                minExp: editingRecord.minExp,
                icon: editingRecord.icon,
                benefits: editingRecord.benefits,
                status: editingRecord.status,
              }
            : { status: 1, level: 1, minExp: 0 }
        }
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={480}
      >
        <ProFormDigit
          name="level"
          label="等级"
          min={1}
          max={99}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入等级' }]}
        />
        <ProFormText
          name="title"
          label="称号"
          placeholder="请输入等级称号"
          rules={[{ required: true, message: '请输入等级称号' }]}
        />
        <ProFormDigit
          name="minExp"
          label="最低经验值"
          min={0}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入最低经验值' }]}
        />
        <ProFormText
          name="icon"
          label="图标"
          placeholder="请输入图标URL或Emoji"
        />
        <ProFormTextArea
          name="benefits"
          label="权益"
          placeholder="请输入该等级享有的权益"
          fieldProps={{ rows: 3 }}
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
