import { useRef, useState } from 'react'
import { ProTable, ModalForm, ProFormText, ProFormDigit, ProFormSelect, ProFormTextArea, ProFormList } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Tag, Popconfirm, Button, Tabs, Modal, Descriptions, InputNumber, Space } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getPickOrders,
  startPick,
  confirmPicked,
  confirmPacked,
  getInboundOrders,
  getInboundOrder,
  createPickOrder,
  createInboundOrder,
  receiveInboundItem,
  completeInboundOrder,
} from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

interface PickOrderRecord {
  id: number
  orderId: string
  warehouseCode: string
  status: string
  pickerName: string
  totalItems: number
  pickedItems: number
  createdAt: string
  startedAt: string
  pickedAt: string
  packedAt: string
}

interface InboundOrderRecord {
  id: number
  inboundNo: string
  supplierName: string
  warehouseCode: string
  totalQty: number
  status: string
  createdAt: string
  completedAt: string
}

const PICK_STATUS_MAP: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待拣货', color: 'default' },
  PICKING: { text: '拣货中', color: 'processing' },
  PICKED: { text: '已拣完', color: 'blue' },
  PACKED: { text: '已打包', color: 'green' },
}

const INBOUND_STATUS_MAP: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待入库', color: 'default' },
  INBOUND: { text: '入库中', color: 'processing' },
  COMPLETED: { text: '已完成', color: 'green' },
  CANCELLED: { text: '已取消', color: 'red' },
}

