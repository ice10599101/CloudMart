import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Descriptions, Table, Popconfirm, Statistic, Card, Row, Col } from 'antd'
import {
  getOrders,
  getOrder,
  shipOrder,
  cancelOrder,
  approveRefund,
  rejectRefund,
  getTodayOrderStats,
} from '@/api/admin/business'
import type { ApiResponse } from '@/types/api'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface OrderRecord {
  id: number
  orderNo: string
  totalAmount: number
  payAmount: number
  discountAmount: number
  couponId: number | null
  status: string
  receiverName: string | null
  receiverPhone: string | null
  receiverAddress: string | null
  shippedAt: string | null
  completedAt: string | null
  refundReason: string | null
  refundRejectReason: string | null
  items: OrderItem[]
  createdAt: string
  updatedAt: string
}

interface OrderItem {
  id: number
  productId: number
  skuId: number
  productName: string
  skuImage: string
  skuAttributes: string
  price: number
  quantity: number
}

interface TodayStats {
  orderCount: number
  orderAmount: number
  paidCount: number
  paidAmount: number
}

const ORDER_STATUS_MAP: Record<string, { label: string; color: string }> = {
  PENDING_PAYMENT: { label: '待付款', color: 'default' },
  PAID: { label: '已付款', color: 'processing' },
  SHIPPED: { label: '已发货', color: 'cyan' },
  COMPLETED: { label: '已完成', color: 'success' },
  CANCELLED: { label: '已取消', color: 'error' },
  REFUNDING: { label: '退款中', color: 'warning' },
  REFUNDED: { label: '已退款', color: 'purple' },
}

