import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Text, View } from '@tarojs/components'
import { petApi, type PetBottleResult, type PetBottleStatus } from '@/api/pet'
import { formatClock, friendlyError, RARITY_LABEL, useCountdown } from '../constants'
import styles from '../index.module.scss'

/** 稀有/宠物/彩蛋瓶结果弹层（普通瓶在领取时直接跳 encounterLetters，不弹此层） */
export function BottleResultModal({ result, onClose }: {
  result: PetBottleResult
  onClose: () => void
}) {
  return (
    <View className={styles.modalMask} onClick={onClose}>
      <View className={styles.modalCard} onClick={(e) => e.stopPropagation()}>
        <Text className={styles.modalTitle}>🍾 捞到了一只{RARITY_LABEL[result.rarity]}！</Text>
        {result.specialContent
          ? <Text className={styles.modalText}>{result.specialContent}</Text>
          : null}
        <Text className={styles.modalText}>
          宠物经验 +{result.exp} · 成功率 {Math.round(result.successRate * 100)}%
        </Text>
        <Button className={styles.primaryBtn} onClick={onClose}>知道啦</Button>
      </View>
    </View>
  )
}

/**
 * 捞漂流瓶面板：区域/成功率展示、出发、冷却与领取（R30 自 index.tsx 拆出，行为不变）。
 * 领取后的结果分流（普通瓶跳信页 / 稀有弹层 / 空手提示）由主页 onClaim 统一处理，
 * 因为主页倒计时领奖条也走同一条领取链路。
 */
export function BottlePanel({ onRefresh, onClaim, reloadSignal = 0 }: {
  onRefresh: () => void
  onClaim: () => Promise<void>
  reloadSignal?: number
}) {
  const [bottle, setBottle] = useState<PetBottleStatus | null>(null)
  const bottleRemaining = useCountdown(bottle?.fishing ? bottle.finishedAt : null)

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.getBottleStatus()
      setBottle(res.data)
    } catch (error) {
      Taro.showToast({ title: friendlyError(error), icon: 'none' })
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load, reloadSignal])

  const toast = (msg: string) => Taro.showToast({ title: msg, icon: 'none' })

  if (!bottle) {
    return <Text className={styles.tip}>加载中…</Text>
  }

  return (
    <View className={styles.bottlePanel}>
      <Text className={styles.tip}>🌊 捞瓶区域：{bottle.unlockedArea}</Text>
      <Text className={styles.tip}>估算成功率 {Math.round(bottle.estimatedSuccessRate * 100)}%（敏捷与等级加成）</Text>
      {bottle.fishing ? (
        <Text className={styles.tip}>🐾 捞瓶中…剩余 {formatClock(bottleRemaining)}</Text>
      ) : (
        <Button
          className={styles.primaryBtn}
          disabled={bottle.cooldownRemainingSeconds > 0 && !bottle.canClaim}
          onClick={async () => {
            if (bottle.canClaim) {
              await onClaim()
              return
            }
            try {
              await petApi.startBottle()
              toast('宠物出发啦！30 分钟后回来看结果')
              void load()
              onRefresh()
            } catch (error) { toast(friendlyError(error)) }
          }}
        >
          {bottle.canClaim ? '查看捞瓶结果' : bottle.cooldownRemainingSeconds > 0 ? `冷却中 ${Math.ceil(bottle.cooldownRemainingSeconds / 60)} 分钟` : '让宠物去捞漂流瓶（30 分钟）'}
        </Button>
      )}
      {bottle.lastOutcome === 'EMPTY' && <Text className={styles.tip}>这次空手而归啦，休息一下再试试～</Text>}
      {bottle.lastOutcome === 'FAILED' && <Text className={styles.tip}>心愿服务暂时不可用，点上面的按钮可以重试领取</Text>}
    </View>
  )
}
