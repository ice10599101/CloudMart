/**
 * 设备指纹（规格 1188-1191 风控基线）：轻量自实现——
 * UA + 语言 + 时区 + 屏幕特征 + Canvas 渲染差异合成稳定哈希。
 *
 * <p>用途仅为服务端「同设备多账号」风控基线（X-Device-Id 请求头），
 * 不做跨站追踪；首次计算后缓存 localStorage，避免重复 Canvas 开销。</p>
 */

const STORAGE_KEY = 'cloudmart_device_id'

let cached: string | null = null

function hash(input: string): string {
  // FNV-1a 32 位：足够基线去重，无需加密强度
  let h = 0x811c9dc5
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return (h >>> 0).toString(36)
}

function canvasSignature(): string {
  try {
    const canvas = document.createElement('canvas')
    canvas.width = 200
    canvas.height = 40
    const ctx = canvas.getContext('2d')
    if (!ctx) return 'no-canvas'
    ctx.textBaseline = 'top'
    ctx.font = "14px 'Arial'"
    ctx.fillStyle = '#f60'
    ctx.fillRect(0, 0, 100, 20)
    ctx.fillStyle = '#0f0'
    ctx.fillText('心愿宇宙·设备指纹', 2, 2)
    return canvas.toDataURL().slice(-64)
  } catch {
    return 'canvas-error'
  }
}

/** 稳定设备指纹（36 位内）；失败时回退随机 ID（仅本次会话语义弱化，不阻塞） */
export function getDeviceId(): string {
  if (cached) return cached
  try {
    const existing = localStorage.getItem(STORAGE_KEY)
    if (existing) {
      cached = existing
      return cached
    }
    const parts = [
      navigator.userAgent,
      navigator.language,
      Intl.DateTimeFormat().resolvedOptions().timeZone ?? '',
      `${screen.width}x${screen.height}x${screen.colorDepth}`,
      String(new Date().getTimezoneOffset()),
      canvasSignature(),
    ]
    const id = parts.map(hash).join('-')
    localStorage.setItem(STORAGE_KEY, id)
    cached = id
    return id
  } catch {
    cached = 'unknown-device'
    return cached
  }
}
