import { useCallback, useRef, useState } from 'react'
import { ProTable, ModalForm, ProFormText, ProFormDigit, ProFormSelect, ProFormTextArea } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Segmented, Select, Space, Table, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  listAdminWishAssets,
  saveAdminWishAsset,
  toggleAdminWishAssetActive,
  deleteAdminWishAsset,
  listAdminWishBrands,
  auditAdminWishBrand,
  listAdminBrandPools,
  updateAdminBrandPool,
  publishAdminBrandPool,
  endAdminBrandPool,
  retryAdminBrandPoolSettlement,
  listAdminBrandPoolRewards,
} from '@/api/admin/wish'
import type { AdminWishAsset, AdminWishBrand, AdminBrandPool } from '@/api/admin/wish'
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

const POOL_STATUS_MAP: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  ACTIVE: { label: '进行中', color: 'green' },
  SETTLING: { label: '结算中', color: 'blue' },
  CONFIG_ERROR: { label: '结算失败', color: 'red' },
  ENDED: { label: '已结束', color: 'orange' },
  COMPLETED: { label: '已完成', color: 'purple' },
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
  const [tab, setTab] = useState<'assets' | 'brands' | 'pools'>('assets')
  const [editing, setEditing] = useState<AdminWishAsset | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [brands, setBrands] = useState<AdminWishBrand[]>([])
  const [brandsLoading, setBrandsLoading] = useState(false)
  // T11：品牌奖励池生命周期
  const [poolBrandId, setPoolBrandId] = useState<number | null>(null)
  const [pools, setPools] = useState<AdminBrandPool[]>([])
  const [poolsLoading, setPoolsLoading] = useState(false)
  const [editingPool, setEditingPool] = useState<AdminBrandPool | null>(null)
  const [poolEditOpen, setPoolEditOpen] = useState(false)
  const [poolRewards, setPoolRewards] = useState<{ pool: AdminBrandPool; rows: Array<Record<string, unknown>> } | null>(null)
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

  const loadPools = useCallback(async (brandId: number) => {
    setPoolsLoading(true)
    try {
      const res = await listAdminBrandPools(brandId)
      if (res.data.success) setPools(res.data.data ?? [])
    } finally {
      setPoolsLoading(false)
    }
  }, [])

  /** 进入奖励池页签：确保品牌列表已加载，默认选中第一个已批准品牌 */
  const ensurePoolsLoaded = useCallback(async () => {
    let list = brands
    if (list.length === 0) {
      setBrandsLoading(true)
      try {
        const res = await listAdminWishBrands()
        if (res.data.success) {
          list = res.data.data ?? []
          setBrands(list)
        }
      } finally {
        setBrandsLoading(false)
      }
    }
    const approved = list.filter((brand) => brand.status === 'APPROVED')
    const target = poolBrandId ?? approved[0]?.id ?? null
    if (target != null) {
      setPoolBrandId(target)
      await loadPools(target)
    } else {
      setPools([])
    }
  }, [brands, poolBrandId, loadPools])

  const poolAction = async (
    action: () => Promise<{ data: { success: boolean; error?: { message?: string } } }>,
    successText: string,
    brandId: number,
  ) => {
    const res = await action()
    if (res.data.success) {
      message.success(successText)
      await loadPools(brandId)
    } else {
      message.error(res.data.error?.message ?? '操作失败')
    }
  }

  const openRewards = async (pool: AdminBrandPool) => {
    const res = await listAdminBrandPoolRewards(pool.brandId, pool.id)
    if (res.data.success) {
      setPoolRewards({ pool, rows: res.data.data ?? [] })
    }
  }

  const handleAudit = async (brand: AdminWishBrand, status: 'APPROVED' | 'REJECTED') => {
    const res = await auditAdminWishBrand(brand.id, status)
    if (res.data.success) {
      message.success(`品牌「${brand.name}」已${status === 'APPROVED' ? '通过' : '驳回'}`)
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
            const next = v as 'assets' | 'brands' | 'pools'
            setTab(next)
            if (next === 'brands') loadBrands()
            if (next === 'pools') void ensurePoolsLoaded()
          }}
          options={[
            { value: 'assets', label: '虚拟资产' },
            { value: 'brands', label: '品牌入驻审核' },
            { value: 'pools', label: '品牌奖励池' },
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
      ) : tab === 'brands' ? (
        <ProTable<AdminWishBrand>
          headerTitle="品牌入驻审核"
          rowKey="id"
          search={false}
          loading={brandsLoading}
          dataSource={brands}
          pagination={false}
          columns={[
            { title: 'ID', dataIndex: 'id', width: 150, ellipsis: true },
            { title: '品牌名称', dataIndex: 'name', width: 180 },
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
      ) : (
        <div>
          <Space style={{ marginBottom: 12 }}>
            <span>品牌：</span>
            <Select
              style={{ width: 280 }}
              value={poolBrandId ?? undefined}
              loading={brandsLoading}
              placeholder="选择已通过审核的品牌"
              onChange={(value: number) => {
                setPoolBrandId(value)
                void loadPools(value)
              }}
              options={brands
                .filter((brand) => brand.status === 'APPROVED')
                .map((brand) => ({ value: brand.id, label: brand.name }))}
            />
            <Button onClick={() => poolBrandId != null && void loadPools(poolBrandId)}>刷新</Button>
            <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>
              规则发布后冻结；结束/重试均以服务端状态为准，不支持直接改结果
            </span>
          </Space>
          <ProTable<AdminBrandPool>
            headerTitle="品牌奖励池"
            rowKey="id"
            search={false}
            loading={poolsLoading}
            dataSource={pools}
            pagination={false}
            columns={[
              { title: 'ID', dataIndex: 'id', width: 150, ellipsis: true },
              { title: '池名称', dataIndex: 'name', width: 180 },
              {
                title: '名额（已参与/目标）',
                width: 160,
                render: (_, r) => `${r.currentCount ?? 0} / ${r.targetCount ?? 0}`,
              },
              { title: '规则版本', dataIndex: 'ruleVersion', width: 90, render: (v) => v ?? '-' },
              { title: '奖励配置', dataIndex: 'rewardJson', ellipsis: true, render: (v) => v || '-' },
              { title: '截止时间', dataIndex: 'endAt', width: 190, render: (v) => v || '-' },
              {
                title: '状态',
                dataIndex: 'status',
                width: 110,
                render: (_, r) => {
                  const m = POOL_STATUS_MAP[r.status]
                  return m ? <Tag color={m.color}>{m.label}</Tag> : r.status
                },
              },
              {
                title: '操作',
                valueType: 'option',
                width: 300,
                render: (_, record) => {
                  const actions = []
                  if (record.status === 'DRAFT' || record.status === 'ACTIVE') {
                    actions.push(
                      <Button
                        key="edit"
                        type="link"
                        size="small"
                        onClick={() => {
                          setEditingPool(record)
                          setPoolEditOpen(true)
                        }}
                      >
                        编辑
                      </Button>,
                    )
                  }
                  if (record.status === 'DRAFT') {
                    actions.push(
                      <Popconfirm
                        key="publish"
                        title="发布后规则冻结，确认发布？"
                        onConfirm={() =>
                          void poolAction(
                            () => publishAdminBrandPool(record.brandId, record.id),
                            '已发布',
                            record.brandId,
                          )
                        }
                      >
                        <Button type="link" size="small">发布</Button>
                      </Popconfirm>,
                    )
                  }
                  if (record.status === 'ACTIVE') {
                    actions.push(
                      <Popconfirm
                        key="end"
                        title="结束后不再接受加入，确认结束？"
                        onConfirm={() =>
                          void poolAction(
                            () => endAdminBrandPool(record.brandId, record.id),
                            '已结束',
                            record.brandId,
                          )
                        }
                      >
                        <Button type="link" size="small" danger>结束</Button>
                      </Popconfirm>,
                    )
                  }
                  if (record.status === 'CONFIG_ERROR') {
                    actions.push(
                      <Popconfirm
                        key="retry"
                        title="确认奖励配置已修复并重试结算？"
                        onConfirm={() =>
                          void poolAction(
                            () => retryAdminBrandPoolSettlement(record.brandId, record.id),
                            '已重试',
                            record.brandId,
                          )
                        }
                      >
                        <Button type="link" size="small">结算重试</Button>
                      </Popconfirm>,
                    )
                  }
                  actions.push(
                    <Button key="rewards" type="link" size="small" onClick={() => void openRewards(record)}>
                      奖励记录
                    </Button>,
                  )
                  return actions
                },
              },
            ]}
          />
        </div>
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

      <ModalForm<{ name: string; targetCount: number; rewardJson?: string; endAt?: string }>
        title={`编辑奖励池：${editingPool?.name ?? ''}`}
        open={poolEditOpen}
        onOpenChange={(open) => {
          setPoolEditOpen(open)
          if (!open) setEditingPool(null)
        }}
        onFinish={async (values) => {
          if (!editingPool) return false
          const res = await updateAdminBrandPool(editingPool.brandId, editingPool.id, {
            name: values.name?.trim(),
            targetCount: values.targetCount,
            rewardJson: values.rewardJson?.trim() || undefined,
            endAt: values.endAt?.trim() || undefined,
          })
          if (!res.data.success) {
            message.error(res.data.error?.message ?? '更新失败')
            return false
          }
          message.success('奖励池已更新')
          setPoolEditOpen(false)
          setEditingPool(null)
          await loadPools(editingPool.brandId)
          return true
        }}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        initialValues={
          editingPool
            ? {
                name: editingPool.name,
                targetCount: editingPool.targetCount,
                rewardJson: editingPool.rewardJson ?? undefined,
                endAt: editingPool.endAt ?? undefined,
              }
            : undefined
        }
      >
        <ProFormText name="name" label="池名称" rules={[{ required: true, message: '请输入池名称' }]} />
        <ProFormDigit name="targetCount" label="目标人数（名额）" min={1} rules={[{ required: true }]} />
        <ProFormTextArea name="rewardJson" label="奖励配置（JSON）" placeholder='{"starlight":100}' />
        <ProFormText name="endAt" label="截止时间" placeholder="2026-06-01T00:00:00" />
      </ModalForm>

      {poolRewards && (
        <div style={{ marginTop: 16 }}>
          <Space style={{ marginBottom: 8 }}>
            <strong>奖励记录：{poolRewards.pool.name}</strong>
            <Button size="small" onClick={() => setPoolRewards(null)}>收起</Button>
          </Space>
          <Table<Record<string, unknown>>
            rowKey={(row) => String(row.id)}
            size="small"
            pagination={false}
            dataSource={poolRewards.rows}
            columns={[
              { title: '记录ID', dataIndex: 'id', width: 160, ellipsis: true },
              { title: '用户ID', dataIndex: 'userId', width: 160, ellipsis: true },
              { title: '奖励类型', dataIndex: 'rewardType', width: 110 },
              { title: '状态', dataIndex: 'status', width: 100 },
              { title: '承诺金额', dataIndex: 'promisedAmount', width: 100 },
              { title: '实际入账', dataIndex: 'creditedAmount', width: 100 },
              { title: '重试次数', dataIndex: 'retryCount', width: 90 },
              { title: '失败原因', dataIndex: 'lastError', ellipsis: true },
              { title: '发放时间', dataIndex: 'grantedAt', width: 190 },
            ]}
          />
        </div>
      )}
    </div>
  )
}
