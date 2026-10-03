import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Text, View } from '@tarojs/components'
import { petApi, petCompanionApi, type PetDailyQuestPanel } from '@/api/pet'
import { growthApi } from '@/api/growth'
import type { CheckInStatus } from '@/types'
import { CARE_ERROR_HINT } from './shared'
import cream from '@/components/pet-cream/pet-cream.module.scss'
import styles from '../index.module.scss'

/** 每日任务面板（三期）：任务列表/领取/全清宝箱（P2-4 自 index.tsx 拆出，行为不变） */
export function DailyQuestPanel({ onRefresh }: { onRefresh: () => void }) {
  const [panel, setPanel] = useState<PetDailyQuestPanel | null>(null)
  const [pending, setPending] = useState<string | null>(null)
  const [checkin, setCheckin] = useState<CheckInStatus | null>(null)

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.getDailyQuests()
      if (res.success && res.data) {
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
      if (res.success) setCheckin(res.data)
    }).catch(() => undefined)
  }, [])

  const doCheckin = async () => {
    setPending('checkin')
    try {
      const { data: res } = await growthApi.checkIn()
      if (res.success && res.data) {
        setCheckin(res.data)
        Taro.showToast({ title: `签到成功 · 连续 ${res.data.continuousDays} 天`, icon: 'success' })
      } else {
        Taro.showToast({ title: '今日已签到', icon: 'none' })
        setCheckin((prev) => prev ?? { isCheckedIn: true, continuousDays: 0, todayExp: 0 })
      }
    } catch (error) {
      Taro.showToast({ title: CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '签到未成功', icon: 'none' })
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
      if (res.success) {
        // R13：文案可为函数（按逐项结果汇总），静态文案行为不变
        Taro.showToast({ title: typeof text === 'function' ? text(res) : text, icon: 'success' })
        await load()
        onRefresh()
      }
    } catch (error) {
      Taro.showToast({ title: CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试', icon: 'none' })
    } finally {
      setPending(null)
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
        onClick={() => run('claim-all', () => petCompanionApi.claimAllDailyQuests(), (data) => {
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
                onClick={quest.claimable && pending !== `q-${quest.code}` ? () => run(`q-${quest.code}`, () => petApi.claimDailyQuest(quest.code), '奖励到手啦！') : undefined}
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
          onClick={panel.chestClaimable && pending !== 'chest' ? () => run('chest', () => petApi.claimDailyQuestChest(), '宝箱开啦！') : undefined}
        >
          <Text>{panel.chestClaimable ? '开启全清宝箱' : '全部领取后可开宝箱'}</Text>
        </View>
      )}
    </View>
  )
}

/** 社交面板：关系 / 好友 / 留言墙 */
