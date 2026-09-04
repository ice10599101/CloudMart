import { useCallback, useRef, useState } from 'react'
import { ProTable, ModalForm, ProFormText, ProFormDigit, ProFormSelect, ProFormTextArea } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Segmented, Space, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  listAdminWishAssets,
  saveAdminWishAsset,
  toggleAdminWishAssetActive,
  deleteAdminWishAsset,
  listAdminWishBrands,
  auditAdminWishBrand,
} from '@/api/admin/wish'
import type { AdminWishAsset, AdminWishBrand } from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'
import AssetIcon from '@/components/AssetIcon'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

/**
 * 心愿资产管理 + 品牌入驻审核（Sprint 3.6 管理后台收尾，四AB B3/B4）：
 * - 资产配置表化：新增皮肤/BGM/特殊果实仅插入配置行，支持上下架
 * - 品牌入驻审核：PENDING → APPROVED/REJECTED（APPROVED 后进入公开列表）
 */

const ASSET_TYPE_LABELS: Record<string, string> = {
  SKIN: '树皮肤',
  BGM: '背景音乐',
  SPECIAL_FRUIT: '特殊果实',
}

const BRAND_STATUS_MAP: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待审核', color: 'orange' },
  APPROVED: { label: '已通过', color: 'green' },
  REJECTED: { label: '已驳回', color: 'red' },
}

interface AssetFormValues {
  assetType: 'SKIN' | 'BGM' | 'SPECIAL_FRUIT'
  name: string
  description?: string
  icon?: string
  priceStarlight: number
  stock: number
}

