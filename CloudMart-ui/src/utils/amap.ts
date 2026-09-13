/**
 * 高德地图 JS API 2.0 加载器（Sprint 3.1 WEB 端）。
 *
 * <p>Key 配置方式（三选一，优先级从高到低）：
 * ① nacos mall-wish.yml 配置 amap.key / amap.security-code（由后端
 *    GET /wish/map/config 下发，调用方传入）；
 * ② 浏览器 localStorage 写入 amap_key / amap_security_code（免发版应急）；
 * ③ 本文件 AMAP_KEY 常量兜底。</p>
 *
 * <p>安全密钥注意（BUG#45）：安全密钥与 Key 成对发放、仅 2021-12-02 之后申请的
 * Key 需要。给不需要密钥的 Key 注入错误的 securityJsCode 会直接弄坏数据通道
 * （瓦片全部拒绝、灰地图且永不触发 complete）。因此 securityCode 仅在
 * localStorage 显式配置或调用方明确传入时注入，不再硬编码默认值。
 * Key 申请：https://lbs.amap.com/（应用 → 添加 Key → 服务平台选「Web端(JS API)」）。</p>
 */

export const AMAP_KEY = 'b7b068d7715d4bcd7e0929d648ab047a'

let inflight: Promise<void> | null = null

function resolveKey(explicit?: string): string {
  if (explicit && explicit.trim()) return explicit.trim()
  const local = window.localStorage.getItem('amap_key')
  return local && local.trim() ? local.trim() : AMAP_KEY
}

function resolveSecurityCode(explicit?: string): string {
  if (explicit && explicit.trim()) return explicit.trim()
  const local = window.localStorage.getItem('amap_security_code')
  return local && local.trim() ? local.trim() : ''
}

/**
 * 动态注入高德 JS SDK（含 MarkerCluster 聚合插件）。
 *
 * @param key 高德 Key（可空，空则依次回退 localStorage → 内置常量；全空 reject 降级列表）
 * @param securityCode 安全密钥（可空；仅传入/localStorage 配置时注入——错误的密钥会弄坏数据通道）
 */
export function loadAmapSdk(key?: string, securityCode?: string): Promise<void> {
  const resolvedKey = resolveKey(key)
  if (!resolvedKey) {
    return Promise.reject(new Error('AMAP_KEY 未配置，地图降级为列表模式'))
  }
  const w = window as unknown as { AMap?: unknown }
  if (w.AMap) {
    return Promise.resolve()
  }
  if (!inflight) {
    inflight = new Promise<void>((resolve, reject) => {
      // 安全密钥必须在 SDK 脚本加载前注入，晚于 script.onload 无效；
      // 未配置时不注入（老 Key 无需密钥，错误密钥反而阻塞数据通道）
      const resolvedCode = resolveSecurityCode(securityCode)
      if (resolvedCode) {
        ;(w as { _AMapSecurityConfig?: { securityJsCode: string } })._AMapSecurityConfig = {
          securityJsCode: resolvedCode,
        }
      }
      const script = document.createElement('script')
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${resolvedKey}&plugin=AMap.MarkerCluster`
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
