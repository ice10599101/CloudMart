import { useState, useEffect, useCallback } from 'react'
import { View, Text, TouchableOpacity, ScrollView, ActivityIndicator, Alert } from 'react-native'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { petApi } from '@/api/pet'
import type { PetInfo, PetMinigameRoundItem, PetActivityRow } from '@/api/pet'
import { errorMessage } from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors as BaseWishColors } from '@/constants/wish-theme'
import { PetCreamTheme } from '@/constants/pet-cream'

/** 法式奶油皮肤：覆写愿望主题的视觉键（本页专用；accentCyan 是本页主按钮色，一并转黄铜） */
const WishColors = { ...BaseWishColors, ...PetCreamTheme, accentCyan: PetCreamTheme.primary }

const CATCH_WINDOWS = 10

/** 接球小游戏当前窗口目标槽位（服务端序列权威） */
type MinigameTarget = 'LEFT' | 'CENTER' | 'RIGHT' | null

function asMinigameTarget(value: string | undefined): MinigameTarget {
  return value === 'LEFT' || value === 'CENTER' || value === 'RIGHT' ? value : null
}

interface RoundState {
  roundId: string
  deadlineAt: number
  /** 服务端开局时间（epoch ms，R11 窗口号由此计算） */
  startedAt: number
  /** 服务端窗口时长（ms） */
  windowMs: number
  submitted: number
  // PET-17：服务端权威目标序列与已接受窗口（恢复回合后目标仍可见）
  sequence: string[]
  acceptedWindows: number[]
}

interface CustodyState {
  active: boolean
  endsAt?: string
  careFeedUsed?: number
  careCleanUsed?: number
  weekUsed?: boolean
}

interface DigestState {
  throughAt: string
  offlineHours: number
  finishedTasks: number
  claimableTasks: number
  visits: number
  milestones: number
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View
      style={{
        backgroundColor: WishColors.bgContainer,
        borderRadius: BorderRadius.lg,
        padding: Spacing.md,
        marginBottom: Spacing.sm,
        gap: Spacing.xs,
      }}
    >
      <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text }}>{title}</Text>
      {children}
    </View>
  )
}

/** 玩法操作按钮（模块级组件：onPress 被识别为事件处理器，
 *  使传入回调内的 ref/时间读取不被纯度分析误判为渲染期执行） */
function PlayButton({
  label,
  onPress,
  primary = false,
  disabled = false,
}: {
  label: string
  onPress: () => void
  primary?: boolean
  disabled?: boolean
}) {
  return (
    <TouchableOpacity
      onPress={onPress}
      disabled={disabled}
      style={{
        paddingVertical: 8,
        paddingHorizontal: 14,
        borderRadius: BorderRadius.md,
        backgroundColor: primary ? WishColors.accentCyan : 'transparent',
        borderWidth: 1,
        borderColor: WishColors.border,
        opacity: disabled ? 0.5 : 1,
      }}
    >
      <Text style={{ fontSize: FontSize.xs, color: primary ? '#FFFDF8' : WishColors.textSecondary }}>{label}</Text>
    </TouchableOpacity>
  )
}

