import { useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Input, Text, View } from '@tarojs/components'
import { petApi, type PetAnniversary, type PetInfo } from '@/api/pet'
import { ACCESSORIES, PET_COLORS, friendlyError, parseResult } from './constants'
import { CreamButton, CreamToggle } from '@/components/pet-cream'
import styles from './index.module.scss'

/**
 * 宠物档案弹层：改名（30 天冷却）/ 主人称呼 / 配饰 / 外观颜色 / 相遇纪念日 / 主页公开。
 * R30 自 index.tsx 拆出，行为不变；保存后回调主页刷新主宠状态。
 */
export function ProfileModal({ pet, open, onClose, onChanged }: {
  pet: PetInfo
  open: boolean
  onClose: () => void
  onChanged: () => void
}) {
  const [profileName, setProfileName] = useState('')
  // 主人称呼（宠物怎么叫主人）草稿
  const [profileOwnerTitle, setProfileOwnerTitle] = useState('')
  const [profileColor, setProfileColor] = useState(PET_COLORS[0])
  const [profileAccessory, setProfileAccessory] = useState('none')
  const [privacyBusy, setPrivacyBusy] = useState(false)
  // F7：纪念日卡片（弹层打开时惰性拉取，失败静默）
  const [anniversary, setAnniversary] = useState<PetAnniversary | null>(null)

  const toast = (msg: string) => Taro.showToast({ title: msg, icon: 'none' })

  useEffect(() => {
    if (!open) return
    setProfileName(pet.name)
    setProfileOwnerTitle(pet.ownerTitle ?? '主人')
    const appearance = parseResult<{ color?: string; accessory?: string }>(pet.appearance)
    setProfileColor(appearance?.color ?? PET_COLORS[0])
    setProfileAccessory(appearance?.accessory ?? 'none')
    void (async () => {
      try {
        const { data: res } = await petApi.getAnniversaries()
        setAnniversary(res.data)
      } catch {
        // 展示型数据：忽略
      }
    })()
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
      onChanged()
    } catch (error) {
      // 改名冷却时外观已保存，仅提示剩余改动
      toast(friendlyError(error))
    }
  }

  const togglePrivacy = async (next: boolean) => {
    setPrivacyBusy(true)
    try {
      await petApi.updatePrivacy({ isPublic: next })
      onChanged()
    } catch (error) {
      toast(friendlyError(error))
    } finally {
      setPrivacyBusy(false)
    }
  }

  if (!open) return null

  return (
    <View className={styles.modalMask} onClick={onClose}>
      <View className={styles.modalCard} onClick={(e) => e.stopPropagation()}>
        <Text className={styles.modalTitle}>宠物档案</Text>
        <Text className={styles.modalLabel}>昵称</Text>
        <Input
          className={styles.nameInput}
          value={profileName}
          maxlength={12}
          placeholder="宠物名（30 天可改一次）"
          onInput={(e) => setProfileName(e.detail.value)}
        />
        <Text className={styles.modalLabel}>它怎么叫你</Text>
        <Input
          className={styles.nameInput}
          value={profileOwnerTitle}
          maxlength={12}
          placeholder="宠物怎么叫你（默认「主人」）"
          onInput={(e) => setProfileOwnerTitle(e.detail.value)}
        />
        <View className={styles.optionRow}>
          {ACCESSORIES.map((item) => (
            <View
              key={item.key}
              className={`${styles.optionChip} ${profileAccessory === item.key ? styles.optionChipActive : ''}`}
              onClick={() => setProfileAccessory(item.key)}
            >
              <Text>{item.label}</Text>
            </View>
          ))}
        </View>
        <View className={styles.optionRow}>
          {PET_COLORS.map((color) => (
            <View
              key={color}
              className={`${styles.optionChip} ${profileColor === color ? styles.optionChipActive : ''}`}
              onClick={() => setProfileColor(color)}
            >
              <Text>{color}</Text>
            </View>
          ))}
        </View>
        {anniversary && (
          <View className={styles.modalRow}>
            <Text className={styles.modalLabel}>
              🎂 相遇第 {anniversary.adoptionDays} 天 · 连续陪伴 {anniversary.companionStreak} 天
              {anniversary.nextMilestone ? ` · ${anniversary.nextMilestone.title}` : ''}
            </Text>
          </View>
        )}
        <View className={styles.modalRow}>
          <Text className={styles.modalLabel}>在个人主页展示宠物</Text>
          <CreamToggle on={pet.isPublic} disabled={privacyBusy} onChange={() => void togglePrivacy(!pet.isPublic)} />
        </View>
        <View className={styles.modalActions}>
          <CreamButton variant="ghost" style={{ flex: 1 }} onClick={onClose}>取消</CreamButton>
          <CreamButton style={{ flex: 1 }} onClick={() => void saveProfile()}>保存</CreamButton>
        </View>
      </View>
    </View>
  )
}
