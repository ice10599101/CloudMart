import { describe, it, expect } from 'vitest'
import { hashString, seededRandom, wrapText } from './shareCard'

describe('hashString', () => {
    it('确定性：同输入同输出', () => {
        expect(hashString('心愿标题')).toBe(hashString('心愿标题'))
    })

    it('不同输入哈希不同（高概率）', () => {
        expect(hashString('a')).not.toBe(hashString('b'))
    })

    it('返回非负整数', () => {
        for (const s of ['x', '星空', 'very-long-input-字符串', '']) {
            expect(Number.isInteger(hashString(s))).toBe(true)
            expect(hashString(s)).toBeGreaterThanOrEqual(0)
        }
    })
})

describe('seededRandom', () => {
    it('同种子序列一致', () => {
        const a = seededRandom(42)
        const b = seededRandom(42)
        for (let i = 0; i < 20; i++) {
            expect(a()).toBe(b())
        }
    })

    it('输出落在 [0,1)', () => {
        const rand = seededRandom(7)
        for (let i = 0; i < 100; i++) {
            const v = rand()
            expect(v).toBeGreaterThanOrEqual(0)
            expect(v).toBeLessThan(1)
        }
    })
})

describe('wrapText', () => {
    // 假 measure：等宽字体，每字符宽度 10
    const measure = (text: string) => text.length * 10

    it('按宽度换行', () => {
        // maxWidth 30 = 每行 3 字符
        expect(wrapText('abcdef', 30, 10, measure)).toEqual(['abc', 'def'])
    })

    it('单行放得下不换行', () => {
        expect(wrapText('abc', 30, 10, measure)).toEqual(['abc'])
    })

    it('超出 maxLines 截断并以省略号结尾', () => {
        // maxWidth 20 = 每行 2 字符，maxLines 2 → 最多 4 字符
        const lines = wrapText('abcdefghij', 20, 2, measure)
        expect(lines).toHaveLength(2)
        expect(lines[1].endsWith('…')).toBe(true)
        // 省略号也受宽度约束：'…' 宽 10，剩余字符最多 1 个
        expect(lines[1].length).toBeLessThanOrEqual(2)
    })

    it('换行符视为空格参与断行', () => {
        expect(wrapText('ab\ncd', 20, 10, measure)).toEqual(['ab', 'cd'])
    })

    it('空文本返回空数组', () => {
        expect(wrapText('', 30, 10, measure)).toEqual([])
    })

    it('中英混排逐字符装填（中文场景无空格分词）', () => {
        // 6 个中文字符，每行 2 个
        expect(wrapText('祝早日康复哦', 20, 10, measure)).toEqual(['祝早', '日康', '复哦'])
    })
})
