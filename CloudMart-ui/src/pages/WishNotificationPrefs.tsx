import { useEffect, useState } from 'react'
import { Table, Switch, App } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { history } from 'umi'
import {
  getNotificationPreferences,
  updateNotificationPreferences,
  type NotificationChannel,
  type NotificationPreferenceMatrix,
  type NotificationType,
} from '@/api/wish'
import { useAuthStore } from '@/stores/auth'

const CHANNELS: { key: NotificationChannel; label: string }[] = [
  { key: 'PUSH', label: '推送' },
  { key: 'IN_APP', label: '站内' },
  { key: 'SMS', label: '短信' },
  { key: 'EMAIL', label: '邮件' },
]

const TYPE_LABELS: Record<NotificationType, string> = {
  WISH_COMMENT: '心愿评论',
  WISH_LIGHT: '点亮/同求',
  WISH_FULFILL: '心愿还愿',
  CAPSULE_OPEN: '胶囊开启',
  AI_REMINDER: 'AI 提醒',
  CHECKIN_REMINDER: '打卡提醒',
  MATCH_RECOMMEND: '小队推荐',
  BRAND_REWARD: '品牌奖励',
  ENCOUNTER_LETTER: '擦肩信笺',
  DEVICE_OFFLINE: '设备离线',
  LEVEL_UP: '等级提升',
  BADGE_EARNED: '徽章获得',
  SYSTEM: '系统公告',
}

interface MatrixRow {
  type: NotificationType
  channels: Record<NotificationChannel, boolean>
}

/** 无记录项默认开启：补全 13 类 × 4 渠道全矩阵 */
function normalizeMatrix(matrix: NotificationPreferenceMatrix): MatrixRow[] {
  const byType = new Map(matrix.preferences.map((p) => [p.type, p.channels]))
  return (Object.keys(TYPE_LABELS) as NotificationType[]).map((type) => {
    const channels = byType.get(type)
    return {
      type,
      channels: CHANNELS.reduce(
        (acc, c) => ({ ...acc, [c.key]: channels ? channels[c.key] !== false : true }),
        {} as Record<NotificationChannel, boolean>,
      ),
    }
  })
}

/**
 * 心愿通知偏好（Sprint 2.5，合规 34.6；对齐 Mobile notificationPrefs / APP notification-prefs）：
 * 13 类通知 × 4 渠道开关矩阵；无记录项默认开启；变更即时 upsert（乐观更新，失败回滚）。
 */
export default function WishNotificationPrefsPage() {
  const { message } = App.useApp()
  const { isAuthenticated } = useAuthStore()
  const [rows, setRows] = useState<MatrixRow[]>([])
  const [loading, setLoading] = useState(true)
  const [savingKey, setSavingKey] = useState<string | null>(null)

  useEffect(() => {
    if (!isAuthenticated) {
      history.push('/login?redirect=/wish/notification-prefs')
      return
    }
    getNotificationPreferences()
      .then((res) => {
        if (res.data.success && res.data.data) {
          setRows(normalizeMatrix(res.data.data))
        }
      })
      .catch(() => message.error('偏好加载失败'))
      .finally(() => setLoading(false))
  }, [isAuthenticated, message])

  const isEnabled = (type: NotificationType, channel: NotificationChannel): boolean => {
    const row = rows.find((r) => r.type === type)
    return row ? row.channels[channel] !== false : true
  }

  const handleToggle = async (type: NotificationType, channel: NotificationChannel, enabled: boolean) => {
    const key = `${type}:${channel}`
    setSavingKey(key)
    // 乐观更新（失败回滚）
    setRows((prev) =>
      prev.map((r) => (r.type === type ? { ...r, channels: { ...r.channels, [channel]: enabled } } : r)),
    )
    try {
      const res = await updateNotificationPreferences([{ type, channel, enabled }])
      if (!res.data.success) throw new Error('save failed')
    } catch {
      setRows((prev) =>
        prev.map((r) => (r.type === type ? { ...r, channels: { ...r.channels, [channel]: !enabled } } : r)),
      )
      message.error('保存失败，请稍后重试')
    } finally {
      setSavingKey(null)
    }
  }

  const columns: ColumnsType<MatrixRow> = [
    {
      title: '通知类型',
      dataIndex: 'type',
      key: 'type',
      width: 140,
      render: (type: NotificationType) => TYPE_LABELS[type] ?? type,
    },
    ...CHANNELS.map((c) => ({
      title: c.label,
      key: c.key,
      width: 100,
      align: 'center' as const,
      render: (_: unknown, record: MatrixRow) => (
        <Switch
          size='small'
          checked={isEnabled(record.type, c.key)}
          disabled={savingKey === `${record.type}:${c.key}`}
          onChange={(checked) => void handleToggle(record.type, c.key, checked)}
        />
      ),
    })),
  ]

  return (
    <div style={{ minHeight: '100vh', background: 'var(--color-bg-base)', padding: '40px 24px' }}>
      <div style={{ maxWidth: 720, margin: '0 auto' }}>
        <h1 style={{ fontSize: 24, fontWeight: 800, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
          心愿通知偏好
        </h1>
        <p style={{ fontSize: 13, color: 'var(--color-text-tertiary)', marginBottom: 24 }}>
          13 类心愿通知 × 4 个渠道的开关矩阵 · 切换即时保存 · 关闭某类通知后将不再通过对应渠道提醒你
        </p>
        <Table<MatrixRow>
          rowKey='type'
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={false}
          size='middle'
        />
      </div>
    </div>
  )
}
