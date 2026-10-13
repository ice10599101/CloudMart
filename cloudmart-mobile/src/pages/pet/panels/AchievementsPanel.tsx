import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Text, View } from '@tarojs/components'
import { petApi, type PetAchievement, type PetReminder } from '@/api/pet'
import { friendlyError } from '../constants'
import styles from '../index.module.scss'

/**
 * 成就面板：宠物动态（提醒同口径，前 5 条）+ 成就墙（R30 自 index.tsx 拆出，行为不变）。
 */
export function AchievementsPanel() {
  const [achievements, setAchievements] = useState<PetAchievement[]>([])
  const [reminders, setReminders] = useState<PetReminder[]>([])

  const load = useCallback(async () => {
    try {
      // 成就墙 + 宠物动态（提醒即动态数据源，与 Web 端同一口径）
      const [achRes, remRes] = await Promise.all([petApi.listAchievements(), petApi.listReminders()])
      setAchievements(achRes.data.data || [])
      setReminders(remRes.data.data || [])
    } catch (error) {
      Taro.showToast({ title: friendlyError(error), icon: 'none' })
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <View>
      <Text className={styles.sectionTitle}>🫧 宠物动态</Text>
      {reminders.length === 0 ? (
        <Text className={styles.tip}>还没有动态，去陪它玩一会吧～</Text>
      ) : (
        reminders.slice(0, 5).map((item) => (
          <View key={String(item.notificationId)} className={styles.careCard}>
            <Text className={styles.careTitle}>{item.title}</Text>
            <Text className={styles.careMeta}>{item.content}</Text>
          </View>
        ))
      )}
      <Text className={styles.sectionTitle}>🏆 成就墙</Text>
      <View className={styles.achGrid}>
        {achievements.map((item) => (
          <View key={String(item.achievementId)} className={`${styles.achCard} ${item.achieved ? '' : styles.achLocked}`}>
            <Text className={styles.achIcon}>{item.icon}</Text>
            <Text className={styles.achName}>{item.name}</Text>
            <Text className={styles.achDesc}>{item.description}</Text>
            <Text className={styles.historyMeta}>{item.achieved ? `已达成 +${item.expReward}` : `未达成 +${item.expReward}`}</Text>
          </View>
        ))}
      </View>
    </View>
  )
}
