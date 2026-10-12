import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm, Descriptions, Image, Rate, Input, Space, Alert } from 'antd'
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
  /** R08 下架理由（作者可见） */
  hideReason?: string | null
  reply: string
  createdAt: string
  updatedAt: string
}

// R08：评价默认上架（status=1 展示中），违规由管理员下架（status=0，必带理由）
const REVIEW_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '已下架', color: 'error' },
  1: { label: '展示中', color: 'success' },
}

export default function Reviews() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [detailVisible, setDetailVisible] = useState(false)
  const [currentReview, setCurrentReview] = useState<ReviewRecord | null>(null)
  const [hideTarget, setHideTarget] = useState<ReviewRecord | null>(null)
  const [hideReason, setHideReason] = useState('')
  const [hiding, setHiding] = useState(false)

  const handleRestore = async (id: number) => {
    await updateReviewStatus(id, { status: 1, reason: '' })
    message.success('已恢复展示')
    actionRef.current?.reload()
  }

  const submitHide = async () => {
    if (!hideTarget) return
    if (!hideReason.trim()) {
      message.error('下架必须填写理由（将同步告知作者）')
      return
    }
    setHiding(true)
    try {
      await updateReviewStatus(hideTarget.id, { status: 0, reason: hideReason.trim() })
      message.success('已下架并记录理由')
      setHideTarget(null)
      setHideReason('')
      actionRef.current?.reload()
    } catch {
      message.error('下架失败')
    } finally {
      setHiding(false)
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
    {
      title: '下架理由',
      dataIndex: 'hideReason',
      width: 180,
      search: false,
      ellipsis: true,
      render: (_, record) => record.hideReason || <span style={{ color: '#bbb' }}>—</span>,
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
        if (Number(record.status) === 1) {
          // R08：下架必须填写理由（后端强校验）
          actions.push(
            <Button
              key="hide"
              type="link"
              size="small"
              danger
              onClick={() => {
                setHideTarget(record)
                setHideReason('')
              }}
            >
              下架
            </Button>,
          )
        } else {
          actions.push(
            <Popconfirm
              key="restore"
              title="确认恢复该评价展示？"
              onConfirm={() => handleRestore(record.id)}
            >
              <Button type="link" size="small">恢复展示</Button>
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
      <ProTable
        headerTitle="评论管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1300 }}
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
          modalProps={{ destroyOnHidden: true, footer: null, keyboard: false }}
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
            {currentReview.hideReason && (
              <Descriptions.Item label="下架理由">{currentReview.hideReason}</Descriptions.Item>
            )}
            <Descriptions.Item label="商家回复">{currentReview.reply || '暂无回复'}</Descriptions.Item>
            <Descriptions.Item label="评论时间">{currentReview.createdAt}</Descriptions.Item>
          </Descriptions>
        </ModalForm>
      )}

      {/* R08 下架理由录入（必填，作者可在订单详情看到） */}
      <ModalForm
        title={`下架评论 #${hideTarget?.id ?? ''}`}
        open={hideTarget !== null}
        onOpenChange={(open) => {
          if (!open) setHideTarget(null)
        }}
        onFinish={async () => {
          await submitHide()
          return true
        }}
        modalProps={{ destroyOnHidden: true, confirmLoading: hiding }}
        width={480}
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="下架后该评价不在商品详情公开展示，理由会展示给评价作者，请如实填写。"
        />
        <Input.TextArea
          value={hideReason}
          onChange={(e) => setHideReason(e.target.value)}
          rows={3}
          maxLength={200}
          showCount
          placeholder="必填：下架理由（≤200 字，作者可见），如「图片含侵权导流二维码」"
        />
      </ModalForm>
    </>
  )
}
