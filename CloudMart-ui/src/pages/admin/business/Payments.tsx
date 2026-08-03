import { useRef, useState } from 'react'
import { ProTable, ProFormSelect, ProFormTextArea } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Descriptions, Modal, Tag, Popconfirm, Input } from 'antd'
import { getPayments, getPaymentByOrder, refundPayment } from '@/api/admin/business'
import type { ApiResponse } from '@/types/api'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

interface PaymentRecord {
  id: number
  orderId: string
  paymentNo: string
  userId: number
  amount: number
  paymentMethod: string
  status: string
  paidAt: string
  createdAt: string
}

interface PaymentDetail {
  id: number
  orderId: string
  paymentNo: string
  userId: number
  username: string
  amount: number
  paymentMethod: string
  status: string
  transactionId: string
  paidAt: string
  refundAmount: number
  refundStatus: string
  createdAt: string
  updatedAt: string
}

const PAYMENT_STATUS_MAP: Record<string, { text: string; status: 'success' | 'processing' | 'warning' | 'error' | 'default' }> = {
  PENDING: { text: '待支付', status: 'default' },
  PAID: { text: '已支付', status: 'success' },
  REFUNDING: { text: '退款中', status: 'processing' },
  REFUNDED: { text: '已退款', status: 'warning' },
  FAILED: { text: '支付失败', status: 'error' },
  CLOSED: { text: '已关闭', status: 'default' },
}

export default function Payments() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [detailVisible, setDetailVisible] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [currentDetail, setCurrentDetail] = useState<PaymentDetail | null>(null)
  const [refundVisible, setRefundVisible] = useState(false)
  const [refundRecord, setRefundRecord] = useState<PaymentRecord | null>(null)
  const [refundReason, setRefundReason] = useState('')
  const [refundLoading, setRefundLoading] = useState(false)

  const handleViewDetail = async (orderId: string) => {
    setDetailLoading(true)
    setDetailVisible(true)
    try {
      const { data: res } = await getPaymentByOrder(orderId)
      const response = res as ApiResponse<PaymentDetail>
      setCurrentDetail(response.data ?? null)
    } finally {
      setDetailLoading(false)
    }
  }

  const handleRefund = async () => {
    if (!refundRecord) return
    setRefundLoading(true)
    try {
      await refundPayment(refundRecord.id, { reason: refundReason })
      message.success('退款申请已提交')
      setRefundVisible(false)
      setRefundRecord(null)
      setRefundReason('')
      actionRef.current?.reload()
    } finally {
      setRefundLoading(false)
    }
  }

  const columns: ProColumns<PaymentRecord>[] = [
    { title: '支付ID', dataIndex: 'id', width: 80, search: false },
    { title: '订单号', dataIndex: 'orderId', width: 180 },
    { title: '支付单号', dataIndex: 'paymentNo', width: 200, search: false, ellipsis: true },
    { title: '用户ID', dataIndex: 'userId', width: 80, search: false },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 120,
      search: false,
      render: (_, record) => `¥${Number(record.amount).toFixed(2)}`,
    },
    {
      title: '支付方式',
      dataIndex: 'paymentMethod',
      width: 100,
      search: false,
      valueEnum: {
        ALIPAY: { text: '支付宝' },
        WECHAT: { text: '微信' },
        BANK_CARD: { text: '银行卡' },
        BALANCE: { text: '余额' },
      },
    },
    {
      title: '支付状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(PAYMENT_STATUS_MAP).map(([key, val]) => [key, { text: val.text, status: val.status }]),
      ),
    },
    { title: '支付时间', dataIndex: 'paidAt', width: 180, valueType: 'dateTime', search: false },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      fixed: 'right',
      render: (_, record) => [
        <Button key="detail" type="link" size="small" onClick={() => handleViewDetail(record.orderId)}>
          详情
        </Button>,
        record.status === 'PAID' && (
          <Popconfirm
            key="refund"
            title="确认对该笔支付发起退款？"
            onConfirm={() => {
              setRefundRecord(record)
              setRefundVisible(true)
            }}
          >
            <Button type="link" size="small" danger>退款</Button>
          </Popconfirm>
        ),
      ],
    },
  ]

  return (
    <>
      <ProTable<PaymentRecord>
        headerTitle="支付管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1400 }}
        request={async (params) => {
          return safeProTableRequest<PaymentRecord>(() =>
            getPayments({
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

      <Modal
        title="支付详情"
        open={detailVisible}
        onCancel={() => {
          setDetailVisible(false)
          setCurrentDetail(null)
        }}
        footer={null}
        width={700}
        loading={detailLoading}
      >
        {currentDetail && (
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="支付ID">{currentDetail.id}</Descriptions.Item>
            <Descriptions.Item label="订单号">{currentDetail.orderId}</Descriptions.Item>
            <Descriptions.Item label="支付单号">{currentDetail.paymentNo}</Descriptions.Item>
            <Descriptions.Item label="交易流水号">{currentDetail.transactionId}</Descriptions.Item>
            <Descriptions.Item label="用户ID">{currentDetail.userId}</Descriptions.Item>
            <Descriptions.Item label="用户名">{currentDetail.username}</Descriptions.Item>
            <Descriptions.Item label="金额">¥{Number(currentDetail.amount).toFixed(2)}</Descriptions.Item>
            <Descriptions.Item label="支付方式">{currentDetail.paymentMethod}</Descriptions.Item>
            <Descriptions.Item label="支付状态">
              <Tag color={PAYMENT_STATUS_MAP[currentDetail.status]?.status ?? 'default'}>
                {PAYMENT_STATUS_MAP[currentDetail.status]?.text ?? currentDetail.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="退款金额">
              {currentDetail.refundAmount > 0 ? `¥${Number(currentDetail.refundAmount).toFixed(2)}` : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="退款状态">{currentDetail.refundStatus || '-'}</Descriptions.Item>
            <Descriptions.Item label="支付时间">{currentDetail.paidAt || '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{currentDetail.createdAt}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{currentDetail.updatedAt}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      <Modal
        title="退款操作"
        open={refundVisible}
        onCancel={() => {
          setRefundVisible(false)
          setRefundRecord(null)
          setRefundReason('')
        }}
        onOk={handleRefund}
        confirmLoading={refundLoading}
        okText="确认退款"
        width={480}
      >
        {refundRecord && (
          <div style={{ marginBottom: 16 }}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="支付ID">{refundRecord.id}</Descriptions.Item>
              <Descriptions.Item label="订单号">{refundRecord.orderId}</Descriptions.Item>
              <Descriptions.Item label="退款金额">
                <span style={{ color: '#FF4757', fontWeight: 600 }}>
                  ¥{Number(refundRecord.amount).toFixed(2)}
                </span>
              </Descriptions.Item>
            </Descriptions>
          </div>
        )}
        <div style={{ marginBottom: 8 }}>退款原因：</div>
        <Input.TextArea
          value={refundReason}
          onChange={(e) => setRefundReason(e.target.value)}
          placeholder="请输入退款原因"
          rows={3}
          maxLength={200}
          showCount
        />
      </Modal>
    </>
  )
}
