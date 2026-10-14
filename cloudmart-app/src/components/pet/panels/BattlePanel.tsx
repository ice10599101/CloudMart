// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useState } from 'react'
import { Text, TouchableOpacity, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { CreamButton, petCreamStyles } from '@/components/pet-cream'
import { petApi, type PetBattleItem, type PetOpponent } from '@/api/pet'
import { SPECIES_EMOJI } from '../shared'

/**
 * 对战面板（旧单宠对战）：应战/婉拒/挑战/历史回放。
 * 战斗演出统一交给宿主 onPresent——Cocos 就绪走游戏演出，未就绪宿主兜底逐回合播放。
 */
export function BattlePanel(
  { onRefresh, onPresent }: { onRefresh: () => void; onPresent: (battle: PetBattleItem) => void },
) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [opponents, setOpponents] = useState<PetOpponent[]>([])
  const [history, setHistory] = useState<PetBattleItem[]>([])

  const load = useCallback(async () => {
    try {
      const [opponentRes, historyRes] = await Promise.all([
        petApi.listOpponents(),
        petApi.listBattleHistory({ page: 1, pageSize: 10 }),
      ])
      setOpponents(opponentRes.data.data || [])
      setHistory(historyRes.data.data || [])
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    async function run() {
      await load()
    }
    void run()
  }, [load])

  const battleRespond = async (battle: PetBattleItem, accept: boolean) => {
    try {
      const { data: res } = accept ? await petApi.acceptBattle(battle.battleId) : await petApi.declineBattle(battle.battleId)
      onPresent(res.data)
      await load()
      onRefresh()
    } catch {
      // 拦截器已提示
    }
  }

  return (
    <View style={{ gap: Spacing.md }}>
      {history.filter((battle) => battle.status === 'PENDING' && battle.role === 'DEFENDER').map((battle) => (
        <View key={String(battle.battleId)} style={[petCreamStyles.questCard, { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }]}>
          <Text style={{ color: colors.text, fontSize: FontSize.sm, flex: 1 }}>
            ⚔️ 收到 {battle.attackerPetName ?? `宠物 #${battle.attackerPetId}`} 的挑战
          </Text>
          <View style={petCreamStyles.rowActions}>
            <CreamButton onPress={() => battleRespond(battle, true)}>应战</CreamButton>
            <CreamButton variant="ghost" onPress={() => battleRespond(battle, false)}>婉拒</CreamButton>
          </View>
        </View>
      ))}
      <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>选择对手</Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
        {opponents.map((opponent) => (
          <TouchableOpacity
            key={`${String(opponent.petId)}-${opponent.name}`}
            onPress={async () => {
              try {
                const { data: res } = await petApi.challenge(opponent.isWild ? { mode: 'PVE' } : { mode: 'PVP', defenderPetId: opponent.petId })
                onPresent(res.data)
                void load()
                onRefresh()
              } catch { /* 拦截器已提示 */ }
            }}
            style={petCreamStyles.tile}
          >
            <Text style={{ fontSize: 30 }}>{SPECIES_EMOJI[opponent.species] || '🐾'}</Text>
            <Text style={{ color: colors.text, fontSize: FontSize.xs }}>{opponent.name}</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>Lv.{opponent.level} · {opponent.isWild ? '野生' : opponent.ownerNickname}</Text>
          </TouchableOpacity>
        ))}
      </View>
      {history.filter((battle) => battle.status !== 'PENDING').slice(0, 5).map((battle) => (
        <TouchableOpacity
          key={String(battle.battleId)}
          onPress={async () => {
            try {
              const { data: res } = await petApi.getBattleDetail(battle.battleId)
              onPresent(res.data)
            } catch { /* 历史详情拉取失败静默 */ }
          }}
          style={[petCreamStyles.questCard, { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 10 }]}
        >
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
            {battle.status === 'FINISHED'
              ? (battle.winnerPetId === (battle.role === 'ATTACKER' ? battle.attackerPetId : battle.defenderPetId) ? '🏆 胜利' : '💧 战败')
              : battle.status === 'DECLINED' ? '🚫 被婉拒' : '⌛ 未应战'}
            {' · '}{battle.role === 'ATTACKER' ? battle.defenderPetName : battle.attackerPetName ?? '对手'}
          </Text>
          <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>+{battle.expReward} 经验</Text>
        </TouchableOpacity>
      ))}
    </View>
  )
}
