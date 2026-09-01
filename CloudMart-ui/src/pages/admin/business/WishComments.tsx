import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Input, Modal, Popconfirm, Tag, Tooltip } from 'antd'
import {
  getAdminWishComments,
  updateAdminWishCommentStatus,
  WISH_COMMENT_STATUS_MAP,
} from '@/api/admin/wish'
import type { AdminWishCommentRecord } from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

/**
 * 心愿评论审核（Sprint 1.2）：敏感词命中处理 + 评论上下架。
 *
 * 敏感词审核入口：筛选 sensitiveHit=是 + status=已上架 得到待处理命中列表，
 * 人工复核后下架（四端立即不展示）或恢复；已是目标状态后端返回 409。
 */
export default function WishComments() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [detail, setDetail] = useState<AdminWishCommentRecord | null>(null)

  const handleStatusChange = async (record: AdminWishCommentRecord, status: 'VISIBLE' | 'HIDDEN') => {
    try {
      const res = await updateAdminWishCommentStatus(record.id, { status })
      message.success(status === 'HIDDEN' ? '已下架' : '已恢复上架')
      if (detail?.id === record.id) setDetail(res.data.data)
      actionRef.current?.reload()
    } catch {
      message.error(status === 'HIDDEN' ? '下架失败' : '恢复失败')
    }
  }

  const columns: ProColumns<AdminWishCommentRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 90, search: false },
    {
      title: '心愿 ID',
      dataIndex: 'wishId',
      width: 100,
      fieldProps: { placeholder: '按心愿 ID 筛选' },
    },
    {
      title: '心愿标题',
      dataIndex: 'wishTitle',
      width: 200,
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
    { title: '用户昵称', dataIndex: 'nickname', width: 120, ellipsis: true, search: false },
    {
      title: '评论内容',
      dataIndex: 'content',
      width: 280,
      ellipsis: true,
      search: false,
      render: (_, record) => (
        <Button type="link" size="small" style={{ padding: 0 }} onClick={() => setDetail(record)}>
          <Tooltip title="点击查看全文" placement="topLeft">
            {record.content}
          </Tooltip>
        </Button>
      ),
    },
    {
      title: '回复',
      dataIndex: 'parentId',
      width: 80,
      search: false,
      render: (_, record) => (record.parentId ? <Tag>#{record.parentId}</Tag> : '-'),
    },
    {
      title: '敏感词',
      dataIndex: 'sensitiveHit',
      width: 90,
      valueType: 'select',
      valueEnum: { true: { text: '命中' }, false: { text: '未命中' } },
      render: (_, record) =>
        record.sensitiveHit ? <Tag color="warning">命中</Tag> : <Tag color="default">未命中</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(WISH_COMMENT_STATUS_MAP).map(([value, info]) => [value, { text: info.label }]),
      ),
      render: (_, record) => {
        const info = WISH_COMMENT_STATUS_MAP[record.status]
        return info ? <Tag color={info.color}>{info.label}</Tag> : record.status
      },
    },
    { title: '评论时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 160, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 110,
      fixed: 'right',
      render: (_, record) =>
        record.status === 'VISIBLE' ? (
          <Popconfirm
            title="确认下架该评论？"
            description="下架后四端立即不展示，可随时恢复"
            onConfirm={() => handleStatusChange(record, 'HIDDEN')}
            okText="确认"
            cancelText="取消"
          >
            <Button danger size="small">
              下架
            </Button>
          </Popconfirm>
        ) : (
          <Popconfirm
            title="确认恢复上架？"
            onConfirm={() => handleStatusChange(record, 'VISIBLE')}
            okText="确认"
            cancelText="取消"
          >
            <Button size="small">
              恢复上架
            </Button>
          </Popconfirm>
        ),
    },
  ]

  return (
    <>
      <ProTable<AdminWishCommentRecord>
        headerTitle="心愿评论审核"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1400 }}
        request={async (params) => {
          return safeProTableRequest<AdminWishCommentRecord>(() =>
            getAdminWishComments({
              page: params.current,
              pageSize: params.pageSize,
              wishId: params.wishId || undefined,
              userId: params.userId || undefined,
              sensitiveHit:
                params.sensitiveHit === undefined || params.sensitiveHit === null
                  ? undefined
                  : params.sensitiveHit === 'true' || params.sensitiveHit === true,
              status: params.status,
            }),
          )
        }}
        columns={columns}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
      />

      <Modal
        title={`评论详情 #${detail?.id ?? ''}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={null}
        width={560}
      >
        {detail && (
          <>
            <div style={{ marginBottom: 8, display: 'flex', gap: 8 }}>
              <Tag color={detail.sensitiveHit ? 'warning' : 'default'}>
                {detail.sensitiveHit ? '敏感词命中' : '敏感词未命中'}
              </Tag>
              <Tag color={WISH_COMMENT_STATUS_MAP[detail.status]?.color}>
                {WISH_COMMENT_STATUS_MAP[detail.status]?.label ?? detail.status}
              </Tag>
            </div>
            <Input.TextArea value={detail.content} readOnly autoSize={{ minRows: 3, maxRows: 10 }} />
            <div style={{ marginTop: 8, color: 'rgba(0,0,0,0.45)', fontSize: 12 }}>
              心愿：#{detail.wishId} {detail.wishTitle} · 用户：{detail.nickname}(#{detail.userId}) ·
              评论时间：{detail.createdAt} · 状态变更：{detail.updatedAt}
            </div>
          </>
        )}
      </Modal>
    </>
  )
}
