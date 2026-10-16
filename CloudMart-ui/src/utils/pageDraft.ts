/**
 * 页面级草稿持久化（localStorage）：
 * 发布类页面切到其他页面（含跨标签页/刷新）再返回时恢复已填内容；
 * 发布/提交成功由页面显式清除。解析/配额失败一律静默降级——
 * 草稿是体验增强，绝不阻塞主流程。
 */

export function loadPageDraft<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

export function savePageDraft(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // 配额超限/隐私模式：忽略
  }
}

export function clearPageDraft(key: string): void {
  try {
    localStorage.removeItem(key)
  } catch {
    // 忽略
  }
}
