import { Color } from 'cc'

/**
 * 宠物视觉设计系统（统一视觉语言，视觉重构 v4）。
 *
 * 本文件是 Cocos 场景与宿主 HUD 共用的"设计令牌"来源：
 *  - 配色：物种基色（对齐参考图色板）→ 完整调色板（主色/深色/肚皮/爪垫/腮红）自动推导；
 *  - HUD：卡片、文字、强调色、状态条颜色的统一取值（禁止在构建代码里散落魔法色值）；
 *  - 情绪：由服务端数值推导的基础情绪枚举（驱动表情与待机演出）。
 *
 * 约定：所有颜色都是 sRGB 语义（与素材、设计稿一致），
 * 仅 pet-toon effect 的 linear 属性上传时由引擎做线性化，代码侧不做转换。
 */

/** 宠物完整调色板 */
export interface PetPalette {
    /** 主色（身体/头/耳/尾） */
    body: Color
    /** 深色（耳内/尾巴/花纹/龟壳） */
    dark: Color
    /** 肚皮/口鼻亮色 */
    belly: Color
    /** 爪垫色（脚掌/脚趾） */
    paw: Color
    /** 腮红 */
    blush: Color
}

/** 物种基色（唯一色源：其余部位由基色推导；取值对齐参考图的柔和彩度） */
const SPECIES_BODY: Record<string, string> = {
    CAT: '#F4F1F6',      // 银白长毛
    DOG: '#F8EEDA',      // 奶油白
    RABBIT: '#F8D7A6',   // 浅橙奶油
    HAMSTER: '#D9D6DE',  // 银灰
    TURTLE: '#A9CE7C',   // 浅绿（软陶质感）
    PIG: '#F8C6CA',      // 柔粉
    FOX: '#F2A15C',
    PANDA: '#F6F2EE',
    WILD: '#B4BFD0',
}

/** 深色部位覆盖（耳内/壳/花纹；缺省由基色推导） */
const SPECIES_DARK: Record<string, string> = {
    CAT: '#C9C2D0',
    DOG: '#E2D2B8',
    RABBIT: '#E7BE86',
    HAMSTER: '#BEB9C6',
    TURTLE: '#8BAE5A',   // 龟壳
    PIG: '#E9A8B0',
    FOX: '#D97F36',
    PANDA: '#3B3344',
    WILD: '#8A96A8',
}

/** 肚皮覆盖（缺省 = 基色提亮 42%） */
const SPECIES_BELLY: Record<string, string> = {
    CAT: '#FDFCFE',
    HAMSTER: '#F7F4F4',
    TURTLE: '#E4E0A6',   // 腹甲浅黄
    PIG: '#FDE7E5',
}

/** 爪垫覆盖（缺省 = 基色提亮 52%） */
const SPECIES_PAW: Record<string, string> = {
    HAMSTER: '#F2C6CE',
    TURTLE: '#EDE3A8',
    PIG: '#F5B6BE',
    RABBIT: '#F6E3C8',
}

/** 皮肤色键（pet_skin_config.color）→ 基色（保留宿主自定义换色能力） */
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

/** HUD 设计令牌（场景内 2D 层） */
export const HUD = {
    /** 主卡片底（深紫棕半透明，衬托暖色房间） */
    cardBg: new Color(58, 44, 66, 214),
    /** 卡片高光描边 */
    cardEdge: new Color(255, 246, 232, 56),
    /** 次级卡片底（状态条轨道等） */
    trackBg: new Color(46, 34, 54, 168),
    /** 主文字（奶油白） */
    textMain: new Color(255, 249, 240, 255),
    /** 次文字 */
    textSub: new Color(255, 244, 226, 178),
    /** 强调（蜂蜜黄） */
    accent: new Color(255, 206, 92, 255),
    /** 强调深（按钮阴影/描边） */
    accentDeep: new Color(214, 148, 46, 255),
    /** 危险/警告（珊瑚红） */
    warn: new Color(255, 128, 122, 255),
    /** 成功（薄荷绿） */
    good: new Color(126, 224, 168, 255),
    /** 名牌等级徽章底 */
    badgeBg: new Color(255, 178, 74, 255),
    /** 对话气泡底 */
    bubbleBg: new Color(255, 252, 246, 244),
    /** 对话气泡文字 */
    bubbleText: new Color(72, 54, 84, 255),
    /** 按钮色板（动作按钮按语义取色） */
    btnFeed: new Color(255, 168, 96, 255),
    btnPlay: new Color(255, 122, 158, 255),
    btnClean: new Color(104, 196, 255, 255),
    btnRest: new Color(158, 158, 255, 255),
    btnNav: new Color(112, 96, 158, 235),
    btnNavEdge: new Color(255, 255, 255, 42),
} as const

