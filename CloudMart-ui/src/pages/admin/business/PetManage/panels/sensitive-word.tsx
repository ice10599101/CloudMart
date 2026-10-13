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
  deletePetSensitiveWord,
  listPetSensitiveWords,
  upsertPetSensitiveWord,
  type AdminPetSensitiveWord,
} from '@/api/admin/pet'

const { Text } = Typography

/** 敏感词类别文案（与后端枚举对齐） */
const SENSITIVE_CATEGORY_LABELS: Record<string, string> = {
  POLITICS: '政治',
  ABUSE: '辱骂',
  AD: '广告导流',
  CRISIS: '危机干预',
}

/** 敏感词库面板（P0-1 内容安全：增删改 + 停用，生效 ≤1 分钟） */
export function SensitiveWordPanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const [rows, setRows] = useState<AdminPetSensitiveWord[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm<{ word: string; category: string }>()
  const [saving, setSaving] = useState(false)

  const load = useCallback(
    async (targetPage = page) => {
      setLoading(true)
      try {
        const { data: res } = await listPetSensitiveWords({ page: targetPage, size: 10 })
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
    [page],
  )

  useEffect(() => {
    void load(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const submit = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const { data: res } = await upsertPetSensitiveWord(values)
      if (res.success) {
        messageApi.success('已保存，≤1 分钟全实例生效')
        form.resetFields()
        void load()
      }
    } catch {
      // 拦截器已提示（重复词 400）
    } finally {
      setSaving(false)
    }
  }

  const doDelete = (row: AdminPetSensitiveWord) => {
    Modal.confirm({
      title: '删除该敏感词？',
      content: `「${row.word}」删除后立即从匹配词库移除。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const { data: res } = await deletePetSensitiveWord(row.id)
          if (res.success) {
            messageApi.success('已删除')
            void load()
          }
        } catch {
          // 拦截器已提示
        }
      },
    })
  }

  const columns: ColumnsType<AdminPetSensitiveWord> = [
    { title: 'ID', dataIndex: 'id', width: 100 },
    { title: '敏感词', dataIndex: 'word', width: 160 },
    {
      title: '类别',
      dataIndex: 'category',
      width: 110,
      render: (v: AdminPetSensitiveWord['category']) => <Tag>{SENSITIVE_CATEGORY_LABELS[v] ?? v}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number) => <Tag color={v === 1 ? 'green' : 'default'}>{v === 1 ? '启用' : '停用'}</Tag>,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作',
      width: 80,
      render: (_: unknown, row) => (
        <Button type="link" size="small" danger onClick={() => doDelete(row)}>
          删除
        </Button>
      ),
    },
  ]

  return (
    <div>
      {contextHolder}
      <Space style={{ marginBottom: 12 }}>
        <Form form={form} layout="inline">
          <Form.Item name="word" rules={[{ required: true, message: '填写敏感词' }]}>
            <Input placeholder="敏感词（≤64 字）" style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="category" initialValue="AD" rules={[{ required: true, message: '选择类别' }]}>
            <Select
              style={{ width: 120 }}
              options={Object.entries(SENSITIVE_CATEGORY_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Button type="primary" loading={saving} onClick={() => void submit()}>
            添加
          </Button>
        </Form>
        <Text type="secondary">命中宠物名/留言直接拒绝；CRISIS 类聊天触发安抚话术并自动举报；变更 ≤1 分钟生效</Text>
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
      />
    </div>
  )
}
