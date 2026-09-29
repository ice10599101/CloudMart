import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Text, View } from '@tarojs/components'
import { petApi, type PetDailyQuestPanel } from '@/api/pet'
import { CARE_ERROR_HINT } from './shared'
import styles from '../index.module.scss'

/** 每日任务面板（三期）：任务列表/领取/全清宝箱（P2-4 自 index.tsx 拆出，行为不变） */
export function DailyQuestPanel({ onRefresh }: { onRefresh: () => void }) {
  const [panel, setPanel] = useState<PetDailyQuestPanel | null>(null)
  const [pending, setPending] = useState<string | null>(null)

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

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        Taro.showToast({ title: text, icon: 'success' })
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
      <Text className={styles.tip}>
        今日进度 {panel.claimedCount}/{panel.totalCount} · 全清宝箱 经验+{panel.chestExp} ✨+{panel.chestCurrency}
      </Text>
      <View className={styles.jobList}>
        {panel.quests.map((quest) => (
          <View key={quest.code} className={styles.jobCard}>
            <View className={styles.jobInfo}>
              <Text className={styles.jobName}>
                {quest.icon} {quest.name}
              </Text>
              <Text className={styles.jobMeta}>
                {quest.description} · 进度 {Math.min(quest.progress, quest.targetValue)}/{quest.targetValue} · 经验+
                {quest.expReward} ✨+{quest.currencyReward}
              </Text>
            </View>
            {quest.status === 'CLAIMED' ? (
              <Text className={styles.jobMeta}>已领取</Text>
            ) : (
              <Button
                className={quest.claimable ? styles.miniBtn : styles.locked}
                disabled={!quest.claimable || pending === `q-${quest.code}`}
                onClick={() => run(`q-${quest.code}`, () => petApi.claimDailyQuest(quest.code), '奖励到手啦！')}
              >
                {quest.claimable ? '领取' : quest.statusLabel}
              </Button>
            )}
          </View>
        ))}
      </View>
      {panel.chestClaimed ? (
        <Text className={styles.jobMeta}>宝箱已领取</Text>
      ) : (
        <Button
          className={panel.chestClaimable ? styles.miniBtn : styles.locked}
          disabled={!panel.chestClaimable || pending === 'chest'}
          onClick={() => run('chest', () => petApi.claimDailyQuestChest(), '宝箱开啦！')}
        >
          {panel.chestClaimable ? '开启全清宝箱' : '全部领取后可开宝箱'}
        </Button>
      )}
    </View>
  )
}

/** 社交面板：关系 / 好友 / 留言墙 */
