import { useEffect, useState } from 'react'
import {
  Button,
  Descriptions,
  Drawer,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import RichText from '@/components/RichText'
import {
  getAdminDriftBottleDashboard,
  getAdminDriftBottleDetail,
  listAdminDriftBottles,
  updateAdminDriftBottleHidden,
  type AdminDriftBottle,
  type AdminDriftBottleDashboard,
  type AdminDriftBottleDetail,
} from '@/api/admin/wish'
import { useMessage } from '@/utils/useMessage'

const { Text } = Typography

/**
 * 漂流瓶管理（重设计）：数据看板（状态分布/今日活动/14 天趋势/投瓶榜）+ 瓶子管理
 * （状态/用户/关键词筛选，详情含瓶下评论真实身份，下架为软隐藏）。
 */

const STATUS_META: Record<AdminDriftBottle['status'], { label: string; color: string }> = {
  FLOATING: { label: '漂流中', color: 'processing' },
  PICKED: { label: '已被捞起', color: 'cyan' },
  RETURNED: { label: '被扔回海里', color: 'warning' },
}

const formatTime = (iso: string | null) =>
  iso ? new Date(iso).toLocaleString('zh-CN', { hour12: false }) : '-'

export default function DriftBottles() {
  const message = useMessage()

  // 看板
  const [dashboard, setDashboard] = useState<AdminDriftBottleDashboard | null>(null)
  const [dashboardLoading, setDashboardLoading] = useState(true)

  // 列表
  const [bottles, setBottles] = useState<AdminDriftBottle[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [loading, setLoading] = useState(true)
  const [filterStatus, setFilterStatus] = useState<AdminDriftBottle['status'] | undefined>()
  const [filterUserId, setFilterUserId] = useState<number | undefined>()
  const [filterKeyword, setFilterKeyword] = useState('')

  // 详情抽屉
  const [detail, setDetail] = useState<AdminDriftBottleDetail | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)

  const loadDashboard = async () => {
    setDashboardLoading(true)
    try {
      const res = await getAdminDriftBottleDashboard()
      if (res.data.success) setDashboard(res.data.data)
    } catch {
      // 拦截器已提示
    } finally {
      setDashboardLoading(false)
    }
  }

  const loadBottles = async (
    targetPage = page,
    targetPageSize = pageSize,
  ) => {
    setLoading(true)
    try {
      const res = await listAdminDriftBottles({
        userId: filterUserId,
        status: filterStatus,
        keyword: filterKeyword.trim() || undefined,
        page: targetPage,
        pageSize: targetPageSize,
      })
      if (res.data.success) {
        setBottles(res.data.data ?? [])
        setTotal(Number(res.data.meta?.total ?? 0))
      }
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadDashboard()
    loadBottles(1, 20)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleSearch = () => {
    setPage(1)
    loadBottles(1, pageSize)
  }

  const handleTableChange = (pagination: TablePaginationConfig) => {
    const next = pagination.current ?? 1
    const nextSize = pagination.pageSize ?? 20
    setPage(next)
    setPageSize(nextSize)
    loadBottles(next, nextSize)
  }

  const openDetail = async (id: number) => {
    setDetailOpen(true)
    setDetailLoading(true)
    try {
      const res = await getAdminDriftBottleDetail(id)
      if (res.data.success) setDetail(res.data.data)
    } catch {
      // 拦截器已提示
    } finally {
      setDetailLoading(false)
    }
  }

  const toggleHidden = async (bottle: AdminDriftBottle) => {
    try {
      await updateAdminDriftBottleHidden(bottle.id, !bottle.isHidden)
      message.success(bottle.isHidden ? '已恢复展示' : '已下架（用户端不可见）')
      loadBottles()
      loadDashboard()
    } catch {
      // 拦截器已提示
    }
  }

  const trendMax = dashboard
    ? Math.max(1, ...dashboard.trend.map((d) => Math.max(d.throwCount, d.fishCount)))
    : 1

  const columns: ColumnsType<AdminDriftBottle> = [
    { title: 'ID', dataIndex: 'id', width: 150, render: (id: number) => <Text copyable>{id}</Text> },
    {
      title: '类型',
      width: 80,
      render: (_, bottle) =>
        bottle.wishId ? <Tag color="gold">关联心愿</Tag> : <Tag>自由文字</Tag>,
    },
    {
      title: '内容',
      ellipsis: true,
      render: (_, bottle) => (
        <div style={{ maxWidth: 320 }}>
          {bottle.wishId ? (
            <span><Tag color="gold">心愿</Tag>{bottle.wishTitle}</span>
          ) : (
            <RichText content={bottle.content} clamp={3} variant="preview" />
          )}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (status: AdminDriftBottle['status']) => {
        const meta = STATUS_META[status]
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '投瓶人',
      dataIndex: 'throwerUserId',
      width: 170,
      render: (_, bottle) => (
        <span>
          <Text strong>{bottle.throwerNickname}</Text>
          <Tag color={bottle.isAnonymous ? 'default' : 'green'} style={{ marginLeft: 6 }}>
            {bottle.isAnonymous ? '匿名' : '实名'}
          </Tag>
        </span>
      ),
    },
    {
      title: '捞瓶人',
      dataIndex: 'pickerUserId',
      width: 170,
      render: (_, bottle) =>
        bottle.pickerUserId === null ? (
          <Text type="secondary">-</Text>
        ) : (
          <span>
            <Text strong>{bottle.pickerNickname}</Text>
            <Tag color={bottle.pickerIsAnonymous ? 'default' : 'green'} style={{ marginLeft: 6 }}>
              {bottle.pickerIsAnonymous ? '匿名' : '实名'}
            </Tag>
          </span>
        ),
    },
    {
      title: '评论',
      dataIndex: 'commentCount',
      width: 70,
    },
    {
      title: '回流',
      dataIndex: 'returnCount',
      width: 70,
      render: (count: number) => (count > 0 ? <Tag color="orange">{count} 次</Tag> : '-'),
    },
    {
      title: '收藏',
      dataIndex: 'isCollected',
      width: 70,
      render: (collected: boolean) => (collected ? <Tag color="gold">已收藏</Tag> : '-'),
    },
    { title: '投瓶时间', dataIndex: 'thrownAt', width: 150, render: formatTime },
    {
      title: '操作',
      width: 160,
      render: (_, bottle) => (
        <Space size="small">
          <Button size="small" type="link" onClick={() => openDetail(bottle.id)}>详情</Button>
          <Popconfirm
            title={bottle.isHidden ? '恢复该漂流瓶展示？' : '下架该漂流瓶？'}
            description="下架为软隐藏，用户端不可见，数据保留"
            onConfirm={() => toggleHidden(bottle)}
          >
            <Button size="small" type="link" danger={!bottle.isHidden}>
              {bottle.isHidden ? '恢复' : '下架'}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const dashboardPanel = (
    <Spin spinning={dashboardLoading}>
      {dashboard && (
        <>
          <div style={{ display: 'flex', gap: 32, flexWrap: 'wrap', marginBottom: 8 }}>
            <Statistic title="瓶子总数" value={dashboard.totalBottles} />
            <Statistic title="漂流中" value={dashboard.floatingCount} />
            <Statistic title="已被捞起" value={dashboard.pickedCount} />
            <Statistic title="被扔回海里" value={dashboard.returnedCount} />
            <Statistic title="有评论瓶子" value={dashboard.repliedCount} />
            <Statistic title="被收藏" value={dashboard.collectedCount} />
            <Statistic
              title="已下架"
              value={dashboard.hiddenCount}
              styles={{ content: { color: dashboard.hiddenCount > 0 ? '#ffb347' : undefined } }}
            />
          </div>
          <div style={{ display: 'flex', gap: 32, flexWrap: 'wrap', marginBottom: 24 }}>
            <Statistic title="今日投瓶" value={dashboard.todayThrowCount} />
            <Statistic title="今日打捞" value={dashboard.todayFishCount} />
            <Statistic title="今日评论" value={dashboard.todayCommentCount} />
          </div>

          <Typography.Title level={5}>近 14 天趋势（投瓶 / 打捞）</Typography.Title>
          <div style={{ display: 'flex', gap: 6, alignItems: 'flex-end', height: 140, marginBottom: 4 }}>
            {dashboard.trend.map((day) => (
              <Tooltip
                key={day.date}
                title={`${day.date} · 投瓶 ${day.throwCount} / 打捞 ${day.fishCount}`}
              >
                <div style={{ flex: 1, display: 'flex', gap: 2, alignItems: 'flex-end', height: '100%' }}>
                  <div
                    style={{
                      flex: 1,
                      height: `${(day.throwCount / trendMax) * 100}%`,
                      minHeight: 2,
                      background: '#1677ff',
                      borderRadius: '2px 2px 0 0',
                    }}
                  />
                  <div
                    style={{
                      flex: 1,
                      height: `${(day.fishCount / trendMax) * 100}%`,
                      minHeight: 2,
                      background: '#36cfc9',
                      borderRadius: '2px 2px 0 0',
                    }}
                  />
                </div>
              </Tooltip>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 16, marginBottom: 24 }}>
            <span><Tag color="blue" />投瓶</span>
            <span><Tag color="cyan" />打捞</span>
          </div>

          <Typography.Title level={5}>投瓶榜 Top10</Typography.Title>
          <ol style={{ paddingLeft: 20, margin: 0 }}>
            {dashboard.topThrowers.map((thrower) => (
              <li key={thrower.userId} style={{ marginBottom: 4 }}>
                用户 {thrower.userId}
                <Text type="secondary">（{thrower.nickname}）</Text>
                ：{thrower.throwCount} 瓶
              </li>
            ))}
            {dashboard.topThrowers.length === 0 && <Text type="secondary">暂无数据</Text>}
          </ol>
        </>
      )}
    </Spin>
  )

  const managePanel = (
    <>
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 150 }}
          value={filterStatus}
          onChange={(v) => setFilterStatus(v)}
          options={Object.entries(STATUS_META).map(([value, meta]) => ({
            value,
            label: meta.label,
          }))}
        />
        <InputNumber
          min={1}
          placeholder="用户 ID（投/捞）"
          style={{ width: 170 }}
          value={filterUserId}
          onChange={(v) => setFilterUserId(v ?? undefined)}
        />
        <Input
          allowClear
          placeholder="关键词（瓶内文字/心愿标题）"
          style={{ width: 220 }}
          value={filterKeyword}
          onChange={(e) => setFilterKeyword(e.target.value)}
          onPressEnter={handleSearch}
        />
        <Button type="primary" onClick={handleSearch}>查询</Button>
      </Space>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={bottles}
        loading={loading}
        onChange={handleTableChange}
        pagination={{ current: page, pageSize, total, showSizeChanger: true }}
        scroll={{ x: 1200 }}
      />
    </>
  )

  return (
    <>
      <Tabs
        defaultActiveKey="dashboard"
        items={[
          { key: 'dashboard', label: '数据看板', children: dashboardPanel },
          { key: 'manage', label: '瓶子管理', children: managePanel },
        ]}
      />
      <Drawer
        title={`漂流瓶详情${detail ? ` #${detail.bottle.id}` : ''}`}
        width={560}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        destroyOnHidden
      >
        <Spin spinning={detailLoading}>
        {detail && (
          <>
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="类型">
                {detail.bottle.wishId
                  ? <Tag color="gold">关联心愿 #{detail.bottle.wishId} {detail.bottle.wishTitle}</Tag>
                  : '自由文字'}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_META[detail.bottle.status].color}>
                  {STATUS_META[detail.bottle.status].label}
                </Tag>
                {detail.bottle.isHidden && <Tag color="red">已下架</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="内容">
                {detail.bottle.wishId
                  ? <Text type="secondary">瓶子关联心愿，正文见「类型」行</Text>
                  : <RichText content={detail.bottle.content} />}
              </Descriptions.Item>
              <Descriptions.Item label="投瓶人">
                <Text strong>{detail.bottle.throwerNickname}</Text>
                <Tag color={detail.bottle.isAnonymous ? 'default' : 'green'} style={{ marginLeft: 6 }}>
                  {detail.bottle.isAnonymous ? '匿名' : '实名'}
                </Tag>
                · {formatTime(detail.bottle.thrownAt)}
              </Descriptions.Item>
              <Descriptions.Item label="捞瓶人">
                {detail.bottle.pickerUserId
                  ? (
                    <span>
                      <Text strong>{detail.bottle.pickerNickname}</Text>
                      <Tag color={detail.bottle.pickerIsAnonymous ? 'default' : 'green'} style={{ marginLeft: 6 }}>
                        {detail.bottle.pickerIsAnonymous ? '匿名' : '实名'}
                      </Tag>
                      · {formatTime(detail.bottle.pickedAt)}
                    </span>
                  )
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="回流次数">{detail.bottle.returnCount}</Descriptions.Item>
              <Descriptions.Item label="收藏">{detail.bottle.isCollected ? '已收藏' : '未收藏'}</Descriptions.Item>
            </Descriptions>

            <Typography.Title level={5} style={{ marginTop: 16 }}>
              瓶下评论（{detail.comments.length}，真实身份）
            </Typography.Title>
            {detail.comments.length === 0 && <Text type="secondary">暂无评论</Text>}
            {detail.comments.map((comment) => (
              <div key={comment.id} style={{ marginBottom: 8 }}>
                <Text strong>用户 {comment.userId}</Text>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  （{comment.isAnonymous ? '匿名' : comment.nickname}）· {formatTime(comment.createdAt)}
                  {comment.parentId ? ` · 回复 #${comment.parentId}` : ''}
                </Text>
                <div style={{ fontSize: 13 }}>{comment.content}</div>
              </div>
            ))}
          </>
        )}
        </Spin>
      </Drawer>
    </>
  )
}
