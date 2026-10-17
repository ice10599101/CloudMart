import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Input, Modal, Radio, Space, Tag, Typography } from 'antd'
import {
  listAdminSoftwareComments,
  moderateAdminSoftwareComment,
} from '@/api/admin/software'
import type { AdminSoftwareComment } from '@/api/admin/software'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待审', color: 'processing' },
  VISIBLE: { label: '可见', color: 'success' },
  HIDDEN: { label: '已隐藏', color: 'default' },
  DELETED: { label: '已删除', color: 'error' },
}

const ACTION_OPTIONS = [
  { value: 'APPROVE', label: '通过（待审→可见，计数+1）' },
  { value: 'REJECT', label: '拒绝（待审→隐藏）' },
  { value: 'HIDE', label: '隐藏（可见→隐藏，计数-1）' },
  { value: 'RESTORE', label: '恢复（隐藏→可见，计数+1）' },
]

/**
 * 评论管理（方案 10.3）：待审/可见/隐藏；按软件定位；审核/隐藏必须写明理由并留痕；
 * HIDDEN/DELETED 不回传原文（与前台一致，防旁路泄漏）。
 */
export default function SoftwareComments() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [target, setTarget] = useState<AdminSoftwareComment | null>(null)
  const [softwareId, setSoftwareId] = useState<string>('')
  const [action, setAction] = useState('HIDE')
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submitModerate = async () => {
    if (!target) return
    if (!reason.trim()) {
      message.warning('请填写处置原因')
      return
    }
    setSubmitting(true)
    try {
      await moderateAdminSoftwareComment(target.softwareId, target.commentId, {
        action: action as 'APPROVE' | 'HIDE' | 'RESTORE' | 'REJECT',
        reason: reason.trim(),
      })
      message.success('已处置')
      setTarget(null)
      actionRef.current?.reload()
    } catch {
      // 状态机冲突等已由全局提示；保留弹窗供核对
    } finally {
      setSubmitting(false)
    }
  }

  const columns: ProColumns<AdminSoftwareComment>[] = [
    { title: '评论ID', dataIndex: 'commentId', width: 90, search: false },
    { title: '用户ID', dataIndex: 'userId', width: 100, search: false },
    {
      title: '用户',
      dataIndex: 'userNickname',
      width: 110,
      search: false,
      render: (_, record) => record.userNickname || '-',
    },
    { title: '软件ID', dataIndex: 'softwareId', width: 90, search: false },
    {
      title: '软件',
      dataIndex: 'softwareName',
      width: 160,
      ellipsis: true,
      search: false,
      render: (_, record) => record.softwareName || '-',
    },
    {
      title: '内容',
      dataIndex: 'content',
      ellipsis: true,
      search: false,
      render: (_, record) =>
        record.content ?? <Typography.Text type="secondary">（不回传原文）</Typography.Text>,
    },
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
    { title: '点赞', dataIndex: 'likeCount', width: 70, search: false },
    { title: '时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 160, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 90,
      render: (_, record) => [
        <a
          key="moderate"
          onClick={() => {
            setTarget(record)
            setAction(record.status === 'PENDING' ? 'APPROVE' : record.status === 'VISIBLE' ? 'HIDE' : 'RESTORE')
            setReason('')
          }}
        >
          处置
        </a>,
      ],
    },
  ]

  return (
    <>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="按软件ID筛选（留空查看全部）"
          allowClear
          enterButton="筛选"
          style={{ width: 320 }}
          onSearch={(value) => {
            setSoftwareId(value.trim())
            actionRef.current?.reload()
          }}
        />
        <Typography.Text type="secondary">
          默认展示全部软件的评论；可按软件ID筛选
        </Typography.Text>
      </Space>
      <ProTable<AdminSoftwareComment>
        headerTitle="评论管理"
        rowKey="commentId"
        actionRef={actionRef}
        columns={columns}
        options={{ density: false }}
        search={{ defaultCollapsed: false }}
        params={{ softwareId }}
        request={(params) =>
          safeProTableRequest(() =>
            listAdminSoftwareComments(softwareId || undefined, {
              status: (params as Record<string, unknown>).status as string | undefined,
              page: params.current,
              size: params.pageSize,
            }),
          )
        }
        pagination={{ defaultPageSize: 20 }}
      />

      <Modal
        title="评论处置"
        open={!!target}
        onCancel={() => setTarget(null)}
        onOk={submitModerate}
        confirmLoading={submitting}
        okText="确认处置"
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          评论 {target?.commentId}（当前状态：
          {target ? (STATUS_MAP[target.status]?.label ?? target.status) : '-'}）；
          处置类型与原因会写入审计。
        </Typography.Paragraph>
        <Radio.Group
          value={action}
          onChange={(e) => setAction(e.target.value)}
          options={ACTION_OPTIONS}
          style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 12 }}
        />
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          placeholder="处置原因（1~500 字，必填）"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </Modal>
    </>
  )
}
