import { Button, Card, Modal, Tag, Timeline, Typography } from 'antd'
import DOMPurify from 'dompurify'
import type { AdminSoftwareDetail } from '@/api/admin/software'

const REVIEW_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已退回',
}

const TRAIL_LABEL: Record<string, string> = {
  CREATE: '创建软件',
  SAVE_DRAFT: '保存了草稿',
  SUBMIT: '提交审核',
  APPROVE: '审核通过',
  REJECT: '审核退回',
  WITHDRAW: '撤回待审',
  CLONE_WORKING: '创建编辑副本',
  UNLIST: '下架',
  RELIST: '重新上架',
  BAN: '封禁',
  UNBAN: '解除封禁',
  DELETE: '删除软件',
  FEATURE: '设为精选',
}

const TRAIL_COLOR: Record<string, string> = {
  CREATE: 'gray',
  SAVE_DRAFT: 'gray',
  SUBMIT: 'blue',
  APPROVE: 'green',
  REJECT: 'red',
  WITHDRAW: 'orange',
  CLONE_WORKING: 'blue',
  BAN: 'red',
  DELETE: 'red',
}

const PLATFORM_LABEL: Record<string, string> = {
  WINDOWS: 'Windows',
  MACOS: 'macOS',
  LINUX: 'Linux',
  ANDROID: 'Android',
  IOS: 'iOS',
  CROSS_PLATFORM: '跨平台',
}

/** 处置轨迹 reason 人话化：剥离 contentHash 等技术参数，仅保留可读原因 */
const trailReason = (action: string, reason: string) => {
  if (reason.includes('contentHash=')) {
    const approved = /approved=true/.test(reason)
    if (action === 'APPROVE') return approved ? '审核结论：通过' : '审核完成'
    if (action === 'REJECT') return '审核结论：退回'
    return ''
  }
  return `原因：${reason}`
}

/** 时间去技术格式：2026-10-17T13:11:11.586 → 2026-10-17 13:11 */
const formatTrailTime = (value: string) =>
  (value || '').replace('T', ' ').replace(/.\d+$/, '').slice(0, 16)

/**
 * 后台软件详情弹窗（帖子预览 + 人话化处置轨迹）。
 * Items / Reviews 两处共用；数据来自 admin-detail 接口。
 */
export default function SoftwareDetailModal({
  detail,
  open,
  onClose,
}: {
  detail: AdminSoftwareDetail | null
  open: boolean
  onClose: () => void
}) {
  return (
    <Modal
      title={`软件详情${detail?.name ? ` · ${detail.name}` : ''}`}
      open={open}
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>
          关闭
        </Button>,
      ]}
      width={720}
      centered
      destroyOnHidden
    >
      {detail && (
        <>
          <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 12 }}>
            {detail.iconAssetId ? (
              <img
                src={`/files/by-id/${detail.iconAssetId}`}
                alt="软件图标"
                style={{ width: 56, height: 56, borderRadius: 12, objectFit: 'cover' }}
              />
            ) : null}
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                <Typography.Text strong style={{ fontSize: 16 }}>
                  {detail.name || '(未命名)'}
                </Typography.Text>
                <Tag color={detail.reviewStatus === 'APPROVED' ? 'success' : 'processing'}>
                  {REVIEW_LABEL[detail.reviewStatus ?? ''] ?? detail.reviewStatus ?? '-'}
                </Tag>
                {detail.lifecycle === 'BANNED' ? <Tag color="error">已封禁</Tag> : null}
              </div>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                发布者 {detail.editorNickname || `用户${detail.editorId ?? '?'}`} · 提交批次{' '}
                {detail.submitSeq ?? '-'}
              </Typography.Text>
            </div>
          </div>
          {detail.summary ? (
            <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
              {detail.summary}
            </Typography.Paragraph>
          ) : null}
          {detail.descriptionHtml ? (
            <div
              style={{ lineHeight: 1.7, wordBreak: 'break-word' }}
              dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(detail.descriptionHtml) }}
            />
          ) : (
            <Typography.Text type="secondary">（介绍为空）</Typography.Text>
          )}
          {(detail.releases ?? []).length > 0 ? (
            <>
              <Typography.Title level={5} style={{ marginTop: 16 }}>
                版本与下载来源
              </Typography.Title>
              {detail.releases!.map((release, rIndex) => (
                <Card key={rIndex} size="small" style={{ marginBottom: 8 }}>
                  <Typography.Text strong>
                    {release.versionName}
                    {release.recommended ? '（推荐）' : ''}
                  </Typography.Text>
                  {release.changelog ? (
                    <Typography.Paragraph
                      type="secondary"
                      style={{ whiteSpace: 'pre-wrap', marginBottom: 4, marginTop: 4 }}
                    >
                      {release.changelog}
                    </Typography.Paragraph>
                  ) : null}
                  {(release.sources ?? []).map((source, sIndex) => (
                    <div
                      key={sIndex}
                      style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center', padding: '2px 0' }}
                    >
                      <Tag>{source.label}</Tag>
                      <Tag>{PLATFORM_LABEL[source.platform] ?? source.platform}</Tag>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {source.url}
                      </Typography.Text>
                      {source.extractionCode ? (
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          提取码 {source.extractionCode}
                        </Typography.Text>
                      ) : null}
                    </div>
                  ))}
                </Card>
              ))}
            </>
          ) : null}
          <Typography.Title level={5} style={{ marginTop: 16 }}>
            处置轨迹
          </Typography.Title>
          {(detail.auditTrail ?? []).length === 0 ? (
            <Typography.Text type="secondary">暂无记录</Typography.Text>
          ) : (
            <Timeline
              items={(detail.auditTrail ?? []).map((entry) => ({
                color: TRAIL_COLOR[entry.action] ?? 'gray',
                children: (
                  <div style={{ paddingBottom: 4 }}>
                    <Typography.Text>{TRAIL_LABEL[entry.action] ?? entry.action}</Typography.Text>
                    <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                      {formatTrailTime(entry.createdAt)}
                    </Typography.Text>
                    {entry.reason ? (
                      <div>
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          {trailReason(entry.action, entry.reason)}
                        </Typography.Text>
                      </div>
                    ) : null}
                  </div>
                ),
              }))}
            />
          )}
        </>
      )}
    </Modal>
  )
}
