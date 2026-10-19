import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Tag, DatePicker } from 'antd'
import {
  createAdminTopic,
  disableAdminTopic,
  getAdminTopic,
  listAdminTopics,
  updateAdminTopic,
} from '@/api/admin/software'
import type { AdminSoftwareTopic } from '@/api/admin/software'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  PUBLISHED: { label: '已上线', color: 'success' },
  DISABLED: { label: '已禁用', color: 'error' },
}

interface ItemRow {
  /** BASE-01：软件 ID 为雪花 ID，字符串传递防精度损失 */
  softwareId: string | null
  sortOrder: number | null
  /** UX02：分组稳定键/用途说明/可选替代 */
  groupKey?: string
  roleNote?: string
  optional?: boolean
}

/**
 * 专题管理（方案 10.6，P1）：创建"办公必备"等合集，手动选软件、排序、上线；
 * 清单整体维护（保存即全量替换）；不可见软件自动从公共专题过滤。
 */
export default function SoftwareTopics() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [editing, setEditing] = useState<{ topicId?: string } | null>(null)
  const [items, setItems] = useState<ItemRow[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm()

  const openCreate = () => {
    setEditing({})
    setItems([])
    form.setFieldsValue({
      slug: '',
      title: '',
      summary: '',
      coverAssetId: null,
      status: 'DRAFT',
      sortOrder: 0,
    })
  }

  const openEdit = async (topicId: string) => {
    try {
      const res = await getAdminTopic(topicId)
      const detail = res.data.data
      setEditing({ topicId })
      setItems(
        (detail.items ?? []).map((item) => ({
          softwareId: item.softwareId,
          sortOrder: item.sortOrder ?? null,
        })),
      )
      form.setFieldsValue({
        slug: detail.slug,
        title: detail.title,
        summary: detail.summary ?? '',
        coverAssetId: detail.coverAssetId ?? null,
        status: detail.status ?? 'DRAFT',
        sortOrder: detail.sortOrder ?? 0,
      })
    } catch {
      message.error('加载专题失败')
    }
  }

  const confirmSubmit = () => {
    Modal.confirm({
      title: '保存专题',
      content: '确认保存当前专题？已上线专题保存后立即对用户生效。',
      okText: '确认保存',
      cancelText: '再改改',
      onOk: () => doSubmit(),
    })
  }

  const doSubmit = async () => {
    const values = await form.validateFields()
    const cleanItems = items
      .filter((item) => item.softwareId != null)
      .map((item) => ({
        softwareId: item.softwareId as string,
        sortOrder: item.sortOrder ?? undefined,
        groupKey: item.groupKey?.trim() || undefined,
        roleNote: item.roleNote?.trim() || undefined,
        optional: item.optional ?? undefined,
      }))
    if (cleanItems.length === 0) {
      message.warning('请至少添加一个软件到专题清单')
      return
    }
    setSubmitting(true)
    try {
      const payload = {
        slug: values.slug,
        title: values.title,
        summary: values.summary || undefined,
        coverAssetId: values.coverAssetId ?? undefined,
        status: values.status,
        sortOrder: values.sortOrder ?? 0,
        description: values.description || undefined,
        scheduledStartAt: values.scheduledRange?.[0]?.toISOString(),
        scheduledEndAt: values.scheduledRange?.[1]?.toISOString(),
        items: cleanItems,
      }
      if (editing?.topicId) {
        await updateAdminTopic(editing.topicId, payload)
      } else {
        await createAdminTopic(payload)
      }
      message.success('已保存（清单整体替换）')
      setEditing(null)
      actionRef.current?.reload()
    } catch {
      // 校验/业务错误已提示
    } finally {
      setSubmitting(false)
    }
  }

  const columns: ProColumns<AdminSoftwareTopic>[] = [
    { title: '专题ID', dataIndex: 'topicId', width: 90, search: false },
    { title: '标识', dataIndex: 'slug', width: 180, search: false },
    { title: '标题', dataIndex: 'title', width: 220, ellipsis: true, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      search: false,
      render: (_, record) => {
        const info = STATUS_MAP[record.status] ?? { label: record.status, color: 'default' }
        return <Tag color={info.color}>{info.label}</Tag>
      },
    },
    { title: '排序', dataIndex: 'sortOrder', width: 80, search: false },
    { title: '软件数', dataIndex: 'itemCount', width: 90, search: false },
    { title: '更新时间', dataIndex: 'updatedAt', valueType: 'dateTime', width: 170, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 150,
      render: (_, record) => [
        <a key="edit" onClick={() => void openEdit(record.topicId)}>
          编辑
        </a>,
        <Popconfirm
          key="disable"
          title="禁用后公共专题不再可见，确认禁用？"
          onConfirm={async () => {
            try {
              await disableAdminTopic(record.topicId)
              message.success('已禁用')
              actionRef.current?.reload()
            } catch {
              message.error('操作失败')
            }
          }}
        >
          <a style={{ color: '#d4380d' }}>禁用</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" onClick={openCreate}>
          新增专题
        </Button>
      </Space>
      <ProTable<AdminSoftwareTopic>
        headerTitle="专题管理"
        rowKey="topicId"
        actionRef={actionRef}
        columns={columns}
        options={{ density: false }}
        search={false}
        request={(params) =>
          safeProTableRequest(() =>
            listAdminTopics({ page: params.current, size: params.pageSize }),
          )
        }
        pagination={{ defaultPageSize: 20 }}
      />

      <Modal
        title={editing?.topicId ? '编辑专题' : '新增专题'}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={confirmSubmit}
        confirmLoading={submitting}
        okText="保存"
        width={720}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item
              name="slug"
              label="标识（URL 用）"
              rules={[
                { required: true, message: '请输入专题标识' },
                {
                  pattern: /^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$/,
                  message: '小写字母/数字/连字符',
                },
              ]}
              style={{ width: 260 }}
            >
              <Input maxLength={64} placeholder="office-essentials" />
            </Form.Item>
            <Form.Item
              name="title"
              label="标题"
              rules={[{ required: true, max: 80, message: '标题 1~80 字' }]}
              style={{ width: 260 }}
            >
              <Input maxLength={80} />
            </Form.Item>
            <Form.Item name="sortOrder" label="排序">
              <InputNumber style={{ width: 100 }} />
            </Form.Item>
            <Form.Item name="status" label="状态" style={{ width: 140 }}>
              <Select
                options={[
                  { value: 'DRAFT', label: '草稿' },
                  { value: 'PUBLISHED', label: '上线' },
                  { value: 'DISABLED', label: '禁用' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="summary" label="运营说明（≤500 字）">
            <Input.TextArea rows={2} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="description" label="专题介绍（≤2000 字，随专题详情展示）">
            <Input.TextArea rows={3} maxLength={2000} showCount />
          </Form.Item>
          <Form.Item name="problem" label="要解决的问题（≤500 字，工具箱：能帮你做什么）">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
          <Form.Item name="audience" label="适用人群（≤500 字）">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
          <Form.Item name="prerequisites" label="准备条件（≤500 字：账号/系统要求/前置工具）">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
          <Form.Item name="scheduledRange" label="投放时间窗（UTC；留空=上线即生效/不自动下线）">
            <DatePicker.RangePicker showTime format="YYYY-MM-DD HH:mm" />
          </Form.Item>

          <Form.Item label="软件清单（顺序即展示顺序；保存全量替换）" required>
            {items.map((item, index) => (
              <Space key={index} style={{ display: 'flex', marginBottom: 8 }}>
                <Input
                  placeholder="软件ID"
                  value={item.softwareId ?? ''}
                  onChange={(e) =>
                    setItems((prev) =>
                      prev.map((row, rowIndex) =>
                        rowIndex === index ? { ...row, softwareId: e.target.value || null } : row,
                      ),
                    )
                  }
                  style={{ width: 200 }}
                />
                <InputNumber
                  placeholder="排序"
                  value={item.sortOrder}
                  onChange={(value) =>
                    setItems((prev) =>
                      prev.map((row, rowIndex) =>
                        rowIndex === index ? { ...row, sortOrder: value ?? null } : row,
                      ),
                    )
                  }
                  style={{ width: 120 }}
                />
                <Button
                  danger
                  type="link"
                  onClick={() => setItems((prev) => prev.filter((_, i) => i !== index))}
                >
                  移除
                </Button>
                              <Input
                  placeholder="分组（如：准备）"
                  value={item.groupKey ?? ''}
                  onChange={(e) =>
                    setItems((prev) =>
                      prev.map((row, rowIndex) =>
                        rowIndex === index
                          ? { ...row, groupKey: e.target.value }
                          : row,
                      ),
                    )
                  }
                  style={{ width: 120 }}
                />
                <Input
                  placeholder="用途说明（选择理由）"
                  value={item.roleNote ?? ''}
                  onChange={(e) =>
                    setItems((prev) =>
                      prev.map((row, rowIndex) =>
                        rowIndex === index
                          ? { ...row, roleNote: e.target.value }
                          : row,
                      ),
                    )
                  }
                  style={{ width: 220 }}
                />
                <input
                  type="checkbox"
                  title="可选替代项"
                  checked={item.optional ?? false}
                  onChange={(e) =>
                    setItems((prev) =>
                      prev.map((row, rowIndex) =>
                        rowIndex === index
                          ? { ...row, optional: e.target.checked }
                          : row,
                      ),
                    )
                  }
                />
                </Space>
            ))}
            <Button
              type="dashed"
              block
              onClick={() => setItems((prev) => [...prev, { softwareId: null, sortOrder: null }])}
            >
              添加软件
            </Button>
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
