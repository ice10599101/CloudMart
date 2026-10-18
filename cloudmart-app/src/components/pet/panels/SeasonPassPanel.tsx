// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useRef, useState } from 'react'
import { ActivityIndicator, Alert, Text, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize } from '@/constants/theme'
import { PetCreamTheme, PetCreamSemantic } from '@/constants/pet-cream'
import { errorMessage } from '@/utils/request'
import { petApi, type PetSeasonPass, type PetSeason } from '@/api/pet'
import { ChipButton, newR19RequestKey } from '../shared'

export function SeasonPassPanel({ claimEnabled }: { claimEnabled: boolean }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [pass, setPass] = useState<PetSeasonPass | null>(null)
  const [loadState, setLoadState] = useState<'LOADING' | 'READY' | 'ERROR'>('LOADING')
  const [claimingTier, setClaimingTier] = useState<number | null>(null)
  // T19：赛季选择——历史赛季进度查看与宽限期内补领
  const [seasons, setSeasons] = useState<PetSeason[] | null>(null)
  const [selectedSeasonId, setSelectedSeasonId] = useState<number | null>(null)
  const [showSeasonPicker, setShowSeasonPicker] = useState(false)
  // 每档独立 requestKey：网络结果未知时保留原键，恢复后同键重放（R05 可恢复交付）
  const claimKeyRef = useRef<Record<number, string>>({})

  const load = useCallback(async (seasonId?: number | null) => {
    setLoadState('LOADING')
    try {
      const { data: res } = await petApi.getSeasonPass(seasonId ?? undefined)
      setPass(res.data)
      setLoadState('READY')
    } catch (error) {
      setLoadState('ERROR')
      Alert.alert('通行证加载失败', errorMessage(error, '请稍后再试'))
    }
  }, [])

  const loadSeasons = useCallback(async () => {
    try {
      const { data: res } = await petApi.getSeasonPassSeasons()
      setSeasons(res.data ?? [])
    } catch {
      // 拦截器已提示；保留空列表展示
      setSeasons([])
    }
  }, [])

  // 挂载加载：setState 发生在 await 之后（重试按钮直接调 load，二者共用同一请求逻辑）
  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const { data: res } = await petApi.getSeasonPass()
        if (!cancelled) {
          setPass(res.data)
          setLoadState('READY')
        }
      } catch (error) {
        if (!cancelled) {
          setLoadState('ERROR')
          Alert.alert('通行证加载失败', errorMessage(error, '请稍后再试'))
        }
      }
    })()
    return () => { cancelled = true }
  }, [])

  const claim = async (tier: number) => {
    if (claimingTier !== null) return
    setClaimingTier(tier)
    try {
      if (!claimKeyRef.current[tier]) claimKeyRef.current[tier] = newR19RequestKey()
      const { data: res } = await petApi.claimSeasonPassTier(tier, claimKeyRef.current[tier], selectedSeasonId ?? undefined)
      delete claimKeyRef.current[tier]
      setPass(res.data)
      Alert.alert('领取成功', `第 ${tier} 档奖励已领取`)
    } catch (error) {
      // 网络结果未知保留原键（可原样重试）；业务终态错误（未达标/已领取）清除换新
      if ((error as { code?: string }).code !== 'NETWORK_ERROR') delete claimKeyRef.current[tier]
      Alert.alert('领取失败', errorMessage(error, '奖励可能还没达标或已领取'))
    } finally {
      setClaimingTier(null)
    }
  }

  const cardStyle = {
    padding: Spacing.sm, borderRadius: 16, borderWidth: 1, borderColor: 'rgba(200, 155, 90, 0.35)',
    backgroundColor: '#FFFDF8', gap: 4, marginBottom: Spacing.sm,
  } as const

  // T19：赛季选择器（只展示本人参与过的赛季，选择后加载该季通行证；服务端判定补领截止）
  const renderSeasonPicker = () => (
    <View style={{ gap: 4, marginTop: 4 }}>
      {(seasons ?? []).map((season) => (
        <ChipButton
          key={season.id}
          label={`${season.name}${season.status === 'ACTIVE' ? '（进行中）' : ''}`}
          onPress={() => {
            setSelectedSeasonId(season.id)
            setShowSeasonPicker(false)
            void load(season.id)
          }}
        />
      ))}
      {seasons && seasons.length === 0 && (
        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>暂无历史赛季</Text>
      )}
    </View>
  )

  if (loadState === 'LOADING') return <ActivityIndicator color={colors.primary} />
  if (loadState === 'ERROR') {
    return (
      <View style={cardStyle}>
        <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>通行证暂时不可用</Text>
        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>网络或服务异常，重试后仍失败请稍后再来</Text>
        <ChipButton label="重新加载" primary onPress={() => void load()} />
      </View>
    )
  }
  if (!pass || pass.seasonId == null) {
    return (
      <View style={cardStyle}>
        <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>当前没有进行中的赛季</Text>
        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>新赛季开赛后，完成每日任务即可累积通行证经验</Text>
        <ChipButton label="查看历史赛季" onPress={() => { void loadSeasons(); setShowSeasonPicker(true) }} />
        {showSeasonPicker && renderSeasonPicker()}
      </View>
    )
  }

  return (
    <View>
      <View style={cardStyle}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <Text style={{ color: colors.text, fontSize: FontSize.md, fontWeight: '700', flex: 1 }}>
            {`🎫 ${pass.seasonName ?? '当前赛季'}`}
          </Text>
          <ChipButton
            label={selectedSeasonId != null ? '返回当前' : '历史赛季'}
            onPress={() => {
              if (selectedSeasonId != null) {
                setSelectedSeasonId(null)
                void load(null)
                return
              }
              void loadSeasons()
              setShowSeasonPicker((v) => !v)
            }}
          />
        </View>
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{`通行证经验 ${pass.passExp} · 完成每日任务与全清宝箱累积`}</Text>
        {!claimEnabled && (
          <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>通行证奖励领取暂未开放，当前仅可查看进度</Text>
        )}
        {showSeasonPicker && selectedSeasonId == null && renderSeasonPicker()}
      </View>
      {pass.tiers.map((tier) => {
        const pending = tier.claimStatus === 'CLAIM_PENDING'
        return (
          <View key={tier.tier} style={cardStyle}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                  {`第 ${tier.tier} 档`}
                </Text>
                <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                  {`${tier.requiredExp} 经验 · ${tier.reached ? '已达标' : `还差 ${Math.max(0, tier.requiredExp - pass.passExp)}`}`}
                </Text>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{tier.rewardDesc}</Text>
              </View>
              {tier.claimed ? (
                <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, flexShrink: 0 }}>已领取</Text>
              ) : pending ? (
                // R05：CLAIM_PENDING 是交付中，绝不能展示为"已领取"
                <Text style={{ color: PetCreamSemantic.warning, fontSize: FontSize.xs, flexShrink: 0 }}>发放中…</Text>
              ) : claimEnabled && tier.reached ? (
                <View style={{ flexShrink: 0 }}>
                  <ChipButton
                    label={claimingTier === tier.tier ? '领取中…' : '领取'}
                    primary
                    disabled={claimingTier !== null}
                    onPress={() => void claim(tier.tier)}
                  />
                </View>
              ) : (
                <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, flexShrink: 0 }}>未达标</Text>
              )}
            </View>
          </View>
        )
      })}
    </View>
  )
}
