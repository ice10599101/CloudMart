import { useRef } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Tag, Tooltip } from 'antd'
import {
  getAdminWishInteractions,
  INTERACTION_TYPE_MAP,
} from '@/api/admin/wish'
import type { AdminInteractionRecord } from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'

/**
 * 心愿互动记录审计（Sprint 1.2）：完整审计轨迹查询。
 *
 * 含已取消（软删）记录：deletedAt 非空表示用户已取消（如同求撤销），
 * 星光消耗不退还，供互动趋势分析与异常行为审计使用。
 */
export default function WishInteractions() {
  const actionRef = useRef<ActionType>(null)

  const columns: ProColumns<AdminInteractionRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 90, search: false },
    {
      title: '类型',
      dataIndex: 'type',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(INTERACTION_TYPE_MAP).map(([value, info]) => [value, { text: `${info.emoji} ${info.label}` }]),
      ),
      render: (_, record) => {
        const info = INTERACTION_TYPE_MAP[record.type]
        return info ? <Tag color={info.color}>{info.emoji} {info.label}</Tag> : record.type
      },
    },
    {
      title: '心愿 ID',
      dataIndex: 'wishId',
      width: 100,
      fieldProps: { placeholder: '按心愿 ID 筛选' },
      render: (_, record) => (
        <Tooltip title={record.wishTitle}>
          <span>{record.wishId}</span>
        </Tooltip>
      ),
    },
    {
      title: '心愿标题',
      dataIndex: 'wishTitle',
      width: 220,
      ellipsis: true,
      search: false,
      render: (_, record) => (
        <Tooltip title={record.wishTitle} placement="topLeft">
          {record.wishTitle || '-'}
        </Tooltip>
      ),
    },
    {
      title: '用户 ID',
      dataIndex: 'userId',
      width: 100,
      fieldProps: { placeholder: '按用户 ID 筛选' },
    },
    { title: '用户昵称', dataIndex: 'nickname', width: 130, ellipsis: true, search: false },
    {
      title: '祝福内容',
      dataIndex: 'content',
      width: 240,
      ellipsis: true,
      search: false,
      render: (_, record) => (record.content ? (
        <Tooltip title={record.content} placement="topLeft">
          {record.content}
        </Tooltip>
      ) : (
        <span style={{ color: 'rgba(0,0,0,0.25)' }}>-</span>
      )),
    },
    { title: '星光消耗', dataIndex: 'starlightCost', width: 90, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      search: false,
      render: (_, record) =>
        record.deletedAt ? <Tag color="default">已取消</Tag> : <Tag color="success">有效</Tag>,
    },
    { title: '互动时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 160, search: false },
    {
      title: '互动时间范围',
      dataIndex: 'timeRange',
      valueType: 'dateTimeRange',
      hideInTable: true,
      search: {
        transform: (value: string[]) => ({
          // 后端 LocalDateTime 接收 ISO 8601（T 分隔）；范围含两端
          startTime: value?.[0] ? `${value[0].replace(' ', 'T')}` : undefined,
          endTime: value?.[1] ? `${value[1].replace(' ', 'T')}` : undefined,
        }),
      },
    },
  ]

  return (
    <ProTable<AdminInteractionRecord>
      headerTitle="互动记录审计"
      actionRef={actionRef}
      rowKey="id"
      scroll={{ x: 1300 }}
      request={async (params) => {
        return safeProTableRequest<AdminInteractionRecord>(() =>
          getAdminWishInteractions({
            page: params.current,
            pageSize: params.pageSize,
            wishId: params.wishId ? Number(params.wishId) : undefined,
            userId: params.userId ? Number(params.userId) : undefined,
            type: params.type,
            startTime: params.startTime,
            endTime: params.endTime,
          }),
        )
      }}
      columns={columns}
      pagination={{ defaultPageSize: 20, showSizeChanger: true }}
    />
  )
}
