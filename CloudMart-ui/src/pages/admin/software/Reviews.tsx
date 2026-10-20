import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Drawer, Input, Radio, Select, Space, Table, Tabs, Tag, Typography } from 'antd'
import DOMPurify from 'dompurify'
import {
  claimReview,
  decideReview,
  getReviewDetail,
  listPendingReviews,
  listReviewCheckTasks,
  releaseReview,
  retryReviewCheckTask,
} from '@/api/admin/software'
import type {
  AdminReviewCheckTask,
  AdminReviewDetail,
  AdminRevisionSnapshot,
} from '@/api/admin/software'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

interface PendingRow {
  softwareId: string
  revisionId: string
  name: string | null
  summary: string | null
  editorId: string
  editorNickname?: string
  submittedAt: string
  lifecycle: string
  /** SW04：CHECKING 自动检查中 / PENDING 待人工审核 */
  reviewStatus?: string
  waitingSeconds?: number
  checkTaskStatus?: string | null
  checkAttempts?: number | null
  checkNextRetryAt?: string | null
  checkLastError?: string | null
}

const CHECK_TASK_STATUS_LABEL: Record<string, string> = {
  PENDING: '待执行',
  RUNNING: '执行中',
  DONE: '已完成',
  REJECTED: '已拒绝',
  CANCELLED: '已取消',
  DEAD_LETTER: '死信待处理',
}

/** 需并列对比的标量字段（10.2：正文/截图/来源单独呈现） */
const COMPARE_FIELDS: Array<{ key: keyof AdminRevisionSnapshot; label: string }> = [
  { key: 'name', label: '名称' },
  { key: 'summary', label: '一句话简介' },
  { key: 'categoryId', label: '分类' },
  { key: 'licenseType', label: '授权类型' },
  { key: 'attributionType', label: '来源声明' },
  { key: 'attributionNote', label: '来源说明' },
  { key: 'developerName', label: '开发者' },
  { key: 'systemRequirements', label: '系统要求' },
  { key: 'accessMode', label: '查看条件' },
  { key: 'minCommunityLevel', label: '最低社区等级' },
  { key: 'accessNote', label: '条件说明' },
]

function text(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}

/** 链接域名：网盘/官网取主机名，磁力按协议标识（审核必须可见域名与提取码变更） */
function linkDomain(url: string | null | undefined): string {
  if (!url) return '-'
  if (url.startsWith('magnet:')) return '磁力链接'
  try {
    return new URL(url).hostname
  } catch {
    return url.slice(0, 40)
  }
}

function flattenSources(snapshot: AdminRevisionSnapshot | null) {
  const map = new Map<
    string,
    {
      sourceKey: string
      sourceType: string
      platform: string | null
      label: string | null
      url: string
      extractionCode: string | null
      archivePassword: string | null
    }
  >()
  for (const release of snapshot?.releases ?? []) {
    for (const source of release.sources ?? []) {
      map.set(source.sourceKey, source)
    }
  }
  return map
}

function same(value1: unknown, value2: unknown): boolean {
  return JSON.stringify(value1 ?? null) === JSON.stringify(value2 ?? null)
}

/**
 * 发布审核（方案 10.2）：并列显示线上版与待审版，重点标出链接域名、提取码、
 * 来源声明、正文与截图变化；通过/退回绑定不可变提交记录（revisionId，
 * 后端 A07 校验防旧批次覆盖），原因 1~1000 字。
 */