export default function WishAssetAdmin() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [tab, setTab] = useState<'assets' | 'brands'>('assets')
  const [editing, setEditing] = useState<AdminWishAsset | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [brands, setBrands] = useState<AdminWishBrand[]>([])
  const [brandsLoading, setBrandsLoading] = useState(false)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const loadBrands = useCallback(async () => {
    setBrandsLoading(true)
    try {
      const res = await listAdminWishBrands()
      if (res.data.success) setBrands(res.data.data ?? [])
    } finally {
      setBrandsLoading(false)
    }
  }, [])

  const handleAudit = async (brand: AdminWishBrand, status: 'APPROVED' | 'REJECTED') => {
    const res = await auditAdminWishBrand(brand.brandId, status)
    if (res.data.success) {
      message.success(`品牌「${brand.brandName}」已${status === 'APPROVED' ? '通过' : '驳回'}`)
      loadBrands()
    }
  }

  const handleSaveAsset = async (values: AssetFormValues) => {
    return confirmSubmit(async () => {
      const payload: Partial<AdminWishAsset> = {
        assetType: values.assetType,
        name: values.name.trim(),
        // 空串而非 null：MyBatis-Plus updateById 忽略 null 字段，清空需空串才能落库
        description: values.description?.trim() || '',
        icon: values.icon?.trim() || '',
        priceStarlight: values.priceStarlight,
        stock: values.stock,
        // 编辑时携带 id，后端按 id 走 updateById；缺失则视为新建
        ...(editing ? { id: editing.id } : {}),
      }
      await saveAdminWishAsset(payload)
      message.success(editing ? '资产已更新' : '资产已创建')
      actionRef.current?.reload()
    })
  }

  const assetColumns: ProColumns<AdminWishAsset>[] = [
    { title: 'ID', dataIndex: 'id', width: 150, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'assetType',
      width: 100,
      render: (_, r) => <Tag color="purple">{ASSET_TYPE_LABELS[r.assetType] ?? r.assetType}</Tag>,
    },
    {
      title: '图标',
      dataIndex: 'icon',
      width: 80,
      render: (_, r) => r.icon ? <AssetIcon icon={r.icon} alt={r.name} /> : <span style={{ color: 'var(--color-text-tertiary)' }}>-</span>,
    },
    { title: '名称', dataIndex: 'name', width: 160 },
    { title: '描述', dataIndex: 'description', ellipsis: true, render: (v) => v || '-' },
    { title: '星光价', dataIndex: 'priceStarlight', width: 90 },
    { title: '库存', dataIndex: 'stock', width: 80 },
    {
      title: '状态',
      dataIndex: 'isActive',
      width: 90,
      render: (_, r) => (r.isActive ? <Tag color="success">上架中</Tag> : <Tag color="default">已下架</Tag>),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, record) => [
        <Button
          key="edit"
          type="link"
          size="small"
          onClick={() => {
            setEditing(record)
            setCreateOpen(true)
          }}
        >
          编辑
        </Button>,
        <Popconfirm
          key="toggle"
          title={record.isActive ? '确认下架？用户将不可再获取' : '确认上架？'}
          onConfirm={async () => {
            const res = await toggleAdminWishAssetActive(record.id, !record.isActive)
            if (res.data.success) message.success(record.isActive ? '已下架' : '已上架')
            actionRef.current?.reload()
          }}
        >
          <Button type="link" size="small" danger={record.isActive}>
            {record.isActive ? '下架' : '上架'}
          </Button>
        </Popconfirm>,
        <Popconfirm
          key="delete"
          title="确认删除该资产？"
          description="删除后不可恢复；已有用户持有的资产将无法删除（请改用下架）"
          onConfirm={async () => {
            const res = await deleteAdminWishAsset(record.id)
            if (res.data.success) {
              message.success(`资产「${record.name}」已删除`)
              actionRef.current?.reload()
            }
          }}
        >
          <Button type="link" size="small" danger>
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Segmented
          value={tab}
          onChange={(v) => {
            setTab(v as 'assets' | 'brands')
            if (v === 'brands') loadBrands()
          }}
          options={[
            { value: 'assets', label: '虚拟资产' },
            { value: 'brands', label: '品牌入驻审核' },
          ]}
        />
      </Space>

      {tab === 'assets' ? (
        <ProTable<AdminWishAsset>
          headerTitle="心愿虚拟资产"
          actionRef={actionRef}
          rowKey="id"
          search={false}
          request={async () => safeProTableRequest<AdminWishAsset>(() => listAdminWishAssets())}
          toolBarRender={() => [
            <Button
              key="create"
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditing(null)
                setCreateOpen(true)
              }}
            >
              新增资产
            </Button>,
          ]}
          columns={assetColumns}
          pagination={false}
        />
      ) : (
        <ProTable<AdminWishBrand>
          headerTitle="品牌入驻审核"
          rowKey="brandId"
          search={false}
          loading={brandsLoading}
          dataSource={brands}
          pagination={false}
          columns={[
            { title: 'ID', dataIndex: 'brandId', width: 150, ellipsis: true },
            { title: '品牌名称', dataIndex: 'brandName', width: 180 },
            { title: '简介', dataIndex: 'description', ellipsis: true, render: (v) => v || '-' },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: (_, r) => {
                const m = BRAND_STATUS_MAP[r.status]
                return m ? <Tag color={m.color}>{m.label}</Tag> : r.status
              },
            },
            {
              title: '操作',
              valueType: 'option',
              width: 180,
              render: (_, record) =>
                record.status === 'PENDING'
                  ? [
                      <Popconfirm key="approve" title="确认通过该品牌入驻？" onConfirm={() => handleAudit(record, 'APPROVED')}>
                        <Button type="link" size="small">通过</Button>
                      </Popconfirm>,
                      <Popconfirm key="reject" title="确认驳回该品牌入驻？" onConfirm={() => handleAudit(record, 'REJECTED')}>
                        <Button type="link" size="small" danger>驳回</Button>
                      </Popconfirm>,
                    ]
                  : [<span key="done" style={{ color: 'var(--color-text-secondary)' }}>-</span>],
            },
          ]}
        />
      )}

      <ModalForm<AssetFormValues>
        title={editing ? `编辑资产：${editing.name}` : '新增资产'}
        open={createOpen}
        onOpenChange={createHandleOpenChange(setCreateOpen, () => setEditing(null))}
        onFinish={handleSaveAsset}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        initialValues={
          editing
            ? {
                assetType: editing.assetType,
                name: editing.name,
                description: editing.description ?? undefined,
                icon: editing.icon ?? undefined,
                priceStarlight: editing.priceStarlight,
                stock: editing.stock,
              }
            : { assetType: 'SKIN', priceStarlight: 50, stock: 100 }
        }
      >
        <ProFormSelect
          name="assetType"
          label="资产类型"
          rules={[{ required: true, message: '请选择资产类型' }]}
          options={Object.entries(ASSET_TYPE_LABELS).map(([value, label]) => ({ value, label }))}
        />
        <ProFormText
          name="name"
          label="资产名称"
          rules={[
            { required: true, message: '请输入资产名称' },
            { max: 64, message: '名称最长 64 字' },
          ]}
        />
        <ProFormTextArea name="description" label="描述" fieldProps={{ rows: 2, maxLength: 200 }} />
        <ProFormText name="icon" label="图标（emoji 或 URL）" placeholder="如 🌳 / https://..." />
        <ProFormDigit
          name="priceStarlight"
          label="星光价格"
          min={0}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入星光价格' }]}
        />
        <ProFormDigit
          name="stock"
          label="库存"
          min={0}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入库存' }]}
        />
      </ModalForm>
    </div>
  )
}
