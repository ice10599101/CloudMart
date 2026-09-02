import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView, Switch } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import type { EncounterLetterItem } from '@/types'
import styles from './index.module.scss'

const STATUS_LABELS: Record<EncounterLetterItem['status'], string> = {
  PENDING: '未拆封',
  DELIVERED: '可拆信',
  READ: '已读',
}

/**
 * 擦肩而过信笺（Sprint 3.3，四AB B6 移动端 + B8 轨迹上报）：
 * 附近模式开关（开启即启动每 5 分钟轨迹上报，服务端限频/伪造检测兜底）+
 * 信笺列表（PENDING 未拆封 / DELIVERED 可拆信 / READ 已读）+ 拆信 + 匿名互动。
 */
export default function EncounterLettersPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [nearbyMode, setNearbyMode] = useState(false)
  const [letters, setLetters] = useState<EncounterLetterItem[]>([])
  const [loading, setLoading] = useState(true)
  const [modeToggling, setModeToggling] = useState(false)
  const [interactingId, setInteractingId] = useState<string | number | null>(null)

  const loadLetters = useCallback(async () => {
    if (!isLoggedIn) return
    try {
      const res = await wishApi.listEncounterLetters()
      if (res.data.success) setLetters(res.data.data ?? [])
    } catch {
      // 静默
    } finally {
      setLoading(false)
    }
  }, [isLoggedIn])

  // 开关状态回显（Redis 键 24h 有效，过期视为关闭）
  useEffect(() => {
    if (!isLoggedIn) return
    wishApi.getNearbyModeStatus()
      .then((res) => { if (res.data.success) setNearbyMode(res.data.data === true) })
      .catch(() => undefined)
  }, [isLoggedIn])

  useEffect(() => {
    loadLetters()
  }, [loadLetters])

  /** 附近模式开关：先调后端成功再更新本地状态（失败不变更） */
  const handleModeToggle = async (enabled: boolean) => {
    setModeToggling(true)
    try {
      const res = await wishApi.setNearbyMode(enabled)
      if (res.data.success) {
        setNearbyMode(enabled)
        Taro.showToast({
          title: enabled ? '附近模式已开启 ✨' : '附近模式已关闭',
          icon: 'none',
        })
      }
    } catch {
      Taro.showToast({ title: '设置失败，请稍后重试', icon: 'none' })
    } finally {
      setModeToggling(false)
    }
  }

  /** 轨迹上报（附近模式开启时调用；定位失败静默） */
  const reportCurrentPosition = async () => {
    try {
      const setting = await Taro.getLocation({ type: 'wgs84' })
      await wishApi.reportTrace(setting.latitude, setting.longitude)
    } catch {
      // 定位被拒/服务限频：静默，下个周期自然重试
    }
  }

  /** 开启附近模式后每 5 分钟上报一次（组件卸载/关闭时停止） */
  useEffect(() => {
    if (!nearbyMode) return
    void reportCurrentPosition()
    const timer = setInterval(() => {
      void reportCurrentPosition()
    }, 5 * 60 * 1000)
    return () => clearInterval(timer)
  }, [nearbyMode])

  /** 拆信：PENDING/DELIVERED → READ，返回内容 */
  const handleOpen = async (letter: EncounterLetterItem) => {
    if (letter.status === 'READ') return
    try {
      const res = await wishApi.readEncounterLetter(letter.letterId)
      if (res.data.success) {
        const updated = res.data.data
        setLetters((prev) => prev.map((it) => (it.letterId === letter.letterId ? updated : it)))
      }
    } catch (err) {
      const errNode = err as { data?: { error?: { message?: string } } }
      Taro.showToast({ title: errNode?.data?.error?.message || '拆信失败，请稍后重试', icon: 'none' })
    }
  }

  /** 匿名互动：BLESS（祝福）/ LIGHT（点亮） */
  const handleInteract = async (letter: EncounterLetterItem, type: 'BLESS' | 'LIGHT') => {
    setInteractingId(letter.letterId)
    try {
      const res = await wishApi.interactEncounterLetter(letter.letterId, type)
      if (res.data.success) {
        setLetters((prev) => prev.map((it) => (it.letterId === letter.letterId ? res.data.data : it)))
        Taro.showToast({ title: type === 'BLESS' ? '已送上祝福 🌟' : '已为 TA 点亮 ✨', icon: 'none' })
      }
    } catch (err) {
      const errNode = err as { data?: { error?: { message?: string } } }
      Taro.showToast({ title: errNode?.data?.error?.message || '互动失败，请稍后重试', icon: 'none' })
    } finally {
      setInteractingId(null)
    }
  }

  return (
    <View className={styles.page} style={{ paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title="擦肩而过" back />

      {/* 附近模式开关 */}
      <View className={styles.modeCard}>
        <View className={styles.modeTextWrap}>
          <Text className={styles.modeTitle}>附近模式</Text>
          <Text className={styles.modeDesc}>
            开启后每 5 分钟匿名上报一次位置（仅存 6 级区块，不含精确坐标），与同路人不期而遇
          </Text>
        </View>
        <Switch
          checked={nearbyMode}
          disabled={modeToggling || !isLoggedIn}
          onChange={(e) => handleModeToggle(e.detail.value)}
          color='#4a90d9'
        />
      </View>

      <ScrollView className={styles.list} scrollY>
        {!isLoggedIn ? (
          <View className={styles.empty}>
            <Text>请先登录后查看相遇信笺</Text>
          </View>
        ) : loading ? (
          <View className={styles.empty}><Text>加载中...</Text></View>
        ) : letters.length === 0 ? (
          <View className={styles.empty}>
            <Text>还没有相遇信笺{'\n'}开启附近模式，与同路人不期而遇</Text>
          </View>
        ) : (
          letters.map((letter) => (
            <View key={letter.letterId} className={styles.letterCard}>
              <View className={styles.letterHeader}>
                <Text className={styles.letterStatus}>
                  {letter.status === 'DELIVERED' ? '✉️ ' : letter.status === 'READ' ? '📬 ' : '🔒 '}
                  {STATUS_LABELS[letter.status]}
                </Text>
                <Text className={styles.letterZone}>{letter.encounterGeohash6.slice(0, 4)} 片区</Text>
              </View>
              <Text className={styles.letterTime}>
                相遇于 {new Date(letter.encounterTime).toLocaleDateString('zh-CN')}
              </Text>
              {letter.content ? (
                <View className={styles.letterContent}>
                  <Text selectable>{letter.content}</Text>
                </View>
              ) : (
                <View className={styles.letterContent}>
                  <Text className={styles.letterHint}>信笺还未到拆封时间，敬请期待</Text>
                </View>
              )}
              {(letter.status === 'PENDING' || letter.status === 'DELIVERED') && (
                <View className={styles.letterActions}>
                  <View
                    className={styles.openBtn}
                    onClick={() => handleOpen(letter)}
                  >
                    <Text>{letter.status === 'PENDING' ? '查看' : '拆信'}</Text>
                  </View>
                </View>
              )}
              {letter.status === 'READ' && (
                <View className={styles.letterActions}>
                  <View
                    className={styles.interactBtn}
                    onClick={interactingId === letter.letterId ? undefined : () => handleInteract(letter, 'BLESS')}
                  >
                    <Text>🌟 祝福</Text>
                  </View>
                  <View
                    className={styles.interactBtn}
                    onClick={interactingId === letter.letterId ? undefined : () => handleInteract(letter, 'LIGHT')}
                  >
                    <Text>✨ 点亮</Text>
                  </View>
                </View>
              )}
            </View>
          ))
        )}
      </ScrollView>
    </View>
  )
}
