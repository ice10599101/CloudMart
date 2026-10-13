// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useRef, useState } from 'react'
import { ActivityIndicator, Alert, ScrollView, Text, TouchableOpacity, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize } from '@/constants/theme'
import { PetCreamTheme, PetCreamSemantic } from '@/constants/pet-cream'
import { errorMessage } from '@/utils/request'
import { petApi, type PetPveBoss, type PetPveRun } from '@/api/pet'
import { ChipButton, newR19RequestKey } from '../shared'

const PVE_STATUS_LABEL: Record<string, string> = {
  OPEN: '招募中',
  FIGHTING: '战斗中',
  WON: '讨伐成功',
  FAILED: '挑战失败',
  EXPIRED: '已过期',
  CANCELLED: '已取消',
}

const PVE_BLOCKED_REASON_LABEL: Record<string, string> = {
  NOT_YOUR_TURN: '还没轮到你出手，请等待队友行动',
  NOT_FIGHTING: '副本尚未进入战斗',
  RUN_OVER: '副本已经结束',
}

/** R19 双人协作 PVE：招募大厅（加入他人）+ 发起副本 + 详情轮询（5s）+ 轮流攻击 */
export function PvePanel({ enabled, currentUserId }: { enabled: boolean; currentUserId: number | null }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [tab, setTab] = useState<'lobby' | 'mine'>('lobby')
  const [bosses, setBosses] = useState<PetPveBoss[]>([])
  const [openRuns, setOpenRuns] = useState<PetPveRun[]>([])
  const [myRuns, setMyRuns] = useState<PetPveRun[]>([])
  const [detail, setDetail] = useState<PetPveRun | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [startingBoss, setStartingBoss] = useState<string | null>(null)
  // 攻击幂等键：网络结果未知时保留，原样重试由服务端返回原回合（R03 同 requestKey 重放）
  const attackKeyRef = useRef<string | null>(null)

  const loadLobby = useCallback(async () => {
    const [bossRes, openRes] = await Promise.all([
      petApi.listPveBosses(),
      petApi.listOpenPveRuns(1, 20),
    ])
    setBosses(bossRes.data.data ?? [])
    setOpenRuns(openRes.data.data?.records ?? [])
  }, [])

  const loadMine = useCallback(async () => {
    const { data: res } = await petApi.listMyPveRuns(1, 20)
    setMyRuns(res.data?.records ?? [])
  }, [])

  // 首次进入与切页签加载：setState 全部发生在 await 之后（cancelled 防竞态覆盖）
  useEffect(() => {
    if (!enabled) return
    let cancelled = false
    void (async () => {
      try {
        if (tab === 'lobby') {
          const [bossRes, openRes] = await Promise.all([
            petApi.listPveBosses(),
            petApi.listOpenPveRuns(1, 20),
          ])
          if (cancelled) return
          setBosses(bossRes.data.data ?? [])
          setOpenRuns(openRes.data.data?.records ?? [])
        } else {
          const { data: res } = await petApi.listMyPveRuns(1, 20)
          if (cancelled) return
          setMyRuns(res.data?.records ?? [])
        }
      } catch (error) {
        if (!cancelled) Alert.alert('加载失败', errorMessage(error, '请检查网络后重试'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [enabled, tab])

  const switchTab = (nextTab: 'lobby' | 'mine') => {
    if (nextTab === tab) return
    setTab(nextTab)
    setLoading(true)
  }

  /** 动作后静默刷新当前列表（后台刷新失败不弹框，下次切页签重载） */
  const refreshActiveList = useCallback(() => {
    const task = tab === 'lobby' ? loadLobby() : loadMine()
    return task.catch(() => undefined)
  }, [tab, loadLobby, loadMine])

  const openDetail = useCallback(async (runId: number | string) => {
    try {
      const { data: res } = await petApi.getPveRun(runId)
      setDetail(res.data)
    } catch (error) {
      Alert.alert('详情加载失败', errorMessage(error, '请稍后再试'))
    }
  }, [])

  // 详情轮询：OPEN/FIGHTING 每 5s 拉取，终态/卸载/切走立即 clearInterval
  // （参照 companionHeartbeat：tick 瞬时失败静默，下一轮重试；主动操作失败单独 Alert）
  useEffect(() => {
    if (!detail || (detail.status !== 'OPEN' && detail.status !== 'FIGHTING')) return
    const timer = setInterval(() => {
      void (async () => {
        try {
          const { data: res } = await petApi.getPveRun(detail.runId)
          setDetail(res.data)
        } catch {
          // 轮询失败静默，下一轮重试
        }
      })()
    }, 5000)
    return () => clearInterval(timer)
  }, [detail])

  const start = async (bossCode: string) => {
    if (startingBoss) return
    setStartingBoss(bossCode)
    try {
      const { data: res } = await petApi.startPveRun(bossCode)
      setDetail(res.data)
      Alert.alert('副本已发起', '等待队友加入，之后双方轮流攻击 Boss')
      void refreshActiveList()
    } catch (error) {
      Alert.alert('发起失败', errorMessage(error, '可能已有进行中的副本，稍后再试'))
    } finally {
      setStartingBoss(null)
    }
  }

  const join = async (run: PetPveRun) => {
    if (busy) return
    setBusy(true)
    try {
      const { data: res } = await petApi.joinPveRun(run.runId)
      setDetail(res.data)
      Alert.alert('已加入战斗', '双方轮流行动，击倒 Boss 后奖励自动发放')
      void refreshActiveList()
    } catch (error) {
      Alert.alert('加入失败', errorMessage(error, '可能刚被别人加入，换一个试试'))
    } finally {
      setBusy(false)
    }
  }

  const attack = async () => {
    if (!detail || busy) return
    setBusy(true)
    try {
      const requestKey = attackKeyRef.current ?? newR19RequestKey()
      attackKeyRef.current = requestKey
      const { data: res } = await petApi.attackPveRun(detail.runId, requestKey)
      attackKeyRef.current = null
      setDetail(res.data)
      if (res.data.status === 'WON') Alert.alert('讨伐成功', 'Boss 已被击倒，奖励已发放')
      else if (res.data.status === 'FAILED') Alert.alert('副本失败', '这次没能坚持住，下次再战')
      void refreshActiveList()
    } catch (error) {
      // 网络结果未知：保留 requestKey 供原样重试（服务端同键返回原回合，不重复结算）；业务终态错误换新键
      if ((error as { code?: string }).code !== 'NETWORK_ERROR') attackKeyRef.current = null
      Alert.alert('攻击失败', errorMessage(error, '可能还没轮到你行动'))
    } finally {
      setBusy(false)
    }
  }

  const cancel = async () => {
    if (!detail || busy) return
    setBusy(true)
    try {
      const { data: res } = await petApi.cancelPveRun(detail.runId)
      setDetail(res.data)
      void refreshActiveList()
    } catch (error) {
      Alert.alert('取消失败', errorMessage(error, '请稍后再试'))
    } finally {
      setBusy(false)
    }
  }

  const cardStyle = {
    padding: Spacing.sm, borderRadius: 16, borderWidth: 1, borderColor: 'rgba(200, 155, 90, 0.35)',
    backgroundColor: '#FFFDF8', gap: 4, marginBottom: Spacing.sm,
  } as const

  if (!enabled) {
    return (
      <View style={cardStyle}>
        <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>协作副本暂未开放</Text>
        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>双人协战 Boss 玩法即将上线，开放后这里会亮起来</Text>
      </View>
    )
  }

  const renderRunCard = (run: PetPveRun, showJoin: boolean) => (
    <View key={String(run.runId)} style={cardStyle}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
        <TouchableOpacity style={{ flex: 1, minWidth: 0 }} onPress={() => void openDetail(run.runId)}>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.sm }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{`👹 ${run.bossName}`}</Text>
            <Text style={{ color: colors.primary, fontSize: FontSize.xs, flexShrink: 0 }}>
              {PVE_STATUS_LABEL[run.status] ?? run.status}
            </Text>
          </View>
          <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
            {`Boss HP ${Math.max(0, run.bossHp)}/${run.bossMaxHp}`}
          </Text>
          <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }} numberOfLines={1}>
            {run.partnerPetName
              ? `${run.initiatorPetName ?? '发起人'} × ${run.partnerPetName}`
              : `${run.initiatorPetName ?? '发起人'} · 等待队友`}
          </Text>
        </TouchableOpacity>
        {showJoin && run.status === 'OPEN'
          && (currentUserId == null || String(run.initiatorUserId) !== String(currentUserId)) && (
          <View style={{ flexShrink: 0 }}>
            <ChipButton label={busy ? '处理中…' : '加入'} primary disabled={busy} onPress={() => void join(run)} />
          </View>
        )}
      </View>
    </View>
  )

  const renderDetail = (run: PetPveRun) => {
    const isInitiator = currentUserId != null && String(run.initiatorUserId) === String(currentUserId)
    const isPartner = currentUserId != null && run.partnerUserId != null
      && String(run.partnerUserId) === String(currentUserId)
    const bossPercent = run.bossMaxHp > 0 ? Math.max(0, Math.round((run.bossHp / run.bossMaxHp) * 100)) : 0
    return (
      <View style={{ gap: Spacing.sm }}>
        <ChipButton label="‹ 返回列表" onPress={() => setDetail(null)} />
        <View style={cardStyle}>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.sm }}>
            <Text style={{ color: colors.text, fontSize: FontSize.md, fontWeight: '700' }}>{`👹 ${run.bossName}`}</Text>
            <Text style={{ color: colors.primary, fontSize: FontSize.xs }}>{PVE_STATUS_LABEL[run.status] ?? run.status}</Text>
          </View>
          <View style={{ height: 8, borderRadius: 999, backgroundColor: '#F2E3CE', overflow: 'hidden' }}>
            <View style={{ height: 8, borderRadius: 999, backgroundColor: PetCreamSemantic.danger, width: `${bossPercent}%` }} />
          </View>
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
            {`Boss HP ${Math.max(0, run.bossHp)}/${run.bossMaxHp} · 已完成 ${run.turnNo} 回合`}
          </Text>
        </View>

        <View style={cardStyle}>
          <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>参战宠物</Text>
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
            {`🛡️ ${run.initiatorPetName ?? '发起人'} HP ${Math.max(0, run.initiatorPetHp)}${run.initiatorPetMaxHp > 0 ? `/${run.initiatorPetMaxHp}` : ''}（发起方）`}
          </Text>
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
            {run.partnerPetHp >= 0
              ? `🛡️ ${run.partnerPetName ?? '队友'} HP ${Math.max(0, run.partnerPetHp)}${run.partnerPetMaxHp > 0 ? `/${run.partnerPetMaxHp}` : ''}`
              : '🕳️ 等待队友加入'}
          </Text>
          {run.status === 'FIGHTING' && (
            <Text style={{ color: run.canAttack ? PetCreamSemantic.success : colors.textTertiary, fontSize: FontSize.xs }}>
              {run.canAttack
                ? '轮到你出手了'
                : PVE_BLOCKED_REASON_LABEL[run.blockedReason ?? ''] ?? '请等待当前行动方完成操作'}
            </Text>
          )}
          {run.status === 'OPEN' && (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>招募中，队友加入后战斗开始</Text>
          )}
          {(run.status === 'WON' || run.status === 'FAILED') && (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
              {run.rewardStatus === 'SETTLED' || run.rewardGranted ? '奖励已结算发放' : '奖励结算处理中'}
            </Text>
          )}
        </View>

        <View style={cardStyle}>
          <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>战斗回合</Text>
          <ScrollView style={{ maxHeight: 220 }} showsVerticalScrollIndicator={false}>
            {run.recentRounds.length === 0 ? (
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>战斗尚未开始</Text>
            ) : run.recentRounds.map((round, index) => (
              <Text key={`${round.round}-${index}`} style={{ color: colors.textSecondary, fontSize: FontSize.xs, lineHeight: 18 }}>
                {`R${round.round} ${round.actorName} ${round.action}${round.critical ? '（暴击）' : ''}${round.dodged ? '（被闪避）' : ''} ${round.targetName}，造成 ${round.damage} 伤害（余 ${Math.max(0, round.targetRemainingHp)}）`}
              </Text>
            ))}
          </ScrollView>
        </View>

        {run.status === 'FIGHTING' && run.canAttack && (
          <ChipButton label={busy ? '结算中…' : '🗡️ 攻击'} primary disabled={busy} onPress={() => void attack()} />
        )}
        {run.status === 'OPEN' && !isInitiator && !isPartner && (
          <ChipButton label={busy ? '处理中…' : '⚔️ 加入战斗'} primary disabled={busy} onPress={() => void join(run)} />
        )}
        {run.status === 'OPEN' && isInitiator && (
          <ChipButton label={busy ? '处理中…' : '取消副本'} disabled={busy} onPress={() => void cancel()} />
        )}
        <Text style={{ color: colors.textTertiary, fontSize: 10, textAlign: 'center' }}>
          双方轮流行动；Boss 倒下后奖励自动发放，详情每 5 秒自动刷新
        </Text>
      </View>
    )
  }

  if (detail) return renderDetail(detail)

  return (
    <View style={{ gap: Spacing.sm }}>
      <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
        <ChipButton label="招募大厅" primary={tab === 'lobby'} onPress={() => switchTab('lobby')} />
        <ChipButton label="我的副本" primary={tab === 'mine'} onPress={() => switchTab('mine')} />
      </View>
      {loading ? (
        <ActivityIndicator color={colors.primary} />
      ) : tab === 'lobby' ? (
        <>
          <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>发起副本</Text>
          {bosses.map((boss) => (
            <View key={boss.bossCode} style={cardStyle}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
                <View style={{ flex: 1, minWidth: 0 }}>
                  <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{`👹 ${boss.name}`}</Text>
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                    {`HP ${boss.maxHp} · 攻 ${boss.attack} · 防 ${boss.defense}`}
                  </Text>
                </View>
                <View style={{ flexShrink: 0 }}>
                  <ChipButton
                    label={startingBoss === boss.bossCode ? '发起中…' : '发起'}
                    primary
                    disabled={startingBoss !== null}
                    onPress={() => void start(boss.bossCode)}
                  />
                </View>
              </View>
            </View>
          ))}
          <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>等待队友的副本</Text>
          {openRuns.length === 0 ? (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>暂无等待中的副本，发起一个吧</Text>
          ) : openRuns.map((run) => renderRunCard(run, true))}
        </>
      ) : (
        <>
          {myRuns.length === 0 ? (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有参与过副本</Text>
          ) : myRuns.map((run) => renderRunCard(run, false))}
        </>
      )}
    </View>
  )
}

/** R19 赛季通行证：赛季档位进度（服务端下发）+ 逐档领取；claimEnabled=false 时仅展示 */
