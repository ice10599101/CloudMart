import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, ScrollView, Text, View } from '@tarojs/components'
import { petApi, type PetBattleItem, type PetOpponent } from '@/api/pet'
import { friendlyError, parseResult, SPECIES_EMOJI, type BattleRound } from '../constants'
import styles from '../index.module.scss'

/**
 * 对战面板：待应战列表 / 选对手（野怪 PVE · 玩家 PVP）/ 最近战绩（R30 自 index.tsx 拆出，行为不变）。
 * 对战结果：回合流水由服务端生成，此处只做文字回放（Cocos 舞台另有动画演出）。
 */
export function BattlePanel({ onRefresh }: { onRefresh: () => void }) {
  const [opponents, setOpponents] = useState<PetOpponent[]>([])
  const [history, setHistory] = useState<PetBattleItem[]>([])
  // 对战回合流水回放浮层（服务端计算，客户端只播放）
  const [roundsView, setRoundsView] = useState<{ rounds: BattleRound[]; won: boolean } | null>(null)

  const toast = (msg: string) => Taro.showToast({ title: msg, icon: 'none' })

  const load = useCallback(async () => {
    try {
      const [opponentRes, historyRes] = await Promise.all([
        petApi.listOpponents(),
        petApi.listBattleHistory({ page: 1, pageSize: 10 }),
      ])
      setOpponents(opponentRes.data.data || [])
      setHistory(historyRes.data.data || [])
    } catch (error) {
      toast(friendlyError(error))
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  /** 对战结果：回合流水由服务端生成，此处只做文字回放（Cocos 舞台另有动画演出） */
  const presentBattle = (battle: PetBattleItem) => {
    if (battle.status !== 'FINISHED') {
      toast('已发起挑战，等对方应战')
      return
    }
    const rounds = parseResult<BattleRound[]>(battle.rounds)
    const myPetId = battle.role === 'ATTACKER' ? battle.attackerPetId : battle.defenderPetId
    const won = String(battle.winnerPetId) === String(myPetId)
    if (!rounds || rounds.length === 0) {
      toast(won ? '对战胜利！' : '对战惜败，下次再战')
      return
    }
    // 回合流水回放浮层（服务端计算，客户端只展示）
    setRoundsView({ rounds, won })
  }

  return (
    <View className={styles.battlePanel}>
      {history.filter((battle) => battle.status === 'PENDING' && battle.role === 'DEFENDER').map((battle) => (
        <View key={String(battle.battleId)} className={styles.pendingRow}>
          <Text className={styles.tip}>⚔️ 收到 {battle.attackerPetName ?? `宠物 #${battle.attackerPetId}`} 的挑战</Text>
          <View className={styles.pendingBtns}>
            <Button className={styles.miniBtn} onClick={async () => {
              try {
                const { data: res } = await petApi.acceptBattle(battle.battleId)
                presentBattle(res.data); void load(); onRefresh()
              } catch (error) { toast(friendlyError(error)) }
            }}>应战</Button>
            <Button className={styles.miniBtnGhost} onClick={async () => {
              try {
                await petApi.declineBattle(battle.battleId)
                toast('已婉拒')
                void load()
              } catch (error) { toast(friendlyError(error)) }
            }}>婉拒</Button>
          </View>
        </View>
      ))}
      <Text className={styles.sectionTitle}>选择对手</Text>
      <View className={styles.opponentGrid}>
        {opponents.map((opponent) => (
          <View key={`${String(opponent.petId)}-${opponent.name}`} className={styles.opponentCard}
            onClick={async () => {
              try {
                const { data: res } = await petApi.challenge(opponent.isWild ? { mode: 'PVE' } : { mode: 'PVP', defenderPetId: opponent.petId })
                presentBattle(res.data)
                void load()
                onRefresh()
              } catch (error) { toast(friendlyError(error)) }
            }}
          >
            <Text className={styles.opponentEmoji}>{SPECIES_EMOJI[opponent.species] || '🐾'}</Text>
            <Text className={styles.opponentName}>{opponent.name}</Text>
            <Text className={styles.opponentMeta}>Lv.{opponent.level} · {opponent.isWild ? '野生' : opponent.ownerNickname}</Text>
          </View>
        ))}
      </View>
      <Text className={styles.sectionTitle}>最近对战</Text>
      {history.filter((battle) => battle.status !== 'PENDING').slice(0, 5).map((battle) => {
        const opponent = battle.role === 'ATTACKER' ? battle.defenderPetName : battle.attackerPetName
        const won = battle.winnerPetId === (battle.role === 'ATTACKER' ? battle.attackerPetId : battle.defenderPetId)
        return (
          <View key={String(battle.battleId)} className={styles.historyRow} onClick={async () => {
            try {
              const { data: res } = await petApi.getBattleDetail(battle.battleId)
              presentBattle(res.data)
            } catch (error) { toast(friendlyError(error)) }
          }}>
            <Text className={styles.historyText}>
              {battle.status === 'FINISHED' ? (won ? '🏆 胜利' : '💧 战败') : battle.status === 'DECLINED' ? '🚫 被婉拒' : '⌛ 未应战'}
              {' · '}{opponent ?? '对手'}
            </Text>
            <Text className={styles.historyMeta}>+{battle.expReward} 经验</Text>
          </View>
        )
      })}

      {/* 对战回合流水回放（服务端计算，客户端只展示；Cocos 舞台另有动画演出） */}
      {roundsView && (
        <View className={styles.modalMask} onClick={() => setRoundsView(null)}>
          <View className={styles.modalCard} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>
              {roundsView.won ? '🏆 对战大获全胜' : '💧 对战惜败'}
            </Text>
            <ScrollView className={styles.roundsScroll}>
              {roundsView.rounds.map((round, index) => (
                <Text key={`${round.round}-${index}`} className={styles.roundLine}>
                  第 {round.round} 回合：{round.actorName}
                  {round.action === 'skill' ? ' 使用技能' : ''}
                  {round.dodged ? ' 被闪避' : ` 造成 ${round.damage} 点伤害`}
                  {round.critical ? '（暴击）' : ''} → {round.targetName} 剩余 {round.targetRemainingHp}
                </Text>
              ))}
            </ScrollView>
            <Button className={styles.primaryBtn} onClick={() => setRoundsView(null)}>关闭</Button>
          </View>
        </View>
      )}
    </View>
  )
}
