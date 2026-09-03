import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'

const CACHE_KEY = 'wish_reported_timezone'

interface ReportedCache {
    timezone: string
    offsetMinutes: number
}

/**
 * 获取 IANA 时区 ID；部分小程序宿主（老安卓内核）无完整 Intl，
 * 降级为固定偏移写法（后端仅校验长度 ≤32，不参与到期判定）。
 */
export function getTimezoneId(): string {
    try {
        const tz = Intl.DateTimeFormat().resolvedOptions().timeZone
        if (tz) return tz
    } catch {
        // fallthrough
    }
    const offset = -new Date().getTimezoneOffset()
    const sign = offset >= 0 ? '+' : '-'
    const abs = Math.abs(offset)
    const hours = String(Math.floor(abs / 60)).padStart(2, '0')
    const minutes = String(abs % 60).padStart(2, '0')
    return `UTC${sign}${hours}:${minutes}`
}

/**
 * 时区上报（文档 2.15/26.3）：仅当时区或 UTC 偏移与上次上报不同才调用
 * （服务端幂等，客户端缓存避免重复请求）；失败静默——上报属合规辅助链路，
 * 不阻断胶囊功能（openAt 判定只用 UTC，与上报无关）。
 * 未登录直接跳过：/wish/my/timezone 为认证接口，冷启动（app.tsx useLaunch）
 * 无 token 时调用必然 401，并触发 request 封装的 redirectTo 登录跳转。
 */
export function reportTimezoneIfNeeded(): void {
    if (!Taro.getStorageSync('access_token')) {
        return
    }
    const timezone = getTimezoneId()
    const offsetMinutes = -new Date().getTimezoneOffset()

    let cached: ReportedCache | null = null
    try {
        const raw = Taro.getStorageSync(CACHE_KEY)
        if (raw) cached = typeof raw === 'string' ? (JSON.parse(raw) as ReportedCache) : (raw as ReportedCache)
    } catch {
        cached = null
    }
    if (cached && cached.timezone === timezone && cached.offsetMinutes === offsetMinutes) {
        return
    }

    wishApi
        .reportMyTimezone(timezone, offsetMinutes)
        .then((res) => {
            if (res.data.success) {
                Taro.setStorageSync(CACHE_KEY, JSON.stringify({ timezone, offsetMinutes } satisfies ReportedCache))
            }
        })
        .catch(() => {
            // 静默失败：下次进入胶囊页重试
        })
}

/** 本地日期+时间 → UTC ISO（按设备本地时区换算；到期判定唯一依据） */
export function localToUtcIso(dateStr: string, timeStr: string): string {
    const [y, m, d] = dateStr.split('-').map(Number)
    const [hh, mm] = timeStr.split(':').map(Number)
    const local = new Date(y, m - 1, d, hh, mm, 0, 0)
    return local.toISOString().slice(0, 19)
}
