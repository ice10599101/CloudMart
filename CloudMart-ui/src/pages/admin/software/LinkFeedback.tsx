import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Input, Modal, Radio, Select, Tag, Typography } from 'antd'
import { getAdminSoftwareDetail, listLinkFeedbacks, markLinkFeedback } from '@/api/admin/software'
import type { AdminLinkFeedback } from '@/api/admin/software'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import SourceStateModal from '@/components/admin/SourceStateModal'

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  OPEN: { label: '待核实', color: 'orange' },
  VERIFYING: { label: '核实中', color: 'processing' },
  RESOLVED: { label: '已修复', color: 'success' },
  REJECTED: { label: '已驳回', color: 'default' },
}

const REASON_CODE_MAP: Record<string, string> = {
  LINK_INVALID: '链接失效',
  CODE_WRONG: '提取码错误',
  FILE_MISMATCH: '文件不符',
  SUSPICIOUS: '疑似风险',
  PLATFORM_MISMATCH: '平台不兼容（SF02）',
  INSTALL_FAILURE: '安装失败（SF02）',
  OTHER: '其他',
}

/**
 * 链接反馈（方案 10.4/5.3）：按来源指纹合并查看反馈人数，标记待核实/已修复/驳回；
 * 附带来源状态处置（核实/禁用/解除，software:link）。
 * "一键定位对应软件的当前来源"以 sourceKey 回填处置弹窗实现；不自动打开外链。
 */
