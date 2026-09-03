/**
 * 心愿分享卡片绘制核心（Sprint 1.5，四AC R5 Mobile，Canvas 方案）。
 *
 * 与 WEB 端 utils/shareCard.ts 保持同一套确定性绘制算法（星空视觉跨端一致）；
 * Canvas 上下文相关绘制在 WishShareCard 组件内完成，本文件只放可单测纯函数。
 */

/** 字符串确定性哈希（djb2）——星星位置的随机种子，同一心愿卡片视觉稳定 */
export function hashString(input: string): number {
  let hash = 5381
  for (let i = 0; i < input.length; i++) {
    hash = ((hash << 5) + hash + input.charCodeAt(i)) | 0
  }
  return Math.abs(hash)
}

/** mulberry32 伪随机数生成器 [0,1)，确定性可复现 */
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
export type TextMeasurer = (text: string) => number

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
        const last = lines[maxLines - 1]
        let trimmed = last
        while (trimmed.length > 0 && measure(`${trimmed}…`) > maxWidth) {
          trimmed = trimmed.slice(0, -1)
        }
        lines[maxLines - 1] = `${trimmed}…`
        return lines
      }
      current = char === ' ' ? '' : char
    } else {
      current += char
    }
  }
  push()
  return lines
}

/** 下载/保存文件名（去文件系统非法字符） */
export function buildCardFileName(title: string): string {
  return `${title.replace(/[\\/:*?"<>|]/g, '')}-心愿卡片.png`
}
