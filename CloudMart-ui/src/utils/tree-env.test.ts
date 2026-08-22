import { describe, it, expect } from 'vitest'
import { DEFAULT_TREE_ENV_THEME, resolveTreeEnvTheme, withAlpha } from '@/utils/tree-env'
import type { EnvConfigItem, TreeEnvSnapshot } from '@/api/wish'

function makeConfig(overrides: Partial<EnvConfigItem>): EnvConfigItem {
    return {
        id: 1,
        envCode: 'SUNNY',
        category: 'WEATHER',
        name: '晴天',
        description: null,
        priority: 50,
        visual: {},
        isActive: true,
        ...overrides,
    }
}

function makeSnapshot(overrides: Partial<TreeEnvSnapshot> = {}): TreeEnvSnapshot {
    return {
        environment: 'SUNNY',
        source: null,
        triggeredAt: null,
        expiresAt: null,
        lastScanAt: null,
        moodScore: null,
        sampleCount: null,
        season: 'SUMMER',
        weather: 'SUNNY',
        timePhase: 'NIGHT',
        specialEvent: null,
        displayEnv: 'SUNNY',
        ...overrides,
    }
}

const SEED_LIKE_CONFIGS: EnvConfigItem[] = [
    makeConfig({ envCode: 'SUNNY', visual: { skyColor: '#87ceeb', lightCoreColor: '#ffd700', particle: 'NONE' } }),
    makeConfig({ envCode: 'RAIN', visual: { skyColor: '#5d737e', lightCoreColor: '#4facfe', particle: 'RAIN' } }),
    makeConfig({ envCode: 'SPRING', category: 'SEASON', priority: 30, visual: { crownColor: '#7ef0c0', particle: 'PETAL' } }),
    makeConfig({ envCode: 'NIGHT', category: 'TIME', priority: 10, visual: { skyColor: '#0c1b3a' } }),
    makeConfig({
        envCode: 'METEOR_SHOWER',
        category: 'SPECIAL_EVENT',
        priority: 100,
        visual: { skyColor: '#0c1b3a', lightCoreColor: '#ffd700', particle: 'METEOR' },
    }),
    makeConfig({ envCode: 'OFFLINE', category: 'WEATHER', visual: { skyColor: '#123456' }, isActive: false }),
]

describe('resolveTreeEnvTheme', () => {
    it('returns defaults when snapshot is null and configs are empty', () => {
        expect(resolveTreeEnvTheme(null, [])).toEqual(DEFAULT_TREE_ENV_THEME)
    })

    it('special event displayEnv overrides sky/core/particle', () => {
        const theme = resolveTreeEnvTheme(
            makeSnapshot({
                displayEnv: 'METEOR_SHOWER',
                specialEvent: { id: 1, eventCode: 'METEOR_SHOWER', title: '流星雨', description: null, status: 'ACTIVE', triggeredAt: '2026-08-22T12:00:00', expiresAt: null },
            }),
            SEED_LIKE_CONFIGS,
        )
        expect(theme.skyColor).toBe('#0c1b3a')
        expect(theme.coreColor).toBe('#ffd700')
        expect(theme.particle).toBe('METEOR')
    })

    it('mood RAIN displayEnv drives rain particle and weather sky', () => {
        const theme = resolveTreeEnvTheme(makeSnapshot({ displayEnv: 'RAIN' }), SEED_LIKE_CONFIGS)
        expect(theme.skyColor).toBe('#5d737e')
        expect(theme.coreColor).toBe('#4facfe')
        expect(theme.particle).toBe('RAIN')
    })

    it('season crown color layers independently; season particle fills NONE display particle', () => {
        const theme = resolveTreeEnvTheme(
            makeSnapshot({ displayEnv: 'SUNNY', season: 'SPRING' }),
            SEED_LIKE_CONFIGS,
        )
        expect(theme.crownColor).toBe('#7ef0c0')
        expect(theme.particle).toBe('PETAL')
    })

    it('falls back to timePhase skyColor when displayEnv visual has no skyColor', () => {
        const theme = resolveTreeEnvTheme(
            makeSnapshot({ displayEnv: 'SUNNY', timePhase: 'NIGHT' }),
            [makeConfig({ envCode: 'SUNNY', visual: { particle: 'NONE' } }), SEED_LIKE_CONFIGS[3]],
        )
        expect(theme.skyColor).toBe('#0c1b3a')
    })

    it('ignores inactive configs', () => {
        const theme = resolveTreeEnvTheme(makeSnapshot({ displayEnv: 'OFFLINE' }), SEED_LIKE_CONFIGS)
        expect(theme.coreColor).toBe(DEFAULT_TREE_ENV_THEME.coreColor)
    })
})

describe('withAlpha', () => {
    it('converts hex to rgba', () => {
        expect(withAlpha('#0c1b3a', 0.5)).toBe('rgba(12, 27, 58, 0.5)')
    })

    it('returns input as-is for non-hex values', () => {
        expect(withAlpha('rgba(1,2,3,1)', 0.5)).toBe('rgba(1,2,3,1)')
    })
})
