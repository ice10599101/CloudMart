import { useRef, useState } from 'react'
import { ProTable, ModalForm, ProFormText, ProFormDigit, ProFormSelect, ProFormTextArea, ProFormList } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Tag, Popconfirm, Button, Tabs, Modal, Descriptions, InputNumber, Select, Space, Alert, Table } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getPickOrders,
  startPick,
  confirmPicked,
  confirmPacked,
  getInboundOrders,
  getInboundOrder,
  createPickOrder,
  createInboundOrder,
  receiveInboundItem,
  newInboundReceiptId,
  completeInboundOrder,
} from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

/** BASE-01：订单/仓库/SKU ID 均为雪花 ID，按字符串贯通（number | string 兼容小整数种子数据） */
interface PickOrderRecord {
  id: number | string
  orderId: number | string
  warehouseId: number | string
  warehouseName: string | null
  status: string
  assignedUserId: number | string | null
  createdAt: string
}

interface InboundItem {
  id: number | string
  skuId: number | string
  productName: string | null
  expectedQuantity: number
  receivedQuantity: number
  locationCode: string | null
}

interface InboundOrderRecord {
  id: number | string
  warehouseId: number | string
  warehouseName: string | null
  referenceNo: string | null
  status: string
  totalQuantity: number
  receivedQuantity: number
  operatorUserId: number | string | null
  completedTime: string | null
  createdAt: string
}

const PICK_STATUS_MAP: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待拣货', color: 'default' },
  PICKING: { text: '拣货中', color: 'processing' },
  PICKED: { text: '已拣完', color: 'blue' },
  PACKED: { text: '已打包', color: 'green' },
}

// TRADE-03：状态契约统一 PENDING→RECEIVING→COMPLETED（页面此前误写 INBOUND）
const INBOUND_STATUS_MAP: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待收货', color: 'default' },
  RECEIVING: { text: '收货中', color: 'processing' },
  COMPLETED: { text: '已完成', color: 'green' },
}

