import { View, Text, ScrollView, Switch, ActivityIndicator, TouchableOpacity } from 'react-native'
import { useState, useEffect } from "react"
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import type { NotificationChannel, NotificationType } from '@/types'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

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
 * 通知偏好（Sprint 2.5，四AB B11 APP 端）：
 * 13 类通知 × 4 渠道开关矩阵；无记录项默认开启；变更即时 upsert（失败回滚）。
 */
export default function NotificationPrefsScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [matrix, setMatrix] = useState<Matrix | null>(null)
  const [loading, setLoading] = useState(true)
  const [savingKey, setSavingKey] = useState<string | null>(null)

  useEffect(() => {
    if (!isLoggedIn) {
      router.replace('/login')
      return
    }
    wishApi.getNotificationPreferences()
      .then((res) => { if (res.data?.success) setMatrix(res.data.data) })
      .catch(() => undefined)
      .finally(() => setLoading(false))
  }, [isLoggedIn])

  const isEnabled = (type: NotificationType, channel: NotificationChannel): boolean => {
    const row = matrix?.preferences.find((p) => p.type === type)
    return row ? row.channels[channel] !== false : true
  }

  const handleToggle = async (type: NotificationType, channel: NotificationChannel, enabled: boolean) => {
    const key = `${type}:${channel}`
    setSavingKey(key)
    setMatrix((prev) => prev ? {
      preferences: prev.preferences.some((p) => p.type === type)
        ? prev.preferences.map((p) => (p.type === type ? { ...p, channels: { ...p.channels, [channel]: enabled } } : p))
        : [...prev.preferences, { type, channels: { [channel]: enabled } as Record<NotificationChannel, boolean> }],
    } : prev)
    try {
      const res = await wishApi.updateNotificationPreferences([{ type, channel, enabled }])
      if (!res.data?.success) throw new Error('save failed')
    } catch {
      setMatrix((prev) => prev ? {
        preferences: prev.preferences.map((p) => (p.type === type ? { ...p, channels: { ...p.channels, [channel]: !enabled } } : p)),
      } : prev)
      alert('保存失败，请稍后重试')
    } finally {
      setSavingKey(null)
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: Spacing.lg, paddingBottom: Spacing.sm }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.accentCyan }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>通知偏好</Text>
        <View style={{ width: 48 }} />
      </View>

      {loading || !matrix ? (
        <ActivityIndicator color={WishColors.accentCyan} style={{ marginTop: 80 }} />
      ) : (
        <ScrollView contentContainerStyle={{ padding: Spacing.md }}>
          <View style={{ backgroundColor: WishColors.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.sm }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: Spacing.sm, borderBottomWidth: 1, borderBottomColor: WishColors.border }}>
              <Text style={{ flex: 1.6, fontSize: FontSize.xs, color: WishColors.textTertiary }}>通知类型</Text>
              {CHANNELS.map((c) => (
                <Text key={c.key} style={{ width: 52, textAlign: 'center', fontSize: FontSize.xs, color: WishColors.textTertiary }}>{c.label}</Text>
              ))}
            </View>
            {matrix.preferences.map((row) => (
              <View key={row.type} style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: Spacing.sm, borderBottomWidth: 1, borderBottomColor: WishColors.border }}>
                <Text style={{ flex: 1.6, fontSize: FontSize.sm, color: WishColors.text }}>
                  {TYPE_LABELS[row.type] ?? row.type}
                </Text>
                {CHANNELS.map((c) => (
                  <View key={c.key} style={{ width: 52, alignItems: 'center' }}>
                    <Switch
                      value={isEnabled(row.type, c.key)}
                      disabled={savingKey === `${row.type}:${c.key}`}
                      onValueChange={(v) => void handleToggle(row.type, c.key, v)}
                      trackColor={{ true: WishColors.accentCyan, false: WishColors.border }}
                    />
                  </View>
                ))}
              </View>
            ))}
          </View>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: Spacing.md, lineHeight: 18 }}>
            开关即时保存 · 关闭某类通知后将不再通过对应渠道提醒你
          </Text>
        </ScrollView>
      )}
    </View>
  )
}
