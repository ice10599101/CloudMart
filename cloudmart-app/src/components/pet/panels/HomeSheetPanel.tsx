// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useState } from 'react'
import { Modal, Switch, Text, View } from 'react-native'
import * as Clipboard from 'expo-clipboard'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { petApi, type PetInfo, type PetShareCard } from '@/api/pet'
import { ChipButton, SHARE_TYPES } from '../shared'
import { HomePanel } from './HomePanel'

/** 家园面板：家园主页 + 小贴士 + 主页公开开关 + 动态卡片分享（含分享文案弹层） */
export function HomeSheetPanel({ pet, onRefresh }: { pet: PetInfo; onRefresh: () => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [shareCard, setShareCard] = useState<PetShareCard | null>(null)
  const [privacyBusy, setPrivacyBusy] = useState(false)

  const togglePrivacy = async (next: boolean) => {
    setPrivacyBusy(true)
    try {
      await petApi.updatePrivacy({ isPublic: next })
      onRefresh()
    } catch {
      // 拦截器已提示
    } finally {
      setPrivacyBusy(false)
    }
  }

  const loadShareCard = async (type: Parameters<typeof petApi.getShareCard>[0]) => {
    try {
      const { data: res } = await petApi.getShareCard(type)
      setShareCard(res.data)
    } catch {
      // 拦截器已提示
    }
  }

  const copyShare = async () => {
    if (!shareCard) return
    await Clipboard.setStringAsync(`${shareCard.title}\n${shareCard.content}${shareCard.highlight ? `\n${shareCard.highlight}` : ''}`)
    setShareCard(null)
    // 复制后直接跳到发布页（与 Web 端一致：不自动发帖，用户自行确认发布）
    router.push('/publish')
  }

  return (
    <>
      <View style={{ gap: 6 }}>
        <HomePanel onRefresh={onRefresh} />
        <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>养成小贴士</Text>
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>🍖 饱食和清洁随时间下降，记得回来照顾它</Text>
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>💼 打工赚宠物币，📚 读书涨智力</Text>
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>🍾 宠物会定时帮你捞社区漂流瓶并主动提醒</Text>
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>⚔️ 对战由服务端计算，输了也有经验</Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: Spacing.sm }}>
          <Text style={{ color: colors.text, fontSize: FontSize.sm }}>在个人主页展示宠物</Text>
          <Switch value={pet.isPublic} disabled={privacyBusy} onValueChange={togglePrivacy} />
        </View>
        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
          关闭后不会进入宠物榜单，其他用户也看不到它
        </Text>
        <View style={{ marginTop: Spacing.sm }}>
          <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>宠物动态卡片</Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: 6 }}>
            {SHARE_TYPES.map((item) => (
              <ChipButton key={item.key} label={item.label} onPress={() => loadShareCard(item.key)} />
            ))}
          </View>
        </View>
      </View>

      {/* 分享卡片（文案服务端生成，复制到剪贴板后去发布页） */}
      <Modal visible={shareCard != null} transparent animationType="fade" onRequestClose={() => setShareCard(null)}>
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(0,0,0,0.5)', padding: Spacing.lg }}>
          <View style={{ width: '100%', backgroundColor: colors.bgBase, borderRadius: BorderRadius.xl, padding: Spacing.lg, gap: Spacing.sm }}>
            <Text style={{ color: colors.text, fontSize: FontSize.lg, fontWeight: '700' }}>{shareCard?.title}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.sm }}>{shareCard?.content}</Text>
            {shareCard?.highlight
              ? <Text style={{ color: colors.primary, fontSize: FontSize.sm, fontWeight: '600' }}>{shareCard.highlight}</Text>
              : null}
            <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
              <ChipButton label="关闭" onPress={() => setShareCard(null)} />
              <ChipButton label="复制文案" primary onPress={copyShare} />
            </View>
          </View>
        </View>
      </Modal>
    </>
  )
}
