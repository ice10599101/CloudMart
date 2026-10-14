// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useState } from 'react'
import { Alert, Text, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { CreamButton, petCreamStyles } from '@/components/pet-cream'
import { growthApi } from '@/api/growth'
import { petApi, type PetDailyQuestPanel } from '@/api/pet'
import type { CheckInStatus } from '@/types'
import { CARE_ERROR_HINT } from '../shared'

export function DailyQuestPanel({ onRefresh }: { onRefresh: () => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [panel, setPanel] = useState<PetDailyQuestPanel | null>(null)
  const [pending, setPending] = useState<string | null>(null)
  const [checkin, setCheckin] = useState<CheckInStatus | null>(null)

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.getDailyQuests()
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

  useEffect(() => {
    growthApi.getCheckInStatus().then(({ data }) => setCheckin(data.data ?? null)).catch(() => undefined)
  }, [])

  const doCheckin = useCallback(async () => {
    setPending('checkin')
    try {
      const { data: res } = await growthApi.checkIn()
      setCheckin(res.data)
      Alert.alert('成功', `签到成功 · 连续 ${res.data.continuousDays} 天`)
      await load()
    } catch (error) {
      Alert.alert('暂时不能这么做', CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '签到未成功')
    } finally {
      setPending(null)
    }
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
    return <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>任务加载中…</Text>
  }

  return (
    <View style={{ gap: Spacing.sm }}>
      <View style={petCreamStyles.questCard}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.sm }}>
          <View style={{ flex: 1, minWidth: 0 }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>🎂 每日签到</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
              {checkin
                ? checkin.isCheckedIn
                  ? `今日已签到 · 连续 ${checkin.continuousDays} 天 · 经验 +${checkin.todayExp}`
                  : '今天还没签到，连续签到经验更多'
                : '签到状态加载中…'}
            </Text>
          </View>
          <CreamButton
            variant={checkin?.isCheckedIn ? 'ghost' : 'primary'}
            disabled={!!checkin?.isCheckedIn || pending === 'checkin'}
            onPress={() => void doCheckin()}
          >
            {checkin?.isCheckedIn ? '已签到' : '签到'}
          </CreamButton>
        </View>
      </View>
      <View style={petCreamStyles.summaryRow}>
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs, flex: 1 }}>
          {`今日进度 ${panel.claimedCount}/${panel.totalCount} · 全清宝箱 经验+${panel.chestExp} 宠物币+${panel.chestCurrency}`}
        </Text>
        {(panel.quests.some((quest) => quest.claimable) || panel.chestClaimable) && (
          <CreamButton disabled={pending === 'claim-all'} onPress={() => run('claim-all', () =>
            panel.setId ? petApi.claimAllDailyQuestsInSet(panel.setId) : petApi.claimAllDailyQuests(), (data) => {
            // R13：按逐项结果汇总提示，失败项不掩盖
            const results = (data as { results?: Array<{ status: string }>; chest?: { status: string } }).results ?? []
            const claimed = results.filter((item) => item.status === 'CLAIMED').length
            const failed = results.filter((item) => item.status === 'FAILED').length
            const chestClaimed = (data as { chest?: { status: string } }).chest?.status === 'CLAIMED'
            const text = [`${claimed} 项任务成功`]
            if (chestClaimed) text.push('宝箱已开启')
            if (failed > 0) text.push(`${failed} 项失败，可稍后重试`)
            if (claimed === 0 && failed === 0 && !chestClaimed) return '奖励已经领过了'
            return text.join('，')
          })}>
            一键领取
          </CreamButton>
        )}
      </View>
      {panel.quests.map((quest) => {
        const percent = quest.targetValue > 0 ? Math.min(100, Math.round((quest.progress / quest.targetValue) * 100)) : 0
        return (
          <View key={quest.code} style={petCreamStyles.questCard}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
              {quest.icon} {quest.name}
            </Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>{quest.description}</Text>
            <View style={petCreamStyles.questProgressRow}>
              <View style={petCreamStyles.questTrack}>
                <View style={[petCreamStyles.questFill, { width: `${percent}%` }]} />
              </View>
              <Text style={petCreamStyles.questProgressText}>{`${Math.min(quest.progress, quest.targetValue)}/${quest.targetValue}`}</Text>
            </View>
            <View style={petCreamStyles.rewardRow}>
              <Text style={petCreamStyles.rewardChip}>{`经验 +${quest.expReward}`}</Text>
              <Text style={petCreamStyles.rewardChip}>{`宠物币 +${quest.currencyReward}`}</Text>
            </View>
            <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
              {quest.status === 'CLAIMED' ? (
                <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>已领取</Text>
              ) : (
                <CreamButton
                  variant={quest.claimable ? 'primary' : 'ghost'}
                  disabled={!quest.claimable || pending === `quest-${quest.code}`}
                  onPress={() => run(`quest-${quest.code}`, () =>
                    panel.setId ? petApi.claimDailyQuestInSet(panel.setId, quest.code) : petApi.claimDailyQuest(quest.code), '奖励到手啦！')}
                >
                  {quest.claimable ? '领取奖励' : quest.statusLabel}
                </CreamButton>
              )}
            </View>
          </View>
        )
      })}
      {panel.chestClaimed ? (
        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>宝箱已领取</Text>
      ) : (
        <CreamButton
          variant={panel.chestClaimable ? 'primary' : 'ghost'}
          disabled={!panel.chestClaimable || pending === 'chest'}
          onPress={() => run('chest', () =>
            panel.setId ? petApi.claimDailyQuestChestInSet(panel.setId) : petApi.claimDailyQuestChest(), '宝箱开啦！')}
        >
          {panel.chestClaimable ? '开启全清宝箱' : '全部领取后可开宝箱'}
        </CreamButton>
      )}
    </View>
  )
}

/** 社交面板：宠物关系 / 好友互访 / 留言墙 */
