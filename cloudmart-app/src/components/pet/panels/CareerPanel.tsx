// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useState } from 'react'
import { Alert, Text, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { petCreamStyles } from '@/components/pet-cream'
import { petApi, type PetCareerPanel } from '@/api/pet'
import { CARE_ERROR_HINT, ChipButton } from '../shared'

export function CareerPanel({ onRefresh }: { onRefresh: () => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [panel, setPanel] = useState<PetCareerPanel | null>(null)
  const [pending, setPending] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.getCareer()
      setPanel(res.data)
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    async function run() {
      await load()
    }
    void run()
  }, [load])

  const run = async (
    key: string,
    action: () => Promise<{ data: { success: boolean } }>,
    text: string | ((data: unknown) => string),
  ) => {
    setPending(key)
    try {
      const { data: res } = await action()
      // R13：文案可为函数（按逐项结果汇总），静态文案行为不变
      Alert.alert('成功', typeof text === 'function' ? text(res) : text)
      await load()
      onRefresh()
    } catch (error) {
      Alert.alert('暂时不能这么做', CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试')
    } finally {
      setPending(null)
    }
  }

  if (!panel) {
    return <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>职业信息加载中…</Text>
  }
  const activity = panel.activeActivity

  return (
    <View style={{ gap: Spacing.sm }}>
      <View style={petCreamStyles.questCard}>
      <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
        {panel.careerCode
          ? `当前职业：${panel.icon ?? ''} ${panel.careerName}（${panel.careerLine} · ${panel.tier} 阶）· 已工作 ${panel.workCount} 次`
          : '还没有工作，挑一份喜欢的职业入职吧'}
        {panel.canPromote && panel.promoteToName ? ` · 可晋升「${panel.promoteToName}」` : ''}
        {panel.promoteLockReason && panel.promoteToName ? ` · 晋升条件：${panel.promoteLockReason}` : ''}
      </Text>
      {panel.careerCode && (
        <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
          {activity ? (
            <ChipButton
              label={activity.canClaim ? '领取工作奖励' : `工作中 ${Math.max(0, Math.ceil(activity.remainingSeconds / 60))} 分钟`}
              primary
              disabled={!activity.canClaim || pending === 'claim'}
              onPress={() => run('claim', () => petApi.claimCareerWork(), '工钱到手啦！')}
            />
          ) : (
            <ChipButton
              label="去上班"
              primary
              disabled={pending === 'start'}
              onPress={() => run('start', () => petApi.startCareerWork(), '开始工作啦')}
            />
          )}
          <ChipButton
            label="晋升"
            disabled={!panel.canPromote || pending === 'promote'}
            onPress={() => run('promote', () => petApi.promoteCareer(), '晋升成功！')}
          />
        </View>
      )}
      </View>
      {panel.careers.map((career) => (
        <View
          key={career.code}
          style={petCreamStyles.questCard}
        >
          <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
            {career.icon} {career.name} · {career.careerLine} {career.tier} 阶
          </Text>
          <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
            {career.description}
          </Text>
          <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
            {`Lv.${career.requiredLevel} · ${Math.round(career.durationSeconds / 60)} 分钟 · 精力 ${career.energyCost}${career.workCount > 0 ? ` · 已工作 ${career.workCount} 次` : ''}`}
          </Text>
          <View style={petCreamStyles.rewardRow}>
            <Text style={petCreamStyles.rewardChip}>{`经验 +${career.expReward}`}</Text>
            <Text style={petCreamStyles.rewardChip}>{`宠物币 +${career.currencyReward}`}</Text>
          </View>
          <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
            {career.current ? (
              <Text style={{ color: colors.primary, fontSize: FontSize.xs }}>在职</Text>
            ) : (
              <ChipButton
                label={career.eligible ? '入职' : career.lockReason ?? '未解锁'}
                primary
                disabled={!career.eligible || pending === `apply-${career.code}`}
                onPress={() => run(`apply-${career.code}`, () => petApi.applyCareer(career.code), '入职成功！')}
              />
            )}
          </View>
        </View>
      ))}
      {panel.history.length > 0 && (
        <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
          工作经历：{panel.history.map((item) => `${item.name}（${item.workCount} 次）`).join(' · ')}
        </Text>
      )}
    </View>
  )
}
