import { useRef, useState } from 'react'
import type { ActionType } from '@ant-design/pro-components'
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from 'antd'
import {
  deleteHomeSection,
  listHomeSections,
  previewHomeSections,
  reorderHomeSections,
  saveHomeSection,
  toggleHomeSection,
  type AdminHomeSection,
} from '@/api/admin/software'
import { useMessage } from '@/utils/useMessage'

const KIND_LABEL: Record<string, string> = {
  SPOTLIGHT: '重点推荐',
  SCENARIOS: '场景入口',
  SOFTWARE_GRID: '软件网格',
  RANKING_LIST: '榜单',
  UPDATE_FEED: '更新流',
  GUIDE_LIST: '教程',
}

const SOURCE_LABEL: Record<string, string> = {
  MANUAL: '手动条目',
  FEATURED: '精选',
  LATEST: '最新',
  RANKING: '热度',
  TOPIC: '专题',
  GUIDE: '教程',
}

interface ItemRow {
  targetType: 'SOFTWARE' | 'TOPIC' | 'GUIDE' | 'FILTER'
  targetId: string | null
  filterPreset: string | null
  headline: string | null
  editorNote: string | null
  displayOrder: number
}

/**
 * 首页内容管理（UX01）：固定区块 + 条目 + 排序 + 投放期 + 预览。
 * 保存/启停/删除用幂等语义；重排提交完整集合（后端原子校验）；
 * 不引入配置版本号。预览含未启用内容（LIVE/OFFLINE 标记）。
 */
