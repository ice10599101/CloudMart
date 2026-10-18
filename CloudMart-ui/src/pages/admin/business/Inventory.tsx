import { useRef, useState } from 'react'
import { ProTable, ModalForm, ProFormText, ProFormDigit, ProFormSelect } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Descriptions, Modal, Tag, Input, Table, Form, InputNumber, Select, Alert } from 'antd'
import { PlusOutlined, SwapOutlined } from '@ant-design/icons'
import {
  getInventory,
  getInventoryDetail,
  getProducts,
  getProduct,
  initInventory,
  adjustInventory,
  listInventoryAdjustments,
} from '@/api/admin/business'
import type { InventoryRow, InventoryAdjustment } from '@/api/admin/business'
import type { ApiResponse } from '@/types/api'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface ProductOption {
  id: number | string
  name: string
}

interface SkuOption {
  id: number | string
  label: string
}

/** 生成幂等键：优先 crypto.randomUUID，环境缺失时退化为时间+随机段 */
function genRequestKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `adj-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

export default function Inventory() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [initModalVisible, setInitModalVisible] = useState(false)
  const [detailVisible, setDetailVisible] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [currentDetail, setCurrentDetail] = useState<InventoryRow | null>(null)
  const [adjustTarget, setAdjustTarget] = useState<InventoryRow | null>(null)
  const [adjustForm] = Form.useForm()
  const [initForm] = Form.useForm()
  const [adjustSubmitting, setAdjustSubmitting] = useState(false)
  const [adjustments, setAdjustments] = useState<InventoryAdjustment[]>([])
  const [adjustmentsLoading, setAdjustmentsLoading] = useState(false)
  // 调整量实时预览（组件顶层调用 hook，符合 React 规则）
  const adjustDelta = Form.useWatch('delta', adjustForm)
  // 初始化选择器：商品搜索 → 商品详情取 SKU（真实 string ID，不再手输 ID）
  const [productOptions, setProductOptions] = useState<ProductOption[]>([])
  const [skuOptions, setSkuOptions] = useState<SkuOption[]>([])
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const searchProducts = async (keyword?: string) => {
    const { data: res } = await getProducts({ keyword, page: 1, pageSize: 20 })
    const response = res as ApiResponse<Array<Record<string, any>>>
    setProductOptions(
      (response.data ?? []).map((p) => ({ id: p.id, name: p.name })),
    )
  }

  const handleProductSelected = async (productId: number | string) => {
    // 详情接口返回 SKU 列表（列表接口为 null），规格展示 attributes
    const { data: res } = await getProduct(productId)
    const response = res as ApiResponse<{ skus: Array<Record<string, any>> }>
    setSkuOptions(
      (response.data?.skus ?? []).map((sku) => ({
        id: sku.id,
        label: `${sku.attributes || sku.skuCode || sku.id}${sku.price != null ? `（¥${sku.price}）` : ''}`,
      })),
    )
    initForm.setFieldValue('skuId', undefined)
  }

  const handleInit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      try {
        // BASE-01：身份 ID 保持字符串原样提交（后端 Jackson 从十进制字符串解析 Long）
        await initInventory({
          skuId: String(values.skuId ?? '').trim(),
          productId: String(values.productId ?? '').trim(),
          stock: Number(values.stock),
        })
        message.success('库存初始化成功')
        actionRef.current?.reload()
      } catch (error) {
        // TRADE-02：已有库存一律拒绝覆盖（409 INVENTORY_ALREADY_INITIALIZED），如实展示服务端原因
        const msg =
          (error as { response?: { data?: { error?: { message?: string } } } })
            ?.response?.data?.error?.message
        message.error(msg ?? '库存初始化失败')
        throw error
      }
    })
  }

  const openAdjust = (record: InventoryRow) => {
    setAdjustTarget(record)
    setAdjustments([])
    adjustForm.resetFields()
  }

  const handleAdjust = async () => {
    if (!adjustTarget) return false
    const values = await adjustForm.validateFields()
    setAdjustSubmitting(true)
    try {
      const { data: res } = await adjustInventory({
        skuId: String(adjustTarget.skuId),
        delta: values.delta,
        reason: values.reason,
        expectedVersion: adjustTarget.version,
        requestKey: genRequestKey(),
      })
      const response = res as ApiResponse<InventoryAdjustment>
      message.success(
        `调整完成：${response.data?.beforeAvailable ?? '?'} → ${response.data?.afterAvailable ?? '?'}（单号 ${response.data?.adjustNo ?? '-'}）`,
      )
      actionRef.current?.reload()
      setAdjustTarget(null)
      return true
    } catch (error) {
      // 版本冲突（INVENTORY_VERSION_CONFLICT）如实提示刷新重试；幂等键冲突同理
      const msg =
        (error as { response?: { data?: { error?: { message?: string } } } })
          ?.response?.data?.error?.message
      message.error(msg ?? '库存调整失败')
      return false
    } finally {
      setAdjustSubmitting(false)
    }
  }

  const loadAdjustments = async (skuId: number | string) => {
    setAdjustmentsLoading(true)
    try {
      const { data: res } = await listInventoryAdjustments({ skuId, page: 1, pageSize: 20 })
      const response = res as ApiResponse<InventoryAdjustment[]>
      setAdjustments(response.data ?? [])
    } finally {
      setAdjustmentsLoading(false)
    }
  }

  const handleViewDetail = async (skuId: number | string) => {
    setDetailLoading(true)
    setDetailVisible(true)
    try {
      const { data: res } = await getInventoryDetail(skuId)
      const response = res as ApiResponse<InventoryRow>
      setCurrentDetail(response.data ?? null)
      await loadAdjustments(skuId)
    } finally {
      setDetailLoading(false)
    }
  }

  const columns: ProColumns<InventoryRow>[] = [
    { title: 'SKU ID', dataIndex: 'skuId', width: 110, search: false },
    {
      title: '商品名称',
      dataIndex: 'productName',
      width: 180,
      ellipsis: true,
      render: (_, record) => {
        if (record.productName) return record.productName
        // T12：区分“商品服务不可用”和“SKU 已删除/下架”，不用空白成功掩盖
        if (record.productInfoStatus === 'UNAVAILABLE') {
          return <span style={{ color: 'var(--color-warning)' }}>商品信息暂不可用</span>
        }
        if (record.productInfoStatus === 'MISSING') {
          return <span style={{ color: 'var(--color-text-tertiary)' }}>SKU 已删除或下架</span>
        }
        return <span style={{ color: 'var(--color-text-tertiary)' }}>-</span>
      },
    },
    {
      title: 'SKU 规格',
      dataIndex: 'skuSpec',
      width: 140,
      ellipsis: true,
      search: false,
      render: (_, record) =>
        record.skuSpec ?? <span style={{ color: 'var(--color-text-tertiary)' }}>-</span>,
    },
    {
      title: '可用库存',
      dataIndex: 'availableStock',
      width: 100,
      search: false,
      render: (_, record) => (
        <Tag color={record.availableStock <= 10 ? 'red' : record.availableStock <= 50 ? 'orange' : 'green'}>
          {record.availableStock}
        </Tag>
      ),
    },
    { title: '预占库存', dataIndex: 'lockedStock', width: 100, search: false },
    { title: '版本', dataIndex: 'version', width: 70, search: false },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 150,
      fixed: 'right',
      render: (_, record) => [
        <Button key="detail" type="link" size="small" onClick={() => handleViewDetail(record.skuId)}>
          详情
        </Button>,
        // TRADE-02：修正只能走盘点调整单（幂等/CAS/审计），无直接覆盖入口
        <Button key="adjust" type="link" size="small" onClick={() => openAdjust(record)}>
          盘点调整
        </Button>,
      ],
    },
  ]

  return (
    <>
      <ProTable<InventoryRow>
        headerTitle="库存管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1200 }}
        request={async (params) => {
          return safeProTableRequest<InventoryRow>(() =>
            // 服务端契约：productId 过滤（TRADE-02 对齐，页面不再发送不存在的 skuId/名称筛选）
            getInventory({
              page: params.current,
              pageSize: params.pageSize,
              productId: params.productId,
            })
          )
        }}
        toolBarRender={() => [
          <Button
            key="init"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setInitModalVisible(true)
              setSkuOptions([])
              searchProducts()
            }}
          >
            初始化库存
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title="初始化库存（仅未初始化的 SKU）"
        open={initModalVisible}
        onOpenChange={createHandleOpenChange(setInitModalVisible)}
        onFinish={handleInit}
        form={initForm}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={560}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="已有库存的 SKU 会被拒绝（INVENTORY_ALREADY_INITIALIZED）；补货走入库单，修正走盘点调整单"
        />
        <ProFormSelect
          name="productId"
          label="商品（搜索选择）"
          placeholder="输入关键词搜索商品"
          showSearch
          debounceTime={300}
          options={productOptions.map((p) => ({ label: p.name, value: String(p.id) }))}
          fieldProps={{
            onSearch: (keyword: string) => searchProducts(keyword || undefined),
          }}
          onChange={(value: string) => handleProductSelected(value)}
          rules={[{ required: true, message: '请选择商品' }]}
        />
        <ProFormSelect
          name="skuId"
          label="SKU（选择商品后自动加载规格）"
          placeholder="先选择商品"
          options={skuOptions.map((s) => ({ label: s.label, value: String(s.id) }))}
          rules={[{ required: true, message: '请选择 SKU' }]}
        />
        <ProFormDigit
          name="stock"
          label="初始库存"
          min={0}
          fieldProps={{ precision: 0 }}
          tooltip="仅对未初始化过的 SKU 生效；初始化后可用库存即此值，预占为 0"
          rules={[{ required: true, message: '请输入初始库存' }]}
        />
      </ModalForm>

      {/* 盘点调整单：delta/原因/幂等键自动生成，版本取行当前值（CAS） */}
      <Modal
        title={`盘点调整：SKU ${adjustTarget?.skuId ?? ''}`}
        open={adjustTarget !== null}
        onCancel={() => setAdjustTarget(null)}
        onOk={handleAdjust}
        confirmLoading={adjustSubmitting}
        okText="提交调整单"
        width={520}
        destroyOnHidden
      >
        {adjustTarget && (
          <>
            <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="当前可用">{adjustTarget.availableStock}</Descriptions.Item>
              <Descriptions.Item label="预占（只读）">{adjustTarget.lockedStock}</Descriptions.Item>
              <Descriptions.Item label="版本（CAS）">{adjustTarget.version}</Descriptions.Item>
              <Descriptions.Item label="调整后可用">
                {typeof adjustDelta === 'number'
                  ? adjustTarget.availableStock + adjustDelta
                  : adjustTarget.availableStock}
              </Descriptions.Item>
            </Descriptions>
            <Form form={adjustForm} layout="vertical">
              <Form.Item
                name="delta"
                label="调整量（正=盘盈，负=盘亏）"
                rules={[
                  { required: true, message: '请输入调整量' },
                  {
                    validator: (_, value) =>
                      value === 0 ? Promise.reject(new Error('调整量不能为 0')) : Promise.resolve(),
                  },
                ]}
              >
                <InputNumber style={{ width: '100%' }} precision={0} placeholder="如 5 或 -3" />
              </Form.Item>
              <Form.Item
                name="reason"
                label="调整原因（必填，入审计台账）"
                rules={[{ required: true, message: '请输入调整原因' }]}
              >
                <Input.TextArea
                  rows={2}
                  maxLength={255}
                  showCount
                  placeholder="如：月度盘点盘盈 / 运输破损核销"
                />
              </Form.Item>
            </Form>
            <Alert
              type="info"
              showIcon
              message="提交即生成调整单：记录操作者、前后值与幂等键；版本冲突时请刷新列表后重试"
            />
          </>
        )}
      </Modal>

      <Modal
        title="库存详情"
        open={detailVisible}
        onCancel={() => {
          setDetailVisible(false)
          setCurrentDetail(null)
          setAdjustments([])
        }}
        footer={null}
        width={760}
        loading={detailLoading}
      >
        {currentDetail && (
          <>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="SKU ID">{currentDetail.skuId}</Descriptions.Item>
              <Descriptions.Item label="商品名称">{currentDetail.productName ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="SKU 规格">{currentDetail.skuSpec ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="商品 ID">{currentDetail.productId}</Descriptions.Item>
              <Descriptions.Item label="可用库存">
                <Tag color={currentDetail.availableStock <= 10 ? 'red' : 'green'}>
                  {currentDetail.availableStock}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="预占库存">{currentDetail.lockedStock}</Descriptions.Item>
              <Descriptions.Item label="版本">{currentDetail.version}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{currentDetail.updatedAt}</Descriptions.Item>
            </Descriptions>
            <div style={{ margin: '16px 0 8px', fontWeight: 600 }}>
              <SwapOutlined /> 最近调整单
            </div>
            <Table<InventoryAdjustment>
              rowKey="adjustNo"
              size="small"
              loading={adjustmentsLoading}
              dataSource={adjustments}
              pagination={false}
              scroll={{ y: 240 }}
              columns={[
                { title: '单号', dataIndex: 'adjustNo', width: 170 },
                {
                  title: '调整量',
                  dataIndex: 'delta',
                  width: 80,
                  render: (v: number) => (
                    <span style={{ color: v > 0 ? '#52c41a' : '#ff4d4f' }}>{v > 0 ? `+${v}` : v}</span>
                  ),
                },
                {
                  title: '前→后',
                  key: 'change',
                  width: 110,
                  render: (_, r) => `${r.beforeAvailable} → ${r.afterAvailable}`,
                },
                { title: '原因', dataIndex: 'reason', ellipsis: true },
                { title: '操作人', dataIndex: 'operatorId', width: 90 },
                { title: '时间', dataIndex: 'createdAt', width: 165 },
              ]}
            />
          </>
        )}
      </Modal>
    </>
  )
}
