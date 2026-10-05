import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  adjustUserPet,
  approvePetAlbumAsset,
  compensateUser,
  deletePetSensitiveWord,
  listPetSensitiveWords,
  listPetConfigGovernanceHistory,
  PET_CONFIG_GOVERNANCE_TYPES,
  rollbackPetConfigGovernance,
  upsertPetSensitiveWord,
  validatePetConfigGovernance,
  type AdminPetConfigVersion,
  type AdminPetSensitiveWord,
  listPetPersonaPhrases,
  listPetSeasons,
  listPetSeasonRewards,
  savePetSeasonRewards,
  settlePetSeason,
  upsertPetPersonaPhrase,
  upsertPetSeason,
  type AdminPetPersonaPhrase,
  type AdminPetSeason,
  type AdminPetSeasonReward,
  getPetDashboard,
  getPetReports,
  getUserPets,
  listPetConfigs,
  listPetEventOccurrences,
  cancelPetQuestInstance,
  listPetQuestReceipts,
  replayPetQuestReceipt,
  listPetWallMessages,
  publishPetEventOccurrence,
  resolvePetReport,
  stopPetEventOccurrenceClaim,
  stopPetEventOccurrenceCounting,
  closePetEventOccurrence,
  togglePetConfig,
  updatePetWallMessageStatus,
  upsertPetConfig,
  type AdminPetEventOccurrence,
  type AdminPetQuestReceipt,
  type AdminPetReport,
  type AdminUserPet,
  type PetConfigType,
  type PetDashboard,
} from '@/api/admin/pet'

const { Text, Paragraph } = Typography

/**
 * 宠物运营后台（社区宠物模块三期）。
 *
 * 三个 Tab：数据看板（运营指标与趋势）/ 配置管理（岗位·课程·装备·皮肤·技能·进化·活动·职业·家具·每日任务，
 * 统一"带 id 为更新"的通用 CRUD）/ 留言审核（隐藏·恢复·删除，保留审计）。
 * 图表沿用工程既有做法（无图表库，用 div 自绘柱状），避免引入新依赖。
 */

/** 配置字段描述（同一条描述驱动表格列与表单控件，新增配置类型只加一行描述） */
interface FieldDef {
  name: string
  label: string
  type?: 'text' | 'textarea' | 'number' | 'switch' | 'select'
  options?: string[]
  required?: boolean
  tip?: string
}

/** 配置类型描述 */
interface ConfigDef {
  key: PetConfigType
  label: string
  fields: FieldDef[]
}

