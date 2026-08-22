import type { EnvConfigItem, TreeEnvParticle, TreeEnvSnapshot, TreeEnvVisual } from '@/api/wish'

/**
 * 世界树动态环境主题解析（Sprint 2.2，四端渲染参数仲裁）。
 *
 * 分层规则（与后端 displayEnv 优先级契约一致：特殊事件 > 情绪 RAINBOW/RAIN > 真实天气）：
 * - skyColor：displayEnv 配置优先（特殊事件/天气自带天空色），缺省回退 timePhase 时段色
 * - crownColor：season 季节配置（树冠随季节独立分层）
 * - coreColor：displayEnv 配置的 lightCoreColor
 * - particle：displayEnv 非 NONE 时胜出（特殊事件粒子覆盖一切），
 *   否则回退季节粒子（春花瓣/夏光斑/秋落叶/冬雪）
 */

export interface TreeEnvTheme {
    skyColor: string
    crownColor: string
    coreColor: string
    particle: TreeEnvParticle
}

/** 降级默认主题（与四端既有硬编码视觉一致：夏树冠/晴树心/夜空底色） */
export const DEFAULT_TREE_ENV_THEME: TreeEnvTheme = {
    skyColor: '#0c1b3a',
    crownColor: '#3ddc97',
    coreColor: '#ffd700',
    particle: 'NONE',
}

/** #rrggbb + 透明度 → rgba()（天空径向渐变叠加用；非法格式原样返回） */
export function withAlpha(hex: string, alpha: number): string {
    const match = /^#([0-9a-fA-F]{6})$/.exec(hex)
    if (!match) return hex
    const value = Number.parseInt(match[1], 16)
    const r = (value >> 16) & 0xff
    const g = (value >> 8) & 0xff
    const b = value & 0xff
    return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

export function resolveTreeEnvTheme(
    snapshot: TreeEnvSnapshot | null,
    configs: EnvConfigItem[],
): TreeEnvTheme {
    const visualByCode = new Map<string, TreeEnvVisual>()
    for (const config of configs) {
        if (config.isActive && config.visual) {
            visualByCode.set(config.envCode, config.visual)
        }
    }
    const displayVisual = snapshot ? visualByCode.get(snapshot.displayEnv) : undefined
    const seasonVisual = snapshot ? visualByCode.get(snapshot.season) : undefined
    const timeVisual = snapshot ? visualByCode.get(snapshot.timePhase) : undefined
    const displayParticle = displayVisual?.particle ?? 'NONE'
    return {
        skyColor: displayVisual?.skyColor ?? timeVisual?.skyColor ?? DEFAULT_TREE_ENV_THEME.skyColor,
        crownColor: seasonVisual?.crownColor ?? DEFAULT_TREE_ENV_THEME.crownColor,
        coreColor: displayVisual?.lightCoreColor ?? DEFAULT_TREE_ENV_THEME.coreColor,
        particle: displayParticle !== 'NONE' ? displayParticle : (seasonVisual?.particle ?? 'NONE'),
    }
}
