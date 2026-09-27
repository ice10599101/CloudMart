import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  approvePetWalletAdjustment,
  createPetWalletAdjustment,
  freezePetWalletAccount,
  getPetWalletReconciliation,
  listPetWalletAccounts,
  listPetWalletAdjustments,
  listPetWalletReconciliations,
  listPetWalletTransactions,
  rejectPetWalletAdjustment,
  runPetWalletReconciliation,
  unfreezePetWalletAccount,
  type AdminPetWalletAccount,
  type AdminPetWalletAdjustment,
  type AdminPetWalletTransaction,
} from '@/api/admin/pet'
import type { ApiResponse } from '@/types/api'

const DIRECTION_TAG: Record<string, { color: string; label: string }> = {
  EARN: { color: 'green', label: '收入' },
  SPEND: { color: 'orange', label: '支出' },
  REFUND: { color: 'blue', label: '退款' },
  ADJUSTMENT: { color: 'purple', label: '调账' },
}

/** 宠物钱包管理（W04/§4.3-3/4/7）：账户查询/冻结、调账申请-审批、对账批次。 */
export default function PetWalletAdmin() {
  return (
    <Card title="宠物币钱包管理" style={{ margin: 16 }}>
      <Tabs
        items={[
          { key: 'accounts', label: '账户与流水', children: <AccountsTab /> },
          { key: 'adjustments', label: '调账', children: <AdjustmentsTab /> },
          { key: 'reconciliations', label: '对账', children: <ReconciliationsTab /> },
        ]}
      />
    </Card>
  )
}

// ---------------- 账户与流水 ----------------

