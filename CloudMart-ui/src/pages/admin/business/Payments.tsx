import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Descriptions, Modal, Tag, Popconfirm, Input, Tabs, InputNumber, Select, Space } from 'antd'
import { ThunderboltOutlined } from '@ant-design/icons'
import {
  getPayments,
  getPaymentByOrder,
  refundPayment,
  listReconciliationRuns,
  listReconciliationDifferences,
  executeReconciliationRun,
  resolveReconciliationDifference,
} from '@/api/admin/business'
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

interface ReconciliationRunRecord {
  id: number
  businessDate: string
  scope: string
  totalChecked: number
  totalDiff: number
  status: string
  startedAt: string
  finishedAt: string
}

interface ReconciliationDiffRecord {
  id: number
  runId: number
  diffType: string
  bizId: string
  severity: string
  detail: string
  evidence: string
  resolveStatus: string
  createdAt: string
}

const RECON_SEVERITY_COLOR: Record<string, string> = {
  HIGH: 'red',
  MEDIUM: 'orange',
  LOW: 'default',
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

  // ==================== 对账工作台（OPS-01） ====================
  const reconActionRef = useRef<ActionType>(null)
  const diffActionRef = useRef<ActionType>(null)
  const [executeOpen, setExecuteOpen] = useState(false)
  const [scanDays, setScanDays] = useState(7)
  const [reconScope, setReconScope] = useState<'PAYMENT_ORDER' | 'REFUND' | 'INVENTORY'>('PAYMENT_ORDER')
  const [executing, setExecuting] = useState(false)
  const [diffRun, setDiffRun] = useState<ReconciliationRunRecord | null>(null)
  const [resolveTarget, setResolveTarget] = useState<ReconciliationDiffRecord | null>(null)
  const [resolveStatus, setResolveStatus] = useState('RESOLVED')
  const [resolveNote, setResolveNote] = useState('')
  const [resolveSubmitting, setResolveSubmitting] = useState(false)

  const handleExecuteRecon = async () => {
    setExecuting(true)
    try {
      await executeReconciliationRun(scanDays, reconScope)
      message.success('对账执行完成')
      setExecuteOpen(false)
      reconActionRef.current?.reload()
    } finally {
      setExecuting(false)
    }
  }

  const handleResolveDiff = async () => {
    if (!resolveTarget) return
    if (!resolveNote.trim()) {
      message.error('请填写处置说明')
      return
    }
    setResolveSubmitting(true)
    try {
      await resolveReconciliationDifference(resolveTarget.id, { resolveStatus, resolveNote })
      message.success('差异已处置')
      setResolveTarget(null)
      setResolveNote('')
      diffActionRef.current?.reload()
    } finally {
      setResolveSubmitting(false)
    }
  }

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

  const reconColumns: ProColumns<ReconciliationRunRecord>[] = [
    { title: '运行ID', dataIndex: 'id', width: 80, search: false },
    { title: '业务日期', dataIndex: 'businessDate', width: 110, search: false },
    { title: '范围', dataIndex: 'scope', width: 120, search: false },
    { title: '核对笔数', dataIndex: 'totalChecked', width: 90, search: false },
    {
      title: '差异数',
      dataIndex: 'totalDiff',
      width: 90,
      search: false,
      render: (_, record) => (
        <span style={{ color: record.totalDiff > 0 ? '#FF4D4F' : undefined }}>{record.totalDiff}</span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      search: false,
      render: (_, record) => (
        <Tag color={record.status === 'DONE' ? 'green' : record.status === 'RUNNING' ? 'blue' : 'red'}>
          {record.status}
        </Tag>
      ),
    },
    { title: '开始时间', dataIndex: 'startedAt', width: 170, valueType: 'dateTime', search: false },
    { title: '结束时间', dataIndex: 'finishedAt', width: 170, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 90,
      fixed: 'right',
      render: (_, record) => [
        <Button key="diffs" type="link" size="small" onClick={() => setDiffRun(record)}>
          查看差异
        </Button>,
      ],
    },
  ]

  const diffColumns: ProColumns<ReconciliationDiffRecord>[] = [
    { title: '差异ID', dataIndex: 'id', width: 80 },
    { title: '差异类型', dataIndex: 'diffType', width: 140 },
    { title: '业务单号', dataIndex: 'bizId', width: 180, ellipsis: true },
    {
      title: '严重度',
      dataIndex: 'severity',
      width: 90,
      render: (_, record) => (
        <Tag color={RECON_SEVERITY_COLOR[record.severity] ?? 'default'}>{record.severity}</Tag>
      ),
    },
    { title: '差异说明', dataIndex: 'detail', ellipsis: true },
    {
      title: '处置状态',
      dataIndex: 'resolveStatus',
      width: 90,
      render: (_, record) => (
        <Tag color={record.resolveStatus === 'OPEN' ? 'orange' : 'green'}>{record.resolveStatus}</Tag>
      ),
    },
    { title: '发生时间', dataIndex: 'createdAt', width: 170, valueType: 'dateTime' },
    {
      title: '操作',
      valueType: 'option',
      width: 80,
      render: (_, record) =>
        record.resolveStatus === 'OPEN' ? (
          <Button
            type="link"
            size="small"
            onClick={() => {
              setResolveTarget(record)
              setResolveStatus('RESOLVED')
              setResolveNote('')
            }}
          >
            处置
          </Button>
        ) : (
          <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>已处置</span>
        ),
    },
  ]

  return (
    <>
      <Tabs
        defaultActiveKey="records"
        items={[
          {
            key: 'records',
            label: '支付记录',
            children: (
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
            ),
          },
          {
            key: 'reconciliation',
            label: '对账工作台',
            children: (
              <ProTable<ReconciliationRunRecord>
                headerTitle="支付对账（OPS-01）"
                actionRef={reconActionRef}
                rowKey="id"
                search={false}
                toolBarRender={() => [
                  <Button key="execute" type="primary" icon={<ThunderboltOutlined />} onClick={() => setExecuteOpen(true)}>
                    执行对账
                  </Button>,
                ]}
                request={async (params) => {
                  return safeProTableRequest<ReconciliationRunRecord>(() =>
                    listReconciliationRuns({
                      page: params.current,
                      size: params.pageSize,
                    })
                  )
                }}
                columns={reconColumns}
                pagination={{ defaultPageSize: 10, showSizeChanger: true }}
              />
            ),
          },
        ]}
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

      <Modal
        title="执行对账"
        open={executeOpen}
        onCancel={() => setExecuteOpen(false)}
        onOk={handleExecuteRecon}
        confirmLoading={executing}
        okText="开始执行"
        width={420}
      >
        <Space direction="vertical">
          <Space align="center">
            <span>对账层级</span>
            <Select
              value={reconScope}
              onChange={(v) => setReconScope(v)}
              style={{ width: 200 }}
              options={[
                { value: 'PAYMENT_ORDER', label: '支付↔订单（PAYMENT_ORDER）' },
                { value: 'REFUND', label: '退款↔订单（REFUND）' },
                { value: 'INVENTORY', label: '预占↔订单（INVENTORY）' },
              ]}
            />
          </Space>
          <Space align="center">
            <span>扫描最近</span>
            <InputNumber value={scanDays} onChange={(v) => setScanDays(v ?? 7)} min={1} max={90} precision={0} />
            <span>天（1~90 天）</span>
          </Space>
        </Space>
      </Modal>

      <Modal
        title={`对账差异 - 运行 #${diffRun?.id ?? ''}`}
        open={diffRun !== null}
        onCancel={() => setDiffRun(null)}
        footer={null}
        width={1000}
      >
        <ProTable<ReconciliationDiffRecord>
          actionRef={diffActionRef}
          rowKey="id"
          search={false}
          size="small"
          request={async (params) => {
            if (!diffRun) return { data: [], total: 0, success: true }
            return safeProTableRequest<ReconciliationDiffRecord>(() =>
              listReconciliationDifferences(diffRun.id, {
                page: params.current,
                size: params.pageSize,
              })
            )
          }}
          columns={diffColumns}
          pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        />
      </Modal>

      <Modal
        title={`处置差异 #${resolveTarget?.id ?? ''}`}
        open={resolveTarget !== null}
        onCancel={() => setResolveTarget(null)}
        onOk={handleResolveDiff}
        confirmLoading={resolveSubmitting}
        okText="确认处置"
        width={480}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div>
            <div style={{ marginBottom: 4 }}>处置结论</div>
            <Select
              value={resolveStatus}
              onChange={setResolveStatus}
              style={{ width: '100%' }}
              options={[
                { value: 'RESOLVED', label: 'RESOLVED（已解决）' },
                { value: 'ACCEPTED', label: 'ACCEPTED（接受差异）' },
              ]}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>处置说明（登记证据，不直接改资金）</div>
            <Input.TextArea
              value={resolveNote}
              onChange={(e) => setResolveNote(e.target.value)}
              placeholder="例如：经核实为渠道掉单，已手工补录凭证"
              rows={3}
              maxLength={200}
              showCount
            />
          </div>
        </Space>
      </Modal>
    </>
  )
}
