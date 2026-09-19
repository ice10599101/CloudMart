import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
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
  getPetDashboard,
  listPetConfigs,
  listPetWallMessages,
  togglePetConfig,
  updatePetWallMessageStatus,
  upsertPetConfig,
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
      width: 160,
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

  const { overview, distribution } = data

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
        ]}
      />
    </Card>
  )
}