export default function SoftwareReviews() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const checkActionRef = useRef<ActionType>(null)
  const [detail, setDetail] = useState<AdminReviewDetail | null>(null)
  const [approve, setApprove] = useState(true)
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  /** SW04：默认人工待审队列；切到 CHECKING 可查看自动检查中的提交 */
  const [reviewStatus, setReviewStatus] = useState<'PENDING' | 'CHECKING'>('PENDING')
  /** 检查任务运维筛选（空=全部） */
  const [checkStatus, setCheckStatus] = useState<string | undefined>(undefined)

  const isChecking = (record: PendingRow) =>
    record.reviewStatus === 'CHECKING'

  const retryCheck = async (taskId: string) => {
    try {
      await retryReviewCheckTask(taskId)
      message.success('已重新入队')
      checkActionRef.current?.reload()
      actionRef.current?.reload()
    } catch {
      // 状态变化已由全局提示
    }
  }

  const openReview = async (revisionId: string) => {
    try {
      const res = await getReviewDetail(revisionId)
      setDetail(res.data.data)
      setApprove(true)
      setReason('')
    } catch {
      message.error('加载待审快照失败')
    }
  }

  const submitDecision = async () => {
    if (!detail) return
    if (!reason.trim()) {
      message.warning('请填写审核说明')
      return
    }
    setSubmitting(true)
    try {
      await decideReview(detail.revisionId, {
        softwareId: detail.softwareId,
        approve,
        reason: reason.trim(),
      })
      message.success(approve ? '已通过并切换为当前线上版本' : '已退回')
      setDetail(null)
      actionRef.current?.reload()
    } catch {
      // 状态已变化/权限不足已由全局提示；保留弹窗供刷新后重试
    } finally {
      setSubmitting(false)
    }
  }

  /** SF08：审核领取（协作标记；本地记忆仅用于按钮切换，服务端是事实源） */
  const [claimedIds, setClaimedIds] = useState<Set<string>>(new Set())

  const handleClaim = (record: PendingRow) => {
    claimReview(record.revisionId)
      .then(() => {
        message.success('已领取，请及时处理（超时 1 小时未释放可被他人抢领）')
        setClaimedIds((prev) => new Set(prev).add(record.revisionId))
      })
      .catch(() => undefined)
  }

  const handleRelease = (record: PendingRow) => {
    releaseReview(record.revisionId)
      .then(() => {
        message.success('已释放')
        setClaimedIds((prev) => {
          const next = new Set(prev)
          next.delete(record.revisionId)
          return next
        })
      })
      .catch(() => undefined)
  }

  const openDetail = (record: PendingRow) => {
    // 详情复用审核弹窗（reviewDetail 接口对管理员可用，内容含完整预览）
    void openReview(record.revisionId)
  }

  const columns: ProColumns<PendingRow>[] = [
    { title: '软件ID', dataIndex: 'softwareId', width: 90, search: false },
    
    { title: '名称', dataIndex: 'name', width: 220, ellipsis: true, search: false },
    {
      title: '发布者',
      dataIndex: 'editorNickname',
      width: 110,
      search: false,
      render: (_, record) => record.editorNickname || `用户${record.editorId}`,
    },
    {
      title: '当前状态',
      dataIndex: 'reviewStatus',
      width: 110,
      search: false,
      render: (_, record) =>
        record.lifecycle === 'BANNED' ? (
          <Tag color="error">已封禁</Tag>
        ) : isChecking(record) ? (
          <Tag color="blue">自动检查中</Tag>
        ) : (
          <Tag color="processing">待审</Tag>
        ),
    },
    {
      title: '检查任务',
      dataIndex: 'checkTaskStatus',
      width: 180,
      search: false,
      render: (_, record) => {
        if (!isChecking(record)) return '-'
        if (!record.checkTaskStatus) return <Tag>任务缺失</Tag>
        const color = record.checkTaskStatus === 'DEAD_LETTER'
          ? 'error'
          : record.checkTaskStatus === 'RUNNING' ? 'blue' : 'default'
        return (
          <Space direction="vertical" size={0}>
            <Tag color={color}>
              {CHECK_TASK_STATUS_LABEL[record.checkTaskStatus] ?? record.checkTaskStatus}
              {record.checkAttempts != null ? ` · 第 ${record.checkAttempts + 1} 次` : ''}
            </Tag>
            {record.checkNextRetryAt ? (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                下次重试 {record.checkNextRetryAt}
              </Typography.Text>
            ) : null}
            {record.checkLastError ? (
              <Typography.Text type="danger" style={{ fontSize: 12 }} ellipsis>
                {record.checkLastError}
              </Typography.Text>
            ) : null}
          </Space>
        )
      },
    },
    {
      title: '等待时长',
      dataIndex: 'waitingSeconds',
      width: 110,
      search: false,
      render: (_, record) => {
        const seconds = record.waitingSeconds
        if (seconds === null || seconds === undefined) return '-'
        if (seconds < 60) return `${seconds} 秒`
        if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟`
        return `${Math.floor(seconds / 3600)} 小时`
      },
    },
    { title: '提交时间', dataIndex: 'submittedAt', valueType: 'dateTime', width: 160, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 130,
      render: (_, record) => [
        <Button
          key="detail"
          type="link"
          onClick={() => openDetail(record)}
        >
          详情
        </Button>,
        isChecking(record) ? (
          <Typography.Text key="waiting" type="secondary">
            等待自动检查
          </Typography.Text>
        ) : claimedIds.has(record.revisionId) ? (
          <Button key="release" type="link" onClick={() => handleRelease(record)}>
            释放
          </Button>
        ) : (
          <Button key="claim" type="link" onClick={() => handleClaim(record)}>
            领取
          </Button>
        ),
        isChecking(record) ? null : (
          <Button
            key="review"
            type="link"
            onClick={() => void openReview(record.revisionId)}
          >
            审核
          </Button>
        ),
      ].filter(Boolean),
    },
  ]

  const target = detail?.target ?? null
  const published = detail?.published ?? null
  const targetSources = flattenSources(target)
  const publishedSources = flattenSources(published)
  const sourceKeys = Array.from(new Set([...publishedSources.keys(), ...targetSources.keys()]))

  const screenshotStatus = new Map<string, 'NEW' | 'REMOVED' | 'SAME'>()
  for (const shot of published?.screenshots ?? []) screenshotStatus.set(shot.assetId, 'REMOVED')
  for (const shot of target?.screenshots ?? []) {
    screenshotStatus.set(shot.assetId, screenshotStatus.has(shot.assetId) ? 'SAME' : 'NEW')
  }

  return (
    <>
      <Tabs
        items={[
          {
            key: 'pending',
            label: reviewStatus === 'CHECKING' ? '自动检查中' : '待审队列',
            children: (
              <ProTable<PendingRow>
                headerTitle="发布审核"
                rowKey="revisionId"
                actionRef={actionRef}
                columns={columns}
                params={{ status: reviewStatus }}
                options={{ density: false }}
                search={false}
                toolbar={{
                  actions: [
                    <Radio.Group
                      key="status"
                      value={reviewStatus}
                      onChange={(e) => setReviewStatus(e.target.value)}
                      optionType="button"
                    >
                      <Radio.Button value="PENDING">待人工审核</Radio.Button>
                      <Radio.Button value="CHECKING">自动检查中</Radio.Button>
                    </Radio.Group>,
                  ],
                }}
                request={(params) =>
                  safeProTableRequest(() =>
                    listPendingReviews({
                      status: params.status as 'PENDING' | 'CHECKING',
                      page: params.current,
                      size: params.pageSize,
                    }),
                  )
                }
                pagination={{ defaultPageSize: 20 }}
              />
            ),
          },
          {
            key: 'checks',
            label: '自动检查任务',
            children: (
              <ProTable<AdminReviewCheckTask>
                headerTitle="自动检查任务"
                rowKey="taskId"
                actionRef={checkActionRef}
                options={{ density: false }}
                search={false}
                params={{ status: checkStatus }}
                columns={[
                  { title: '任务ID', dataIndex: 'taskId', width: 90 },
                  { title: '软件ID', dataIndex: 'softwareId', width: 90 },
                  {
                    title: '名称',
                    dataIndex: 'name',
                    ellipsis: true,
                    render: (_, record) => record.name || '-',
                  },
                  {
                    title: '任务状态',
                    dataIndex: 'status',
                    width: 120,
                    render: (_, record) => (
                      <Tag color={record.status === 'DEAD_LETTER' ? 'error'
                        : record.status === 'RUNNING' ? 'blue'
                          : record.status === 'DONE' ? 'success' : 'default'}>
                        {CHECK_TASK_STATUS_LABEL[record.status] ?? record.status}
                      </Tag>
                    ),
                  },
                  {
                    title: '提交状态',
                    dataIndex: 'reviewStatus',
                    width: 110,
                    render: (_, record) => record.reviewStatus ?? '-',
                  },
                  { title: '尝试', dataIndex: 'attempts', width: 70 },
                  {
                    title: '直接发布',
                    dataIndex: 'autoPublish',
                    width: 90,
                    render: (_, record) => (record.autoPublish ? <Tag color="gold">是</Tag> : '否'),
                  },
                  {
                    title: '下次重试',
                    dataIndex: 'nextRetryAt',
                    width: 170,
                    render: (_, record) => record.nextRetryAt ?? '-',
                  },
                  {
                    title: '最后错误',
                    dataIndex: 'lastError',
                    ellipsis: true,
                    render: (_, record) => record.lastError ?? '-',
                  },
                  {
                    title: '操作',
                    valueType: 'option',
                    width: 100,
                    render: (_, record) => [
                      ['PENDING', 'RUNNING', 'DEAD_LETTER'].includes(record.status) ? (
                        <Button
                          key="retry"
                          type="link"
                          onClick={() => void retryCheck(record.taskId)}
                        >
                          重试
                        </Button>
                      ) : (
                        <Typography.Text key="done" type="secondary">已结束</Typography.Text>
                      ),
                    ],
                  },
                ]}
                toolbar={{
                  actions: [
                    <Select
                      key="status"
                      allowClear
                      placeholder="全部状态"
                      style={{ width: 150 }}
                      value={checkStatus}
                      onChange={setCheckStatus}
                      options={[
                        { value: 'PENDING', label: '待执行' },
                        { value: 'RUNNING', label: '执行中' },
                        { value: 'DEAD_LETTER', label: '死信待处理' },
                        { value: 'DONE', label: '已完成' },
                        { value: 'REJECTED', label: '已拒绝' },
                        { value: 'CANCELLED', label: '已取消' },
                      ]}
                    />,
                  ],
                }}
                request={(params) =>
                  safeProTableRequest(() =>
                    listReviewCheckTasks({
                      status: params.status as string | undefined,
                      page: params.current,
                      size: params.pageSize,
                    }),
                  )
                }
                pagination={{ defaultPageSize: 20 }}
              />
            ),
          },
        ]}
      />

      <Drawer
        title="审核快照对比"
        width="min(1180px, 94vw)"
        open={!!detail}
        onClose={() => setDetail(null)}
        destroyOnHidden
        extra={
          <Space>
            <Button onClick={() => setDetail(null)}>关闭</Button>
            <Button type="primary" loading={submitting} onClick={submitDecision}>
              {approve ? '通过并上线' : '退回'}
            </Button>
          </Space>
        }
      >
        {detail && (
          <>
            <Space style={{ marginBottom: 12 }} wrap>
              <Tag>软件 {detail.softwareId}</Tag>
              <Tag>快照 {detail.revisionId}</Tag>
              <Tag color="blue">审核记录 {detail.revisionId}</Tag>
              <Typography.Text type="secondary">
                {published ? '已并列展示线上版（左）与待审版（右）' : '首次发布：无线上版可对比'}
              </Typography.Text>
            </Space>

            <Table
              size="small"
              pagination={false}
              rowKey="key"
              style={{ marginBottom: 16 }}
              dataSource={COMPARE_FIELDS.map((field) => ({
                key: field.key,
                label: field.label,
                published: text(published?.[field.key]),
                target: text(target?.[field.key]),
                changed: !same(published?.[field.key], target?.[field.key]),
              }))}
              columns={[
                { title: '字段', dataIndex: 'label', width: 140 },
                { title: '线上版', dataIndex: 'published' },
                { title: '待审版', dataIndex: 'target' },
                {
                  title: '',
                  dataIndex: 'changed',
                  width: 80,
                  render: (_, row) => (row.changed ? <Tag color="gold">有变化</Tag> : '-'),
                },
              ]}
              onRow={(row) => ({
                style: row.changed ? { background: '#fffbe6' } : undefined,
              })}
            />

            <Typography.Title level={5}>下载来源（域名 / 提取码变更）</Typography.Title>
            <Table
              size="small"
              pagination={false}
              rowKey="sourceKey"
              style={{ marginBottom: 16 }}
              dataSource={sourceKeys.map((sourceKey) => {
                const before = publishedSources.get(sourceKey)
                const after = targetSources.get(sourceKey)
                let status: string
                let color: string
                if (!before && after) {
                  status = '新增'
                  color = 'green'
                } else if (before && !after) {
                  status = '移除'
                  color = 'red'
                } else if (
                  before &&
                  after &&
                  (!same(before.url, after.url) ||
                    !same(before.extractionCode, after.extractionCode) ||
                    !same(before.archivePassword, after.archivePassword))
                ) {
                  status = '变更'
                  color = 'gold'
                } else {
                  status = '未变'
                  color = 'default'
                }
                const current = after ?? before
                return {
                  sourceKey,
                  status,
                  color,
                  domain: linkDomain(current?.url),
                  extractionCode: current?.extractionCode ?? '-',
                  platform: current?.platform ?? '-',
                }
              })}
              columns={[
                { title: '来源键', dataIndex: 'sourceKey', width: 200, ellipsis: true },
                { title: '平台', dataIndex: 'platform', width: 100 },
                { title: '链接域名', dataIndex: 'domain', width: 180 },
                { title: '提取码', dataIndex: 'extractionCode', width: 100 },
                {
                  title: '提交变化',
                  dataIndex: 'status',
                  width: 100,
                  render: (_, row) => <Tag color={row.color}>{row.status}</Tag>,
                },
              ]}
            />

            <Typography.Title level={5}>截图变化</Typography.Title>
            <div style={{ marginBottom: 16 }}>
              {[...screenshotStatus.entries()].length === 0 ? (
                <Typography.Text type="secondary">无截图</Typography.Text>
              ) : (
                [...screenshotStatus.entries()].map(([assetId, status]) => (
                  <Tag
                    key={assetId}
                    color={status === 'NEW' ? 'green' : status === 'REMOVED' ? 'red' : 'default'}
                    style={{ marginBottom: 4 }}
                  >
                    {assetId} · {status === 'NEW' ? '新增' : status === 'REMOVED' ? '移除' : '未变'}
                  </Tag>
                ))
              )}
            </div>

            <Typography.Title level={5}>
              正文
              {!same(published?.descriptionHtml, target?.descriptionHtml) && (
                <Tag color="gold" style={{ marginLeft: 8 }}>
                  有变化
                </Tag>
              )}
            </Typography.Title>
            <div
              style={{
                border: '1px solid #f0f0f0',
                borderRadius: 6,
                padding: 12,
                maxHeight: 320,
                overflow: 'auto',
                marginBottom: 16,
              }}
              // 待审正文已由服务端消毒；渲染前再走一次客户端白名单，双重防御
              dangerouslySetInnerHTML={{
                __html: DOMPurify.sanitize(target?.descriptionHtml ?? '<p>（无正文）</p>'),
              }}
            />

            <Typography.Title level={5}>审核决策</Typography.Title>
            <Radio.Group
              value={approve}
              onChange={(e) => setApprove(e.target.value)}
              style={{ marginBottom: 12 }}
            >
              <Radio.Button value={true}>通过</Radio.Button>
              <Radio.Button value={false}>退回</Radio.Button>
            </Radio.Group>
            {/* SF08：操作原因模板——只帮助填写，管理员仍可编辑为具体理由 */}
            <Select
              allowClear
              placeholder="套用原因模板（选填）"
              style={{ width: '100%', marginBottom: 8 }}
              onChange={(value) => {
                if (value) setReason(value)
              }}
              options={[
                { value: '功能与描述相符，来源核验通过，同意发布。', label: '通过：功能与描述相符' },
                { value: '来源链接可用且与声明一致，同意发布。', label: '通过：来源核验通过' },
                { value: '安装后无法正常运行，请核对系统要求与安装指南。', label: '退回：无法正常运行' },
                { value: '来源链接无法打开，请修复后重新提交。', label: '退回：来源无法打开' },
                { value: '描述与实际功能不符，请如实填写简介与说明。', label: '退回：描述不符' },
                { value: '缺少必要的来源声明或授权依据，请补充。', label: '退回：来源声明缺失' },
              ]}
            />
            <Input.TextArea
              rows={3}
              maxLength={1000}
              showCount
              placeholder="审核说明（1~1000 字，必填，会写入审计与作者通知）"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
            <Typography.Paragraph type="secondary" style={{ marginTop: 8 }}>
              通过要求作者当前仍有发布资格；退回后作者可修改并重新提交。
              审核结论绑定当前不可变快照 {detail.revisionId}。
            </Typography.Paragraph>
          </>
        )}
      </Drawer>
    </>
  )
}
