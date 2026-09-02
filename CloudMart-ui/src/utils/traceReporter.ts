/**
 * 擦肩而过轨迹上报器（Sprint 3.3，四AB B8）。
 *
 * 行为契约（对齐后端 POST /wish/map/trace）：
 * - 附近模式开启期间每 5 分钟上报一次当前位置（服务端限频 5min>10 次 → 429，
 *   正常节奏不会触发）；start 时立即首报一次
 * - 定位失败/上报失败静默降级（定位属个人隐私功能，不打扰用户；下个周期自然重试）
 * - stop 后停止全部计时器；重复 start 幂等（先清理旧计时器）
 *
 * 定位获取通过 getPosition 注入（浏览器实现用 navigator.geolocation），
 * 上报通过 report 注入——本文件核心逻辑（计时/防重入/静默）与平台解耦可单测。
 */

export interface GeoPosition {
    lat: number
    lng: number
}

export interface TraceReporterOptions {
    report: (pos: GeoPosition) => Promise<void>
    getPosition: () => Promise<GeoPosition>
    /** 上报间隔，默认 5 分钟（后端频率限制 5min/10 次，正常节奏留有余量） */
    intervalMs?: number
    /** 上报/定位失败回调（可选，用于页面内轻度提示；默认静默） */
    onError?: (err: unknown) => void
    /** 单次上报成功回调（可选） */
    onSuccess?: () => void
}

export interface TraceReporter {
    start: () => void
    stop: () => void
    /** 当前是否在运行（测试用） */
    isRunning: () => boolean
}

export function createTraceReporter(options: TraceReporterOptions): TraceReporter {
    const intervalMs = options.intervalMs ?? 5 * 60 * 1000
    let timer: ReturnType<typeof setInterval> | null = null
    let inFlight = false

    const reportOnce = async () => {
        if (inFlight) return
        inFlight = true
        try {
            const pos = await options.getPosition()
            await options.report(pos)
            options.onSuccess?.()
        } catch (err) {
            // 静默降级：定位被拒/限频/伪造冻结等，下个周期自然重试
            options.onError?.(err)
        } finally {
            inFlight = false
        }
    }

    return {
        start() {
            if (timer) return
            void reportOnce()
            timer = setInterval(() => {
                void reportOnce()
            }, intervalMs)
        },
        stop() {
            if (timer) {
                clearInterval(timer)
                timer = null
            }
        },
        isRunning: () => timer !== null,
    }
}

/** 浏览器定位 Promise 包装（_geolocation 不可用或用户拒绝时 reject） */
export function getBrowserPosition(): Promise<GeoPosition> {
    return new Promise((resolve, reject) => {
        if (typeof navigator === 'undefined' || !navigator.geolocation) {
            reject(new Error('GEOLOCATION_UNAVAILABLE'))
            return
        }
        navigator.geolocation.getCurrentPosition(
            (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
            (err) => reject(err),
            { enableHighAccuracy: false, timeout: 8000, maximumAge: 4 * 60 * 1000 },
        )
    })
}
