// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { Modal, Text, TouchableOpacity, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { type BattleRound } from '../shared'

/** 对战回合兜底浮层（未部署 Cocos 时也可完整看战斗过程）；点击任意位置跳过 */
export function BattleOverlayModal(
  { overlay, activeRound, onDismiss }: {
    overlay: { rounds: BattleRound[]; won: boolean } | null
    activeRound: BattleRound | null
    onDismiss: () => void
  },
) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  if (!overlay) return null
  return (
    <Modal visible transparent animationType="fade">
      <TouchableOpacity
        activeOpacity={1}
        onPress={onDismiss}
        style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(0,0,0,0.55)', padding: Spacing.lg }}
      >
        <View style={{ width: '100%', backgroundColor: colors.bgBase, borderRadius: BorderRadius.xl, padding: Spacing.lg, gap: Spacing.sm }}>
          <Text style={{ color: colors.text, fontSize: FontSize.lg, fontWeight: '700', textAlign: 'center' }}>
            ⚔️ 对战回放
          </Text>
          {activeRound
            ? (
              <>
                <Text style={{ color: colors.text, fontSize: FontSize.sm, textAlign: 'center' }}>第 {activeRound.round} 回合</Text>
                <Text style={{ color: colors.text, fontSize: FontSize.md, textAlign: 'center' }}>
                  {activeRound.actorName} {activeRound.action}
                </Text>
                <Text style={{ color: colors.primary, fontSize: FontSize.sm, textAlign: 'center' }}>
                  {activeRound.dodged
                    ? '被灵活地闪开了'
                    : `造成 ${activeRound.damage} 点伤害${activeRound.critical ? '（暴击！）' : ''}`}
                </Text>
                <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, textAlign: 'center' }}>
                  {activeRound.targetName} 剩余生命 {Math.max(0, activeRound.targetRemainingHp)}
                </Text>
              </>
            )
            : (
              <Text style={{ color: colors.text, fontSize: FontSize.lg, fontWeight: '700', textAlign: 'center' }}>
                {overlay.won ? '🏆 我们赢啦！' : '💧 这次惜败，再来一局！'}
              </Text>
            )}
          <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, textAlign: 'center' }}>点击任意位置跳过</Text>
        </View>
      </TouchableOpacity>
    </Modal>
  )
}
