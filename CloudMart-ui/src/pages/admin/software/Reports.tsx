import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Input, Modal, Radio, Space, Tag, Typography } from 'antd'
import { handleSoftwareReport, listSoftwareReports } from '@/api/admin/software'
import type { AdminSoftwareReport } from '@/api/admin/software'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  OPEN: { label: '待处理', color: 'orange' },
  VERIFYING: { label: '核实中', color: 'processing' },
  RESOLVED: { label: '已成立', color: 'success' },
  REJECTED: { label: '已驳回', color: 'default' },
}

const TARGET_TYPE_MAP: Record<string, string> = {
  SOFTWARE: '软件',
  SOFTWARE_COMMENT: '评论',
}

/**
 * 举报管理（方案 10.11）：侵权、恶意内容和违规评论按 software_report 处理；
 * 处置动作显式选择（NONE/BAN_SOFTWARE/HIDE_COMMENT），不隐式封禁；
 * 处置事务在软件库本地完成（不远程调用社区）。权限 software:report。
 */
export default function SoftwareReports() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [target, setTarget] = useState<AdminSoftwareReport | null>(null)
  const [status, setStatus] = useState<'RESOLVED' | 'REJECTED'>('RESOLVED')
  const [action, setAction] = useState<'NONE' | 'BAN_SOFTWARE' | 'HIDE_COMMENT'>('NONE')
  const [note, setNote] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submit = async () => {
    if (!target) return
    if (!note.trim()) {
      message.warning('请填写处理说明')
      return
    }
    setSubmitting(true)
    try {
      await handleSoftwareReport(target.reportId, {
        status,
        action: status === 'RESOLVED' ? action : 'NONE',
        note: note.trim(),
      })
      message.success(status === 'RESOLVED' ? '举报已成立并完成处置' : '已驳回')
      setTarget(null)
      actionRef.current?.reload()
    } catch {
      // 幂等/状态冲突等已由全局提示
    } finally {
      setSubmitting(false)
    }
  }

  const columns: ProColumns<AdminSoftwareReport>[] = [
    { title: '举报ID', dataIndex: 'reportId', width: 90, search: false },
    { title: '举报人', dataIndex: 'reporterId', width: 100, search: false },
    { title: '软件ID', dataIndex: 'softwareId', width: 90, search: false },
    {
      title: '目标',
      dataIndex: 'targetType',
      width: 140,
      search: false,
      render: (_, record) => (
        <Space size={4}>
          <Tag>{TARGET_TYPE_MAP[record.targetType] ?? record.targetType}</Tag>
          {record.targetId && <Typography.Text type="secondary">#{record.targetId}</Typography.Text>}
        </Space>
      ),
    },
    { title: '理由', dataIndex: 'reason', width: 180, ellipsis: true, search: false },
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
    { title: '处理说明', dataIndex: 'handleNote', width: 160, ellipsis: true, search: false },
    { title: '时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 160, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 90,
      render: (_, record) =>
        record.status === 'OPEN' || record.status === 'VERIFYING'
          ? [
              <a
                key="handle"
                onClick={() => {
                  setTarget(record)
                  setStatus('RESOLVED')
                  setAction('NONE')
                  setNote('')
                }}
              >
                处置
              </a>,
            ]
          : [],
    },
  ]

  return (
    <>
      <ProTable<AdminSoftwareReport>
        headerTitle="举报管理"
        rowKey="reportId"
        actionRef={actionRef}
        columns={columns}
        options={{ density: false }}
        scroll={{ x: 1300 }}
        request={(params) =>
          safeProTableRequest(() =>
            listSoftwareReports({
              status: (params as Record<string, unknown>).status as string | undefined,
              page: params.current,
              size: params.pageSize,
            }),
          )
        }
        pagination={{ defaultPageSize: 20 }}
      />

      <Modal
        title="举报处置"
        open={!!target}
        onCancel={() => setTarget(null)}
        onOk={submit}
        confirmLoading={submitting}
        okText="确认"
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          举报 {target?.reportId}（{target ? TARGET_TYPE_MAP[target.targetType] : ''}；
          软件 {target?.softwareId}
          {target?.targetId ? `，目标评论 ${target.targetId}` : ''}）。仅标记成立不会自动处置，
          需显式选择动作。
        </Typography.Paragraph>
        <Radio.Group
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          style={{ marginBottom: 12 }}
        >
          <Radio.Button value="RESOLVED">举报成立</Radio.Button>
          <Radio.Button value="REJECTED">驳回</Radio.Button>
        </Radio.Group>
        {status === 'RESOLVED' && (
          <Radio.Group
            value={action}
            onChange={(e) => setAction(e.target.value)}
            style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 12 }}
          >
            <Radio value="NONE">仅标记成立（不做处置）</Radio>
            <Radio value="BAN_SOFTWARE" disabled={target?.targetType !== 'SOFTWARE'}>
              封禁软件（作者不可自行恢复）
            </Radio>
            <Radio value="HIDE_COMMENT" disabled={target?.targetType !== 'SOFTWARE_COMMENT'}>
              隐藏评论
            </Radio>
          </Radio.Group>
        )}
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          placeholder="处理说明（1~500 字，必填，会写入审计）"
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
      </Modal>
    </>
  )
}
