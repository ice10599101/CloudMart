import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  closePetEventOccurrence,
  listPetConfigs,
  listPetEventOccurrences,
  publishPetEventOccurrence,
  stopPetEventOccurrenceClaim,
  stopPetEventOccurrenceCounting,
  togglePetConfig,
  upsertPetConfig,
  type AdminPetEventOccurrence,
} from '@/api/admin/pet'
import type { ConfigDef } from '../types'

const { Text } = Typography

/** R33/V66 活动期次管理弹层：发布新期次 + 分段控制（停止计数/停止领奖/关闭） */
function OccurrenceModal({ code, onClose }: { code: string; onClose: () => void }) {
  const [messageApi, contextHolder] = message.useMessage()
  const [rows, setRows] = useState<AdminPetEventOccurrence[]>([])
  const [loading, setLoading] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [acting, setActing] = useState<string | null>(null)
  const [form] = Form.useForm()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await listPetEventOccurrences(code)
      if (res.success) setRows(res.data ?? [])
    } finally {
      setLoading(false)
    }
  }, [code])

  useEffect(() => {
    void load()
  }, [load])

  const publish = async () => {
    const values = await form.validateFields()
    setPublishing(true)
    try {
      const { data: res } = await publishPetEventOccurrence(code, values)
      if (res.success) {
        messageApi.success(`已发布第 ${res.data?.occurrenceIndex} 期`)
        form.resetFields()
        await load()
      } else {
        messageApi.error(res.error?.message ?? '发布失败')
      }
    } finally {
      setPublishing(false)
    }
  }

  const act = async (id: number | string, action: 'stop-counting' | 'stop-claim' | 'close', label: string) => {
    setActing(`${action}:${id}`)
    try {
      const caller = action === 'close' ? closePetEventOccurrence
        : action === 'stop-counting' ? stopPetEventOccurrenceCounting
          : stopPetEventOccurrenceClaim
      const { data: res } = await caller(id)
      if (res.success) {
        messageApi.success(`${label}成功`)
        await load()
      } else {
        messageApi.error(res.error?.message ?? `${label}失败`)
      }
    } finally {
      setActing(null)
    }
  }

  return (
    <Modal title={`活动期次 · ${code}`} open onCancel={onClose} footer={null} width={860} destroyOnClose>
      {contextHolder}
      <Card size="small" title="发布新期次" style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" initialValues={{ graceHours: 24 }}>
          <Form.Item name="startAt" label="窗口开始" rules={[{ required: true, message: '必填' }]}>
            <Input placeholder="2026-10-05T00:00:00" style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="endAt" label="窗口结束" rules={[{ required: true, message: '必填' }]}>
            <Input placeholder="2026-10-06T00:00:00" style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="graceHours" label="领奖宽限(h)">
            <InputNumber min={1} max={168} style={{ width: 100 }} />
          </Form.Item>
          <Button type="primary" loading={publishing} onClick={() => void publish()}>
            发布
          </Button>
        </Form>
        <Text type="secondary" style={{ fontSize: 12 }}>
          时间为 UTC（ISO 串）；奖励快照按当前配置冻结；同一活动同时至多一个进行中期次
        </Text>
      </Card>
      <Table
        rowKey={(r) => String(r.id)}
        size="small"
        loading={loading}
        pagination={false}
        dataSource={rows}
        columns={[
          { title: '期号', dataIndex: 'occurrenceIndex', width: 70 },
          { title: '窗口开始', dataIndex: 'startAt', width: 160 },
          { title: '窗口结束', dataIndex: 'endAt', width: 160 },
          { title: '领奖截止', dataIndex: 'claimDeadlineAt', width: 160 },
          {
            title: '状态',
            dataIndex: 'status',
            width: 150,
            render: (v: string, row) => (
              <Space size={4}>
                <Tag color={v === 'ACTIVE' ? 'green' : 'default'}>{v}</Tag>
                {row.countingStoppedAt && <Tag color="orange">已停计数</Tag>}
                {row.claimStoppedAt && <Tag color="red">已停领奖</Tag>}
              </Space>
            ),
          },
          {
            title: '操作',
            key: 'ops',
            width: 240,
            render: (_, row) =>
              row.status === 'ACTIVE' ? (
                <Space size={4}>
                  {!row.countingStoppedAt && (
                    <Button size="small" loading={acting === `stop-counting:${row.id}`}
                      onClick={() => void act(row.id, 'stop-counting', '停止计数')}>
                      停止计数
                    </Button>
                  )}
                  {!row.claimStoppedAt && (
                    <Button size="small" danger loading={acting === `stop-claim:${row.id}`}
                      onClick={() => void act(row.id, 'stop-claim', '停止领奖')}>
                      停止领奖
                    </Button>
                  )}
                  <Popconfirm title="确认关闭该期次？" onConfirm={() => void act(row.id, 'close', '关闭')}>
                    <Button size="small" danger>关闭</Button>
                  </Popconfirm>
                </Space>
              ) : (
                <span style={{ color: '#999' }}>—</span>
              ),
          },
        ]}
      />
    </Modal>
  )
}