const CONFIGS: ConfigDef[] = [
  {
    key: 'configs/jobs',
    label: '打工岗位',
    fields: [
      { name: 'name', label: '岗位名', required: true },
      { name: 'description', label: '描述', type: 'textarea' },
      { name: 'durationSeconds', label: '耗时(秒)', type: 'number', required: true },
      { name: 'energyCost', label: '精力消耗', type: 'number' },
      { name: 'hungerCost', label: '饥饿消耗', type: 'number' },
      { name: 'expReward', label: '经验奖励', type: 'number' },
      { name: 'currencyReward', label: '星光奖励', type: 'number' },
      { name: 'requiredLevel', label: '等级要求', type: 'number' },
      { name: 'sort', label: '排序', type: 'number' },
    ],
  },
  {
    key: 'configs/studies',
    label: '读书课程',
    fields: [
      { name: 'name', label: '课程名', required: true },
      { name: 'description', label: '描述', type: 'textarea' },
      { name: 'category', label: '分类' },
      { name: 'durationSeconds', label: '耗时(秒)', type: 'number', required: true },
      { name: 'energyCost', label: '精力消耗', type: 'number' },
      { name: 'expReward', label: '经验奖励', type: 'number' },
      { name: 'intelligenceReward', label: '智力奖励', type: 'number' },
      { name: 'requiredLevel', label: '等级要求', type: 'number' },
      { name: 'sort', label: '排序', type: 'number' },
    ],
  },
  {
    key: 'configs/equipment',
    label: '装备',
    fields: [
      { name: 'code', label: '编码', required: true },
      { name: 'name', label: '名称', required: true },
      { name: 'description', label: '描述', type: 'textarea' },
      { name: 'slot', label: '部位', type: 'select', options: ['HAT', 'NECKLACE', 'SCARF', 'BACKPACK'], required: true },
      { name: 'icon', label: '图标' },
      { name: 'rarity', label: '稀有度', type: 'select', options: ['COMMON', 'RARE', 'EPIC'] },
      { name: 'priceStarlight', label: '售价星光', type: 'number' },
      { name: 'bonusStrength', label: '力量+', type: 'number' },
      { name: 'bonusIntelligence', label: '智力+', type: 'number' },
      { name: 'bonusAgility', label: '敏捷+', type: 'number' },
      { name: 'bonusCharm', label: '魅力+', type: 'number' },
      { name: 'bonusMaxHp', label: '生命上限+', type: 'number' },
      { name: 'requiredLevel', label: '等级要求', type: 'number' },
      { name: 'requiredEvolutionStage', label: '进化要求', type: 'number' },
      { name: 'sort', label: '排序', type: 'number' },
    ],
  },
  {
    key: 'configs/skins',
    label: '皮肤',
    fields: [
      { name: 'code', label: '编码', required: true },
      { name: 'name', label: '名称', required: true },
      { name: 'description', label: '描述', type: 'textarea' },
      { name: 'species', label: '限定种类', type: 'select', options: ['', 'CAT', 'DOG', 'RABBIT', 'FOX', 'PANDA'] },
      { name: 'color', label: '主色', required: true },
      { name: 'accessory', label: '配饰' },
      { name: 'icon', label: '图标' },
      { name: 'rarity', label: '稀有度', type: 'select', options: ['COMMON', 'RARE', 'EPIC'] },
      { name: 'priceStarlight', label: '售价星光', type: 'number' },
      { name: 'requiredLevel', label: '等级要求', type: 'number' },
      { name: 'requiredEvolutionStage', label: '进化要求', type: 'number' },
      { name: 'sort', label: '排序', type: 'number' },
    ],
  },
  {
    key: 'configs/skills',
    label: '技能',
    fields: [
      { name: 'code', label: '编码', required: true },
      { name: 'name', label: '名称', required: true },
      { name: 'description', label: '描述', type: 'textarea' },
      { name: 'skillType', label: '类型', type: 'select', options: ['ACTIVE', 'PASSIVE'] },
      {
        name: 'effect',
        label: '效果',
        type: 'select',
        options: ['POWER_STRIKE', 'LUCKY_FISH', 'QUICK_STEP', 'BOOKWORM', 'CHARM_AURA', 'TOUGH_BODY'],
        tip: '必须是服务端已实现的效果，否则技能无效',
      },
      { name: 'effectValue', label: '效果数值', type: 'number' },
      { name: 'icon', label: '图标' },
      { name: 'priceStarlight', label: '售价星光', type: 'number' },
      { name: 'requiredLevel', label: '等级要求', type: 'number' },
      { name: 'sort', label: '排序', type: 'number' },
    ],
  },
  {
    key: 'configs/evolutions',
    label: '进化',
    fields: [
      { name: 'code', label: '编码', required: true },
      { name: 'name', label: '名称', required: true },
      { name: 'description', label: '描述', type: 'textarea' },
      { name: 'stageFrom', label: '起始阶段', type: 'number', required: true },
      { name: 'stageTo', label: '目标阶段', type: 'number', required: true },
      { name: 'requiredLevel', label: '等级要求', type: 'number', required: true },
      { name: 'costStarlight', label: '消耗星光', type: 'number' },
      { name: 'bonusMaxHp', label: '生命+', type: 'number' },
      { name: 'bonusStrength', label: '力量+', type: 'number' },
      { name: 'bonusIntelligence', label: '智力+', type: 'number' },
      { name: 'bonusAgility', label: '敏捷+', type: 'number' },
      { name: 'bonusCharm', label: '魅力+', type: 'number' },
      { name: 'unlockSkinCode', label: '解锁皮肤编码' },
      { name: 'icon', label: '图标' },
      { name: 'sort', label: '排序', type: 'number' },
    ],
  },
  {
    key: 'configs/events',
    label: '社区活动',
    fields: [
      { name: 'code', label: '编码', required: true },
      { name: 'name', label: '名称', required: true },
      { name: 'description', label: '描述', type: 'textarea' },
      {
        name: 'eventType',
        label: '统计口径',
        type: 'select',
        options: ['BOTTLE', 'BATTLE', 'WORK', 'STUDY', 'FEED', 'PLAY', 'VISIT'],
      },
      { name: 'targetValue', label: '目标次数', type: 'number', required: true },
      { name: 'rewardStarlight', label: '奖励星光', type: 'number' },
      { name: 'rewardExp', label: '奖励经验', type: 'number' },
      { name: 'rewardItemCode', label: '奖励物品编码' },
      { name: 'sort', label: '排序', type: 'number' },
    ],
  },
  {
    key: 'careers',
    label: '宠物职业',
    fields: [
      { name: 'code', label: '编码', required: true },
      { name: 'name', label: '职业名', required: true },
      { name: 'description', label: '描述', type: 'textarea' },
      { name: 'careerLine', label: '职业路线', required: true },
      { name: 'tier', label: '阶段', type: 'number' },
      { name: 'icon', label: '图标' },
      { name: 'requiredLevel', label: '入职等级', type: 'number' },
      { name: 'requiredIntelligence', label: '入职智力', type: 'number' },
      { name: 'durationSeconds', label: '工作耗时(秒)', type: 'number', required: true },
      { name: 'energyCost', label: '精力消耗', type: 'number' },
      { name: 'hungerCost', label: '饥饿消耗', type: 'number' },
      { name: 'expReward', label: '经验奖励', type: 'number' },
      { name: 'currencyReward', label: '星光奖励', type: 'number' },
      { name: 'promoteToCode', label: '晋升目标编码', tip: '留空表示该路线最高阶' },
      { name: 'promoteRequiredCount', label: '晋升所需次数', type: 'number' },
      { name: 'promoteStarCost', label: '晋升消耗星光', type: 'number' },
      { name: 'sort', label: '排序', type: 'number' },
    ],
  },
  {
    key: 'furniture',
    label: '家具',
    fields: [
      { name: 'code', label: '编码', required: true },
      { name: 'name', label: '名称', required: true },
      { name: 'description', label: '描述', type: 'textarea' },
      {
        name: 'category',
        label: '分类',
        type: 'select',
        options: ['WALL', 'FLOOR', 'FURNITURE', 'PLANT', 'TOY', 'BED'],
        required: true,
      },
      { name: 'icon', label: '图标' },
      { name: 'rarity', label: '稀有度', type: 'select', options: ['COMMON', 'RARE', 'EPIC'] },
      { name: 'priceStarlight', label: '售价星光', type: 'number' },
      { name: 'requiredLevel', label: '等级要求', type: 'number' },
      { name: 'comfort', label: '舒适度', type: 'number', tip: '房间舒适度 = 已摆放家具之和' },
      { name: 'sort', label: '排序', type: 'number' },
    ],
  },
  {
    key: 'configs/foods',
    label: '食物',
    fields: [
      { name: 'code', label: '编码', required: true },
      { name: 'name', label: '名称', required: true },
      { name: 'description', label: '描述', type: 'textarea' },
      { name: 'icon', label: '图标' },
      { name: 'priceStarlight', label: '售价星光', type: 'number', required: true },
      { name: 'hunger', label: '饱食+', type: 'number', required: true },
      { name: 'happiness', label: '心情+', type: 'number', required: true },
      { name: 'hp', label: '生命+', type: 'number', required: true },
      { name: 'sort', label: '排序', type: 'number' },
    ],
  },
  {
    key: 'daily-quests',
    label: '每日任务',
    fields: [
      { name: 'code', label: '编码', required: true },
      { name: 'name', label: '任务名', required: true },
      { name: 'description', label: '描述', type: 'textarea' },
      { name: 'icon', label: '图标' },
      {
        name: 'questType',
        label: '统计口径',
        type: 'select',
        options: [
          'FEED', 'PLAY', 'CLEAN', 'REST', 'WORK', 'STUDY', 'BOTTLE', 'BATTLE',
          'VISIT', 'CHAT', 'CAREER_WORK', 'FRIEND_VISIT', 'WALL_MESSAGE', 'COMPANION', 'DECORATE',
        ],
        tip: '必须是已埋点口径，否则任务无法完成',
        required: true,
      },
      { name: 'targetValue', label: '目标次数', type: 'number', required: true },
      { name: 'expReward', label: '奖励经验', type: 'number' },
      { name: 'currencyReward', label: '奖励星光', type: 'number' },
      { name: 'requiredLevel', label: '等级要求', type: 'number' },
      { name: 'sort', label: '排序', type: 'number' },
    ],
  },
]

const PET_MANAGE_CSS = `
.pet-admin-trend { display: flex; align-items: flex-end; gap: 4px; height: 120px; }
.pet-admin-trend-bar { flex: 1; min-width: 6px; border-radius: 3px 3px 0 0; background: linear-gradient(180deg, #69c0ff, #1677ff); }
.pet-admin-trend-label { font-size: 10px; color: #8c8c8c; text-align: center; margin-top: 4px; }
`

