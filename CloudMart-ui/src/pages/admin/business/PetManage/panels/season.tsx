import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  listPetSeasonRewards,
  listPetSeasons,
  savePetSeasonRewards,
  settlePetSeason,
  upsertPetSeason,
  type AdminPetSeason,
  type AdminPetSeasonReward,
} from '@/api/admin/pet'

const { Text } = Typography

/** 赛季管理面板（F2：创建/延后/梯度/手动结算） */
export function SeasonPanel() {
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
