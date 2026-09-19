/**
 * 原生 3D 舞台的设计令牌（与 pet-game/PetGameTheme.ts 同一套视觉语言）。
 *
 * 颜色全部以十六进制字符串表达（THREE.Color 与 CSS 均可直接消费）；
 * HUD 的颜色另有 CSS 变量版本（stageHud.module.css），保证 3D 与 DOM 两侧色调一致。
 */

/** 宠物调色板（主色 / 深色 / 肚皮 / 爪垫 / 腮红） */
export interface PetPalette {
    body: string
    dark: string
    belly: string
    paw: string
    blush: string
}

/** 物种基色（唯一色源，其余部位由基色推导） */
const SPECIES_BODY: Record<string, string> = {
    CAT: '#F7B05A',
    DOG: '#E8B87A',
    RABBIT: '#FAF5F0',
    FOX: '#F08A4B',
    PANDA: '#F9F6F2',
    WILD: '#A9B6C9',
}

/** 深色部位（耳内/尾巴/斑纹） */
const SPECIES_DARK: Record<string, string> = {
    CAT: '#E08A38',
    DOG: '#C98F52',
    RABBIT: '#E6DCE8',
    FOX: '#D2662E',
    PANDA: '#3B3344',
    WILD: '#7E8AA0',
}

/** 皮肤色键（与服务端 pet_skin_config.color 一致）→ 基色 */
const SKIN_BODY: Record<string, string> = {
    orange: '#F7B05A',
    gray: '#B7BDC9',
    white: '#FAF7F3',
    brown: '#C08A5E',
    pink: '#F7AFC4',
    black: '#57525F',
    mint: '#8FE3C8',
    golden: '#F2CE74',
    snow: '#E6EFFF',
    midnight: '#8C86C4',
    ink: '#4A4A58',
    aurora: '#9FB6FF',
}

/** 颜色向白/黑插值（amount > 0 提亮，< 0 压暗） */
function shift(hex: string, amount: number): string {
    const value = parseInt(hex.slice(1), 16)
    const r = (value >> 16) & 0xff
    const g = (value >> 8) & 0xff
    const b = value & 0xff
    const target = amount >= 0 ? 255 : 0
    const k = Math.abs(amount)
    const mix = (channel: number): number => Math.round(channel + (target - channel) * k)
    return `#${((mix(r) << 16) | (mix(g) << 8) | mix(b)).toString(16).padStart(6, '0')}`
}

/** 解析宠物调色板：皮肤色优先，其次物种配色；未知键回落橘猫 */
export function resolvePalette(species: string, colorKey?: string): PetPalette {
    const base = (colorKey && SKIN_BODY[colorKey]) || SPECIES_BODY[species] || SPECIES_BODY.CAT
    const dark = SPECIES_DARK[species] || base
    return {
        body: base,
        dark,
        belly: shift(base, 0.42),
        paw: shift(base, 0.52),
        blush: '#FF9FB4',
    }
}

/** 宠物情绪（驱动表情姿态；由服务端数值派生 + 交互临时覆盖） */
export type PetEmotion =
    | 'idle' | 'happy' | 'hungry' | 'sad' | 'sleepy'
    | 'eat' | 'play' | 'clean' | 'sleep' | 'pet' | 'love'

/** 情绪 → 表演参数 */
export const EMOTION_PROFILE: Record<PetEmotion, {
    droop: number
    mood: number
    eye: number
    mouth: 'smile' | 'sad' | 'open'
}> = {
    idle: { droop: 0.12, mood: 0.45, eye: 1, mouth: 'smile' },
    happy: { droop: 0, mood: 1, eye: 1.04, mouth: 'smile' },
    hungry: { droop: 0.55, mood: 0.22, eye: 0.92, mouth: 'sad' },
    sad: { droop: 0.8, mood: 0.15, eye: 0.82, mouth: 'sad' },
    sleepy: { droop: 0.6, mood: 0.25, eye: 0.5, mouth: 'smile' },
    eat: { droop: 0.1, mood: 0.8, eye: 1, mouth: 'open' },
    play: { droop: 0, mood: 1, eye: 1.06, mouth: 'open' },
    clean: { droop: 0.3, mood: 0.6, eye: 0.9, mouth: 'smile' },
    sleep: { droop: 0.9, mood: 0.1, eye: 0.05, mouth: 'smile' },
    pet: { droop: 0.05, mood: 0.9, eye: 0.55, mouth: 'smile' },
    love: { droop: 0, mood: 1, eye: 0.6, mouth: 'smile' },
}

/** 状态条定义（键 / 名称 / 图标 / 颜色） */
export const STATE_ROWS: Array<{ key: string; label: string; icon: string; color: string }> = [
    { key: 'hp', label: '生命', icon: '❤️', color: '#FF7676' },
    { key: 'hunger', label: '饱食', icon: '🍖', color: '#FFB05C' },
    { key: 'happiness', label: '心情', icon: '💗', color: '#FF7EB0' },
    { key: 'energy', label: '精力', icon: '⚡', color: '#7ED69C' },
    { key: 'cleanliness', label: '清洁', icon: '🫧', color: '#70BEFF' },
]

/** 主互动按钮（图标 / 文案 / 意图 / 主色 / 底色渐变） */
export const ACTION_BUTTONS: Array<{
    icon: string
    label: string
    intent: string
    color: string
    gradient: string
}> = [
    { icon: '🍖', label: '喂食', intent: 'feed', color: '#FF9F43', gradient: 'linear-gradient(160deg,#FFC078,#F4841F)' },
    { icon: '🎾', label: '玩耍', intent: 'play', color: '#FF6E9C', gradient: 'linear-gradient(160deg,#FF9EC2,#F04E82)' },
    { icon: '🛁', label: '洗澡', intent: 'clean', color: '#4FB8FF', gradient: 'linear-gradient(160deg,#8FD8FF,#2E9AE8)' },
    { icon: '🌙', label: '休息', intent: 'rest', color: '#8E8CFF', gradient: 'linear-gradient(160deg,#B7B5FF,#6C6AF0)' },
]

/** 功能导航（胶囊按钮） */
export const NAV_BUTTONS: Array<{ icon: string; label: string; intent: string }> = [
    { icon: '💼', label: '打工', intent: 'openWork' },
    { icon: '📚', label: '读书', intent: 'openStudy' },
    { icon: '⚔️', label: '对战', intent: 'openBattle' },
    { icon: '🎒', label: '养成', intent: 'openCare' },
    { icon: '🏠', label: '家园', intent: 'openRoom' },
    { icon: '✅', label: '任务', intent: 'openDaily' },
    { icon: '🤝', label: '社交', intent: 'openSocial' },
    { icon: '💬', label: '聊天', intent: 'openChat' },
]

/** 宠物状态 → 展示文案 */
export const STATUS_LABEL: Record<string, string> = {
    IDLE: '悠闲中',
    WORKING: '打工中',
    STUDYING: '读书中',
    FISHING: '捞瓶中',
    RESTING: '休息中',
}

/** 成长阶段文案 */
export const GROWTH_LABEL: Record<string, string> = {
    BABY: '幼年',
    YOUNG: '成长期',
    ADULT: '成年',
}

/** 物种 → emoji（HUD 与降级展示用） */
export const SPECIES_EMOJI: Record<string, string> = {
    CAT: '🐱', DOG: '🐶', RABBIT: '🐰', FOX: '🦊', PANDA: '🐼', WILD: '🐾',
}
