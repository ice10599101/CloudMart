import { useRef, useState } from 'react'
import { ProTable, ModalForm, ProFormText, ProFormDigit, ProFormSelect } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Image, Popconfirm, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getAdminWishBadges,
  createAdminWishBadge,
  updateAdminWishBadge,
  updateAdminWishBadgeStatus,
  BADGE_RARITY_MAP,
  BADGE_CONDITION_TYPE_MAP,
} from '@/api/admin/wish'
import type { AdminBadgeRecord, AdminBadgeCondition } from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

/**
 * 心愿徽章管理（文档 33.4.7：新增/编辑/上下架 + condition JSON 编辑校验）。
 *
 * condition 采用结构化表单（类型/阈值/描述三段式）而非裸 JSON 文本域：
 * 从根源消灭 JSON 语法错误，提交时序列化为标准 JSON，后端
 * BadgeConditionParser.validate 兜底结构校验。
 * 下架语义：不参与授予判定、不出现在徽章墙/图鉴；已获得记录保留。
 */

/** 结构化表单值（序列化为 condition JSON 提交） */
interface BadgeFormValues {
  name: string
  icon?: string
  rarity: keyof typeof BADGE_RARITY_MAP
  conditionType: keyof typeof BADGE_CONDITION_TYPE_MAP
  threshold: number
  description: string
}

/** 回显：解析存量 condition JSON 填充表单；非法配置提示重新填写 */
function parseCondition(condition: string): Partial<BadgeFormValues> | null {
  try {
    const parsed = JSON.parse(condition) as AdminBadgeCondition
    if (!parsed.type || !parsed.threshold || !parsed.description) return null
    return { conditionType: parsed.type, threshold: parsed.threshold, description: parsed.description }
  } catch {
    return null
  }
}

function serializeCondition(values: BadgeFormValues): string {
  return JSON.stringify({
    type: values.conditionType,
    threshold: values.threshold,
    description: values.description,
  })
}

