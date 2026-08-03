import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm, Modal, Descriptions } from 'antd'
import {
  getAdminComments,
  updateCommentStatus,
  deleteComment,
} from '@/api/admin/community'
import type { AdminCommentRecord } from '@/api/admin/community'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

const COMMENT_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '正常', color: 'success' },
  1: { label: '隐藏', color: 'warning' },
  2: { label: '已删除', color: 'error' },
}

export default function Comments() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [detailRecord, setDetailRecord] = useState<AdminCommentRecord | null>(null)

  const handleToggleVisibility = async (record: AdminCommentRecord) => {
    const newStatus = record.status === 0 ? 1 : 0
    await updateCommentStatus(record.id, { status: newStatus })
    message.success(newStatus === 1 ? '隐藏成功' : '恢复成功')
    actionRef.current?.reload()
  }

  const columns: ProColumns<AdminCommentRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '帖子ID', dataIndex: 'postId', width: 100, search: false },
    { title: '用户ID', dataIndex: 'userId', width: 100, search: false },
    {
      title: '内容',
      dataIndex: 'content',
      width: 260,
      ellipsis: true,
      search: false,
    },
    { title: '点赞数', dataIndex: 'likeCount', width: 90, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = COMMENT_STATUS_MAP[record.status] ?? { label: '未知', color: 'default' }
        return <Tag color={statusInfo.color}>{statusInfo.label}</Tag>
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '内容搜索',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '请输入评论内容' },
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      fixed: 'right',
      render: (_, record) => {
        const actions: React.ReactNode[] = [
          <Button key="detail" type="link" size="small" onClick={() => setDetailRecord(record)}>详情</Button>,
        ]
        if (record.status !== 2) {
          actions.push(
            <Popconfirm
              key="toggle"
              title={record.status === 0 ? '确认隐藏该评论？' : '确认恢复该评论？'}
              onConfirm={() => handleToggleVisibility(record)}
            >
              <Button type="link" size="small">
                {record.status === 0 ? '隐藏' : '恢复'}
              </Button>
            </Popconfirm>,
          )
          actions.push(
            <Popconfirm
              key="delete"
              title="确定删除吗？"
              onConfirm={async () => {
                await deleteComment(record.id)
                message.success('删除成功')
                actionRef.current?.reload()
              }}
            >
              <Button type="link" size="small" danger>删除</Button>
            </Popconfirm>,
          )
        }
        return actions
      },
    },
  ]

  return (
    <>
      <ProTable<AdminCommentRecord>
        headerTitle="评论管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1100 }}
        request={async (params) => {
          return safeProTableRequest<AdminCommentRecord>(() =>
            getAdminComments({
              page: params.current,
              pageSize: params.pageSize,
              keyword: params.keyword,
              status: params.status,
            })
          )
        }}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title="评论详情"
        open={!!detailRecord}
        onCancel={() => setDetailRecord(null)}
        footer={null}
      >
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="ID">{detailRecord?.id}</Descriptions.Item>
          <Descriptions.Item label="帖子ID">{detailRecord?.postId}</Descriptions.Item>
          <Descriptions.Item label="用户ID">{detailRecord?.userId}</Descriptions.Item>
          <Descriptions.Item label="内容">{detailRecord?.content}</Descriptions.Item>
          <Descriptions.Item label="点赞数">{detailRecord?.likeCount}</Descriptions.Item>
          <Descriptions.Item label="状态">
            {(() => {
              const info = COMMENT_STATUS_MAP[detailRecord?.status ?? -1]
              return info ? <Tag color={info.color}>{info.label}</Tag> : '未知'
            })()}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">{detailRecord?.createdAt}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{detailRecord?.updatedAt}</Descriptions.Item>
        </Descriptions>
      </Modal>
    </>
  )
}
