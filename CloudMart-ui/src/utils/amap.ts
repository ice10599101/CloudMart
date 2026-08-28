/**
 * 高德地图 JS API 2.0 加载器（Sprint 3.1 WEB 端）。
 *
 * <p>Key 配置方式（二选一）：① 修改本文件 AMAP_KEY 常量；
 * ② 用户浏览器 localStorage 写入 amap_key（免发版应急）。
 * Key 为空时地图组件自动降级为列表模式（不影响数据功能，仅无地图底图）。
 * Key 申请：https://lbs.amap.com/（Web端 JS API）。</p>
 */

export const AMAP_KEY = ''

let inflight: Promise<void> | null = null

/** 动态注入高德 JS SDK（含 MarkerCluster 聚合插件）；Key 缺失时 reject */
export function loadAmapSdk(): Promise<void> {
  const key = resolveKey()
  if (!key) {
    return Promise.reject(new Error('AMAP_KEY 未配置，地图降级为列表模式'))
  }
  const w = window as unknown as { AMap?: unknown }
  if (w.AMap) {
    return Promise.resolve()
  }
  if (!inflight) {
    inflight = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${key}&plugin=AMap.MarkerCluster`
      script.async = true
      script.onload = () => resolve()
      script.onerror = () => reject(new Error('高德地图 SDK 加载失败'))
      document.head.appendChild(script)
    }).finally(() => {
      inflight = null
    })
  }
  return inflight
}

function resolveKey(): string {
  const local = window.localStorage.getItem('amap_key')
  return local && local.trim() ? local.trim() : AMAP_KEY
}
