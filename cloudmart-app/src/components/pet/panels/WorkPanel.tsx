// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useState } from 'react'
import { Text, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { CreamButton, petCreamStyles } from '@/components/pet-cream'
import { petApi, type PetJobItem } from '@/api/pet'
import { ChipButton } from '../shared'

/** 打工面板：挂载/重载信号触发拉取；接单与领奖后回调宿主刷新主宠状态 */
export function WorkPanel(
  { claimable, reloadSignal, onRefresh }: { claimable: boolean; reloadSignal: number; onRefresh: () => void },
) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [jobs, setJobs] = useState<PetJobItem[]>([])

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.listJobs()
      setJobs(res.data || [])
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load, reloadSignal])

  return (
    <View style={{ gap: Spacing.sm }}>
      {jobs.map((job) => (
        <View key={String(job.configId)} style={petCreamStyles.questCard}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
            <View style={{ flex: 1, minWidth: 0 }}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{job.name}</Text>
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                {`⏱ ${Math.round(job.durationSeconds / 60)} 分钟 · ⚡-${job.energyCost}`}
              </Text>
              <View style={petCreamStyles.rewardRow}>
                <Text style={petCreamStyles.rewardChip}>{`经验 +${job.expReward}`}</Text>
                <Text style={petCreamStyles.rewardChip}>{`宠物币 +${job.currencyReward}`}</Text>
              </View>
            </View>
            {job.eligible ? (
              <CreamButton onPress={async () => {
                try {
                  await petApi.startWork(job.configId)
                  onRefresh()
                  void load()
                } catch { /* 拦截器已提示 */ }
              }}>接单</CreamButton>
            ) : (
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, flexShrink: 0 }}>{`Lv.${job.requiredLevel} 解锁`}</Text>
            )}
          </View>
        </View>
      ))}
      {claimable && (
        <ChipButton label="领取打工奖励" primary onPress={async () => {
          try {
            await petApi.claimWork()
            onRefresh()
            void load()
          } catch { /* 已领取 409 */ }
        }} />
      )}
    </View>
  )
}
