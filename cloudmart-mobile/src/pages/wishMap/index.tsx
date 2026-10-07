import { useEffect, useState, useRef } from 'react'
import { Input, Map, Picker, Switch, Text, Textarea, View } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { encounterApi, wishApi } from '@/api/wish'
import type { MapCluster, NearbyWish, WarmEventItem } from '@/types'
import type { MyWishListItem } from '@/types'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import styles from './index.module.scss'

/**
 * 附近心愿地图（Sprint 3.1 移动端）：Taro Map 组件（小程序原生 map，
 * H5 端腾讯地图渲染），markers 含聚合角标 callout；定位拒绝时服务端
 * 默认城市兜底（三端兜底语义一致）。
 *
 * P0-5 擦肩而过恢复：附近模式开关（Redis 24h）→ 开启期间每 5 分钟上报
 * 轨迹（服务端 geohash 桶化，无原始坐标；5 分钟 >10 次 429，客户端按
 * 5 分钟节奏规避）→ 相遇生成匿名信笺（信箱页查看/回应）。
 */

/** 轨迹上报间隔（毫秒）：对齐服务端 30 分钟桶与 5 分钟限频窗口 */
const TRACE_INTERVAL_MS = 5 * 60 * 1000

type MapProps = import('@tarojs/components/types/Map').MapProps
type MapMarker = NonNullable<MapProps['markers']>[number]

