import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, Descriptions, Empty, Input, Modal, Spin, Steps, Tag, message } from 'antd'
import {
  cancelAfterSale,
  getAfterSaleDetail,
  submitReturnShipping,
} from '@/api/afterSale'
import type { AfterSaleCase } from '@/api/afterSale'
import type { ApiResponse } from '@/types/api'

const STATUS_META: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待受理', color: 'processing' },
  APPROVED: { text: '已批准·待寄回', color: 'blue' },
  REJECTED: { text: '已拒绝', color: 'red' },
  REFUNDED: { text: '已退款', color: 'green' },
  CLOSED: { text: '已关闭', color: 'default' },
}

const TYPE_LABEL: Record<string, string> = {
  REFUND_ONLY: '仅退款',
  RETURN_REFUND: '退货退款',
}

const STATUS_STEP: Record<string, number> = {
  PENDING: 0,
  APPROVED: 1,
  REFUNDED: 2,
  REJECTED: 2,
  CLOSED: 2,
}

export default function AfterSaleDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [detail, setDetail] = useState<AfterSaleCase | null>(null)
  const [loading, setLoading] = useState(true)
  const [shippingOpen, setShippingOpen] = useState(false)
  const [carrier, setCarrier] = useState('')
  const [trackingNo, setTrackingNo] = useState('')
  const [shippingSubmitting, setShippingSubmitting] = useState(false)

  const load = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const { data: res } = await getAfterSaleDetail(id)
      setDetail((res as ApiResponse<AfterSaleCase>).data ?? null)
    } catch {
      setDetail(null)
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    void load()
  }, [load])

  const handleCancel = () => {
    if (!detail) return
    Modal.confirm({
      title: '取消售后申请',
      content: `确认撤销案件「${detail.caseNo}」吗？撤销后可重新发起。`,
      okText: '确认撤销',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await cancelAfterSale(detail.id)
          message.success('已撤销')
          await load()
        } catch {
          message.error('取消失败')
        }
      },
    })
  }

  const handleShipping = async () => {
    if (!detail) return
    if (!carrier.trim() || !trackingNo.trim()) {
      message.warning('请填写承运商与运单号')
      return
    }
    setShippingSubmitting(true)
    try {
      await submitReturnShipping(detail.id, carrier.trim(), trackingNo.trim())
      message.success('退货运单已登记，等待商家验货')
      setShippingOpen(false)
      setCarrier('')
      setTrackingNo('')
      await load()
    } catch {
      message.error('登记失败')
    } finally {
      setShippingSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  if (!detail) {
    return (
      <div style={{ maxWidth: 880, margin: '0 auto', padding: 24 }}>
        <Empty description="案件不存在或无权查看" />
        <div style={{ textAlign: 'center', marginTop: 12 }}>
          <Button type="link" onClick={() => navigate('/after-sale')}>
            返回我的售后
          </Button>
        </div>
      </div>
    )
  }

  const meta = STATUS_META[detail.status] ?? { text: detail.status, color: 'default' }
  const step = STATUS_STEP[detail.status] ?? 0
  // 页面不自造状态：可操作项严格按服务端状态渲染
  const canCancel = detail.status === 'PENDING'
  const canShip = detail.status === 'APPROVED' && detail.type === 'RETURN_REFUND'
  const isRejected = detail.status === 'REJECTED'

  return (
    <div style={{ maxWidth: 880, margin: '0 auto', padding: '20px 16px 48px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
        <h2 style={{ margin: 0, fontSize: 18 }}>售后详情</h2>
        <Button type="link" onClick={() => navigate('/after-sale')}>
          返回列表
        </Button>
      </div>

      {/* 案件进度（状态语义与列表一致） */}
      {detail.status !== 'CLOSED' && (
        <Steps
          size="small"
          current={step}
          status={isRejected ? 'error' : undefined}
          items={[
            { title: '提交申请' },
            { title: isRejected ? '已拒绝' : '商家受理' },
            { title: detail.type === 'RETURN_REFUND' ? '验货退款' : '退款' },
          ]}
          style={{ marginBottom: 16 }}
        />
      )}

      <div
        style={{
          background: 'var(--color-bg-container)',
          border: '1px solid var(--color-border)',
          borderRadius: 10,
          padding: 16,
          marginBottom: 14,
        }}
      >
        <Descriptions column={2} size="small" bordered>
          <Descriptions.Item label="案件号">{detail.caseNo}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={meta.color}>{meta.text}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="类型">{TYPE_LABEL[detail.type] ?? detail.type}</Descriptions.Item>
          <Descriptions.Item label="数量">{detail.quantity}</Descriptions.Item>
          <Descriptions.Item label="订单号">{detail.orderNo ?? detail.orderId}</Descriptions.Item>
          <Descriptions.Item label="申请时间">{(detail.createdAt ?? '').slice(0, 19).replace('T', ' ')}</Descriptions.Item>
          <Descriptions.Item label="退款金额">
            {detail.refundAmount != null ? (
              <span style={{ color: '#fa541c', fontWeight: 600 }}>¥{detail.refundAmount}</span>
            ) : (
              '-'
            )}
          </Descriptions.Item>
          <Descriptions.Item label="退款单号">{detail.refundNo ?? '-'}</Descriptions.Item>
          {isRejected && (
            <Descriptions.Item label="拒绝原因" span={2}>
              <span style={{ color: '#cf1322' }}>{detail.rejectReason ?? '-'}</span>
            </Descriptions.Item>
          )}
          {detail.reason && (
            <Descriptions.Item label="申请原因" span={2}>
              {detail.reason}
            </Descriptions.Item>
          )}
        </Descriptions>
      </div>

      {/* 可操作项（严格按服务端状态） */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 14 }}>
        {canShip && (
          <Button type="primary" onClick={() => setShippingOpen(true)}>
            填写退货运单
          </Button>
        )}
        {canCancel && (
          <Button danger onClick={handleCancel}>
            撤销申请
          </Button>
        )}
        {detail.refundNo && detail.status === 'REFUNDED' && (
          <Button onClick={() => navigate(`/orders`)}>查看订单</Button>
        )}
      </div>

      {/* 时间线（服务端事实） */}
      <div
        style={{
          background: 'var(--color-bg-container)',
          border: '1px solid var(--color-border)',
          borderRadius: 10,
          padding: 16,
        }}
      >
        <h3 style={{ margin: '0 0 12px', fontSize: 15 }}>处理进度</h3>
        {(detail.timeline ?? []).length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无进度记录" />
        ) : (
          <Steps
            direction="vertical"
            size="small"
            current={(detail.timeline?.length ?? 1) - 1}
            items={(detail.timeline ?? []).map((entry) => ({
              title: entry.action,
              description: (
                <span>
                  {entry.detail ? `${entry.detail} · ` : ''}
                  <span style={{ color: 'var(--color-text-tertiary)' }}>
                    {(entry.createdAt ?? '').slice(0, 19).replace('T', ' ')}
                  </span>
                </span>
              ),
            }))}
          />
        )}
      </div>

      <Modal
        title="填写退货运单"
        open={shippingOpen}
        onOk={handleShipping}
        confirmLoading={shippingSubmitting}
        onCancel={() => setShippingOpen(false)}
        okText="提交"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <Input
            placeholder="承运商（如：顺丰速运）"
            value={carrier}
            onChange={(e) => setCarrier(e.target.value)}
            maxLength={32}
          />
          <Input
            placeholder="运单号"
            value={trackingNo}
            onChange={(e) => setTrackingNo(e.target.value)}
            maxLength={64}
          />
        </div>
      </Modal>
    </div>
  )
}
