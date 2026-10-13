import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Text, View } from '@tarojs/components'
import { petApi, type PetInfo, type PetStudyItem } from '@/api/pet'
import { friendlyError } from '../constants'
import cream from '@/components/pet-cream/pet-cream.module.scss'
import styles from '../index.module.scss'

/**
 * 读书面板：课程列表 / 上课 / 领奖（R30 自 index.tsx 拆出，行为不变）。
 * reloadSignal 由主页在活动到期/主页领奖后递增，驱动列表刷新。
 */
export function StudyPanel({ pet, onRefresh, reloadSignal = 0 }: {
  pet: PetInfo
  onRefresh: () => void
  reloadSignal?: number
}) {
  const [studies, setStudies] = useState<PetStudyItem[]>([])

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.listStudies()
      setStudies(res.data || [])
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
      {studies.map((study) => (
        <View key={String(study.configId)} className={styles.jobCard}>
          <View className={styles.jobInfo}>
            <Text className={styles.jobName}>{study.name}</Text>
            <Text className={styles.jobMeta}>⏱ {Math.round(study.durationSeconds / 60)} 分钟</Text>
            <View className={cream.rewardRow}>
              <Text className={cream.rewardChip}>{`经验 +${study.expReward}`}</Text>
              <Text className={cream.rewardChip}>{`智力 +${study.intelligenceReward}`}</Text>
            </View>
          </View>
          {study.eligible ? (
            <Button className={styles.miniBtn} onClick={async () => {
              try {
                await petApi.startStudy(study.configId)
                toast('开始读书啦'); void load(); onRefresh()
              } catch (error) { toast(friendlyError(error)) }
            }}>上课</Button>
          ) : (
            <Text className={styles.locked}>Lv.{study.requiredLevel}</Text>
          )}
        </View>
      ))}
      {pet.claimableActivityType === 'STUDY' && (
        <Button className={styles.primaryBtn} onClick={async () => {
          try {
            await petApi.claimStudy()
            toast('学习奖励已领取！'); void load(); onRefresh()
          } catch (error) { toast(friendlyError(error)) }
        }}>领取学习奖励</Button>
      )}
    </View>
  )
}
