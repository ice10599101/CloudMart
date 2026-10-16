/**
 * 页面级草稿持久化（sessionStorage）：
 * 发布类页面切到其他页面再返回时恢复已填内容（会话内有效，关标签页即清）。
 * 探测/解析失败一律静默降级——草稿是体验增强，绝不阻塞主流程。
 */

export function loadPageDraft<T>(key: string): T | null {
  try {
    const raw = sessionStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

export function savePageDraft(key: string, value: unknown): void {
  try {
    sessionStorage.setItem(key, JSON.stringify(value))
  } catch {
    // 配额超限/隐私模式：忽略
  }
}

export function clearPageDraft(key: string): void {
  try {
    sessionStorage.removeItem(key)
  } catch {
    // 忽略
  }
}
