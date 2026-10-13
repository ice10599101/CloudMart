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
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  getPetReports,
  resolvePetReport,
  type AdminPetReport,
} from '@/api/admin/pet'

const { Text, Paragraph } = Typography

/** 举报展示文案（与后端 pet_report 枚举对齐） */
const TARGET_TYPE_LABELS: Record<string, string> = {
  WALL_MESSAGE: '留言墙',
  BOTTLE_CONTENT: '漂流瓶',
  NICKNAME: '昵称',
  CHAT_MESSAGE: '聊天',
}
const STATUS_LABELS: Record<string, string> = {
  PENDING: '待处理',
  HANDLED: '已处理',
  REJECTED: '已驳回',
}
const ACTION_LABELS: Record<string, string> = {
  CONTENT_REMOVED: '下架内容',
  USER_WARNED: '警告用户',
  USER_PET_BANNED: '封禁宠物',
  DISMISSED: '驳回举报',
}

/** 举报处理面板（P0-2 举报闭环） */
export function ReportPanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const [rows, setRows] = useState<AdminPetReport[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState<string | undefined>('PENDING')
  const [resolving, setResolving] = useState<AdminPetReport | null>(null)
  const [form] = Form.useForm<{ action: string; reason: string }>()
  const [submitting, setSubmitting] = useState(false)

  const load = useCallback(
    async (targetPage = page) => {
      setLoading(true)
      try {
        const { data: res } = await getPetReports({ page: targetPage, size: 10, status })
        if (res.success) {
          setRows(res.data || [])
          setTotal(Number(res.meta?.total ?? res.data?.length ?? 0))
        }
      } catch {
        // 拦截器已提示
      } finally {
        setLoading(false)
      }
    },
    [page, status],
  )

  useEffect(() => {
    void load(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status])

  const openResolve = (row: AdminPetReport) => {
    setResolving(row)
    form.setFieldsValue({ action: 'CONTENT_REMOVED', reason: '' })
  }

  const submitResolve = async () => {
    if (!resolving) {
      return
    }
    const values = await form.validateFields()
    Modal.confirm({
      title: '确认处理该举报？',
      content: `动作：${ACTION_LABELS[values.action] ?? values.action}；处理后举报人将收到通知，且操作不可撤销。`,
      okText: '确认处理',
      cancelText: '再想想',
      onOk: async () => {
        setSubmitting(true)
        try {
          const { data: res } = await resolvePetReport(resolving.id, values)
          if (res.success) {
            messageApi.success('已处理，举报人将收到通知')
            setResolving(null)
            void load()
          }
        } catch {
          // 拦截器已提示
        } finally {
          setSubmitting(false)
        }
      },
    })
  }

  const columns: ColumnsType<AdminPetReport> = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    {
      title: '类型',
      dataIndex: 'targetType',
      width: 110,
      render: (value: AdminPetReport['targetType']) => (
        <Tag>{TARGET_TYPE_LABELS[value] ?? value}</Tag>
      ),
    },
    { title: '对象ID', dataIndex: 'targetId', width: 100 },
    { title: '举报人', dataIndex: 'reporterUserId', width: 100 },
    { title: '举报原因', dataIndex: 'reason', ellipsis: true },
    {
      title: '来源',
      dataIndex: 'isAuto',
      width: 80,
      render: (value: AdminPetReport['isAuto']) =>
        value === 1 ? <Tag color="orange">自动</Tag> : <Tag>用户</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: AdminPetReport['status']) => (
        <Tag color={value === 'PENDING' ? 'red' : value === 'HANDLED' ? 'green' : 'default'}>
          {STATUS_LABELS[value] ?? value}
        </Tag>
      ),
    },
    {
      title: '处理动作',
      dataIndex: 'handleAction',
      width: 110,
      render: (v: AdminPetReport['handleAction']) => (v ? ACTION_LABELS[v] ?? v : '-'),
    },
    { title: '处理说明', dataIndex: 'handleReason', ellipsis: true, render: (v: AdminPetReport['handleReason']) => v ?? '-' },
    { title: '举报时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作',
      width: 90,
      render: (_: unknown, row) =>
        row.status === 'PENDING' ? (
          <Button type="link" size="small" onClick={() => openResolve(row)}>
            处理
          </Button>
        ) : (
          <Text type="secondary">{row.handledAt?.slice(0, 19) ?? '-'}</Text>
        ),
    },
  ]

  return (
    <div>
      {contextHolder}
      <Space style={{ marginBottom: 12 }}>
        <Select
          value={status}
          onChange={setStatus}
          style={{ width: 160 }}
          allowClear
          placeholder="全部状态"
          options={[
            { value: 'PENDING', label: '待处理' },
            { value: 'HANDLED', label: '已处理' },
            { value: 'REJECTED', label: '已驳回' },
          ]}
        />
        <Button type="primary" onClick={() => void load()}>
          查询
        </Button>
        <Text type="secondary">处理需选择动作并填写说明；确认后举报人收到站内通知</Text>
      </Space>
      <Table
        rowKey={(row) => String(row.id)}
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: page,
          pageSize: 10,
          total,
          showSizeChanger: false,
          onChange: (next) => {
            setPage(next)
            void load(next)
          },
        }}
        scroll={{ x: 1100 }}
      />
      <Modal
        title="处理举报"
        open={resolving !== null}
        onCancel={() => setResolving(null)}
        onOk={() => void submitResolve()}
        confirmLoading={submitting}
        okText="下一步"
        destroyOnClose
      >
        {resolving && (
          <div style={{ marginBottom: 12 }}>
            <Paragraph>
              <Text type="secondary">举报对象：</Text>
              <Tag>{TARGET_TYPE_LABELS[resolving.targetType] ?? resolving.targetType}</Tag>
              <Text type="secondary">#{String(resolving.targetId)}</Text>
            </Paragraph>
            <Paragraph>
              <Text type="secondary">举报原因：</Text>
              {resolving.reason}
            </Paragraph>
          </div>
        )}
        <Form form={form} layout="vertical">
          <Form.Item name="action" label="处理动作" rules={[{ required: true, message: '请选择处理动作' }]}>
            <Select options={Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label }))} />
          </Form.Item>
          <Form.Item
            name="reason"
            label="处理说明（将通知举报人）"
            rules={[
              { required: true, message: '请填写处理说明' },
              { max: 200, message: '不超过 200 字' },
            ]}
          >
            <Input.TextArea rows={3} placeholder="例如：已确认违规内容并下架，感谢反馈" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
