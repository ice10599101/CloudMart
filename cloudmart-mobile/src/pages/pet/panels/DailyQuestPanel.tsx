import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Text, View } from '@tarojs/components'
import { petApi, petCompanionApi, type PetDailyQuestPanel, type PetDailyQuestSet } from '@/api/pet'
import { growthApi } from '@/api/growth'
import type { CheckInStatus } from '@/types'
import { CARE_ERROR_HINT } from './shared'
import { errorMessage } from '@/utils/request'
import cream from '@/components/pet-cream/pet-cream.module.scss'
import styles from '../index.module.scss'

/** 每日任务面板（三期）：任务列表/领取/全清宝箱（P2-4 自 index.tsx 拆出，行为不变） */
export function DailyQuestPanel({ onRefresh }: { onRefresh: () => void }) {
  const [panel, setPanel] = useState<PetDailyQuestPanel | null>(null)
  // T19：历史任务（列表/详情切换；详情复用领取链路，服务端按 claimDeadline 判定）
  const [historySets, setHistorySets] = useState<PetDailyQuestSet[] | null>(null)
  const [historyPanel, setHistoryPanel] = useState<PetDailyQuestPanel | null>(null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [pending, setPending] = useState<string | null>(null)
  const [checkin, setCheckin] = useState<CheckInStatus | null>(null)

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.getDailyQuests()
      if (res.data) {
        setPanel(res.data)
      }
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    growthApi.getCheckInStatus().then(({ data: res }) => {
      setCheckin(res.data)
    }).catch(() => undefined)
  }, [])

  const doCheckin = async () => {
    setPending('checkin')
    try {
      const { data: res } = await growthApi.checkIn()
      if (res.data) {
        setCheckin(res.data)
        Taro.showToast({ title: `签到成功 · 连续 ${res.data.continuousDays} 天`, icon: 'success' })
      }
    } catch (error) {
      const code = (error as { code?: string }).code ?? ''
      Taro.showToast({ title: CARE_ERROR_HINT[code] ?? errorMessage(error, '签到未成功'), icon: 'none' })
    } finally {
      setPending(null)
    }
  }

  const run = async (
    key: string,
    action: () => Promise<{ data: { success: boolean } }>,
    text: string | ((data: unknown) => string),
  ) => {
    setPending(key)
    try {
      const { data: res } = await action()
      // R13：文案可为函数（按逐项结果汇总），静态文案行为不变
      Taro.showToast({ title: typeof text === 'function' ? text(res) : text, icon: 'success' })
      await load()
      onRefresh()
    } catch (error) {
      const code = (error as { code?: string }).code ?? ''
      Taro.showToast({ title: CARE_ERROR_HINT[code] ?? errorMessage(error, '请稍后再试'), icon: 'none' })
    } finally {
      setPending(null)
    }
  }

  // T19：历史任务集列表（含宽限期内可补领与已过截止，服务端按 claimDeadline 判定）
  const loadHistorySets = async () => {
    setHistoryLoading(true)
    setHistoryPanel(null)
    try {
      const { data: res } = await petApi.getQuestSets('EXPIRED')
      setHistorySets(res.data ?? [])
    } catch {
      setHistorySets([])
    } finally {
      setHistoryLoading(false)
    }
  }

  const loadHistoryDetail = async (setId: number) => {
    setHistoryLoading(true)
    try {
      const { data: res } = await petApi.getQuestSetDetail(setId)
      setHistoryPanel(res.data)
    } catch {
      Taro.showToast({ title: '历史任务加载失败', icon: 'none' })
    } finally {
      setHistoryLoading(false)
    }
  }

  if (!panel) {
    return <Text className={styles.tip}>任务加载中…</Text>
  }

  return (
    <View>
      <View className={styles.jobCard}>
        <View className={styles.jobInfo}>
          <Text className={styles.jobName}>🎂 每日签到</Text>
          <Text className={styles.jobMeta}>
            {checkin
              ? checkin.isCheckedIn
                ? `今日已签到 · 连续 ${checkin.continuousDays} 天 · 经验 +${checkin.todayExp}`
                : '今天还没签到，连续签到经验更多'
              : '签到状态加载中…'}
          </Text>
        </View>
        <View
          className={checkin?.isCheckedIn ? cream.tabActive : cream.tab}
          onClick={checkin?.isCheckedIn || pending === 'checkin' ? undefined : () => void doCheckin()}
        >
          <Text>{checkin?.isCheckedIn ? '已签到' : '签到'}</Text>
        </View>
      </View>
      <Text className={styles.tip}>
        今日进度 {panel.claimedCount}/{panel.totalCount} · 全清宝箱 经验+{panel.chestExp} 宠物币+{panel.chestCurrency}
      </Text>
      <Button
        size='mini'
        style={{ marginBottom: '12rpx' }}
        loading={pending === 'claim-all'}
        disabled={!panel.setId}
        onClick={() => run(
          'claim-all',
          // C14：唯一一键领奖路径——按任务集实例
          () => petCompanionApi.claimAllDailyQuestsInSet(panel.setId!),
          (data) => {
          const payload = data as { results?: Array<{ status: string }>; chest?: { status: string } }
          const claimed = (payload.results ?? []).filter((item) => item.status === 'CLAIMED').length
          const failed = (payload.results ?? []).filter((item) => item.status === 'FAILED').length
          const chestClaimed = payload.chest?.status === 'CLAIMED'
          const text = [`${claimed} 项任务成功`]
          if (chestClaimed) text.push('宝箱已开启')
          if (failed > 0) text.push(`${failed} 项失败，可稍后重试`)
          if (claimed === 0 && failed === 0 && !chestClaimed) return '奖励已经领过了'
          return text.join('，')
        })}
      >
        一键领取
      </Button>
      <View className={styles.jobList}>
        {panel.quests.map((quest) => (
          <View key={quest.code} className={styles.jobCard}>
            <View className={styles.jobInfo}>
              <Text className={styles.jobName}>
                {quest.icon} {quest.name}
              </Text>
              <Text className={styles.jobMeta}>{quest.description}</Text>
              <View className={cream.questProgressRow}>
                <View className={cream.questTrack}>
                  <View
                    className={cream.questFill}
                    style={{ width: `${Math.min(100, Math.round((quest.progress / quest.targetValue) * 100))}%` }}
                  />
                </View>
                <Text className={cream.questProgressText}>
                  {`${Math.min(quest.progress, quest.targetValue)}/${quest.targetValue}`}
                </Text>
              </View>
              <View className={cream.rewardRow}>
                <Text className={cream.rewardChip}>{`经验 +${quest.expReward}`}</Text>
                <Text className={cream.rewardChip}>{`宠物币 +${quest.currencyReward}`}</Text>
              </View>
            </View>
            {quest.status === 'CLAIMED' ? (
              <Text className={styles.jobMeta}>已领取</Text>
            ) : (
              <View
                className={`${cream.tab} ${quest.claimable ? cream.tabActive : cream.lockedTab}`}
                onClick={quest.claimable && quest.id && panel.setId && pending !== `q-${quest.id}` ? () => run(`q-${quest.id}`, () =>
                  petApi.claimDailyQuestInSet(panel.setId!, quest.id!), '奖励到手啦！') : undefined}
              >
                <Text>{quest.claimable ? '领取' : quest.statusLabel}</Text>
              </View>
            )}
          </View>
        ))}
      </View>
      {panel.chestClaimed ? (
        <Text className={styles.jobMeta}>宝箱已领取</Text>
      ) : (
        <View
          className={`${cream.tab} ${panel.chestClaimable ? cream.tabActive : cream.lockedTab}`}
          onClick={panel.chestClaimable && panel.setId && pending !== 'chest' ? () => run('chest', () =>
              petApi.claimDailyQuestChestInSet(panel.setId!), '宝箱开啦！') : undefined}
        >
          <Text>{panel.chestClaimable ? '开启全清宝箱' : '全部领取后可开宝箱'}</Text>
        </View>
      )}

      {/* T19：历史任务/可补领（超期只读；宽限期内可领，服务端判定 claimable/deadline） */}
      <View className={styles.jobCard} style={{ marginTop: '16rpx' }}>
        <View className={styles.jobInfo}>
          <Text className={styles.jobName}>🗓️ 历史任务</Text>
          <Text className={styles.jobMeta}>
            {historySets == null ? '查看往日任务与补领截止' : `${historySets.length} 个历史任务集`}
          </Text>
        </View>
        <View
          className={cream.tab}
          onClick={historySets == null ? () => void loadHistorySets() : () => { setHistorySets(null); setHistoryPanel(null) }}
        >
          <Text>{historySets == null ? '查看' : '收起'}</Text>
        </View>
      </View>
      {historyLoading ? <Text className={styles.tip}>加载中…</Text> : null}
      {historySets != null && historyPanel == null
        ? historySets.map((set) => (
            <View key={set.id} className={styles.jobCard} onClick={() => void loadHistoryDetail(set.id)}>
              <View className={styles.jobInfo}>
                <Text className={styles.jobName}>📅 {set.businessDate}</Text>
                <Text className={styles.jobMeta}>
                  {set.status === 'ACTIVE'
                    ? `可补领 · 截止 ${set.claimDeadline?.slice(0, 16).replace('T', ' ') ?? ''}`
                    : '已过补领截止（只读）'}
                </Text>
              </View>
              <Text className={styles.jobMeta}>›</Text>
            </View>
          ))
        : null}
      {historyPanel != null ? (
        <View>
          <Text className={styles.tip}>
            历史任务集 进度 {historyPanel.claimedCount}/{historyPanel.totalCount}
          </Text>
          <View className={styles.jobList}>
            {historyPanel.quests.map((quest) => (
              <View key={quest.code} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {quest.icon} {quest.name}
                  </Text>
                  <Text className={styles.jobMeta}>{quest.description}</Text>
                </View>
                {quest.status === 'CLAIMED' ? (
                  <Text className={styles.jobMeta}>已领取</Text>
                ) : (
                  <View
                    className={`${cream.tab} ${quest.claimable ? cream.tabActive : cream.lockedTab}`}
                    onClick={quest.claimable && quest.id && historyPanel.setId && pending !== `h-${quest.id}` ? () => run(`h-${quest.id}`, () =>
                      petApi.claimDailyQuestInSet(historyPanel.setId!, quest.id!), '补领成功！') : undefined}
                  >
                    <Text>{quest.claimable ? '补领' : quest.statusLabel}</Text>
                  </View>
                )}
              </View>
            ))}
          </View>
          <Text className={styles.jobMeta} onClick={() => setHistoryPanel(null)}>返回历史列表</Text>
        </View>
      ) : null}
    </View>
  )
}

/** 社交面板：关系 / 好友 / 留言墙 */
