import { useEffect, useState } from 'react'
import { Map, Text, View } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import type { MapCluster, NearbyWish } from '@/types'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import styles from './index.module.scss'

/**
 * 附近心愿地图（Sprint 3.1 移动端）：Taro Map 组件（小程序原生 map，
 * H5 端腾讯地图渲染），markers 含聚合角标 callout；定位拒绝时服务端
 * 默认城市兜底（三端兜底语义一致）。
 */

type MapProps = import('@tarojs/components/types/Map').MapProps
type MapMarker = NonNullable<MapProps['markers']>[number]

export default function WishMapPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [markers, setMarkers] = useState<MapMarker[]>([])
  const [clusters, setClusters] = useState<MapCluster[]>([])
  const [loading, setLoading] = useState(true)
  const [center, setCenter] = useState<{ lat: number; lng: number } | null>(null)

  // 定位（拒绝/失败 → null，服务端默认城市兜底）
  useEffect(() => {
    Taro.getLocation({ type: 'wgs84' })
      .then((res) => setCenter({ lat: res.latitude, lng: res.longitude }))
      .catch(() => setCenter(null))
  }, [])

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
      </View>
      <WishBGM />
    </View>
  )
}
