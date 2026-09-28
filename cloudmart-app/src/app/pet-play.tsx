import { useState, useEffect, useCallback } from 'react'
import { View, Text, TouchableOpacity, ScrollView, ActivityIndicator } from 'react-native'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { petApi } from '@/api/pet'
import type { PetInfo } from '@/api/pet'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

const CATCH_WINDOWS = 10

interface RoundState {
  roundId: string
  deadlineAt: number
  submitted: number
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

/** 玩法中心（N 系列）：接球 / 托管 / 摘要 / 合作 / 图鉴；服务端权威（§7.4-§7.7） */
export default function PetPlayScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [pet, setPet] = useState<PetInfo | null>(null)
  const [round, setRound] = useState<RoundState | null>(null)
  const [remaining, setRemaining] = useState(0)
  const [mgResult, setMgResult] = useState('')
  const [custody, setCustody] = useState<CustodyState | null>(null)
  const [digest, setDigest] = useState<DigestState | null>(null)
  const [coops, setCoops] = useState<Record<string, unknown>[]>([])
  const [collection, setCollection] = useState<{ total: number; unlocked: number } | null>(null)

  const loadAll = useCallback(async () => {
    const petRes = await petApi.getMyPet()
    if (petRes.data?.success && petRes.data.data) setPet(petRes.data.data)
    const custodyRes = await petApi.getCustodyStatus()
    if (custodyRes.data?.success && custodyRes.data.data) setCustody(custodyRes.data.data)
    const digestRes = await petApi.getOfflineDigest()
    if (digestRes.data?.success && digestRes.data.data) setDigest(digestRes.data.data)
    const coopRes = await petApi.listCooperations()
    if (coopRes.data?.success && coopRes.data.data) setCoops(coopRes.data.data)
    const collRes = await petApi.getCollectionStats()
    if (collRes.data?.success && collRes.data.data) {
      setCollection({ total: Number(collRes.data.data.total ?? 0), unlocked: Number(collRes.data.data.unlocked ?? 0) })
    }
  }, [])

  useEffect(() => {
    if (!isLoggedIn) router.replace('/login')
  }, [isLoggedIn])

  useEffect(() => {
    if (isLoggedIn) void loadAll()
  }, [isLoggedIn, loadAll])

  useEffect(() => {
    if (!round) return
    const timer = setInterval(() => {
      setRemaining(Math.max(0, Math.round((round.deadlineAt - Date.now()) / 1000)))
    }, 500)
    return () => clearInterval(timer)
  }, [round])

  const startRound = async () => {
    if (!pet) return
    const res = await petApi.startMinigameRound(pet.petId)
    if (res.data?.success && res.data.data) {
      setRound({ roundId: String(res.data.data.roundId), deadlineAt: new Date(res.data.data.deadlineAt).getTime(), submitted: 0 })
      setMgResult('')
      setRemaining(30)
    }
  }

  const catchSlot = async (slot: 'LEFT' | 'CENTER' | 'RIGHT') => {
    if (!round) return
    const windowIndex = round.submitted + 1
    if (windowIndex > CATCH_WINDOWS) return
    const res = await petApi.submitMinigameOps(round.roundId, [{ seq: windowIndex, windowIndex, slot }])
    if (res.data?.success) setRound({ ...round, submitted: windowIndex })
  }

  const settleRound = async () => {
    if (!round) return
    const res = await petApi.settleMinigame(round.roundId)
    if (res.data?.success && res.data.data && res.data.data.status === 'SETTLED') {
      const r = res.data.data
      setMgResult(
        r.validCompletion
          ? `完成！经验+${r.reward.exp} 亲密度+${r.reward.intimacy} 心情+${r.reward.happiness}`
          : '本局未达标（需接住 3 个），无收益',
      )
      setRound(null)
    }
  }

  useEffect(() => {
    if (round && remaining === 0) void settleRound()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [remaining, round])

  const confirmDigest = async () => {
    if (!digest) return
    await petApi.confirmOfflineDigest(digest.throughAt)
    void loadAll()
  }

  const custodyAction = async (start: boolean) => {
    if (start) await petApi.startCustody()
    else await petApi.endCustody()
    void loadAll()
  }

  const coopAction = async (id: string, action: 'accept' | 'claim' | 'leave') => {
    if (action === 'accept') await petApi.acceptCooperation(id)
    else if (action === 'claim') await petApi.claimCooperationReward(id)
    else await petApi.leaveCooperation(id)
    void loadAll()
  }

  const btn = (label: string, onPress: () => void, primary = false, disabled = false) => (
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
      <Text style={{ fontSize: FontSize.xs, color: primary ? '#001529' : WishColors.textSecondary }}>{label}</Text>
    </TouchableOpacity>
  )

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
          {!round && !mgResult && btn('开始一局', () => void startRound(), true, !pet)}
          {round && (
            <View style={{ gap: Spacing.xs }}>
              <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text }}>
                {remaining}s · 接住 {round.submitted}/{CATCH_WINDOWS}
              </Text>
              <View style={{ flexDirection: 'row', gap: Spacing.xs, flexWrap: 'wrap' }}>
                {btn('←左', () => void catchSlot('LEFT'), false, remaining === 0)}
                {btn('●中', () => void catchSlot('CENTER'), false, remaining === 0)}
                {btn('右→', () => void catchSlot('RIGHT'), false, remaining === 0)}
                {btn('提前结束', () => void settleRound())}
              </View>
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
              {btn('提前结束（名额不恢复）', () => void custodyAction(false))}
            </View>
          ) : custody?.weekUsed ? (
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>本周已使用，下周一再试</Text>
          ) : (
            btn('开始托管（24 小时）', () => void custodyAction(true), true)
          )}
        </Card>

        {digest && (
          <Card title={`📜 离线回顾（约 ${digest.offlineHours} 小时）`}>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>
              完成 {digest.finishedTasks} · 待领 {digest.claimableTasks} · 来访 {digest.visits} · 里程碑 {digest.milestones}
            </Text>
            {btn('我知道了', () => void confirmDigest())}
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
                  {status === 'INVITED' && btn('接受', () => void coopAction(id, 'accept'), true)}
                  {status === 'COMPLETED' && btn('领取', () => void coopAction(id, 'claim'), true)}
                  {(status === 'INVITED' || status === 'ACTIVE') && btn('退出', () => void coopAction(id, 'leave'))}
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

        <ActivityIndicator style={{ marginTop: Spacing.md }} color={WishColors.accentCyan} />
      </ScrollView>
    </View>
  )
}