/** 宠物内容配置通用 CRUD（描述驱动：表格列 = 字段描述，表单控件由 type 决定） */
function ConfigPanel({ def }: { def: ConfigDef }) {
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
      const { data: res } = await togglePetConfig(def.key, Number(row.id), enabled)
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

/** R32 任务事件回执面板：收据查询 + SKIPPED_STALE 重放补算（只允许重放已有事实） */
function QuestReceiptPanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const [rows, setRows] = useState<AdminPetQuestReceipt[]>([])
  const [loading, setLoading] = useState(false)
  const [questCode, setQuestCode] = useState<string | undefined>(undefined)
  const [status, setStatus] = useState<string | undefined>(undefined)
  const [userId, setUserId] = useState<string | undefined>(undefined)
  const [replaying, setReplaying] = useState<number | string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await listPetQuestReceipts({
        questCode, status, userId: userId || undefined, page: 1, size: 20,
      })
      if (res.success) setRows(res.data?.records ?? [])
    } finally {
      setLoading(false)
    }
  }, [questCode, status, userId])

  useEffect(() => {
    void load()
  }, [load])

  const replay = async (row: AdminPetQuestReceipt) => {
    setReplaying(row.id)
    try {
      const { data: res } = await replayPetQuestReceipt(row.id)
      if (res.success) {
        messageApi.success(`已按事实日 ${row.businessDate} 补记 ${row.amount} 次`)
        await load()
      } else {
        messageApi.error(res.error?.message ?? '重放失败')
      }
    } finally {
      setReplaying(null)
    }
  }

  return (
    <div>
      {contextHolder}
      <Space style={{ marginBottom: 12 }} wrap>
        <Input
          allowClear
          placeholder="用户 ID"
          style={{ width: 140 }}
          onChange={(e) => setUserId(e.target.value || undefined)}
        />
        <Select
          allowClear
          placeholder="任务类型"
          style={{ width: 150 }}
          onChange={(v) => setQuestCode(v)}
          options={['FEED', 'PLAY', 'CLEAN', 'REST', 'WORK', 'STUDY', 'BATTLE', 'CHAT',
            'BOTTLE', 'VISIT', 'FRIEND_VISIT', 'CAREER_WORK', 'WALL_MESSAGE', 'COMPANION', 'DECORATE']
            .map((code) => ({ value: code, label: code }))}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 150 }}
          onChange={(v) => setStatus(v)}
          options={[
            { value: 'APPLIED', label: 'APPLIED 已计入' },
            { value: 'SKIPPED_STALE', label: 'SKIPPED_STALE 待补算' },
          ]}
        />
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table
        rowKey={(r) => String(r.id)}
        size="small"
        loading={loading}
        pagination={{ pageSize: 10, showSizeChanger: false }}
        dataSource={rows}
        columns={[
          { title: '回执', dataIndex: 'id', width: 180, render: (v) => String(v) },
          { title: '用户', dataIndex: 'userId', width: 150, render: (v) => String(v) },
          { title: '任务类型', dataIndex: 'questCode', width: 120 },
          { title: '事实键', dataIndex: 'eventId', ellipsis: true },
          { title: '事实时间', dataIndex: 'sourceTime', width: 160 },
          { title: '计入日', dataIndex: 'businessDate', width: 110 },
          { title: '数量', dataIndex: 'amount', width: 70 },
          {
            title: '状态',
            dataIndex: 'status',
            width: 140,
            render: (v: string) => (
              <Tag color={v === 'APPLIED' ? 'green' : 'orange'}>{v === 'APPLIED' ? '已计入' : '待补算'}</Tag>
            ),
          },
          {
            title: '操作',
            key: 'ops',
            width: 100,
            render: (_, row) =>
              row.status === 'SKIPPED_STALE' ? (
                <Button size="small" type="link" loading={replaying === row.id} onClick={() => void replay(row)}>
                  重放
                </Button>
              ) : (
                <span style={{ color: '#999' }}>—</span>
              ),
          },
        ]}
      />
    </div>
  )
}

/** 留言审核面板 */
function WallPanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const [rows, setRows] = useState<Record<string, unknown>[]>([])
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState<string | undefined>(undefined)
  const [petId, setPetId] = useState<number | undefined>(undefined)
  // F6：时间范围筛选 + CSV 导出（后端流式拼装，20 万行上限）
  const [dateRange, setDateRange] = useState<[string, string] | null>(null)

  const exportCsv = () => {
    const params = new URLSearchParams()
    if (dateRange) {
      params.set('from', dateRange[0])
      params.set('to', dateRange[1])
    }
    // 走网关同源下载（携带会话 Cookie；浏览器原生处理文件流）
    window.open(`/api/admin/pet/wall/messages/export?${params.toString()}`, '_blank')
    messageApi.success('已开始导出（大数据量浏览器可能需要数秒）')
  }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await listPetWallMessages({ status, petId, page: 1, size: 50 })
      if (res.success) {
        setRows(res.data || [])
      }
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false)
    }
  }, [status, petId])

  useEffect(() => {
    void load()
  }, [load])

  const changeStatus = async (row: Record<string, unknown>, next: string) => {
    try {
      const { data: res } = await updatePetWallMessageStatus(Number(row.id), next)
      if (res.success) {
        messageApi.success('已更新')
        void load()
      }
    } catch {
      // 拦截器已提示
    }
  }

  const columns: ColumnsType<Record<string, unknown>> = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    { title: '宠物ID', dataIndex: 'petId', width: 90 },
    { title: '作者用户', dataIndex: 'authorUserId', width: 110 },
    { title: '内容', dataIndex: 'content', ellipsis: true },
    { title: '点赞', dataIndex: 'likeCount', width: 70 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: unknown) => {
        const text = String(value)
        const color = text === 'NORMAL' ? 'green' : text === 'HIDDEN' ? 'orange' : 'default'
        const label = text === 'NORMAL' ? '正常' : text === 'HIDDEN' ? '已隐藏' : '已删除'
        return <Tag color={color}>{label}</Tag>
      },
    },
    { title: '时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作',
      width: 200,
      render: (_: unknown, row) => (
        <Space>
          {row.status !== 'HIDDEN' && (
            <Popconfirm title="隐藏这条留言？用户端将立即不可见" onConfirm={() => changeStatus(row, 'HIDDEN')}>
              <Button type="link" size="small" danger>
                隐藏
              </Button>
            </Popconfirm>
          )}
          {row.status !== 'NORMAL' && (
            <Button type="link" size="small" onClick={() => changeStatus(row, 'NORMAL')}>
              恢复
            </Button>
          )}
          {row.status !== 'DELETED' && (
            <Popconfirm title="删除这条留言？（保留审计）" onConfirm={() => changeStatus(row, 'DELETED')}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      {contextHolder}
      <Space style={{ marginBottom: 12 }}>
        <InputNumber
          placeholder="宠物 ID"
          value={petId}
          onChange={(value) => setPetId(value ?? undefined)}
          style={{ width: 140 }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 160 }}
          value={status}
          onChange={setStatus}
          options={[
            { value: 'NORMAL', label: '正常' },
            { value: 'HIDDEN', label: '已隐藏' },
            { value: 'DELETED', label: '已删除' },
          ]}
        />
        <Button type="primary" onClick={() => void load()}>
          查询
        </Button>
        <DatePicker.RangePicker
          onChange={(values) =>
            setDateRange(
              values && values[0] && values[1]
                ? [values[0].format('YYYY-MM-DD'), values[1].format('YYYY-MM-DD')]
                : null,
            )
          }
        />
        <Button onClick={exportCsv}>导出 CSV</Button>
        <Text type="secondary">隐藏后用户端立即不可见，管理端仍保留（可溯源、可恢复）</Text>
      </Space>
      <Table
        rowKey={(row) => String(row.id)}
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 10, showSizeChanger: false }}
        scroll={{ x: 1000 }}
      />
    </div>
  )
}

