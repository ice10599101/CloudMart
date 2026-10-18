import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormDigit,
  ProFormDateTimePicker,
  ProFormList,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm, Modal, InputNumber, Alert } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getTieredPromotions,
  createTieredPromotion,
  updateTieredPromotion,
  enableTieredPromotion,
  disableTieredPromotion,
  deleteTieredPromotion,
  previewTieredPromotion,
  type TieredPreviewResult,
} from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface TierRule {
  minAmount: number
  discountAmount: number
}

interface TieredPromotionRecord {
  id: number
  name: string
  description: string
  rules: TierRule[]
  status: string
  startTime: string
  endTime: string
  createdAt: string
}

const PROMOTION_STATUS_MAP: Record<string, { text: string; color: string }> = {
  ENABLED: { text: '已启用', color: 'green' },
  DISABLED: { text: '已禁用', color: 'red' },
  PENDING: { text: '待开始', color: 'default' },
  ENDED: { text: '已结束', color: 'default' },
}

export default function TieredPromotion() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<TieredPromotionRecord | null>(null)
  // R12：发布前试算
  const [previewRecord, setPreviewRecord] = useState<TieredPromotionRecord | null>(null)
  const [previewAmount, setPreviewAmount] = useState<number | null>(100)
  const [previewResult, setPreviewResult] = useState<TieredPreviewResult | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const runPreview = async (id: number, amount: number | null) => {
    if (!amount || amount <= 0) {
      message.warning('请输入有效的购物篮金额')
      return
    }
    setPreviewLoading(true)
    try {
      const res = await previewTieredPromotion(id, { orderAmount: amount })
      setPreviewResult(res.data.data)
    } catch {
      setPreviewResult(null)
    } finally {
      setPreviewLoading(false)
    }
  }

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      const payload = {
        ...values,
        startTime: values.startTime?.format('YYYY-MM-DD HH:mm:ss'),
        endTime: values.endTime?.format('YYYY-MM-DD HH:mm:ss'),
        rules: (values.rules ?? []).map((rule: { minAmount: number; discountAmount: number }) => ({
          minAmount: Number(rule.minAmount),
          discountAmount: Number(rule.discountAmount),
        })),
      }
      if (editingRecord) {
        await updateTieredPromotion(editingRecord.id, payload)
        message.success('编辑成功')
      } else {
        await createTieredPromotion(payload)
        message.success('阶梯满减活动创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleToggleStatus = async (record: TieredPromotionRecord, enable: boolean) => {
    if (enable) {
      await enableTieredPromotion(record.id)
    } else {
      await disableTieredPromotion(record.id)
    }
    message.success(enable ? '已启用' : '已禁用')
    actionRef.current?.reload()
  }

  const columns: ProColumns<TieredPromotionRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '活动名称', dataIndex: 'name', width: 160 },
    { title: '描述', dataIndex: 'description', width: 200, search: false, ellipsis: true },
    {
      title: '阶梯规则',
      dataIndex: 'rules',
      width: 300,
      search: false,
      render: (_, record) => {
        if (!record.rules || record.rules.length === 0) return '-'
        return (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
            {record.rules.map((rule, index) => (
              <Tag key={index} color="blue">
                满{rule.minAmount}减{rule.discountAmount}
              </Tag>
            ))}
          </div>
        )
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = PROMOTION_STATUS_MAP[record.status]
        return <Tag color={statusInfo?.color ?? 'default'}>{statusInfo?.text ?? record.status}</Tag>
      },
    },
    { title: '开始时间', dataIndex: 'startTime', width: 170, valueType: 'dateTime', search: false },
    { title: '结束时间', dataIndex: 'endTime', width: 170, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 290,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="preview"
          type="link"
          size="small"
          onClick={() => {
            setPreviewRecord(record)
            setPreviewAmount(100)
            setPreviewResult(null)
          }}
        >
          预览试算
        </Button>,
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
        record.status === 'DISABLED' && (
          <Popconfirm
            key="enable"
            title="确认启用该活动？"
            onConfirm={() => handleToggleStatus(record, true)}
          >
            <Button type="link" size="small">启用</Button>
          </Popconfirm>
        ),
        record.status === 'ENABLED' && (
          <Popconfirm
            key="disable"
            title="确认禁用该活动？"
            onConfirm={() => handleToggleStatus(record, false)}
          >
            <Button type="link" size="small" danger>禁用</Button>
          </Popconfirm>
        ),
        <Popconfirm
          key="delete"
          title="确定删除吗？"
          onConfirm={async () => {
            await deleteTieredPromotion(record.id!)
            message.success('删除成功')
            actionRef.current?.reload()
          }}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<TieredPromotionRecord>
        headerTitle="阶梯满减管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1300 }}
        request={async (params) => {
          return safeProTableRequest<TieredPromotionRecord>(() =>
            getTieredPromotions({
              page: params.current,
              pageSize: params.pageSize,
              name: params.name,
              status: params.status,
            })
          )
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
            新增活动
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑阶梯满减活动' : '新增阶梯满减活动'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? {
                ...editingRecord,
                startTime: undefined,
                endTime: undefined,
              }
            : { rules: [{ minAmount: 100, discountAmount: 10 }] }
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={600}
      >
        <ProFormText
          name="name"
          label="活动名称"
          placeholder="请输入活动名称"
          rules={[{ required: true, message: '请输入活动名称' }]}
        />
        <ProFormText
          name="description"
          label="活动描述"
          placeholder="请输入活动描述"
        />
        <ProFormDateTimePicker
          name="startTime"
          label="开始时间"
          rules={[{ required: true, message: '请选择开始时间' }]}
        />
        <ProFormDateTimePicker
          name="endTime"
          label="结束时间"
          rules={[{ required: true, message: '请选择结束时间' }]}
        />
        <ProFormList
          name="rules"
          label="阶梯规则"
          creatorButtonProps={{
            creatorButtonText: '添加阶梯',
          }}
          min={1}
          rules={[{ validator: (_: unknown, value: unknown[]) => (value && value.length > 0 ? Promise.resolve() : Promise.reject(new Error('至少添加一条阶梯规则'))) }]}
        >
          <ProFormDigit
            name="minAmount"
            label="满额"
            min={0.01}
            fieldProps={{ precision: 2, addonAfter: '元' }}
            rules={[{ required: true, message: '请输入满额' }]}
          />
          <ProFormDigit
            name="discountAmount"
            label="减额"
            min={0.01}
            fieldProps={{ precision: 2, addonAfter: '元' }}
            rules={[{ required: true, message: '请输入减额' }]}
          />
        </ProFormList>
      </ModalForm>

      {/* R12：发布前预览——忽略活动状态/时间窗，按购物篮金额试算命中阶梯 */}
      <Modal
        title={`发布前预览：${previewRecord?.name ?? ''}`}
        open={!!previewRecord}
        onCancel={() => setPreviewRecord(null)}
        onOk={() => previewRecord && runPreview(previewRecord.id, previewAmount)}
        confirmLoading={previewLoading}
        okText="试算"
      >
        <div style={{ marginBottom: 12, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
          试算不受启用状态与活动时间窗限制，仅供发布前确认规则配置。
        </div>
        <InputNumber
          style={{ width: '100%' }}
          min={0.01}
          precision={2}
          addonBefore="购物篮金额 ¥"
          value={previewAmount}
          onChange={(v) => setPreviewAmount(v)}
        />
        {previewResult && (
          <Alert
            style={{ marginTop: 12 }}
            type={previewResult.matched ? (previewResult.userVisible ? 'success' : 'info') : 'warning'}
            showIcon
            message={
              previewResult.matched
                ? `命中阶梯（满 ¥${previewResult.minAmount}），本单减 ¥${previewResult.discountAmount}`
                : '该金额未达到任何阶梯门槛，无减免'
            }
            description={
              <div style={{ fontSize: 12 }}>
                {(previewResult.reasons ?? []).map((reason) => (
                  <div key={reason}>{reason}</div>
                ))}
                {!previewResult.userVisible &&
                  (previewResult.blockingReasons ?? []).map((reason) => (
                    <div key={reason} style={{ color: 'var(--color-text-tertiary)' }}>
                      用户侧暂不可用：{reason}
                    </div>
                  ))}
              </div>
            }
          />
        )}
      </Modal>
    </>
  )
}
