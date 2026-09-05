import { useState, useEffect, useCallback } from 'react'
import { Button, Card, Empty, Segmented, Table, App } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { history } from 'umi'
import { getMyResourceLogs, type ResourceLogItem } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'

const STATUS_COLORS: Record<string, string> = { EARN: '#52c41a', SPEND: '#ff6b6b' }

/**
 * 星光流水（Sprint 1.4 验收「星光流水列表 三端展示一致」，遗留 P1 WEB 端）：
 * EARN/SPEND 筛选 + 游标分页。
 */
export default function StarlightLog() {
  const { message } = App.useApp()
  const { user, userLoading } = useAuthStore()
  const [logs, setLogs] = useState<ResourceLogItem[]>([])
  const [filter, setFilter] = useState<'' | 'EARN' | 'SPEND'>('')
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  const load = useCallback(async (nextCursor?: string | null, reset = false) => {
    if (reset) setLoading(true)
    else setLoadingMore(true)
    try {
      const res = await getMyResourceLogs({ type: filter || undefined, cursor: nextCursor ?? undefined, pageSize: 20 })
      if (res.data.success) {
        const list = res.data.data ?? []
        setLogs((prev) => (reset ? list : [...prev, ...list]))
        setCursor(list.length > 0 ? String(list[list.length - 1].id) : null)
        setHasMore(list.length >= 20)
      }
    } catch {
      if (reset) setLogs([])
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [filter])

  useEffect(() => {
    if (!user && !userLoading) {
      history.push('/login?redirect=/wish/starlight-log')
      return
    }
    if (user) load(null, true)
  }, [user, userLoading, load])

  return (
    <div style={{ maxWidth: 860, margin: '24px auto', padding: '0 16px' }}>
      <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => history.push('/profile')} style={{ marginBottom: 12 }}>
        个人中心
      </Button>
      <Card
        title="星光流水"
        extra={
          <Segmented
            value={filter}
            onChange={(v) => setFilter(v as '' | 'EARN' | 'SPEND')}
            options={[
              { value: '', label: '全部' },
              { value: 'EARN', label: '收入' },
              { value: 'SPEND', label: '支出' },
            ]}
          />
        }
      >
        {loading ? (
          <Empty description="加载中" />
        ) : logs.length === 0 ? (
          <Empty description="暂无星光流水" />
        ) : (
          <>
            <Table
              rowKey={(r) => String(r.id)}
              size="small"
              dataSource={logs}
              pagination={false}
              columns={[
                {
                  title: '类型',
                  dataIndex: 'type',
                  width: 90,
                  render: (v: string) => <span style={{ color: STATUS_COLORS[v] ?? undefined, fontWeight: 600 }}>{v === 'EARN' ? '收入' : '支出'}</span>,
                },
                { title: '事由', dataIndex: 'reason', ellipsis: true },
                {
                  title: '变动',
                  dataIndex: 'amount',
                  width: 90,
                  render: (v: number) => (
                    <span style={{ color: v > 0 ? '#52c41a' : '#ff6b6b', fontWeight: 600 }}>
                      {v > 0 ? '+' : ''}{v}
                    </span>
                  ),
                },
                { title: '余额', dataIndex: 'balanceAfter', width: 90 },
                {
                  title: '时间',
                  dataIndex: 'createdAt',
                  width: 170,
                  render: (v: string) => new Date(v).toLocaleString('zh-CN'),
                },
              ]}
            />
            {hasMore && (
              <div style={{ textAlign: 'center', marginTop: 16 }}>
                <Button loading={loadingMore} onClick={() => load(cursor, false)}>加载更多</Button>
              </div>
            )}
          </>
        )}
      </Card>
    </div>
  )
}
