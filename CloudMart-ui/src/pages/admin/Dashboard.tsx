import { useEffect, useState, useCallback } from 'react'
import { useMessage } from '@/utils/useMessage'
import { Card, Col, Row, Statistic, Table, Spin, Tag, Button, Select, Empty } from 'antd'
import {
  ShoppingCartOutlined,
  DollarOutlined,
  UserOutlined,
  StarOutlined,
  AlertOutlined,
  FileTextOutlined,
  BarChartOutlined,
  CommentOutlined,
  DownloadOutlined,
  HeartOutlined,
  CheckCircleOutlined,
  FireOutlined,
} from '@ant-design/icons'
import { getDashboardStats } from '@/api/admin/system'
import { getAdminPosts, getCommunityStats, getCommunityTrend } from '@/api/admin/community'
import type { AdminPostRecord } from '@/api/admin/community'
import { getAdminWishes, getAdminWishStats } from '@/api/admin/wish'
import type { AdminWishRecord, AdminWishStats } from '@/api/admin/wish'

interface DashboardStats {
  userCount: number
  onlineCount: number
  todayOrderCount: number
  todayRevenue: number
}

interface CommunityTrendItem {
  date: string
  postCount: number
  commentCount: number
  reportCount: number
}

const POST_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '草稿', color: 'default' },
  1: { label: '已发布', color: 'green' },
  2: { label: '隐藏', color: 'orange' },
  3: { label: '已删除', color: 'red' },
}

const WISH_STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'processing',
  FULFILLING: 'cyan',
  FULFILLED: 'success',
  OVERDUE: 'warning',
}

// 顶部综合指标：社区内容生态优先，电商弱化为辅助指标（综合娱乐社区定位）
const OVERVIEW_CARDS = [
  { title: '用户总数', key: 'userCount', icon: UserOutlined, accentColor: 'var(--color-primary)', prefix: '' },
  { title: '今日新帖', key: 'todayPostCount', icon: FileTextOutlined, accentColor: '#2ED573', prefix: '' },
  { title: '今日新心愿', key: 'todayWishCount', icon: StarOutlined, accentColor: '#A78BFA', prefix: '' },
  { title: '今日评论', key: 'todayCommentCount', icon: CommentOutlined, accentColor: '#70A1FF', prefix: '' },
  { title: '今日订单', key: 'todayOrderCount', icon: ShoppingCartOutlined, accentColor: '#FFA502', prefix: '' },
  { title: '今日销售额', key: 'todayRevenue', icon: DollarOutlined, accentColor: '#2ED573', prefix: '¥' },
] as const

const WISH_CARDS = [
  { title: '心愿总数', key: 'totalWishCount', icon: StarOutlined, accentColor: '#A78BFA' },
  { title: '已实现', key: 'fulfilledWishCount', icon: CheckCircleOutlined, accentColor: '#2ED573' },
  { title: '今日打卡', key: 'todayCheckinCount', icon: FireOutlined, accentColor: '#FFA502' },
  { title: '今日互动', key: 'todayInteractionCount', icon: HeartOutlined, accentColor: '#FF6B6B' },
] as const

function exportToCsv(filename: string, headers: string[], rows: string[][]) {
  const bom = '\uFEFF'
  const csvContent = [
    headers.join(','),
    ...rows.map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(',')),
  ].join('\n')
  const blob = new Blob([bom + csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
}

function MiniBarChart({ data, dataKey, color, height = 120 }: { data: number[]; dataKey: string; color: string; height?: number }) {
  if (data.length === 0) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height }}>
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
      </div>
    )
  }
  const maxVal = Math.max(...data, 1)
  const barWidth = Math.max(8, Math.floor(280 / data.length) - 4)

  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 2, height, padding: '8px 0' }}>
      {data.map((val, i) => (
        <div
          key={`${dataKey}-${i}`}
          style={{
            width: barWidth,
            height: Math.max(2, (val / maxVal) * (height - 20)),
            background: `linear-gradient(to top, ${color}, ${color}88)`,
            borderRadius: 3,
            transition: 'height 0.3s ease',
            position: 'relative',
          }}
          title={`${val}`}
        />
      ))}
    </div>
  )
}

