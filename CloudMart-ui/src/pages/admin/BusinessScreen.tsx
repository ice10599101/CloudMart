import { useEffect, useState } from 'react'
import { Row, Col, Card, Statistic, Table, Tag, Spin } from 'antd'
import {
  UserOutlined,
  ShoppingCartOutlined,
  DollarOutlined,
  FileTextOutlined,
  FullscreenOutlined,
} from '@ant-design/icons'
import { history } from 'umi'
import { getDashboardStats, getSalesTrend, getRecentOrders } from '@/api/admin/system'
import type { ApiResponse } from '@/types/api'

/**
 * N-6 经营大屏：汇报/盯盘模式——大字号指标 + 7 日销售趋势 + 实时订单流。
 * 数据复用既有 /admin/dashboard/{stats,sales-trend,recent-orders} 端点。
 */

interface Stats {
  userCount: number
  memberCount: number
  todayOrderCount: number
  todayRevenue: number | string
  productCount: number
  onlineCount: number
}

interface TrendItem {
  date: string
  orderCount: number
  revenue: number | string
}

interface RecentOrder {
  id: number
  orderNo?: string
  userName?: string
  payAmount?: number
  status?: string
  createdAt?: string
}

const numberValue = (v: unknown) => Number(v ?? 0)

export default function BusinessScreen() {
  const [stats, setStats] = useState<Stats | null>(null)
  const [trend, setTrend] = useState<TrendItem[]>([])
  const [orders, setOrders] = useState<RecentOrder[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([getDashboardStats(), getSalesTrend({ days: 7 }), getRecentOrders({ limit: 10 })])
      .then(([statsRes, trendRes, ordersRes]) => {
        const s = statsRes.data as unknown as ApiResponse<Stats>
        const t = trendRes.data as unknown as ApiResponse<TrendItem[]>
        const o = ordersRes.data as unknown as ApiResponse<RecentOrder[]>
        if (s?.success) setStats(s.data)
        if (t?.success) setTrend(t.data ?? [])
        if (o?.success) setOrders(o.data ?? [])
      })
      .catch(() => undefined)
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 500 }}>
        <Spin size="large" description="大屏加载中..." />
      </div>
    )
  }

  const total7dRevenue = trend.reduce((sum, t) => sum + numberValue(t.revenue), 0)
  const total7dOrders = trend.reduce((sum, t) => sum + numberValue(t.orderCount), 0)

  const bigCards = [
    { title: '会员总数', value: numberValue(stats?.memberCount), icon: <UserOutlined />, color: '#70A1FF' },
    { title: '今日订单', value: numberValue(stats?.todayOrderCount), icon: <ShoppingCartOutlined />, color: '#FFA502' },
    { title: '今日销售额', value: numberValue(stats?.todayRevenue), icon: <DollarOutlined />, color: '#2ED573', prefix: '¥' },
    { title: '7 日销售额', value: total7dRevenue, icon: <DollarOutlined />, color: '#A78BFA', prefix: '¥' },
    { title: '7 日订单', value: total7dOrders, icon: <FileTextOutlined />, color: '#4ECDC4' },
    { title: '在线管理员', value: numberValue(stats?.onlineCount), icon: <UserOutlined />, color: '#FF6B6B' },
  ]

  return (
    <div style={{ padding: 24, background: 'radial-gradient(ellipse at top, rgba(21,32,56,0.9), rgba(11,18,32,1))', minHeight: '100vh' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ color: 'var(--color-text)', margin: 0, letterSpacing: 4 }}>📊 CloudMart 经营大屏</h2>
        <a onClick={() => history.push('/admin/dashboard')} style={{ color: 'var(--color-primary)' }}>
          <FullscreenOutlined /> 返回仪表盘
        </a>
      </div>

      <Row gutter={[16, 16]}>
        {bigCards.map((card) => (
          <Col xs={12} sm={8} lg={4} key={card.title}>
            <Card bordered={false} style={{ borderRadius: 14, background: 'rgba(21,32,56,0.75)', border: '1px solid var(--color-border)' }}>
              <Statistic
                title={<span style={{ fontSize: 13 }}>{card.title}</span>}
                value={card.value}
                prefix={card.prefix}
                styles={{ content: { color: card.color, fontSize: 32, fontWeight: 700 } }}
              />
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={14}>
          <Card title="近 7 日销售趋势" bordered={false} style={{ borderRadius: 14, background: 'rgba(21,32,56,0.75)', border: '1px solid var(--color-border)' }}>
            <Table
              size="small"
              rowKey="date"
              pagination={false}
              dataSource={trend}
              columns={[
                { title: '日期', dataIndex: 'date', width: 120 },
                { title: '订单数', dataIndex: 'orderCount', width: 100 },
                {
                  title: '销售额',
                  dataIndex: 'revenue',
                  render: (v) => <span style={{ color: '#2ED573', fontWeight: 600 }}>¥{numberValue(v).toFixed(2)}</span>,
                },
                {
                  title: '占比',
                  render: (_, record) => {
                    const pct = total7dRevenue > 0 ? Math.round((numberValue(record.revenue) / total7dRevenue) * 100) : 0
                    return (
                      <div style={{ background: 'rgba(0,212,255,0.15)', borderRadius: 6, height: 14, width: `${pct}%`, minWidth: 24, textAlign: 'right', paddingRight: 4 }}>
                        <span style={{ fontSize: 11, color: '#00d4ff' }}>{pct}%</span>
                      </div>
                    )
                  },
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="实时订单流" bordered={false} style={{ borderRadius: 14, background: 'rgba(21,32,56,0.75)', border: '1px solid var(--color-border)' }}>
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={orders}
              columns={[
                { title: '订单号', dataIndex: 'orderNo', ellipsis: true },
                {
                  title: '金额',
                  dataIndex: 'payAmount',
                  width: 90,
                  render: (v) => <span style={{ color: '#FFA502' }}>¥{numberValue(v).toFixed(2)}</span>,
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 90,
                  render: (v) => <Tag color="processing">{v ?? '—'}</Tag>,
                },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
