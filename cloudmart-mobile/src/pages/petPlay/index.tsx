import { useState, useEffect, useCallback, useRef } from 'react'
import { View, Text, Button, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { petApi, petCompanionApi } from '@/api/pet'
import { PET_CREAM_STYLE } from '@/styles/petCream'
import type { PetInfo, PetMinigameRoundItem, PetActivityRow } from '@/api/pet'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

const CATCH_WINDOWS = 10
const SLOT_LABEL: Record<string, string> = { LEFT: '←左', CENTER: '●中', RIGHT: '右→' }

interface RoundState {
  roundId: string
  deadlineAt: number
  /** 服务端开局时间（epoch ms，R11 窗口号由此计算） */
  startedAt: number
  /** 服务端窗口时长（ms） */
  windowMs: number
  submitted: number
}

/**
 * 玩法中心（N 系列）：接球小游戏 / 有限托管 / 离线摘要 / 好友合作 / 收藏图鉴。
 * 规则与时窗服务端权威；确认只推进展示上界（BE-10）；领取按 claim 唯一事实幂等（BE-01）。
 */
export default function PetPlayPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [pet, setPet] = useState<PetInfo | null>(null)
  const [round, setRound] = useState<RoundState | null>(null)
  const [remaining, setRemaining] = useState(0)
  const [mgResult, setMgResult] = useState<string>('')
  const [custody, setCustody] = useState<{ active: boolean; endsAt?: string; careFeedUsed?: number; careCleanUsed?: number; weekUsed?: boolean } | null>(null)
  const [digest, setDigest] = useState<{ throughAt: string; offlineHours: number; finishedTasks: number; claimableTasks: number; visits: number; milestones: number } | null>(null)
  const [coops, setCoops] = useState<Array<Record<string, unknown>>>([])
  const [collection, setCollection] = useState<{ total: number; unlocked: number } | null>(null)
  // N04 对局历史（最近 10 局，offset 分页第一页）
  const [history, setHistory] = useState<PetMinigameRoundItem[]>([])
  // B09 限时活动（与常驻 /pet/events 是两套体系；结束后 72 小时内可领奖）
  const [activities, setActivities] = useState<PetActivityRow[]>([])

  const loadAll = useCallback(async () => {
    const petRes = await petApi.getMyPet()
    if (petRes.data.success && petRes.data.data) {
      setPet(petRes.data.data)
    }
    const custodyRes = await petApi.getCustodyStatus()
    if (custodyRes.data.success) setCustody(custodyRes.data.data)
    const digestRes = await petApi.getOfflineDigest()
    if (digestRes.data.success && digestRes.data.data) setDigest(digestRes.data.data)
    const coopRes = await petApi.listCooperations()
    if (coopRes.data.success && coopRes.data.data) setCoops(coopRes.data.data)
    const collRes = await petApi.getCollectionStats()
    if (collRes.data.success && collRes.data.data) {
      setCollection({ total: Number(collRes.data.data.total ?? 0), unlocked: Number(collRes.data.data.unlocked ?? 0) })
    }
    const histRes = await petCompanionApi.listMinigameRounds(1, 10)
    if (histRes.data.success && histRes.data.data) setHistory(histRes.data.data)
    const actRes = await petCompanionApi.listActivities()
    if (actRes.data.success && actRes.data.data) setActivities(actRes.data.data)
  }, [])

  useEffect(() => {
    if (isLoggedIn) void loadAll()
  }, [isLoggedIn, loadAll])

  useEffect(() => {
    if (isLoggedIn) void restoreCurrentRound()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn])

  // 小游戏倒计时
  useEffect(() => {
    if (!round) return
    const timer = setInterval(() => {
      setRemaining(Math.max(0, Math.round((round.deadlineAt - Date.now()) / 1000)))
    }, 500)
    return () => clearInterval(timer)
  }, [round])

  // R11：服务端时钟偏移——窗口归属以服务端时间为准（不再按点击次数递增）
  const serverOffsetRef = useRef(0)

  const startRound = async () => {
    if (!pet) return
    const res = await petApi.startMinigameRound(pet.petId)
    if (res.data.success && res.data.data) {
      const data = res.data.data as unknown as Record<string, unknown> & { roundId: number | string; deadlineAt: string; startedAt?: string; serverNow?: string; windowMs?: number }
      const startedAt = data.startedAt ? Date.parse(data.startedAt) : Date.now()
      const windowMs = typeof data.windowMs === 'number' ? data.windowMs : 3000
      if (data.serverNow) {
        serverOffsetRef.current = Date.parse(data.serverNow) - Date.now()
      }
      setRound({
        roundId: String(data.roundId),
        deadlineAt: new Date(data.deadlineAt).getTime(),
        startedAt,
        windowMs,
        submitted: 0,
      })
      setMgResult('')
      setRemaining(30)
    }
  }

  const catchSlot = async (slot: 'LEFT' | 'CENTER' | 'RIGHT') => {
    if (!round) return
    const serverNow = Date.now() + serverOffsetRef.current
    const windowIndex = Math.floor((serverNow - round.startedAt) / round.windowMs) + 1
    if (windowIndex < 1 || windowIndex > CATCH_WINDOWS) return
    const res = await petApi.submitMinigameOps(round.roundId, [{ seq: windowIndex, windowIndex, slot }])
    if (res.data.success && res.data.data) {
      const accepted = Number((res.data.data as { totalAccepted?: number }).totalAccepted ?? round.submitted)
      setRound({ ...round, submitted: accepted })
    }
  }

  // R11：重进页面恢复当前局（断线恢复，同 roundId 续玩）
  const restoreCurrentRound = useCallback(async () => {
    const res = await petApi.currentMinigameRound()
    const data = res.data.success ? (res.data.data as { round?: Record<string, unknown> | null } | null) : null
    const roundData = data?.round
    if (roundData && roundData.roundId) {
      const startedAt = Date.parse(String(roundData.startedAt))
      const windowMs = typeof roundData.windowMs === 'number' ? roundData.windowMs : 3000
      if (roundData.serverNow) {
        serverOffsetRef.current = Date.parse(String(roundData.serverNow)) - Date.now()
      }
      const deadline = new Date(String(roundData.deadlineAt)).getTime()
      const accepted = Array.isArray(roundData.acceptedWindows) ? roundData.acceptedWindows.length : 0
      setRound({
        roundId: String(roundData.roundId),
        deadlineAt: deadline,
        startedAt,
        windowMs,
        submitted: accepted,
      })
      setRemaining(Math.max(0, Math.round((deadline - (Date.now() + serverOffsetRef.current)) / 1000)))
    }
  }, [])

  const settleRound = async () => {
    if (!round) return
    const res = await petApi.settleMinigame(round.roundId)
    if (res.data.success && res.data.data && res.data.data.status === 'SETTLED') {
      const r = res.data.data
      setMgResult(r.validCompletion
        ? `完成！经验+${r.reward.exp} 亲密度+${r.reward.intimacy} 心情+${r.reward.happiness}`
        : '本局未达标（需接住 3 个），无收益')
      setRound(null)
      // 结算后刷新对局历史
      const histRes = await petCompanionApi.listMinigameRounds(1, 10)
      if (histRes.data.success && histRes.data.data) setHistory(histRes.data.data)
    }
  }

  useEffect(() => {
    if (round && remaining === 0) void settleRound()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [remaining, round])

  const confirmDigest = async () => {
    if (!digest) return
    const res = await petApi.confirmOfflineDigest(digest.throughAt)
    if (res.data.success) {
      Taro.showToast({ title: '已确认', icon: 'success' })
      void loadAll()
    }
  }

  const custodyAction = async (start: boolean) => {
    const res = start ? await petApi.startCustody() : await petApi.endCustody()
    if (res.data.success) {
      Taro.showToast({ title: start ? '托管已开始' : '已结束', icon: 'success' })
      void loadAll()
    }
  }

  const coopAction = async (id: string, action: 'accept' | 'claim' | 'leave') => {
    if (action === 'accept') await petApi.acceptCooperation(id)
    else if (action === 'claim') await petApi.claimCooperationReward(id)
    else await petApi.leaveCooperation(id)
    Taro.showToast({ title: '操作成功', icon: 'success' })
    void loadAll()
  }

  const claimActivity = async (activityId: number | string) => {
    const res = await petCompanionApi.claimActivity(activityId)
    if (res.data.success) {
      Taro.showToast({ title: '已领取', icon: 'success' })
      void loadAll()
    }
  }

  return (
    <View className={styles.page} style={{ ...PET_CREAM_STYLE, paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title="玩法中心" back />
      <ScrollView scrollY className={styles.list}>

        {/* N04 接球 */}
        <View className={styles.card}>
          <Text className={styles.cardTitle}>🎯 接球小游戏</Text>
          {!round && !mgResult && <Button size='mini' type='primary' onClick={startRound}>开始一局</Button>}
          {round && (
            <View className={styles.roundBox}>
              <Text className={styles.countdown}>{remaining}s · 接住 {round.submitted}/{CATCH_WINDOWS}</Text>
              <View className={styles.slotRow}>
                {(['LEFT', 'CENTER', 'RIGHT'] as const).map((s) => (
                  <Button key={s} size='mini' disabled={remaining === 0} onClick={() => catchSlot(s)}>{SLOT_LABEL[s]}</Button>
                ))}
              </View>
              <Button size='mini' onClick={settleRound}>提前结束</Button>
            </View>
          )}
          {mgResult ? <Text className={styles.meta}>{mgResult}</Text> : null}
          {history.length > 0 && (
            <View className={styles.col}>
              <Text className={styles.cardTitle}>最近对局</Text>
              {history.map((item) => (
                <Text key={String(item.roundId)} className={styles.meta}>
                  #{String(item.roundId)} · 接住 {item.successCount}/10 · {item.rewardEligible ? '有收益' : '训练局'}
                  {item.startedAt ? ` · ${item.startedAt.slice(5, 16).replace('T', ' ')}` : ''}
                </Text>
              ))}
            </View>
          )}
        </View>

        {/* N05 托管 */}
        <View className={styles.card}>
          <Text className={styles.cardTitle}>🏨 有限托管</Text>
          {custody?.active ? (
            <View className={styles.col}>
              <Text className={styles.meta}>托管中 · 照顾 {custody.careFeedUsed ?? 0}/2 喂食 · {custody.careCleanUsed ?? 0}/1 清洁</Text>
              <Button size='mini' onClick={() => custodyAction(false)}>提前结束（名额不恢复）</Button>
            </View>
          ) : custody?.weekUsed ? (
            <Text className={styles.meta}>本周已使用，下周一再试</Text>
          ) : (
            <Button size='mini' type='primary' onClick={() => custodyAction(true)}>开始托管（24 小时）</Button>
          )}
        </View>

        {/* N05 摘要 */}
        {digest && (
          <View className={styles.card}>
            <Text className={styles.cardTitle}>📜 离线回顾（约 {digest.offlineHours} 小时）</Text>
            <Text className={styles.meta}>
              完成 {digest.finishedTasks} · 待领 {digest.claimableTasks} · 来访 {digest.visits} · 里程碑 {digest.milestones}
            </Text>
            <Button size='mini' onClick={confirmDigest}>我知道了</Button>
          </View>
        )}

        {/* N06 合作 */}
        <View className={styles.card}>
          <Text className={styles.cardTitle}>🤝 好友合作（在宠物页邀请）</Text>
          {coops.length === 0 ? <Text className={styles.meta}>暂无合作记录</Text> : coops.map((c) => {
            const status = String(c.status)
            const id = String(c.id)
            return (
              <View key={id} className={styles.coopRow}>
                <Text className={styles.meta}>{status === 'INVITED' ? '待接受' : status === 'ACTIVE' ? '进行中' : status === 'COMPLETED' ? '已达成' : '已结束'}</Text>
                <View className={styles.slotRow}>
                  {status === 'INVITED' && <Button size='mini' type='primary' onClick={() => coopAction(id, 'accept')}>接受</Button>}
                  {status === 'COMPLETED' && <Button size='mini' type='primary' onClick={() => coopAction(id, 'claim')}>领取</Button>}
                  {(status === 'INVITED' || status === 'ACTIVE') && <Button size='mini' onClick={() => coopAction(id, 'leave')}>退出</Button>}
                </View>
              </View>
            )
          })}
        </View>

        {/* N07 图鉴 */}
        <View className={styles.card}>
          <Text className={styles.cardTitle}>📖 收藏图鉴</Text>
          <Text className={styles.meta}>{collection ? `已解锁 ${collection.unlocked}/${collection.total}` : '加载中…'}</Text>
        </View>

        {/* B09 限时活动 */}
        {activities.length > 0 && (
          <View className={styles.card}>
            <Text className={styles.cardTitle}>🎈 限时活动</Text>
            {activities.map((item) => (
              <View key={String(item.activityId)} className={styles.coopRow}>
                <Text className={styles.meta}>
                  {item.configName ?? item.activityType}
                  {' · '}{item.status === 'COMPLETED' ? '可领取' : item.status === 'ACTIVE' ? `进行中（剩余 ${Math.ceil(item.remainingSeconds / 60)} 分钟）` : item.status === 'CLAIMED' ? '已领取' : '已过期'}
                </Text>
                {item.canClaim && (
                  <Button size='mini' type='primary' onClick={() => void claimActivity(item.activityId)}>领取</Button>
                )}
              </View>
            ))}
          </View>
        )}

        {!isLoggedIn && <View className={styles.card}><Text className={styles.meta}>请先登录</Text></View>}
      </ScrollView>
    </View>
  )
}
