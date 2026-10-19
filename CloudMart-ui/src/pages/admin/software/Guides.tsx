import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'antd'
import {
  deleteAdminGuide,
  listAdminGuides,
  saveAdminGuide,
  toggleAdminGuide,
  type AdminSoftwareGuide,
} from '@/api/admin/software'
import { useMessage } from '@/utils/useMessage'

const TYPE_LABEL: Record<string, string> = {
  INSTALL_GUIDE: '安装入门',
  WORKFLOW: '场景工作流',
  COMPARISON: '选型对比',
  TROUBLESHOOTING: '常见问题',
}

const dayjs = (v?: string | null) => (v ? v.slice(0, 10) : '')

/**
 * 软件教程管理（UX04）：元数据维护（articleId 绑定社区文章）；
 * 正文与审核真源在社区，保存时预检文章公开状态，读取时实时校验并标注。
 */
export default function SoftwareGuidesAdmin() {
  const message = useMessage()
  const [rows, setRows] = useState<AdminSoftwareGuide[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState<{ guideId?: string } | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm()

  const load = useCallback(async (p: number) => {
    setLoading(true)
    try {
      const res = await listAdminGuides(null, p, 12)
      if (res.data.success) {
        setRows(res.data.data?.records ?? [])
        setTotal(res.data.data?.total ?? 0)
        setPage(p)
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(1)
  }, [load])

  const openEdit = (row?: AdminSoftwareGuide) => {
    setEditing(row ? { guideId: row.id } : {})
    form.setFieldsValue({
      guideType: row?.guideType ?? 'INSTALL_GUIDE',
      title: row?.title ?? '',
      summary: row?.summary ?? '',
      articleId: row?.articleId ?? '',
      relatedSoftwareIds: row?.relatedSoftwareIds ?? [],
      platformScope: row?.platformScope ?? [],
      appliesToVersion: row?.appliesToVersion ?? '',
      checkedDate: row?.checkedAt ? dayjs(row.checkedAt) : null,
      checkNote: row?.checkNote ?? '',
      enabled: row?.enabled ?? false,
      displayOrder: row?.displayOrder ?? 0,
    })
  }

  const submit = async () => {
    const values = await form.validateFields()
    setSubmitting(true)
    try {
      const res = await saveAdminGuide(editing?.guideId ?? null, {
        guideType: values.guideType,
        title: values.title,
        summary: values.summary || null,
        articleId: values.articleId,
        relatedSoftwareIds: values.relatedSoftwareIds,
        platformScope: values.platformScope,
        appliesToVersion: values.appliesToVersion || null,
        checkedAt: values.checkedDate ? values.checkedDate.format('YYYY-MM-DD') : null,
        checkNote: values.checkNote || null,
        enabled: values.enabled,
        displayOrder: values.displayOrder,
      })
      if (res.data.success) {
        message.success('已保存')
        setEditing(null)
        await load(page)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => openEdit()}>
          新增教程
        </Button>
        <Button onClick={() => load(page)}>刷新</Button>
      </Space>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={rows}
        pagination={{ current: page, total, pageSize: 12, onChange: (p) => load(p) }}
        columns={[
          { title: '类型', dataIndex: 'guideType', width: 100, render: (v: string) => TYPE_LABEL[v] ?? v },
          { title: '标题', dataIndex: 'title', width: 200 },
          {
            title: '文章状态',
            dataIndex: 'articlePublic',
            width: 100,
            render: (ok: boolean) =>
              ok ? <Tag color="success">公开</Tag> : <Tag color="error">非公开·已隐藏</Tag>,
          },
          {
            title: '关联软件',
            dataIndex: 'relatedSoftwareIds',
            width: 160,
            render: (ids: string[]) => ids.join(', ') || '-',
          },
          { title: '检查日期', dataIndex: 'checkedAt', width: 110 },
          {
            title: '启用',
            dataIndex: 'enabled',
            width: 70,
            render: (v: boolean) => (v ? <Tag color="success">启用</Tag> : <Tag>停用</Tag>),
          },
          {
            title: '操作',
            width: 220,
            render: (_: unknown, row: AdminSoftwareGuide) => (
              <Space size="small" wrap>
                <Button size="small" onClick={() => openEdit(row)}>
                  编辑
                </Button>
                <Button
                  size="small"
                  onClick={() => toggleAdminGuide(row.id, !row.enabled).then(() => load(page))}
                >
                  {row.enabled ? '停用' : '启用'}
                </Button>
                <Popconfirm
                  title="确认删除该教程？"
                  onConfirm={() => deleteAdminGuide(row.id).then(() => load(page))}
                >
                  <Button size="small" danger>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ] as never}
      />

      <Modal
        title={editing?.guideId ? '编辑教程' : '新增教程'}
        open={!!editing}
        onCancel={() => setEditing(null)}
        onOk={() => void submit()}
        confirmLoading={submitting}
        width={680}
        okText="保存"
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="middle" wrap>
            <Form.Item name="guideType" label="类型" rules={[{ required: true }]}>
              <Select
                style={{ width: 160 }}
                options={Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
            <Form.Item name="title" label="标题" rules={[{ required: true, max: 120 }]}>
              <Input style={{ width: 320 }} />
            </Form.Item>
          </Space>
          <Form.Item name="summary" label="摘要（≤200 字）" rules={[{ max: 200 }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Space size="middle" wrap>
            <Form.Item
              name="articleId"
              label="社区文章 ID"
              rules={[{ required: true }]}
              extra="正文与审核真源在社区；一篇帖子只挂一个教程"
            >
              <Input style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="relatedSoftwareIds" label="关联软件 ID（≤3）">
              <Select
                mode="tags"
                style={{ width: 260 }}
                open={false}
                suffixIcon={null}
                onChange={(values: string[]) =>
                  form.setFieldValue(
                    'relatedSoftwareIds',
                    values.map((v) => v.trim()).filter(Boolean),
                  )
                }
              />
            </Form.Item>
          </Space>
          <Space size="middle" wrap>
            <Form.Item name="platformScope" label="适用平台">
              <Select
                mode="multiple"
                style={{ width: 280 }}
                placeholder="不选=不限"
                options={['WINDOWS', 'MACOS', 'LINUX', 'WEB'].map((v) => ({ value: v, label: v }))}
              />
            </Form.Item>
            <Form.Item name="appliesToVersion" label="适用版本范围" rules={[{ max: 60 }]}>
              <Input style={{ width: 180 }} />
            </Form.Item>
          </Space>
          <Space size="middle" wrap>
            <Form.Item name="checkedDate" label="内容检查日期">
              <DatePicker />
            </Form.Item>
            <Form.Item name="checkNote" label="检查说明" rules={[{ max: 200 }]}>
              <Input style={{ width: 320 }} />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Select
                style={{ width: 100 }}
                options={[
                  { value: true, label: '启用' },
                  { value: false, label: '停用' },
                ]}
              />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  )
}
