import { useState } from 'react'
import {
  Button,
  Card,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  approvePetAlbumAsset,
  listPetConfigGovernanceHistory,
  PET_CONFIG_GOVERNANCE_TYPES,
  rollbackPetConfigGovernance,
  validatePetConfigGovernance,
  type AdminPetConfigVersion,
} from '@/api/admin/pet'

const { Text } = Typography

// ==================== BE-11 相册审核 / B21 配置治理 ====================

/** 相册审核（按资产 ID 通过）+ 配置治理三件套（校验预览/历史/回退） */
export function GovernancePanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const typeOptions = PET_CONFIG_GOVERNANCE_TYPES.map((t) => ({ value: t, label: t }))
  // 相册审核
  const [assetId, setAssetId] = useState<number | null>(null)
  const [approving, setApproving] = useState(false)
  // 校验预览
  const [validateType, setValidateType] = useState<string>('job')
  const [validateJson, setValidateJson] = useState('{\n  "durationSeconds": 300,\n  "expReward": 50\n}')
  const [validating, setValidating] = useState(false)
  // 历史/回退
  const [historyType, setHistoryType] = useState<string>('job')
  const [historyConfigId, setHistoryConfigId] = useState<number | null>(null)
  const [versions, setVersions] = useState<AdminPetConfigVersion[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)

  const handleApprove = async () => {
    if (assetId === null) {
      return
    }
    setApproving(true)
    try {
      await approvePetAlbumAsset(assetId)
      messageApi.success(`相册资源 #${assetId} 已审核通过`)
      setAssetId(null)
    } catch {
      // 拦截器已提示
    } finally {
      setApproving(false)
    }
  }

  const handleValidate = async () => {
    let data: Record<string, unknown>
    try {
      data = validateJson.trim() ? JSON.parse(validateJson) as Record<string, unknown> : {}
    } catch {
      messageApi.error('JSON 格式非法')
      return
    }
    setValidating(true)
    try {
      await validatePetConfigGovernance({ configType: validateType, data })
      messageApi.success('校验通过')
    } catch {
      // 拦截器已提示
    } finally {
      setValidating(false)
    }
  }

  const loadHistory = async () => {
    if (historyConfigId === null) {
      messageApi.warning('请先填写配置 ID')
      return
    }
    setHistoryLoading(true)
    try {
      const { data: res } = await listPetConfigGovernanceHistory({ configType: historyType, configId: historyConfigId })
      if (res.success) {
        setVersions(res.data ?? [])
      }
    } catch {
      // 拦截器已提示
    } finally {
      setHistoryLoading(false)
    }
  }

  const handleRollback = async (row: AdminPetConfigVersion) => {
    if (historyConfigId === null) {
      return
    }
    try {
      await rollbackPetConfigGovernance({ configType: historyType, configId: historyConfigId, version: row.version })
      messageApi.success(`已回退到版本 v${row.version}`)
      void loadHistory()
    } catch {
      // 拦截器已提示
    }
  }

  const versionColumns: ColumnsType<AdminPetConfigVersion> = [
    { title: '版本', dataIndex: 'version', width: 70, render: (v) => `v${v}` },
    {
      title: '操作',
      dataIndex: 'operation',
      width: 100,
      render: (v: AdminPetConfigVersion['operation']) => (
        <Tag color={v === 'PUBLISH' ? 'blue' : 'orange'}>{v}</Tag>
      ),
    },
    { title: '操作人', dataIndex: 'operator', width: 120 },
    { title: '时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作',
      width: 90,
      render: (_: unknown, row) => (
        <Popconfirm title={`确认回退到 v${row.version}？快照字段将写回当前配置。`} onConfirm={() => void handleRollback(row)}>
          <Button type="link" size="small">回退</Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <div>
      {contextHolder}
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card type="inner" title="相册资源审核（BE-11）">
          <Space>
            <InputNumber
              placeholder="相册资源 ID"
              value={assetId}
              onChange={(v) => setAssetId(v)}
              precision={0}
              min={1}
              style={{ width: 180 }}
            />
            <Popconfirm title="确认审核通过？通过后用户端可见。" disabled={assetId === null} onConfirm={() => void handleApprove()}>
              <Button type="primary" loading={approving} disabled={assetId === null}>审核通过</Button>
            </Popconfirm>
            <Text type="secondary">用户上传的相册资源为 PENDING，仅审核链路可设 APPROVED</Text>
          </Space>
        </Card>
        <Card type="inner" title="配置校验预览（B21）">
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Space>
              <Select value={validateType} onChange={setValidateType} options={typeOptions} style={{ width: 180 }} />
              <Button type="primary" loading={validating} onClick={() => void handleValidate()}>校验</Button>
              <Text type="secondary">数值上下限组合校验，不落库</Text>
            </Space>
            <Input.TextArea
              value={validateJson}
              onChange={(e) => setValidateJson(e.target.value)}
              rows={6}
              style={{ fontFamily: 'monospace' }}
              placeholder='配置字段 JSON，例如 {"durationSeconds": 300}'
            />
          </Space>
        </Card>
        <Card type="inner" title="配置历史与回退（B21）">
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Space>
              <Select value={historyType} onChange={setHistoryType} options={typeOptions} style={{ width: 180 }} />
              <InputNumber
                placeholder="配置 ID"
                value={historyConfigId}
                onChange={(v) => setHistoryConfigId(v)}
                precision={0}
                min={1}
                style={{ width: 160 }}
              />
              <Button loading={historyLoading} onClick={() => void loadHistory()}>查询历史</Button>
              <Text type="secondary">最近 50 条发布/回退快照</Text>
            </Space>
            <Table
              rowKey={(row) => String(row.id)}
              size="small"
              loading={historyLoading}
              columns={versionColumns}
              dataSource={versions}
              pagination={false}
              locale={{ emptyText: '暂无历史版本' }}
            />
          </Space>
        </Card>
      </Space>
    </div>
  )
}
