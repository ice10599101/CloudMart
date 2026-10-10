import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { petApi, type PveRunVO } from '@/api/pet'
import { PET_CREAM_STYLE } from '@/styles/petCream'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

/**
 * 协作 PVE 副本（§6）：招募大厅（加入他人）+ 发起 + 战斗操作。
 * Boss 内置三只（forest_ogre/sand_worm/void_knight），轮流体感由服务端轮流行动约束。
 */

const BOSSES = [
  { code: 'forest_ogre', name: '森林巨魔', hp: 600, desc: '入门 Boss，适合新宠物' },
  { code: 'sand_worm', name: '流沙巨虫', hp: 900, desc: '进阶挑战，双人协作' },
  { code: 'void_knight', name: '虚空骑士', hp: 1500, desc: '高难度，满编出击' },
] as const

type Tab = 'lobby' | 'mine'

export default function PvePage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [tab, setTab] = useState<Tab>('lobby')
  const [openRuns, setOpenRuns] = useState<PveRunVO[]>([])
  const [myRuns, setMyRuns] = useState<PveRunVO[]>([])
  const [detail, setDetail] = useState<PveRunVO | null>(null)
  const [busy, setBusy] = useState(false)
  const [starting, setStarting] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      if (tab === 'lobby') {
        const { data: res } = await petApi.listOpenPveRuns(1, 20)
        if (res.success) setOpenRuns((res.data as unknown as { records?: PveRunVO[] }).records ?? [])
      } else {
        const { data: res } = await petApi.listMyPveRuns(1, 20)
        if (res.success) setMyRuns((res.data as unknown as { records?: PveRunVO[] }).records ?? [])
      }
    } catch {
      Taro.showToast({ title: '加载失败', icon: 'none' })
    }
  }, [tab])

  useEffect(() => { void load() }, [load])

  const openDetail = async (runId: number) => {
    try {
      const res = await petApi.pveDetail(runId)
      if (res.data.success) setDetail(res.data.data)
    } catch {
      Taro.showToast({ title: '详情加载失败', icon: 'none' })
    }
  }

  const start = async (bossCode: string) => {
    if (starting) return
    setStarting(bossCode)
    try {
      const { data: res } = await petApi.startPveRun(bossCode)
      if (res.success && res.data) {
        Taro.showToast({ title: '副本已发起，等待队友加入', icon: 'none' })
        setDetail(res.data)
      }
    } catch {
      Taro.showToast({ title: '发起失败（可能已有进行中副本）', icon: 'none' })
    } finally {
      setStarting(null)
    }
  }

  const join = async (runId: number) => {
    if (busy) return
    setBusy(true)
    try {
      const { data: res } = await petApi.joinPveRun(runId)
      if (res.success && res.data) {
        Taro.showToast({ title: '已加入战斗！', icon: 'success' })
        setDetail(res.data)
      }
    } catch {
      Taro.showToast({ title: '加入失败（可能刚被别人加入）', icon: 'none' })
    } finally {
      setBusy(false)
    }
  }

  const attack = async (runId: number) => {
    if (busy) return
    setBusy(true)
    try {
      const { data: res } = await petApi.attackPveRun(runId)
      if (res.success && res.data) {
        setDetail(res.data)
        if (res.data.status === 'WON') Taro.showToast({ title: '🎉 Boss 讨伐成功！奖励已发放', icon: 'success' })
        else if (res.data.status === 'FAILED') Taro.showToast({ title: '副本失败，下次再战', icon: 'none' })
      }
    } catch {
      Taro.showToast({ title: '攻击失败（可能还没轮到你）', icon: 'none' })
    } finally {
      setBusy(false)
    }
  }

  const runCard = (run: PveRunVO, showJoin: boolean) => (
    <View key={run.runId} className={styles.card} onClick={() => openDetail(run.runId)}>
      <View className={styles.cardHead}>
        <Text className={styles.cardBoss}>👹 {run.bossName}</Text>
        <Text className={styles.cardStatus}>{run.status}</Text>
      </View>
      <Text className={styles.cardHp}>Boss HP {run.bossHp}/{run.bossMaxHp}</Text>
      <Text className={styles.cardMeta}>
        发起人宠物 {run.initiatorPetName ?? '—'}{run.partnerPetName ? ` × ${run.partnerPetName}` : ' · 等待队友'}
      </Text>
      {showJoin && run.status === 'OPEN' && (
        <View className={styles.joinBtn} onClick={() => join(run.runId)}>
          <Text className={styles.joinBtnText}>⚔️ 加入战斗</Text>
        </View>
      )}
    </View>
  )

  return (
    <View className={styles.page} style={{ ...PET_CREAM_STYLE, paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title="协作 PVE" back />
      <View className={styles.tabRow}>
        <View className={`${styles.tab} ${tab === 'lobby' ? styles.tabActive : ''}`} onClick={() => setTab('lobby')}>
          <Text className={styles.tabText}>招募大厅</Text>
        </View>
        <View className={`${styles.tab} ${tab === 'mine' ? styles.tabActive : ''}`} onClick={() => setTab('mine')}>
          <Text className={styles.tabText}>我的副本</Text>
        </View>
      </View>
      <ScrollView scrollY className={styles.scroll}>
        {tab === 'lobby' && (
          <>
            <Text className={styles.sectionLabel}>发起副本</Text>
            {BOSSES.map(boss => (
              <View key={boss.code} className={styles.bossCard}>
                <View className={styles.bossInfo}>
                  <Text className={styles.bossName}>👹 {boss.name}</Text>
                  <Text className={styles.bossHp}>HP {boss.hp} · {boss.desc}</Text>
                </View>
                <View
                  className={`${styles.startBtn} ${starting === boss.code ? styles.startBtnBusy : ''}`}
                  onClick={() => starting !== boss.code && start(boss.code)}
                >
                  <Text className={styles.startBtnText}>{starting === boss.code ? '发起中...' : '发起'}</Text>
                </View>
              </View>
            ))}
            <Text className={styles.sectionLabel}>等待队友的副本</Text>
            {openRuns.length === 0 ? (
              <Text className={styles.empty}>暂无等待中的副本，发起一个吧</Text>
            ) : openRuns.map(run => runCard(run, true))}
          </>
        )}
        {tab === 'mine' && (
          myRuns.length === 0 ? (
            <Text className={styles.empty}>还没有参与过副本</Text>
          ) : myRuns.map(run => runCard(run, false))
        )}
      </ScrollView>

      {/* 战斗操作浮层 */}
      {detail && (
        <View className={styles.detailMask} onClick={() => setDetail(null)}>
          <View className={styles.detailBody} onClick={e => e.stopPropagation()}>
            <Text className={styles.detailTitle}>👹 {detail.bossName}（{detail.status}）</Text>
            <View className={styles.hpBarTrack}>
              <View className={styles.hpBarFill} style={{ width: `${Math.max(0, Math.round(detail.bossHp / detail.bossMaxHp * 100))}%` }} />
            </View>
            <Text className={styles.hpText}>Boss HP {detail.bossHp}/{detail.bossMaxHp}</Text>
            <Text className={styles.hpText}>我方 HP：{detail.initiatorPetName} {detail.initiatorPetHp}{detail.partnerPetHp >= 0 ? ` · ${detail.partnerPetName} ${detail.partnerPetHp}` : ' · 等待队友'}</Text>
            <ScrollView scrollY className={styles.roundsBox}>
              {detail.recentRounds.length === 0 ? (
                <Text className={styles.roundsEmpty}>战斗尚未开始</Text>
              ) : detail.recentRounds.map((r, i) => (
                <Text key={i} className={styles.roundLine}>
                  R{r.round} {r.actorName} {r.critical ? '暴击' : ''}{r.dodged ? '被闪避' : ''} {r.action} {r.targetName}，造成 {r.damage} 伤害（余 {r.targetRemainingHp}）
                </Text>
              ))}
            </ScrollView>
            {detail.status === 'FIGHTING' && (
              <View className={styles.attackBtn} onClick={() => attack(detail.runId)}>
                <Text className={styles.attackBtnText}>{busy ? '结算中...' : '⚔️ 攻击'}</Text>
              </View>
            )}
            <Text className={styles.detailHint}>双方轮流行动；Boss 倒下后奖励自动发放</Text>
          </View>
        </View>
      )}
    </View>
  )
}
