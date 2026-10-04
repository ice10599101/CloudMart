import { useCallback, useEffect, useState } from 'react'
import { Button, Input, Modal, Select, Table, Tag, Tabs, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  AfterSaleCase,
  approveAfterSaleCase,
  inspectAfterSaleCase,
  pageAfterSaleCases,
  rejectAfterSaleCase,
} from '@/api/afterSale'

const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待受理', color: 'orange' },
  APPROVED: { label: '已受理·待退款', color: 'blue' },
  REJECTED: { label: '已拒绝', color: 'red' },
  REFUNDED: { label: '退款完成', color: 'green' },
  CLOSED: { label: '已关闭', color: 'default' },
}

const TYPE_LABELS: Record<string, string> = {
  REFUND_ONLY: '仅退款',
  RETURN_REFUND: '退货退款',
}

/**
 * T11：售后案件处置页（business:order:refund 权限）。
 * 受理必须关联 T02 退款单号；退货退款案件质检 PASSED 后自动流转退款。
 */
export default function AfterSaleCasesPage() {
  const [cases, setCases] = useState<AfterSaleCase[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(20)
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined)
  const [loading, setLoading] = useState(false)
  const [activeCase, setActiveCase] = useState<AfterSaleCase | null>(null)

  const fetchCases = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await pageAfterSaleCases({ page, pageSize, status: statusFilter })
      setCases(res.data ?? [])
      setTotal(res.meta?.total ?? res.data?.length ?? 0)
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, statusFilter])

  useEffect(() => {
    fetchCases()
  }, [fetchCases])

  const handleApprove = useCallback(
    async (c: AfterSaleCase) => {
      const input = window.prompt('批准退款金额（≤订单应付）：', c.refundAmount ?? '')
      if (input === null) return
      // T04：退款号由服务端派生（RFC{caseId}），后台仅核定金额——不再手填关联 ID
      try {
        await approveAfterSaleCase(c.id, Number(input))
        message.success('售后已受理')
        fetchCases()
      } catch {
        message.error('受理失败')
      }
    },
    [fetchCases],
  )

  const handleReject = useCallback(
    async (c: AfterSaleCase) => {
      const reason = window.prompt('拒绝原因：')
      if (reason === null || !reason.trim()) return
      try {
        await rejectAfterSaleCase(c.id, reason.trim())
        message.success('已拒绝')
        fetchCases()
      } catch {
        message.error('操作失败')
      }
    },
    [fetchCases],
  )

  const handleInspect = useCallback(
    async (c: AfterSaleCase, result: 'PASSED' | 'REJECTED') => {
      try {
        await inspectAfterSaleCase(c.id, result)
        message.success(result === 'PASSED' ? '质检通过，退款已自动流转' : '质检不通过已记录')
        fetchCases()
      } catch {
        message.error('操作失败')
      }
    },
    [fetchCases],
  )

  const columns: ColumnsType<AfterSaleCase> = [
    { title: '案件号', dataIndex: 'caseNo', key: 'caseNo', width: 200 },
    { title: '订单号', dataIndex: 'orderNo', key: 'orderNo', width: 190 },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (t: string) => TYPE_LABELS[t] ?? t,
    },
    { title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s: string) => <Tag color={STATUS_META[s]?.color}>{STATUS_META[s]?.label ?? s}</Tag>,
    },
    {
      title: '退款',
      key: 'refund',
      width: 150,
      render: (_: unknown, c: AfterSaleCase) =>
        c.refundAmount ? `¥${c.refundAmount}${c.refundNo ? ` (${c.refundNo})` : ''}` : '—',
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      render: (_: unknown, c: AfterSaleCase) => (
        <>
          {c.status === 'PENDING' && (
            <>
              <Button type="link" size="small" onClick={() => handleApprove(c)}>
                受理
              </Button>
              <Button type="link" size="small" danger onClick={() => handleReject(c)}>
                拒绝
              </Button>
            </>
          )}
          {c.status === 'APPROVED' && c.type === 'RETURN_REFUND' && !c.rejectReason && (
            <>
              <Button type="link" size="small" onClick={() => handleInspect(c, 'PASSED')}>
                质检通过
              </Button>
              <Button type="link" size="small" danger onClick={() => handleInspect(c, 'REJECTED')}>
                质检不通过
              </Button>
            </>
          )}
          <Button type="link" size="small" onClick={() => setActiveCase(c)}>
            详情
          </Button>
        </>
      ),
    },
  ]

  const detailTab = activeCase && (
    <div>
      <p>
        <b>案件号：</b>
        {activeCase.caseNo}　<b>状态：</b>
        {STATUS_META[activeCase.status]?.label ?? activeCase.status}
        {activeCase.rejectReason && (
          <>
          　<b>拒绝原因：</b>
          {activeCase.rejectReason}
        </>
        )}
      </p>
      <p>
        <b>原因：</b>
        {activeCase.reason}
      </p>
      <Table
        size="small"
        rowKey={(e) => e.createdAt + e.action}
        dataSource={activeCase.timeline ?? []}
        columns={[
          { title: '动作', dataIndex: 'action', key: 'action', width: 140 },
          { title: '操作者', dataIndex: 'operator', key: 'operator', width: 140 },
          { title: '详情', dataIndex: 'detail', key: 'detail', ellipsis: true },
          { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
        ]}
        pagination={false}
      />
    </div>
  )

  return (
    <div style={{ padding: 24 }}>
      <Tabs
        activeKey={statusFilter ?? 'ALL'}
        onChange={(key) => {
          setPage(1)
          setStatusFilter(key === 'ALL' ? undefined : key)
        }}
        items={[
          { key: 'ALL', label: '全部' },
          { key: 'PENDING', label: '待受理' },
          { key: 'APPROVED', label: '已受理' },
          { key: 'REFUNDED', label: '退款完成' },
          { key: 'REJECTED', label: '已拒绝' },
          { key: 'CLOSED', label: '已关闭' },
        ]}
      />
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={cases}
        pagination={{
          current: page,
          pageSize,
          total,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p) => setPage(p),
        }}
      />
      <Modal
        open={!!activeCase}
        title={`售后案件 ${activeCase?.caseNo ?? ''}`}
        onCancel={() => setActiveCase(null)}
        footer={null}
        width={720}
      >
        {detailTab}
      </Modal>
    </div>
  )
}
