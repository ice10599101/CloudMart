import { useCallback, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  adjustUserPet,
  cancelPetQuestInstance,
  compensateUser,
  getUserPets,
  type AdminUserPet,
} from '@/api/admin/pet'

const { Text } = Typography

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
export function UserPanel() {
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
