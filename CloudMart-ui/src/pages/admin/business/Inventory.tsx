import { useRef, useState } from 'react'
import { ProTable, ModalForm, ProFormText, ProFormDigit } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Descriptions, Modal, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { getInventory, getInventoryDetail, initInventory } from '@/api/admin/business'
import type { ApiResponse } from '@/types/api'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface InventoryRecord {
  /** BASE-01：库存行 ID 与 SKU ID 均为雪花 ID，按字符串贯通（number | string 兼容小整数种子数据） */
  id: number | string
  skuId: number | string
  productName: string
  skuName: string
  availableStock: number
  lockedStock: number
  totalStock: number
  warehouseCode: string
  updatedAt: string
}

interface InventoryDetail {
  skuId: number | string
  productName: string
  skuName: string
  availableStock: number
  lockedStock: number
  totalStock: number
  warehouseCode: string
  safetyStock: number
  replenishmentQty: number
  createdAt: string
  updatedAt: string
}

export default function Inventory() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [initModalVisible, setInitModalVisible] = useState(false)
  const [detailVisible, setDetailVisible] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [currentDetail, setCurrentDetail] = useState<InventoryDetail | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

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

  const handleViewDetail = async (skuId: number | string) => {
    setDetailLoading(true)
    setDetailVisible(true)
    try {
      const { data: res } = await getInventoryDetail(skuId)
      const response = res as ApiResponse<InventoryDetail>
      setCurrentDetail(response.data ?? null)
    } finally {
      setDetailLoading(false)
    }
  }

  const columns: ProColumns<InventoryRecord>[] = [
    { title: 'SKU ID', dataIndex: 'skuId', width: 100 },
    { title: '商品名称', dataIndex: 'productName', width: 180, ellipsis: true },
    { title: 'SKU 名称', dataIndex: 'skuName', width: 140, ellipsis: true },
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
    { title: '锁定库存', dataIndex: 'lockedStock', width: 100, search: false },
    { title: '总库存', dataIndex: 'totalStock', width: 100, search: false },
    { title: '仓库编码', dataIndex: 'warehouseCode', width: 120, search: false },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      fixed: 'right',
      render: (_, record) => [
        <Button key="detail" type="link" size="small" onClick={() => handleViewDetail(record.skuId)}>
          详情
        </Button>,
      ],
    },
  ]

  return (
    <>
      <ProTable<InventoryRecord>
        headerTitle="库存管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1200 }}
        request={async (params) => {
          return safeProTableRequest<InventoryRecord>(() =>
            getInventory({
              page: params.current,
              pageSize: params.pageSize,
              skuId: params.skuId,
              productName: params.productName,
            })
          )
        }}
        toolBarRender={() => [
          <Button
            key="init"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setInitModalVisible(true)}
          >
            初始化库存
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title="初始化库存"
        open={initModalVisible}
        onOpenChange={createHandleOpenChange(setInitModalVisible)}
        onFinish={handleInit}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={520}
      >
        <ProFormText
          name="skuId"
          label="SKU ID"
          placeholder="请输入 SKU ID"
          tooltip="雪花 ID 按字符串原样提交（BASE-01：不做数字转换，防精度损失）"
          rules={[
            { required: true, message: '请输入 SKU ID' },
            { pattern: /^\d{1,19}$/, message: 'SKU ID 应为 1-19 位数字' },
          ]}
        />
        <ProFormText
          name="productId"
          label="商品 ID"
          placeholder="请输入商品 ID"
          rules={[
            { required: true, message: '请输入商品 ID' },
            { pattern: /^\d{1,19}$/, message: '商品 ID 应为 1-19 位数字' },
          ]}
        />
        <ProFormDigit
          name="stock"
          label="初始库存"
          min={0}
          fieldProps={{ precision: 0 }}
          tooltip="仅对未初始化过的 SKU 生效；已有库存会被拒绝（TRADE-02，补货请走后续调整单）"
          rules={[{ required: true, message: '请输入初始库存' }]}
        />
      </ModalForm>

      <Modal
        title="库存详情"
        open={detailVisible}
        onCancel={() => {
          setDetailVisible(false)
          setCurrentDetail(null)
        }}
        footer={null}
        width={640}
        loading={detailLoading}
      >
        {currentDetail && (
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="SKU ID">{currentDetail.skuId}</Descriptions.Item>
            <Descriptions.Item label="商品名称">{currentDetail.productName}</Descriptions.Item>
            <Descriptions.Item label="SKU 名称">{currentDetail.skuName}</Descriptions.Item>
            <Descriptions.Item label="仓库编码">{currentDetail.warehouseCode}</Descriptions.Item>
            <Descriptions.Item label="可用库存">
              <Tag color={currentDetail.availableStock <= 10 ? 'red' : 'green'}>
                {currentDetail.availableStock}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="锁定库存">{currentDetail.lockedStock}</Descriptions.Item>
            <Descriptions.Item label="总库存">{currentDetail.totalStock}</Descriptions.Item>
            <Descriptions.Item label="安全库存">{currentDetail.safetyStock}</Descriptions.Item>
            <Descriptions.Item label="补货数量">{currentDetail.replenishmentQty}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{currentDetail.createdAt}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{currentDetail.updatedAt}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </>
  )
}