export default function Wms() {
  const message = useMessage()
  const pickActionRef = useRef<ActionType>(null)
  const inboundActionRef = useRef<ActionType>(null)
  const [activeTab, setActiveTab] = useState('pick')
  const [inboundDetail, setInboundDetail] = useState<Record<string, any> | null>(null)
  const [inboundDetailLoading, setInboundDetailLoading] = useState(false)
  // 拣货单/入库单创建与收货（对齐下游 AdminWmsController 写端点）
  const [pickCreateOpen, setPickCreateOpen] = useState(false)
  const [inboundCreateOpen, setInboundCreateOpen] = useState(false)
  const [receiveTargetId, setReceiveTargetId] = useState<number | null>(null)
  const [receiveItemId, setReceiveItemId] = useState<number | null>(null)
  const [receiveQuantity, setReceiveQuantity] = useState<number | null>(null)
  const [receiveSubmitting, setReceiveSubmitting] = useState(false)

  const fetchInboundDetail = async (id: number) => {
    setInboundDetailLoading(true)
    try {
      const { data: res } = await getInboundOrder(id)
      setInboundDetail((res as any)?.data ?? res ?? null)
    } catch {
      setInboundDetail(null)
    } finally {
      setInboundDetailLoading(false)
    }
  }

  const handleInboundDetailClose = () => {
    setInboundDetail(null)
  }

  const handleStartPick = async (id: number) => {
    await startPick(id)
    message.success('拣货已开始')
    pickActionRef.current?.reload()
  }

  const handleConfirmPicked = async (id: number) => {
    await confirmPicked(id)
    message.success('已确认拣货完成')
    pickActionRef.current?.reload()
  }

  const handleConfirmPacked = async (id: number) => {
    await confirmPacked(id)
    message.success('已确认打包完成')
    pickActionRef.current?.reload()
  }

  const handleReceive = async () => {
    if (receiveTargetId === null || receiveItemId === null || receiveQuantity === null) return
    setReceiveSubmitting(true)
    try {
      await receiveInboundItem(receiveTargetId, receiveItemId, receiveQuantity)
      message.success('收货登记成功')
      setReceiveTargetId(null)
      setReceiveItemId(null)
      setReceiveQuantity(null)
      inboundActionRef.current?.reload()
    } finally {
      setReceiveSubmitting(false)
    }
  }

  const handleCompleteInbound = async (id: number) => {
    await completeInboundOrder(id)
    message.success('入库单已完成')
    inboundActionRef.current?.reload()
  }

  const pickColumns: ProColumns<PickOrderRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '订单号', dataIndex: 'orderId', width: 180 },
    { title: '仓库编码', dataIndex: 'warehouseCode', width: 120, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = PICK_STATUS_MAP[record.status]
        return <Tag color={statusInfo?.color ?? 'default'}>{statusInfo?.text ?? record.status}</Tag>
      },
    },
    { title: '拣货员', dataIndex: 'pickerName', width: 100, search: false },
    { title: '商品总数', dataIndex: 'totalItems', width: 90, search: false },
    { title: '已拣数量', dataIndex: 'pickedItems', width: 90, search: false },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: 'right',
      render: (_, record) => [
        record.status === 'PENDING' && (
          <Popconfirm
            key="start"
            title="确认开始拣货？"
            onConfirm={() => handleStartPick(record.id)}
          >
            <Button type="link" size="small">开始拣货</Button>
          </Popconfirm>
        ),
        record.status === 'PICKING' && (
          <Popconfirm
            key="picked"
            title="确认拣货完成？"
            onConfirm={() => handleConfirmPicked(record.id)}
          >
            <Button type="link" size="small">确认拣完</Button>
          </Popconfirm>
        ),
        record.status === 'PICKED' && (
          <Popconfirm
            key="packed"
            title="确认打包完成？"
            onConfirm={() => handleConfirmPacked(record.id)}
          >
            <Button type="link" size="small">确认打包</Button>
          </Popconfirm>
        ),
      ],
    },
  ]

  const inboundColumns: ProColumns<InboundOrderRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '入库单号', dataIndex: 'inboundNo', width: 180 },
    { title: '供应商', dataIndex: 'supplierName', width: 140, search: false },
    { title: '仓库编码', dataIndex: 'warehouseCode', width: 120, search: false },
    { title: '总数量', dataIndex: 'totalQty', width: 90, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = INBOUND_STATUS_MAP[record.status]
        return <Tag color={statusInfo?.color ?? 'default'}>{statusInfo?.text ?? record.status}</Tag>
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, valueType: 'dateTime', search: false },
    { title: '完成时间', dataIndex: 'completedAt', width: 170, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      fixed: 'right',
      render: (_, record) => [
        <Button key="detail" type="link" size="small" onClick={() => fetchInboundDetail(record.id)}>
          详情
        </Button>,
        (record.status === 'PENDING' || record.status === 'INBOUND') && (
          <Button
            key="receive"
            type="link"
            size="small"
            onClick={() => {
              setReceiveTargetId(record.id)
              setReceiveItemId(null)
              setReceiveQuantity(null)
            }}
          >
            收货
          </Button>
        ),
        record.status === 'INBOUND' && (
          <Popconfirm key="complete" title="确认完成入库？完成后入库单关闭。" onConfirm={() => handleCompleteInbound(record.id)}>
            <Button type="link" size="small">完成入库</Button>
          </Popconfirm>
        ),
      ],
    },
  ]

  return (
    <>
    <Tabs
      activeKey={activeTab}
      onChange={setActiveTab}
      items={[
        {
          key: 'pick',
          label: '拣货单',
          children: (
            <ProTable<PickOrderRecord>
              headerTitle="拣货单管理"
              actionRef={pickActionRef}
              rowKey="id"
              scroll={{ x: 1300 }}
              toolBarRender={() => [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => setPickCreateOpen(true)}>
                  新建拣货单
                </Button>,
              ]}
              request={async (params) => {
                return safeProTableRequest<PickOrderRecord>(() =>
                  getPickOrders({
                    page: params.current,
                    pageSize: params.pageSize,
                    orderId: params.orderId,
                    status: params.status,
                  })
                )
              }}
              columns={pickColumns}
              pagination={{ defaultPageSize: 10, showSizeChanger: true }}
            />
          ),
        },
        {
          key: 'inbound',
          label: '入库单',
          children: (
            <ProTable<InboundOrderRecord>
              headerTitle="入库单管理"
              actionRef={inboundActionRef}
              rowKey="id"
              scroll={{ x: 1200 }}
              toolBarRender={() => [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => setInboundCreateOpen(true)}>
                  新建入库单
                </Button>,
              ]}
              request={async (params) => {
                return safeProTableRequest<InboundOrderRecord>(() =>
                  getInboundOrders({
                    page: params.current,
                    pageSize: params.pageSize,
                    inboundNo: params.inboundNo,
                    status: params.status,
                  })
                )
              }}
              columns={inboundColumns}
              pagination={{ defaultPageSize: 10, showSizeChanger: true }}
            />
          ),
        },
      ]}
    />

    <Modal
      title="入库单详情"
      open={!!inboundDetail}
      onCancel={handleInboundDetailClose}
      footer={null}
      width={640}
      loading={inboundDetailLoading}
    >
      {inboundDetail && (
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="ID">{inboundDetail.id}</Descriptions.Item>
          <Descriptions.Item label="入库单号">{inboundDetail.inboundNo}</Descriptions.Item>
          <Descriptions.Item label="供应商">{inboundDetail.supplierName}</Descriptions.Item>
          <Descriptions.Item label="仓库编码">{inboundDetail.warehouseCode}</Descriptions.Item>
          <Descriptions.Item label="总数量">{inboundDetail.totalQty}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={INBOUND_STATUS_MAP[inboundDetail.status]?.color ?? 'default'}>
              {INBOUND_STATUS_MAP[inboundDetail.status]?.text ?? inboundDetail.status}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">{inboundDetail.createdAt}</Descriptions.Item>
          <Descriptions.Item label="完成时间">{inboundDetail.completedAt ?? '-'}</Descriptions.Item>
        </Descriptions>
      )}
    </Modal>

    <ModalForm
      title="新建拣货单"
      open={pickCreateOpen}
      onOpenChange={setPickCreateOpen}
      onFinish={async (values: Record<string, any>) => {
        await createPickOrder({
          orderId: values.orderId,
          warehouseId: values.warehouseId,
          remark: values.remark,
        })
        message.success('拣货单已创建')
        pickActionRef.current?.reload()
        return true
      }}
      modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
      width={480}
    >
      <ProFormDigit
        name="orderId"
        label="订单 ID"
        rules={[{ required: true, message: '请输入订单 ID' }]}
        fieldProps={{ precision: 0 }}
      />
      <ProFormDigit
        name="warehouseId"
        label="仓库 ID"
        rules={[{ required: true, message: '请输入仓库 ID' }]}
        fieldProps={{ precision: 0 }}
      />
      <ProFormTextArea name="remark" label="备注" placeholder="选填" />
    </ModalForm>

    <ModalForm
      title="新建入库单"
      open={inboundCreateOpen}
      onOpenChange={setInboundCreateOpen}
      onFinish={async (values: Record<string, any>) => {
        await createInboundOrder({
          warehouseId: values.warehouseId,
          type: values.type,
          referenceNo: values.referenceNo,
          remark: values.remark,
          items: (values.items ?? []).map((item: Record<string, any>) => ({
            skuId: item.skuId,
            productName: item.productName,
            expectedQuantity: item.expectedQuantity,
            locationCode: item.locationCode,
          })),
        })
        message.success('入库单已创建')
        inboundActionRef.current?.reload()
        return true
      }}
      modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
      width={640}
    >
      <ProFormDigit
        name="warehouseId"
        label="仓库 ID"
        rules={[{ required: true, message: '请输入仓库 ID' }]}
        fieldProps={{ precision: 0 }}
      />
      <ProFormSelect
        name="type"
        label="类型"
        options={[
          { label: '采购入库', value: 'PURCHASE' },
          { label: '退货入库', value: 'RETURN' },
          { label: '调拨入库', value: 'TRANSFER' },
        ]}
        rules={[{ required: true, message: '请选择类型' }]}
      />
      <ProFormText name="referenceNo" label="关联单号" placeholder="选填" />
      <ProFormTextArea name="remark" label="备注" placeholder="选填" />
      <ProFormList
        name="items"
        label="入库明细"
        min={1}
        creatorButtonProps={{ creatorButtonText: '添加明细' }}
      >
        <Space align="baseline" wrap>
          <ProFormDigit name="skuId" label="SKU ID" rules={[{ required: true, message: '必填' }]} fieldProps={{ precision: 0 }} />
          <ProFormText name="productName" label="商品名称" rules={[{ required: true, message: '必填' }]} />
          <ProFormDigit name="expectedQuantity" label="预期数量" rules={[{ required: true, message: '必填' }]} fieldProps={{ precision: 0 }} min={1} />
          <ProFormText name="locationCode" label="库位编码" />
        </Space>
      </ProFormList>
    </ModalForm>

    <Modal
      title="收货入库"
      open={receiveTargetId !== null}
      onCancel={() => setReceiveTargetId(null)}
      onOk={handleReceive}
      confirmLoading={receiveSubmitting}
      okText="确认收货"
      width={420}
    >
      <Space direction="vertical" style={{ width: '100%' }} size={12}>
        <div>
          <div style={{ marginBottom: 4 }}>明细 ID</div>
          <InputNumber
            value={receiveItemId}
            onChange={(v) => setReceiveItemId(v)}
            precision={0}
            style={{ width: '100%' }}
            placeholder="入库单明细 ID"
          />
        </div>
        <div>
          <div style={{ marginBottom: 4 }}>实收数量</div>
          <InputNumber
            value={receiveQuantity}
            onChange={(v) => setReceiveQuantity(v)}
            precision={0}
            min={1}
            style={{ width: '100%' }}
            placeholder="本次实收数量"
          />
        </div>
      </Space>
    </Modal>
  </>
  )
}