export default function Wms() {
  const message = useMessage()
  const pickActionRef = useRef<ActionType>(null)
  const inboundActionRef = useRef<ActionType>(null)
  const [activeTab, setActiveTab] = useState('pick')
  const [inboundDetail, setInboundDetail] = useState<Record<string, any> | null>(null)
  const [inboundDetailLoading, setInboundDetailLoading] = useState(false)
  // 拣货单/入库单创建与收货（对齐下游 AdminWmsController 写端点）
  const [pickCreateOpen, setPickCreateOpen] = useState(false)
  const [inboundCreateOpen, setInboundCreateOpen] = useState(false)
  const [receiveTargetId, setReceiveTargetId] = useState<number | string | null>(null)
  const [receiveItems, setReceiveItems] = useState<InboundItem[]>([])
  const [receiveItemId, setReceiveItemId] = useState<number | string | null>(null)
  const [receiveQuantity, setReceiveQuantity] = useState<number | null>(null)
  // C19：弹窗打开即生成稳定 receiptId，确认/重试原样回传（幂等收敛）
  const [receiveReceiptId, setReceiveReceiptId] = useState<string | null>(null)
  const [receiveQuality, setReceiveQuality] = useState<'PASSED' | 'QUARANTINE'>('PASSED')
  const [receiveSubmitting, setReceiveSubmitting] = useState(false)

  const fetchInboundDetail = async (id: number | string) => {
    setInboundDetailLoading(true)
    try {
      const { data: res } = await getInboundOrder(id)
      setInboundDetail((res as any)?.data ?? res ?? null)
    } catch {
      setInboundDetail(null)
    } finally {
      setInboundDetailLoading(false)
    }
  }

  const handleInboundDetailClose = () => {
    setInboundDetail(null)
  }

  // TRADE-03：开始拣货不带 assignedUserId——服务端缺省分配给当前已认证管理员
  const handleStartPick = async (id: number | string) => {
    await startPick(id)
    message.success('拣货已开始（默认分配给当前操作管理员）')
    pickActionRef.current?.reload()
  }

  const handleConfirmPicked = async (id: number | string) => {
    await confirmPicked(id)
    message.success('已确认拣货完成')
    pickActionRef.current?.reload()
  }

  const handleConfirmPacked = async (id: number | string) => {
    await confirmPacked(id)
    message.success('已确认打包完成')
    pickActionRef.current?.reload()
  }

  // TRADE-03：收货明细从入库单详情 items 选择（不再手输明细 ID）
  const openReceive = async (record: InboundOrderRecord) => {
    setReceiveTargetId(record.id)
    setReceiveItems([])
    setReceiveItemId(null)
    setReceiveQuantity(null)
    setReceiveQuality('PASSED')
    setReceiveReceiptId(newInboundReceiptId())
    try {
      const { data: res } = await getInboundOrder(record.id)
      const detail = (res as any)?.data ?? res
      setReceiveItems(
        (detail?.items ?? []).filter((item: InboundItem) => item.receivedQuantity < item.expectedQuantity),
      )
    } catch {
      message.error('入库单明细加载失败，请重试')
      setReceiveTargetId(null)
    }
  }

  const handleReceive = async () => {
    if (receiveTargetId === null || receiveItemId === null || receiveQuantity === null || !receiveReceiptId) return
    setReceiveSubmitting(true)
    try {
      // C19：同一次确认携带同一 receiptId——网络超时后点"确认收货"是重放而非新收货
      await receiveInboundItem(receiveTargetId, {
        itemId: receiveItemId,
        receivedQuantity: receiveQuantity,
        receiptId: receiveReceiptId,
        qualityResult: receiveQuality,
      })
      message.success('收货登记成功')
      setReceiveTargetId(null)
      setReceiveItemId(null)
      setReceiveQuantity(null)
      setReceiveReceiptId(null)
      inboundActionRef.current?.reload()
    } finally {
      setReceiveSubmitting(false)
    }
  }

  const handleCompleteInbound = async (id: number | string) => {
    await completeInboundOrder(id)
    message.success('入库单已完成')
    inboundActionRef.current?.reload()
  }

  const pickColumns: ProColumns<PickOrderRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 90, search: false },
    { title: '订单号', dataIndex: 'orderId', width: 170 },
    {
      title: '仓库',
      dataIndex: 'warehouseName',
      width: 130,
      search: false,
      render: (_, record) => record.warehouseName ?? `仓库 ${record.warehouseId}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      search: false,
      render: (_, record) => {
        const statusInfo = PICK_STATUS_MAP[record.status]
        return <Tag color={statusInfo?.color ?? 'default'}>{statusInfo?.text ?? record.status}</Tag>
      },
    },
    // TRADE-03：拣货员只有 assignedUserId（无 pickerName 字段）
    {
      title: '拣货员 ID',
      dataIndex: 'assignedUserId',
      width: 120,
      search: false,
      render: (_, record) => record.assignedUserId ?? '-',
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: 'right',
      render: (_, record) => [
        record.status === 'PENDING' && (
          <Popconfirm
            key="start"
            title="确认开始拣货？"
            description="默认分配给当前操作管理员"
            onConfirm={() => handleStartPick(record.id)}
          >
            <Button type="link" size="small">开始拣货</Button>
          </Popconfirm>
        ),
        record.status === 'PICKING' && (
          <Popconfirm
            key="picked"
            title="确认拣货完成？"
            onConfirm={() => handleConfirmPicked(record.id)}
          >
            <Button type="link" size="small">确认拣完</Button>
          </Popconfirm>
        ),
        record.status === 'PICKED' && (
          <Popconfirm
            key="packed"
            title="确认打包完成？"
            onConfirm={() => handleConfirmPacked(record.id)}
          >
            <Button type="link" size="small">确认打包</Button>
          </Popconfirm>
        ),
      ],
    },
  ]

  const inboundColumns: ProColumns<InboundOrderRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 90, search: false },
    { title: '关联单号', dataIndex: 'referenceNo', width: 160, render: (_, r) => r.referenceNo ?? '-' },
    {
      title: '仓库',
      dataIndex: 'warehouseName',
      width: 130,
      search: false,
      render: (_, record) => record.warehouseName ?? `仓库 ${record.warehouseId}`,
    },
    {
      title: '计划/已收',
      key: 'progress',
      width: 110,
      search: false,
      render: (_, record) => `${record.totalQuantity} / ${record.receivedQuantity}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = INBOUND_STATUS_MAP[record.status]
        return <Tag color={statusInfo?.color ?? 'default'}>{statusInfo?.text ?? record.status}</Tag>
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, valueType: 'dateTime', search: false },
    {
      title: '完成时间',
      dataIndex: 'completedTime',
      width: 170,
      valueType: 'dateTime',
      search: false,
      render: (_, r) => r.completedTime ?? '-',
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      fixed: 'right',
      render: (_, record) => [
        <Button key="detail" type="link" size="small" onClick={() => fetchInboundDetail(record.id)}>
          详情
        </Button>,
        // TRADE-03：状态统一 RECEIVING（页面此前误写 INBOUND 导致收货入口状态失配）
        (record.status === 'PENDING' || record.status === 'RECEIVING') && (
          <Button key="receive" type="link" size="small" onClick={() => openReceive(record)}>
            收货
          </Button>
        ),
        record.status === 'RECEIVING' && (
          <Popconfirm key="complete" title="确认完成入库？未收齐将被拒绝（需差异审批）。" onConfirm={() => handleCompleteInbound(record.id)}>
            <Button type="link" size="small">完成入库</Button>
          </Popconfirm>
        ),
      ],
    },
  ]

  return (
    <>
    <Tabs
      activeKey={activeTab}
      onChange={setActiveTab}
      items={[
        {
          key: 'pick',
          label: '拣货单',
          children: (
            <ProTable<PickOrderRecord>
              headerTitle="拣货单管理"
              actionRef={pickActionRef}
              rowKey="id"
              scroll={{ x: 1300 }}
              toolBarRender={() => [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => setPickCreateOpen(true)}>
                  新建拣货单
                </Button>,
              ]}
              request={async (params) => {
                return safeProTableRequest<PickOrderRecord>(() =>
                  getPickOrders({
                    page: params.current,
                    pageSize: params.pageSize,
                    status: params.status,
                  })
                )
              }}
              columns={pickColumns}
              pagination={{ defaultPageSize: 10, showSizeChanger: true }}
            />
          ),
        },
        {
          key: 'inbound',
          label: '入库单',
          children: (
            <ProTable<InboundOrderRecord>
              headerTitle="入库单管理"
              actionRef={inboundActionRef}
              rowKey="id"
              scroll={{ x: 1200 }}
              toolBarRender={() => [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => setInboundCreateOpen(true)}>
                  新建入库单
                </Button>,
              ]}
              request={async (params) => {
                return safeProTableRequest<InboundOrderRecord>(() =>
                  getInboundOrders({
                    page: params.current,
                    pageSize: params.pageSize,
                    status: params.status,
                  })
                )
              }}
              columns={inboundColumns}
              pagination={{ defaultPageSize: 10, showSizeChanger: true }}
            />
          ),
        },
      ]}
    />

    <Modal
      title="入库单详情"
      open={!!inboundDetail}
      onCancel={handleInboundDetailClose}
      footer={null}
      width={820}
      loading={inboundDetailLoading}
    >
      {inboundDetail && (
        <>
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="ID">{inboundDetail.id}</Descriptions.Item>
            <Descriptions.Item label="关联单号">{inboundDetail.referenceNo ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="仓库">
              {inboundDetail.warehouseName ?? `仓库 ${inboundDetail.warehouseId}`}
            </Descriptions.Item>
            <Descriptions.Item label="类型">{inboundDetail.type}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={INBOUND_STATUS_MAP[inboundDetail.status]?.color ?? 'default'}>
                {INBOUND_STATUS_MAP[inboundDetail.status]?.text ?? inboundDetail.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="计划/已收">
              {inboundDetail.totalQuantity} / {inboundDetail.receivedQuantity}
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">{inboundDetail.createdAt}</Descriptions.Item>
            <Descriptions.Item label="完成时间">{inboundDetail.completedTime ?? '-'}</Descriptions.Item>
          </Descriptions>
          <div style={{ margin: '16px 0 8px', fontWeight: 600 }}>入库明细</div>
          <Table<InboundItem>
            rowKey="id"
            size="small"
            dataSource={inboundDetail.items ?? []}
            pagination={false}
            scroll={{ y: 240 }}
            columns={[
              { title: '明细 ID', dataIndex: 'id', width: 90 },
              { title: 'SKU ID', dataIndex: 'skuId', width: 120 },
              { title: '商品', dataIndex: 'productName', ellipsis: true, render: (v: string | null) => v ?? '-' },
              { title: '库位', dataIndex: 'locationCode', width: 90, render: (v: string | null) => v ?? '-' },
              { title: '计划', dataIndex: 'expectedQuantity', width: 70 },
              { title: '已收', dataIndex: 'receivedQuantity', width: 70 },
              {
                title: '剩余',
                key: 'remaining',
                width: 70,
                render: (_, item) => item.expectedQuantity - item.receivedQuantity,
              },
            ]}
          />
        </>
      )}
    </Modal>

    <ModalForm
      title="新建拣货单"
      open={pickCreateOpen}
      onOpenChange={setPickCreateOpen}
      onFinish={async (values: Record<string, any>) => {
        await createPickOrder({
          orderId: values.orderId,
          warehouseId: values.warehouseId,
          remark: values.remark,
        })
        message.success('拣货单已创建')
        pickActionRef.current?.reload()
        return true
      }}
      modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
      width={480}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="订单 ID 为雪花数字串，按字符串原样提交（防精度损失）"
      />
      <ProFormText
        name="orderId"
        label="订单 ID"
        placeholder="订单 ID（1-19 位数字）"
        rules={[
          { required: true, message: '请输入订单 ID' },
          { pattern: /^\d{1,19}$/, message: '订单 ID 应为 1-19 位数字' },
        ]}
      />
      <ProFormText
        name="warehouseId"
        label="仓库 ID"
        placeholder="仓库 ID（1-19 位数字）"
        rules={[
          { required: true, message: '请输入仓库 ID' },
          { pattern: /^\d{1,19}$/, message: '仓库 ID 应为 1-19 位数字' },
        ]}
      />
      <ProFormTextArea name="remark" label="备注" placeholder="选填" />
    </ModalForm>

    <ModalForm
      title="新建入库单"
      open={inboundCreateOpen}
      onOpenChange={setInboundCreateOpen}
      onFinish={async (values: Record<string, any>) => {
        await createInboundOrder({
          warehouseId: values.warehouseId,
          type: values.type,
          referenceNo: values.referenceNo,
          remark: values.remark,
          items: (values.items ?? []).map((item: Record<string, any>) => ({
            skuId: item.skuId,
            productName: item.productName,
            expectedQuantity: item.expectedQuantity,
            locationCode: item.locationCode,
          })),
        })
        message.success('入库单已创建')
        inboundActionRef.current?.reload()
        return true
      }}
      modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
      width={640}
    >
      <ProFormText
        name="warehouseId"
        label="仓库 ID"
        placeholder="仓库 ID（1-19 位数字）"
        rules={[
          { required: true, message: '请输入仓库 ID' },
          { pattern: /^\d{1,19}$/, message: '仓库 ID 应为 1-19 位数字' },
        ]}
      />
      <ProFormSelect
        name="type"
        label="类型"
        options={[
          { label: '采购入库', value: 'PURCHASE' },
          { label: '退货入库', value: 'RETURN' },
          { label: '调拨入库', value: 'TRANSFER' },
        ]}
        rules={[{ required: true, message: '请选择类型' }]}
      />
      <ProFormText name="referenceNo" label="关联单号" placeholder="选填" />
      <ProFormTextArea name="remark" label="备注" placeholder="选填" />
      <ProFormList
        name="items"
        label="入库明细"
        min={1}
        creatorButtonProps={{ creatorButtonText: '添加明细' }}
      >
        <Space align="baseline" wrap>
          <ProFormText name="skuId" label="SKU ID" rules={[{ required: true, message: '必填' }, { pattern: /^\d{1,19}$/, message: '1-19 位数字' }]} />
          <ProFormText name="productName" label="商品名称" rules={[{ required: true, message: '必填' }]} />
          <ProFormDigit name="expectedQuantity" label="预期数量" rules={[{ required: true, message: '必填' }]} fieldProps={{ precision: 0 }} min={1} />
          <ProFormText name="locationCode" label="库位编码" />
        </Space>
      </ProFormList>
    </ModalForm>

    <Modal
      title="收货入库"
      open={receiveTargetId !== null}
      onCancel={() => {
        setReceiveTargetId(null)
        setReceiveReceiptId(null)
      }}
      onOk={handleReceive}
      confirmLoading={receiveSubmitting}
      okText="确认收货"
      width={460}
    >
      <Space direction="vertical" style={{ width: '100%' }} size={12}>
        <div>
          <div style={{ marginBottom: 4 }}>入库明细（仅列未收满项）</div>
          {/* TRADE-03：明细从详情接口加载（itemId/skuId/商品名/剩余量），不再手输明细 ID */}
          <Select<number | string>
            value={receiveItemId ?? undefined}
            onChange={(v) => {
              setReceiveItemId(v)
              const item = receiveItems.find((i) => String(i.id) === String(v))
              if (item) {
                setReceiveQuantity(item.expectedQuantity - item.receivedQuantity)
              }
            }}
            style={{ width: '100%' }}
            placeholder="选择入库明细"
            options={receiveItems.map((item) => ({
              value: item.id,
              label: `#${item.id} ${item.productName ?? `SKU ${item.skuId}`}（剩余 ${
                item.expectedQuantity - item.receivedQuantity
              }）`,
            }))}
          />
        </div>
        <div>
          <div style={{ marginBottom: 4 }}>实收数量（超收将被拒绝）</div>
          <InputNumber
            value={receiveQuantity}
            onChange={(v) => setReceiveQuantity(v)}
            precision={0}
            min={1}
            style={{ width: '100%' }}
            placeholder="本次实收数量"
          />
        </div>
        <div>
          <div style={{ marginBottom: 4 }}>质检结果</div>
          <Select
            value={receiveQuality}
            onChange={(v) => setReceiveQuality(v)}
            style={{ width: '100%' }}
            options={[
              { value: 'PASSED', label: '合格入库（计入可售）' },
              { value: 'QUARANTINE', label: '隔离验收（不计可售）' },
            ]}
          />
        </div>
        <Alert
          type="info"
          showIcon
          message={`收货流水号：${receiveReceiptId ?? '-'}（网络异常时请勿关弹窗，重试按此号幂等收敛）`}
        />
      </Space>
    </Modal>
  </>
  )
}
