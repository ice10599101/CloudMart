// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { Modal, Text, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import type { PetBottleResult } from '@/api/pet'
import { ChipButton, RARITY_LABEL } from '../shared'

/** 稀有/宠物/彩蛋瓶结果弹层（普通瓶走邂逅瓶列表页，不弹此层） */
export function BottleResultModal(
  { result, onClose }: { result: PetBottleResult | null; onClose: () => void },
) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  return (
    <Modal visible={result != null} transparent animationType="fade" onRequestClose={onClose}>
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(0,0,0,0.5)', padding: Spacing.lg }}>
        <View style={{ width: '100%', backgroundColor: colors.bgBase, borderRadius: BorderRadius.xl, padding: Spacing.lg, gap: Spacing.sm }}>
          <Text style={{ fontSize: 44, textAlign: 'center' }}>🍾</Text>
          <Text style={{ color: colors.text, fontSize: FontSize.lg, fontWeight: '700', textAlign: 'center' }}>
            捞到了一只{RARITY_LABEL[result?.rarity ?? 'NORMAL']}！
          </Text>
          {result?.specialContent
            ? <Text style={{ color: colors.textSecondary, fontSize: FontSize.sm, textAlign: 'center' }}>{result.specialContent}</Text>
            : null}
          <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, textAlign: 'center' }}>
            宠物经验 +{result?.exp ?? 0} · 成功率 {Math.round((result?.successRate ?? 0) * 100)}%
          </Text>
          <ChipButton label="知道啦" primary onPress={onClose} />
        </View>
      </View>
    </Modal>
  )
}
