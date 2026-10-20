import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  listPetPassClaims,
  retryPetPassClaim,
  type AdminPetPassClaim,
} from '@/api/admin/pet'

const { Text } = Typography

const STATUS_META: Record<string, { color: string; label: string }> = {
  CLAIM_PENDING: { color: 'blue', label: '补发中' },
  SETTLED: { color: 'green', label: '已到账' },
  NEEDS_ATTENTION: { color: 'red', label: '待人工处置' },
}

/** 解析领取时冻结的奖励快照，只用于展示（重试始终按服务端原快照重放） */
function snapshotLabel(raw: string | null): string {
  if (!raw) return '—'
  try {
    const snap = JSON.parse(raw) as { coins?: number; skin?: string; ruleVersion?: string }
    const skin = snap.skin ? ` + 皮肤 ${snap.skin}` : ''
    return `${snap.coins ?? 0} 星光${skin}（规则 ${snap.ruleVersion ?? '-'}）`
  } catch {
    return raw
  }
}

/** T18 通行证补发台账：积压可见、失败原因可见、人工重放原事实（原因留痕） */
export function PassClaimPanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const [rows, setRows] = useState<AdminPetPassClaim[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState<string | undefined>(undefined)
  const [userId, setUserId] = useState<string | undefined>(undefined)
  const [seasonId, setSeasonId] = useState<string | undefined>(undefined)
  const [target, setTarget] = useState<AdminPetPassClaim | null>(null)
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const load = useCallback(
    async (targetPage = page) => {
      setLoading(true)
      try {
        const { data: res } = await listPetPassClaims({
          status,
          userId: userId || undefined,
          seasonId: seasonId || undefined,
          page: targetPage,
          size: 20,
        })
        if (res.success) {
          setRows(res.data ?? [])
          setTotal(Number(res.meta?.total ?? res.data?.length ?? 0))
        }
      } catch {
        // 拦截器已提示
      } finally {
        setLoading(false)
      }
    },
    [status, userId, seasonId, page],
  )

  useEffect(() => {
    void load(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const submitRetry = async () => {
    if (!target) return
    if (!reason.trim()) {
      messageApi.warning('请填写处置原因（随操作日志留痕）')
      return
    }
    setSubmitting(true)
    try {
      const { data: res } = await retryPetPassClaim(target.id, reason.trim())
      if (res.success) {
        messageApi.success('已重置为立即补发，将按领取时快照重放原奖励')
        setTarget(null)
        setReason('')
        await load()
      } else {
        messageApi.error(res.error?.message ?? '重试失败')
      }
    } catch {
      // 拦截器已提示
    } finally {
      setSubmitting(false)
    }
  }

  const columns: ColumnsType<AdminPetPassClaim> = [
    { title: '事实', dataIndex: 'id', width: 150, render: (v) => String(v) },
    { title: '用户', dataIndex: 'userId', width: 130, render: (v) => String(v) },
    { title: '赛季', dataIndex: 'seasonId', width: 100, render: (v) => String(v) },
    { title: '档位', dataIndex: 'tier', width: 70 },
    { title: '奖励快照', dataIndex: 'rewardSnapshot', ellipsis: true, render: snapshotLabel },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (v: string) => {
        const meta = STATUS_META[v] ?? { color: 'default', label: v }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    { title: '尝试', dataIndex: 'attempts', width: 70, render: (v) => v ?? 0 },
    {
      title: '最近错误/下次重试',
      width: 200,
      render: (_, row) => (
        <div>
          <div>{row.lastErrorCode ?? '—'}</div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {row.nextRetryAt ?? (row.status === 'NEEDS_ATTENTION' ? '等待人工处置' : '可立即执行')}
          </Text>
        </div>
      ),
    },
    {
      title: '租约',
      width: 160,
      render: (_, row) =>
        row.leaseOwner ? (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {row.leaseOwner.slice(0, 8)}… / {row.leaseUntil}
          </Text>
        ) : (
          '—'
        ),
    },
    {
      title: '操作',
      width: 100,
      render: (_, row) =>
        row.status === 'SETTLED' ? (
          <span style={{ color: '#999' }}>—</span>
        ) : (
          <Button size="small" type="link" onClick={() => setTarget(row)}>
            重试
          </Button>
        ),
    },
  ]

  return (
    <div>
      {contextHolder}
      <Space style={{ marginBottom: 12 }} wrap>
        <Input
          allowClear
          placeholder="用户 ID"
          style={{ width: 140 }}
          onChange={(e) => setUserId(e.target.value || undefined)}
        />
        <Input
          allowClear
          placeholder="赛季 ID"
          style={{ width: 140 }}
          onChange={(e) => setSeasonId(e.target.value || undefined)}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 180 }}
          onChange={(v) => setStatus(v)}
          options={[
            { value: 'CLAIM_PENDING', label: 'CLAIM_PENDING 补发中' },
            { value: 'SETTLED', label: 'SETTLED 已到账' },
            { value: 'NEEDS_ATTENTION', label: 'NEEDS_ATTENTION 待处置' },
          ]}
        />
        <Button onClick={() => void load()}>刷新</Button>
        <Text type="secondary">
          重试只重放领取时快照，不新造奖励；积压/最老等待/重试率/终止数见 Prometheus 指标
        </Text>
      </Space>
      <Table
        rowKey={(row) => String(row.id)}
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: page,
          pageSize: 20,
          total,
          showSizeChanger: false,
          onChange: (next) => {
            setPage(next)
            void load(next)
          },
        }}
      />
      <Modal
        title={target ? `重试补发 #${String(target.id)}` : '重试补发'}
        open={target !== null}
        onCancel={() => {
          setTarget(null)
          setReason('')
        }}
        onOk={() => void submitRetry()}
        confirmLoading={submitting}
        okText="确认重试"
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text>
            用户 {target ? String(target.userId) : '-'}，赛季 {target ? String(target.seasonId) : '-'}，
            第 {target?.tier ?? '-'} 档；将按
            <Text strong>领取时冻结的快照</Text>
            重放奖励，成功后置为已到账。
          </Text>
          <Input.TextArea
            rows={3}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="处置原因（必填，例如：工单#123 钱包维护已恢复）"
            maxLength={200}
          />
        </Space>
      </Modal>
    </div>
  )
}
