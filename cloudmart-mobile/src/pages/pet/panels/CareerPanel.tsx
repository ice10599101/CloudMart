import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Text, View } from '@tarojs/components'
import { petApi, type PetCareerPanel } from '@/api/pet'
import { CARE_ERROR_HINT } from './shared'
import styles from '../index.module.scss'

/** 职业面板（三期）：入职/打工/晋升，自管数据与动作（P2-4 自 index.tsx 拆出，行为不变） */
export function CareerPanel({ onRefresh }: { onRefresh: () => void }) {
  const [panel, setPanel] = useState<PetCareerPanel | null>(null)
  const [pending, setPending] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.getCareer()
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
    return <Text className={styles.tip}>职业信息加载中…</Text>
  }
  const activity = panel.activeActivity

  return (
    <View>
      <Text className={styles.tip}>
        {panel.careerCode
          ? `当前职业：${panel.icon ?? ''} ${panel.careerName}（${panel.careerLine} · ${panel.tier} 阶）· 已工作 ${panel.workCount} 次`
          : '还没有工作，挑一份喜欢的职业入职吧'}
        {panel.canPromote && panel.promoteToName ? ` · 可晋升「${panel.promoteToName}」` : ''}
        {panel.promoteLockReason && panel.promoteToName ? ` · 晋升条件：${panel.promoteLockReason}` : ''}
      </Text>
      {panel.careerCode && (
        <View className={styles.actionRow}>
          {activity ? (
            <Button
              className={styles.miniBtn}
              disabled={!activity.canClaim || pending === 'claim'}
              onClick={() => run('claim', () => petApi.claimCareerWork(), '工钱到手啦！')}
            >
              {activity.canClaim ? '领取工作奖励' : `工作中 ${Math.max(0, Math.ceil(activity.remainingSeconds / 60))} 分钟`}
            </Button>
          ) : (
            <Button
              className={styles.miniBtn}
              disabled={pending === 'start'}
              onClick={() => run('start', () => petApi.startCareerWork(), '开始工作啦')}
            >
              去上班
            </Button>
          )}
          <Button
            className={styles.miniBtnGhost}
            disabled={!panel.canPromote || pending === 'promote'}
            onClick={() => run('promote', () => petApi.promoteCareer(), '晋升成功！')}
          >
            晋升
          </Button>
        </View>
      )}
      <View className={styles.jobList}>
        {panel.careers.map((career) => (
          <View key={career.code} className={styles.jobCard}>
            <View className={styles.jobInfo}>
              <Text className={styles.jobName}>
                {career.icon} {career.name} · {career.careerLine} {career.tier} 阶
              </Text>
              <Text className={styles.jobMeta}>{career.description}</Text>
              <Text className={styles.jobMeta}>
                Lv.{career.requiredLevel}
                {career.requiredIntelligence > 0 ? ` · 智力 ${career.requiredIntelligence}` : ''} ·{' '}
                {Math.round(career.durationSeconds / 60)} 分钟 · 经验+{career.expReward} ✨+{career.currencyReward}
                {career.workCount > 0 ? ` · 已工作 ${career.workCount} 次` : ''}
              </Text>
            </View>
            {career.current ? (
              <Text className={styles.jobMeta}>在职</Text>
            ) : (
              <Button
                className={career.eligible ? styles.miniBtn : styles.locked}
                disabled={!career.eligible || pending === `apply-${career.code}`}
                onClick={() => run(`apply-${career.code}`, () => petApi.applyCareer(career.code), '入职成功！')}
              >
                {career.eligible ? '入职' : career.lockReason ?? '未解锁'}
              </Button>
            )}
          </View>
        ))}
      </View>
      {panel.history.length > 0 && (
        <Text className={styles.jobMeta}>
          工作经历：{panel.history.map((item) => `${item.name}（${item.workCount} 次）`).join(' · ')}
        </Text>
      )}
    </View>
  )
}

/** 每日任务面板：进度 + 领奖 + 全清宝箱 */
