import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd'
import {
  listInviteRelations,
  releaseInviteHold,
  type AdminInviteRelation,
} from '@/api/admin/invite'
import { useMessage } from '@/utils/useMessage'

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  PENDING_ELIGIBILITY: { label: '待资格', color: 'processing' },
  ELIGIBLE: { label: '资格达标', color: 'blue' },
  GRANTED: { label: '已发放', color: 'success' },
  SETTLING: { label: '发放中（等待双侧到账）', color: 'processing' },
  HOLD: { label: '挂起待核', color: 'error' },
  REJECTED_LIMIT: { label: '额度超限', color: 'warning' },
}

/**
 * 邀请台账（T15）：资格状态/限制原因/规则版本只读；
 * HOLD 复核释放（原因必填留痕；走完整资格检查链，不直接改发放标记）。
 */
export default function AdminInvites() {
  const message = useMessage()
  const [rows, setRows] = useState<AdminInviteRelation[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [statusFilter, setStatusFilter] = useState<string | null>('HOLD')
  const [loading, setLoading] = useState(false)
  const [releaseFor, setReleaseFor] = useState<AdminInviteRelation | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm()

  const load = useCallback(async (p: number) => {
    setLoading(true)
    try {
      const res = await listInviteRelations(statusFilter, p, 20)
      if (res.data.success) {
        setRows(res.data.data?.items ?? [])
        setTotal(res.data.data?.total ?? 0)
        setPage(p)
      }
    } finally {
      setLoading(false)
    }
  }, [statusFilter])

  useEffect(() => {
    void load(1)
  }, [load])

  const submitRelease = async () => {
    if (!releaseFor) return
    const values = await form.validateFields()
    setSubmitting(true)
    try {
      const res = await releaseInviteHold(releaseFor.id, values.reason)
      if (res.data.success) {
        message.success(`复核完成：${res.data.data?.result ?? '已完成'}`)
        setReleaseFor(null)
        await load(page)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select
          style={{ width: 150 }}
          value={statusFilter ?? 'ALL'}
          onChange={(v: string) => setStatusFilter(v === 'ALL' ? null : v)}
          options={[
            { value: 'ALL', label: '全部状态' },
            { value: 'HOLD', label: '挂起待核' },
            { value: 'PENDING_ELIGIBILITY', label: '待资格' },
            { value: 'ELIGIBLE', label: '资格达标' },
            { value: 'GRANTED', label: '已发放' },
            { value: 'SETTLING', label: '发放中' },
            { value: 'REJECTED_LIMIT', label: '额度超限' },
          ]}
        />
        <Button onClick={() => load(page)}>刷新</Button>
      </Space>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={rows}
        pagination={{ current: page, total, pageSize: 20, onChange: (p) => load(p) }}
        columns={[
          { title: '关系 ID', dataIndex: 'id', width: 100 },
          { title: '邀请人', dataIndex: 'inviterId', width: 110 },
          { title: '受邀人', dataIndex: 'inviteeId', width: 110 },
          {
            title: '资格状态',
            dataIndex: 'rewardStatus',
            width: 110,
            render: (v: string) => (
              <Tag color={STATUS_MAP[v]?.color}>{STATUS_MAP[v]?.label ?? v}</Tag>
            ),
          },
          { title: '限制/进度原因', dataIndex: 'rewardReason', ellipsis: true },
          { title: '达标时间', dataIndex: 'eligibleAt', width: 160, render: (v: string) => v || '-' },
          { title: '规则版本', dataIndex: 'ruleVersion', width: 90 },
          {
            title: '操作',
            width: 130,
            render: (_: unknown, row: AdminInviteRelation) =>
              row.rewardStatus === 'HOLD' ? (
                <Button
                  size="small"
                  type="primary"
                  onClick={() => {
                    setReleaseFor(row)
                    form.resetFields()
                  }}
                >
                  复核释放
                </Button>
              ) : (
                '-'
              ),
          },
        ] as never}
      />

      <Modal
        title={`HOLD 复核释放（关系 #${releaseFor?.id ?? ''}）`}
        open={!!releaseFor}
        onCancel={() => setReleaseFor(null)}
        onOk={() => void submitRelease()}
        confirmLoading={submitting}
        okText="确认释放"
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="reason"
            label="复核原因（必填，留痕）"
            rules={[{ required: true, message: '必须填写复核原因' }]}
            extra="确认受邀人账号状态已恢复；释放后走完整资格检查链（额度原子预占、发奖幂等），不直接改发放标记"
          >
            <Input.TextArea rows={3} placeholder="如：账号受限已解除，重新核验签到与注册时长" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
