import { useRef } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm, message as antMessage } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import {
  getAdminPosts,
  updatePostStatus,
  togglePostTop,
  deletePost,
} from '@/api/admin/community'
import type { AdminPostRecord } from '@/api/admin/community'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

const POST_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '草稿', color: 'default' },
  1: { label: '已发布', color: 'success' },
  2: { label: '隐藏', color: 'warning' },
  3: { label: '已删除', color: 'error' },
}

export default function Posts() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)

  const handleToggleVisibility = async (record: AdminPostRecord) => {
    const newStatus = record.status === 1 ? 2 : 1
    await updatePostStatus(record.id, { status: newStatus })
    message.success(newStatus === 2 ? '隐藏成功' : '显示成功')
    actionRef.current?.reload()
  }

  const handleToggleTop = async (record: AdminPostRecord) => {
    await togglePostTop(record.id, { isTop: !record.isTop })
    message.success(record.isTop ? '取消置顶成功' : '置顶成功')
    actionRef.current?.reload()
  }

  const columns: ProColumns<AdminPostRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '标题', dataIndex: 'title', width: 200, ellipsis: true },
    { title: '作者ID', dataIndex: 'userId', width: 100, search: false },
    {
      title: '媒体类型',
      dataIndex: 'mediaType',
      width: 100,
      search: false,
      valueEnum: {
        TEXT: { text: '文本' },
        IMAGE: { text: '图片' },
        VIDEO: { text: '视频' },
      },
    },
    { title: '点赞数', dataIndex: 'likeCount', width: 90, search: false },
    { title: '评论数', dataIndex: 'commentCount', width: 90, search: false },
    { title: '收藏数', dataIndex: 'favoriteCount', width: 90, search: false },
    { title: '浏览数', dataIndex: 'viewCount', width: 90, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = POST_STATUS_MAP[record.status] ?? { label: '未知', color: 'default' }
        return <Tag color={statusInfo.color}>{statusInfo.label}</Tag>
      },
    },
    {
      title: '置顶',
      dataIndex: 'isTop',
      width: 80,
      search: false,
      render: (_, record) =>
        record.isTop ? <Tag color="blue">置顶</Tag> : <Tag>否</Tag>,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '标题搜索',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '请输入帖子标题' },
    },
    {
      title: '操作',
      valueType: 'option',
      width: 280,
      fixed: 'right',
      render: (_, record) => {
        const actions: React.ReactNode[] = [
          <Button key="detail" type="link" size="small" href={`/community/post/${record.id}`} target="_blank">
            查看详情
          </Button>,
        ]
        if (record.status === 1 || record.status === 2) {
          actions.push(
            <Popconfirm
              key="visibility"
              title={record.status === 1 ? '确认隐藏该帖子？' : '确认显示该帖子？'}
              onConfirm={() => handleToggleVisibility(record)}
            >
              <Button type="link" size="small">
                {record.status === 1 ? '隐藏' : '显示'}
              </Button>
            </Popconfirm>,
          )
        }
        if (record.status === 1) {
          actions.push(
            <Popconfirm
              key="top"
              title={record.isTop ? '确认取消置顶？' : '确认置顶该帖子？'}
              onConfirm={() => handleToggleTop(record)}
            >
              <Button type="link" size="small">
                {record.isTop ? '取消置顶' : '置顶'}
              </Button>
            </Popconfirm>,
          )
        }
        if (record.status !== 3) {
          actions.push(
            <Popconfirm
              key="delete"
              title="确定删除吗？"
              onConfirm={async () => {
                await deletePost(record.id)
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
    <ProTable<AdminPostRecord>
      headerTitle="帖子管理"
      actionRef={actionRef}
      rowKey="id"
      scroll={{ x: 1400 }}
      toolBarRender={() => [
        <Button
          key="export"
          icon={<DownloadOutlined />}
          onClick={async () => {
            try {
              const res = await getAdminPosts({ page: 1, pageSize: 1000 })
              const data = (res.data as { data?: AdminPostRecord[] }).data ?? []
              if (data.length === 0) {
                antMessage.warning('暂无数据可导出')
                return
              }
              const bom = '\uFEFF'
              const headers = ['ID', '标题', '作者ID', '媒体类型', '点赞数', '评论数', '收藏数', '浏览数', '状态', '置顶', '创建时间']
              const rows = data.map((r) => [
                String(r.id), r.title ?? '', String(r.userId), r.mediaType ?? 'TEXT',
                String(r.likeCount ?? 0), String(r.commentCount ?? 0), String(r.favoriteCount ?? 0), String(r.viewCount ?? 0),
                POST_STATUS_MAP[r.status]?.label ?? '未知', r.isTop ? '是' : '否', r.createdAt ?? '',
              ])
              const csv = [headers.join(','), ...rows.map((row) => row.map((c) => `"${c.replace(/"/g, '""')}"`).join(','))].join('\n')
              const blob = new Blob([bom + csv], { type: 'text/csv;charset=utf-8;' })
              const link = document.createElement('a')
              link.href = URL.createObjectURL(blob)
              link.download = '帖子数据.csv'
              link.click()
              URL.revokeObjectURL(link.href)
              antMessage.success('导出成功')
            } catch {
              antMessage.error('导出失败')
            }
          }}
        >
          导出CSV
        </Button>,
      ]}
      request={async (params) => {
        return safeProTableRequest<AdminPostRecord>(() =>
          getAdminPosts({
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
  )
}
