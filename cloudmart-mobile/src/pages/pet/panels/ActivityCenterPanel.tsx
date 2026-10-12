import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Text, View } from '@tarojs/components'
import {
  petApi,
  petCompanionApi,
  type PetActivityCenterSummary,
  type PetActivityRow,
  type PetClaimBatchItem,
} from '@/api/pet'
import { errorMessage } from '@/utils/request'
import cream from '@/components/pet-cream/pet-cream.module.scss'
import styles from '../index.module.scss'

/**
 * R18 活动中心（对齐 Web ActivityCenterPanel 的数据口径）：
 * /pet/activity-center 聚合摘要 + /pet/activities 明细 + claim-batch 逐项终态。
 * 三态分离：加载失败（可重试）/ 空列表 / 功能数据正常；批量领取逐项展示
 * CLAIMED/ALREADY_CLAIMED/NOT_READY/FAILED，不忽略单项失败。
 */

type LoadState = 'LOADING' | 'READY' | 'ERROR'

const BATCH_ITEM_LABEL: Record<PetClaimBatchItem['status'], string> = {
  CLAIMED: '已领取',
  ALREADY_CLAIMED: '此前已领',
  NOT_READY: '尚未完成',
  FAILED: '领取失败',
}

function remainingText(seconds: number): string {
  if (seconds <= 0) return '已结束'
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  if (hours > 0) return `剩余 ${hours}小时${minutes % 60}分`
  return `剩余 ${minutes}分钟`
}

export function ActivityCenterPanel({ onRefresh }: { onRefresh: () => void }) {
  const [loadState, setLoadState] = useState<LoadState>('LOADING')
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
        petCompanionApi.listActivities(),
      ])
      setSummary(centerRes.data.data ?? null)
      setActivities(listRes.data.data ?? [])
      setBatchResults(null)
      setLoadState('READY')
    } catch (error) {
      // R18：故障态独立于空列表——失败给重试，不伪装成"暂无活动"
      setLoadState('ERROR')
      Taro.showToast({ title: errorMessage(error, '活动中心加载失败'), icon: 'none' })
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
      setBatchResults(res.data ?? [])
      const failed = (res.data ?? []).filter((item) => item.status === 'FAILED').length
      Taro.showToast({
        title: failed > 0 ? `${failed} 项领取失败，可稍后重试` : '批量领取完成',
        icon: failed > 0 ? 'none' : 'success',
      })
      await load()
      onRefresh()
    } catch (error) {
      Taro.showToast({ title: errorMessage(error, '批量领取失败'), icon: 'none' })
    } finally {
      setBatchPending(false)
    }
  }

  const runSingle = async (activity: PetActivityRow) => {
    setSinglePendingId(String(activity.activityId))
    try {
      await petCompanionApi.claimActivity(activity.activityId)
      Taro.showToast({ title: '奖励到手啦', icon: 'success' })
      await load()
      onRefresh()
    } catch (error) {
      Taro.showToast({ title: errorMessage(error, '领取失败'), icon: 'none' })
    } finally {
      setSinglePendingId(null)
    }
  }

  if (loadState === 'LOADING') {
    return <Text className={styles.tip}>活动中心加载中…</Text>
  }

  if (loadState === 'ERROR') {
    return (
      <View className={styles.jobCard}>
        <Text className={styles.jobName}>活动中心暂时不可用</Text>
        <Text className={styles.jobMeta}>网络或服务异常，重试后仍失败请稍后再来</Text>
        <View className={cream.tab} onClick={() => void load()}>
          <Text>重新加载</Text>
        </View>
      </View>
    )
  }

  return (
    <View>
      <View className={styles.jobCard}>
        <Text className={styles.jobName}>
          📅 营业日 {summary?.businessDate ?? '—'}
        </Text>
        <Text className={styles.jobMeta}>
          待领取活动 {summary?.pendingClaimsCount ?? 0} 项
          {summary?.eventSummary?.claimableCount ? ` · 事件奖励可领 ${summary.eventSummary.claimableCount} 项` : ''}
          {summary?.accountBusyActivity?.activity ? ' · 当前宠物活动进行中' : ''}
          {summary?.accountBusyActivity?.custody ? ' · 账号有托管活动' : ''}
        </Text>
      </View>

      {activities.length === 0 ? (
        <Text className={styles.tip}>当前没有进行中的限时活动，新活动开放后会出现在这里</Text>
      ) : (
        <View className={styles.jobList}>
          <Button
            size='mini'
            type='primary'
            style={{ marginBottom: '12rpx' }}
            loading={batchPending}
            disabled={claimableIds.length === 0}
            onClick={() => void runBatch()}
          >
            {claimableIds.length > 0 ? `一键领取（${claimableIds.length} 项）` : '没有待领取的活动'}
          </Button>
          {activities.map((activity) => (
            <View key={String(activity.activityId)} className={styles.jobCard}>
              <View className={styles.jobInfo}>
                <Text className={styles.jobName}>
                  {activity.petName ? `${activity.petName} · ` : ''}{activity.configName ?? activity.activityType}
                </Text>
                <Text className={styles.jobMeta}>
                  {ACTIVITY_TYPE_LABEL[activity.activityType] ?? activity.activityType}
                  {' · '}{remainingText(activity.remainingSeconds)}
                </Text>
              </View>
              {activity.status === 'CLAIMED' || !activity.canClaim ? (
                <Text className={styles.jobMeta}>
                  {activity.status === 'CLAIMED' ? '已领取' : '进行中'}
                </Text>
              ) : (
                <View
                  className={`${cream.tab} ${cream.tabActive}`}
                  onClick={singlePendingId === String(activity.activityId) ? undefined : () => void runSingle(activity)}
                >
                  <Text>{singlePendingId === String(activity.activityId) ? '领取中…' : '领取'}</Text>
                </View>
              )}
            </View>
          ))}
        </View>
      )}

      {batchResults && batchResults.length > 0 && (
        <View className={styles.jobCard}>
          <Text className={styles.jobName}>本次领取结果</Text>
          {batchResults.map((item) => (
            <Text key={String(item.activityId)} className={styles.jobMeta}>
              活动 {item.activityId}：{BATCH_ITEM_LABEL[item.status]}
              {item.status === 'FAILED' && item.message ? `（${item.message}）` : ''}
            </Text>
          ))}
        </View>
      )}
    </View>
  )
}

const ACTIVITY_TYPE_LABEL: Record<string, string> = {
  WORK: '打工',
  STUDY: '读书',
  BOTTLE: '捞瓶',
  BATTLE: '对战',
}
