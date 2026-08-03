import { useEffect, useState, useCallback } from 'react'
import { Card, Col, Row, Statistic, Table, Spin, Tag, Button, Select, message } from 'antd'
import {
  ShoppingCartOutlined,
  DollarOutlined,
  UserOutlined,
  AppstoreOutlined,
  AlertOutlined,
  FileTextOutlined,
  BarChartOutlined,
  CommentOutlined,
  DownloadOutlined,
} from '@ant-design/icons'
import { getDashboardStats, getRecentOrders, getSalesTrend } from '@/api/admin/system'
import { getAdminPosts, getCommunityStats, getCommunityTrend } from '@/api/admin/community'
import type { AdminPostRecord } from '@/api/admin/community'

interface DashboardStats {
  userCount: number
  roleCount: number
  menuCount: number
  onlineCount: number
  todayOrderCount: number
  todayRevenue: number
  productCount: number
  memberCount: number
}

interface RecentOrder {
  id: number
  orderNo: string
  username: string
  totalAmount: number
  status: string
  createdAt: string
}

interface SalesTrendItem {
  date: string
  sales: number
  orders: number
}

interface CommunityStats {
  totalPostCount: number
  todayPostCount: number
  pendingReviewCount: number
  rejectedPostCount: number
  totalCommentCount: number
  todayCommentCount: number
  pendingReportCount: number
  totalReportCount: number
}

interface CommunityTrendItem {
  date: string
  postCount: number
  commentCount: number
  reportCount: number
}

const ORDER_STATUS_MAP: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待付款', color: 'default' },
  PAID: { label: '已付款', color: 'processing' },
  SHIPPED: { label: '已发货', color: 'warning' },
  COMPLETED: { label: '已完成', color: 'success' },
  CANCELLED: { label: '已取消', color: 'error' },
  REFUNDING: { label: '退款中', color: 'warning' },
}

const POST_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '草稿', color: 'default' },
  1: { label: '已发布', color: 'green' },
  2: { label: '隐藏', color: 'orange' },
  3: { label: '已删除', color: 'red' },
}

const STAT_CARDS = [
  { title: '今日订单', key: 'todayOrderCount', icon: ShoppingCartOutlined, accentColor: 'var(--color-primary)', prefix: '' },
  { title: '今日销售额', key: 'todayRevenue', icon: DollarOutlined, accentColor: '#2ED573', prefix: '¥' },
  { title: '会员总数', key: 'memberCount', icon: UserOutlined, accentColor: '#A78BFA', prefix: '' },
  { title: '商品总数', key: 'productCount', icon: AppstoreOutlined, accentColor: '#FFA502', prefix: '' },
] as const

