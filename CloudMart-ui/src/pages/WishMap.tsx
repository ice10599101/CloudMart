import { useEffect, useRef, useState } from 'react'
import { Skeleton } from 'antd'
import { history } from 'umi'
import { getMapWishes, getMapClusters, type MapCluster, type NearbyWish } from '@/api/wish'
import WishBGM from '@/components/WishBGM'
import { AMAP_KEY, loadAmapSdk } from '@/utils/amap'
import styles from './WishMap.module.css'

/**
 * 附近心愿地图（Sprint 3.1 WEB 端）：
 * 高德 JS API 2.0（Key 未配置自动降级列表模式）+ 网格聚合角标 +
 * 加载进度提示 + 空坐标兜底（定位失败直接用服务端默认城市）。
 */

export default function WishMap() {
  const mapDivRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<{ destroy?: () => void } | null>(null)
  const [sdkState, setSdkState] = useState<'loading' | 'ready' | 'fallback'>('loading')
  const [loadingData, setLoadingData] = useState(true)
  const [wishes, setWishes] = useState<NearbyWish[]>([])
  const [clusters, setClusters] = useState<MapCluster[]>([])
  const [userPos, setUserPos] = useState<{ lat: number; lng: number } | null>(null)

  // 定位（拒绝/失败 → null，由服务端默认城市兜底，三端兜底语义一致）
  useEffect(() => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => setUserPos({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
        () => setUserPos(null),
        { timeout: 3000 },
      )
    } else {
      setUserPos(null)
    }
  }, [])

  useEffect(() => {
    if (userPos === null && !('geolocation' in navigator)) {
      return
    }
    // userPos 定位结束后拉数据（null 也是一次有效结果）
    const lat = userPos?.lat
    const lng = userPos?.lng
    setLoadingData(true)
    Promise.all([
      getMapWishes({ lat, lng, radius: 5000 }),
      getMapClusters({ lat, lng, radius: 5000 }),
    ])
      .then(([wishesRes, clusterRes]) => {
        if (wishesRes.data.success) setWishes(wishesRes.data.data ?? [])
        if (clusterRes.data.success) setClusters(clusterRes.data.data ?? [])
      })
      .catch(() => {
        // 地图数据加载失败保持空态
      })
      .finally(() => setLoadingData(false))
  }, [userPos])

  // 地图渲染（Key 缺失 → fallback 列表模式）
  useEffect(() => {
    if (loadingData) return
    loadAmapSdk()
      .then(() => {
        setSdkState('ready')
        const w = window as unknown as { AMap: Record<string, unknown> & (new (el: HTMLElement, opts: Record<string, unknown>) => unknown) }
        const AMapCtor = w.AMap as unknown as (new (el: HTMLElement, opts: Record<string, unknown>) => unknown)
        if (!mapDivRef.current) return
        const center = userPos ?? {
          lat: wishes[0]?.approximateLat ?? 23.1291,
          lng: wishes[0]?.approximateLng ?? 113.2644,
        }
        const map: unknown = new AMapCtor(mapDivRef.current, {
          zoom: 13,
          center: [center.lng, center.lat],
          mapStyle: 'amap://styles/dark',
          viewMode: '3D',
          pitch: 30,
        })
        mapRef.current = map as { destroy?: () => void }
        // 聚合角标（MarkerCluster：缩放自动合并/展开——聚合动效验收）
        const AMapNS = w.AMap as unknown as {
          MarkerCluster: new (map: unknown, points: Array<{ lnglat: [number, number]; weight?: number }>, opts?: Record<string, unknown>) => unknown
        }
        const cluster = new AMapNS.MarkerCluster(map, clusters.map((c) => ({
          lnglat: [c.centerLng, c.centerLat],
          weight: c.count,
        })), {
          gridSize: 60,
          renderMarker: (ctx: { marker: { setContent: (html: string) => void } }) => {
            ctx.marker.setContent(
              `<div style="background:rgba(233,69,96,0.85);color:#fff;border-radius:999px;padding:4px 10px;font-size:12px;">心愿</div>`,
            )
          },
        })
        void cluster
      })
      .catch(() => setSdkState('fallback'))
  }, [loadingData, userPos, wishes, clusters])

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.body}>
        <h1 className={styles.pageTitle}>📍 附近的心愿</h1>
        <p className={styles.pageSubtitle}>坐标经 geohash 模糊化处理（约 150m 网格 + 偏移），仅展示公开心愿</p>

        <div className={styles.mapWrap}>
          {sdkState !== 'fallback' && <div ref={mapDivRef} style={{ width: '100%', height: '100%' }} />}
          {(sdkState === 'loading' || loadingData) && sdkState !== 'fallback' && (
            <div className={styles.loadingMask}>
              <div className={styles.loadingBar}>
                <div className={styles.loadingBarInner} />
              </div>
              <span className={styles.loadingText}>地图加载中...</span>
            </div>
          )}
          {sdkState === 'fallback' && (
            <div style={{ padding: 16, height: '100%', overflow: 'auto' }}>
              <div className={styles.fallbackNotice}>
                地图 Key 未配置，已降级为列表模式（数据与聚合功能不受影响）。
                配置方式：localStorage 写入 amap_key，或修改 src/utils/amap.ts 后重新构建。
              </div>
              <div className={styles.listGrid}>
                {wishes.map((w) => (
                  <div
                    key={w.wishId}
                    className={styles.wishCard}
                    onClick={() => history.push(`/wish/${w.wishId}`)}
                  >
                    <p className={styles.wishTitle}>「{w.title}」</p>
                    <p className={styles.wishMeta}>距离约 {w.distance}m · {w.lightCount} 次点亮</p>
                  </div>
                ))}
                {wishes.length === 0 && !loadingData && (
                  <p className={styles.emptyText}>附近暂无公开心愿（可拖动地图或稍后再来）</p>
                )}
              </div>
            </div>
          )}
        </div>

        {/* 聚合角标 */}
        {clusters.length > 0 && (
          <div className={styles.clusterRow}>
            {clusters.slice(0, 8).map((c) => (
              <span key={c.geohash6} className={styles.clusterPill}>
                网格 {c.geohash6} · <span className={styles.clusterCount}>{c.count}</span> 个心愿
              </span>
            ))}
          </div>
        )}

        {/* 附近心愿列表 */}
        {sdkState === 'ready' && (
          <div className={styles.listGrid}>
            {wishes.slice(0, 9).map((w) => (
              <div
                key={w.wishId}
                className={styles.wishCard}
                onClick={() => history.push(`/wish/${w.wishId}`)}
              >
                <p className={styles.wishTitle}>「{w.title}」</p>
                <p className={styles.wishMeta}>
                  <span className={styles.distance}>≈ {w.distance}m</span> · {w.lightCount} 次点亮
                </p>
              </div>
            ))}
            {wishes.length === 0 && <p className={styles.emptyText}>附近暂无公开心愿</p>}
          </div>
        )}
        {loadingData && <Skeleton active paragraph={{ rows: 4 }} title={false} />}
      </div>
      <WishBGM />
    </div>
  )
}