export default function WishMapPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [markers, setMarkers] = useState<MapMarker[]>([])
  const [clusters, setClusters] = useState<MapCluster[]>([])
  const [loading, setLoading] = useState(true)
  const [center, setCenter] = useState<{ lat: number; lng: number } | null>(null)
  // B7：围栏打卡（选自己的心愿 → 提交定位 → 到达/未到达）+ 温暖事件 UGC
  const [myWishes, setMyWishes] = useState<MyWishListItem[]>([])
  const [checkWishIdx, setCheckWishIdx] = useState<number | null>(null)
  const [checkResult, setCheckResult] = useState('')
  const [warmEvents, setWarmEvents] = useState<WarmEventItem[]>([])
  const [warmOpen, setWarmOpen] = useState(false)
  const [warmTitle, setWarmTitle] = useState('')
  const [warmContent, setWarmContent] = useState('')
  const [warmSaving, setWarmSaving] = useState(false)
  const [checkBusy, setCheckBusy] = useState(false)
  // P0-5：附近模式 + 轨迹上报
  const [nearbyMode, setNearbyMode] = useState(false)
  const [nearbyBusy, setNearbyBusy] = useState(false)
  const [pendingLetters, setPendingLetters] = useState(0)
  const traceTimer = useRef<ReturnType<typeof setInterval> | null>(null)

  // 定位（拒绝/失败 → null，服务端默认城市兜底）
  useEffect(() => {
    Taro.getLocation({ type: 'wgs84' })
      .then((res) => setCenter({ lat: res.latitude, lng: res.longitude }))
      .catch(() => setCenter(null))
  }, [])

  // 附近模式状态回显（Redis 开关 24h 过期视为关闭）
  useEffect(() => {
    encounterApi.getNearbyMode()
      .then((res) => { if (res.data.success) setNearbyMode(res.data.data === true) })
      .catch(() => undefined)
  }, [])

  // 信笺待拆数（信箱入口角标）
  useEffect(() => {
    encounterApi.listLetters()
      .then((res) => {
        const letters = res.data?.data ?? []
        setPendingLetters(letters.filter((l) => l.status === 'DELIVERED').length)
      })
      .catch(() => undefined)
  }, [])

  /** 单次轨迹上报（须有定位；失败静默，下个周期重试） */
  const reportTrace = async (lat: number, lng: number) => {
    try {
      await encounterApi.reportTrace(lat, lng)
    } catch {
      // 限频/冻结/网络失败静默——不阻塞地图主流程
    }
  }

  // 附近模式开启且有定位：立即上报一次 + 每 5 分钟续报；关闭/卸载即停止
  useEffect(() => {
    if (!nearbyMode || center === null) return
    reportTrace(center.lat, center.lng)
    traceTimer.current = setInterval(() => {
      Taro.getLocation({ type: 'wgs84' })
        .then((res) => reportTrace(res.latitude, res.longitude))
        .catch(() => undefined)
    }, TRACE_INTERVAL_MS)
    return () => {
      if (traceTimer.current) {
        clearInterval(traceTimer.current)
        traceTimer.current = null
      }
    }
  }, [nearbyMode, center])

  const toggleNearbyMode = async (enabled: boolean) => {
    if (nearbyBusy) return
    setNearbyBusy(true)
    try {
      const res = await encounterApi.setNearbyMode(enabled)
      if (res.data.success) {
        setNearbyMode(enabled)
        Taro.showToast({
          title: enabled ? '附近模式已开启，相遇正在发生' : '附近模式已关闭',
          icon: 'none',
        })
      }
    } catch (err) {
      const message = (err as { data?: { error?: { message?: string } } })?.data?.error?.message
      Taro.showToast({ title: message || '操作失败，请稍后重试', icon: 'none' })
    } finally {
      setNearbyBusy(false)
    }
  }

  // 打卡候选：自己的 ACTIVE 心愿
  useEffect(() => {
    wishApi.listMyWishes({ status: 'ACTIVE', pageSize: 50 })
      .then((res) => { if (res.data.success) setMyWishes(res.data.data ?? []) })
      .catch(() => undefined)
  }, [])

  // 温暖事件列表（定位成功后按坐标拉取；失败/未定位由服务端默认城市兜底）
  useEffect(() => {
    if (center === null) return
    wishApi.listWarmEvents({ lat: center.lat, lng: center.lng, radius: 5000 })
      .then((res) => { if (res.data.success) setWarmEvents(res.data.data ?? []) })
      .catch(() => undefined)
  }, [center])

  useEffect(() => {
    if (center === null && !Taro.canIUse('getLocation')) return
    // center 为 null 也拉取（服务端默认城市兜底）；仅等待一次定位尝试
    const timer = setTimeout(() => {
      const params = center ? { lat: center.lat, lng: center.lng, radius: 5000 } : { radius: 5000 }
      Promise.all([wishApi.getMapWishes(params), wishApi.getMapClusters(params)])
        .then(([wishesRes, clusterRes]) => {
          const wishes: NearbyWish[] = wishesRes.data.success ? wishesRes.data.data ?? [] : []
          setClusters(clusterRes.data.success ? clusterRes.data.data ?? [] : [])
          setMarkers(wishes.map((w, idx) => ({
            id: idx,
            latitude: w.approximateLat,
            longitude: w.approximateLng,
            title: w.title,
            // 小程序 marker 要求 iconPath；用打包内置图标（无外部资源依赖）
            iconPath: require('@/assets/map-pin.png'),
            width: 24,
            height: 30,
            callout: {
              content: `「${w.title}」≈${w.distance}m`,
              color: '#1a1a2e',
              fontSize: 12,
              anchorX: 0,
              anchorY: 0,
              borderRadius: 8,
              borderWidth: 1,
              borderColor: '#e94560',
              bgColor: '#ffffff',
              padding: 6,
              textAlign: 'center' as const,
              display: 'BYCLICK' as const,
            },
          })))
        })
        .catch(() => undefined)
        .finally(() => setLoading(false))
    }, center === null ? 800 : 0)
    return () => clearTimeout(timer)
  }, [center])

  /** 围栏打卡：提交当前坐标，展示"到达/未到达"（响应不含围栏坐标） */
  const handleCheckFence = async () => {
    if (checkWishIdx === null || !myWishes[checkWishIdx]) {
      Taro.showToast({ title: '先选择要打卡的心愿', icon: 'none' })
      return
    }
    setCheckBusy(true)
    try {
      let pos = center
      if (!pos) {
        const setting = await Taro.getLocation({ type: 'wgs84' })
        pos = { lat: setting.latitude, lng: setting.longitude }
      }
      const res = await wishApi.checkFence(myWishes[checkWishIdx].id, pos.lat, pos.lng)
      if (res.data.success) {
        setCheckResult(res.data.data.insideFence ? '🎉 到达！心愿绽放' : '还未到达围栏附近，继续加油')
      }
    } catch (err) {
      const errNode = err as { data?: { error?: { message?: string } } }
      setCheckResult(errNode?.data?.error?.message || '打卡失败，请稍后重试')
    } finally {
      setCheckBusy(false)
    }
  }

  /** 发布温暖事件（坐标来自当前定位，无定位由服务端默认城市兜底） */
  const handlePublishWarm = async () => {
    if (!warmTitle.trim() || !warmContent.trim()) {
      Taro.showToast({ title: '标题和内容都要填写哦', icon: 'none' })
      return
    }
    setWarmSaving(true)
    try {
      let pos = center
      if (!pos) {
        const setting = await Taro.getLocation({ type: 'wgs84' })
        pos = { lat: setting.latitude, lng: setting.longitude }
      }
      const res = await wishApi.publishWarmEvent({
        title: warmTitle.trim(),
        content: warmContent.trim(),
        lat: pos.lat,
        lng: pos.lng,
      })
      if (res.data.success) {
        Taro.showToast({ title: '感谢分享温暖瞬间（审核通过后展示）', icon: 'none' })
        setWarmOpen(false)
        setWarmTitle('')
        setWarmContent('')
        wishApi.listWarmEvents({ lat: pos.lat, lng: pos.lng, radius: 5000 })
          .then((r) => { if (r.data.success) setWarmEvents(r.data.data ?? []) })
          .catch(() => undefined)
      }
    } catch (err) {
      const errNode = err as { data?: { error?: { message?: string } } }
      Taro.showToast({ title: errNode?.data?.error?.message || '发布失败，请稍后重试', icon: 'none' })
    } finally {
      setWarmSaving(false)
    }
  }

  return (
    <View className={styles.container}>
      <CustomNavBar title="附近心愿" back />
      <View style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}>
        <View className={styles.mapWrap}>
          <Map
            className={styles.map}
            latitude={markers[0]?.latitude ?? center?.lat ?? 23.1291}
            longitude={markers[0]?.longitude ?? center?.lng ?? 113.2644}
            markers={markers}
            scale={13}
            enable3D
            showCompass
            enableRotate
            enableOverlooking
            onError={() => undefined}
            onMarkerTap={(e) => {
              const marker = markers.find((m) => m.id === Number(e.detail.markerId))
              if (marker) {
                Taro.showModal({
                  title: marker.title,
                  content: marker.callout?.content ?? '',
                  showCancel: false,
                })
              }
            }}
          />
          {loading && (
            <View className={styles.loadingMask}>
              <Text className={styles.loadingText}>地图加载中...</Text>
            </View>
          )}
        </View>
        <View className={styles.clusterRow}>
          {clusters.slice(0, 6).map((c: MapCluster) => (
            <Text key={c.geohash6} className={styles.clusterPill}>
              网格 {c.geohash6} · {c.count} 个心愿
            </Text>
          ))}
          {clusters.length === 0 && !loading && (
            <Text className={styles.emptyText}>附近暂无公开心愿（定位失败时展示默认城市）</Text>
          )}
        </View>

        {/* P0-5：附近模式（擦肩而过）+ 信笺信箱入口 */}
        <View className={styles.nearbyRow}>
          <View className={styles.nearbyInfo}>
            <Text className={styles.nearbyTitle}>✨ 附近模式</Text>
            <Text className={styles.nearbyHint}>开启后匿名上报轨迹，相遇时留下信笺</Text>
          </View>
          <Switch checked={nearbyMode} disabled={nearbyBusy} onChange={(e) => toggleNearbyMode(e.detail.value)} color='#e94560' />
          <View className={styles.letterEntry} onClick={() => Taro.navigateTo({ url: '/pages/encounter/index' })}>
            <Text className={styles.letterEntryText}>💌 信笺</Text>
            {pendingLetters > 0 && (
              <View className={styles.letterBadge}>
                <Text className={styles.letterBadgeText}>{pendingLetters}</Text>
              </View>
            )}
          </View>
        </View>

        {/* B7：围栏打卡 + 温暖事件 */}
        <View className={styles.b7Row}>
          <Picker
            mode='selector'
            range={myWishes.map((w) => w.title)}
            value={checkWishIdx ?? 0}
            onChange={(e) => { setCheckWishIdx(Number(e.detail.value)); setCheckResult('') }}
          >
            <View className={styles.b7Btn}>
              <Text>{checkWishIdx !== null && myWishes[checkWishIdx] ? `打卡：${myWishes[checkWishIdx].title.slice(0, 8)}` : '围栏打卡'}</Text>
            </View>
          </Picker>
          <View className={styles.b7Btn} onClick={checkBusy ? undefined : handleCheckFence}>
            <Text>{checkBusy ? '验证中...' : '提交定位'}</Text>
          </View>
          <View className={styles.b7Btn} onClick={() => setWarmOpen(true)}>
            <Text>分享温暖</Text>
          </View>
        </View>
        {checkResult ? <Text className={styles.checkResult}>{checkResult}</Text> : null}
        {myWishes.length === 0 && (
          <Text className={styles.emptyText}>创建进行中的心愿并设置围栏后可打卡</Text>
        )}

        <View className={styles.warmSection}>
          <Text className={styles.warmSectionTitle}>🔥 附近温暖瞬间</Text>
          {warmEvents.length === 0 ? (
            <Text className={styles.emptyText}>附近还没有温暖事件，分享第一个吧</Text>
          ) : (
            warmEvents.slice(0, 10).map((ev) => (
              <View key={ev.eventId} className={styles.warmCard}>
                <View className={styles.warmCardHeader}>
                  <Text className={styles.warmTitle}>{ev.title}</Text>
                  <Text className={styles.warmDist}>{ev.distance}m</Text>
                </View>
                <Text className={styles.warmContent}>{ev.content}</Text>
                <Text className={styles.warmMeta}>{ev.nickname || '匿名'} · {new Date(ev.createdAt).toLocaleDateString('zh-CN')}</Text>
              </View>
            ))
          )}
        </View>

        {warmOpen && (
          <View className={styles.modalMask} onClick={() => setWarmOpen(false)}>
            <View className={styles.modalBody} onClick={(e) => e.stopPropagation()}>
              <Text className={styles.warmModalTitle}>分享温暖瞬间</Text>
              <Input
                className={styles.warmInput}
                value={warmTitle}
                maxlength={30}
                placeholder='标题（如：天桥下的免费雨伞）'
                onInput={(e) => setWarmTitle(e.detail.value)}
              />
              <Textarea
                className={styles.warmTextarea}
                value={warmContent}
                maxlength={200}
                placeholder='描述这件温暖的小事…'
                onInput={(e) => setWarmContent(e.detail.value)}
              />
              <View className={styles.modalBtns}>
                <View className={styles.warmModalCancel} onClick={() => setWarmOpen(false)}>
                  <Text>取消</Text>
                </View>
                <View className={styles.warmModalOk} onClick={warmSaving ? undefined : handlePublishWarm}>
                  <Text>{warmSaving ? '发布中...' : '发布'}</Text>
                </View>
              </View>
            </View>
          </View>
        )}
      </View>
      <WishBGM />
    </View>
  )
}
