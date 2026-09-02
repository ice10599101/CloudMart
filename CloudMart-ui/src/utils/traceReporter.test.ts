import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createTraceReporter } from './traceReporter'

/** 定时器全部使用假时钟 */
beforeEach(() => {
    vi.useFakeTimers()
})

afterEach(() => {
    vi.useRealTimers()
})

describe('createTraceReporter', () => {
    it('start 立即首报一次', async () => {
        const report = vi.fn().mockResolvedValue(undefined)
        const reporter = createTraceReporter({
            report,
            getPosition: async () => ({ lat: 23.1, lng: 113.2 }),
        })

        reporter.start()
        // 首报异步完成
        await vi.advanceTimersByTimeAsync(0)

        expect(report).toHaveBeenCalledTimes(1)
        expect(report).toHaveBeenCalledWith({ lat: 23.1, lng: 113.2 })
        reporter.stop()
    })

    it('每 5 分钟上报一次', async () => {
        const report = vi.fn().mockResolvedValue(undefined)
        const reporter = createTraceReporter({
            report,
            getPosition: async () => ({ lat: 1, lng: 2 }),
        })

        reporter.start()
        await vi.advanceTimersByTimeAsync(0)
        expect(report).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(5 * 60 * 1000)
        expect(report).toHaveBeenCalledTimes(2)

        await vi.advanceTimersByTimeAsync(5 * 60 * 1000)
        expect(report).toHaveBeenCalledTimes(3)
        reporter.stop()
    })

    it('stop 后停止上报', async () => {
        const report = vi.fn().mockResolvedValue(undefined)
        const reporter = createTraceReporter({ report, getPosition: async () => ({ lat: 1, lng: 2 }) })

        reporter.start()
        await vi.advanceTimersByTimeAsync(0)
        reporter.stop()

        await vi.advanceTimersByTimeAsync(30 * 60 * 1000)
        expect(report).toHaveBeenCalledTimes(1)
    })

    it('重复 start 幂等（不叠加计时器）', async () => {
        const report = vi.fn().mockResolvedValue(undefined)
        const reporter = createTraceReporter({ report, getPosition: async () => ({ lat: 1, lng: 2 }) })

        reporter.start()
        reporter.start()
        reporter.start()
        await vi.advanceTimersByTimeAsync(0)
        expect(report).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(5 * 60 * 1000)
        expect(report).toHaveBeenCalledTimes(2)
        reporter.stop()
    })

    it('上报失败静默：不影响后续周期', async () => {
        const report = vi.fn()
            .mockRejectedValueOnce(new Error('429'))
            .mockResolvedValue(undefined)
        const onError = vi.fn()
        const reporter = createTraceReporter({
            report,
            getPosition: async () => ({ lat: 1, lng: 2 }),
            onError,
        })

        reporter.start()
        await vi.advanceTimersByTimeAsync(0)
        expect(onError).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(5 * 60 * 1000)
        expect(report).toHaveBeenCalledTimes(2)
        reporter.stop()
    })

    it('上一次请求未完成时跳过本轮（防重入）', async () => {
        let resolveReport!: (v: void) => void
        const report = vi.fn().mockImplementation(() => new Promise<void>((r) => { resolveReport = r }))
        const reporter = createTraceReporter({
            report,
            getPosition: async () => ({ lat: 1, lng: 2 }),
            intervalMs: 1000,
        })

        reporter.start()
        await vi.advanceTimersByTimeAsync(0)
        expect(report).toHaveBeenCalledTimes(1)

        // 首报未 resolve，经过 3 个周期不叠加
        await vi.advanceTimersByTimeAsync(3000)
        expect(report).toHaveBeenCalledTimes(1)

        resolveReport()
        await vi.advanceTimersByTimeAsync(1000)
        expect(report).toHaveBeenCalledTimes(2)
        reporter.stop()
    })

    it('定位失败同样静默且计入 onError', async () => {
        const report = vi.fn()
        const onError = vi.fn()
        const reporter = createTraceReporter({
            report,
            getPosition: async () => { throw new Error('PERMISSION_DENIED') },
            onError,
        })

        reporter.start()
        await vi.advanceTimersByTimeAsync(0)

        expect(report).not.toHaveBeenCalled()
        expect(onError).toHaveBeenCalledTimes(1)
        reporter.stop()
    })
})
