import { useCallback, useEffect, useState } from 'react'
import { Button, Modal, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  listAdminSoftwareSources,
  type AdminSoftwareSourceItem,
} from '@/api/admin/software'
import { useMessage } from '@/utils/useMessage'
import SourceStateModal from './SourceStateModal'

const AVAILABILITY_MAP: Record<string, { label: string; color: string }> = {
  UNKNOWN: { label: '待核实', color: 'default' },
  AVAILABLE: { label: '可用', color: 'success' },
  SUSPECTED: { label: '可疑', color: 'warning' },
  INVALID: { label: '已失效', color: 'error' },
  DISABLED: { label: '平台禁用', color: 'error' },
}

const PLATFORM_LABEL: Record<string, string> = {
  WINDOWS: 'Windows',
  MACOS: 'macOS',
  LINUX: 'Linux',
  ANDROID: 'Android',
  IOS: 'iOS',
  CROSS_PLATFORM: '跨平台',
}

/**
 * SW06：后台来源管理（按业务版本/平台/架构）。
 *
 * <p>不要求先有用户反馈即可核验/禁用/恢复；链接默认脱敏，提取码与解压密码
 * 不进入该视图、不支持导出。处置复用 {@link SourceStateModal} 与同一 API。</p>
 */
export default function SoftwareSourcesModal({
  softwareId,
  open,
  onClose,
}: {
  softwareId: string
  open: boolean
  onClose: () => void
}) {
  const message = useMessage()
  const [rows, setRows] = useState<AdminSoftwareSourceItem[]>([])
  const [loading, setLoading] = useState(false)
  const [target, setTarget] = useState<AdminSoftwareSourceItem | null>(null)

  const load = useCallback(async () => {
    if (!softwareId) return
    setLoading(true)
    try {
      const res = await listAdminSoftwareSources(softwareId)
      if (!res.data.success) {
        message.error(res.data.error?.message ?? '加载来源失败')
        return
      }
      setRows(res.data.data ?? [])
    } catch {
      message.error('加载来源失败')
    } finally {
      setLoading(false)
    }
  }, [message, softwareId])

  useEffect(() => {
    if (open) void load()
  }, [load, open])

  const columns: ColumnsType<AdminSoftwareSourceItem> = [
    {
      title: '业务版本',
      dataIndex: 'versionName',
      width: 180,
      render: (_, record) => (
        <Space size={4}>
          <span>{record.versionName}</span>
          <Tag>{record.channel === 'BETA' ? '测试版' : '稳定版'}</Tag>
        </Space>
      ),
    },
    {
      title: '平台/架构',
      width: 170,
      render: (_, record) =>
        `${PLATFORM_LABEL[record.platform ?? ''] ?? record.platform ?? '-'} / ${record.arch ?? '-'}`,
    },
    { title: '展示名', dataIndex: 'label', width: 140, render: (v) => v || '-' },
    {
      title: '链接（脱敏）',
      dataIndex: 'maskedUrl',
      ellipsis: true,
      render: (v) => v || '-',
    },
    {
      title: '状态',
      dataIndex: 'availability',
      width: 100,
      render: (v: string) => {
        const info = AVAILABILITY_MAP[v] ?? { label: v, color: 'default' }
        return <Tag color={info.color}>{info.label}</Tag>
      },
    },
    {
      title: '处置原因',
      dataIndex: 'statusReason',
      width: 180,
      ellipsis: true,
      render: (v) => v || '-',
    },
    {
      title: '操作',
      width: 90,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => setTarget(record)}>
          处置
        </Button>
      ),
    },
  ]

  return (
    <>
      <Modal
        title="来源管理"
        open={open}
        onCancel={onClose}
        footer={null}
        width={960}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          软件 {softwareId} 当前线上快照的来源。核实/禁用/恢复均写入审计；链接已脱敏，
          提取码与解压密码不在此展示、不支持导出。
        </Typography.Paragraph>
        <Table
          rowKey="sourceKey"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={false}
          scroll={{ x: 900 }}
          locale={{ emptyText: '当前线上内容暂无来源' }}
        />
      </Modal>

      <SourceStateModal
        open={!!target}
        softwareId={softwareId}
        sourceKey={target?.sourceKey ?? ''}
        contextLabel={target ? `${target.versionName} / ${target.platform ?? '-'} / ${target.arch ?? '-'}` : undefined}
        onClose={() => setTarget(null)}
        onSuccess={load}
      />
    </>
  )
}
