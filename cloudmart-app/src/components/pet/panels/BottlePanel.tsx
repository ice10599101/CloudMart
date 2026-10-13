// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useState } from 'react'
import { Text, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { CreamButton, petCreamStyles } from '@/components/pet-cream'
import { petApi, type PetBottleStatus } from '@/api/pet'
import { formatClock, useCountdown } from '../shared'

/**
 * 捞瓶面板：挂载/重载信号拉取状态。
 * 领取（onClaim）由宿主处理——稀有结果走宿主弹层、普通结果跳转邂逅瓶列表；
 * 宿主刷新后递增 reloadSignal，面板据此重新拉取状态。
 */
export function BottlePanel(
  { reloadSignal, onRefresh, onClaim }: { reloadSignal: number; onRefresh: () => void; onClaim: () => Promise<void> },
) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [bottle, setBottle] = useState<PetBottleStatus | null>(null)
  const bottleRemaining = useCountdown(bottle?.fishing ? bottle.finishedAt : null)

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.getBottleStatus()
      setBottle(res.data)
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load, reloadSignal])

  if (!bottle) return null

  return (
    <View style={petCreamStyles.questCard}>
      <Text style={{ color: colors.text, fontSize: FontSize.sm }}>🌊 {bottle.unlockedArea} · 估算成功率 {Math.round(bottle.estimatedSuccessRate * 100)}%</Text>
      {bottle.fishing
        ? <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>🐾 捞瓶中…剩余 {formatClock(bottleRemaining)}</Text>
        : (
          <CreamButton
            disabled={bottle.cooldownRemainingSeconds > 0 && !bottle.canClaim}
            onPress={async () => {
              try {
                if (bottle.canClaim) {
                  await onClaim()
                  return
                }
                await petApi.startBottle()
                void load()
                onRefresh()
              } catch { /* 拦截器已提示 */ }
            }}
          >
            {bottle.canClaim ? '查看捞瓶结果' : bottle.cooldownRemainingSeconds > 0 ? `冷却中 ${Math.ceil(bottle.cooldownRemainingSeconds / 60)} 分钟` : '让宠物去捞漂流瓶'}
          </CreamButton>
        )}
      {bottle.lastOutcome === 'EMPTY' && (
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>这次空手而归啦，休息一下再试试～</Text>
      )}
      {bottle.lastOutcome === 'FAILED' && (
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>心愿服务暂时不可用，点上面的按钮可以重试领取</Text>
      )}
    </View>
  )
}
