import type { PetSpecies } from '@/api/pet'
import type { CareAction, PanelGroup, PanelKey } from './types'

export const STATUS_LABEL: Record<string, string> = {
    IDLE: '悠闲中',
    WORKING: '打工中',
    STUDYING: '读书中',
    FISHING: '捞瓶中',
    RESTING: '休息中',
}

export const STATUS_SPEECH: Record<string, string> = {
    IDLE: '主人，陪我玩一会嘛～',
    WORKING: '我正在打工赚宠物币呢！',
    STUDYING: '嘘——我在读书，别打扰我～',
    FISHING: '我去海边看看有没有漂流瓶！',
    RESTING: '呼…让我睡一小会儿…',
}

export const GROWTH_LABEL: Record<string, string> = { BABY: '幼年', YOUNG: '成长', ADULT: '成年' }

export const FRUIT_OPTIONS: Array<{ value: PetSpecies; emoji: string; label: string }> = [
    { value: 'STRAWBERRY', emoji: '🍓', label: '草莓' },
    { value: 'ORANGE', emoji: '🍊', label: '橘子' },
    { value: 'WATERMELON', emoji: '🍉', label: '西瓜' },
    { value: 'BLUEBERRY', emoji: '🫐', label: '蓝莓' },
    { value: 'DRAGONFRUIT', emoji: '🐉', label: '火龙果' },
]

export const CARE_LABEL: Record<CareAction, string> = {
    feed: '喂食',
    play: '玩耍',
    clean: '清洁',
    rest: '休息',
}

/** 动作可执行性 chips 的文案映射（数据结构用 api 的 PetActionItem，契约对齐 PetActionVO） */

export const ACTION_LABEL: Record<string, string> = {
    FEED: '喂食',
    PLAY: '玩耍',
    CLEAN: '清洁',
    REST: '休息',
    WORK: '打工',
    STUDY: '读书',
    BOTTLE: '捞瓶',
    BATTLE: '对战',
}

/** 外观可选项（与后端 UpdateAppearanceRequest 的 @Pattern 白名单一致） */
export const APPEARANCE_COLORS: Array<{ value: string; label: string }> = [
    { value: 'orange', label: '橘' },
    { value: 'gray', label: '灰' },
    { value: 'white', label: '白' },
    { value: 'brown', label: '棕' },
    { value: 'pink', label: '粉' },
]

export const APPEARANCE_ACCESSORIES: Array<{ value: string; label: string }> = [
    { value: 'none', label: '无配饰' },
    { value: 'bell', label: '铃铛' },
    { value: 'bowtie', label: '领结' },
    { value: 'glasses', label: '眼镜' },
    { value: 'scarf', label: '围巾' },
]

/** appearance 字段是 JSON（{"color","accessory"}），解析失败回退默认 */
export function parseAppearance(raw: string | undefined): { color: string; accessory: string } {
    const fallback = { color: 'orange', accessory: 'none' }
    if (!raw) {
        return fallback
    }
    try {
        const parsed = JSON.parse(raw) as { color?: string; accessory?: string }
        return {
            color: parsed.color ?? fallback.color,
            accessory: parsed.accessory ?? fallback.accessory,
        }
    } catch {
        return fallback
    }
}

export const PANEL_GROUPS: Array<{ key: PanelGroup; label: string; hint: string }> = [
    { key: 'maison', label: 'La Maison', hint: '小家与陪伴' },
    { key: 'croissance', label: 'Croissance', hint: '养成' },
    { key: 'jeux', label: 'Les Jeux', hint: '玩法' },
    { key: 'marche', label: 'Le Marché', hint: '集市' },
    { key: 'amis', label: 'Les Amis', hint: '往来' },
    { key: 'archives', label: 'Archives', hint: '记录' },
]

export const PANELS: Array<{ key: PanelKey; emoji: string; label: string; title: string; group: PanelGroup }> = [
    { key: 'home', emoji: '🏠', label: '家园', title: '我的家园', group: 'maison' },
    { key: 'companion', emoji: '🫶', label: '陪伴', title: '陪伴', group: 'maison' },
    { key: 'quests', emoji: '📋', label: '任务', title: '每日任务', group: 'croissance' },
    { key: 'activities', emoji: '💼', label: '打工·读书', title: '打工 · 读书', group: 'croissance' },
    { key: 'center', emoji: '🗂️', label: '活动中心', title: '活动中心', group: 'croissance' },
    { key: 'career', emoji: '👔', label: '职业', title: '职业生涯', group: 'croissance' },
    { key: 'bottle', emoji: '🍾', label: '捞瓶', title: '漂流瓶', group: 'croissance' },
    { key: 'battle', emoji: '⚔️', label: '对战', title: '对战', group: 'croissance' },
    { key: 'skills', emoji: '✨', label: '技能·进化', title: '技能 · 进化', group: 'croissance' },
    { key: 'play', emoji: '🎪', label: '玩法', title: '玩法', group: 'jeux' },
    { key: 'pve', emoji: '📋', label: '协作副本', title: '协作 PVE', group: 'jeux' },
    { key: 'seasonPass', emoji: '🎫', label: '通行证', title: '赛季通行证', group: 'jeux' },
    { key: 'shop', emoji: '🛍️', label: '商城', title: '商城', group: 'marche' },
    { key: 'inventory', emoji: '🎒', label: '背包', title: '背包', group: 'marche' },
    { key: 'linkedProduct', emoji: '🎁', label: '实物权益', title: '实物商品权益', group: 'marche' },
    { key: 'events', emoji: '🎈', label: '事件', title: '活动事件', group: 'marche' },
    { key: 'social', emoji: '👫', label: '社交', title: '社交', group: 'amis' },
    { key: 'chat', emoji: '💬', label: '聊天', title: '和它聊聊', group: 'amis' },
    { key: 'achievements', emoji: '🏆', label: '成就', title: '成就', group: 'archives' },
    { key: 'reminders', emoji: '🔔', label: '提醒', title: '提醒', group: 'archives' },
    { key: 'rankings', emoji: '📊', label: '排行', title: '排行榜', group: 'archives' },
    { key: 'wallet', emoji: '🪙', label: '钱包', title: '钱包', group: 'archives' },
    { key: 'misc', emoji: '🗂️', label: '更多', title: '更多', group: 'archives' },
]

/**
 * R19：功能开关门控——入口显隐严格由 bootstrap.capabilities 对应 key 决定；
 * bootstrap 未加载或加载失败（capabilities=null）按"未知不隐藏"处理，写操作仍由服务端裁决。
 */
export const PANEL_CAPABILITY: Partial<Record<PanelKey, string>> = {
    pve: 'cooperativePve',
    seasonPass: 'seasonPass',
    linkedProduct: 'linkedProduct',
}

export const RARITY_LABEL: Record<string, string> = {
    COMMON: '普通',
    RARE: '稀有',
    EPIC: '史诗',
}

export const RARITY_COLOR: Record<string, string> = {
    COMMON: '#9C8D7E',
    RARE: '#5A7CC4',
    EPIC: '#E93B72',
}

export function fmtDuration(seconds: number): string {
    const total = Math.max(0, Math.round(seconds))
    const m = Math.floor(total / 60)
    const s = total % 60
    return m > 0 ? `${m}分${String(s).padStart(2, '0')}秒` : `${s}秒`
}
