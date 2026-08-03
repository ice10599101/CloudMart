import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm, Select } from 'antd'
import { getShipping, updateShippingStatus } from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

interface ShippingRecord {
  id: number
  orderId: string
  shippingNo: string
  carrier: string
  status: string
  createdAt: string
  updatedAt: string
}

const SHIPPING_STATUS_MAP: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待发货', color: 'default' },
  SHIPPED: { text: '已发货', color: 'processing' },
  IN_TRANSIT: { text: '运输中', color: 'blue' },
  DELIVERED: { text: '已签收', color: 'green' },
  RETURNED: { text: '已退回', color: 'red' },
}

const CARRIER_MAP: Record<string, string> = {
  SF: '顺丰速运',
  YTO: '圆通速递',
  ZTO: '中通快递',
  STO: '申通快递',
  YD: '韵达快递',
  JD: '京东物流',
  EMS: 'EMS',
}

const STATUS_TRANSITIONS: Record<string, string[]> = {
  PENDING: ['SHIPPED'],
  SHIPPED: ['IN_TRANSIT', 'RETURNED'],
  IN_TRANSIT: ['DELIVERED', 'RETURNED'],
  DELIVERED: [],
  RETURNED: [],
}

export default function Shipping() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [updatingId, setUpdatingId] = useState<number | null>(null)

  const handleUpdateStatus = async (id: number, newStatus: string) => {
    setUpdatingId(id)
    try {
      await updateShippingStatus(id, { status: newStatus })
      message.success('状态更新成功')
      actionRef.current?.reload()
    } finally {
      setUpdatingId(null)
    }
  }

  const columns: ProColumns<ShippingRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '订单号', dataIndex: 'orderId', width: 180 },
    { title: '物流单号', dataIndex: 'shippingNo', width: 200, search: false, ellipsis: true },
    {
      title: '承运商',
      dataIndex: 'carrier',
      width: 120,
      search: false,
      render: (_, record) => CARRIER_MAP[record.carrier] ?? record.carrier,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (_, record) => {
        const statusInfo = SHIPPING_STATUS_MAP[record.status]
        return <Tag color={statusInfo?.color ?? 'default'}>{statusInfo?.text ?? record.status}</Tag>
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      fixed: 'right',
      render: (_, record) => {
        const transitions = STATUS_TRANSITIONS[record.status] ?? []
        if (transitions.length === 0) return null
        return [
          <Select
            key="status"
            placeholder="更新状态"
            style={{ width: 120 }}
            loading={updatingId === record.id}
            onChange={(value: string) => handleUpdateStatus(record.id, value)}
            options={transitions.map((s) => ({
              label: SHIPPING_STATUS_MAP[s]?.text ?? s,
              value: s,
            }))}
          />,
        ]
      },
    },
  ]

  return (
    <ProTable<ShippingRecord>
      headerTitle="物流管理"
      actionRef={actionRef}
      rowKey="id"
      scroll={{ x: 1100 }}
      request={async (params) => {
        return safeProTableRequest<ShippingRecord>(() =>
          getShipping({
            page: params.current,
            pageSize: params.pageSize,
            orderId: params.orderId,
            status: params.status,
          })
        )
      }}
      columns={columns}
      pagination={{ defaultPageSize: 10, showSizeChanger: true }}
    />
  )
}