/** 玩法中心（N 系列）：接球 / 托管 / 摘要 / 合作 / 图鉴；服务端权威（§7.4-§7.7） */
export default function PetPlayScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [pet, setPet] = useState<PetInfo | null>(null)
  const [round, setRound] = useState<RoundState | null>(null)
  const [remaining, setRemaining] = useState(0)
  // PET-17/T41：当前窗口目标随计时 tick 推进（禁止渲染期读取时钟/ref 计算）
  const [activeTarget, setActiveTarget] = useState<MinigameTarget>(null)
  // R11：服务端时钟偏移（state：effect 与事件回调共用，不可使用会被变异的 ref）
  const [serverOffsetMs, setServerOffsetMs] = useState(0)
  const [mgResult, setMgResult] = useState('')
  const [custody, setCustody] = useState<CustodyState | null>(null)
  const [digest, setDigest] = useState<DigestState | null>(null)
  const [coops, setCoops] = useState<Record<string, unknown>[]>([])
  const [collection, setCollection] = useState<{ total: number; unlocked: number } | null>(null)
  // B09 限时活动（与常驻 /pet/events 是两套体系）
  const [activities, setActivities] = useState<PetActivityRow[]>([])
  const [pending, setPending] = useState<string | null>(null)
  // N04 对局历史（最近 10 局）
  const [history, setHistory] = useState<PetMinigameRoundItem[]>([])

  const loadAll = useCallback(async () => {
    try {
      const petRes = await petApi.getMyPet()
      if (petRes.data.data) setPet(petRes.data.data)
      const custodyRes = await petApi.getCustodyStatus()
      if (custodyRes.data.data) setCustody(custodyRes.data.data)
      const digestRes = await petApi.getOfflineDigest()
      if (digestRes.data.data) setDigest(digestRes.data.data)
      const coopRes = await petApi.listCooperations()
      if (coopRes.data.data) setCoops(coopRes.data.data)
      const collRes = await petApi.getCollectionStats()
      if (collRes.data.data) {
        setCollection({ total: Number(collRes.data.data.total ?? 0), unlocked: Number(collRes.data.data.unlocked ?? 0) })
      }
      const histRes = await petApi.listMinigameRounds(1, 10)
      if (histRes.data.data) setHistory(histRes.data.data)
      const actRes = await petApi.listActivities()
      if (actRes.data.data) setActivities(actRes.data.data)
    } catch {
      // 后台聚合加载失败静默，保留各卡片既有状态
    }
  }, [])

  const claimActivity = async (activityId: number | string) => {
    setPending(`activity-${activityId}`)
    try {
      await petApi.claimActivity(activityId)
      const actRes = await petApi.listActivities()
      if (actRes.data.data) setActivities(actRes.data.data)
    } catch (error) {
      Alert.alert('领取失败', errorMessage(error, '请稍后再试'))
    } finally {
      setPending(null)
    }
  }

  useEffect(() => {
    if (!isLoggedIn) router.replace('/login')
  }, [isLoggedIn])

  useEffect(() => {
    if (!isLoggedIn) return
    async function init() {
      await loadAll()
    }
    void init()
  }, [isLoggedIn, loadAll])

  useEffect(() => {
    if (!round) return
    // interval 回调属于外部订阅：时钟读取/setState 均在此事件上下文进行
    const timer = setInterval(() => {
      setRemaining(Math.max(0, Math.round((round.deadlineAt - Date.now()) / 1000)))
      const serverNow = Date.now() + serverOffsetMs
      const windowIndex = Math.floor((serverNow - round.startedAt) / round.windowMs) + 1
      setActiveTarget(asMinigameTarget(round.sequence[windowIndex - 1]))
    }, 500)
    return () => clearInterval(timer)
  }, [round, serverOffsetMs])

  const startRound = async () => {
    if (!pet) return
    try {
      const res = await petApi.startMinigameRound(pet.petId)
      if (!res.data.data) return
      const data = res.data.data as unknown as Record<string, unknown> & { roundId: number | string; deadlineAt: string; startedAt?: string; serverNow?: string; windowMs?: number }
      const startedAt = data.startedAt ? Date.parse(data.startedAt) : Date.now()
      const windowMs = typeof data.windowMs === 'number' ? data.windowMs : 3000
      const offset = data.serverNow ? Date.parse(data.serverNow) - Date.now() : serverOffsetMs
      setServerOffsetMs(offset)
      const sequence = Array.isArray(data.sequence) ? (data.sequence as string[]) : []
      setRound({
        roundId: String(data.roundId),
        deadlineAt: new Date(data.deadlineAt).getTime(),
        startedAt,
        windowMs,
        submitted: 0,
        sequence,
        acceptedWindows: [],
      })
      // 首帧目标即时显示（事件上下文中读取时钟合法；后续由 500ms tick 推进）
      const firstWindowIndex = Math.floor((Date.now() + offset - startedAt) / windowMs) + 1
      setActiveTarget(asMinigameTarget(sequence[firstWindowIndex - 1]))
      setMgResult('')
      setRemaining(30)
    } catch (error) {
      Alert.alert('开始失败', errorMessage(error, '请稍后再试'))
    }
  }

  const catchSlot = async (slot: 'LEFT' | 'CENTER' | 'RIGHT') => {
    if (!round) return
    // R11：窗口号由服务端时钟决定（不再按点击次数递增）；服务端 accepted 权威
    const serverNow = Date.now() + serverOffsetMs
    const windowIndex = Math.floor((serverNow - round.startedAt) / round.windowMs) + 1
    if (windowIndex < 1 || windowIndex > CATCH_WINDOWS) return
    try {
      const res = await petApi.submitMinigameOps(round.roundId, [{ seq: windowIndex, windowIndex, slot }])
      if (!res.data.data) return
      const body = res.data.data as { accepted: number; totalAccepted?: number }
      // PET-17：窗口归属以服务端为准——accepted 为本批新接受数（重复/迟到窗口为 0，不追加本地）
      const acceptedCount = Number(body.totalAccepted ?? round.submitted)
      setRound({
        ...round,
        submitted: acceptedCount,
        acceptedWindows: body.accepted >= 1 && !round.acceptedWindows.includes(windowIndex)
          ? [...round.acceptedWindows, windowIndex]
          : round.acceptedWindows,
      })
    } catch (error) {
      Alert.alert('操作无效', errorMessage(error, '请稍后再试'))
    }
  }

  // R11：重进页面恢复当前局（断线恢复，同 roundId 续玩）
  const restoreCurrentRound = useCallback(async () => {
    try {
      const res = await petApi.currentMinigameRound()
      const data = res.data.data ?? null
      const roundData = data?.round
      if (roundData && roundData.roundId) {
        const startedAt = Date.parse(String(roundData.startedAt))
        const windowMs = typeof roundData.windowMs === 'number' ? roundData.windowMs : 3000
        const offset = roundData.serverNow
          ? Date.parse(String(roundData.serverNow)) - Date.now()
          : serverOffsetMs
        setServerOffsetMs(offset)
        const acceptedWindows = Array.isArray(roundData.acceptedWindows)
          ? (roundData.acceptedWindows as number[]).map(Number)
          : []
        const sequence = Array.isArray(roundData.sequence) ? (roundData.sequence as string[]) : []
        setRound({
          roundId: String(roundData.roundId),
          deadlineAt: new Date(String(roundData.deadlineAt)).getTime(),
          startedAt,
          windowMs,
          submitted: acceptedWindows.length,
          sequence,
          acceptedWindows,
        })
        const restoredWindowIndex = Math.floor((Date.now() + offset - startedAt) / windowMs) + 1
        setActiveTarget(asMinigameTarget(sequence[restoredWindowIndex - 1]))
        setRemaining(Math.max(0, Math.round((new Date(String(roundData.deadlineAt)).getTime() - (Date.now() + offset)) / 1000)))
      }
    } catch {
      // 无进行中对局或查询失败：保持未开局状态
    }
  }, [])

  const settleRound = async () => {
    if (!round) return
    try {
      const res = await petApi.settleMinigame(round.roundId)
      if (res.data.data && res.data.data.status === 'SETTLED') {
        const r = res.data.data
        setMgResult(
          r.validCompletion
            ? `完成！经验+${r.reward.exp} 亲密度+${r.reward.intimacy} 心情+${r.reward.happiness}`
            : '本局未达标（需接住 3 个），无收益',
        )
        setRound(null)
        setActiveTarget(null)
        // 结算后刷新对局历史
        const histRes = await petApi.listMinigameRounds(1, 10)
        if (histRes.data.data) setHistory(histRes.data.data)
      }
    } catch (error) {
      Alert.alert('结算失败', errorMessage(error, '请稍后再试'))
    }
  }

  useEffect(() => {
    if (!round || remaining !== 0) return
    async function settleWhenTimeout() {
      await settleRound()
    }
    void settleWhenTimeout()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [remaining, round])

  useEffect(() => {
    if (!isLoggedIn) return
    async function restore() {
      await restoreCurrentRound()
    }
    void restore()
  }, [isLoggedIn, restoreCurrentRound])

  const confirmDigest = async () => {
    if (!digest) return
    try {
      await petApi.confirmOfflineDigest(digest.throughAt)
      void loadAll()
    } catch (error) {
      Alert.alert('操作失败', errorMessage(error, '请稍后再试'))
    }
  }

  const custodyAction = async (start: boolean) => {
    try {
      if (start) await petApi.startCustody()
      else await petApi.endCustody()
      void loadAll()
    } catch (error) {
      Alert.alert(start ? '开启托管失败' : '结束托管失败', errorMessage(error, '请稍后再试'))
    }
  }

  const coopAction = async (id: string, action: 'accept' | 'claim' | 'leave') => {
    try {
      if (action === 'accept') await petApi.acceptCooperation(id)
      else if (action === 'claim') await petApi.claimCooperationReward(id)
      else await petApi.leaveCooperation(id)
      void loadAll()
    } catch (error) {
      const title = action === 'accept' ? '接受失败' : action === 'claim' ? '领取失败' : '退出失败'
      Alert.alert(title, errorMessage(error, '请稍后再试'))
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: Spacing.lg, paddingBottom: Spacing.sm }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.accentCyan }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>玩法中心</Text>
        <View style={{ width: 48 }} />
      </View>

      <ScrollView contentContainerStyle={{ padding: Spacing.lg, paddingBottom: Spacing.xl }}>
        <Card title="🎯 接球小游戏（每日 5 局有收益）">
          {!round && !mgResult && (
            <PlayButton label="开始一局" onPress={() => void startRound()} primary disabled={!pet} />
          )}
          {round && (
            <View style={{ gap: Spacing.xs }}>
              <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text }}>
                {remaining}s · 接住 {round.submitted}/{CATCH_WINDOWS}
              </Text>
              {activeTarget ? (
                <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan }}>
                  本窗目标：{activeTarget === 'LEFT' ? '左' : activeTarget === 'CENTER' ? '中' : '右'}
                </Text>
              ) : null}
              <View style={{ flexDirection: 'row', gap: Spacing.xs, flexWrap: 'wrap' }}>
                <PlayButton label="←左" onPress={() => void catchSlot('LEFT')} disabled={remaining === 0} />
                <PlayButton label="●中" onPress={() => void catchSlot('CENTER')} disabled={remaining === 0} />
                <PlayButton label="右→" onPress={() => void catchSlot('RIGHT')} disabled={remaining === 0} />
                <PlayButton label="提前结束" onPress={() => void settleRound()} />
              </View>
            </View>
          )}
          {mgResult ? (
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>{mgResult}</Text>
          ) : null}
          {history.length > 0 && (
            <View style={{ gap: 2 }}>
              <Text style={{ fontSize: FontSize.xs, fontWeight: '700', color: WishColors.text }}>最近对局</Text>
              {history.map((item) => (
                <Text key={String(item.roundId)} style={{ fontSize: 10, color: WishColors.textSecondary }}>
                  {`#${String(item.roundId)} · 接住 ${item.successCount}/10 · ${item.rewardEligible ? '有收益' : '训练局'}${item.startedAt ? ` · ${item.startedAt.slice(5, 16).replace('T', ' ')}` : ''}`}
                </Text>
              ))}
            </View>
          )}
          {mgResult ? <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>{mgResult}</Text> : null}
        </Card>

        <Card title="🏨 有限托管（每周 1 次 / 24 小时）">
          {custody?.active ? (
            <View style={{ gap: Spacing.xs }}>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>
                托管中 · 喂食 {custody.careFeedUsed ?? 0}/2 · 清洁 {custody.careCleanUsed ?? 0}/1
              </Text>
              <PlayButton label="提前结束（名额不恢复）" onPress={() => void custodyAction(false)} />
            </View>
          ) : custody?.weekUsed ? (
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>本周已使用，下周一再试</Text>
          ) : (
            <PlayButton label="开始托管（24 小时）" onPress={() => void custodyAction(true)} primary />
          )}
        </Card>

        {digest && (
          <Card title={`📜 离线回顾（约 ${digest.offlineHours} 小时）`}>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>
              完成 {digest.finishedTasks} · 待领 {digest.claimableTasks} · 来访 {digest.visits} · 里程碑 {digest.milestones}
            </Text>
            <PlayButton label="我知道了" onPress={() => void confirmDigest()} />
          </Card>
        )}

        <Card title="🤝 好友合作（在宠物页邀请）">
          {coops.length === 0 ? (
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>暂无合作记录</Text>
          ) : (
            coops.map((c) => {
              const status = String(c.status)
              const id = String(c.id)
              return (
                <View key={id} style={{ flexDirection: 'row', gap: Spacing.xs, alignItems: 'center', marginBottom: Spacing.xs }}>
                  <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary, flex: 1 }}>
                    {status === 'INVITED' ? '待接受' : status === 'ACTIVE' ? '进行中' : status === 'COMPLETED' ? '已达成' : '已结束'}
                  </Text>
                  {status === 'INVITED' && (
                    <PlayButton label="接受" onPress={() => void coopAction(id, 'accept')} primary />
                  )}
                  {status === 'COMPLETED' && (
                    <PlayButton label="领取" onPress={() => void coopAction(id, 'claim')} primary />
                  )}
                  {(status === 'INVITED' || status === 'ACTIVE') && (
                    <PlayButton label="退出" onPress={() => void coopAction(id, 'leave')} />
                  )}
                </View>
              )
            })
          )}
        </Card>

        <Card title="📖 收藏图鉴">
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>
            {collection ? `已解锁 ${collection.unlocked}/${collection.total}` : '加载中…'}
          </Text>
        </Card>

        {/* B09 限时活动（与常驻 /pet/events 是两套体系；结束后 72 小时内可领奖） */}
        {activities.length > 0 && (
          <Card title="🎈 限时活动">
            {activities.map((item) => (
              <View key={String(item.activityId)} style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
                <Text style={{ flex: 1, fontSize: FontSize.xs, color: WishColors.textSecondary }}>
                  {item.configName ?? item.activityType}
                  {' · '}{item.status === 'COMPLETED' ? '可领取' : item.status === 'ACTIVE' ? `进行中（剩余 ${Math.ceil(item.remainingSeconds / 60)} 分钟）` : item.status === 'CLAIMED' ? '已领取' : '已过期'}
                </Text>
                {item.canClaim && (
                  <TouchableOpacity disabled={pending === `activity-${item.activityId}`} onPress={() => void claimActivity(item.activityId)}>
                    <Text style={{ fontSize: FontSize.xs, color: WishColors.primary }}>领取</Text>
                  </TouchableOpacity>
                )}
              </View>
            ))}
          </Card>
        )}

        <ActivityIndicator style={{ marginTop: Spacing.md }} color={WishColors.accentCyan} />
      </ScrollView>
    </View>
  )
}
