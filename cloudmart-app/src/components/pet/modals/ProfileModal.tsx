// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useEffect, useState } from 'react'
import { Alert, Modal, Switch, Text, TextInput, TouchableOpacity, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { petApi, type PetInfo } from '@/api/pet'
import { ACCESSORIES, ChipButton, parseResult, PET_COLORS } from '../shared'

/**
 * 档案弹窗：改名 30 天冷却 + 外观 + 主人称呼 + 主页公开。
 * 打开时按当前主宠同步草稿；保存/隐私变更后经回调让宿主刷新权威主宠状态。
 */
export function ProfileModal(
  { pet, open, onClose, onChanged, onPrivacyChanged }: {
    pet: PetInfo
    open: boolean
    onClose: () => void
    onChanged: () => void
    onPrivacyChanged: (isPublic: boolean) => void
  },
) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [profileName, setProfileName] = useState('')
  const [profileColor, setProfileColor] = useState(PET_COLORS[0])
  const [profileAccessory, setProfileAccessory] = useState('none')
  const [profileOwnerTitle, setProfileOwnerTitle] = useState('')
  const [privacyBusy, setPrivacyBusy] = useState(false)

  useEffect(() => {
    if (!open) return
    // pet 是弹窗本地可编辑草稿的外部数据源：打开/pet 变更时同步（异步边界避免级联渲染）
    async function syncDraft() {
      setProfileName(pet.name)
      setProfileOwnerTitle(pet.ownerTitle ?? '主人')
      const appearance = parseResult<{ color?: string; accessory?: string }>(pet.appearance)
      setProfileColor(appearance?.color ?? PET_COLORS[0])
      setProfileAccessory(appearance?.accessory ?? 'none')
    }
    void syncDraft()
  }, [open, pet])

  const saveProfile = async () => {
    const wantRename = profileName.trim() && profileName.trim() !== pet.name
    const wantTitle = profileOwnerTitle.trim() && profileOwnerTitle.trim() !== (pet.ownerTitle ?? '主人')
    try {
      await petApi.updateAppearance({ color: profileColor, accessory: profileAccessory })
      if (wantRename) {
        await petApi.renamePet({ name: profileName.trim() })
      }
      if (wantTitle) {
        await petApi.setOwnerTitle(profileOwnerTitle.trim())
      }
      onClose()
      // 宿主刷新会重新拉取权威主宠并同步 Cocos 舞台（等价原实现最终的 getMyPet + syncStage）
      onChanged()
    } catch (e) {
      // 改名冷却（409 PET_RENAME_COOLDOWN）时外观已保存，剩余弹窗保留给用户
      Alert.alert('宠物档案', (e as Error)?.message ?? '保存失败，请稍后重试')
    }
  }

  const togglePrivacy = async (next: boolean) => {
    setPrivacyBusy(true)
    try {
      await petApi.updatePrivacy({ isPublic: next })
      onPrivacyChanged(next)
    } catch {
      // 拦截器已提示
    } finally {
      setPrivacyBusy(false)
    }
  }

  return (
    <Modal visible={open} transparent animationType="slide" onRequestClose={onClose}>
      <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.45)' }}>
        <View style={{ backgroundColor: colors.bgBase, borderTopLeftRadius: BorderRadius.xl, borderTopRightRadius: BorderRadius.xl, padding: Spacing.lg, gap: Spacing.md }}>
          <Text style={{ color: colors.text, fontSize: FontSize.lg, fontWeight: '700' }}>宠物档案</Text>
          <TextInput
            value={profileName}
            onChangeText={setProfileName}
            maxLength={12}
            placeholder="宠物名（30 天可改一次）"
            placeholderTextColor={colors.textTertiary}
            style={{
              paddingHorizontal: Spacing.md, paddingVertical: 10, borderRadius: BorderRadius.md,
              borderWidth: 1, borderColor: colors.border, color: colors.text, backgroundColor: colors.bgContainer,
            }}
          />
          <TextInput
            value={profileOwnerTitle}
            onChangeText={setProfileOwnerTitle}
            maxLength={12}
            placeholder="宠物怎么叫你（默认「主人」）"
            placeholderTextColor={colors.textTertiary}
            style={{
              paddingHorizontal: Spacing.md, paddingVertical: 10, borderRadius: BorderRadius.md,
              borderWidth: 1, borderColor: colors.border, color: colors.text, backgroundColor: colors.bgContainer,
            }}
          />
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
            {ACCESSORIES.map((item) => (
              <TouchableOpacity
                key={item.key}
                onPress={() => setProfileAccessory(item.key)}
                style={{
                  paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999,
                  borderWidth: 1, borderColor: profileAccessory === item.key ? colors.primary : colors.border,
                  backgroundColor: profileAccessory === item.key ? colors.primary : 'transparent',
                }}
              >
                <Text style={{ color: profileAccessory === item.key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{item.label}</Text>
              </TouchableOpacity>
            ))}
          </View>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
            {PET_COLORS.map((color) => (
              <TouchableOpacity
                key={color}
                onPress={() => setProfileColor(color)}
                style={{
                  paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999,
                  borderWidth: 2, borderColor: profileColor === color ? colors.primary : colors.border,
                }}
              >
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{color}</Text>
              </TouchableOpacity>
            ))}
          </View>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm }}>在个人主页展示宠物</Text>
            <Switch value={pet.isPublic} disabled={privacyBusy} onValueChange={togglePrivacy} />
          </View>
          <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
            <ChipButton label="取消" onPress={onClose} />
            <ChipButton label="保存" primary onPress={saveProfile} />
          </View>
        </View>
      </View>
    </Modal>
  )
}
