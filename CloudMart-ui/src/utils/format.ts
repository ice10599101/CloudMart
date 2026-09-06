export function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

/** 块级标签：替换为空格以保留分段语义；其余（strong/em/span/a 等行内标签）直接删除 */
const BLOCK_TAG_PATTERN = /<\/?(p|div|br|h[1-6]|ul|ol|li|blockquote|pre|table|thead|tbody|tr|td|th|hr|section|article)[^>]*>/gi

/**
 * 富文本 HTML 转纯文本，用于卡片摘要、AI 输入预填等纯文本场景。
 * 仅做展示级转换（去标签 + 常用实体解码 + 空白折叠）；安全渲染必须走 DOMPurify，不得用本函数过滤 XSS。
 */
export function stripHtml(html: string | null | undefined): string {
  if (!html) return ''
  return html
    .replace(BLOCK_TAG_PATTERN, ' ')
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&')
    .replace(/\s+/g, ' ')
    .trim()
}

/**
 * 解析后端时间字符串。后端 LocalDateTime 序列化不带时区后缀（如 2026-09-06T13:42:15），
 * ES 规范会按 UTC 解析导致本地时间偏差；统一补本地时区后再解析。
 * 数字输入按秒/毫秒时间戳兼容处理；无法解析返回 null。
 */
export function parseServerTime(input: string | number | null | undefined): number | null {
  if (input === null || input === undefined || input === '') return null
  if (typeof input === 'number') {
    const ms = input > 1e12 ? input : input * 1000
    return Number.isFinite(ms) ? ms : null
  }
  const str = String(input).trim()
  if (/^\d+$/.test(str)) {
    const num = Number(str)
    const ms = num > 1e12 ? num : num * 1000
    return Number.isFinite(ms) ? ms : null
  }
  // 无时区后缀的 ISO 串 → 视为本地时间（与后端服务器一致采用本地时区语义）
  const m = str.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}(:\d{2})?(\.\d+)?)$/)
  const date = m ? new Date(`${m[1]}T${m[2].slice(0, 8)}`) : new Date(str)
  const t = date.getTime()
  return Number.isFinite(t) ? t : null
}

export function timeAgo(dateStr: string | number | null | undefined): string {
  const ts = parseServerTime(dateStr)
  // 无法解析时不显示误导性的"XX年前"，直接隐藏时间
  if (ts === null) return ''
  const diff = Date.now() - ts
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return '刚刚'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days === 1) return '昨天'
  if (days < 30) return `${days}天前`
  const months = Math.floor(days / 30)
  if (months < 12) return `${months}个月前`
  return `${Math.floor(months / 12)}年前`
}
