/**
 * 心愿治理工作台（N01）：统一待处理队列 + 治理决定 + 申诉复核。
 * 后端：mall-wish /admin/moderation/**（mall-admin 代理，服务令牌）。
 */
import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, Input, Space, Table, Tag, Tooltip, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { getModerationCases, getModerationAppeals, decideModerationCase, resolveAppeal } from '@/api/admin/wish'

interface ModerationCaseRow {
  id: string
  targetType: string
  targetId: string
  status: string
  version: number
  createdAt: string
}

interface AppealRow {
  id: string
  decisionId: string
  status: string
}

const STATUS_COLOR: Record<string, string> = {
  OPEN: 'orange',
  IN_REVIEW: 'blue',
  RESOLVED: 'green',
}

export default function WishModeration() {
  const { message } = App.useApp()
  const [cases, setCases] = useState<ModerationCaseRow[]>([])
  const [appeals, setAppeals] = useState<AppealRow[]>([])
  const [loading, setLoading] = useState(false)
  const [decisionText, setDecisionText] = useState<Record<string, string>>({})

  const loadCases = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getModerationCases({ pageSize: 50 })
      setCases((res as { data: ModerationCaseRow[] }).data ?? [])
      const appealRes = await getModerationAppeals({ pageSize: 50 })
      setAppeals((appealRes as { data: AppealRow[] }).data ?? [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadCases()
  }, [loadCases])

  const handleDecide = async (row: ModerationCaseRow, decision: 'NO_ACTION' | 'HIDE' | 'RESTORE') => {
    const reasonText = decisionText[row.id] ?? ''
    if (decision !== 'NO_ACTION' && !reasonText.trim()) {
      message.warning('HIDE/RESTORE 必须填写原因')
      return
    }
    await decideModerationCase(row.id, {
      version: row.version,
      decision,
      reasonText,
      reasonCode: decision === 'HIDE' ? 'ABUSE' : undefined,
    })
    message.success('决定已记录')
    void loadCases()
  }

  const handleResolveAppeal = async (appeal: AppealRow, accept: boolean) => {
    await resolveAppeal(appeal.id, { accept, resultReason: accept ? '复核通过' : '维持原决定' })
    message.success('复核完成')
    void loadCases()
  }

  const columns: ColumnsType<ModerationCaseRow> = [
    { title: '工单 ID', dataIndex: 'id', key: 'id' },
    { title: '目标', key: 'target',
      render: (_, r) => `${r.targetType} #${r.targetId}` },
    { title: '状态', dataIndex: 'status', key: 'status',
      render: (s: string) => <Tag color={STATUS_COLOR[s] ?? 'default'}>{s}</Tag> },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
    { title: '处理', key: 'actions',
      render: (_, r) => r.status === 'RESOLVED' ? null : (
        <Space direction="vertical" size={4}>
          <Space size={4}>
            <Button size="small" onClick={() => void handleDecide(r, 'NO_ACTION')}>无动作结案</Button>
            <Button size="small" danger onClick={() => void handleDecide(r, 'HIDE')}>隐藏</Button>
            <Button size="small" type="primary" ghost onClick={() => void handleDecide(r, 'RESTORE')}>恢复</Button>
          </Space>
          <Input.TextArea
            rows={1}
            placeholder="HIDE/RESTORE 必填原因"
            value={decisionText[r.id] ?? ''}
            onChange={(e) => setDecisionText((prev) => ({ ...prev, [r.id]: e.target.value }))}
          />
        </Space>
      ) },
  ]

  const appealColumns: ColumnsType<AppealRow> = [
    { title: '申诉 ID', dataIndex: 'id', key: 'id' },
    { title: '针对决定', dataIndex: 'decisionId', key: 'decisionId' },
    { title: '状态', dataIndex: 'status', key: 'status',
      render: (s: string) => <Tag color={s === 'PENDING' ? 'orange' : s === 'ACCEPTED' ? 'green' : 'default'}>{s}</Tag> },
    { title: '复核', key: 'review',
      render: (_, r) => r.status === 'PENDING' ? (
        <Space size={4}>
          <Button size="small" type="primary" ghost
            onClick={() => void handleResolveAppeal(r, true)}>通过恢复</Button>
          <Button size="small" danger
            onClick={() => void handleResolveAppeal(r, false)}>驳回</Button>
        </Space>
      ) : null },
  ]

  return (
    <Card title={<Typography.Title level={4}>心愿治理工作台</Typography.Title>} extra={
      <Button onClick={() => void loadCases()}>刷新</Button>
    }>
      <Tooltip title="同一内容同一版本仅一个活动工单；决定追加写可追溯；复核人不得为原决定处理人">
        <span />
      </Tooltip>
      <Typography.Title level={5} style={{ marginTop: 8 }}>治理队列</Typography.Title>
      <Table<ModerationCaseRow> rowKey="id" loading={loading} dataSource={cases}
        columns={columns} pagination={{ pageSize: 20 }} />
      <Typography.Title level={5} style={{ marginTop: 24 }}>申诉复核</Typography.Title>
      <Table<AppealRow> rowKey="id" loading={loading} dataSource={appeals}
        columns={appealColumns} pagination={{ pageSize: 20 }} />
    </Card>
  )
}