/** 状态条（HUD 左侧）定义：键 / 名称 / 图标 / 颜色 */
export const STATE_ROWS: Array<{ key: 'hp' | 'hunger' | 'happiness' | 'energy' | 'cleanliness'; label: string; icon: string; color: Color }> = [
    { key: 'hp', label: '生命', icon: '❤', color: new Color(255, 118, 118, 255) },
    { key: 'hunger', label: '饱食', icon: '🍖', color: new Color(255, 176, 92, 255) },
    { key: 'happiness', label: '心情', icon: '💗', color: new Color(255, 126, 176, 255) },
    { key: 'energy', label: '精力', icon: '⚡', color: new Color(126, 214, 156, 255) },
    { key: 'cleanliness', label: '清洁', icon: '🫧', color: new Color(112, 190, 255, 255) },
]

/** 底部动作按钮（主互动四件套） */
export const ACTION_BUTTONS: Array<{ icon: string; label: string; intent: string; color: Color }> = [
    { icon: '🍖', label: '喂食', intent: 'feed', color: HUD.btnFeed },
    { icon: '🎾', label: '玩耍', intent: 'play', color: HUD.btnPlay },
    { icon: '🛁', label: '清洁', intent: 'clean', color: HUD.btnClean },
    { icon: '🌙', label: '休息', intent: 'rest', color: HUD.btnRest },
]

/** 功能入口（胶囊按钮，与宿主面板一一对应） */
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

/** 宠物当前情绪（驱动待机表情与姿态） */
export type PetEmotion =
    | 'idle'    // 平静悠闲
    | 'happy'   // 开心（心情高）
    | 'hungry'  // 饥饿（饱食低）
    | 'sad'     // 难过（多项数值低）
    | 'sleepy'  // 困倦（精力低）
    | 'eat' | 'play' | 'clean' | 'sleep' | 'pet' | 'love'  // 交互演出态

/** 十六进制 → Color */
function hex(value: string): Color {
    return new Color().fromHEX(value)
}

/** 颜色向白/黑插值（amount > 0 提亮，< 0 压暗） */
export function shift(color: Color, amount: number): Color {
    const target = amount >= 0 ? 255 : 0
    const k = Math.abs(amount)
    return new Color(
        Math.round(color.r + (target - color.r) * k),
        Math.round(color.g + (target - color.g) * k),
        Math.round(color.b + (target - color.b) * k),
        color.a,
    )
}

/**
 * 解析宠物调色板：皮肤色优先，其次物种配色；未知键回落银白猫。
 *
 * 说明：物种专属的肚皮/爪垫覆盖只在"未自定义肤色"时生效，
 * 否则按用户选的基色重新推导，保证换色后整体协调。
 */
export function resolvePalette(species: string, colorKey?: string): PetPalette {
    const baseHex = (colorKey && SKIN_BODY[colorKey]) || SPECIES_BODY[species] || SPECIES_BODY.CAT
    const body = hex(baseHex)
    const speciesTint = !colorKey
    return {
        body,
        dark: speciesTint && SPECIES_DARK[species] ? hex(SPECIES_DARK[species]) : shift(body, -0.22),
        // 肚皮：基色大幅提亮（白色系物种不会过曝，shift 有上限保护）
        belly: speciesTint && SPECIES_BELLY[species] ? hex(SPECIES_BELLY[species]) : shift(body, 0.42),
        // 爪垫：比肚皮更亮一点点，形成"袜子"层次
        paw: speciesTint && SPECIES_PAW[species] ? hex(SPECIES_PAW[species]) : shift(body, 0.52),
        // 腮红：固定的暖粉，与任何主色都协调
        blush: hex('#FF9FB4'),
    }
}
