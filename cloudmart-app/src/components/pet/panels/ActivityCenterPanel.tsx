// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useState } from 'react'
import { ActivityIndicator, Alert, Text, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { errorMessage } from '@/utils/request'
import { petApi, type PetActivityCenterSummary, type PetActivityRow, type PetClaimBatchItem } from '@/api/pet'
import { ChipButton } from '../shared'

/**
 * R18 活动中心面板（对齐 Web/Taro 数据口径）：activity-center 聚合摘要 +
 * activities 明细 + claim-batch 逐项终态。空列表/故障/功能数据三态分离。
 */
const ACTIVITY_TYPE_LABEL_APP: Record<string, string> = {
  WORK: '打工', STUDY: '读书', BOTTLE: '捞瓶', BATTLE: '对战',
}
const BATCH_ITEM_LABEL_APP: Record<PetClaimBatchItem['status'], string> = {
  CLAIMED: '已领取', ALREADY_CLAIMED: '此前已领', NOT_READY: '尚未完成', FAILED: '领取失败',
}

export function ActivityCenterPanel({ onRefresh }: { onRefresh: () => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [loadState, setLoadState] = useState<'LOADING' | 'READY' | 'ERROR'>('LOADING')
  const [summary, setSummary] = useState<PetActivityCenterSummary | null>(null)
  const [activities, setActivities] = useState<PetActivityRow[]>([])
  const [batchResults, setBatchResults] = useState<PetClaimBatchItem[] | null>(null)
  const [batchPending, setBatchPending] = useState(false)
  const [singlePendingId, setSinglePendingId] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoadState('LOADING')
    try {
      const [centerRes, listRes] = await Promise.all([
        petApi.getActivityCenter(),
        petApi.listActivities(),
      ])
      setSummary(centerRes.data.data ?? null)
      setActivities(listRes.data.data ?? [])
      setBatchResults(null)
      setLoadState('READY')
    } catch {
      setLoadState('ERROR')
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const claimableIds = activities.filter((item) => item.canClaim).map((item) => item.activityId)

  const runBatch = async () => {
    if (claimableIds.length === 0 || batchPending) return
    setBatchPending(true)
    try {
      const { data: res } = await petApi.claimActivitiesBatch(claimableIds)
      const results = res.data ?? []
      setBatchResults(results)
      const failed = results.filter((item) => item.status === 'FAILED').length
      Alert.alert('批量领取', failed > 0 ? `${failed} 项领取失败，可稍后重试` : '批量领取完成')
      await load()
      onRefresh()
    } catch (error) {
      Alert.alert('批量领取失败', errorMessage(error, '请稍后再试'))
    } finally {
      setBatchPending(false)
    }
  }

  const runSingle = async (activity: PetActivityRow) => {
    setSinglePendingId(String(activity.activityId))
    try {
      await petApi.claimActivity(activity.activityId)
      await load()
      onRefresh()
    } catch (error) {
      Alert.alert('领取失败', errorMessage(error, '请稍后再试'))
    } finally {
      setSinglePendingId(null)
    }
  }

  const cardStyle = {
    padding: Spacing.sm, borderRadius: 16, borderWidth: 1, borderColor: 'rgba(200, 155, 90, 0.35)',
    backgroundColor: '#FFFDF8', gap: 4, marginBottom: Spacing.sm,
  } as const

  if (loadState === 'LOADING') {
    return <ActivityIndicator color={colors.primary} />
  }
  if (loadState === 'ERROR') {
    return (
      <View style={cardStyle}>
        <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>活动中心暂时不可用</Text>
        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>网络或服务异常，重试后仍失败请稍后再来</Text>
        <ChipButton label="重新加载" primary onPress={() => void load()} />
      </View>
    )
  }

  return (
    <View>
      <View style={cardStyle}>
        <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
          {`📅 营业日 ${summary?.businessDate ?? '—'}`}
        </Text>
        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
          {`待领取活动 ${summary?.pendingClaimsCount ?? 0} 项`}
          {summary?.eventSummary?.claimableCount ? ` · 事件奖励可领 ${summary.eventSummary.claimableCount} 项` : ''}
          {summary?.accountBusyActivity?.activity ? ' · 当前宠物活动进行中' : ''}
          {summary?.accountBusyActivity?.custody ? ' · 账号有托管活动' : ''}
        </Text>
      </View>
      {activities.length === 0 ? (
        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
          当前没有进行中的限时活动，新活动开放后会出现在这里
        </Text>
      ) : (
        <View>
          <ChipButton
            label={claimableIds.length > 0 ? `一键领取（${claimableIds.length} 项）` : '没有待领取的活动'}
            primary
            disabled={claimableIds.length === 0 || batchPending}
            onPress={() => void runBatch()}
          />
          {activities.map((activity) => (
            <View key={String(activity.activityId)} style={cardStyle}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: Spacing.sm }}>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                    {activity.petName ? `${activity.petName} · ` : ''}{activity.configName ?? activity.activityType}
                  </Text>
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                    {ACTIVITY_TYPE_LABEL_APP[activity.activityType] ?? activity.activityType}
                  </Text>
                </View>
                {activity.status === 'CLAIMED' || !activity.canClaim ? (
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                    {activity.status === 'CLAIMED' ? '已领取' : '进行中'}
                  </Text>
                ) : (
                  <ChipButton
                    label={singlePendingId === String(activity.activityId) ? '领取中…' : '领取'}
                    primary
                    disabled={singlePendingId === String(activity.activityId)}
                    onPress={() => void runSingle(activity)}
                  />
                )}
              </View>
            </View>
          ))}
        </View>
      )}
      {batchResults && batchResults.length > 0 && (
        <View style={cardStyle}>
          <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>本次领取结果</Text>
          {batchResults.map((item) => (
            <Text key={String(item.activityId)} style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
              {`活动 ${item.activityId}：${BATCH_ITEM_LABEL_APP[item.status]}`}
              {item.status === 'FAILED' && item.message ? `（${item.message}）` : ''}
            </Text>
          ))}
        </View>
      )}
    </View>
  )
}

/** 每日任务面板：进度 + 领奖 + 全清宝箱 */
