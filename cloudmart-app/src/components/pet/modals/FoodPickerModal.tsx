// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useEffect, useState } from 'react'
import { ActivityIndicator, Modal, ScrollView, Text, TouchableOpacity, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { petApi, type PetInfo, type PetInventoryItem } from '@/api/pet'
import { ChipButton } from '../shared'

/**
 * 喂食食物选择器（长按喂食弹出；背包 FOOD 项）。
 * 打开时自拉背包食物；为空 400ms 后回调宿主舞台提示并关闭；喂食成功经 onFed 回传最新主宠。
 */
export function FoodPickerModal(
  { visible, onClose, onFed, onEmpty }: {
    visible: boolean
    onClose: () => void
    onFed: (pet: PetInfo) => void
    onEmpty: () => void
  },
) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [foodOptions, setFoodOptions] = useState<PetInventoryItem[] | null>(null)
  const [foodBusy, setFoodBusy] = useState<string | null>(null)

  useEffect(() => {
    if (!visible) return
    let cancelled = false
    void (async () => {
      setFoodOptions(null)
      try {
        const { data: res } = await petApi.listInventory()
        const foods = res.data.filter((it) => it.itemType === 'FOOD' && (it.quantity ?? 0) > 0)
        if (cancelled) return
        setFoodOptions(foods)
        if (foods.length === 0) {
          setTimeout(() => {
            if (cancelled) return
            onEmpty()
            onClose()
          }, 400)
        }
      } catch { /* 拦截器已提示 */ }
    })()
    return () => {
      cancelled = true
    }
  }, [visible, onClose, onEmpty])

  const feedFood = async (code: string) => {
    setFoodBusy(code)
    try {
      const { data: res } = await petApi.feedItem(code)
      onFed(res.data)
      onClose()
    } catch { /* 拦截器已提示 */ } finally {
      setFoodBusy(null)
    }
  }

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.45)' }}>
        <View style={{ backgroundColor: colors.bgBase, borderTopLeftRadius: BorderRadius.xl, borderTopRightRadius: BorderRadius.xl, padding: Spacing.lg, gap: Spacing.sm, maxHeight: '60%' }}>
          <Text style={{ color: colors.text, fontSize: FontSize.lg, fontWeight: '700' }}>🍖 选一份食物</Text>
          <ScrollView style={{ maxHeight: 360 }} showsVerticalScrollIndicator={false}>
            {foodOptions === null ? (
              <ActivityIndicator color={colors.primary} />
            ) : (
              foodOptions.map((item) => (
                <TouchableOpacity
                  key={item.code}
                  disabled={foodBusy === item.code}
                  onPress={() => void feedFood(item.code)}
                  style={{
                    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.sm,
                    padding: Spacing.sm, marginBottom: Spacing.xs, borderRadius: BorderRadius.md,
                    borderWidth: 1, borderColor: 'rgba(200, 155, 90, 0.35)', backgroundColor: '#FFFDF8',
                    opacity: foodBusy === item.code ? 0.5 : 1,
                  }}
                >
                  <Text style={{ color: colors.text, fontSize: FontSize.sm, flex: 1 }}>
                    {item.icon} {item.name} <Text style={{ color: colors.textTertiary, fontSize: 10 }}>×{item.quantity ?? 0}</Text>
                  </Text>
                  <Text style={{ color: PetCreamTheme.primary, fontSize: FontSize.sm, fontWeight: '600' }}>
                    {foodBusy === item.code ? '喂食中…' : '喂食'}
                  </Text>
                </TouchableOpacity>
              ))
            )}
          </ScrollView>
          <ChipButton label="关闭" onPress={onClose} />
        </View>
      </View>
    </Modal>
  )
}
