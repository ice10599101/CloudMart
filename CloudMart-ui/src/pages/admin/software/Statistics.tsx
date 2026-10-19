import { useEffect, useState } from 'react'
import { Button, Card, Col, Row, Spin, Statistic, Table, Tabs, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { getSoftwareStatistics, listFanoutTasks, retryFanoutTask ,
  listStatTrends,
  type StatTrendPoint,
} from '@/api/admin/software'
import type { AdminFanoutTask, AdminSoftwareStatistics } from '@/api/admin/software'
import { useMessage } from '@/utils/useMessage'

/**
 * 数据统计（方案 10.7，P1）+ 通知任务运维（SW10）：发布数、审核时长、获取链接次数/人数、
 * 收藏数、新评分、待处理失效反馈；SW10 增加"通知任务"页签——fanout 任务状态、投递聚合、
 * 游标、失败原因、下次执行时间与人工重试（沿用原发布事件身份），权限独立 software:fanout。
 */

const STATUS_COLOR: Record<string, string> = {
  PENDING: 'processing',
  RUNNING: 'blue',
  DONE: 'success',
  FAILED: 'error',
}

const DELIVERY_LABEL: Record<string, string> = {
  PENDING: '待投递',
  SENT: '已投递',
  CANCELLED: '已取消',
  SUPERSEDED: '被新发布取代',
  FAILED: '失败',
}

export default function SoftwareStatistics() {
  const message = useMessage()
  const [data, setData] = useState<AdminSoftwareStatistics | null>(null)
  const [loading, setLoading] = useState(false)
  const [tasks, setTasks] = useState<AdminFanoutTask[]>([])
  const [tasksLoading, setTasksLoading] = useState(false)
  const [retrying, setRetrying] = useState<string | null>(null)
  /** SF06：运营趋势（日序列 + 效率指标） */
  const [trends, setTrends] = useState<StatTrendPoint[]>([])
  const [efficiency, setEfficiency] = useState<Record<string, number | null> | null>(null)
  const [trendsLoading, setTrendsLoading] = useState(false)

  const loadTrends = () => {
    setTrendsLoading(true)
    const to = new Date().toISOString().slice(0, 10)
    const from = new Date(Date.now() - 29 * 86400000).toISOString().slice(0, 10)
    listStatTrends({ from, to, withEfficiency: true })
      .then((res) => {
        const payload = res.data.data as {
          series?: StatTrendPoint[]
          efficiency?: Record<string, number | null>
        }
        setTrends(payload.series ?? [])
        setEfficiency(payload.efficiency ?? null)
      })
      .catch(() => undefined)
      .finally(() => setTrendsLoading(false))
  }

  useEffect(() => {
    setLoading(true)
    getSoftwareStatistics()
      .then((res) => setData(res.data.data))
      .catch(() => message.error('统计加载失败'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const loadTasks = () => {
    setTasksLoading(true)
    listFanoutTasks(1, 50)
      .then((res) => setTasks(res.data.data?.records ?? []))
      .catch(() => message.error('通知任务加载失败'))
      .finally(() => setTasksLoading(false))
  }

  const handleRetry = async (taskId: string) => {
    setRetrying(taskId)
    try {
      const res = await retryFanoutTask(taskId)
      if (res.data.success) {
        message.success('已重新入队（沿用原发布事件身份）')
        loadTasks()
      } else {
        message.error(res.data.error?.message ?? '重试失败')
      }
    } catch {
      message.error('重试失败')
    } finally {
      setRetrying(null)
    }
  }

  const windowDays = data?.windowDays ?? 30

  const taskColumns: ColumnsType<AdminFanoutTask> = [
    { title: '软件', dataIndex: 'softwareId', width: 90 },
    { title: '发布序号', dataIndex: 'publicationSeq', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: string) => (
        <Tag color={STATUS_COLOR[status] ?? 'default'}>{status}</Tag>
      ),
    },
    { title: '游标', dataIndex: 'cursorSubscriptionId', width: 100 },
    { title: '失败次数', dataIndex: 'attempts', width: 90 },
    {
      title: '投递聚合',
      dataIndex: 'deliveryCounts',
      render: (counts: Record<string, string>) =>
        Object.entries(counts ?? {}).length === 0
          ? '-'
          : Object.entries(counts)
              .map(([status, count]) => `${DELIVERY_LABEL[status] ?? status} ${count}`)
              .join(' · '),
    },
    {
      title: '下次执行',
      dataIndex: 'nextRetryAt',
      width: 170,
      render: (value: string | null) => value ?? '-',
    },
    {
      title: '失败原因',
      dataIndex: 'lastError',
      ellipsis: true,
      render: (value: string | null) => value ?? '-',
    },
    {
      title: '操作',
      width: 100,
      render: (_, record) =>
        record.status === 'DONE' ? (
          '-'
        ) : (
          <Button
            size="small"
            loading={retrying === record.taskId}
            onClick={() => void handleRetry(record.taskId)}
          >
            人工重试
          </Button>
        ),
    },
  ]

  const statsTab = (
    <>
      <Typography.Paragraph type="secondary">
        {data?.windowNote ?? `趋势类口径为最近 ${windowDays} 天`}
      </Typography.Paragraph>
      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="当前在架软件" value={data?.publishedCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="待审快照" value={data?.pendingReviewCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title={`平均审核时长（${windowDays} 天，分钟）`}
              value={data?.avgReviewMinutes ?? '-'}
              precision={data?.avgReviewMinutes != null ? 1 : undefined}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="待处理失效反馈" value={data?.pendingFeedbacks ?? 0} />
          </Card>
        </Col>

        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title={`获取链接次数（${windowDays} 天）`}
              value={data?.accessCount ?? 0}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title={`获取链接去重人数（${windowDays} 天）`}
              value={data?.accessUsers ?? 0}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="收藏总量" value={data?.favoriteTotal ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title={`新增收藏（${windowDays} 天）`} value={data?.newFavorites ?? 0} />
          </Card>
        </Col>

        <Col xs={12} md={6}>
          <Card>
            <Statistic title={`新评分人数（${windowDays} 天）`} value={data?.newRatings ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title={`新评分均分（${windowDays} 天）`}
              value={data?.newRatingAvg ?? '-'}
              precision={data?.newRatingAvg != null ? 1 : undefined}
              suffix={data?.newRatingAvg != null ? '/ 5 星' : undefined}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="待处理举报" value={data?.pendingReports ?? 0} />
          </Card>
        </Col>
      </Row>
    </>
  )

  return (
    <Spin spinning={loading}>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        运营统计
      </Typography.Title>
      <Tabs
        defaultActiveKey="stats"
        items={[
          { key: 'stats', label: '运营统计', children: statsTab },
          {
            key: 'trends',
            label: '趋势（SF06）',
            children: (
              <>
                <Typography.Paragraph type="secondary">
                  获取链接为站内去重获取次数/人数（非外部下载量）；收藏/订阅为新增与取消分别记录，
                  净值 = 新增 − 取消。效率指标：审核时长为提交到人工决定（分钟），
                  反馈修复未结项不计入分位。
                </Typography.Paragraph>
                {efficiency ? (
                  <Row gutter={16} style={{ marginBottom: 16 }}>
                    <Col span={6}>
                      <Statistic title="审核 P50（分钟）" value={efficiency.reviewP50Minutes ?? '-'} />
                    </Col>
                    <Col span={6}>
                      <Statistic title="审核 P95（分钟）" value={efficiency.reviewP95Minutes ?? '-'} />
                    </Col>
                    <Col span={6}>
                      <Statistic title="反馈修复 P50（分钟）" value={efficiency.feedbackFixP50Minutes ?? '-'} />
                    </Col>
                    <Col span={6}>
                      <Statistic title="反馈未结/被拒" value={`${efficiency.feedbackOpenCount ?? 0} / ${efficiency.feedbackRejectedCount ?? 0}`} />
                    </Col>
                  </Row>
                ) : null}
                <Table<StatTrendPoint>
                  rowKey="date"
                  size="small"
                  loading={trendsLoading}
                  dataSource={trends}
                  pagination={{ defaultPageSize: 15, showSizeChanger: false }}
                  columns={[
                    { title: '日期', dataIndex: 'date', width: 110 },
                    { title: '获取次数', dataIndex: 'resolveCount', width: 90 },
                    { title: '获取人数', dataIndex: 'resolveUserCount', width: 90 },
                    { title: '收藏+', dataIndex: 'favoriteAdd', width: 70 },
                    { title: '收藏-', dataIndex: 'favoriteRemove', width: 70 },
                    { title: '收藏净增', dataIndex: 'favoriteNet', width: 80 },
                    { title: '订阅+', dataIndex: 'subscribeAdd', width: 70 },
                    { title: '订阅-', dataIndex: 'subscribeRemove', width: 70 },
                    { title: '订阅净增', dataIndex: 'subscribeNet', width: 80 },
                  ]}
                />
              </>
            ),
          },
          {
            key: 'fanout',
            label: '通知任务',
            children: (
              <>
                <Typography.Paragraph type="secondary">
                  订阅通知 fanout 任务（SW10）：正常分批让出不计失败；人工重试沿用原发布事件
                  身份（seq/revisionId 不变），不生成新的发布通知。
                </Typography.Paragraph>
                <Table<AdminFanoutTask>
                  rowKey="taskId"
                  size="small"
                  loading={tasksLoading}
                  dataSource={tasks}
                  columns={taskColumns}
                  pagination={{ defaultPageSize: 20, showSizeChanger: false }}
                  onRow={() => ({
                    // 页签首次展开时加载（Tabs 懒渲染）
                  })}
                />
                <TaskListLoader onLoad={loadTasks} />
              </>
            ),
          },
        ]}
        onChange={(key) => {
          if (key === 'fanout' && tasks.length === 0 && !tasksLoading) {
            loadTasks()
          }
          if (key === 'trends' && trends.length === 0 && !trendsLoading) {
            loadTrends()
          }
        }}
      />
    </Spin>
  )
}

/** 页签懒加载辅助：挂载时触发一次加载（Tabs 默认销毁隐藏面板，挂载即需要数据） */
function TaskListLoader({ onLoad }: { onLoad: () => void }) {
  useEffect(() => {
    onLoad()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return null
}
