import { useCallback, useState } from 'react'
import {
  Button,
  Card,
  Collapse,
  Descriptions,
  Empty,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  getUserSeasonPassProgress,
  listUserQuestProgress,
  type AdminPetPassClaim,
  type AdminPetQuest,
  type AdminPetQuestProgress,
  type AdminSeasonPassProgress,
} from '@/api/admin/pet'

const { Text } = Typography

const QUEST_STATUS: Record<string, { color: string; label: string }> = {
  IN_PROGRESS: { color: 'default', label: '进行中' },
  COMPLETE: { color: 'blue', label: '可领取' },
  CLAIMED: { color: 'green', label: '已领取' },
  CANCELLED: { color: 'red', label: '已取消' },
}

const CLAIM_STATUS: Record<string, { color: string; label: string }> = {
  CLAIM_PENDING: { color: 'blue', label: '补发中' },
  SETTLED: { color: 'green', label: '已到账' },
  NEEDS_ATTENTION: { color: 'red', label: '待人工处置' },
}

function snapshotText(raw: string | null): string {
  return raw ? raw : '—'
}

/** T19 用户进度查询：任务集/任务快照 + 赛季通行证与逐档奖励回执（只读，不提供改完成值入口） */
export function UserProgressPanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const [userIdInput, setUserIdInput] = useState<number | undefined>(undefined)
  const [userId, setUserId] = useState<string | undefined>(undefined)
  const [petId, setPetId] = useState<string | undefined>(undefined)
  const [setId, setSetId] = useState<string | undefined>(undefined)
  const [questRows, setQuestRows] = useState<AdminPetQuestProgress[]>([])
  const [questLoading, setQuestLoading] = useState(false)
  const [pass, setPass] = useState<AdminSeasonPassProgress | null>(null)
  const [seasonId, setSeasonId] = useState<string | undefined>(undefined)
  const [passLoading, setPassLoading] = useState(false)

  const loadPass = useCallback(async (id: string, targetSeason?: string) => {
    setPassLoading(true)
    try {
      const { data: res } = await getUserSeasonPassProgress(id, targetSeason)
      if (res.success) {
        setPass(res.data ?? null)
        setSeasonId(res.data?.season ? String(res.data.season.id) : undefined)
      }
    } catch {
      // 拦截器已提示
    } finally {
      setPassLoading(false)
    }
  }, [])

  const load = async () => {
    if (!userIdInput) {
      messageApi.warning('请输入用户 ID')
      return
    }
    const id = String(userIdInput)
    setUserId(id)
    setQuestLoading(true)
    try {
      const { data: res } = await listUserQuestProgress(id, {
        petId: petId || undefined,
        setId: setId || undefined,
        page: 1,
        size: 10,
      })
      if (res.success) {
        setQuestRows(res.data ?? [])
      }
    } catch {
      // 拦截器已提示
    } finally {
      setQuestLoading(false)
    }
    await loadPass(id)
  }

  const questColumns: ColumnsType<AdminPetQuest> = [
    { title: '任务', dataIndex: 'questCode', width: 150 },
    {
      title: '进度',
      width: 90,
      render: (_, row) => `${row.progress}/${row.targetValue}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => {
        const meta = QUEST_STATUS[v] ?? { color: 'default', label: v }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    { title: '奖励快照', dataIndex: 'rewardSnapshot', ellipsis: true, render: snapshotText },
    { title: '领取时间', dataIndex: 'claimedAt', width: 170, render: (v) => v ?? '—' },
    {
      title: '取消',
      width: 160,
      render: (_, row) =>
        row.status === 'CANCELLED' ? `${row.cancelReason ?? '-'}（${row.cancelledBy ?? '-'}）` : '—',
    },
  ]

  const claimColumns: ColumnsType<AdminPetPassClaim> = [
    { title: '档位', dataIndex: 'tier', width: 70 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (v: string) => {
        const meta = CLAIM_STATUS[v] ?? { color: 'default', label: v }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    { title: '奖励快照', dataIndex: 'rewardSnapshot', ellipsis: true, render: snapshotText },
    { title: '尝试', dataIndex: 'attempts', width: 70, render: (v) => v ?? 0 },
    { title: '最近错误', dataIndex: 'lastErrorCode', width: 160, render: (v) => v ?? '—' },
    { title: '到账时间', dataIndex: 'settledAt', width: 170, render: (v) => v ?? '—' },
  ]

  return (
    <div>
      {contextHolder}
      <Space style={{ marginBottom: 12 }} wrap>
        <InputNumber
          placeholder="用户 ID"
          value={userIdInput}
          onChange={(value) => setUserIdInput(value ?? undefined)}
          style={{ width: 140 }}
        />
        <Input
          allowClear
          placeholder="宠物 ID（可选）"
          style={{ width: 150 }}
          onChange={(e) => setPetId(e.target.value || undefined)}
        />
        <Input
          allowClear
          placeholder="任务集 ID（可选）"
          style={{ width: 160 }}
          onChange={(e) => setSetId(e.target.value || undefined)}
        />
        <Button type="primary" loading={questLoading || passLoading} onClick={() => void load()}>
          查询
        </Button>
        <Text type="secondary">只读查询：任务/奖励快照与回执由服务端事实表返回，不提供改完成值入口</Text>
      </Space>
      {!userId && <Empty description="输入用户 ID 开始查询" />}
      {userId && (
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Card size="small" title="任务集与任务快照" loading={questLoading}>
            {questRows.length === 0 ? (
              <Empty description="该用户没有匹配的任务集" />
            ) : (
              <Collapse
                items={questRows.map((row) => ({
                  key: String(row.questSet.id),
                  label: `${row.questSet.businessDate} · 宠物 ${String(row.questSet.petId)} · ${
                    row.questSet.status === 'ACTIVE' ? '进行中' : '已过期'
                  } · 截止 ${row.questSet.claimDeadline ?? '随集'}`,
                  children: (
                    <Table
                      rowKey={(q) => String(q.id)}
                      size="small"
                      pagination={false}
                      columns={questColumns}
                      dataSource={row.quests}
                    />
                  ),
                }))}
              />
            )}
          </Card>
          <Card
            size="small"
            title="赛季通行证进度与奖励回执"
            loading={passLoading}
            extra={
              (pass?.seasons?.length ?? 0) > 1 && (
                <Select
                  size="small"
                  style={{ width: 220 }}
                  value={seasonId}
                  onChange={(value) => {
                    setSeasonId(value)
                    void loadPass(userId, value)
                  }}
                  options={(pass?.seasons ?? []).map((season) => ({
                    value: String(season.id),
                    label: season.name,
                  }))}
                />
              )
            }
          >
            {!pass?.pass ? (
              <Empty description="该用户暂无通行证记录" />
            ) : (
              <>
                <Descriptions size="small" column={3} bordered>
                  <Descriptions.Item label="赛季">{pass.season?.name ?? '-'}</Descriptions.Item>
                  <Descriptions.Item label="经验">{pass.pass.passExp}</Descriptions.Item>
                  <Descriptions.Item label="已领投影">{pass.pass.claimedTiers}</Descriptions.Item>
                </Descriptions>
                <Table
                  style={{ marginTop: 12 }}
                  rowKey={(row) => String(row.id)}
                  size="small"
                  pagination={false}
                  columns={claimColumns}
                  dataSource={pass.claims}
                />
              </>
            )}
          </Card>
        </Space>
      )}
    </div>
  )
}
