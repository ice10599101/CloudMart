import { useRef, useState } from 'react'
import { Button, Card, Popconfirm, Segmented, Space, Tag } from 'antd'
import type { ProColumns } from '@ant-design/pro-components'
import { ActionType, ProTable } from '@ant-design/pro-components'
import { listOperationsTasks, retryOperationsTask, type OperationsTask } from '@/api/admin/operations'
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
                    if (res.data.success) {
                      message.success(res.data.data?.accepted ? '已受理，将按退避重新投递' : '已被其他操作员受理')
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

  return (
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
  )
}
