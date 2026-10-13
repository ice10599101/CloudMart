// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useEffect, useState } from 'react'
import { Text, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { petCreamStyles } from '@/components/pet-cream'
import { petApi, type PetAchievement, type PetReminder } from '@/api/pet'

/** 成就面板：成就墙 + 宠物动态（提醒即"宠物动态"数据源，与 Web 端同一展示口径） */
export function AchievementsPanel() {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [reminders, setReminders] = useState<PetReminder[]>([])
  const [achievements, setAchievements] = useState<PetAchievement[]>([])

  useEffect(() => {
    void (async () => {
      try {
        const [achRes, remRes] = await Promise.all([petApi.listAchievements(), petApi.listReminders()])
        setAchievements(achRes.data.data || [])
        setReminders(remRes.data.data || [])
      } catch {
        // 展示型数据：忽略
      }
    })()
  }, [])

  return (
    <View style={{ gap: Spacing.sm }}>
      <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>🫧 宠物动态</Text>
      {reminders.length === 0 ? (
        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有动态，去陪它玩一会吧～</Text>
      ) : (
        reminders.slice(0, 5).map((item) => (
          <View key={String(item.notificationId)} style={petCreamStyles.questCard}>
            <Text style={{ color: colors.text, fontSize: FontSize.xs, fontWeight: '600' }}>{item.title}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: 10 }}>{item.content}</Text>
          </View>
        ))
      )}
      <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>🏆 成就墙</Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
        {achievements.map((item) => (
          <View
            key={String(item.achievementId)}
            style={[petCreamStyles.questCard, {
              width: '47%', alignItems: 'center', opacity: item.achieved ? 1 : 0.45,
            }]}
          >
            <Text style={{ fontSize: 30 }}>{item.icon}</Text>
            <Text style={{ color: colors.text, fontSize: FontSize.xs, fontWeight: '600' }}>{item.name}</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10, textAlign: 'center' }}>{item.description}</Text>
            <Text style={{ color: colors.primary, fontSize: 10, marginTop: 2 }}>
              {item.achieved ? `已达成 +${item.expReward}` : `未达成 +${item.expReward}`}
            </Text>
          </View>
        ))}
      </View>
    </View>
  )
}