export default function Dashboard() {
  const message = useMessage()
  const [overview, setOverview] = useState<Record<string, number>>({})
  const [communityTrend, setCommunityTrend] = useState<CommunityTrendItem[]>([])
  const [wishStats, setWishStats] = useState<AdminWishStats | null>(null)
  const [latestWishes, setLatestWishes] = useState<AdminWishRecord[]>([])
  const [latestPosts, setLatestPosts] = useState<AdminPostRecord[]>([])
  const [trendDays, setTrendDays] = useState(7)
  const [loading, setLoading] = useState(true)

  async function fetchData() {
    setLoading(true)
    try {
      // allSettled 容错：无对应模块权限/服务降级时对应分区显示空态，不阻塞整页
      const [statsRes, postsRes, communityRes, wishStatsRes, wishListRes] = await Promise.allSettled([
        getDashboardStats(),
        getAdminPosts({ page: 1, pageSize: 6 }),
        getCommunityStats(),
        getAdminWishStats(),
        getAdminWishes({ page: 1, pageSize: 5 }),
      ])

      if (statsRes.status === 'fulfilled' && statsRes.value.data) {
        const resData = (statsRes.value.data as { data: DashboardStats }).data
        setOverview({
          userCount: resData?.userCount ?? 0,
          todayOrderCount: resData?.todayOrderCount ?? 0,
          todayRevenue: resData?.todayRevenue ?? 0,
        })
      }
      if (postsRes.status === 'fulfilled' && postsRes.value.data) {
        const resData = postsRes.value.data as { data: AdminPostRecord[] }
        setLatestPosts(resData.data ?? [])
      }
      if (communityRes.status === 'fulfilled' && communityRes.value.data) {
        const resData = (communityRes.value.data as { data: Record<string, number> }).data
        setOverview((prev) => ({
          ...prev,
          todayPostCount: resData?.todayPostCount ?? 0,
          todayCommentCount: resData?.todayCommentCount ?? 0,
          pendingReviewCount: resData?.pendingReviewCount ?? 0,
          pendingReportCount: resData?.pendingReportCount ?? 0,
          totalPostCount: resData?.totalPostCount ?? 0,
          totalCommentCount: resData?.totalCommentCount ?? 0,
        }))
      }
      if (wishStatsRes.status === 'fulfilled' && wishStatsRes.value.data) {
        const resData = (wishStatsRes.value.data as { data: AdminWishStats }).data
        setWishStats(resData)
        setOverview((prev) => ({
          ...prev,
          todayWishCount: resData?.todayWishCount ?? 0,
        }))
      }
      if (wishListRes.status === 'fulfilled' && wishListRes.value.data) {
        const resData = wishListRes.value.data as { data: AdminWishRecord[] }
        setLatestWishes(resData.data ?? [])
      }
    } finally {
      setLoading(false)
    }
  }

  async function fetchCommunityTrend(days: number) {
    try {
      const res = await getCommunityTrend(days)
      const data = (res.data as { data?: CommunityTrendItem[] }).data ?? []
      setCommunityTrend(data)
    } catch {
      setCommunityTrend([])
    }
  }

  useEffect(() => {
    fetchData()
  }, [])

  useEffect(() => {
    fetchCommunityTrend(trendDays)
  }, [trendDays])

  const handleExportCommunityTrend = useCallback(() => {
    if (communityTrend.length === 0) {
      message.warning('暂无数据可导出')
      return
    }
    const headers = ['日期', '帖子数', '评论数', '举报数']
    const rows = communityTrend.map((item) => [
      item.date,
      String(item.postCount),
      String(item.commentCount),
      String(item.reportCount),
    ])
    exportToCsv(`内容趋势_${trendDays}天.csv`, headers, rows)
    message.success('导出成功')
  }, [communityTrend, trendDays])

  const postColumns = [
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    { title: '作者ID', dataIndex: 'userId', key: 'userId', width: 90 },
    { title: '点赞', dataIndex: 'likeCount', key: 'likeCount', width: 70 },
    { title: '评论', dataIndex: 'commentCount', key: 'commentCount', width: 70 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 90, render: (v: number) => <Tag color={POST_STATUS_MAP[v]?.color ?? 'default'}>{POST_STATUS_MAP[v]?.label ?? '未知'}</Tag> },
    { title: '发布时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
  ]

  const wishColumns = [
    { title: '心愿', dataIndex: 'title', key: 'title', ellipsis: true },
    { title: '状态', dataIndex: 'status', key: 'status', width: 90, render: (v: string) => <Tag color={WISH_STATUS_COLOR[v] ?? 'default'}>{v}</Tag> },
    { title: '点亮', dataIndex: 'lightCount', key: 'lightCount', width: 60 },
    { title: '祝福', dataIndex: 'blessCount', key: 'blessCount', width: 60 },
    { title: '公开', dataIndex: 'visibility', key: 'visibility', width: 80, render: (v: string) => (v === 'PUBLIC' ? <Tag color="purple">公开</Tag> : <Tag>私密</Tag>) },
    { title: '发布时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
  ]

  const trendColumns = [
    { title: '日期', dataIndex: 'date', key: 'date', width: 120 },
    { title: '帖子数', dataIndex: 'postCount', key: 'postCount', width: 100 },
    { title: '评论数', dataIndex: 'commentCount', key: 'commentCount', width: 100 },
    { title: '举报数', dataIndex: 'reportCount', key: 'reportCount', width: 100 },
  ]

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <Spin size="large" description="加载中..." />
      </div>
    )
  }

  return (
    <div style={{ padding: 24 }}>
      {/* 综合概览：社区内容生态 + 电商辅助 */}
      <Row gutter={[16, 16]}>
        {OVERVIEW_CARDS.map((card) => {
          const IconComp = card.icon
          const value = overview[card.key] ?? 0
          return (
            <Col xs={24} sm={12} lg={4} key={card.key}>
              <Card hoverable style={{ borderRadius: 10, border: '1px solid var(--color-border)', background: 'linear-gradient(145deg, rgba(21, 32, 56, 0.8), rgba(11, 18, 32, 0.9))' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <Statistic title={card.title} value={value} prefix={card.prefix} styles={{ content: { color: card.accentColor, fontSize: 26 } }} />
                  </div>
                  <div style={{ width: 52, height: 52, borderRadius: 12, background: `${card.accentColor}15`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <IconComp style={{ fontSize: 26, color: card.accentColor }} />
                  </div>
                </div>
              </Card>
            </Col>
          )
        })}
      </Row>

      {/* 内容创作趋势 */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={14}>
          <Card
            title={<span style={{ color: 'var(--color-text-secondary)' }}>内容创作趋势</span>}
            extra={
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Select value={trendDays} onChange={setTrendDays} size="small" style={{ width: 80 }} options={[{ value: 7, label: '7天' }, { value: 14, label: '14天' }, { value: 30, label: '30天' }]} />
                <Button size="small" icon={<DownloadOutlined />} onClick={handleExportCommunityTrend}>导出</Button>
              </div>
            }
            style={{ borderRadius: 10, border: '1px solid var(--color-border)' }}
          >
            <div style={{ display: 'flex', gap: 24, marginBottom: 16 }}>
              <div style={{ flex: 1 }}>
                <div style={{ color: 'var(--color-text-secondary)', fontSize: 12, marginBottom: 4 }}>帖子数</div>
                <MiniBarChart data={communityTrend.map((d) => d.postCount)} dataKey="post" color="var(--color-primary)" height={80} />
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ color: 'var(--color-text-secondary)', fontSize: 12, marginBottom: 4 }}>评论数</div>
                <MiniBarChart data={communityTrend.map((d) => d.commentCount)} dataKey="comment" color="#A78BFA" height={80} />
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ color: 'var(--color-text-secondary)', fontSize: 12, marginBottom: 4 }}>举报数</div>
                <MiniBarChart data={communityTrend.map((d) => d.reportCount)} dataKey="report" color="#FF6B6B" height={80} />
              </div>
            </div>
            <Table dataSource={communityTrend} columns={trendColumns} rowKey="date" pagination={false} size="small" />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title={<span style={{ color: 'var(--color-text-secondary)' }}>心愿宇宙</span>} style={{ borderRadius: 10, border: '1px solid var(--color-border)', height: '100%' }}>
            <Row gutter={[12, 12]}>
              {WISH_CARDS.map((card) => {
                const IconComp = card.icon
                const value = wishStats?.[card.key] ?? 0
                return (
                  <Col span={12} key={card.key}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', borderRadius: 10, background: 'rgba(21, 32, 56, 0.6)' }}>
                      <div style={{ width: 44, height: 44, borderRadius: 10, background: `${card.accentColor}15`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                        <IconComp style={{ fontSize: 22, color: card.accentColor }} />
                      </div>
                      <Statistic title={card.title} value={value} styles={{ content: { color: card.accentColor, fontSize: 22 } }} />
                    </div>
                  </Col>
                )
              })}
            </Row>
            <div style={{ marginTop: 12, color: 'var(--color-text-tertiary)', fontSize: 12, textAlign: 'center' }}>
              进行中 {wishStats?.activeWishCount ?? 0} 个心愿正在被守护
            </div>
          </Card>
        </Col>
      </Row>

      {/* 最新动态：社区帖子 + 心愿流 */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card title={<span style={{ color: 'var(--color-text-secondary)' }}>最新社区动态</span>} style={{ borderRadius: 10, border: '1px solid var(--color-border)' }}>
            <Table dataSource={latestPosts} columns={postColumns} rowKey="id" pagination={false} size="small" scroll={{ x: 700 }} />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title={<span style={{ color: 'var(--color-text-secondary)' }}>最新心愿</span>} style={{ borderRadius: 10, border: '1px solid var(--color-border)' }}>
            <Table dataSource={latestWishes} columns={wishColumns} rowKey="id" pagination={false} size="small" scroll={{ x: 700 }} />
          </Card>
        </Col>
      </Row>

      {/* 待办提醒 */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card style={{ borderRadius: 10, border: '1px solid var(--color-border)' }}>
            <Statistic
              title="待审核帖子"
              value={overview.pendingReviewCount ?? 0}
              prefix={<AlertOutlined style={{ color: '#FFA502' }} />}
              styles={{ content: { color: '#FFA502' } }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card style={{ borderRadius: 10, border: '1px solid var(--color-border)' }}>
            <Statistic
              title="待处理举报"
              value={overview.pendingReportCount ?? 0}
              prefix={<AlertOutlined style={{ color: '#FF6B6B' }} />}
              styles={{ content: { color: '#FF6B6B' } }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card style={{ borderRadius: 10, border: '1px solid var(--color-border)' }}>
            <Statistic
              title="总帖子数"
              value={overview.totalPostCount ?? 0}
              prefix={<BarChartOutlined style={{ color: '#2ED573' }} />}
              styles={{ content: { color: '#2ED573' } }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card style={{ borderRadius: 10, border: '1px solid var(--color-border)' }}>
            <Statistic
              title="总评论数"
              value={overview.totalCommentCount ?? 0}
              prefix={<CommentOutlined style={{ color: '#70A1FF' }} />}
              styles={{ content: { color: '#70A1FF' } }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