const COMMUNITY_STAT_CARDS = [
  { title: '今日新帖', key: 'todayPostCount' as const, icon: FileTextOutlined, accentColor: 'var(--color-primary)' },
  { title: '待审核', key: 'pendingReviewCount' as const, icon: AlertOutlined, accentColor: '#FFA502' },
  { title: '待处理举报', key: 'pendingReportCount' as const, icon: AlertOutlined, accentColor: '#FF6B6B' },
  { title: '总帖子数', key: 'totalPostCount' as const, icon: BarChartOutlined, accentColor: '#2ED573' },
  { title: '今日评论', key: 'todayCommentCount' as const, icon: CommentOutlined, accentColor: '#A78BFA' },
  { title: '总评论数', key: 'totalCommentCount' as const, icon: CommentOutlined, accentColor: '#70A1FF' },
  { title: '总举报数', key: 'totalReportCount' as const, icon: AlertOutlined, accentColor: '#FF6348' },
  { title: '已驳回帖', key: 'rejectedPostCount' as const, icon: FileTextOutlined, accentColor: '#FF4757' },
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
  if (data.length === 0) return null
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
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [recentOrders, setRecentOrders] = useState<RecentOrder[]>([])
  const [salesTrend, setSalesTrend] = useState<SalesTrendItem[]>([])
  const [communityStats, setCommunityStats] = useState<CommunityStats>({
    totalPostCount: 0, todayPostCount: 0, pendingReviewCount: 0, rejectedPostCount: 0,
    totalCommentCount: 0, todayCommentCount: 0, pendingReportCount: 0, totalReportCount: 0,
  })
  const [communityTrend, setCommunityTrend] = useState<CommunityTrendItem[]>([])
  const [trendDays, setTrendDays] = useState(7)
  const [latestPosts, setLatestPosts] = useState<AdminPostRecord[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchData()
  }, [])

  useEffect(() => {
    fetchCommunityTrend(trendDays)
  }, [trendDays])

  async function fetchData() {
    setLoading(true)
    try {
      const [statsRes, ordersRes, trendRes, postsRes, communityRes] = await Promise.allSettled([
        getDashboardStats(),
        getRecentOrders({ pageSize: 10 }),
        getSalesTrend({ days: 7 }),
        getAdminPosts({ page: 1, pageSize: 5 }),
        getCommunityStats(),
      ])

      if (statsRes.status === 'fulfilled' && statsRes.value.data) {
        const resData = (statsRes.value.data as { data: DashboardStats }).data
        setStats(resData)
      }
      if (ordersRes.status === 'fulfilled' && ordersRes.value.data) {
        const resData = (ordersRes.value.data as { data: RecentOrder[] }).data
        setRecentOrders(resData ?? [])
      }
      if (trendRes.status === 'fulfilled' && trendRes.value.data) {
        const resData = (trendRes.value.data as { data: SalesTrendItem[] }).data
        setSalesTrend(resData ?? [])
      }
      if (postsRes.status === 'fulfilled' && postsRes.value.data) {
        const resData = postsRes.value.data as { data: AdminPostRecord[]; meta?: { total?: number } }
        setLatestPosts(resData.data ?? [])
      }
      if (communityRes.status === 'fulfilled' && communityRes.value.data) {
        const resData = (communityRes.value.data as { data: Record<string, number> }).data
        setCommunityStats({
          totalPostCount: resData.totalPostCount ?? 0,
          todayPostCount: resData.todayPostCount ?? 0,
          pendingReviewCount: resData.pendingReviewCount ?? 0,
          rejectedPostCount: resData.rejectedPostCount ?? 0,
          totalCommentCount: resData.totalCommentCount ?? 0,
          todayCommentCount: resData.todayCommentCount ?? 0,
          pendingReportCount: resData.pendingReportCount ?? 0,
          totalReportCount: resData.totalReportCount ?? 0,
        })
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
    exportToCsv(`社区趋势_${trendDays}天.csv`, headers, rows)
    message.success('导出成功')
  }, [communityTrend, trendDays])

  const handleExportOrders = useCallback(() => {
    if (recentOrders.length === 0) {
      message.warning('暂无订单数据可导出')
      return
    }
    const headers = ['订单号', '用户', '金额', '状态', '下单时间']
    const rows = recentOrders.map((o) => [
      o.orderNo,
      o.username,
      String(o.totalAmount),
      ORDER_STATUS_MAP[o.status]?.label ?? o.status,
      o.createdAt,
    ])
    exportToCsv('最近订单.csv', headers, rows)
    message.success('导出成功')
  }, [recentOrders])

  const orderColumns = [
    { title: '订单号', dataIndex: 'orderNo', key: 'orderNo', width: 180 },
    { title: '用户', dataIndex: 'username', key: 'username', width: 120 },
    { title: '金额', dataIndex: 'totalAmount', key: 'totalAmount', width: 120, render: (v: number) => `¥${v?.toFixed(2) ?? '0.00'}` },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <Tag color={ORDER_STATUS_MAP[v]?.color ?? 'default'}>{ORDER_STATUS_MAP[v]?.label ?? v}</Tag> },
    { title: '下单时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  ]

  const trendColumns = [
    { title: '日期', dataIndex: 'date', key: 'date' },
    { title: '销售额', dataIndex: 'sales', key: 'sales', render: (v: number) => `¥${v?.toFixed(2) ?? '0.00'}` },
    { title: '订单数', dataIndex: 'orders', key: 'orders' },
  ]

  const communityColumns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    { title: '作者ID', dataIndex: 'userId', key: 'userId', width: 100 },
    { title: '点赞', dataIndex: 'likeCount', key: 'likeCount', width: 80 },
    { title: '评论', dataIndex: 'commentCount', key: 'commentCount', width: 80 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: number) => <Tag color={POST_STATUS_MAP[v]?.color ?? 'default'}>{POST_STATUS_MAP[v]?.label ?? '未知'}</Tag> },
    { title: '发布时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  ]

  const communityTrendColumns = [
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
      <Row gutter={[16, 16]}>
        {STAT_CARDS.map((card) => {
          const IconComp = card.icon
          const value = stats?.[card.key] ?? 0
          return (
            <Col xs={24} sm={12} lg={6} key={card.key}>
              <Card hoverable style={{ borderRadius: 10, border: '1px solid var(--color-border)', background: 'linear-gradient(145deg, rgba(21, 32, 56, 0.8), rgba(11, 18, 32, 0.9))' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <Statistic title={card.title} value={value} prefix={card.prefix} styles={{ content: { color: card.accentColor, fontSize: 28 } }} />
                  </div>
                  <div style={{ width: 56, height: 56, borderRadius: 12, background: `${card.accentColor}15`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <IconComp style={{ fontSize: 28, color: card.accentColor }} />
                  </div>
                </div>
              </Card>
            </Col>
          )
        })}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={14}>
          <Card title={<span style={{ color: 'var(--color-text-secondary)' }}>销售趋势（近7天）</span>} style={{ borderRadius: 10, border: '1px solid var(--color-border)' }}>
            <Table dataSource={salesTrend} columns={trendColumns} rowKey="date" pagination={false} size="small" />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card
            title={<span style={{ color: 'var(--color-text-secondary)' }}>最近订单</span>}
            extra={<Button size="small" icon={<DownloadOutlined />} onClick={handleExportOrders}>导出</Button>}
            style={{ borderRadius: 10, border: '1px solid var(--color-border)' }}
          >
            <Table dataSource={recentOrders} columns={orderColumns} rowKey="id" pagination={false} size="small" scroll={{ x: 700 }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        {COMMUNITY_STAT_CARDS.map((card) => {
          const IconComp = card.icon
          const value = communityStats[card.key]
          return (
            <Col xs={24} sm={12} lg={6} key={card.key}>
              <Card hoverable style={{ borderRadius: 10, border: '1px solid var(--color-border)', background: 'linear-gradient(145deg, rgba(21, 32, 56, 0.8), rgba(11, 18, 32, 0.9))' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <Statistic title={card.title} value={value} styles={{ content: { color: card.accentColor, fontSize: 28 } }} />
                  </div>
                  <div style={{ width: 56, height: 56, borderRadius: 12, background: `${card.accentColor}15`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <IconComp style={{ fontSize: 28, color: card.accentColor }} />
                  </div>
                </div>
              </Card>
            </Col>
          )
        })}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card
            title={<span style={{ color: 'var(--color-text-secondary)' }}>社区趋势</span>}
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
            <Table dataSource={communityTrend} columns={communityTrendColumns} rowKey="date" pagination={false} size="small" />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card
            title={<span style={{ color: 'var(--color-text-secondary)' }}>社区动态</span>}
            style={{ borderRadius: 10, border: '1px solid var(--color-border)' }}
          >
            <Table dataSource={latestPosts} columns={communityColumns} rowKey="id" pagination={false} size="small" scroll={{ x: 800 }} />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
