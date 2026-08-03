import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Tag, Popconfirm, Button, Tabs, Modal, Descriptions } from 'antd'
import {
  getPickOrders,
  startPick,
  confirmPicked,
  confirmPacked,
  getInboundOrders,
  getInboundOrder,
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
      width: 80,
      fixed: 'right',
      render: (_, record) => [
        <Button key="detail" type="link" size="small" onClick={() => fetchInboundDetail(record.id)}>
          详情
        </Button>,
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
  </>
  )
}