function AccountsTab() {
  const [userIdQuery, setUserIdQuery] = useState<string>('')
  const [accounts, setAccounts] = useState<AdminPetWalletAccount[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [transactions, setTransactions] = useState<AdminPetWalletTransaction[]>([])
  const [selectedUser, setSelectedUser] = useState<number | string | null>(null)
  const [statusModal, setStatusModal] = useState<{ user: number | string; freeze: boolean; version: number | string } | null>(null)
  const [form] = Form.useForm()

  const loadAccounts = useCallback(async (nextPage = page) => {
    setLoading(true)
    try {
      const { data }: { data: ApiResponse<AdminPetWalletAccount[]> } = await listPetWalletAccounts({
        userId: userIdQuery || undefined,
        page: nextPage,
        size: 10,
      })
      setAccounts(data.data ?? [])
      setTotal(Number(data.meta?.total ?? data.data?.length ?? 0))
      setPage(nextPage)
    } finally {
      setLoading(false)
    }
  }, [userIdQuery, page])

  useEffect(() => {
    void loadAccounts(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const loadTransactions = useCallback(async (userId: number | string) => {
    setSelectedUser(userId)
    const { data }: { data: ApiResponse<AdminPetWalletTransaction[]> } = await listPetWalletTransactions({ userId, size: 10 })
    setTransactions(data.data ?? [])
  }, [])

  const submitStatus = async () => {
    if (!statusModal) return
    const values = await form.validateFields()
    const payload = { reason: values.reason as string, expectedVersion: statusModal.version }
    if (statusModal.freeze) {
      await freezePetWalletAccount(statusModal.user, payload)
      message.success('已冻结')
    } else {
      await unfreezePetWalletAccount(statusModal.user, payload)
      message.success('已解冻')
    }
    setStatusModal(null)
    form.resetFields()
    await loadAccounts(page)
  }

  const accountColumns: ColumnsType<AdminPetWalletAccount> = [
    { title: '用户', dataIndex: 'userId', key: 'userId' },
    { title: '余额（PET_COIN）', dataIndex: 'balance', key: 'balance' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) =>
        status === 'FROZEN' ? <Tag color="red">已冻结</Tag> : <Tag color="green">正常</Tag>,
    },
    { title: '版本', dataIndex: 'version', key: 'version' },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void loadTransactions(record.userId)}>
            流水
          </Button>
          <Button
            size="small"
            danger={record.status !== 'FROZEN'}
            onClick={() =>
              setStatusModal({ user: record.userId, freeze: record.status !== 'FROZEN', version: record.version })
            }
          >
            {record.status !== 'FROZEN' ? '冻结' : '解冻'}
          </Button>
        </Space>
      ),
    },
  ]

  const transactionColumns: ColumnsType<AdminPetWalletTransaction> = [
    { title: '流水', dataIndex: 'transactionId', key: 'transactionId', width: 180 },
    { title: '业务', dataIndex: 'bizType', key: 'bizType' },
    {
      title: '方向',
      dataIndex: 'direction',
      key: 'direction',
      width: 80,
      render: (d: string) => <Tag color={DIRECTION_TAG[d]?.color}>{DIRECTION_TAG[d]?.label ?? d}</Tag>,
    },
    { title: '金额', dataIndex: 'amount', key: 'amount', width: 90 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
    { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space>
        <Input
          placeholder="用户 ID"
          value={userIdQuery}
          onChange={(e) => setUserIdQuery(e.target.value)}
          style={{ width: 220 }}
        />
        <Button type="primary" onClick={() => void loadAccounts(1)}>
          查询
        </Button>
      </Space>
      <Table
        rowKey={(r) => String(r.accountId)}
        columns={accountColumns}
        dataSource={accounts}
        loading={loading}
        pagination={{ current: page, total, pageSize: 10, onChange: (p) => void loadAccounts(p) }}
      />
      {selectedUser && (
        <Card size="small" title={`用户 ${selectedUser} 的最近流水`}>
          <Table rowKey={(r) => String(r.transactionId)} columns={transactionColumns} dataSource={transactions} pagination={false} />
        </Card>
      )}
      <Modal
        title={statusModal?.freeze ? '冻结账户' : '解冻账户'}
        open={statusModal !== null}
        onOk={() => void submitStatus()}
        onCancel={() => setStatusModal(null)}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="reason" label="原因（必填，入审计）" rules={[{ required: true }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}

// ---------------- 调账 ----------------

function AdjustmentsTab() {
  const [adjustments, setAdjustments] = useState<AdminPetWalletAdjustment[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [createForm] = Form.useForm()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data }: { data: ApiResponse<AdminPetWalletAdjustment[]> } = await listPetWalletAdjustments({ size: 20 })
      setAdjustments(data.data ?? [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const submitCreate = async () => {
    const values = await createForm.validateFields()
    await createPetWalletAdjustment({
      userId: values.userId as string,
      delta: String(values.delta),
      reason: values.reason as string,
      ticketNo: values.ticketNo as string | undefined,
    })
    message.success('调账申请已创建，等待他人审批')
    setCreateOpen(false)
    createForm.resetFields()
    await load()
  }

  const act = async (id: number | string, action: 'approve' | 'reject') => {
    const call = action === 'approve' ? approvePetWalletAdjustment : rejectPetWalletAdjustment
    await call(id, {})
    message.success(action === 'approve' ? '已审批通过并入账' : '已拒绝')
    await load()
  }

  const columns: ColumnsType<AdminPetWalletAdjustment> = [
    { title: '申请单', dataIndex: 'id', key: 'id', width: 180 },
    { title: '用户', dataIndex: 'userId', key: 'userId' },
    {
      title: '金额',
      dataIndex: 'delta',
      key: 'delta',
      render: (delta: number | string) => (
        <Typography.Text type={String(delta).startsWith('-') ? 'danger' : undefined}>
          {delta}
        </Typography.Text>
      ),
    },
    { title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
    { title: '工单', dataIndex: 'ticketNo', key: 'ticketNo' },
    { title: '申请人', dataIndex: 'requestedBy', key: 'requestedBy' },
    { title: '审批人', dataIndex: 'approvedBy', key: 'approvedBy' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) =>
        status === 'PENDING' ? <Tag color="gold">待审批</Tag> : status === 'APPROVED' ? <Tag color="green">已通过</Tag> : <Tag color="red">已拒绝</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) =>
        record.status === 'PENDING' ? (
          <Space>
            <Button size="small" type="primary" onClick={() => void act(record.id, 'approve')}>
              通过
            </Button>
            <Button size="small" danger onClick={() => void act(record.id, 'reject')}>
              拒绝
            </Button>
          </Space>
        ) : (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {record.transactionId ? `流水 ${record.transactionId}` : '-'}
          </Typography.Text>
        ),
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Button type="primary" onClick={() => setCreateOpen(true)}>
        新建调账申请
      </Button>
      <Table rowKey={(r) => String(r.id)} columns={columns} dataSource={adjustments} loading={loading} pagination={false} />
      <Modal
        title="新建调账申请"
        open={createOpen}
        onOk={() => void submitCreate()}
        onCancel={() => setCreateOpen(false)}
      >
        <Form form={createForm} layout="vertical">
          <Form.Item name="userId" label="目标用户 ID" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="delta"
            label="金额（带符号，正=补发，负=扣回；禁止 0）"
            rules={[{ required: true }, { pattern: /^-?[1-9]\d*$/, message: '非 0 整数' }]}
          >
            <Input placeholder="如 100 或 -50" />
          </Form.Item>
          <Form.Item name="reason" label="原因（必填，审计可见）" rules={[{ required: true }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="ticketNo" label="关联工单号（可选）">
            <Input />
          </Form.Item>
        </Form>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          申请需另一管理员审批；审批通过原子入账。冻结账户允许补发入账，扣回将被拒绝。
        </Typography.Text>
      </Modal>
    </Space>
  )
}

// ---------------- 对账 ----------------

function ReconciliationsTab() {
  const [runs, setRuns] = useState<Record<string, unknown>[]>([])
  const [items, setItems] = useState<Record<string, unknown>[]>([])
  const [selectedRun, setSelectedRun] = useState<number | string | null>(null)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data }: { data: ApiResponse<Record<string, unknown>[]> } = await listPetWalletReconciliations({ size: 20 })
      setRuns(data.data ?? [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const openRun = async (runId: number | string) => {
    setSelectedRun(runId)
    const { data }: { data: ApiResponse<Record<string, unknown>[]> } = await getPetWalletReconciliation(runId)
    setItems(data.data ?? [])
  }

  const runColumns: ColumnsType<Record<string, unknown>> = [
    { title: '批次', dataIndex: 'id', key: 'id', width: 180, render: (v) => String(v) },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
    { title: '账户数', dataIndex: 'accountCount', key: 'accountCount', width: 90 },
    {
      title: '差异数',
      dataIndex: 'diffCount',
      key: 'diffCount',
      width: 90,
      render: (v: number) => (Number(v) > 0 ? <Tag color="red">{v}</Tag> : <Tag color="green">0</Tag>),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Button size="small" onClick={() => void openRun(String(record.id))}>
          差异明细
        </Button>
      ),
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Button onClick={() => void runPetWalletReconciliation()}>立即执行一轮对账</Button>
      <Table rowKey={(r) => String(r.id)} columns={runColumns} dataSource={runs} loading={loading} pagination={false} />
      {selectedRun && (
        <Card size="small" title={`批次 ${selectedRun} 差异明细（OPEN 人工处置，不自动改平）`}>
          {items.length === 0 ? (
            <Empty description="无差异" />
          ) : (
            <pre style={{ maxHeight: 320, overflow: 'auto', fontSize: 12 }}>
              {JSON.stringify(items, null, 2)}
            </pre>
          )}
        </Card>
      )}
    </Space>
  )
}