export default function WishBadges() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<AdminBadgeRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleSubmit = async (values: BadgeFormValues & { code?: string }) => {
    const condition = serializeCondition(values)
    return confirmSubmit(async () => {
      if (editingRecord) {
        await updateAdminWishBadge(editingRecord.id, {
          name: values.name,
          icon: values.icon,
          rarity: values.rarity,
          condition,
        })
        message.success('更新成功')
      } else {
        await createAdminWishBadge({
          code: values.code!,
          name: values.name,
          icon: values.icon,
          rarity: values.rarity,
          condition,
        })
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleToggleStatus = async (record: AdminBadgeRecord) => {
    await updateAdminWishBadgeStatus(record.id, !record.isActive)
    message.success(record.isActive ? '已下架（不判定不展示，已获得记录保留）' : '已上架')
    actionRef.current?.reload()
  }

  const columns: ProColumns<AdminBadgeRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 90, search: false },
    { title: '编码', dataIndex: 'code', width: 130, search: false },
    { title: '名称', dataIndex: 'name', width: 130, search: false },
    {
      title: '图标',
      dataIndex: 'icon',
      width: 80,
      search: false,
      render: (_, record) =>
        record.icon ? (
          record.icon.startsWith('http') ? (
            <Image src={record.icon} width={32} height={32} style={{ objectFit: 'cover' }} />
          ) : (
            <span style={{ fontSize: 24 }}>{record.icon}</span>
          )
        ) : (
          '-'
        ),
    },
    {
      title: '稀有度',
      dataIndex: 'rarity',
      width: 90,
      search: false,
      render: (_, record) => {
        const rarity = BADGE_RARITY_MAP[record.rarity]
        return rarity ? <Tag color={rarity.color}>{rarity.label}</Tag> : record.rarity
      },
    },
    {
      title: '触发条件',
      dataIndex: 'condition',
      width: 260,
      search: false,
      ellipsis: true,
      render: (_, record) => {
        const parsed = parseCondition(record.condition)
        if (!parsed) {
          return <Tag color="error">condition 非法（保存修复后生效）</Tag>
        }
        const typeLabel = BADGE_CONDITION_TYPE_MAP[parsed.conditionType!]
        return (
          <Typography.Text type="secondary">
            {typeLabel ? typeLabel.label : parsed.conditionType} ≥ {parsed.threshold}
          </Typography.Text>
        )
      },
    },
    {
      title: '状态',
      dataIndex: 'isActive',
      width: 90,
      search: false,
      render: (_, record) =>
        record.isActive ? <Tag color="success">上架中</Tag> : <Tag color="default">已下架</Tag>,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 160,
      search: false,
      render: (_, record) => new Date(record.updatedAt).toLocaleString(),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 150,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="edit"
          type="link"
          size="small"
          onClick={() => {
            setEditingRecord(record)
            setModalVisible(true)
          }}
        >
          编辑
        </Button>,
        <Popconfirm
          key="toggle"
          title={record.isActive ? '确认下架？下架后不参与判定与展示' : '确认上架？'}
          onConfirm={() => handleToggleStatus(record)}
        >
          <Button type="link" size="small" danger={record.isActive}>
            {record.isActive ? '下架' : '上架'}
          </Button>
        </Popconfirm>,
      ],
    },
  ]

  const editingCondition = editingRecord ? parseCondition(editingRecord.condition) : null

  return (
    <>
      <ProTable<AdminBadgeRecord>
        headerTitle="心愿徽章管理"
        actionRef={actionRef}
        rowKey="id"
        search={false}
        scroll={{ x: 1100 }}
        request={async () => {
          return safeProTableRequest<AdminBadgeRecord>(() => getAdminWishBadges())
        }}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingRecord(null)
              setModalVisible(true)
            }}
          >
            新增徽章
          </Button>,
        ]}
        columns={columns}
        pagination={false}
      />

      <ModalForm<BadgeFormValues & { code?: string }>
        title={editingRecord ? `编辑徽章：${editingRecord.code}` : '新增徽章'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? {
                name: editingRecord.name,
                icon: editingRecord.icon ?? undefined,
                rarity: editingRecord.rarity,
                ...(editingCondition ?? {}),
              }
            : { rarity: 'COMMON', conditionType: 'WISH_CREATED', threshold: 1 }
        }
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={520}
      >
        {!editingRecord && (
          <ProFormText
            name="code"
            label="徽章编码"
            placeholder="唯一编码，如 HELP_500"
            rules={[
              { required: true, message: '请输入徽章编码' },
              { max: 30, message: '编码不能超过30字符' },
              { pattern: /^[A-Z][A-Z0-9_]*$/, message: '大写字母开头，仅大写字母/数字/下划线' },
            ]}
            extra="创建后不可修改"
          />
        )}
        <ProFormText
          name="name"
          label="徽章名称"
          placeholder="请输入徽章名称"
          rules={[
            { required: true, message: '请输入徽章名称' },
            { max: 60, message: '名称不能超过60字符' },
          ]}
        />
        <ProFormText name="icon" label="图标" placeholder="图标URL或Emoji" />
        <ProFormSelect
          name="rarity"
          label="稀有度"
          options={Object.entries(BADGE_RARITY_MAP).map(([value, { label }]) => ({ value, label }))}
          rules={[{ required: true, message: '请选择稀有度' }]}
        />
        <ProFormSelect
          name="conditionType"
          label="触发类型"
          options={Object.entries(BADGE_CONDITION_TYPE_MAP).map(([value, { label }]) => ({ value, label }))}
          rules={[{ required: true, message: '请选择触发类型' }]}
        />
        <ProFormDigit
          name="threshold"
          label="达标阈值"
          placeholder="统计值 ≥ 阈值即授予"
          min={1}
          max={999999}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入达标阈值' }]}
        />
        <ProFormText
          name="description"
          label="获取方式描述"
          placeholder="展示在徽章墙锁定态，如：累计帮助100人"
          rules={[
            { required: true, message: '请输入获取方式描述' },
            { max: 100, message: '描述不能超过100字符' },
          ]}
        />
        {editingRecord && !editingCondition && (
          <Typography.Text type="warning">
            存量 condition JSON 非法，重新配置保存后将修复
          </Typography.Text>
        )}
      </ModalForm>
    </>
  )
}
