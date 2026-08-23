import { Platform } from 'react-native'
import { wishApi } from '@/api/wish'

/** 模块级缓存：App 会话内去重，重启后再报一次（服务端幂等，成本可忽略） */
let reported: { timezone: string; offsetMinutes: number } | null = null

/**
 * 获取 IANA 时区 ID；RN Hermes 引擎 Intl 支持完整，
 * web 端现代浏览器同样支持，异常时降级固定偏移写法（后端仅校验长度）。
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
 * 时区上报（文档 2.15/26.3）：仅当时区或 UTC 偏移与上次上报不同才调用；
 * 失败静默——上报属合规辅助链路，不阻断胶囊功能（openAt 判定只用 UTC）。
 */
export function reportTimezoneIfNeeded(): void {
    const timezone = getTimezoneId()
    const offsetMinutes = -new Date().getTimezoneOffset()
    if (reported && reported.timezone === timezone && reported.offsetMinutes === offsetMinutes) {
        return
    }

    wishApi
        .reportMyTimezone(timezone, offsetMinutes)
        .then((res) => {
            if (res.data?.success) {
                reported = { timezone, offsetMinutes }
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

/** UTC ISO → 本地展示字符串（YYYY-MM-DD HH:mm） */
export function formatLocal(iso: string | null): string {
    if (!iso) return ''
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export const IS_WEB = Platform.OS === 'web'
