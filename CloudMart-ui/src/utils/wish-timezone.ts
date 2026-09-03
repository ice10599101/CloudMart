import { reportMyTimezone } from '@/api/wish'

const CACHE_KEY = 'wish:timezone-reported'

interface ReportedCache {
    timezone: string
    offsetMinutes: number
}

/**
 * 时区上报（文档 2.15/26.3）：仅当时区或 UTC 偏移与上次上报不同才调用
 * （服务端幂等，客户端缓存避免重复请求）；失败静默——上报属合规辅助链路，
 * 不阻断胶囊功能（openAt 判定只用 UTC，与上报无关）。
 */
export function reportTimezoneIfNeeded(): void {
    // 未登录不上报：认证接口无 token 必然 401（与 Mobile/APP 端口径一致）
    if (!localStorage.getItem('access_token')) return
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
    if (!timezone) return
    const offsetMinutes = -new Date().getTimezoneOffset()

    let cached: ReportedCache | null = null
    try {
        const raw = localStorage.getItem(CACHE_KEY)
        if (raw) cached = JSON.parse(raw) as ReportedCache
    } catch {
        cached = null
    }
    if (cached && cached.timezone === timezone && cached.offsetMinutes === offsetMinutes) {
        return
    }

    reportMyTimezone(timezone, offsetMinutes)
        .then(() => {
            localStorage.setItem(CACHE_KEY, JSON.stringify({ timezone, offsetMinutes } satisfies ReportedCache))
        })
        .catch(() => {
            // 静默失败：下次进入胶囊页重试
        })
}
