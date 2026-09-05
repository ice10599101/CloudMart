import { useEffect, useRef, useState, useCallback } from 'react'

/**
 * 树洞 AI 回复打字机效果（Sprint 2.3 验收：AI 回复有打字机效果）。
 * 逐字显示文本，支持跳过（点击立即完成）。
 */
export function useTypewriter(fullText: string, speed = 30) {
    const [displayed, setDisplayed] = useState('')
    const [done, setDone] = useState(false)
    const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
    const indexRef = useRef(0)

    const skip = useCallback(() => {
        if (timerRef.current) {
            clearInterval(timerRef.current)
            timerRef.current = null
        }
        setDisplayed(fullText)
        setDone(true)
    }, [fullText])

    useEffect(() => {
        if (!fullText) {
            setDisplayed('')
            setDone(true)
            return
        }
        setDisplayed('')
        setDone(false)
        indexRef.current = 0

        timerRef.current = setInterval(() => {
            indexRef.current += 1
            if (indexRef.current >= fullText.length) {
                setDisplayed(fullText)
                setDone(true)
                if (timerRef.current) {
                    clearInterval(timerRef.current)
                    timerRef.current = null
                }
            } else {
                setDisplayed(fullText.slice(0, indexRef.current))
            }
        }, speed)

        return () => {
            if (timerRef.current) {
                clearInterval(timerRef.current)
                timerRef.current = null
            }
        }
    }, [fullText, speed])

    return { displayed, done, skip }
}
