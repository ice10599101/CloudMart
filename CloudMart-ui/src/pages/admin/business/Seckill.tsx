import { useRef, useState, useEffect } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormDigit,
  ProFormDateTimePicker,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Tabs, Popconfirm, Card, Select } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getSeckillActivities,
  createSeckillActivity,
  updateSeckillActivity,
  updateSeckillActivityStatus,
  deleteSeckillActivity,
  getSeckillProducts,
  createSeckillProduct,
  deleteSeckillProduct,
} from '@/api/admin/business'
import type { ApiResponse } from '@/types/api'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface ActivityRecord {
  id: number
  name: string
  description: string
  startTime: string
  endTime: string
  status: number
  createdAt: string
  updatedAt: string
}

interface SeckillProductRecord {
  id: number
  activityId: number
  skuId: number
  seckillPrice: number
  originalPrice: number
  totalStock: number
  availableStock: number
  perUserLimit: number
  status: string
  createdAt: string
}

const ACTIVITY_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '未开始', color: 'default' },
  1: { label: '进行中', color: 'success' },
  2: { label: '已结束', color: 'error' },
  3: { label: '已禁用', color: 'warning' },
}

export default function Seckill() {
  const message = useMessage()
  const [activeTab, setActiveTab] = useState('activities')
  const activityActionRef = useRef<ActionType>(null)
  const productActionRef = useRef<ActionType>(null)
  const [activityModalVisible, setActivityModalVisible] = useState(false)
  const [editingActivityRecord, setEditingActivityRecord] = useState<ActivityRecord | null>(null)
  const [productModalVisible, setProductModalVisible] = useState(false)
  const [activityList, setActivityList] = useState<ActivityRecord[]>([])
  const [selectedActivityId, setSelectedActivityId] = useState<number | null>(null)
  const { confirmSubmit: confirmSubmitActivity, createHandleOpenChange: createHandleOpenChangeActivity } = useModalConfirm()
  const { confirmSubmit: confirmSubmitProduct, createHandleOpenChange: createHandleOpenChangeProduct } = useModalConfirm()

  async function fetchActivities() {
    const { data: res } = await getSeckillActivities({ pageSize: 200 })
    const response = res as ApiResponse<ActivityRecord[]>
    const list = response.data ?? []
    setActivityList(list)
    if (list.length > 0 && !selectedActivityId) {
      setSelectedActivityId(list[0].id)
    }
  }

  useEffect(() => {
    fetchActivities()
  }, [])

  const handleCreateActivity = async (values: Record<string, any>) => {
    return confirmSubmitActivity(async () => {
      if (editingActivityRecord) {
        await updateSeckillActivity(editingActivityRecord.id, values)
        message.success('活动编辑成功')
      } else {
        await createSeckillActivity(values)
        message.success('活动创建成功')
      }
      setEditingActivityRecord(null)
      fetchActivities()
      activityActionRef.current?.reload()
    })
  }

  const handleToggleActivityStatus = async (activityId: number, targetStatus: number) => {
    await updateSeckillActivityStatus(activityId, { status: targetStatus })
    message.success('状态更新成功')
    fetchActivities()
    activityActionRef.current?.reload()
  }

  const handleCreateProduct = async (values: Record<string, any>) => {
    if (!selectedActivityId) {
      message.warning('请先选择活动')
      return false
    }
    return confirmSubmitProduct(async () => {
      await createSeckillProduct(selectedActivityId, values)
      message.success('秒杀商品添加成功')
      productActionRef.current?.reload()
    })
  }

  const handleDeleteProduct = async (id: number) => {
    await deleteSeckillProduct(id)
    message.success('删除成功')
    productActionRef.current?.reload()
  }

  const activityColumns: ProColumns<ActivityRecord>[] = [
    { title: '活动ID', dataIndex: 'id', width: 80, search: false },
    { title: '活动名称', dataIndex: 'name', width: 200, ellipsis: true },
    { title: '活动描述', dataIndex: 'description', width: 200, search: false, ellipsis: true },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      width: 180,
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '结束时间',
      dataIndex: 'endTime',
      width: 180,
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = ACTIVITY_STATUS_MAP[record.status] ?? { label: '未知', color: 'default' }
        return <Tag color={statusInfo.color}>{statusInfo.label}</Tag>
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 240,
      fixed: 'right',
      render: (_, record) => {
        const actions: React.ReactNode[] = []
        actions.push(
          <Button
            key="edit"
            type="link"
            size="small"
            onClick={() => {
              setEditingActivityRecord(record)
              setActivityModalVisible(true)
            }}
          >
            编辑
          </Button>,
        )
        if (record.status === 0) {
          actions.push(
            <Popconfirm
              key="start"
              title="确认开始该活动？"
              onConfirm={() => handleToggleActivityStatus(record.id, 1)}
            >
              <Button type="link" size="small">开始</Button>
            </Popconfirm>,
          )
        }
        if (record.status === 1) {
          actions.push(
            <Popconfirm
              key="disable"
              title="确认禁用该活动？"
              onConfirm={() => handleToggleActivityStatus(record.id, 3)}
            >
              <Button type="link" size="small" danger>禁用</Button>
            </Popconfirm>,
          )
        }
        if (record.status === 3) {
          actions.push(
            <Popconfirm
              key="enable"
              title="确认启用该活动？"
              onConfirm={() => handleToggleActivityStatus(record.id, 1)}
            >
              <Button type="link" size="small">启用</Button>
            </Popconfirm>,
          )
        }
        actions.push(
          <Popconfirm
            key="delete"
            title="确定删除吗？"
            onConfirm={async () => {
              await deleteSeckillActivity(record.id!)
              message.success('删除成功')
              fetchActivities()
              activityActionRef.current?.reload()
            }}
          >
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>,
        )
        actions.push(
          <Button
            key="products"
            type="link"
            size="small"
            onClick={() => {
              setSelectedActivityId(record.id)
              setActiveTab('products')
            }}
          >
            商品
          </Button>,
        )
        return actions
      },
    },
  ]

  const productColumns: ProColumns<SeckillProductRecord>[] = [
    { title: '商品ID', dataIndex: 'id', width: 80 },
    {
      title: '原价',
      dataIndex: 'originalPrice',
      width: 100,
      render: (_, record) => `¥${Number(record.originalPrice).toFixed(2)}`,
    },
    {
      title: '秒杀价',
      dataIndex: 'seckillPrice',
      width: 100,
      render: (_, record) => (
        <span style={{ color: '#FF4757', fontWeight: 'bold' }}>
          ¥{Number(record.seckillPrice).toFixed(2)}
        </span>
      ),
    },
    {
      title: '总库存',
      dataIndex: 'totalStock',
      width: 80,
    },
    {
      title: '剩余库存',
      dataIndex: 'availableStock',
      width: 100,
      render: (_, record) => (
        <span style={{ color: record.availableStock <= 5 ? '#FF4757' : undefined }}>
          {record.availableStock}
        </span>
      ),
    },
    {
      title: '限购',
      dataIndex: 'perUserLimit',
      width: 80,
      render: (_, record) => `${record.perUserLimit}件/人`,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime' },
    {
      title: '操作',
      valueType: 'option',
      width: 80,
      fixed: 'right',
      render: (_, record) => [
        <Popconfirm
          key="delete"
          title="确认删除该秒杀商品？"
          onConfirm={() => handleDeleteProduct(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  const activityOptions = activityList.map((a) => ({
    label: `${a.name}（ID: ${a.id}）`,
    value: a.id,
  }))

  return (
    <Card>
      <Tabs
        activeKey={activeTab}
        onChange={(key) => setActiveTab(key)}
        items={[
          {
            key: 'activities',
            label: '秒杀活动',
            children: (
              <ProTable<ActivityRecord>
                headerTitle="秒杀活动列表"
                actionRef={activityActionRef}
                rowKey="id"
                scroll={{ x: 1200 }}
                request={async (params) => {
                  return safeProTableRequest<ActivityRecord>(() =>
                    getSeckillActivities({
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
                      setEditingActivityRecord(null)
                      setActivityModalVisible(true)
                    }}
                  >
                    新增活动
                  </Button>,
                ]}
                columns={activityColumns}
                pagination={{ defaultPageSize: 10, showSizeChanger: true }}
              />
            ),
          },
          {
            key: 'products',
            label: '秒杀商品',
            children: (
              <>
                <div style={{ marginBottom: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
                  <span>选择活动：</span>
                  <Select
                    value={selectedActivityId}
                    onChange={(val: number) => {
                      setSelectedActivityId(val)
                      productActionRef.current?.reload()
                    }}
                    placeholder="请选择活动"
                    style={{ width: 300 }}
                    options={activityOptions}
                  />
                </div>
                {selectedActivityId ? (
                  <ProTable<SeckillProductRecord>
                    headerTitle="秒杀商品列表"
                    actionRef={productActionRef}
                    rowKey="id"
                    scroll={{ x: 1100 }}
                    search={false}
                    request={async (params) => {
                      return safeProTableRequest<SeckillProductRecord>(() =>
                        getSeckillProducts(selectedActivityId, {
                          page: params.current,
                          pageSize: params.pageSize,
                        })
                      )
                    }}
                    toolBarRender={() => [
                      <Button
                        key="add"
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => setProductModalVisible(true)}
                      >
                        新增秒杀商品
                      </Button>,
                    ]}
                    columns={productColumns}
                    pagination={{ defaultPageSize: 10, showSizeChanger: true }}
                  />
                ) : (
                  <div style={{ textAlign: 'center', padding: 40, color: 'var(--color-text-tertiary)' }}>
                    请先选择一个秒杀活动
                  </div>
                )}
              </>
            ),
          },
        ]}
      />

      <ModalForm
        title={editingActivityRecord ? '编辑秒杀活动' : '新增秒杀活动'}
        open={activityModalVisible}
        onOpenChange={createHandleOpenChangeActivity(setActivityModalVisible, () => setEditingActivityRecord(null))}
        onFinish={handleCreateActivity}
        initialValues={
          editingActivityRecord
            ? {
                name: editingActivityRecord.name,
                description: editingActivityRecord.description,
                startTime: undefined,
                endTime: undefined,
              }
            : undefined
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={520}
      >
        <ProFormText
          name="name"
          label="活动名称"
          placeholder="请输入活动名称"
          rules={[{ required: true, message: '请输入活动名称' }]}
        />
        <ProFormText
          name="description"
          label="活动描述"
          placeholder="请输入活动描述"
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

      <ModalForm
        title="新增秒杀商品"
        open={productModalVisible}
        onOpenChange={createHandleOpenChangeProduct(setProductModalVisible)}
        onFinish={handleCreateProduct}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={520}
      >
        <ProFormDigit
          name="skuId"
          label="SKU ID"
          min={1}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入 SKU ID' }]}
        />
        <ProFormDigit
          name="seckillPrice"
          label="秒杀价格"
          min={0}
          fieldProps={{ precision: 2, prefix: '¥' }}
          rules={[{ required: true, message: '请输入秒杀价格' }]}
        />
        <ProFormDigit
          name="originalPrice"
          label="原价"
          min={0}
          fieldProps={{ precision: 2, prefix: '¥' }}
          rules={[{ required: true, message: '请输入原价' }]}
        />
        <ProFormDigit
          name="totalStock"
          label="秒杀库存"
          min={1}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入秒杀库存' }]}
        />
        <ProFormDigit
          name="perUserLimit"
          label="每人限购"
          min={1}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入每人限购数量' }]}
          initialValue={1}
        />
      </ModalForm>
    </Card>
  )
}
