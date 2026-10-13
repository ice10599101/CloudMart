import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Text, View } from '@tarojs/components'
import { notificationApi } from '@/api/notification'
import { petApi, petCompanionApi, type PetReminder } from '@/api/pet'
import { PRIORITY_LABEL, friendlyError } from '../constants'
import styles from '../index.module.scss'

/**
 * 提醒面板：宠物想对你说（P0/P1/P2 优先级、未读高亮、单条点击已读、全部已读）。
 * R30 自 index.tsx 拆出，行为不变；未读数变化经 onUnreadChanged 回传主页宫格角标。
 */
export function RemindersPanel({ onUnreadChanged }: { onUnreadChanged?: (unread: number) => void }) {
  const [reminders, setReminders] = useState<PetReminder[]>([])
  const [reminderUnread, setReminderUnread] = useState(0)

  const toast = (msg: string) => Taro.showToast({ title: msg, icon: 'none' })

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.listReminders()
      setReminders(res.data || [])
      petApi.getReminderUnreadCount()
        .then(({ data: countRes }) => {
          const count = countRes.data ?? 0
          setReminderUnread(count)
          onUnreadChanged?.(count)
        })
        .catch(() => undefined)
    } catch (error) {
      toast(friendlyError(error))
    }
  }, [onUnreadChanged])

  useEffect(() => {
    void load()
  }, [load])

  const markReminderRead = async (item: PetReminder) => {
    if (item.isRead) return
    try {
      await notificationApi.markRead(item.notificationId)
      setReminders((prev) => prev.map((r) => (r.notificationId === item.notificationId ? { ...r, isRead: true } : r)))
      setReminderUnread((n) => {
        const next = Math.max(0, n - 1)
        onUnreadChanged?.(next)
        return next
      })
    } catch (error) {
      toast(friendlyError(error))
    }
  }

  const markAllRemindersRead = async () => {
    try {
      // 宠物提醒与站内通知是两条链路：此处必须走 /pet/reminders/read-all
      await petCompanionApi.markAllRemindersRead()
      setReminders((prev) => prev.map((r) => ({ ...r, isRead: true })))
      setReminderUnread(0)
      onUnreadChanged?.(0)
    } catch (error) {
      toast(friendlyError(error))
    }
  }

  return (
    <View>
      <View className={styles.modalRow}>
        <Text className={styles.modalLabel}>宠物想对你说（{reminderUnread} 条未读）</Text>
        <Button className={styles.miniBtnGhost} onClick={markAllRemindersRead}>全部已读</Button>
      </View>
      {reminders.length === 0 && (
        <Text className={styles.tip}>还没有提醒～宠物会在打工完成、捞到漂流瓶、有人评论你时主动开口</Text>
      )}
      {reminders.map((item) => (
        <View
          key={String(item.notificationId)}
          className={`${styles.reminderCard} ${item.isRead ? styles.reminderRead : styles.reminderUnread}`}
          onClick={() => markReminderRead(item)}
        >
          <View className={styles.reminderTitleRow}>
            <Text className={styles.reminderTitle}>{item.title}</Text>
            <Text className={`${styles.priorityTag} ${item.priority === 'P0' ? styles.priorityP0 : item.priority === 'P1' ? styles.priorityP1 : ''}`}>
              {PRIORITY_LABEL[item.priority ?? 'P2']}
            </Text>
          </View>
          <Text className={styles.reminderContent}>{item.content}</Text>
        </View>
      ))}
    </View>
  )
}
