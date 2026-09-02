import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, Modal, RefreshControl, ScrollView, TextInput } from 'react-native'
import { useCallback, useEffect, useState } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import * as Location from 'expo-location'
import type { NearbyWish, WarmEventItem } from '@/types'
import type { MyWishListItem } from '@/types'

/**
 * 附近心愿（Sprint 3.1 APP 端）：列表模式（同一 API 契约，数据与三端一致）。
 * 地图渲染需 react-native-amap3d（原生依赖 + 高德 Key），待配置后升级
 * 地图模式（偏差留档进度文件四V·5）；本页为无依赖可用形态。
 */
export default function NearbyWishesScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [wishes, setWishes] = useState<NearbyWish[]>([])
  const [loading, setLoading] = useState(true)
  // B7：围栏打卡 + 温暖事件
  const [userPos, setUserPos] = useState<{ lat: number; lng: number } | null>(null)
  const [myWishes, setMyWishes] = useState<MyWishListItem[]>([])
  const [checkWishId, setCheckWishId] = useState<string | number | null>(null)
  const [checkResult, setCheckResult] = useState('')
  const [checkBusy, setCheckBusy] = useState(false)
  const [warmEvents, setWarmEvents] = useState<WarmEventItem[]>([])
  const [warmOpen, setWarmOpen] = useState(false)
  const [warmTitle, setWarmTitle] = useState('')
  const [warmContent, setWarmContent] = useState('')
  const [warmSaving, setWarmSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      // 定位：expo-location 权限链路（拒绝/失败 → null，服务端默认城市兜底）
      let pos: { lat: number; lng: number } | null = null
      try {
        const perm = await Location.getForegroundPermissionsAsync()
        if (perm.granted) {
          const setting = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced })
          pos = { lat: setting.coords.latitude, lng: setting.coords.longitude }
          setUserPos(pos)
        }
      } catch {
        // 静默
      }
      const [wishesRes, warmRes] = await Promise.all([
        wishApi.getMapWishes(pos ? { lat: pos.lat, lng: pos.lng, radius: 5000 } : { radius: 5000 }),
        wishApi.listWarmEvents(pos ? { lat: pos.lat, lng: pos.lng, radius: 5000 } : { radius: 5000 }).catch(() => null),
      ])
      if (wishesRes.data.success) setWishes(wishesRes.data.data ?? [])
      if (warmRes?.data.success) setWarmEvents(warmRes.data.data ?? [])
    } catch {
      // 静默空态
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  // 打卡候选：自己的 ACTIVE 心愿
  useEffect(() => {
    if (!isLoggedIn) return
    wishApi.listMyWishes({ status: 'ACTIVE', pageSize: 50 })
      .then((res) => { if (res.data?.success) setMyWishes(res.data.data ?? []) })
      .catch(() => undefined)
  }, [isLoggedIn])

  /** 围栏打卡：提交当前坐标，展示到达/未到达 */
  const handleCheckFence = async () => {
    if (!checkWishId) {
      alert('先点选要打卡的心愿')
      return
    }
    setCheckBusy(true)
    try {
      let pos = userPos
      if (!pos) {
        const setting = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced })
        pos = { lat: setting.coords.latitude, lng: setting.coords.longitude }
      }
      const res = await wishApi.checkFence(checkWishId, pos.lat, pos.lng)
      if (res.data?.success) {
        setCheckResult(res.data.data.insideFence ? '🎉 到达！心愿绽放' : '还未到达围栏附近，继续加油')
      }
    } catch (err) {
      const errNode = err as { response?: { data?: { error?: { message?: string } } } }
      setCheckResult(errNode?.response?.data?.error?.message || '打卡失败，请稍后重试')
    } finally {
      setCheckBusy(false)
    }
  }

  /** 发布温暖事件（坐标来自当前定位，无定位由服务端默认城市兜底） */
  const handlePublishWarm = async () => {
    if (!warmTitle.trim() || !warmContent.trim()) {
      alert('标题和内容都要填写哦')
      return
    }
    setWarmSaving(true)
    try {
      let pos = userPos
      if (!pos) {
        const setting = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced })
        pos = { lat: setting.coords.latitude, lng: setting.coords.longitude }
      }
      const res = await wishApi.publishWarmEvent({
        title: warmTitle.trim(),
        content: warmContent.trim(),
        lat: pos.lat,
        lng: pos.lng,
      })
      if (res.data?.success) {
        setWarmOpen(false)
        setWarmTitle('')
        setWarmContent('')
        wishApi.listWarmEvents({ lat: pos.lat, lng: pos.lng, radius: 5000 })
          .then((r) => { if (r.data?.success) setWarmEvents(r.data.data ?? []) })
          .catch(() => undefined)
      }
    } catch (err) {
      const errNode = err as { response?: { data?: { error?: { message?: string } } } }
      alert(errNode?.response?.data?.error?.message || '发布失败，请稍后重试')
    } finally {
      setWarmSaving(false)
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      {/* 顶栏 */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingHorizontal: Spacing.md,
          paddingVertical: Spacing.sm,
          borderBottomWidth: 1,
          borderBottomColor: WishColors.border,
        }}
      >
        <TouchableOpacity onPress={() => router.back()} accessibilityLabel="返回">
          <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: WishColors.text }}>附近的心愿</Text>
        <TouchableOpacity accessibilityLabel="刷新" onPress={load}>
          <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan }}>刷新</Text>
        </TouchableOpacity>
      </View>

      <Text
        style={{
          paddingHorizontal: Spacing.md,
          paddingVertical: Spacing.sm,
          fontSize: FontSize.xs,
          color: WishColors.textTertiary,
        }}
      >
        坐标经 geohash 模糊化（约 150m 网格 + 偏移），仅展示公开心愿 · 定位失败时展示默认城市
      </Text>

      {/* B7：围栏打卡 */}
      <View style={{ marginHorizontal: Spacing.md, marginTop: Spacing.sm, backgroundColor: WishColors.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.md }}>
        <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text, marginBottom: Spacing.sm }}>🎯 围栏打卡</Text>
        {myWishes.length === 0 ? (
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>创建进行中的心愿并设置围栏后可打卡</Text>
        ) : (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: Spacing.sm }}>
            {myWishes.map((w) => (
              <TouchableOpacity
                key={w.id}
                activeOpacity={0.85}
                onPress={() => { setCheckWishId(w.id); setCheckResult('') }}
                style={{
                  paddingHorizontal: Spacing.md,
                  paddingVertical: 6,
                  borderRadius: BorderRadius.md,
                  marginRight: Spacing.sm,
                  borderWidth: 1,
                  borderColor: checkWishId === w.id ? WishColors.accentCyan : WishColors.border,
                  backgroundColor: checkWishId === w.id ? 'rgba(0, 212, 255, 0.12)' : 'transparent',
                }}
              >
                <Text style={{ fontSize: FontSize.xs, color: checkWishId === w.id ? WishColors.accentCyan : WishColors.textSecondary }} numberOfLines={1}>
                  {w.title}
                </Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        )}
        <TouchableOpacity
          activeOpacity={0.85}
          disabled={checkBusy || !checkWishId}
          onPress={handleCheckFence}
          style={{
            paddingVertical: Spacing.sm,
            borderRadius: BorderRadius.md,
            alignItems: 'center',
            backgroundColor: 'rgba(0, 212, 255, 0.12)',
            opacity: checkBusy || !checkWishId ? 0.5 : 1,
          }}
        >
          <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan }}>
            {checkBusy ? '验证中...' : '提交当前定位'}
          </Text>
        </TouchableOpacity>
        {checkResult ? (
          <Text style={{ fontSize: FontSize.xs, color: '#ffd700', marginTop: Spacing.sm }}>{checkResult}</Text>
        ) : null}
      </View>

      {/* B7：温暖事件 */}
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginHorizontal: Spacing.md, marginTop: Spacing.md, marginBottom: Spacing.sm }}>
        <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text }}>🔥 附近温暖瞬间</Text>
        <TouchableOpacity onPress={() => setWarmOpen(true)}>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.accentCyan }}>+ 分享温暖</Text>
        </TouchableOpacity>
      </View>
      {warmEvents.slice(0, 5).map((ev) => (
        <View key={ev.eventId} style={{ marginHorizontal: Spacing.md, marginBottom: Spacing.sm, backgroundColor: WishColors.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.md }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 2 }}>
            <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text }}>{ev.title}</Text>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>{ev.distance}m</Text>
          </View>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary, marginBottom: 4 }}>{ev.content}</Text>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>
            {ev.nickname || '匿名'} · {new Date(ev.createdAt).toLocaleDateString('zh-CN')}
          </Text>
        </View>
      ))}
      {warmEvents.length === 0 && (
        <Text style={{ textAlign: 'center', color: WishColors.textTertiary, fontSize: FontSize.xs, marginBottom: Spacing.sm }}>
          附近还没有温暖事件，分享第一个吧
        </Text>
      )}

      <FlatList
        data={wishes}
        keyExtractor={(item) => String(item.wishId)}
        renderItem={({ item }) => (
          <TouchableOpacity
            activeOpacity={0.8}
            onPress={() => router.push(`/wish-detail?id=${item.wishId}`)}
            style={{
              marginHorizontal: Spacing.md,
              marginBottom: Spacing.sm,
              backgroundColor: WishColors.bgContainer,
              borderWidth: 1,
              borderColor: WishColors.border,
              borderRadius: BorderRadius.xl,
              padding: Spacing.md,
            }}
          >
            <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text }}>
              「{item.title}」
            </Text>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 4 }}>
              距离约 {item.distance}m · {item.lightCount} 次点亮
            </Text>
          </TouchableOpacity>
        )}
        ListEmptyComponent={
          loading ? (
            <ActivityIndicator color={WishColors.primary} style={{ marginVertical: Spacing.xxl }} />
          ) : (
            <Text style={{ textAlign: 'center', color: WishColors.textTertiary, padding: Spacing.lg, fontSize: FontSize.sm }}>
              附近暂无公开心愿
            </Text>
          )
        }
        contentContainerStyle={{ paddingVertical: Spacing.sm, paddingBottom: insets.bottom + Spacing.xl }}
      />

      <Modal visible={warmOpen} transparent animationType="fade" onRequestClose={() => setWarmOpen(false)}>
        <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'center', padding: Spacing.xl }}>
          <View style={{ backgroundColor: WishColors.bgContainer, borderRadius: BorderRadius.xl, padding: Spacing.lg }}>
            <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text, marginBottom: Spacing.md }}>分享温暖瞬间</Text>
            <TextInput
              value={warmTitle}
              onChangeText={setWarmTitle}
              maxLength={30}
              placeholder="标题（如：天桥下的免费雨伞）"
              placeholderTextColor={WishColors.textSecondary}
              style={{ borderWidth: 1, borderColor: WishColors.border, borderRadius: BorderRadius.md, padding: Spacing.md, marginBottom: Spacing.sm, fontSize: FontSize.sm, color: WishColors.text }}
            />
            <TextInput
              value={warmContent}
              onChangeText={setWarmContent}
              maxLength={200}
              multiline
              placeholder="描述这件温暖的小事…"
              placeholderTextColor={WishColors.textSecondary}
              style={{ borderWidth: 1, borderColor: WishColors.border, borderRadius: BorderRadius.md, padding: Spacing.md, marginBottom: Spacing.md, fontSize: FontSize.sm, color: WishColors.text, minHeight: 80, textAlignVertical: 'top' }}
            />
            <View style={{ flexDirection: 'row', gap: Spacing.md }}>
              <TouchableOpacity
                activeOpacity={0.85}
                onPress={() => setWarmOpen(false)}
                style={{ flex: 1, paddingVertical: Spacing.sm + 2, borderRadius: BorderRadius.lg, alignItems: 'center', backgroundColor: 'rgba(255,255,255,0.08)' }}
              >
                <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity
                activeOpacity={0.85}
                disabled={warmSaving}
                onPress={handlePublishWarm}
                style={{ flex: 1, paddingVertical: Spacing.sm + 2, borderRadius: BorderRadius.lg, alignItems: 'center', backgroundColor: WishColors.accentCyan, opacity: warmSaving ? 0.6 : 1 }}
              >
                <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#0b1026' }}>
                  {warmSaving ? '发布中...' : '发布'}
                </Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  )
}
