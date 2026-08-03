import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormDigit,
  ProFormDateTimePicker,
  ProFormSwitch,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm, Modal, Descriptions } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getGroupActivities,
  createGroupActivity,
  updateGroupActivity,
  enableGroupActivity,
  disableGroupActivity,
  deleteGroupActivity,
  getGroupOrders,
} from '@/api/admin/business'
import type { ApiResponse } from '@/types/api'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface GroupActivityRecord {
  id: number
  name: string
  productId: number
  productName: string
  skuId: number
  originalPrice: number
  groupPrice: number
  targetNumber: number
  maxGroups: number
  currentGroups: number
  perUserLimit: number
  status: string
  startTime: string
  endTime: string
  createdAt: string
}

interface GroupOrderRecord {
  id: number
  activityId: number
  activityName: string
  leaderUserId: number
  leaderUsername: string
  currentNumber: number
  targetNumber: number
  status: string
  createdAt: string
  expiredAt: string
}

const ACTIVITY_STATUS_MAP: Record<string, { text: string; color: string }> = {
  ENABLED: { text: '已启用', color: 'green' },
  DISABLED: { text: '已禁用', color: 'red' },
  PENDING: { text: '待开始', color: 'default' },
  ENDED: { text: '已结束', color: 'default' },
}

const GROUP_ORDER_STATUS_MAP: Record<string, { text: string; color: string }> = {
  IN_PROGRESS: { text: '拼团中', color: 'processing' },
  SUCCESS: { text: '拼团成功', color: 'success' },
  FAILED: { text: '拼团失败', color: 'error' },
  EXPIRED: { text: '已过期', color: 'default' },
}

