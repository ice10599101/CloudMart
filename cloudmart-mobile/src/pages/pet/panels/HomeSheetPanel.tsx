import { useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Text, View } from '@tarojs/components'
import { petApi, type PetInfo, type PetShareCard } from '@/api/pet'
import { SHARE_TYPES, friendlyError } from '../constants'
import { CreamToggle } from '@/components/pet-cream'
import { HomePanel } from './HomePanel'
import styles from '../index.module.scss'

/**
 * 家园弹层面板：三期家园（房间布置/家具/拜访）+ 养护提示 + 主页公开开关 + 动态分享卡片。
 * R30 自 index.tsx 拆出，行为不变；分享文案由服务端生成。
 */
export function HomeSheetPanel({ pet, onRefresh }: { pet: PetInfo; onRefresh: () => void }) {
  const [shareCard, setShareCard] = useState<PetShareCard | null>(null)
  const [privacyBusy, setPrivacyBusy] = useState(false)

  const toast = (msg: string) => Taro.showToast({ title: msg, icon: 'none' })

  const loadShareCard = async (type: Parameters<typeof petApi.getShareCard>[0]) => {
    try {
      const { data: res } = await petApi.getShareCard(type)
      setShareCard(res.data)
    } catch (error) {
      toast(friendlyError(error))
    }
  }

  const copyShare = () => {
    if (!shareCard) return
    Taro.setClipboardData({
      data: `${shareCard.title}\n${shareCard.content}${shareCard.highlight ? `\n${shareCard.highlight}` : ''}`,
      success: () => toast('已复制，去发布页分享吧'),
    })
  }

  const togglePrivacy = async (next: boolean) => {
    setPrivacyBusy(true)
    try {
      await petApi.updatePrivacy({ isPublic: next })
      onRefresh()
    } catch (error) {
      toast(friendlyError(error))
    } finally {
      setPrivacyBusy(false)
    }
  }

  return (
    <View className={styles.tips}>
      {/* 三期：家园（房间布置 / 家具 / 拜访） */}
      <HomePanel petId={pet.petId} onRefresh={onRefresh} />
      <Text className={styles.tip}>🍖 饱食和清洁随时间下降，记得回来照顾它</Text>
      <Text className={styles.tip}>💼 打工赚宠物币，📚 读书涨智力</Text>
      <Text className={styles.tip}>🍾 宠物会定时帮你捞社区漂流瓶并主动提醒</Text>
      <Text className={styles.tip}>⚔️ 对战由服务端计算，输了也有经验</Text>
      <Text className={styles.tip}>💬 和它聊聊天，它会记住你的喜好哦</Text>
      <View className={styles.modalRow}>
        <Text className={styles.modalLabel}>在个人主页展示宠物</Text>
        <CreamToggle on={pet.isPublic} disabled={privacyBusy} onChange={() => void togglePrivacy(!pet.isPublic)} />
      </View>
      <Text className={styles.tip}>关闭后不会进入宠物榜单，其他用户也看不到它</Text>
      <Text className={styles.sectionTitle}>宠物动态卡片</Text>
      <View className={styles.optionRow}>
        {SHARE_TYPES.map((item) => (
          <View
            key={item.key}
            className={styles.optionChip}
            onClick={() => void loadShareCard(item.key)}
          >
            <Text>{item.label}</Text>
          </View>
        ))}
      </View>

      {/* 分享卡片（文案服务端生成） */}
      {shareCard && (
        <View className={styles.modalMask} onClick={() => setShareCard(null)}>
          <View className={styles.modalCard} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>{shareCard.title}</Text>
            <Text className={styles.modalText}>{shareCard.content}</Text>
            {shareCard.highlight ? <Text className={styles.shareHighlight}>{shareCard.highlight}</Text> : null}
            <View className={styles.modalActions}>
              <Button className={`${styles.miniBtnGhost} ${styles.modalAction}`} onClick={() => setShareCard(null)}>关闭</Button>
              <Button className={`${styles.primaryBtn} ${styles.modalAction}`} onClick={copyShare}>复制文案</Button>
            </View>
          </View>
        </View>
      )}
    </View>
  )
}
