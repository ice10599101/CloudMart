import { useRef, useState } from 'react'
import { Button, Card, Popconfirm, Segmented, Space, Tabs, Tag } from 'antd'
import type { ProColumns } from '@ant-design/pro-components'
import { ActionType, ProTable } from '@ant-design/pro-components'
import {
  listOperationsTasks,
  retryOperationsTask,
  listDeletionTasks,
  retryDeletionTask,
  listExportTasks,
  retryExportTask,
  type OperationsTask,
  type DeletionTask,
  type ExportTask,
} from '@/api/admin/operations'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

type ServiceDomain = 'mall-order' | 'mall-wish'

const STATUS_LABELS: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待投递', color: 'blue' },
  SENDING: { text: '投递中', color: 'geekblue' },
  FAILED: { text: '失败重试中', color: 'orange' },
  DEAD: { text: '死信', color: 'red' },
  DEAD_LETTER: { text: '死信', color: 'red' },
}

export default function Operations() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [service, setService] = useState<ServiceDomain>('mall-order')
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined)

  const columns: ProColumns<OperationsTask>[] = [
    { title: '事件ID', dataIndex: 'eventId', width: 280, copyable: true, ellipsis: true },
    { title: '事件类型', dataIndex: 'eventType', width: 180 },
    { title: '聚合', dataIndex: 'aggregateId', width: 180 },
    { title: 'requestId', dataIndex: 'requestId', width: 200, ellipsis: true, copyable: true },
    { title: '下次重试', dataIndex: 'nextRetryAt', width: 170, search: false,
      render: (_, r) => (r.nextRetryAt ?? '').slice(0, 19).replace('T', ' ') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const meta = STATUS_LABELS[record.status] ?? { text: record.status, color: 'default' }
        return <Tag color={meta.color}>{meta.text}</Tag>
      },
    },
    { title: '已尝试', dataIndex: 'attempts', width: 70, search: false },
    { title: '最近错误', dataIndex: 'lastError', ellipsis: true, search: false },
    { title: '更新时间', dataIndex: 'updatedAt', width: 170, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      fixed: 'right',
      render: (_, record) =>
        record.status === 'DEAD' || record.status === 'DEAD_LETTER'
          ? [
              <Popconfirm
                key="retry"
                title="确认重试该死信？原业务键不变，受理≠成功"
                onConfirm={async () => {
                  const reason = window.prompt('重试原因（必填，随审计留痕）')
                  if (!reason?.trim()) {
                    message.warning('重试原因必填')
                    return
                  }
                  try {
                    const res = await retryOperationsTask(service, record.eventId, reason.trim())
                    if (res.data.success && res.data.data) {
                      const { accepted, status } = res.data.data
                      message.success(
                        accepted
                          ? `已受理（当前状态：${status ?? 'PENDING'}），将按退避重新投递`
                          : `未被受理（任务当前状态：${status ?? 'UNKNOWN'}，可能已被处理）`,
                      )
                      actionRef.current?.reload()
                    }
                  } catch {
                    message.error('重试未受理（服务不可用）')
                  }
                }}
              >
                <Button type="link" size="small">重试</Button>
              </Popconfirm>,
            ]
          : [],
    },
  ]

  // OPS-03：注销任务处置（定位→重试失败步骤，不跳过资金检查）
  const deletionColumns: ProColumns<DeletionTask>[] = [
    { title: '任务 ID', dataIndex: 'id', width: 90 },
    { title: '用户 ID', dataIndex: 'userId', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (_, r) => <Tag>{r.status}</Tag>,
    },
    { title: '服务进度', dataIndex: 'serviceProgress', ellipsis: true },
    { title: '阻断原因', dataIndex: 'blockReason', ellipsis: true },
    {
      title: '申请时间',
      dataIndex: 'requestedAt',
      width: 170,
      render: (_, r) => (r.requestedAt ?? '').slice(0, 19).replace('T', ' '),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      fixed: 'right',
      render: (_, r) => [
        <Popconfirm
          key="retry"
          title="重试失败步骤？（不跳过资金检查）"
          onConfirm={async () => {
            const reason = window.prompt('重试原因（必填，随审计留痕）')
            if (!reason?.trim()) {
              message.warning('重试原因必填')
              return
            }
            try {
              await retryDeletionTask(r.id, reason.trim())
              message.success('已受理重试')
            } catch {
              message.error('重试未受理（服务不可用）')
            }
          }}
        >
          <Button type="link" size="small">重试</Button>
        </Popconfirm>,
      ],
    },
  ]

  // OPS-03：导出任务处置（FAILED→PENDING 重排，由持续 worker 认领）
  const exportColumns: ProColumns<ExportTask>[] = [
    { title: '任务 ID', dataIndex: 'id', width: 90 },
    { title: '用户 ID', dataIndex: 'userId', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (_, r) => <Tag>{r.status}</Tag>,
    },
    { title: '失败原因', dataIndex: 'failReason', ellipsis: true },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (_, r) => (r.createdAt ?? '').slice(0, 19).replace('T', ' '),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      fixed: 'right',
      render: (_, r) =>
        r.status === 'FAILED'
          ? [
              <Popconfirm
                key="retry"
                title="重置为 PENDING 重新排队？（不跳过校验）"
                onConfirm={async () => {
                  const reason = window.prompt('重试原因（必填，随审计留痕）')
                  if (!reason?.trim()) {
                    message.warning('重试原因必填')
                    return
                  }
                  try {
                    await retryExportTask(r.id, reason.trim())
                    message.success('已重新排队')
                  } catch {
                    message.error('重试未受理（服务不可用）')
                  }
                }}
              >
                <Button type="link" size="small">重试</Button>
              </Popconfirm>,
            ]
          : [],
    },
  ]

  return (
    <Tabs
      items={[
        {
          key: 'outbox',
          label: 'Outbox 失败任务',
          children: (
            <Card
              title={
                <Space>
                  <span>异常处理中心</span>
                  <Segmented
                    value={service}
                    onChange={(v) => setService(v as ServiceDomain)}
                    options={[
                      { label: '订单域', value: 'mall-order' },
                      { label: '心愿域', value: 'mall-wish' },
                    ]}
                  />
                  <Segmented
                    value={statusFilter ?? 'ALL'}
                    onChange={(v) => setStatusFilter(v === 'ALL' ? undefined : (v as string))}
                    options={[
                      { label: '全部', value: 'ALL' },
                      { label: '死信', value: 'DEAD' },
                      { label: '失败重试中', value: 'FAILED' },
                      { label: '待投递', value: 'PENDING' },
                    ]}
                  />
                </Space>
              }
            >
              <ProTable<OperationsTask>
                headerTitle={false}
                actionRef={actionRef}
                rowKey="eventId"
                search={false}
                polling={30_000}
                request={async (params) =>
                  safeProTableRequest<OperationsTask>(() =>
                    listOperationsTasks(service, {
                      page: params.current,
                      pageSize: params.pageSize,
                      status: statusFilter,
                    }),
                  )
                }
                columns={columns}
                pagination={{ pageSize: 20 }}
              />
            </Card>
          ),
        },
        {
          key: 'deletion',
          label: '账号注销任务',
          children: (
            <Card title="账号注销任务（OPS-03：定位失败步骤并受控重试，不跳过资金检查）">
              <ProTable<DeletionTask>
                rowKey="id"
                search={false}
                polling={60_000}
                request={async (params) =>
                  safeProTableRequest<DeletionTask>(() =>
                    listDeletionTasks({ page: params.current, pageSize: params.pageSize }),
                  )
                }
                columns={deletionColumns}
                pagination={{ pageSize: 20 }}
              />
            </Card>
          ),
        },
        {
          key: 'exports',
          label: '心愿数据导出',
          children: (
            <Card title="心愿数据导出任务（OPS-03：FAILED 可受控重试，由持续 worker 认领执行）">
              <ProTable<ExportTask>
                rowKey="id"
                search={false}
                polling={60_000}
                request={async (params) =>
                  safeProTableRequest<ExportTask>(() =>
                    listExportTasks({ page: params.current, pageSize: params.pageSize }),
                  )
                }
                columns={exportColumns}
                pagination={{ pageSize: 20 }}
              />
            </Card>
          ),
        },
      ]}
    />
  )
}