export default function GroupActivity() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<GroupActivityRecord | null>(null)
  const [orderModalVisible, setOrderModalVisible] = useState(false)
  const [groupOrders, setGroupOrders] = useState<GroupOrderRecord[]>([])
  const [orderLoading, setOrderLoading] = useState(false)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      const payload = {
        ...values,
        startTime: values.startTime?.format('YYYY-MM-DD HH:mm:ss'),
        endTime: values.endTime?.format('YYYY-MM-DD HH:mm:ss'),
      }
      if (editingRecord) {
        await updateGroupActivity(editingRecord.id, payload)
        message.success('编辑成功')
      } else {
        await createGroupActivity(payload)
        message.success('拼团活动创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleToggleStatus = async (record: GroupActivityRecord, enable: boolean) => {
    if (enable) {
      await enableGroupActivity(record.id)
    } else {
      await disableGroupActivity(record.id)
    }
    message.success(enable ? '已启用' : '已禁用')
    actionRef.current?.reload()
  }

  const handleViewOrders = async () => {
    setOrderLoading(true)
    setOrderModalVisible(true)
    try {
      const { data: res } = await getGroupOrders({ page: 1, pageSize: 100 })
      const response = res as ApiResponse<GroupOrderRecord[]>
      setGroupOrders(response.data ?? [])
    } finally {
      setOrderLoading(false)
    }
  }

  const orderColumns: ProColumns<GroupOrderRecord>[] = [
    { title: '拼团ID', dataIndex: 'id', width: 80 },
    { title: '活动名称', dataIndex: 'activityName', width: 160, ellipsis: true },
    { title: '团长用户ID', dataIndex: 'leaderUserId', width: 100 },
    { title: '团长用户名', dataIndex: 'leaderUsername', width: 120 },
    { title: '当前人数', dataIndex: 'currentNumber', width: 90 },
    { title: '要求人数', dataIndex: 'targetNumber', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = GROUP_ORDER_STATUS_MAP[record.status]
        return <Tag color={statusInfo?.color ?? 'default'}>{statusInfo?.text ?? record.status}</Tag>
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, valueType: 'dateTime' },
    { title: '过期时间', dataIndex: 'expiredAt', width: 170, valueType: 'dateTime' },
  ]

  const columns: ProColumns<GroupActivityRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '活动名称', dataIndex: 'name', width: 160 },
    { title: '商品名称', dataIndex: 'productName', width: 160, search: false, ellipsis: true },
    {
      title: '原价',
      dataIndex: 'originalPrice',
      width: 100,
      search: false,
      render: (_, record) => `¥${Number(record.originalPrice).toFixed(2)}`,
    },
    {
      title: '拼团价',
      dataIndex: 'groupPrice',
      width: 100,
      search: false,
      render: (_, record) => (
        <span style={{ color: '#FF4757', fontWeight: 600 }}>¥{Number(record.groupPrice).toFixed(2)}</span>
      ),
    },
    { title: '成团人数', dataIndex: 'targetNumber', width: 90, search: false },
    { title: '已参团', dataIndex: 'currentGroups', width: 90, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = ACTIVITY_STATUS_MAP[record.status]
        return <Tag color={statusInfo?.color ?? 'default'}>{statusInfo?.text ?? record.status}</Tag>
      },
    },
    { title: '开始时间', dataIndex: 'startTime', width: 170, valueType: 'dateTime', search: false },
    { title: '结束时间', dataIndex: 'endTime', width: 170, valueType: 'dateTime', search: false },
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
        record.status === 'DISABLED' && (
          <Popconfirm
            key="enable"
            title="确认启用该活动？"
            onConfirm={() => handleToggleStatus(record, true)}
          >
            <Button type="link" size="small">启用</Button>
          </Popconfirm>
        ),
        record.status === 'ENABLED' && (
          <Popconfirm
            key="disable"
            title="确认禁用该活动？"
            onConfirm={() => handleToggleStatus(record, false)}
          >
            <Button type="link" size="small" danger>禁用</Button>
          </Popconfirm>
        ),
        <Popconfirm
          key="delete"
          title="确定删除吗？"
          onConfirm={async () => {
            await deleteGroupActivity(record.id!)
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
      <ProTable<GroupActivityRecord>
        headerTitle="拼团活动管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1400 }}
        request={async (params) => {
          return safeProTableRequest<GroupActivityRecord>(() =>
            getGroupActivities({
              page: params.current,
              pageSize: params.pageSize,
              name: params.name,
              status: params.status,
            })
          )
        }}
        toolBarRender={() => [
          <Button
            key="orders"
            onClick={handleViewOrders}
          >
            查看拼团订单
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
            新增活动
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑拼团活动' : '新增拼团活动'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? {
                ...editingRecord,
                startTime: undefined,
                endTime: undefined,
              }
            : undefined
        }
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={560}
      >
        <ProFormText
          name="name"
          label="活动名称"
          placeholder="请输入活动名称"
          rules={[{ required: true, message: '请输入活动名称' }]}
        />
        <ProFormDigit
          name="productId"
          label="商品ID"
          min={1}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入商品ID' }]}
        />
        <ProFormDigit
          name="skuId"
          label="SKU ID"
          min={1}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入 SKU ID' }]}
        />
        <ProFormDigit
          name="originalPrice"
          label="原价"
          min={0.01}
          fieldProps={{ precision: 2 }}
          rules={[{ required: true, message: '请输入原价' }]}
        />
        <ProFormDigit
          name="groupPrice"
          label="拼团价"
          min={0.01}
          fieldProps={{ precision: 2 }}
          rules={[{ required: true, message: '请输入拼团价' }]}
        />
        <ProFormDigit
          name="targetNumber"
          label="成团人数"
          min={2}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入成团人数' }]}
        />
        <ProFormDigit
          name="maxGroups"
          label="最大开团数"
          min={0}
          fieldProps={{ precision: 0 }}
          extra="0表示不限"
          rules={[{ required: true, message: '请输入最大开团数' }]}
        />
        <ProFormDigit
          name="perUserLimit"
          label="每人限参团数"
          min={1}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入每人限参团数' }]}
        />
        <ProFormDateTimePicker
          name="startTime"
          label="开始时间"
          rules={[{ required: true, message: '请选择开始时间' }]}
        />
        <ProFormDateTimePicker
          name="endTime"
          label="结束时间"
          rules={[{ required: true, message: '请选择结束时间' }]}
        />
      </ModalForm>

      <Modal
        title="拼团订单"
        open={orderModalVisible}
        onCancel={() => setOrderModalVisible(false)}
        footer={null}
        width={1100}
      >
        <ProTable<GroupOrderRecord>
          columns={orderColumns}
          dataSource={groupOrders}
          rowKey="id"
          loading={orderLoading}
          search={false}
          pagination={{ pageSize: 10 }}
          toolBarRender={false}
        />
      </Modal>
    </>
  )
}