export default function SoftwareLinkFeedback() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [markTarget, setMarkTarget] = useState<AdminLinkFeedback | null>(null)
  const [markStatus, setMarkStatus] = useState<'VERIFYING' | 'RESOLVED' | 'REJECTED'>('VERIFYING')
  const [resolvedRevisionId, setResolvedRevisionId] = useState<string | null>(null)
  /** SW07：结案只能引用本软件当前已发布的修复快照（后端强校验） */
  const [publishedRevision, setPublishedRevision] = useState<string | null>(null)
  const [markNote, setMarkNote] = useState('')

  const [sourceTarget, setSourceTarget] = useState<AdminLinkFeedback | null>(null)

  const [submitting, setSubmitting] = useState(false)

  const openMark = (record: AdminLinkFeedback) => {
    setMarkTarget(record)
    setMarkStatus(record.status === 'OPEN' ? 'VERIFYING' : 'RESOLVED')
    setResolvedRevisionId(null)
    setPublishedRevision(null)
    setMarkNote('')
    void getAdminSoftwareDetail(record.softwareId).then((res) => {
      if (res.data.success) {
        setPublishedRevision(res.data.data.publishedRevisionId)
      }
    })
  }

  const submitMark = async () => {
    if (!markTarget) return
    if (markStatus === 'RESOLVED' && publishedRevision == null) {
      message.warning('该软件当前没有已发布快照，无法标记已修复')
      return
    }
    if (markStatus === 'RESOLVED' && resolvedRevisionId == null) {
      message.warning('标记已修复必须选择修复上线的快照')
      return
    }
    setSubmitting(true)
    try {
      await markLinkFeedback(markTarget.feedbackId, {
        status: markStatus,
        resolvedRevisionId: markStatus === 'RESOLVED' ? resolvedRevisionId : null,
        note: markNote.trim() || undefined,
      })
      message.success('已标记')
      setMarkTarget(null)
      actionRef.current?.reload()
    } catch {
      // 业务错误已提示
    } finally {
      setSubmitting(false)
    }
  }

  const columns: ProColumns<AdminLinkFeedback>[] = [
    { title: '反馈ID', dataIndex: 'feedbackId', width: 90, search: false },
    { title: '软件ID', dataIndex: 'softwareId', width: 90, search: false },
    {
      title: '软件',
      dataIndex: 'softwareName',
      width: 160,
      ellipsis: true,
      search: false,
      render: (_, record) => record.softwareName || '-',
    },
    { title: '来源键', dataIndex: 'sourceKey', width: 190, ellipsis: true, search: false },
    { title: '反馈人', dataIndex: 'reporterId', width: 100, search: false },
    {
      title: '反馈类型',
      dataIndex: 'reasonCode',
      width: 110,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(REASON_CODE_MAP).map(([value, label]) => [value, { text: label }]),
      ),
      render: (_, record) => REASON_CODE_MAP[record.reasonCode] ?? record.reasonCode,
    },
    { title: '补充说明', dataIndex: 'description', ellipsis: true, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(STATUS_MAP).map(([value, info]) => [value, { text: info.label }]),
      ),
      render: (_, record) => {
        const info = STATUS_MAP[record.status] ?? { label: record.status, color: 'default' }
        return <Tag color={info.color}>{info.label}</Tag>
      },
    },
    {
      title: '修复快照',
      dataIndex: 'resolvedRevisionId',
      width: 100,
      search: false,
      render: (_, record) => record.resolvedRevisionId ?? '-',
    },
    { title: '时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 160, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 170,
      render: (_, record) => [
        <a
          key="mark"
          onClick={() => openMark(record)}
        >
          标记
        </a>,
        <a
          key="source"
          onClick={() => setSourceTarget(record)}
        >
          来源处置
        </a>,
      ],
    },
  ]

  return (
    <>
      <ProTable<AdminLinkFeedback>
        headerTitle="链接反馈"
        rowKey="feedbackId"
        actionRef={actionRef}
        columns={columns}
        options={{ density: false }}
        scroll={{ x: 1300 }}
        request={(params) =>
          safeProTableRequest(() =>
            listLinkFeedbacks({
              status: (params as Record<string, unknown>).status as string | undefined,
              reasonCode: (params as Record<string, unknown>).reasonCode as string | undefined,
              page: params.current,
              size: params.pageSize,
            }),
          )
        }
        pagination={{ defaultPageSize: 20 }}
      />

      <Modal
        title="标记反馈"
        open={!!markTarget}
        onCancel={() => setMarkTarget(null)}
        onOk={submitMark}
        confirmLoading={submitting}
        okText="确认"
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          反馈 {markTarget?.feedbackId}（软件 {markTarget?.softwareId}，来源键{' '}
          {markTarget?.sourceKey}）。"已修复"只能引用本软件当前已发布的修复快照，且需满足修复事实：
          来源已随快照移除，或管理员已核验 AVAILABLE（指纹=当前内容）。原始指纹会永久保留。
        </Typography.Paragraph>
        <Radio.Group
          value={markStatus}
          onChange={(e) => setMarkStatus(e.target.value)}
          style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 12 }}
        >
          <Radio value="VERIFYING">标记核实中</Radio>
          <Radio value="RESOLVED" disabled={publishedRevision == null}>
            标记已修复（需选择修复快照）
          </Radio>
          <Radio value="REJECTED">驳回反馈</Radio>
        </Radio.Group>
        {markStatus === 'RESOLVED' && (
          <Select
            placeholder={publishedRevision ? '选择修复快照' : '该软件当前没有已发布快照'}
            value={resolvedRevisionId}
            onChange={(value) => setResolvedRevisionId(value)}
            style={{ width: '100%', marginBottom: 12 }}
            options={
              publishedRevision
                ? [{ value: publishedRevision, label: `已发布快照 ${publishedRevision}` }]
                : []
            }
          />
        )}
        <Input.TextArea
          rows={2}
          maxLength={500}
          placeholder="备注（可空）"
          value={markNote}
          onChange={(e) => setMarkNote(e.target.value)}
        />
      </Modal>

      <SourceStateModal
        open={!!sourceTarget}
        softwareId={sourceTarget?.softwareId ?? ''}
        sourceKey={sourceTarget?.sourceKey ?? ''}
        contextLabel={sourceTarget ? `反馈 ${sourceTarget.feedbackId}` : undefined}
        onClose={() => setSourceTarget(null)}
        onSuccess={() => actionRef.current?.reload()}
      />
    </>
  )
}
