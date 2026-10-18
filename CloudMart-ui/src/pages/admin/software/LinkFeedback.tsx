import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Input, InputNumber, Modal, Radio, Space, Tag, Typography } from 'antd'
import { listLinkFeedbacks, markLinkFeedback } from '@/api/admin/software'
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
  const [resolvedRevisionId, setResolvedRevisionId] = useState<number | null>(null)
  const [markNote, setMarkNote] = useState('')

  const [sourceTarget, setSourceTarget] = useState<AdminLinkFeedback | null>(null)

  const [submitting, setSubmitting] = useState(false)

  const submitMark = async () => {
    if (!markTarget) return
    if (markStatus === 'RESOLVED' && resolvedRevisionId == null) {
      message.warning('标记已修复必须填写修复上线的快照ID')
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
      search: false,
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
          onClick={() => {
            setMarkTarget(record)
            setMarkStatus(record.status === 'OPEN' ? 'VERIFYING' : 'RESOLVED')
            setResolvedRevisionId(null)
            setMarkNote('')
          }}
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
          {markTarget?.sourceKey}）。"已修复"必须绑定修复上线的新快照，用于保留原始指纹与修复记录。
        </Typography.Paragraph>
        <Radio.Group
          value={markStatus}
          onChange={(e) => setMarkStatus(e.target.value)}
          style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 12 }}
        >
          <Radio value="VERIFYING">标记核实中</Radio>
          <Radio value="RESOLVED">标记已修复（需修复快照ID）</Radio>
          <Radio value="REJECTED">驳回反馈</Radio>
        </Radio.Group>
        {markStatus === 'RESOLVED' && (
          <InputNumber
            placeholder="修复上线的快照ID（revisionId）"
            value={resolvedRevisionId}
            onChange={(value) => setResolvedRevisionId(value)}
            style={{ width: '100%', marginBottom: 12 }}
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
