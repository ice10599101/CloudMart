import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { petApi, type PveBoss, type PveRunVO } from '@/api/pet'
import { errorMessage } from '@/utils/request'
import { PET_CREAM_STYLE } from '@/styles/petCream'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

/**
 * 协作 PVE 副本（§6/R03/R19）：招募大厅（加入他人）+ 发起 + 战斗操作。
 * Boss 配置由 GET /pve/bosses 服务端权威下发，前端不硬编码 HP/攻击/防御；
 * 轮流体感由服务端 turnNo/行动方约束，攻击按服务端结果渲染。
 */

type Tab = 'lobby' | 'mine'

export default function PvePage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [tab, setTab] = useState<Tab>('lobby')
  const [bosses, setBosses] = useState<PveBoss[]>([])
  const [openRuns, setOpenRuns] = useState<PveRunVO[]>([])
  const [myRuns, setMyRuns] = useState<PveRunVO[]>([])
  const [detail, setDetail] = useState<PveRunVO | null>(null)
  const [busy, setBusy] = useState(false)
  const [starting, setStarting] = useState<string | null>(null)

  // R19：Boss 配置服务端下发（失败显示空态+提示，不回退内置数值）
  useEffect(() => {
    petApi.listPveBosses()
      .then(({ data: res }) => setBosses(res.data ?? []))
      .catch(() => setBosses([]))
  }, [])

  const load = useCallback(async () => {
    try {
      if (tab === 'lobby') {
        const { data: res } = await petApi.listOpenPveRuns(1, 20)
        setOpenRuns((res.data as unknown as { records?: PveRunVO[] }).records ?? [])
      } else {
        const { data: res } = await petApi.listMyPveRuns(1, 20)
        setMyRuns((res.data as unknown as { records?: PveRunVO[] }).records ?? [])
      }
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '加载失败'), icon: 'none' })
    }
  }, [tab])

  useEffect(() => { void load() }, [load])

  const openDetail = async (runId: number) => {
    try {
      const res = await petApi.pveDetail(runId)
      setDetail(res.data.data)
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '详情加载失败'), icon: 'none' })
    }
  }

  const start = async (bossCode: string) => {
    if (starting) return
    setStarting(bossCode)
    try {
      const { data: res } = await petApi.startPveRun(bossCode)
      if (res.data) {
        Taro.showToast({ title: '副本已发起，等待队友加入', icon: 'none' })
        setDetail(res.data)
      }
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '发起失败（可能已有进行中副本）'), icon: 'none' })
    } finally {
      setStarting(null)
    }
  }

  const join = async (runId: number) => {
    if (busy) return
    setBusy(true)
    try {
      const { data: res } = await petApi.joinPveRun(runId)
      if (res.data) {
        Taro.showToast({ title: '已加入战斗！', icon: 'success' })
        setDetail(res.data)
      }
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '加入失败（可能刚被别人加入）'), icon: 'none' })
    } finally {
      setBusy(false)
    }
  }

  const attack = async (runId: number) => {
    if (busy) return
    setBusy(true)
    try {
      const { data: res } = await petApi.attackPveRun(runId)
      if (res.data) {
        setDetail(res.data)
        if (res.data.status === 'WON') Taro.showToast({ title: '🎉 Boss 讨伐成功！奖励已发放', icon: 'success' })
        else if (res.data.status === 'FAILED') Taro.showToast({ title: '副本失败，下次再战', icon: 'none' })
      }
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '攻击失败（可能还没轮到你）'), icon: 'none' })
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
            {bosses.length === 0 ? (
              <Text className={styles.empty}>Boss 配置加载中或暂未开放</Text>
            ) : bosses.map(boss => (
              <View key={boss.bossCode} className={styles.bossCard}>
                <View className={styles.bossInfo}>
                  <Text className={styles.bossName}>👹 {boss.name}</Text>
                  <Text className={styles.bossHp}>{`HP ${boss.maxHp} · 攻击 ${boss.attack} · 防御 ${boss.defense}`}</Text>
                </View>
                <View
                  className={`${styles.startBtn} ${starting === boss.bossCode ? styles.startBtnBusy : ''}`}
                  onClick={() => starting !== boss.bossCode && start(boss.bossCode)}
                >
                  <Text className={styles.startBtnText}>{starting === boss.bossCode ? '发起中...' : '发起'}</Text>
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
