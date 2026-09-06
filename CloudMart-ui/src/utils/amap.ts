/**
 * 高德地图 JS API 2.0 加载器（Sprint 3.1 WEB 端）。
 *
 * <p>Key 配置方式（二选一）：① 修改本文件 AMAP_KEY / AMAP_SECURITY_CODE 常量；
 * ② 用户浏览器 localStorage 写入 amap_key / amap_security_code（免发版应急）。
 * Key 为空时地图组件自动降级为列表模式（不影响数据功能，仅无地图底图）。
 * Key 申请：https://lbs.amap.com/（应用 → 添加 Key → 服务平台选「Web端(JS API)」）。</p>
 *
 * <p>安全密钥：2021-12-02 之后申请的 JS API Key 必须配合 securityJsCode 使用，
 * 缺失或不匹配时 SDK 请求报 INVALID_USER_SCODER。须在 SDK 脚本加载前注入
 * window._AMapSecurityConfig 才会生效。</p>
 */

export const AMAP_KEY = 'b7b068d7715d4bcd7e0929d648ab047a'
/** 高德控制台与 Key 成对发放的安全密钥（securityJsCode） */
export const AMAP_SECURITY_CODE = '1b5989f7ccd045daadd442d3283cf6a4'

let inflight: Promise<void> | null = null

function resolveKey(): string {
  const local = window.localStorage.getItem('amap_key')
  return local && local.trim() ? local.trim() : AMAP_KEY
}

function resolveSecurityCode(): string {
  const local = window.localStorage.getItem('amap_security_code')
  return local && local.trim() ? local.trim() : AMAP_SECURITY_CODE
}

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
      // 安全密钥必须在 SDK 脚本加载前注入，晚于 script.onload 无效
      const securityCode = resolveSecurityCode()
      if (securityCode) {
        ;(w as { _AMapSecurityConfig?: { securityJsCode: string } })._AMapSecurityConfig = {
          securityJsCode: securityCode,
        }
      }
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
