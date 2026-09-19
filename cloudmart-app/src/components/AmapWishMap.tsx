import { useEffect, useMemo, useState } from 'react'
import { View, Text, ActivityIndicator } from 'react-native'
import { WebView, type WebViewMessageEvent } from 'react-native-webview'
import { useTheme } from '@/hooks/use-theme-context'
import { wishApi } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { NearbyWish } from '@/types'

/**
 * 高德地图 WebView（心愿地图，对齐 Web 端 WishMap 地图模式）。
 *
 * Key 优先级与 Web 端 utils/amap.ts 一致：
 * ① 后端 GET /wish/map/config 下发（nacos mall-wish.yml amap.*）；
 * ② 内置 AMAP_KEY 常量兜底。
 * 经 react-native-webview 加载高德 JS API 2.0 + MarkerCluster：
 * 心愿点自动聚合（缩放合并/展开），点击单个心愿点 postMessage 回 Native 跳详情。
 * 无需 react-native-amap3d 原生依赖（原注释留档方案已作废——Key 由后端下发，WebView 即可用）。
 */

/** 与 Web 端 utils/amap.ts 相同的内置兜底 Key */
const AMAP_KEY = 'b7b068d7715d4bcd7e0929d648ab047a'

const DEFAULT_CENTER = { lat: 23.1291, lng: 113.2644 }

interface AmapWishMapProps {
  wishes: NearbyWish[]
  /** 地图中心（用户定位；缺省取首个心愿或默认城市） */
  center?: { lat: number; lng: number } | null
  /** 点击心愿点回调 */
  onWishPress?: (wishId: number) => void
  /** SDK/Key 不可用回调（调用方降级列表模式） */
  onUnavailable?: () => void
  height?: number
}

function buildMapHtml(wishes: NearbyWish[], key: string, securityCode: string, center: { lat: number; lng: number }): string {
  const points = wishes.map((w) => ({
    lnglat: [w.approximateLng, w.approximateLat],
    wishId: w.wishId,
    title: w.title,
    lightCount: w.lightCount,
  }))
  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
<style>
  html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #0b1026; }
  .wish-pill { background: rgba(233,69,96,0.85); color: #fff; border-radius: 999px; padding: 4px 10px; font-size: 12px; white-space: nowrap; cursor: pointer; }
</style>
</head>
<body>
<div id="map"></div>
<script>
  ${securityCode ? `window._AMapSecurityConfig = { securityJsCode: ${JSON.stringify(securityCode)} };` : ''}
  var WISHES = ${JSON.stringify(points)};
  var script = document.createElement('script');
  script.src = 'https://webapi.amap.com/maps?v=2.0&key=${key}&plugin=AMap.MarkerCluster';
  script.async = true;
  script.onload = function () {
    try {
      var map = new AMap.Map('map', {
        zoom: 13,
        center: [${center.lng}, ${center.lat}],
        mapStyle: 'amap://styles/dark',
      });
      var cluster = new AMap.MarkerCluster(map, WISHES, {
        gridSize: 60,
        renderMarker: function (ctx) {
          var data = ctx.marker.getExtData() || {};
          ctx.marker.setContent('<div class="wish-pill" data-wish-id="' + data.wishId + '">' + (data.title || '心愿') + '</div>');
          ctx.marker.on('click', function () {
            if (data.wishId && window.ReactNativeWebView) {
              window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'wish', wishId: data.wishId }));
            }
          });
        },
      });
      setTimeout(function () { map.setFitView(null, false, [40, 40, 40, 40]); }, 600);
    } catch (e) {
      if (window.ReactNativeWebView) {
        window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'error', message: String(e && e.message || e) }));
      }
    }
  };
  script.onerror = function () {
    if (window.ReactNativeWebView) {
      window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'error', message: 'AMap SDK load failed' }));
    }
  };
  document.head.appendChild(script);
</script>
</body>
</html>`
}

export default function AmapWishMap({ wishes, center, onWishPress, onUnavailable, height = 260 }: AmapWishMapProps) {
  const theme = useTheme()
  const [loadingSdk, setLoadingSdk] = useState(true)
  const [failed, setFailed] = useState(false)
  const [key, setKey] = useState('')
  const [securityCode, setSecurityCode] = useState('')

  // 后端下发 Key 优先，内置 Key 兜底（与 Web 端 resolveKey 优先级一致）
  useEffect(() => {
    let alive = true
    wishApi
      .getMapConfig()
      .catch(() => null)
      .then((res) => {
        if (!alive) return
        const config = (res?.data as { data?: { amapKey?: string; securityCode?: string } } | null)?.data
        setKey(config?.amapKey?.trim() || AMAP_KEY)
        setSecurityCode(config?.securityCode?.trim() ?? '')
      })
    return () => {
      alive = false
    }
  }, [])

  const html = useMemo(() => {
    if (!key) return ''
    const resolvedCenter = center ?? {
      lat: wishes[0]?.approximateLat ?? DEFAULT_CENTER.lat,
      lng: wishes[0]?.approximateLng ?? DEFAULT_CENTER.lng,
    }
    return buildMapHtml(wishes, key, securityCode, resolvedCenter)
  }, [wishes, key, securityCode, center])

  const handleMessage = (event: WebViewMessageEvent) => {
    try {
      const data = JSON.parse(event.nativeEvent.data) as { type?: string; wishId?: number; message?: string }
      if (data.type === 'wish' && data.wishId) {
        onWishPress?.(data.wishId)
      } else if (data.type === 'error') {
        setFailed(true)
        onUnavailable?.()
      }
    } catch {
      // 非 JSON 消息忽略
    }
  }

  if (failed) {
    return null
  }

  return (
    <View
      style={{
        height,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        backgroundColor: '#0b1026',
        borderWidth: 1,
        borderColor: 'rgba(255,255,255,0.08)',
      }}
    >
      {html ? (
        <WebView
          source={{ html }}
          originWhitelist={['*']}
          javaScriptEnabled
          domStorageEnabled
          onMessage={handleMessage}
          onLoadEnd={() => setLoadingSdk(false)}
          onError={() => {
            setFailed(true)
            onUnavailable?.()
          }}
          style={{ flex: 1, backgroundColor: 'transparent' }}
        />
      ) : (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator color={theme.primary} />
          <Text style={{ fontSize: FontSize.xs, color: 'rgba(255,255,255,0.5)', marginTop: Spacing.sm }}>地图加载中...</Text>
        </View>
      )}
      {loadingSdk ? (
        <View pointerEvents="none" style={{ position: 'absolute', bottom: Spacing.sm, alignSelf: 'center' }}>
          <Text style={{ fontSize: 10, color: 'rgba(255,255,255,0.35)' }}>高德地图</Text>
        </View>
      ) : null}
    </View>
  )
}
