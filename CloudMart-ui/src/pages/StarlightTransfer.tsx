import { useState, useEffect, useCallback } from 'react'
import { Button, Card, Empty, Form, Input, InputNumber, Statistic, Table, Tag } from 'antd'
import { ArrowLeftOutlined, SendOutlined } from '@ant-design/icons'
import { history } from 'umi'
import { message } from '@/utils/appMessage'
import {
  createTransfer,
  getMyTransfers,
  getTransferLimits,
  type StarlightTransferLimits,
  type StarlightTransferRecord,
} from '@/api/starlightTransfer'
import { useAuthStore } from '@/stores/auth'

const PAGE_SIZE = 10

interface TransferFormValues {
  toUserId: number
  amount: number
  message?: string
}

/**
 * R19 星光转赠：今日额度卡 + 转赠表单（requestKey 幂等）+ 发出/收到明细分页。
 * 失败提示由 request 拦截器统一 message.error，页面不伪装成功。
 */
export default function StarlightTransfer() {
  const { user, userLoading } = useAuthStore()
  const [form] = Form.useForm<TransferFormValues>()
  const [limits, setLimits] = useState<StarlightTransferLimits | null>(null)
  const [records, setRecords] = useState<StarlightTransferRecord[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [loadingLimits, setLoadingLimits] = useState(true)
  const [loadingRecords, setLoadingRecords] = useState(true)
  const [submitting, setSubmitting] = useState(false)

  const loadLimits = useCallback(async () => {
    setLoadingLimits(true)
    try {
      const res = await getTransferLimits()
      if (res.data.success) setLimits(res.data.data)
    } catch {
      // 错误已由 request 拦截器统一提示
    } finally {
      setLoadingLimits(false)
    }
  }, [])

  const loadRecords = useCallback(async (nextPage: number) => {
    setLoadingRecords(true)
    try {
      const res = await getMyTransfers({ page: nextPage, pageSize: PAGE_SIZE })
      if (res.data.success) {
        setRecords(res.data.data?.records ?? [])
        setTotal(res.data.data?.total ?? 0)
        setPage(nextPage)
      }
    } catch {
      // 错误已由 request 拦截器统一提示
    } finally {
      setLoadingRecords(false)
    }
  }, [])

  useEffect(() => {
    if (!user && !userLoading) {
      history.push('/login?redirect=/starlight/transfer')
      return
    }
    if (user) {
      void loadLimits()
      void loadRecords(1)
    }
  }, [user, userLoading, loadLimits, loadRecords])

  const handleSubmit = async (values: TransferFormValues) => {
    setSubmitting(true)
    try {
      // idempotency.ts 未导出通用 uuid 生成器：按约定用 crypto.randomUUID() 去横线作为幂等键
      const requestKey = globalThis.crypto.randomUUID().replace(/-/g, '')
      const res = await createTransfer({
        toUserId: values.toUserId,
        amount: values.amount,
        message: values.message?.trim() || undefined,
        requestKey,
      })
      if (res.data.success) {
        message.success(`转赠成功，今日剩余可转 ${res.data.data.remainingToday}`)
        form.resetFields()
        void loadLimits()
        void loadRecords(1)
      }
    } catch {
      // 错误已由 request 拦截器统一提示（额度不足/超过上限/重复请求等）
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div style={{ maxWidth: 860, margin: '24px auto', padding: '0 16px' }}>
      <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => history.push('/profile')} style={{ marginBottom: 12 }}>
        个人中心
      </Button>

      <Card title="今日转赠额度" loading={loadingLimits} style={{ marginBottom: 16 }}>
        {limits && (
          <div style={{ display: 'flex', gap: 48, flexWrap: 'wrap' }}>
            <Statistic title="今日已用" value={limits.usedToday} suffix={`/ ${limits.dailyCap}`} />
            <Statistic title="今日剩余" value={limits.remainingToday} valueStyle={{ color: '#52c41a' }} />
            <Statistic title="业务日期" value={limits.businessDate} />
          </div>
        )}
      </Card>

      <Card title="转赠星光" style={{ marginBottom: 16 }}>
        <Form
          form={form}
          layout="vertical"
          style={{ maxWidth: 480 }}
          onFinish={(values) => {
            void handleSubmit(values)
          }}
        >
          <Form.Item
            label="对方用户ID"
            name="toUserId"
            rules={[
              { required: true, message: '请输入对方用户ID' },
              {
                validator: (_, value: number | null | undefined) =>
                  typeof value === 'number' && Number.isInteger(value) && value > 0
                    ? Promise.resolve()
                    : Promise.reject(new Error('用户ID须为正整数')),
              },
            ]}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="请输入收款方小答号（用户ID）" />
          </Form.Item>
          <Form.Item
            label="转赠数量"
            name="amount"
            rules={[
              { required: true, message: '请输入转赠数量' },
              {
                validator: (_, value: number | null | undefined) =>
                  typeof value === 'number' && Number.isInteger(value) && value >= 10 && value <= 100
                    ? Promise.resolve()
                    : Promise.reject(new Error('单笔转赠数量为 10-100 的整数')),
              },
            ]}
            extra="单笔可转 10-100 星光（整数）"
          >
            <InputNumber min={10} max={100} precision={0} style={{ width: '100%' }} placeholder="10-100" />
          </Form.Item>
          <Form.Item
            label="附言"
            name="message"
            rules={[{ max: 100, message: '附言最长 100 字' }]}
          >
            <Input.TextArea maxLength={100} showCount rows={3} placeholder="给 TA 写句祝福吧（选填，最长 100 字）" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={submitting}>
              确认转赠
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <Card title="转赠记录">
        <Table<StarlightTransferRecord>
          rowKey={(r) => String(r.id)}
          size="small"
          loading={loadingRecords}
          dataSource={records}
          locale={{ emptyText: <Empty description="暂无转赠记录" /> }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (nextPage) => {
              void loadRecords(nextPage)
            },
          }}
          columns={[
            {
              title: '方向',
              dataIndex: 'direction',
              width: 90,
              render: (direction: StarlightTransferRecord['direction']) =>
                direction === 'SENT'
                  ? <Tag color="blue">发出</Tag>
                  : <Tag color="green">收到</Tag>,
            },
            {
              title: '对方用户ID',
              width: 120,
              render: (_, record) => (record.direction === 'SENT' ? record.toUserId : record.fromUserId),
            },
            {
              title: '金额',
              dataIndex: 'amount',
              width: 90,
              render: (v: number, record: StarlightTransferRecord) => (
                <span style={{ color: record.direction === 'SENT' ? '#ff6b6b' : '#52c41a', fontWeight: 600 }}>
                  {record.direction === 'SENT' ? '-' : '+'}{v}
                </span>
              ),
            },
            { title: '附言', dataIndex: 'message', ellipsis: true, render: (v: string | null) => v || '-' },
            {
              title: '时间',
              dataIndex: 'createdAt',
              width: 170,
              render: (v: string) => new Date(v).toLocaleString('zh-CN'),
            },
          ]}
        />
      </Card>
    </div>
  )
}