export default function Orders() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [detailVisible, setDetailVisible] = useState(false)
  const [currentOrder, setCurrentOrder] = useState<OrderRecord | null>(null)
  const [shipModalVisible, setShipModalVisible] = useState(false)
  const [shipOrderId, setShipOrderId] = useState<number | null>(null)
  const [rejectModalVisible, setRejectModalVisible] = useState(false)
  const [rejectOrderId, setRejectOrderId] = useState<number | null>(null)
  const [todayStats, setTodayStats] = useState<TodayStats | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  async function fetchTodayStats() {
    try {
      const { data: res } = await getTodayOrderStats()
      const response = res as ApiResponse<TodayStats>
      setTodayStats(response.data ?? null)
    } catch {
      setTodayStats(null)
    }
  }

  const handleViewDetail = async (record: OrderRecord) => {
    const { data: res } = await getOrder(record.id)
    const response = res as ApiResponse<OrderRecord>
    setCurrentOrder(response.data ?? record)
    setDetailVisible(true)
  }

  const handleShip = async (values: Record<string, any>) => {
    if (!shipOrderId) return false
    return confirmSubmit(async () => {
      await shipOrder(shipOrderId, values)
      message.success('发货成功')
      setShipOrderId(null)
      actionRef.current?.reload()
    })
  }

  const handleCancel = async (id: number) => {
    await cancelOrder(id)
    message.success('取消订单成功')
    actionRef.current?.reload()
  }

  const handleApproveRefund = async (id: number) => {
    await approveRefund(id)
    message.success('退款已批准')
    actionRef.current?.reload()
  }

  const handleRejectRefund = async (values: Record<string, any>) => {
    if (!rejectOrderId) return false
    return confirmSubmit(async () => {
      await rejectRefund(rejectOrderId, values)
      message.success('退款已拒绝')
      setRejectOrderId(null)
      actionRef.current?.reload()
    })
  }

  const renderActions = (_: unknown, record: OrderRecord) => {
    const actions = [
      <Button
        key="detail"
        type="link"
        size="small"
        onClick={() => handleViewDetail(record)}
      >
        详情
      </Button>,
    ]

    if (record.status === 'PAID') {
      actions.push(
        <Button
          key="ship"
          type="link"
          size="small"
          onClick={() => {
            setShipOrderId(record.id)
            setShipModalVisible(true)
          }}
        >
          发货
        </Button>,
        <Popconfirm
          key="cancel"
          title="确认取消该订单？"
          onConfirm={() => handleCancel(record.id)}
        >
          <Button type="link" size="small" danger>取消</Button>
        </Popconfirm>,
      )
    }

    if (record.status === 'REFUNDING') {
      actions.push(
        <Popconfirm
          key="approve"
          title="确认批准退款？"
          onConfirm={() => handleApproveRefund(record.id)}
        >
          <Button type="link" size="small">批准退款</Button>
        </Popconfirm>,
        <Button
          key="reject"
          type="link"
          size="small"
          danger
          onClick={() => {
            setRejectOrderId(record.id)
            setRejectModalVisible(true)
          }}
        >
          拒绝退款
        </Button>,
      )
    }

    return actions
  }

  const columns: ProColumns<OrderRecord>[] = [
    { title: '订单ID', dataIndex: 'id', width: 80, search: false },
    { title: '订单号', dataIndex: 'orderNo', width: 200 },
    { title: '用户', dataIndex: 'username', width: 120, search: false },
    {
      title: '订单金额',
      dataIndex: 'totalAmount',
      width: 120,
      search: false,
      render: (_, record) => `¥${Number(record.totalAmount).toFixed(2)}`,
    },
    {
      title: '实付金额',
      dataIndex: 'payAmount',
      width: 120,
      search: false,
      render: (_, record) => `¥${Number(record.payAmount).toFixed(2)}`,
    },
    {
      title: '订单状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = ORDER_STATUS_MAP[record.status] ?? { label: '未知', color: 'default' }
        return <Tag color={statusInfo.color}>{statusInfo.label}</Tag>
      },
    },
    {
      title: '下单时间',
      dataIndex: 'createdAt',
      width: 180,
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '下单时间范围',
      dataIndex: 'createdAtRange',
      valueType: 'dateTimeRange',
      hideInTable: true,
      search: {
        transform: (value: [string, string]) => ({
          startTime: value[0],
          endTime: value[1],
        }),
      },
    },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: 'right',
      render: renderActions,
    },
  ]

  return (
    <>
      {todayStats && (
        <Row gutter={16} style={{ marginBottom: 16 }}>
          <Col span={6}>
            <Card size="small">
              <Statistic title="今日订单数" value={todayStats.orderCount} />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic title="今日订单金额" value={todayStats.orderAmount} prefix="¥" precision={2} />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic title="今日已付款" value={todayStats.paidCount} />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic title="今日已付金额" value={todayStats.paidAmount} prefix="¥" precision={2} />
            </Card>
          </Col>
        </Row>
      )}

      <ProTable<OrderRecord>
        headerTitle="订单管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1200 }}
        request={async (params) => {
          fetchTodayStats()
          return safeProTableRequest<OrderRecord>(() =>
            getOrders({
              page: params.current,
              pageSize: params.pageSize,
              orderNo: params.orderNo,
              status: params.status,
              startTime: params.startTime,
              endTime: params.endTime,
            }),
          )
        }}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title="订单发货"
        open={shipModalVisible}
        onOpenChange={createHandleOpenChange(setShipModalVisible, () => setShipOrderId(null))}
        onFinish={handleShip}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={480}
      >
        <ProFormText
          name="shippingCompany"
          label="物流公司"
          placeholder="请输入物流公司"
          rules={[{ required: true, message: '请输入物流公司' }]}
        />
        <ProFormText
          name="trackingNo"
          label="物流单号"
          placeholder="请输入物流单号"
          rules={[{ required: true, message: '请输入物流单号' }]}
        />
      </ModalForm>

      <ModalForm
        title="拒绝退款"
        open={rejectModalVisible}
        onOpenChange={createHandleOpenChange(setRejectModalVisible, () => setRejectOrderId(null))}
        onFinish={handleRejectRefund}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={480}
      >
        <ProFormTextArea
          name="reason"
          label="拒绝原因"
          placeholder="请输入拒绝退款原因"
          rules={[{ required: true, message: '请输入拒绝原因' }]}
          fieldProps={{ rows: 3 }}
        />
      </ModalForm>

      {currentOrder && (
        <ModalForm
          title={`订单详情 - ${currentOrder.orderNo}`}
          open={detailVisible}
          onOpenChange={setDetailVisible}
          onFinish={async () => true}
          modalProps={{ destroyOnHidden: true, footer: null, mask: { closable: false }, keyboard: false }}
          width={720}
          submitter={false}
        >
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="订单号">{currentOrder.orderNo}</Descriptions.Item>
            <Descriptions.Item label="用户">{currentOrder.receiverName}</Descriptions.Item>
            <Descriptions.Item label="订单金额">¥{Number(currentOrder.totalAmount).toFixed(2)}</Descriptions.Item>
            <Descriptions.Item label="实付金额">¥{Number(currentOrder.payAmount).toFixed(2)}</Descriptions.Item>
            <Descriptions.Item label="优惠金额">¥{Number(currentOrder.discountAmount).toFixed(2)}</Descriptions.Item>
            <Descriptions.Item label="订单状态">
              <Tag color={ORDER_STATUS_MAP[currentOrder.status]?.color ?? 'default'}>
                {ORDER_STATUS_MAP[currentOrder.status]?.label ?? '未知'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="下单时间">{currentOrder.createdAt}</Descriptions.Item>
            {currentOrder.shippedAt && <Descriptions.Item label="发货时间">{currentOrder.shippedAt}</Descriptions.Item>}
            {currentOrder.completedAt && <Descriptions.Item label="完成时间">{currentOrder.completedAt}</Descriptions.Item>}
            <Descriptions.Item label="收货人">{currentOrder.receiverName || '-'}</Descriptions.Item>
            <Descriptions.Item label="联系电话">{currentOrder.receiverPhone || '-'}</Descriptions.Item>
            <Descriptions.Item label="收货地址" span={2}>{currentOrder.receiverAddress || '-'}</Descriptions.Item>
          </Descriptions>

          <Table
            style={{ marginTop: 16 }}
            size="small"
            pagination={false}
            dataSource={currentOrder.items ?? []}
            rowKey="id"
            columns={[
              { title: '商品', dataIndex: 'productName', width: 200 },
              { title: '规格', dataIndex: 'skuAttributes', width: 120 },
              { title: '单价', dataIndex: 'price', width: 100, render: (v: number) => `¥${Number(v).toFixed(2)}` },
              { title: '数量', dataIndex: 'quantity', width: 80 },
              { title: '小计', width: 100, render: (_: unknown, r: OrderItem) => `¥${(r.price * r.quantity).toFixed(2)}` },
            ]}
          />
        </ModalForm>
      )}
    </>
  )
}
