import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Drawer, Input, Radio, Space, Table, Tag, Typography } from 'antd'
import DOMPurify from 'dompurify'
import {
  decideReview,
  getReviewDetail,
  listPendingReviews,
} from '@/api/admin/software'
import type { AdminReviewDetail, AdminRevisionSnapshot } from '@/api/admin/software'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

interface PendingRow {
  softwareId: string
  revisionId: string
  revisionNo: number
  submitSeq: number
  name: string | null
  summary: string | null
  editorId: string
  editorNickname?: string
  submittedAt: string
  lifecycle: string
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
 * 来源声明、正文与截图变化；通过/退回必须绑定提交批次（submitSeq，
 * 后端 A07 校验防旧批次覆盖），原因 1~1000 字。
 */
export default function SoftwareReviews() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [detail, setDetail] = useState<AdminReviewDetail | null>(null)
  const [approve, setApprove] = useState(true)
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

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
        submitSeq: detail.submitSeq,
        expectedVersion: detail.softwareLockVersion,
        approve,
        reason: reason.trim(),
      })
      message.success(approve ? '已通过并切换为当前线上版本' : '已退回')
      setDetail(null)
      actionRef.current?.reload()
    } catch {
      // 批次过期/版本冲突已由全局提示；保留弹窗供刷新后重试
    } finally {
      setSubmitting(false)
    }
  }

  const openDetail = (record: PendingRow) => {
    // 详情复用审核弹窗（reviewDetail 接口对管理员可用，内容含完整预览）
    void openReview(record.revisionId)
  }

  const columns: ProColumns<PendingRow>[] = [
    { title: '软件ID', dataIndex: 'softwareId', width: 90, search: false },
    
    {
      title: '名称',
      dataIndex: 'name',
      width: 220,
      ellipsis: true,
      search: false,
      render: (_, record) => (
        <Button
          type="link"
          style={{ padding: 0 }}
          onClick={() => openDetail(record)}
        >
          {record.name || '(未命名)'}
        </Button>
      ),
    },
    { title: '提交批次', dataIndex: 'submitSeq', width: 90, search: false },
    {
      title: '发布者',
      dataIndex: 'editorNickname',
      width: 110,
      search: false,
      render: (_, record) => record.editorNickname || `用户${record.editorId}`,
    },
    {
      title: '当前状态',
      dataIndex: 'lifecycle',
      width: 100,
      search: false,
      render: (_, record) =>
        record.lifecycle === 'BANNED' ? (
          <Tag color="error">已封禁</Tag>
        ) : (
          <Tag color="processing">待审</Tag>
        ),
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
        <Button
          key="review"
          type="link"
          onClick={() => void openReview(record.revisionId)}
        >
          审核
        </Button>,
      ],
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
      <ProTable<PendingRow>
        headerTitle="发布审核"
        rowKey="revisionId"
        actionRef={actionRef}
        columns={columns}
        options={{ density: false }}
        search={false}
        request={(params) =>
          safeProTableRequest(() =>
            listPendingReviews({ page: params.current, size: params.pageSize }),
          )
        }
        pagination={{ defaultPageSize: 20 }}
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
              <Tag color="blue">提交批次 {detail.submitSeq}</Tag>
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
            <Input.TextArea
              rows={3}
              maxLength={1000}
              showCount
              placeholder="审核说明（1~1000 字，必填，会写入审计与作者通知）"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
            <Typography.Paragraph type="secondary" style={{ marginTop: 8 }}>
              通过要求作者当前仍有发布资格；退回后作者可修改并重新提交。提交批次
              #{detail.submitSeq}（防旧批次覆盖）。
            </Typography.Paragraph>
          </>
        )}
      </Drawer>
    </>
  )
}
