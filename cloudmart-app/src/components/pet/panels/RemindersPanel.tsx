// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useEffect, useState } from 'react'
import { Text, TouchableOpacity, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme, PetCreamSemantic } from '@/constants/pet-cream'
import { notificationApi } from '@/api/notification'
import { petApi, type PetReminder } from '@/api/pet'
import { ChipButton, PRIORITY_LABEL } from '../shared'

/** 提醒面板：自管列表与已读态；未读数变化经 onUnreadChange 回传宿主刷新宫格角标 */
export function RemindersPanel({ onUnreadChange }: { onUnreadChange: (unread: number) => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [reminders, setReminders] = useState<PetReminder[]>([])

  useEffect(() => {
    void (async () => {
      try {
        const { data: res } = await petApi.listReminders()
        const list = res.data || []
        setReminders(list)
        onUnreadChange(list.filter((item) => !item.isRead).length)
      } catch {
        // 拦截器已提示
      }
    })()
    // 仅挂载时拉取一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const markReminderRead = async (item: PetReminder) => {
    if (item.isRead) return
    try {
      await notificationApi.markRead(item.notificationId)
      setReminders((prev) => {
        const next = prev.map((r) => (r.notificationId === item.notificationId ? { ...r, isRead: true } : r))
        onUnreadChange(next.filter((r) => !r.isRead).length)
        return next
      })
    } catch {
      // 拦截器已提示
    }
  }

  const markAllRemindersRead = async () => {
    try {
      await notificationApi.markAllRead()
      setReminders((prev) => prev.map((r) => ({ ...r, isRead: true })))
      onUnreadChange(0)
    } catch {
      // 拦截器已提示
    }
  }

  return (
    <View style={{ gap: Spacing.sm }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>宠物想对你说（{reminders.filter((item) => !item.isRead).length} 条未读）</Text>
        <ChipButton label="全部已读" onPress={markAllRemindersRead} />
      </View>
      {reminders.length === 0 && (
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
          还没有提醒～宠物会在打工完成、捞到漂流瓶、有人评论你时主动开口
        </Text>
      )}
      {reminders.map((item) => (
        <TouchableOpacity
          key={String(item.notificationId)}
          onPress={() => markReminderRead(item)}
          style={{
            padding: Spacing.sm, borderRadius: BorderRadius.md, gap: 4,
            backgroundColor: item.isRead ? colors.bgBase : PetCreamSemantic.unreadHighlight,
            borderWidth: 1, borderColor: colors.border,
          }}
        >
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.xs }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600', flex: 1 }}>{item.title}</Text>
            <Text style={{
              fontSize: 10, paddingHorizontal: 6, paddingVertical: 2, borderRadius: 999,
              color: item.priority === 'P0' ? '#fff' : colors.textTertiary,
              backgroundColor: item.priority === 'P0' ? PetCreamSemantic.danger : item.priority === 'P1' ? colors.primary : colors.border,
            }}>
              {PRIORITY_LABEL[item.priority ?? 'P2']}
            </Text>
          </View>
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{item.content}</Text>
        </TouchableOpacity>
      ))}
    </View>
  )
}