export default function SoftwareHomeContent() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [sections, setSections] = useState<AdminHomeSection[]>([])
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState<{ sectionId?: string } | null>(null)
  const [items, setItems] = useState<ItemRow[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewRows, setPreviewRows] = useState<AdminHomeSection[]>([])
  const [form] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const res = await listHomeSections()
      if (res.data.success) setSections(res.data.data ?? [])
    } finally {
      setLoading(false)
    }
  }

  const openCreate = () => {
    setEditing({})
    setItems([])
    form.setFieldsValue({
      slotKey: '',
      title: '',
      subtitle: '',
      kind: 'SOFTWARE_GRID',
      dataSource: 'MANUAL',
      enabled: false,
      displayOrder: 0,
      limitCount: 8,
      minItems: 0,
      fallbackPolicy: 'HIDE',
      platformScope: [],
      range: null,
    })
  }

  const openEdit = (section: AdminHomeSection) => {
    setEditing({ sectionId: section.id })
    setItems(
      (section.items ?? []).map((it, i) => ({
        targetType: it.targetType,
        targetId: it.targetId,
        filterPreset: it.filterPreset,
        headline: it.headline,
        editorNote: it.editorNote,
        displayOrder: it.displayOrder ?? i,
      })),
    )
    form.setFieldsValue({
      slotKey: section.slotKey,
      title: section.title,
      subtitle: section.subtitle,
      kind: section.kind,
      dataSource: section.dataSource,
      enabled: section.enabled,
      displayOrder: section.displayOrder,
      limitCount: section.limitCount,
      minItems: section.minItems,
      fallbackPolicy: section.fallbackPolicy,
      platformScope: section.platformScope,
    })
  }

  const submit = async () => {
    const values = await form.validateFields()
    setSubmitting(true)
    try {
      const res = await saveHomeSection(editing?.sectionId ?? null, {
        slotKey: values.slotKey,
        title: values.title,
        subtitle: values.subtitle || null,
        kind: values.kind,
        dataSource: values.dataSource,
        enabled: values.enabled,
        displayOrder: values.displayOrder,
        limitCount: values.limitCount,
        minItems: values.minItems,
        fallbackPolicy: values.fallbackPolicy,
        platformScope: values.platformScope,
        startAt: null,
        endAt: null,
        items: values.dataSource === 'MANUAL'
          ? items.map((it, i) => ({ ...it, displayOrder: i }))
          : undefined,
      })
      if (res.data.success) {
        message.success('已保存')
        setEditing(null)
        await load()
        actionRef.current?.reload()
      }
    } finally {
      setSubmitting(false)
    }
  }

  const move = async (index: number, delta: number) => {
    const ids = sections.map((s) => s.id)
    const target = index + delta
    if (target < 0 || target >= ids.length) return
    ;[ids[index], ids[target]] = [ids[target], ids[index]]
    const res = await reorderHomeSections(ids)
    if (res.data.success) await load()
  }

  const openPreview = async () => {
    const res = await previewHomeSections('WEB')
    if (res.data.success) {
      setPreviewRows(res.data.data ?? [])
      setPreviewOpen(true)
    }
  }

  const columns = [
    { title: '顺序', dataIndex: 'displayOrder', width: 64 },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      render: (enabled: boolean) =>
        enabled ? <Tag color="success">启用</Tag> : <Tag>停用</Tag>,
    },
    { title: '标题', dataIndex: 'title', width: 160 },
    { title: '槽位键', dataIndex: 'slotKey', width: 140 },
    {
      title: '类型/来源',
      width: 150,
      render: (_: unknown, row: AdminHomeSection) =>
        `${KIND_LABEL[row.kind] ?? row.kind} / ${SOURCE_LABEL[row.dataSource] ?? row.dataSource}`,
    },
    {
      title: '平台范围',
      dataIndex: 'platformScope',
      width: 120,
      render: (scope: string[]) => (scope.length ? scope.join(' / ') : '全部端'),
    },
    {
      title: '操作',
      width: 260,
      render: (_: unknown, row: AdminHomeSection, index: number) => (
        <Space size="small" wrap>
          <Button size="small" onClick={() => openEdit(row)}>
            编辑
          </Button>
          <Button
            size="small"
            onClick={() =>
              toggleHomeSection(row.id, !row.enabled).then(() => load())
            }
          >
            {row.enabled ? '停用' : '启用'}
          </Button>
          <Button size="small" disabled={index === 0} onClick={() => move(index, -1)}>
            上移
          </Button>
          <Button
            size="small"
            disabled={index === sections.length - 1}
            onClick={() => move(index, 1)}
          >
            下移
          </Button>
          <Popconfirm
            title="确认删除该区块？"
            onConfirm={() => deleteHomeSection(row.id).then(() => load())}
          >
            <Button size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={openCreate}>
          新增区块
        </Button>
        <Button onClick={openPreview}>预览首页</Button>
        <Button onClick={() => load()}>刷新</Button>
      </Space>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns as never}
        dataSource={sections}
        pagination={false}
      />

      <Modal
        title={editing?.sectionId ? '编辑区块' : '新增区块'}
        open={!!editing}
        onCancel={() => setEditing(null)}
        onOk={() => void submit()}
        confirmLoading={submitting}
        width={720}
        okText="保存"
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="middle" wrap>
            <Form.Item
              name="slotKey"
              label="槽位键"
              rules={[
                { required: true },
                { pattern: /^[a-z0-9-]{2,40}$/, message: '小写字母/数字/连字符' },
              ]}
            >
              <Input style={{ width: 200 }} />
            </Form.Item>
            <Form.Item name="title" label="标题" rules={[{ required: true, max: 60 }]}>
              <Input style={{ width: 200 }} />
            </Form.Item>
            <Form.Item name="subtitle" label="副标题" rules={[{ max: 120 }]}>
              <Input style={{ width: 200 }} />
            </Form.Item>
          </Space>
          <Space size="middle" wrap>
            <Form.Item name="kind" label="区块类型" rules={[{ required: true }]}>
              <Select
                style={{ width: 160 }}
                options={Object.entries(KIND_LABEL).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
            <Form.Item name="dataSource" label="数据来源" rules={[{ required: true }]}>
              <Select
                style={{ width: 140 }}
                options={Object.entries(SOURCE_LABEL).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="fallbackPolicy" label="内容不足">
              <Select
                style={{ width: 120 }}
                options={[
                  { value: 'HIDE', label: '隐藏区块' },
                  { value: 'EMPTY', label: '显示空态' },
                ]}
              />
            </Form.Item>
          </Space>
          <Space size="middle" wrap>
            <Form.Item name="limitCount" label="条目上限(1~20)">
              <InputNumber min={1} max={20} />
            </Form.Item>
            <Form.Item name="minItems" label="最少条目">
              <InputNumber min={0} max={20} />
            </Form.Item>
            <Form.Item name="platformScope" label="平台范围">
              <Select
                mode="multiple"
                style={{ width: 220 }}
                placeholder="不选=全部端"
                options={[
                  { value: 'WEB', label: 'Web' },
                  { value: 'APP', label: 'App' },
                  { value: 'TARO', label: '小程序' },
                ]}
              />
            </Form.Item>
          </Space>

          <Form.Item noStyle shouldUpdate={(a, b) => a.dataSource !== b.dataSource}>
            {({ getFieldValue }) =>
              getFieldValue('dataSource') === 'MANUAL' ? (
                <div>
                  <Space style={{ marginBottom: 8 }}>
                    <Button
                      size="small"
                      onClick={() =>
                        setItems([
                          ...items,
                          {
                            targetType: 'SOFTWARE',
                            targetId: null,
                            filterPreset: null,
                            headline: null,
                            editorNote: null,
                            displayOrder: items.length,
                          },
                        ])
                      }
                    >
                      添加软件条目
                    </Button>
                    <Button
                      size="small"
                      onClick={() =>
                        setItems([
                          ...items,
                          {
                            targetType: 'TOPIC',
                            targetId: null,
                            filterPreset: null,
                            headline: null,
                            editorNote: null,
                            displayOrder: items.length,
                          },
                        ])
                      }
                    >
                      添加专题条目
                    </Button>
                  </Space>
                  <Table
                    rowKey={(r) => `${r.targetType}-${r.targetId ?? r.filterPreset}-${r.displayOrder}`}
                    size="small"
                    dataSource={items}
                    pagination={false}
                    columns={[
                      {
                        title: '类型',
                        dataIndex: 'targetType',
                        width: 110,
                        render: (v: string) =>
                          v === 'SOFTWARE' ? '软件' : v === 'TOPIC' ? '专题' : v,
                      },
                      {
                        title: '目标ID',
                        dataIndex: 'targetId',
                        width: 170,
                        render: (v: string | null, _row: ItemRow, i: number) => (
                          <Input
                            size="small"
                            value={v ?? ''}
                            onChange={(e) => {
                              const next = [...items];
                              next[i] = { ...next[i], targetId: e.target.value || null };
                              setItems(next);
                            }}
                          />
                        ),
                      },
                      {
                        title: '运营标题',
                        dataIndex: 'headline',
                        render: (v: string | null, _row: ItemRow, i: number) => (
                          <Input
                            size="small"
                            value={v ?? ''}
                            maxLength={60}
                            onChange={(e) => {
                              const next = [...items];
                              next[i] = { ...next[i], headline: e.target.value || null };
                              setItems(next);
                            }}
                          />
                        ),
                      },
                      {
                        title: '编辑推荐理由',
                        dataIndex: 'editorNote',
                        render: (v: string | null, _row: ItemRow, i: number) => (
                          <Input
                            size="small"
                            value={v ?? ''}
                            maxLength={200}
                            onChange={(e) => {
                              const next = [...items];
                              next[i] = { ...next[i], editorNote: e.target.value || null };
                              setItems(next);
                            }}
                          />
                        ),
                      },
                      {
                        title: '',
                        width: 60,
                        render: (_: unknown, _row: ItemRow, i: number) => (
                          <Button
                            size="small"
                            type="text"
                            danger
                            onClick={() => setItems(items.filter((_, j) => j !== i))}
                          >
                            删
                          </Button>
                        ),
                      },
                    ] as never}
                  />
                </div>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="首页预览（Web 端，含未启用内容）"
        open={previewOpen}
        onCancel={() => setPreviewOpen(false)}
        footer={null}
        width={640}
      >
        {previewRows.map((row) => (
          <div key={row.id} style={{ marginBottom: 12 }}>
            <Space>
              <Tag color={row.state === 'LIVE' ? 'success' : 'default'}>
                {row.state === 'LIVE' ? '生效中' : '未生效'}
              </Tag>
              <strong>{row.title}</strong>
              <Tag>{KIND_LABEL[row.kind] ?? row.kind}</Tag>
              <Tag>{SOURCE_LABEL[row.dataSource] ?? row.dataSource}</Tag>
              <span>{(row.items ?? []).length} 条</span>
            </Space>
          </div>
        ))}
      </Modal>
    </div>
  )
}