/** 数据看板面板 */
function DashboardPanel() {
  const [days, setDays] = useState(14)
  const [data, setData] = useState<PetDashboard | null>(null)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await getPetDashboard(days)
      if (res.success && res.data) {
        setData(res.data)
      }
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false)
    }
  }, [days])

  useEffect(() => {
    void load()
  }, [load])

  const trendMax = useMemo(() => {
    if (!data) {
      return 1
    }
    return Math.max(1, ...data.trend.map((point) => point.activePets))
  }, [data])

  if (!data) {
    return <Empty description={loading ? '加载中…' : '暂无数据'} />
  }

  const { overview, distribution, aiUsage } = data

  return (
    <div>
      <style>{PET_MANAGE_CSS}</style>
      <Space style={{ marginBottom: 12 }}>
        <Select
          value={days}
          onChange={setDays}
          style={{ width: 140 }}
          options={[
            { value: 7, label: '近 7 天' },
            { value: 14, label: '近 14 天' },
            { value: 30, label: '近 30 天' },
          ]}
        />
        <Text type="secondary">活跃 = 当日有行为流水的宠物（去重）</Text>
      </Space>

      <Row gutter={[12, 12]}>
        <Col span={4}><Card size="small"><Statistic title="宠物总数" value={overview.totalPets} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="今日新增" value={overview.newPetsToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="今日活跃" value={overview.activePetsToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="7 日活跃" value={overview.activePets7d} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="今日对战" value={overview.battlesToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="今日捞瓶" value={overview.bottlesToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="关系数" value={overview.totalRelations} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="好友数" value={overview.totalFriends} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="家园数" value={overview.totalRooms} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="平均舒适度" value={overview.avgComfort} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="今日任务领取" value={overview.questsClaimedToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="在职宠物" value={overview.careerHired} /></Card></Col>
        {/* P1-8：AI 聊天成本可观测 */}
        <Col span={4}><Card size="small"><Statistic title="今日 AI 回复" value={aiUsage.aiRepliesToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="今日估算 token" value={aiUsage.tokensToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="AI 降级累计" value={aiUsage.fallbackTotal} /></Card></Col>
      </Row>

      <Card size="small" title="近 N 日活跃宠物" style={{ marginTop: 16 }} loading={loading}>
        <div className="pet-admin-trend">
          {data.trend.map((point) => (
            <Tooltip key={point.date} title={`${point.date}：活跃 ${point.activePets} / 行为 ${point.activities} / 对战 ${point.battles} / 留言 ${point.wallMessages} / 串门 ${point.visits}`}>
              <div style={{ flex: 1, textAlign: 'center' }}>
                <div className="pet-admin-trend-bar" style={{ height: `${(point.activePets / trendMax) * 100}px` }} />
                <div className="pet-admin-trend-label">{point.date.slice(5)}</div>
              </div>
            </Tooltip>
          ))}
        </div>
      </Card>

      <Row gutter={[12, 12]} style={{ marginTop: 16 }}>
        <Col span={8}>
          <Card size="small" title="种类分布">
            {distribution.species.length === 0 ? (
              <Empty />
            ) : (
              distribution.species.map((bucket) => (
                <Paragraph key={bucket.name} style={{ marginBottom: 4 }}>
                  <Text>{bucket.name}</Text> <Text type="secondary">{bucket.value} 只</Text>
                </Paragraph>
              ))
            )}
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" title="等级分布">
            {distribution.levelBuckets.map((bucket) => (
              <Paragraph key={bucket.name} style={{ marginBottom: 4 }}>
                <Text>{bucket.name}</Text> <Text type="secondary">{bucket.value} 只</Text>
              </Paragraph>
            ))}
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" title="职业就业 Top">
            {distribution.careers.length === 0 ? (
              <Empty />
            ) : (
              distribution.careers.map((bucket) => (
                <Paragraph key={bucket.name} style={{ marginBottom: 4 }}>
                  <Text>{bucket.name}</Text> <Text type="secondary">{bucket.value} 只</Text>
                </Paragraph>
              ))
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card size="small" title="家具摆放 Top">
            {distribution.topFurniture.length === 0 ? (
              <Empty />
            ) : (
              distribution.topFurniture.map((bucket) => (
                <Paragraph key={bucket.name} style={{ marginBottom: 4 }}>
                  <Text>{bucket.name}</Text> <Text type="secondary">{bucket.value} 件</Text>
                </Paragraph>
              ))
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card size="small" title="亲密度 Top">
            {distribution.topIntimacy.length === 0 ? (
              <Empty />
            ) : (
              distribution.topIntimacy.map((bucket) => (
                <Paragraph key={bucket.name} style={{ marginBottom: 4 }}>
                  <Text>{bucket.name}</Text> <Text type="secondary">{bucket.value}</Text>
                </Paragraph>
              ))
            )}
          </Card>
        </Col>
        <Col span={24}>
          <Card size="small" title="物品购买结构（宠物背包）">
            <Space wrap>
              {Object.entries(overview.purchasesByType).length === 0 && <Text type="secondary">暂无购买记录</Text>}
              {Object.entries(overview.purchasesByType).map(([type, count]) => (
                <Tag key={type} color="blue">
                  {type}: {count}
                </Tag>
              ))}
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  )
}

/** 赛季管理面板（F2：创建/延后/梯度/手动结算） */
function SeasonPanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const [rows, setRows] = useState<AdminPetSeason[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState<AdminPetSeason | null>(null)
  const [creating, setCreating] = useState(false)
  const [form] = Form.useForm<{ name: string; range: [string, string] }>()
  const [saving, setSaving] = useState(false)
  const [tiers, setTiers] = useState<AdminPetSeasonReward[]>([])

  const load = useCallback(
    async (targetPage = page) => {
      setLoading(true)
      try {
        const { data: res } = await listPetSeasons({ page: targetPage, size: 10 })
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

  const openEditor = async (row: AdminPetSeason | null) => {
    setCreating(row === null)
    setEditing(row)
    if (row) {
      form.setFieldsValue({ name: row.name, range: [row.startsAt, row.endsAt] })
      try {
        const { data: res } = await listPetSeasonRewards(row.id)
        if (res.success) {
          setTiers(res.data || [])
        }
      } catch {
        // 拦截器已提示
      }
    } else {
      form.setFieldsValue({ name: '', range: undefined })
      setTiers([{ rankMin: 1, rankMax: 1, rewardStarlight: 100, rewardExp: 0 }])
    }
  }

  const submit = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const { data: res } = await upsertPetSeason({
        id: creating ? undefined : editing?.id,
        name: values.name,
        startsAt: values.range[0],
        endsAt: values.range[1],
      })
      if (res.success && creating && res.data) {
        // 新赛季直接保存梯度
        await savePetSeasonRewards(String(res.data.id), tiers)
      } else if (res.success && editing && !creating) {
        await savePetSeasonRewards(String(editing.id), tiers)
      }
      messageApi.success('已保存')
      setEditing(null)
      setCreating(false)
      void load()
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false)
    }
  }

  const doSettle = (row: AdminPetSeason) => {
    Modal.confirm({
      title: '确认手动结算该赛季？',
      content: `${row.name}：快照最终榜并按梯度发奖（幂等，可重跑）。`,
      okText: '确认结算',
      cancelText: '取消',
      onOk: async () => {
        try {
          const { data: res } = await settlePetSeason(row.id)
          if (res.success) {
            messageApi.success('已结算')
            void load()
          }
        } catch {
          // 拦截器已提示
        }
      },
    })
  }

  const columns: ColumnsType<AdminPetSeason> = [
    { title: '名称', dataIndex: 'name' },
    { title: '开始', dataIndex: 'startsAt', width: 120, render: (v: string) => v?.slice(0, 10) },
    { title: '结束', dataIndex: 'endsAt', width: 120, render: (v: string) => v?.slice(0, 10) },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: AdminPetSeason['status']) => (
        <Tag color={v === 'ACTIVE' ? 'green' : 'default'}>{v === 'ACTIVE' ? '进行中' : '已结算'}</Tag>
      ),
    },
    {
      title: '操作',
      width: 200,
      render: (_: unknown, row) => (
        <Space>
          {row.status === 'ACTIVE' && <Button type="link" size="small" onClick={() => void openEditor(row)}>编辑</Button>}
          {row.status === 'ACTIVE' && (
            <Button type="link" size="small" danger onClick={() => doSettle(row)}>
              结算
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
        <Button
          type="primary"
          onClick={() => {
            void openEditor(null)
          }}
        >
          新建赛季
        </Button>
        <Text type="secondary">进行中赛季唯一；结束时间只允许延后；梯度区间需从第 1 名连续覆盖</Text>
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
      <Modal
        title={creating ? '新建赛季' : '编辑赛季'}
        open={editing !== null || creating}
        onCancel={() => {
          setEditing(null)
          setCreating(false)
        }}
        onOk={() => void submit()}
        confirmLoading={saving}
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="赛季名称" rules={[{ required: true, message: '请填写名称' }]}>
            <Input placeholder="例如：第一届养成大赛" />
          </Form.Item>
          <Form.Item name="range" label="起止时间（UTC）" rules={[{ required: true, message: '请选择区间' }]}>
            <DatePicker.RangePicker showTime style={{ width: '100%' }} />
          </Form.Item>
        </Form>
        <Card size="small" title="奖励梯度（星光/经验，按名次区间）">
          {tiers.map((tier, idx) => (
            <Space key={idx} wrap style={{ marginBottom: 8 }}>
              <InputNumber
                min={1}
                value={tier.rankMin}
                onChange={(v) => setTiers(tiers.map((t, i) => (i === idx ? { ...t, rankMin: v ?? 1 } : t)))}
                addonBefore="第"
                style={{ width: 130 }}
              />
              <InputNumber
                min={1}
                value={tier.rankMax}
                onChange={(v) => setTiers(tiers.map((t, i) => (i === idx ? { ...t, rankMax: v ?? 1 } : t)))}
                addonBefore="至"
                style={{ width: 130 }}
              />
              <InputNumber
                min={0}
                value={tier.rewardStarlight}
                onChange={(v) => setTiers(tiers.map((t, i) => (i === idx ? { ...t, rewardStarlight: v ?? 0 } : t)))}
                addonBefore="星光"
                style={{ width: 150 }}
              />
              <InputNumber
                min={0}
                value={tier.rewardExp}
                onChange={(v) => setTiers(tiers.map((t, i) => (i === idx ? { ...t, rewardExp: v ?? 0 } : t)))}
                addonBefore="经验"
                style={{ width: 150 }}
              />
              {tiers.length > 1 && (
                <Button type="link" danger size="small" onClick={() => setTiers(tiers.filter((_, i) => i !== idx))}>
                  删除
                </Button>
              )}
            </Space>
          ))}
          <Button type="dashed" block onClick={() => setTiers([...tiers, { rankMin: 1, rankMax: 1, rewardStarlight: 0, rewardExp: 0 }])}>
            增加梯度
          </Button>
        </Card>
      </Modal>
    </div>
  )
}

/** 性格文案（与后端 PetPersonality 枚举对齐） */
const PERSONALITY_LABELS: Record<string, string> = {
  LIVELY: '活泼',
  GENTLE: '温柔',
  TSUNDERE: '傲娇',
  SIMPLE: '憨厚',
  COOL: '高冷',
  CHATTERBOX: '话痨',
}

/** 口头禅面板（F8 配置化：按性格编辑，保存后 ≤60 秒同步全部实例） */
function PersonaPhrasePanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const [rows, setRows] = useState<AdminPetPersonaPhrase[]>([])
  const [savingKey, setSavingKey] = useState<string | null>(null)
  const [drafts, setDrafts] = useState<Record<string, string>>({})

  const load = useCallback(async () => {
    try {
      const { data: res } = await listPetPersonaPhrases()
      if (res.success) {
        setRows(res.data || [])
        const initial: Record<string, string> = {}
        for (const row of res.data || []) {
          initial[row.personality] = row.source === 'DB' ? row.phrase : ''
        }
        setDrafts(initial)
      }
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const save = async (personality: string) => {
    const phrase = (drafts[personality] ?? '').trim()
    if (!phrase) {
      messageApi.warning('先填写口头禅（{name} 代表宠物名）')
      return
    }
    setSavingKey(personality)
    try {
      const { data: res } = await upsertPetPersonaPhrase({ personality, phrase })
      if (res.success) {
        messageApi.success('已保存，≤60 秒同步全部实例')
        void load()
      }
    } catch {
      // 拦截器已提示
    } finally {
      setSavingKey(null)
    }
  }

  return (
    <div>
      {contextHolder}
      <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
        口头禅会注入 AI 聊天 prompt 并展示在聊天页人设卡；{'{name}'} 占位宠物名；保存后 60 秒内全部实例生效
      </Text>
      {rows.map((row) => (
        <Card size="small" key={row.personality} style={{ marginBottom: 12 }}>
          <Space wrap style={{ width: '100%' }}>
            <Tag>{PERSONALITY_LABELS[row.personality] ?? row.personality}</Tag>
            <Input
              style={{ width: 360 }}
              placeholder={row.source === 'DEFAULT' ? `出厂默认：${row.phrase}` : row.phrase}
              value={drafts[row.personality] ?? ''}
              onChange={(e) => setDrafts({ ...drafts, [row.personality]: e.target.value })}
            />
            <Button
              type="primary"
              size="small"
              loading={savingKey === row.personality}
              onClick={() => void save(row.personality)}
            >
              保存
            </Button>
            <Text type="secondary">{row.source === 'DB' ? '已自定义' : '出厂默认'}</Text>
          </Space>
        </Card>
      ))}
    </div>
  )
}

/** 敏感词类别文案（与后端枚举对齐） */
const SENSITIVE_CATEGORY_LABELS: Record<string, string> = {
  POLITICS: '政治',
  ABUSE: '辱骂',
  AD: '广告导流',
  CRISIS: '危机干预',
}

/** 敏感词库面板（P0-1 内容安全：增删改 + 停用，生效 ≤1 分钟） */
function SensitiveWordPanel() {
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

/** F5 可调字段（与后端白名单对齐） */
const ADJUSTABLE_FIELDS = [
  { value: 'exp', label: '经验' },
  { value: 'hp', label: '生命' },
  { value: 'hunger', label: '饱食' },
  { value: 'happiness', label: '心情' },
  { value: 'energy', label: '精力' },
  { value: 'cleanliness', label: '清洁' },
  { value: 'strength', label: '力量' },
  { value: 'intelligence', label: '智力' },
  { value: 'agility', label: '敏捷' },
  { value: 'charm', label: '魅力' },
]

const STATUS_TEXT: Record<string, string> = {
  IDLE: '悠闲中',
  WORKING: '打工中',
  STUDYING: '读书中',
  FISHING: '捞瓶中',
  RESTING: '休息中',
}

/** 用户宠物运营面板（F5：搜索用户 → 宠物全貌 → 调账/补偿，全部留审计） */
function UserPanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const [userIdInput, setUserIdInput] = useState<number | undefined>(undefined)
  const [userId, setUserId] = useState<number | undefined>(undefined)
  const [pets, setPets] = useState<AdminUserPet[]>([])
  const [loading, setLoading] = useState(false)
  const [selected, setSelected] = useState<AdminUserPet | null>(null)
  const [adjustForm] = Form.useForm<{ field: string; delta: number; reason: string }>()
  const [compForm] = Form.useForm<{ delta: number; reason: string; ticketNo?: string }>()
  const [cancelForm] = Form.useForm<{ questDate: string; questCode: string; reason: string }>()
  const [submitting, setSubmitting] = useState(false)

  const load = useCallback(async (targetId: number | undefined) => {
    if (!targetId) {
      messageApi.warning('请输入用户 ID')
      return
    }
    setLoading(true)
    try {
      const { data: res } = await getUserPets(targetId)
      if (res.success) {
        setPets(res.data || [])
      }
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false)
    }
  }, [messageApi])

  // §8.2 受审计取消命令：IN_PROGRESS/COMPLETE 可取消，CLAIMED 拒绝（收回走调账链路）
  const submitCancel = async () => {
    if (!selected) {
      messageApi.warning('请先选择宠物')
      return
    }
    const values = await cancelForm.validateFields()
    setSubmitting(true)
    try {
      const { data: res } = await cancelPetQuestInstance(
        selected.petId, values.questDate, values.questCode, values.reason)
      if (res.success) {
        messageApi.success(`已取消 ${values.questDate} 的 ${values.questCode}（审计留痕）`)
        cancelForm.resetFields()
      } else {
        messageApi.error(res.error?.message ?? '取消失败')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const submitAdjust = async () => {
    if (!selected || !userId) {
      return
    }
    const values = await adjustForm.validateFields()
    const label = ADJUSTABLE_FIELDS.find((f) => f.value === values.field)?.label ?? values.field
    Modal.confirm({
      title: '确认调整宠物数值？',
      content: `${selected.name}（#${String(selected.petId)}）的 ${label} 调整 ${values.delta > 0 ? '+' : ''}${values.delta}。操作将留快照审计，不可静默撤销。`,
      okText: '确认调整',
      cancelText: '再想想',
      onOk: async () => {
        setSubmitting(true)
        try {
          const { data: res } = await adjustUserPet(userId, selected.petId, values)
          if (res.success) {
            messageApi.success('已调整并留痕')
            adjustForm.resetFields()
            await load(userId)
            setSelected(null)
          }
        } catch {
          // 拦截器已提示
        } finally {
          setSubmitting(false)
        }
      },
    })
  }

  const submitCompensation = async () => {
    if (!userId) {
      return
    }
    const values = await compForm.validateFields()
    Modal.confirm({
      title: '确认提交补偿申请？',
      content: `为用户 ${String(userId)} 申请 ${values.delta > 0 ? '+' : ''}${values.delta} 宠物币。申请单需另一管理员审批后才会入账。`,
      okText: '提交申请',
      cancelText: '再想想',
      onOk: async () => {
        setSubmitting(true)
        try {
          const { data: res } = await compensateUser(userId, values)
          if (res.success) {
            messageApi.success('补偿申请已提交，等待另一管理员审批')
            compForm.resetFields()
          }
        } catch {
          // 拦截器已提示
        } finally {
          setSubmitting(false)
        }
      },
    })
  }

  return (
    <div>
      {contextHolder}
      <Space style={{ marginBottom: 12 }}>
        <InputNumber
          placeholder="用户 ID"
          value={userIdInput}
          onChange={(value) => setUserIdInput(value ?? undefined)}
          style={{ width: 160 }}
        />
        <Button
          type="primary"
          onClick={() => {
            setUserId(userIdInput)
            void load(userIdInput)
          }}
          loading={loading}
        >
          查询
        </Button>
        <Text type="secondary">数值调整与钱包补偿均留审计/走审批；字段与幅度有服务端白名单限制</Text>
      </Space>
      {!userId && <Empty description="输入用户 ID 开始查询" />}
      {userId && (
        <Row gutter={[12, 12]}>
          {pets.length === 0 && !loading && <Col span={24}><Empty description="该用户没有宠物" /></Col>}
          {pets.map((pet) => (
            <Col span={8} key={String(pet.petId)}>
              <Card
                size="small"
                title={`${pet.name}（Lv.${pet.level}）`}
                extra={
                  <Button type="link" size="small" onClick={() => setSelected(pet)}>
                    详情
                  </Button>
                }
              >
                <Space wrap>
                  <Tag color={pet.isActive ? 'green' : 'default'}>{pet.isActive ? '主宠' : '副宠'}</Tag>
                  <Tag>{pet.species}</Tag>
                  <Tag>{STATUS_TEXT[pet.status ?? ''] ?? pet.status ?? '-'}</Tag>
                  <Text type="secondary">
                    HP {pet.hp}/{pet.maxHp} · 饱食 {pet.hunger} · 心情 {pet.happiness}
                  </Text>
                  <Text type="secondary">
                    钱包 {pet.walletBalance ?? '-'}{pet.walletStatus ? `（${pet.walletStatus}）` : ''}
                  </Text>
                </Space>
              </Card>
            </Col>
          ))}
        </Row>
      )}
      <Drawer
        title={selected ? `${selected.name}（#${String(selected.petId)}）` : '宠物详情'}
        open={selected !== null}
        onClose={() => setSelected(null)}
        width={520}
      >
        {selected && (
          <>
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label="等级">{selected.level}（exp {selected.exp}）</Descriptions.Item>
              <Descriptions.Item label="成长阶段">{selected.growthStage}</Descriptions.Item>
              <Descriptions.Item label="HP">{selected.hp}/{selected.maxHp}</Descriptions.Item>
              <Descriptions.Item label="饱食">{selected.hunger}</Descriptions.Item>
              <Descriptions.Item label="心情">{selected.happiness}</Descriptions.Item>
              <Descriptions.Item label="精力">{selected.energy}</Descriptions.Item>
              <Descriptions.Item label="清洁">{selected.cleanliness}</Descriptions.Item>
              <Descriptions.Item label="四维">
                力{selected.strength} 智{selected.intelligence} 敏{selected.agility} 魅{selected.charm}
              </Descriptions.Item>
              <Descriptions.Item label="钱包余额" span={2}>
                {selected.walletBalance ?? '-'} {selected.walletStatus ? `（${selected.walletStatus}）` : ''}
              </Descriptions.Item>
              <Descriptions.Item label="背包摘要" span={2}>
                {selected.inventorySummary.length === 0
                  ? '空'
                  : selected.inventorySummary
                      .map((line) => `${line.itemCode} x${line.quantity}`)
                      .join('，')}
              </Descriptions.Item>
            </Descriptions>
            <Card size="small" title="数值调整（快照审计）" style={{ marginTop: 16 }}>
              <Form form={adjustForm} layout="vertical">
                <Space wrap>
                  <Form.Item name="field" label="字段" rules={[{ required: true, message: '选择字段' }]}>
                    <Select style={{ width: 120 }} options={ADJUSTABLE_FIELDS} placeholder="字段" />
                  </Form.Item>
                  <Form.Item name="delta" label="变化量（±10000）" rules={[{ required: true, message: '填写变化量' }]}>
                    <InputNumber style={{ width: 140 }} />
                  </Form.Item>
                </Space>
                <Form.Item name="reason" label="调整理由（工单号）" rules={[{ required: true, message: '必填' }]}>
                  <Input.TextArea rows={2} placeholder="例如：工单#123 补偿" />
                </Form.Item>
                <Button type="primary" danger loading={submitting} onClick={() => void submitAdjust()}>
                  提交调整
                </Button>
              </Form>
            </Card>
            <Card size="small" title="钱包补偿申请（需另一管理员审批）" style={{ marginTop: 16 }}>
              <Form form={compForm} layout="vertical">
                <Space wrap>
                  <Form.Item name="delta" label="金额（±10000）" rules={[{ required: true, message: '填写金额' }]}>
                    <InputNumber style={{ width: 140 }} />
                  </Form.Item>
                  <Form.Item name="ticketNo" label="工单号">
                    <Input style={{ width: 160 }} placeholder="可空" />
                  </Form.Item>
                </Space>
                <Form.Item name="reason" label="补偿理由" rules={[{ required: true, message: '必填' }]}>
                  <Input.TextArea rows={2} />
                </Form.Item>
                <Button type="primary" loading={submitting} onClick={() => void submitCompensation()}>
                  提交申请
                </Button>
              </Form>
            </Card>
            <Card size="small" title="取消某日任务实例（§8.2 受审计命令）" style={{ marginTop: 16 }}>
              <Form form={cancelForm} layout="vertical">
                <Space wrap>
                  <Form.Item name="questDate" label="业务日" rules={[{ required: true, message: '如 2026-10-05' }]}>
                    <Input placeholder="2026-10-05" style={{ width: 140 }} />
                  </Form.Item>
                  <Form.Item name="questCode" label="任务编码" rules={[{ required: true, message: '如 daily_feed' }]}>
                    <Input placeholder="daily_feed" style={{ width: 160 }} />
                  </Form.Item>
                </Space>
                <Form.Item name="reason" label="取消原因" rules={[{ required: true, message: '必填，随审计留痕' }]}>
                  <Input.TextArea rows={2} />
                </Form.Item>
                <Button type="primary" danger loading={submitting} onClick={() => void submitCancel()}>
                  取消实例
                </Button>
                <Text type="secondary" style={{ marginLeft: 12, fontSize: 12 }}>
                  已领取（CLAIMED）实例会被拒绝——奖励收回须走调账补偿链路
                </Text>
              </Form>
            </Card>
          </>
        )}
      </Drawer>
    </div>
  )
}

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
function ReportPanel() {
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

// ==================== BE-11 相册审核 / B21 配置治理 ====================

/** 相册审核（按资产 ID 通过）+ 配置治理三件套（校验预览/历史/回退） */
function GovernancePanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const typeOptions = PET_CONFIG_GOVERNANCE_TYPES.map((t) => ({ value: t, label: t }))
  // 相册审核
  const [assetId, setAssetId] = useState<number | null>(null)
  const [approving, setApproving] = useState(false)
  // 校验预览
  const [validateType, setValidateType] = useState<string>('job')
  const [validateJson, setValidateJson] = useState('{\n  "durationSeconds": 300,\n  "expReward": 50\n}')
  const [validating, setValidating] = useState(false)
  // 历史/回退
  const [historyType, setHistoryType] = useState<string>('job')
  const [historyConfigId, setHistoryConfigId] = useState<number | null>(null)
  const [versions, setVersions] = useState<AdminPetConfigVersion[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)

  const handleApprove = async () => {
    if (assetId === null) {
      return
    }
    setApproving(true)
    try {
      await approvePetAlbumAsset(assetId)
      messageApi.success(`相册资源 #${assetId} 已审核通过`)
      setAssetId(null)
    } catch {
      // 拦截器已提示
    } finally {
      setApproving(false)
    }
  }

  const handleValidate = async () => {
    let data: Record<string, unknown>
    try {
      data = validateJson.trim() ? JSON.parse(validateJson) as Record<string, unknown> : {}
    } catch {
      messageApi.error('JSON 格式非法')
      return
    }
    setValidating(true)
    try {
      await validatePetConfigGovernance({ configType: validateType, data })
      messageApi.success('校验通过')
    } catch {
      // 拦截器已提示
    } finally {
      setValidating(false)
    }
  }

  const loadHistory = async () => {
    if (historyConfigId === null) {
      messageApi.warning('请先填写配置 ID')
      return
    }
    setHistoryLoading(true)
    try {
      const { data: res } = await listPetConfigGovernanceHistory({ configType: historyType, configId: historyConfigId })
      if (res.success) {
        setVersions(res.data ?? [])
      }
    } catch {
      // 拦截器已提示
    } finally {
      setHistoryLoading(false)
    }
  }

  const handleRollback = async (row: AdminPetConfigVersion) => {
    if (historyConfigId === null) {
      return
    }
    try {
      await rollbackPetConfigGovernance({ configType: historyType, configId: historyConfigId, version: row.version })
      messageApi.success(`已回退到版本 v${row.version}`)
      void loadHistory()
    } catch {
      // 拦截器已提示
    }
  }

  const versionColumns: ColumnsType<AdminPetConfigVersion> = [
    { title: '版本', dataIndex: 'version', width: 70, render: (v) => `v${v}` },
    {
      title: '操作',
      dataIndex: 'operation',
      width: 100,
      render: (v: AdminPetConfigVersion['operation']) => (
        <Tag color={v === 'PUBLISH' ? 'blue' : 'orange'}>{v}</Tag>
      ),
    },
    { title: '操作人', dataIndex: 'operator', width: 120 },
    { title: '时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作',
      width: 90,
      render: (_: unknown, row) => (
        <Popconfirm title={`确认回退到 v${row.version}？快照字段将写回当前配置。`} onConfirm={() => void handleRollback(row)}>
          <Button type="link" size="small">回退</Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <div>
      {contextHolder}
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card type="inner" title="相册资源审核（BE-11）">
          <Space>
            <InputNumber
              placeholder="相册资源 ID"
              value={assetId}
              onChange={(v) => setAssetId(v)}
              precision={0}
              min={1}
              style={{ width: 180 }}
            />
            <Popconfirm title="确认审核通过？通过后用户端可见。" disabled={assetId === null} onConfirm={() => void handleApprove()}>
              <Button type="primary" loading={approving} disabled={assetId === null}>审核通过</Button>
            </Popconfirm>
            <Text type="secondary">用户上传的相册资源为 PENDING，仅审核链路可设 APPROVED</Text>
          </Space>
        </Card>
        <Card type="inner" title="配置校验预览（B21）">
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Space>
              <Select value={validateType} onChange={setValidateType} options={typeOptions} style={{ width: 180 }} />
              <Button type="primary" loading={validating} onClick={() => void handleValidate()}>校验</Button>
              <Text type="secondary">数值上下限组合校验，不落库</Text>
            </Space>
            <Input.TextArea
              value={validateJson}
              onChange={(e) => setValidateJson(e.target.value)}
              rows={6}
              style={{ fontFamily: 'monospace' }}
              placeholder='配置字段 JSON，例如 {"durationSeconds": 300}'
            />
          </Space>
        </Card>
        <Card type="inner" title="配置历史与回退（B21）">
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Space>
              <Select value={historyType} onChange={setHistoryType} options={typeOptions} style={{ width: 180 }} />
              <InputNumber
                placeholder="配置 ID"
                value={historyConfigId}
                onChange={(v) => setHistoryConfigId(v)}
                precision={0}
                min={1}
                style={{ width: 160 }}
              />
              <Button loading={historyLoading} onClick={() => void loadHistory()}>查询历史</Button>
              <Text type="secondary">最近 50 条发布/回退快照</Text>
            </Space>
            <Table
              rowKey={(row) => String(row.id)}
              size="small"
              loading={historyLoading}
              columns={versionColumns}
              dataSource={versions}
              pagination={false}
              locale={{ emptyText: '暂无历史版本' }}
            />
          </Space>
        </Card>
      </Space>
    </div>
  )
}

export default function PetManage() {
  return (
    <Card title="宠物运营" bodyStyle={{ paddingTop: 8 }}>
      <Tabs
        items={[
          { key: 'dashboard', label: '数据看板', children: <DashboardPanel /> },
          {
            key: 'configs',
            label: '配置管理',
            children: (
              <Tabs
                type="card"
                size="small"
                items={CONFIGS.map((def) => ({
                  key: def.key,
                  label: def.label,
                  children: <ConfigPanel def={def} />,
                }))}
              />
            ),
          },
          { key: 'wall', label: '留言审核', children: <WallPanel /> },
          { key: 'reports', label: '举报处理', children: <ReportPanel /> },
          { key: 'album-governance', label: '相册·治理', children: <GovernancePanel /> },
          { key: 'users', label: '用户宠物', children: <UserPanel /> },
          { key: 'seasons', label: '赛季管理', children: <SeasonPanel /> },
          { key: 'sensitive-words', label: '敏感词库', children: <SensitiveWordPanel /> },
          { key: 'persona-phrases', label: '口头禅', children: <PersonaPhrasePanel /> },
          { key: 'quest-receipts', label: '任务回执', children: <QuestReceiptPanel /> },
        ]}
      />
    </Card>
  )
}