/** 宠物内容配置通用 CRUD（描述驱动：表格列 = 字段描述，表单控件由 type 决定） */
export function ConfigPanel({ def }: { def: ConfigDef }) {
  const [messageApi, contextHolder] = message.useMessage()
  const [rows, setRows] = useState<Record<string, unknown>[]>([])
  // R33：社区活动面板的期次管理弹层（发布/停止计数/停止领奖/关闭）
  const [occurrenceCode, setOccurrenceCode] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Record<string, unknown> | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await listPetConfigs(def.key)
      if (res.success) {
        setRows(res.data || [])
      }
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false)
    }
  }, [def.key])

  useEffect(() => {
    void load()
  }, [load])

  const openEditor = (row?: Record<string, unknown>) => {
    setEditing(row ?? null)
    form.resetFields()
    if (row) {
      form.setFieldsValue(row)
    } else {
      form.setFieldsValue({ enabled: true, sort: 0, requiredLevel: 1 })
    }
    setOpen(true)
  }

  const submit = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const payload = { ...(editing ?? {}), ...values }
      const { data: res } = await upsertPetConfig(def.key, payload)
      if (res.success) {
        messageApi.success(editing ? '已更新' : '已新增')
        setOpen(false)
        void load()
      }
    } catch {
      // 校验失败或后端拒绝（错误码会在拦截器提示）
    } finally {
      setSaving(false)
    }
  }

  const toggle = async (row: Record<string, unknown>, enabled: boolean) => {
    try {
      const { data: res } = await togglePetConfig(def.key, String(row.id), enabled)
      if (res.success) {
        messageApi.success(enabled ? '已启用' : '已停用')
        void load()
      }
    } catch {
      // 拦截器已提示
    }
  }

  const columns: ColumnsType<Record<string, unknown>> = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    ...def.fields.slice(0, 5).map((field) => ({
      title: field.label,
      dataIndex: field.name,
      ellipsis: true,
      render: (value: unknown) => (value === null || value === undefined ? '-' : String(value)),
    })),
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (value: unknown) => (value ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      width: 210,
      render: (_: unknown, row) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEditor(row)}>
            编辑
          </Button>
          <Popconfirm
            title={row.enabled ? '确认停用？' : '确认启用？'}
            onConfirm={() => toggle(row, !row.enabled)}
          >
            <Button type="link" size="small" danger={Boolean(row.enabled)}>
              {row.enabled ? '停用' : '启用'}
            </Button>
          </Popconfirm>
          {def.key === 'configs/events' && (
            <Button type="link" size="small" onClick={() => setOccurrenceCode(String(row.code ?? ''))}>
              期次
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      {contextHolder}
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" onClick={() => openEditor()}>
          新增{def.label}
        </Button>
        <Text type="secondary">数值由服务端权威计算，后台只负责录入；带 ID 保存即为更新</Text>
      </Space>
      <Table
        rowKey={(row) => String(row.id)}
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 10, showSizeChanger: false }}
        scroll={{ x: 900 }}
      />
      <Modal
        title={`${editing ? '编辑' : '新增'}${def.label}`}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={submit}
        confirmLoading={saving}
        width={620}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            {def.fields.map((field) => (
              <Col span={12} key={field.name}>
                <Form.Item
                  name={field.name}
                  label={field.tip ? <Tooltip title={field.tip}>{field.label}</Tooltip> : field.label}
                  rules={field.required ? [{ required: true, message: `请填写${field.label}` }] : undefined}
                >
                  {field.type === 'number' ? (
                    <InputNumber style={{ width: '100%' }} />
                  ) : field.type === 'textarea' ? (
                    <Input.TextArea rows={2} />
                  ) : field.type === 'switch' ? (
                    <Switch />
                  ) : field.type === 'select' ? (
                    <Select
                      allowClear
                      options={(field.options ?? []).map((option) => ({
                        value: option,
                        label: option || '（不限）',
                      }))}
                    />
                  ) : (
                    <Input />
                  )}
                </Form.Item>
              </Col>
            ))}
          </Row>
        </Form>
      </Modal>
      {occurrenceCode !== null && (
        <OccurrenceModal code={occurrenceCode} onClose={() => setOccurrenceCode(null)} />
      )}
    </div>
  )
}
