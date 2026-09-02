/**
 * 心愿分享卡片绘制核心（Sprint 1.5 分享卡片，Canvas 方案）。
 *
 * 架构说明：文档原设想"Feign 调 mall-community 生成图片 URL"，但 mall-community
 * 实际无图片生成 API，且服务端 AWT 绘制中文存在容器字体缺失风险（方框），
 * 故改为前端 Canvas 绘制 + PNG 下载：预览/下载本就是前端验收项，
 * 星空视觉与前端主题统一，且无服务端依赖。
 *
 * 本文件只放可单测的纯函数（换行、确定性随机种子），
 * Canvas 上下文相关绘制在组件内完成。
 */

/** 字符串确定性哈希（djb2）——作为星星位置的随机种子，保证同一心愿卡片视觉稳定 */
export function hashString(input: string): number {
    let hash = 5381
    for (let i = 0; i < input.length; i++) {
        hash = ((hash << 5) + hash + input.charCodeAt(i)) | 0
    }
    return Math.abs(hash)
}

/** mulberry32 伪随机数生成器（0,1)，确定性可复现 */
export function seededRandom(seed: number): () => number {
    let state = seed | 0
    return () => {
        state = (state + 0x6d2b79f5) | 0
        let t = Math.imul(state ^ (state >>> 15), 1 | state)
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296
    }
}

/** measure 函数抽象：换行算法与 Canvas 解耦以便单测 */
export type TextMeasurer = (text: string) => number;

/**
 * 逐字符换行（中文无空格分词，逐字符贪心装填）。
 * - 超出 maxLines 时截断，末行以「…」结尾
 * - 单个字符宽于 maxWidth 时独立成行（不裁字符）
 */
export function wrapText(text: string, maxWidth: number, maxLines: number, measure: TextMeasurer): string[] {
    const lines: string[] = []
    let current = ''

    const push = () => {
        if (current) {
            lines.push(current)
            current = ''
        }
    }

    for (const char of text.replace(/\r?\n/g, ' ')) {
        if (measure(current + char) > maxWidth && current) {
            push()
            if (lines.length === maxLines) {
                // 已满行：把最后一个字符换成省略号，尽量放下当前字符
                const last = lines[maxLines - 1]
                let trimmed = last
                while (trimmed.length > 0 && measure(`${trimmed}…`) > maxWidth) {
                    trimmed = trimmed.slice(0, -1)
                }
                lines[maxLines - 1] = `${trimmed}…`
                return lines
            }
            // 断行由空格触发时，新行不以上一个断行源的空格开头
            current = char === ' ' ? '' : char
        } else {
            current += char
        }
    }
    push()
    return lines
}
