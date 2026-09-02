import { useEffect, useState } from 'react'
import { View, Text, ScrollView, Switch } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import type { NotificationChannel, NotificationType } from '@/types'
import styles from './index.module.scss'

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

type Matrix = { preferences: Array<{ type: NotificationType; channels: Record<NotificationChannel, boolean> }> }

/**
 * 通知偏好（Sprint 2.5，四AB B11 移动端）：
 * 13 类通知 × 4 渠道开关矩阵；无记录项默认开启；变更即时 upsert。
 */
export default function NotificationPrefsPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [matrix, setMatrix] = useState<Matrix | null>(null)
  const [loading, setLoading] = useState(true)
  const [savingKey, setSavingKey] = useState<string | null>(null)

  useEffect(() => {
    if (!isLoggedIn) return
    wishApi.getNotificationPreferences()
      .then((res) => { if (res.data.success) setMatrix(res.data.data) })
      .catch(() => Taro.showToast({ title: '偏好加载失败', icon: 'none' }))
      .finally(() => setLoading(false))
  }, [isLoggedIn])

  const isEnabled = (type: NotificationType, channel: NotificationChannel): boolean => {
    const row = matrix?.preferences.find((p) => p.type === type)
    return row ? row.channels[channel] !== false : true
  }

  const handleToggle = async (type: NotificationType, channel: NotificationChannel, enabled: boolean) => {
    const key = `${type}:${channel}`
    setSavingKey(key)
    // 乐观更新（失败回滚）
    setMatrix((prev) => prev ? {
      preferences: prev.preferences.some((p) => p.type === type)
        ? prev.preferences.map((p) => (p.type === type ? { ...p, channels: { ...p.channels, [channel]: enabled } } : p))
        : [...prev.preferences, { type, channels: { [channel]: enabled } as Record<NotificationChannel, boolean> }],
    } : prev)
    try {
      const res = await wishApi.updateNotificationPreferences([{ type, channel, enabled }])
      if (!res.data.success) throw new Error('save failed')
    } catch {
      setMatrix((prev) => prev ? {
        preferences: prev.preferences.map((p) => (p.type === type ? { ...p, channels: { ...p.channels, [channel]: !enabled } } : p)),
      } : prev)
      Taro.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
    } finally {
      setSavingKey(null)
    }
  }

  return (
    <View className={styles.page} style={{ paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title='通知偏好' back />
      <ScrollView className={styles.list} scrollY>
        {!isLoggedIn ? (
          <View className={styles.empty}><Text>请先登录</Text></View>
        ) : loading || !matrix ? (
          <View className={styles.empty}><Text>加载中...</Text></View>
        ) : (
          <View className={styles.matrix}>
            <View className={styles.headerRow}>
              <Text className={styles.headerType}>通知类型</Text>
              {CHANNELS.map((c) => (
                <Text key={c.key} className={styles.headerChannel}>{c.label}</Text>
              ))}
            </View>
            {matrix.preferences.map((row) => (
              <View key={row.type} className={styles.typeRow}>
                <Text className={styles.typeName}>{TYPE_LABELS[row.type] ?? row.type}</Text>
                {CHANNELS.map((c) => (
                  <View key={c.key} className={styles.switchWrap}>
                    <Switch
                      checked={isEnabled(row.type, c.key)}
                      disabled={savingKey === `${row.type}:${c.key}`}
                      color='#4a90d9'
                      onChange={(e) => void handleToggle(row.type, c.key, e.detail.value)}
                    />
                  </View>
                ))}
              </View>
            ))}
            <Text className={styles.hint}>开关即时保存 · 关闭某类通知后将不再通过对应渠道提醒你</Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}
