import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm, Descriptions, Image, Rate } from 'antd'
import {
  getReviews,
  updateReviewStatus,
  deleteReview,
} from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

interface ReviewRecord {
  id: number
  productId: number
  productName: string
  skuImage: string
  userId: number
  username: string
  avatar: string
  rating: number
  content: string
  images: string[]
  status: number
  reply: string
  createdAt: string
  updatedAt: string
}

const REVIEW_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '待审核', color: 'warning' },
  1: { label: '已通过', color: 'success' },
  2: { label: '已拒绝', color: 'error' },
}

export default function Reviews() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [detailVisible, setDetailVisible] = useState(false)
  const [currentReview, setCurrentReview] = useState<ReviewRecord | null>(null)

  const handleStatusChange = async (id: number, status: number) => {
    try {
      await updateReviewStatus(id, { status })
      message.success(status === 1 ? '审核通过' : '已拒绝')
      actionRef.current?.reload()
    } catch {
      message.error('状态更新失败')
    }
  }

  const handleDelete = async (id: number) => {
    await deleteReview(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const handleViewDetail = (record: ReviewRecord) => {
    setCurrentReview(record)
    setDetailVisible(true)
  }

  const columns: ProColumns<ReviewRecord>[] = [
    { title: '评论ID', dataIndex: 'id', width: 80, search: false },
    { title: '商品名称', dataIndex: 'productName', width: 180, ellipsis: true },
    { title: '用户', dataIndex: 'username', width: 100, search: false },
    {
      title: '评分',
      dataIndex: 'rating',
      width: 140,
      render: (_, record) => <Rate disabled value={record.rating} style={{ fontSize: 14 }} />,
    },
    {
      title: '评论内容',
      dataIndex: 'content',
      width: 200,
      search: false,
      ellipsis: true,
    },
    {
      title: '评分',
      dataIndex: 'ratingFilter',
      hideInTable: true,
      valueEnum: {
        1: { text: '1星' },
        2: { text: '2星' },
        3: { text: '3星' },
        4: { text: '4星' },
        5: { text: '5星' },
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = REVIEW_STATUS_MAP[record.status] ?? { label: '未知', color: 'default' }
        return <Tag color={statusInfo.color}>{statusInfo.label}</Tag>
      },
    },
    { title: '评论时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: 'right',
      render: (_, record) => {
        const actions: React.ReactNode[] = [
          <Button
            key="detail"
            type="link"
            size="small"
            onClick={() => handleViewDetail(record)}
          >
            详情
          </Button>,
        ]
        if (Number(record.status) === 0) {
          actions.push(
            <Popconfirm
              key="approve"
              title="确认通过该评论？"
              onConfirm={() => handleStatusChange(record.id, 1)}
            >
              <Button type="link" size="small">通过</Button>
            </Popconfirm>,
            <Popconfirm
              key="reject"
              title="确认拒绝该评论？"
              onConfirm={() => handleStatusChange(record.id, 2)}
            >
              <Button type="link" size="small" danger>拒绝</Button>
            </Popconfirm>,
          )
        }
        actions.push(
          <Popconfirm
            key="delete"
            title="确认删除该评论？删除后不可恢复"
            onConfirm={() => handleDelete(record.id)}
          >
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>,
        )
        return actions
      },
    },
  ]

  return (
    <>
      <ProTable<ReviewRecord>
        headerTitle="评论管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1200 }}
        request={async (params) => {
          return safeProTableRequest<ReviewRecord>(() =>
            getReviews({
              page: params.current,
              pageSize: params.pageSize,
              productName: params.productName,
              rating: params.ratingFilter,
              status: params.status,
            })
          )
        }}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      {currentReview && (
        <ModalForm
          title="评论详情"
          open={detailVisible}
          onOpenChange={setDetailVisible}
          onFinish={async () => true}
          modalProps={{ destroyOnHidden: true, footer: null, mask: { closable: false }, keyboard: false }}
          width={640}
          submitter={false}
        >
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="商品">{currentReview.productName}</Descriptions.Item>
            <Descriptions.Item label="用户">{currentReview.username}</Descriptions.Item>
            <Descriptions.Item label="评分">
              <Rate disabled value={currentReview.rating} />
            </Descriptions.Item>
            <Descriptions.Item label="评论内容">{currentReview.content}</Descriptions.Item>
            <Descriptions.Item label="评论图片">
              {currentReview.images && currentReview.images.length > 0 ? (
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  {currentReview.images.map((img, index) => (
                    <Image
                      key={index}
                      src={img}
                      width={80}
                      height={80}
                      style={{ objectFit: 'cover', borderRadius: 4 }}
                    />
                  ))}
                </div>
              ) : (
                '无图片'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={REVIEW_STATUS_MAP[currentReview.status]?.color ?? 'default'}>
                {REVIEW_STATUS_MAP[currentReview.status]?.label ?? '未知'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="商家回复">{currentReview.reply || '暂无回复'}</Descriptions.Item>
            <Descriptions.Item label="评论时间">{currentReview.createdAt}</Descriptions.Item>
          </Descriptions>
        </ModalForm>
      )}
    </>
  )
}
