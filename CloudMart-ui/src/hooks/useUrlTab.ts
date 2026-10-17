import { useEffect, useState } from 'react'
import { useSearchParams } from 'umi'

/**
 * tab（或单选筛选）状态同步到 URL 查询参数：
 * - 切换时写入 history（后退/前进/分享链接均可还原离开时的标签页）；
 * - 后退/前进触发 URL 变化时反向同步 state；
 * - 默认值不写入参数，保持 URL 干净。
 *
 * valid 用于校验 URL 参数合法性（非法值回落 fallback）。
 */
export function useUrlTab<T extends string>(
  key: string,
  valid: readonly T[],
  fallback: T,
): [T, (next: T) => void] {
  const [searchParams, setSearchParams] = useSearchParams()
  const raw = searchParams.get(key) as T | null
  const [tab, setTabState] = useState<T>(
    raw && (valid as readonly string[]).includes(raw) ? raw : fallback,
  )

  // 浏览器后退/前进：URL 参数变化时反向同步 state
  useEffect(() => {
    if (raw && (valid as readonly string[]).includes(raw)) {
      setTabState(raw)
    } else if (!raw) {
      setTabState(fallback)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [raw])

  const setTab = (next: T) => {
    setTabState(next)
    const params = new URLSearchParams(searchParams)
    if (next === fallback) {
      params.delete(key)
    } else {
      params.set(key, next)
    }
    setSearchParams(params)
  }

  return [tab, setTab]
}
