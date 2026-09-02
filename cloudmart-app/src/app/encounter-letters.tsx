import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, RefreshControl, Switch } from 'react-native'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import * as Location from 'expo-location'
import { wishApi } from '@/api/wish'
import type { EncounterLetterItem } from '@/types'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

const STATUS_LABELS: Record<EncounterLetterItem['status'], string> = {
  PENDING: '🔒 未拆封',
  DELIVERED: '✉️ 可拆信',
  READ: '📬 已读',
}

/**
 * 擦肩而过信笺（Sprint 3.3，四AB B6 APP 端 + B8 轨迹上报）：
 * 附近模式开关（开启申请定位权限 + 每 5 分钟上报轨迹，服务端限频/伪造检测兜底）+
 * 信笺列表（PENDING/DELIVERED/READ）+ 拆信 + 匿名互动（祝福/点亮）。
 */
export default function EncounterLettersScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [nearbyMode, setNearbyMode] = useState(false)
  const [letters, setLetters] = useState<EncounterLetterItem[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [interactingId, setInteractingId] = useState<string | number | null>(null)

  const loadLetters = useCallback(async () => {
    if (!isLoggedIn) return
    try {
      const res = await wishApi.listEncounterLetters()
      if (res.data?.success) setLetters(res.data.data ?? [])
    } catch {
      // 静默
    } finally {
      setLoading(false)
    }
  }, [isLoggedIn])

  // 开关状态回显（Redis 键 24h 有效）
  useEffect(() => {
    if (!isLoggedIn) return
    wishApi.getNearbyModeStatus()
      .then((res) => { if (res.data?.success) setNearbyMode(res.data.data === true) })
      .catch(() => undefined)
  }, [isLoggedIn])

  useEffect(() => {
    loadLetters()
  }, [loadLetters])

  /** 轨迹上报（定位权限被拒/失败静默，下个周期自然重试） */
  const reportCurrentPosition = async () => {
    try {
      const setting = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      })
      await wishApi.reportTrace(setting.coords.latitude, setting.coords.longitude)
    } catch {
      // 静默
    }
  }

  /** 开启后每 5 分钟上报一次；关闭/卸载停止 */
  useEffect(() => {
    if (!nearbyMode) return
    void reportCurrentPosition()
    const timer = setInterval(() => {
      void reportCurrentPosition()
    }, 5 * 60 * 1000)
    return () => clearInterval(timer)
  }, [nearbyMode])

  const handleModeToggle = async (enabled: boolean) => {
    if (enabled) {
      const perm = await Location.requestForegroundPermissionsAsync()
      if (perm.status !== 'granted') {
        alert('需要定位权限才能开启附近模式，请在系统设置中允许')
        return
      }
    }
    try {
      const res = await wishApi.setNearbyMode(enabled)
      if (res.data?.success) {
        setNearbyMode(enabled)
        alert(enabled ? '附近模式已开启 ✨' : '附近模式已关闭')
      }
    } catch {
      alert('设置失败，请稍后重试')
    }
  }

  const handleOpen = async (letter: EncounterLetterItem) => {
    if (letter.status === 'READ') return
    try {
      const res = await wishApi.readEncounterLetter(letter.letterId)
      if (res.data?.success) {
        const updated = res.data.data
        setLetters((prev) => prev.map((it) => (it.letterId === letter.letterId ? updated : it)))
      }
    } catch (err) {
      const errNode = err as { response?: { data?: { error?: { message?: string } } } }
      alert(errNode?.response?.data?.error?.message || '拆信失败，请稍后重试')
    }
  }

  const handleInteract = async (letter: EncounterLetterItem, type: 'BLESS' | 'LIGHT') => {
    setInteractingId(letter.letterId)
    try {
      const res = await wishApi.interactEncounterLetter(letter.letterId, type)
      if (res.data?.success) {
        setLetters((prev) => prev.map((it) => (it.letterId === letter.letterId ? res.data.data : it)))
        alert(type === 'BLESS' ? '已送上祝福 🌟' : '已为 TA 点亮 ✨')
      }
    } catch (err) {
      const errNode = err as { response?: { data?: { error?: { message?: string } } } }
      alert(errNode?.response?.data?.error?.message || '互动失败，请稍后重试')
    } finally {
      setInteractingId(null)
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: Spacing.lg, paddingBottom: Spacing.sm }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.accentCyan }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>擦肩而过</Text>
        <View style={{ width: 48 }} />
      </View>

      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginHorizontal: Spacing.lg,
          padding: Spacing.md,
          borderRadius: BorderRadius.lg,
          backgroundColor: WishColors.bgContainer,
        }}
      >
        <View style={{ flex: 1, marginRight: Spacing.md }}>
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginBottom: 2 }}>附近模式</Text>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, lineHeight: 16 }}>
            开启后每 5 分钟匿名上报一次位置（仅存 6 级区块，不含精确坐标），与同路人不期而遇
          </Text>
        </View>
        <Switch
          value={nearbyMode}
          disabled={!isLoggedIn}
          onValueChange={(v) => void handleModeToggle(v)}
          trackColor={{ true: WishColors.accentCyan, false: WishColors.border }}
        />
      </View>

      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={{ padding: Spacing.lg }}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={async () => {
              setRefreshing(true)
              await loadLetters()
              setRefreshing(false)
            }}
            tintColor={WishColors.accentCyan}
          />
        }
      >
        {!isLoggedIn ? (
          <Text style={{ textAlign: 'center', marginTop: 80, color: WishColors.textTertiary, fontSize: FontSize.sm }}>
            请先登录后查看相遇信笺
          </Text>
        ) : loading ? (
          <ActivityIndicator color={WishColors.accentCyan} style={{ marginTop: 60 }} />
        ) : letters.length === 0 ? (
          <Text style={{ textAlign: 'center', marginTop: 60, color: WishColors.textTertiary, fontSize: FontSize.sm, lineHeight: 22 }}>
            还没有相遇信笺{'\n'}开启附近模式，与同路人不期而遇
          </Text>
        ) : (
          letters.map((letter) => (
            <View
              key={letter.letterId}
              style={{
                backgroundColor: WishColors.bgContainer,
                borderRadius: BorderRadius.lg,
                padding: Spacing.md,
                marginBottom: Spacing.sm,
              }}
            >
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 }}>
                <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text }}>
                  {STATUS_LABELS[letter.status]}
                </Text>
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>
                  {letter.encounterGeohash6.slice(0, 4)} 片区
                </Text>
              </View>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginBottom: Spacing.sm }}>
                相遇于 {new Date(letter.encounterTime).toLocaleDateString('zh-CN')}
              </Text>
              <Text style={{ fontSize: FontSize.sm, color: letter.content ? WishColors.text : WishColors.textTertiary, lineHeight: 20, marginBottom: Spacing.sm }}>
                {letter.content ?? '信笺还未到拆封时间，敬请期待'}
              </Text>
              {(letter.status === 'PENDING' || letter.status === 'DELIVERED') && (
                <TouchableOpacity
                  activeOpacity={0.85}
                  onPress={() => handleOpen(letter)}
                  style={{
                    alignSelf: 'flex-end',
                    paddingHorizontal: Spacing.md,
                    paddingVertical: 6,
                    borderRadius: BorderRadius.md,
                    backgroundColor: WishColors.accentCyan,
                  }}
                >
                  <Text style={{ fontSize: FontSize.xs, color: '#0b1026', fontWeight: '600' }}>
                    {letter.status === 'PENDING' ? '查看' : '拆信'}
                  </Text>
                </TouchableOpacity>
              )}
              {letter.status === 'READ' && (
                <View style={{ flexDirection: 'row', gap: Spacing.sm, justifyContent: 'flex-end' }}>
                  {(['BLESS', 'LIGHT'] as const).map((type) => (
                    <TouchableOpacity
                      key={type}
                      activeOpacity={0.85}
                      disabled={interactingId === letter.letterId}
                      onPress={() => handleInteract(letter, type)}
                      style={{
                        paddingHorizontal: Spacing.md,
                        paddingVertical: 6,
                        borderRadius: BorderRadius.md,
                        borderWidth: 1,
                        borderColor: WishColors.border,
                      }}
                    >
                      <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>
                        {type === 'BLESS' ? '🌟 祝福' : '✨ 点亮'}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              )}
            </View>
          ))
        )}
      </ScrollView>
    </View>
  )
}
