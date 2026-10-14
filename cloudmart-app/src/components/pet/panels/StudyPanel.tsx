// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useState } from 'react'
import { Text, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { CreamButton, petCreamStyles } from '@/components/pet-cream'
import { petApi, type PetStudyItem } from '@/api/pet'
import { ChipButton } from '../shared'

/** 读书面板：挂载/重载信号触发拉取；上课与领奖后回调宿主刷新主宠状态 */
export function StudyPanel(
  { claimable, reloadSignal, onRefresh }: { claimable: boolean; reloadSignal: number; onRefresh: () => void },
) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [studies, setStudies] = useState<PetStudyItem[]>([])

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.listStudies()
      setStudies(res.data || [])
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    async function run() {
      await load()
    }
    void run()
  }, [load, reloadSignal])

  return (
    <View style={{ gap: Spacing.sm }}>
      {studies.map((study) => (
        <View key={String(study.configId)} style={petCreamStyles.questCard}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
            <View style={{ flex: 1, minWidth: 0 }}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{study.name}</Text>
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                {`⏱ ${Math.round(study.durationSeconds / 60)} 分钟`}
              </Text>
              <View style={petCreamStyles.rewardRow}>
                <Text style={petCreamStyles.rewardChip}>{`经验 +${study.expReward}`}</Text>
                <Text style={petCreamStyles.rewardChip}>{`智力 +${study.intelligenceReward}`}</Text>
              </View>
            </View>
            {study.eligible ? (
              <CreamButton onPress={async () => {
                try {
                  await petApi.startStudy(study.configId)
                  onRefresh()
                  void load()
                } catch { /* 拦截器已提示 */ }
              }}>上课</CreamButton>
            ) : (
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, flexShrink: 0 }}>{`Lv.${study.requiredLevel} 解锁`}</Text>
            )}
          </View>
        </View>
      ))}
      {claimable && (
        <ChipButton label="领取学习奖励" primary onPress={async () => {
          try {
            await petApi.claimStudy()
            onRefresh()
            void load()
          } catch { /* 已领取 409 */ }
        }} />
      )}
    </View>
  )
}
