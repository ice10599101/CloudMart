import { useEffect, useState } from 'react'
import { Modal, Button, Rate, Input, Tag, Alert, Space } from 'antd'
import { message } from '@/utils/appMessage'
import {
  createReview,
  getOwnReviewByOrderItem,
  type ReviewItem,
} from '@/api/review'

/** 订单详情中评价入口所需的最小订单行字段 */
export interface ReviewableOrderItem {
  id: number
  orderId?: number
  productId: number
  skuId: number
  productName: string
}

/**
 * R08 订单行评价入口与表单：
 * 以 orderItemId 为唯一锚点；未评价显示"评价"，已评价显示"查看评价"，
 * 被管理员下架时展示理由。资格/防重复由后端权威判定。
 */
export default function OrderItemReview({
  orderId,
  item,
  onReviewed,
}: {
  orderId: number
  item: ReviewableOrderItem
  onReviewed?: () => void
}) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [existing, setExisting] = useState<ReviewItem | null>(null)
  const [rating, setRating] = useState(5)
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const res = await getOwnReviewByOrderItem(item.id)
      const review = res.data.data ?? null
      setExisting(review)
      if (review) {
        setRating(review.rating)
        setContent(review.content)
      }
    } catch {
      // 静默：打开失败不阻断订单页
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) {
      void load()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, item.id])

  const submit = async () => {
    if (!content.trim()) {
      message.warning('请填写评价内容')
      return
    }
    setSubmitting(true)
    try {
      const res = await createReview({
        orderItemId: item.id,
        orderId,
        productId: item.productId,
        skuId: item.skuId,
        rating,
        content: content.trim(),
      })
      message.success('评价成功，星光奖励将在稍后到账')
      setExisting(res.data.data ?? null)
      onReviewed?.()
      setOpen(false)
    } catch {
      // 拦截器已统一提示（无资格/已评价等）
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <Button
        type={existing ? 'default' : 'primary'}
        size="small"
        ghost={!!existing}
        loading={loading}
        onClick={() => setOpen(true)}
        style={{ marginTop: 6 }}
      >
        {existing ? '查看评价' : '评价'}
      </Button>

      <Modal
        title={`评价商品 · ${item.productName}`}
        open={open}
        onCancel={() => setOpen(false)}
        footer={
          existing
            ? null
            : [
                <Button key="cancel" onClick={() => setOpen(false)}>
                  取消
                </Button>,
                <Button key="submit" type="primary" loading={submitting} onClick={submit}>
                  提交评价
                </Button>,
              ]
        }
        destroyOnClose
      >
        {existing ? (
          <Space direction="vertical" style={{ width: '100%' }} size={10}>
            {existing.status === 0 && existing.hideReason && (
              <Alert
                type="warning"
                showIcon
                message="该评价已被管理员下架"
                description={`理由：${existing.hideReason}`}
              />
            )}
            <Rate disabled value={existing.rating} />
            <div style={{ whiteSpace: 'pre-wrap', color: 'var(--color-text)' }}>{existing.content}</div>
            {existing.images && existing.images.length > 0 && (
              <Space wrap>
                {existing.images.map((img) => (
                  <img key={img} src={img} alt="评价图片" style={{ width: 72, height: 72, objectFit: 'cover', borderRadius: 6 }} />
                ))}
              </Space>
            )}
            <Tag color="default">每条已完成订单行限评一次，不可修改</Tag>
          </Space>
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <div>
              <div style={{ marginBottom: 6, color: 'var(--color-text-secondary)' }}>评分</div>
              <Rate value={rating} onChange={setRating} />
            </div>
            <div>
              <div style={{ marginBottom: 6, color: 'var(--color-text-secondary)' }}>评价内容</div>
              <Input.TextArea
                rows={4}
                maxLength={1000}
                showCount
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder="分享真实的购买体验（1000 字以内）"
              />
            </div>
            <Alert type="info" showIcon message="仅可评价已完成订单中的商品；提交后奖励星光，评价不可修改。" />
          </Space>
        )}
      </Modal>
    </>
  )
}
