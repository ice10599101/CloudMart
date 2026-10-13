import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Text, View } from '@tarojs/components'
import { petApi, type PetInfo, type PetJobItem } from '@/api/pet'
import { friendlyError } from '../constants'
import cream from '@/components/pet-cream/pet-cream.module.scss'
import styles from '../index.module.scss'

/**
 * 打工面板：岗位列表 / 接单 / 领奖（R30 自 index.tsx 拆出，行为不变）。
 * reloadSignal 由主页在活动到期/主页领奖后递增，驱动列表刷新。
 */
export function WorkPanel({ pet, onRefresh, reloadSignal = 0 }: {
  pet: PetInfo
  onRefresh: () => void
  reloadSignal?: number
}) {
  const [jobs, setJobs] = useState<PetJobItem[]>([])

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.listJobs()
      setJobs(res.data || [])
    } catch (error) {
      Taro.showToast({ title: friendlyError(error), icon: 'none' })
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load, reloadSignal])

  const toast = (msg: string) => Taro.showToast({ title: msg, icon: 'none' })

  return (
    <View className={styles.jobList}>
      {jobs.map((job) => (
        <View key={String(job.configId)} className={styles.jobCard}>
          <View className={styles.jobInfo}>
            <Text className={styles.jobName}>{job.name}</Text>
            <Text className={styles.jobMeta}>⏱ {Math.round(job.durationSeconds / 60)} 分钟 · ⚡-{job.energyCost}</Text>
            <View className={cream.rewardRow}>
              <Text className={cream.rewardChip}>{`经验 +${job.expReward}`}</Text>
              <Text className={cream.rewardChip}>{`宠物币 +${job.currencyReward}`}</Text>
            </View>
          </View>
          {job.eligible ? (
            <Button className={styles.miniBtn} onClick={async () => {
              try {
                await petApi.startWork(job.configId)
                toast('开工啦！'); void load(); onRefresh()
              } catch (error) { toast(friendlyError(error)) }
            }}>接单</Button>
          ) : (
            <Text className={styles.locked}>Lv.{job.requiredLevel}</Text>
          )}
        </View>
      ))}
      {pet.claimableActivityType === 'WORK' && (
        <Button className={styles.primaryBtn} onClick={async () => {
          try {
            await petApi.claimWork()
            toast('奖励已领取！'); void load(); onRefresh()
          } catch (error) { toast(friendlyError(error)) }
        }}>领取打工奖励</Button>
      )}
    </View>
  )
}
