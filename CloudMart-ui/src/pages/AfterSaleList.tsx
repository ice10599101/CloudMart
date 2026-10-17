import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Empty, Segmented, Spin, Tag } from 'antd'
import { RightOutlined } from '@ant-design/icons'
import { pageMyAfterSales } from '@/api/afterSale'
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

const STATUS_FILTERS = [
  { label: '全部', value: '' },
  { label: '待受理', value: 'PENDING' },
  { label: '待寄回', value: 'APPROVED' },
  { label: '已退款', value: 'REFUNDED' },
  { label: '已拒绝', value: 'REJECTED' },
]

export default function AfterSaleList() {
  const navigate = useNavigate()
  const [cases, setCases] = useState<AfterSaleCase[]>([])
  const [total, setTotal] = useState(0)
  const [statusFilter, setStatusFilter] = useState('')
  const [loading, setLoading] = useState(true)

  const load = useCallback(async (status: string) => {
    setLoading(true)
    try {
      const { data: res } = await pageMyAfterSales({ page: 1, pageSize: 20, status: status || undefined })
      const response = res as ApiResponse<{ records: AfterSaleCase[]; total: number }>
      // WEB-01：分页按实际 page 对象（records/total）取数，不再按数组
      setCases(response.data?.records ?? [])
      setTotal(response.data?.total ?? 0)
    } catch {
      setCases([])
      setTotal(0)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(statusFilter)
  }, [statusFilter, load])

  return (
    <div style={{ maxWidth: 880, margin: '0 auto', padding: '20px 16px 48px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
        <h2 style={{ margin: 0, fontSize: 18 }}>我的售后</h2>
        <span style={{ color: 'var(--color-text-tertiary)', fontSize: 13 }}>共 {total} 条</span>
      </div>

      <Segmented
        options={STATUS_FILTERS}
        value={statusFilter}
        onChange={(v) => setStatusFilter(String(v))}
        style={{ marginBottom: 14 }}
      />

      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : cases.length === 0 ? (
        <Empty description="暂无售后记录" style={{ padding: '48px 0' }} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {cases.map((item) => {
            const meta = STATUS_META[item.status] ?? { text: item.status, color: 'default' }
            return (
              <div
                key={String(item.id)}
                onClick={() => navigate(`/after-sale/${item.id}`)}
                style={{
                  background: 'var(--color-bg-container)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 10,
                  padding: '14px 16px',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                }}
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 4 }}>
                    <Tag color={meta.color}>{meta.text}</Tag>
                    <span style={{ fontSize: 13, color: 'var(--color-text-tertiary)' }}>
                      {TYPE_LABEL[item.type] ?? item.type}
                    </span>
                  </div>
                  <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 2 }}>{item.caseNo}</div>
                  <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    订单 {item.orderNo ?? item.orderId} · {item.reason}
                  </div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  {item.refundAmount != null && (
                    <div style={{ color: '#fa541c', fontWeight: 600 }}>¥{item.refundAmount}</div>
                  )}
                  <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
                    {(item.createdAt ?? '').slice(0, 10)}
                  </div>
                </div>
                <RightOutlined style={{ color: 'var(--color-text-quaternary)' }} />
              </div>
            )
          })}
        </div>
      )}

      {cases.length > 0 && (
        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <Button type="link" onClick={() => navigate('/orders')}>
            返回我的订单
          </Button>
        </div>
      )}
    </div>
  )
}
