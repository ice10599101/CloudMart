import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Input,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd'
import {
  listPetQuestReceipts,
  replayPetQuestReceipt,
  type AdminPetQuestReceipt,
} from '@/api/admin/pet'

/** R32 任务事件回执面板：收据查询 + SKIPPED_STALE 重放补算（只允许重放已有事实） */
export function QuestReceiptPanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const [rows, setRows] = useState<AdminPetQuestReceipt[]>([])
  const [loading, setLoading] = useState(false)
  const [questCode, setQuestCode] = useState<string | undefined>(undefined)
  const [status, setStatus] = useState<string | undefined>(undefined)
  const [userId, setUserId] = useState<string | undefined>(undefined)
  const [replaying, setReplaying] = useState<number | string | null>(null)
  // PET-22/T49：真实分页（后端返回 total，积压可翻页处理，不再固定第 1 页）
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await listPetQuestReceipts({
        questCode, status, userId: userId || undefined, page, size: 20,
      })
      if (res.success) {
        setRows(res.data?.records ?? [])
        setTotal(Number(res.data?.total ?? 0))
      }
    } finally {
      setLoading(false)
    }
  }, [questCode, status, userId, page])

  useEffect(() => {
    void load()
  }, [load])

  const replay = async (row: AdminPetQuestReceipt) => {
    setReplaying(row.id)
    try {
      const { data: res } = await replayPetQuestReceipt(row.id)
      if (res.success) {
        messageApi.success(`已按事实日 ${row.businessDate} 补记 ${row.amount} 次`)
        await load()
      } else {
        messageApi.error(res.error?.message ?? '重放失败')
      }
    } finally {
      setReplaying(null)
    }
  }

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
        <Select
          allowClear
          placeholder="任务类型"
          style={{ width: 150 }}
          onChange={(v) => setQuestCode(v)}
          options={['FEED', 'PLAY', 'CLEAN', 'REST', 'WORK', 'STUDY', 'BATTLE', 'CHAT',
            'BOTTLE', 'VISIT', 'FRIEND_VISIT', 'CAREER_WORK', 'WALL_MESSAGE', 'COMPANION', 'DECORATE']
            .map((code) => ({ value: code, label: code }))}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 150 }}
          onChange={(v) => setStatus(v)}
          options={[
            { value: 'APPLIED', label: 'APPLIED 已计入' },
            { value: 'PENDING', label: 'PENDING 投影中' },
            { value: 'FAILED', label: 'FAILED 投影失败' },
            { value: 'SKIPPED_STALE', label: 'SKIPPED_STALE 待补算' },
          ]}
        />
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table
        rowKey={(r) => String(r.id)}
        size="small"
        loading={loading}
        pagination={{
          current: page,
          pageSize: 20,
          total,
          showSizeChanger: false,
          onChange: (p) => setPage(p),
        }}
        dataSource={rows}
        columns={[
          { title: '回执', dataIndex: 'id', width: 180, render: (v) => String(v) },
          { title: '用户', dataIndex: 'userId', width: 150, render: (v) => String(v) },
          { title: '任务类型', dataIndex: 'questCode', width: 120 },
          { title: '事实键', dataIndex: 'eventId', ellipsis: true },
          { title: '事实时间', dataIndex: 'sourceTime', width: 160 },
          { title: '计入日', dataIndex: 'businessDate', width: 110 },
          { title: '数量', dataIndex: 'amount', width: 70 },
          {
            title: '状态',
            dataIndex: 'status',
            width: 140,
            render: (v: string) => {
              const map: Record<string, { color: string; label: string }> = {
                APPLIED: { color: 'green', label: '已计入' },
                PENDING: { color: 'blue', label: '投影中' },
                FAILED: { color: 'red', label: '投影失败' },
                SKIPPED_STALE: { color: 'orange', label: '待补算' },
              }
              const meta = map[v] ?? { color: 'default', label: v }
              return <Tag color={meta.color}>{meta.label}</Tag>
            },
          },
          {
            title: '操作',
            key: 'ops',
            width: 100,
            render: (_, row) =>
              row.status === 'SKIPPED_STALE' || row.status === 'FAILED' ? (
                <Button size="small" type="link" loading={replaying === row.id} onClick={() => void replay(row)}>
                  重放
                </Button>
              ) : (
                <span style={{ color: '#999' }}>—</span>
              ),
          },
        ]}
      />
    </div>
  )
}
