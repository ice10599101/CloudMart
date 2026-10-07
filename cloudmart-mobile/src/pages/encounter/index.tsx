import { useEffect, useState, useCallback } from 'react'
import { View, Text, ScrollView, Textarea } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { encounterApi, type EncounterLetter } from '@/api/wish'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

/**
 * P0-5 相遇信笺信箱（擦肩而过用户端入口）。
 * 信笺匿名化（无对方身份信息）；PENDING 待投递 → DELIVERED 可拆信 →
 * READ 已拆信；互动：BLESS 匿名祝福（免费）/ LIGHT 点亮对方心愿（扣星光 2），
 * 单信笺每日 1 次（后端限频）。
 */

const STATUS_META: Record<string, { label: string; hint: string }> = {
  PENDING: { label: '投递中', hint: '相遇的记忆正在飞往对方' },
  DELIVERED: { label: '待拆封', hint: '信已送达，点击拆信' },
  READ: { label: '已拆封', hint: '' },
}

function formatTime(time?: string | null) {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

export default function EncounterPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [letters, setLetters] = useState<EncounterLetter[]>([])
  const [loading, setLoading] = useState(true)
  /** 拆信后本地展开的正文（letterId → content） */
  const [opened, setOpened] = useState<Record<number, string>>({})
  const [interacted, setInteracted] = useState<Record<number, boolean>>({})
  const [blessOpenId, setBlessOpenId] = useState<number | null>(null)
  const [blessContent, setBlessContent] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)

  const loadLetters = useCallback(async () => {
    try {
      setLoading(true)
      const res = await encounterApi.listLetters()
      setLetters(res.data?.data as unknown as EncounterLetter[])
    } catch (err) {
      const message = (err as { data?: { error?: { message?: string } } })?.data?.error?.message
      Taro.showToast({ title: message || '信笺加载失败', icon: 'none' })
      setLetters([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadLetters()
  }, [loadLetters])

  const handleOpen = async (letter: EncounterLetter) => {
    if (busyId !== null) return
    if (letter.status === 'PENDING') {
      Taro.showToast({ title: '信还在路上', icon: 'none' })
      return
    }
    setBusyId(letter.letterId)
    try {
      const res = await encounterApi.readLetter(letter.letterId)
      const detail = res.data?.data as unknown as EncounterLetter
      if (detail?.content) {
        setOpened((prev) => ({ ...prev, [letter.letterId]: detail.content as string }))
      }
      loadLetters()
    } catch (err) {
      const message = (err as { data?: { error?: { message?: string } } })?.data?.error?.message
      Taro.showToast({ title: message || '拆信失败', icon: 'none' })
    } finally {
      setBusyId(null)
    }
  }

  const handleInteract = async (letter: EncounterLetter, type: 'BLESS' | 'LIGHT') => {
    if (busyId !== null) return
    if (type === 'BLESS') {
      setBlessOpenId(letter.letterId)
      setBlessContent('')
      return
    }
    setBusyId(letter.letterId)
    try {
      await encounterApi.interactLetter(letter.letterId, type)
      setInteracted((prev) => ({ ...prev, [letter.letterId]: true }))
      Taro.showToast({ title: '已点亮对方的心愿（-2 星光）', icon: 'success' })
    } catch (err) {
      const message = (err as { data?: { error?: { message?: string } } })?.data?.error?.message
      Taro.showToast({ title: message || '点亮失败', icon: 'none' })
    } finally {
      setBusyId(null)
    }
  }

  const submitBless = async () => {
    if (blessOpenId === null) return
    setBusyId(blessOpenId)
    try {
      await encounterApi.interactLetter(blessOpenId, 'BLESS', blessContent.trim() || undefined)
      setInteracted((prev) => ({ ...prev, [blessOpenId]: true }))
      setBlessOpenId(null)
      setBlessContent('')
      Taro.showToast({ title: '匿名祝福已寄出', icon: 'success' })
    } catch (err) {
      const message = (err as { data?: { error?: { message?: string } } })?.data?.error?.message
      Taro.showToast({ title: message || '祝福寄出失败', icon: 'none' })
    } finally {
      setBusyId(null)
    }
  }

  return (
    <View data-theme={dataTheme} className={styles.container} style={themeStyle}>
      <CustomNavBar title="相遇信笺" back />
      <ScrollView scrollY className={styles.scroll} style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}>
        <Text className={styles.intro}>
          与陌生人的心愿擦肩而过时，会留下一封匿名信笺——没有身份，只有一段相遇和一颗需要被点亮的心愿。
        </Text>
        {loading && <Text className={styles.emptyText}>信笺加载中...</Text>}
        {!loading && letters.length === 0 && (
          <View className={styles.emptyBlock}>
            <Text className={styles.emptyIcon}>💌</Text>
            <Text className={styles.emptyText}>还没有相遇的信笺</Text>
            <Text className={styles.emptyHint}>在「附近心愿」地图开启附近模式，带着心愿出门走走吧</Text>
          </View>
        )}
        {letters.map((letter) => {
          const meta = STATUS_META[letter.status] ?? { label: letter.status, hint: '' }
          const body = opened[letter.letterId] ?? letter.content
          return (
            <View key={letter.letterId} className={styles.letterCard}>
              <View className={styles.letterHead}>
                <Text className={styles.letterTag}>{meta.label}</Text>
                {letter.wishTags?.length > 0 && (
                  <Text className={styles.letterTags}>{letter.wishTags.map((t) => `#${t}`).join(' ')}</Text>
                )}
                <Text className={styles.letterTime}>{formatTime(letter.encounterTime)}</Text>
              </View>

              {letter.status === 'PENDING' && <Text className={styles.letterPending}>✉️ {meta.hint}</Text>}

              {body ? (
                <View className={styles.letterBody}>
                  <Text className={styles.letterContent}>{body}</Text>
                  {letter.status === 'READ' && <Text className={styles.letterHint}>这封信来自一场真实的擦肩而过</Text>}
                </View>
              ) : letter.status === 'DELIVERED' ? (
                <View className={styles.openBtn} onClick={busyId === letter.letterId ? undefined : () => handleOpen(letter)}>
                  <Text className={styles.openBtnText}>{busyId === letter.letterId ? '拆信中...' : '✂️ 拆信'}</Text>
                </View>
              ) : null}

              {(letter.status === 'DELIVERED' || letter.status === 'READ') && (
                <View className={styles.interactRow}>
                  <View
                    className={`${styles.interactBtn} ${interacted[letter.letterId] ? styles.interactBtnDone : ''}`}
                    onClick={interacted[letter.letterId] || busyId === letter.letterId ? undefined : () => handleInteract(letter, 'BLESS')}
                  >
                    <Text className={styles.interactText}>{interacted[letter.letterId] ? '已回应' : '🕊️ 匿名祝福'}</Text>
                  </View>
                  <View
                    className={`${styles.interactBtn} ${interacted[letter.letterId] ? styles.interactBtnDone : ''}`}
                    onClick={interacted[letter.letterId] || busyId === letter.letterId ? undefined : () => handleInteract(letter, 'LIGHT')}
                  >
                    <Text className={styles.interactText}>✨ 点亮心愿（2 星光）</Text>
                  </View>
                </View>
              )}
            </View>
          )
        })}
      </ScrollView>

      {blessOpenId !== null && (
        <View className={styles.modalMask} onClick={() => setBlessOpenId(null)}>
          <View className={styles.modalBody} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>寄出匿名祝福</Text>
            <Textarea
              className={styles.modalTextarea}
              value={blessContent}
              maxlength={200}
              placeholder='写点什么（选填，仅对方可见这句祝福）'
              onInput={(e) => setBlessContent(e.detail.value)}
            />
            <View className={styles.modalBtns}>
              <View className={styles.modalCancel} onClick={() => setBlessOpenId(null)}>
                <Text>取消</Text>
              </View>
              <View className={styles.modalOk} onClick={busyId === blessOpenId ? undefined : submitBless}>
                <Text>{busyId === blessOpenId ? '寄出中...' : '寄出'}</Text>
              </View>
            </View>
          </View>
        </View>
      )}
    </View>
  )
}
