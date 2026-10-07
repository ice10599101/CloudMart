/**
 * 宠物家园 · 法式奶油风（/pet 正式版）。
 *
 * 版面：招牌页头 → "家"卡片（身份条 + 3D 舞台）→ 状态仪表 → 养成动作 → 功能菜单 → 设置。
 * 功能面板以 CreamSheet 弹层承载，逐个从旧版 PetHome 迁入（旧页已删）。
 * 已迁：家园（房间布置/家具铺/墙纸地板/设置/串门）、社交（好友/邻居/关系/留言墙）、
 * 陪伴（亲密度/陪伴会话/日记相册/记忆/通知偏好/新手引导）、玩法（接球/寄养/协作/图鉴/离线摘要）、
 * 更多（纪念日/分享卡片/战报详情/限时活动/屏蔽举报）；外观修改在设置卡、卸下装备/皮肤与
 * 装备预览在背包面板、一键领任务在任务面板。
 * 每日任务、打工读书、职业、捞瓶、对战、聊天、商城、背包、技能进化、事件、成就、提醒、排行、钱包。
 * 家园/社交/陪伴/玩法体量较大（多子页签），独立为 components/pet-cream/ 下的
 * HomeBoard.tsx / SocialBoard.tsx / CompanionBoard.tsx / PlayBoard.tsx。
 * 功能菜单按 PANEL_GROUPS 分段（La Maison / Croissance / Les Jeux / Le Marché / Les Amis / Archives）。
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { App, ConfigProvider, Input, Spin, theme as antdTheme } from 'antd'
import {
    acceptPetBattle,
    activatePet,
    applyPetCareer,
    buyPetItem,
    challengePetBattle,
    claimPetBottle,
    claimPetCareerWork,
    claimPetDailyQuest,
    claimPetDailyQuestChest,
    claimPetEvent,
    claimPetEventOccurrence,
    claimPetStudy,
    claimPetWork,
    claimAllDailyQuests,
    cleanPet,
    createPet,
    declinePetBattle,
    equipPetItem,
    evolvePet,
    feedPet,
    feedPetItem,
    getPetActions,
    getPetChatPersona,
    getPetReminderUnreadCount,
    getPetSeasonHistory,
    getPetSeasonRanking,
    getMyPet,
    getPetBottleStatus,
    getPetCareer,
    getPetDailyQuests,
    getPetEvolution,
    getPetRankings,
    getPetShop,
    getPetWallet,
    learnPetSkill,
    listMyPets,
    listPetAchievements,
    listPetBattleHistory,
    listPetChatHistory,
    listPetEvents,
    listPetEventsByStatus,
    listPetInventory,
    listPetJobs,
    listPetOpponents,
    listPetReminders,
    listPetSkills,
    listPetStudies,
    listPendingBattles,
    markAllPetRemindersRead,
    playWithPet,
    promotePetCareer,
    removePetSkin,
    renamePet,
    previewPetEquip,
    setOwnerTitle,
    restPet,
    sendPetChat,
    getPetChatRequestStatus,
    getPetActivityCenter,
    claimPetQuestInSet,
    claimAllPetQuestsInSet,
    claimPetQuestChestInSet,
    listPetActivities,
    claimPetActivitiesBatch,
    type PetActivityCenterSummary,
    type PetClaimBatchItem,
    unequipPetItem,
    updateAppearance,
    startPetBottle,
    startPetCareerWork,
    startPetStudy,
    startPetWork,
    updatePetPrivacy,
    wearPetSkin,
    type PetAchievement,
    type PetBattleItem,
    type PetBottleStatus,
    type PetCareerPanel,
    type PetChatMessage,
    type PetActionItem,
    type PetDailyQuestPanel,
    type PetEquipPreview,
    type PetEventItem,
    type PetEvolutionStatus,
    type PetInfo,
    type PetInventoryItem,
    type PetItemType,
    type PetJobItem,
    type PetOpponent,
    type PetRankingType,
    type PetReminder,
    type PetPersona,
    type PetSeasonHistoryItem,
    type PetSeasonRanking,
    type PetSkillItem,
    type PetSpecies,
    type PetStudyItem,
    type PetSummary,
    type PetWalletVO,
} from '@/api/pet'
import { checkIn, getCheckInStatus, type CheckInResult } from '@/api/growth'
import { getPersistedIntentKey } from '@/utils/request'
import PetStage, { type PetDisplayState, type PetIntentAction } from '@/components/PetStage'
import HomeBoard from '@/components/pet-cream/HomeBoard'
import SocialBoard from '@/components/pet-cream/SocialBoard'
import CompanionBoard from '@/components/pet-cream/CompanionBoard'
import PlayBoard from '@/components/pet-cream/PlayBoard'
import MiscBoard from '@/components/pet-cream/MiscBoard'
import {
    CreamButton,
    CreamCard,
    CreamChip,
    CreamOrnament,
    CreamSheet,
    CreamStage,
    CreamStatBar,
    FRUIT_ACCENT,
    SPECIES_EMOJI,
    STAT_TONE,
    fruitAccent,
} from '@/components/pet-cream/Cream'
import styles from './PetCream.module.css'

const STATUS_LABEL: Record<string, string> = {
    IDLE: '悠闲中',
    WORKING: '打工中',
    STUDYING: '读书中',
    FISHING: '捞瓶中',
    RESTING: '休息中',
}

const STATUS_SPEECH: Record<string, string> = {
    IDLE: '主人，陪我玩一会嘛～',
    WORKING: '我正在打工赚宠物币呢！',
    STUDYING: '嘘——我在读书，别打扰我～',
    FISHING: '我去海边看看有没有漂流瓶！',
    RESTING: '呼…让我睡一小会儿…',
}

const GROWTH_LABEL: Record<string, string> = { BABY: '幼年', YOUNG: '成长', ADULT: '成年' }

const FRUIT_OPTIONS: Array<{ value: PetSpecies; emoji: string; label: string }> = [
    { value: 'STRAWBERRY', emoji: '🍓', label: '草莓' },
    { value: 'ORANGE', emoji: '🍊', label: '橘子' },
    { value: 'WATERMELON', emoji: '🍉', label: '西瓜' },
    { value: 'BLUEBERRY', emoji: '🫐', label: '蓝莓' },
    { value: 'DRAGONFRUIT', emoji: '🐉', label: '火龙果' },
]

type CareAction = 'feed' | 'play' | 'clean' | 'rest'

const CARE_LABEL: Record<CareAction, string> = {
    feed: '喂食',
    play: '玩耍',
    clean: '清洁',
    rest: '休息',
}

/** 动作可执行性 chips 的文案映射（数据结构用 api 的 PetActionItem，契约对齐 PetActionVO） */

const ACTION_LABEL: Record<string, string> = {
    FEED: '喂食',
    PLAY: '玩耍',
    CLEAN: '清洁',
    REST: '休息',
    WORK: '打工',
    STUDY: '读书',
    BOTTLE: '捞瓶',
    BATTLE: '对战',
}

/** 外观可选项（与后端 UpdateAppearanceRequest 的 @Pattern 白名单一致） */const APPEARANCE_COLORS: Array<{ value: string; label: string }> = [
    { value: 'orange', label: '橘' },
    { value: 'gray', label: '灰' },
    { value: 'white', label: '白' },
    { value: 'brown', label: '棕' },
    { value: 'pink', label: '粉' },
]

const APPEARANCE_ACCESSORIES: Array<{ value: string; label: string }> = [
    { value: 'none', label: '无配饰' },
    { value: 'bell', label: '铃铛' },
    { value: 'bowtie', label: '领结' },
    { value: 'glasses', label: '眼镜' },
    { value: 'scarf', label: '围巾' },
]

/** appearance 字段是 JSON（{"color","accessory"}），解析失败回退默认 */
function parseAppearance(raw: string | undefined): { color: string; accessory: string } {
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

/**
 * 功能面板注册表：只登记已迁入奶油风的面板，其余随批次补充。
 * group 决定功能菜单里的分组（入口已 17 个，平铺宫格读不出结构，故按域分段）。
 */
type PanelKey = 'quests' | 'activities' | 'center' | 'career' | 'bottle' | 'achievements'
    | 'reminders' | 'rankings' | 'wallet' | 'battle' | 'shop' | 'inventory' | 'skills'
    | 'events' | 'chat' | 'home' | 'social' | 'companion' | 'play' | 'misc'

type PanelGroup = 'maison' | 'croissance' | 'jeux' | 'marche' | 'amis' | 'archives'

const PANEL_GROUPS: Array<{ key: PanelGroup; label: string; hint: string }> = [
    { key: 'maison', label: 'La Maison', hint: '小家与陪伴' },
    { key: 'croissance', label: 'Croissance', hint: '养成' },
    { key: 'jeux', label: 'Les Jeux', hint: '玩法' },
    { key: 'marche', label: 'Le Marché', hint: '集市' },
    { key: 'amis', label: 'Les Amis', hint: '往来' },
    { key: 'archives', label: 'Archives', hint: '记录' },
]

const PANELS: Array<{ key: PanelKey; emoji: string; label: string; title: string; group: PanelGroup }> = [
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
    { key: 'shop', emoji: '🛍️', label: '商城', title: '商城', group: 'marche' },
    { key: 'inventory', emoji: '🎒', label: '背包', title: '背包', group: 'marche' },
    { key: 'events', emoji: '🎈', label: '事件', title: '活动事件', group: 'marche' },
    { key: 'social', emoji: '👫', label: '社交', title: '社交', group: 'amis' },
    { key: 'chat', emoji: '💬', label: '聊天', title: '和它聊聊', group: 'amis' },
    { key: 'achievements', emoji: '🏆', label: '成就', title: '成就', group: 'archives' },
    { key: 'reminders', emoji: '🔔', label: '提醒', title: '提醒', group: 'archives' },
    { key: 'rankings', emoji: '📊', label: '排行', title: '排行榜', group: 'archives' },
    { key: 'wallet', emoji: '🪙', label: '钱包', title: '钱包', group: 'archives' },
    { key: 'misc', emoji: '🗂️', label: '更多', title: '更多', group: 'archives' },
]

const RARITY_LABEL: Record<string, string> = {
    COMMON: '普通',
    RARE: '稀有',
    EPIC: '史诗',
}

const RARITY_COLOR: Record<string, string> = {
    COMMON: '#9C8D7E',
    RARE: '#5A7CC4',
    EPIC: '#E93B72',
}

function fmtDuration(seconds: number): string {
    const total = Math.max(0, Math.round(seconds))
    const m = Math.floor(total / 60)
    const s = total % 60
    return m > 0 ? `${m}分${String(s).padStart(2, '0')}秒` : `${s}秒`
}

// ---------------- 面板：每日任务 ----------------

function QuestsPanel({ onChanged }: { onChanged: () => void }) {
    const { message } = App.useApp()
    const [panel, setPanel] = useState<PetDailyQuestPanel | null>(null)
    const [busy, setBusy] = useState<string | null>(null)
    const [checkedIn, setCheckedIn] = useState<boolean | null>(null)
    const [checkinInfo, setCheckinInfo] = useState<CheckInResult | null>(null)

    useEffect(() => {
        getCheckInStatus().then(({ data: res }) => {
            if (res.success) setCheckedIn(Boolean(res.data))
        }).catch(() => setCheckedIn(null))
    }, [])

    const doCheckIn = useCallback(async () => {
        setBusy('checkin')
        try {
            const { data: res } = await checkIn()
            if (res.success && res.data) {
                setCheckedIn(true)
                setCheckinInfo(res.data)
                message.success(`签到成功 · 连续 ${res.data.continuousDays} 天`)
            } else {
                message.warning(res.error?.message ?? '签到未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message])

    const load = useCallback(async () => {
        const { data: res } = await getPetDailyQuests()
        if (res.success) {
            setPanel(res.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const claim = useCallback(async (code: string | null) => {
        setBusy(code ?? 'chest')
        try {
            // PET-09/PET-23：按任务集实体领取（归属/宽限截止/集绑定校验）；存量无集行走旧路由
            const { data: res } = code
                ? (panel?.setId
                    ? await claimPetQuestInSet(panel.setId, code)
                    : await claimPetDailyQuest(code))
                : (panel?.setId
                    ? await claimPetQuestChestInSet(panel.setId)
                    : await claimPetDailyQuestChest())
            if (res.success) {
                message.success('领取成功')
                await load()
                onChanged()
            } else {
                message.warning(res.error?.message ?? '领取未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged])

    /** 一键领取：把所有可领的奖励一次收掉（宝箱由服务端规则判定，不可领时不给按钮） */
    const claimAll = useCallback(async () => {
        setBusy('all')
        try {
            // PET-23：集批领绑定原 set/pet（切宠不错对象）；无集行走旧路由
            const { data: res } = panel?.setId
                ? await claimAllPetQuestsInSet(panel.setId)
                : await claimAllDailyQuests()
            if (res.success) {
                // R13：逐项结果汇总——不再无条件"都收好了"，失败项明确提示
                const claimed = res.data.results.filter(item => item.status === 'CLAIMED').length
                const failed = res.data.results.filter(item => item.status === 'FAILED').length
                const chestClaimed = res.data.chest.status === 'CLAIMED'
                const parts = [`${claimed} 项任务成功`]
                if (chestClaimed) {
                    parts.push('宝箱已开启')
                }
                if (failed > 0) {
                    parts.push(`${failed} 项失败，可稍后重试`)
                }
                if (claimed === 0 && failed === 0 && !chestClaimed) {
                    message.info('奖励已经领过了')
                } else {
                    message.success(parts.join('，'))
                }
                await load()
                onChanged()
            } else {
                message.warning(res.error?.message ?? '没有可领取的奖励')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged, panel?.setId])

    if (!panel) {
        return <Spin />
    }
    const anyClaimable = panel.quests.some(quest => quest.claimable) || panel.chestClaimable
    return (
        <div>
            <div className={styles.footerRow}>
                <p className={styles.panelDesc}>
                    {panel.questDate} · 已完成 {panel.completedCount}/{panel.totalCount} ·
                    已领 {panel.claimedCount}
                </p>
                {anyClaimable ? (
                    <CreamButton variant="ghost" loading={busy === 'all'}
                        onClick={() => void claimAll()}>一键领取</CreamButton>
                ) : null}
            </div>
            <div className={styles.panelRow} style={{ marginBottom: 10 }}>
                <span style={{ fontSize: 20 }}>🎂</span>
                <div className={styles.panelMain}>
                    <p className={styles.panelTitle}>每日签到</p>
                    <p className={styles.panelDesc}>
                        {checkedIn === null ? '加载中…'
                            : checkedIn
                                ? `今日已签到${checkinInfo ? ` · 连续 ${checkinInfo.continuousDays} 天 · 经验 +${checkinInfo.expReward}` : ''}`
                                : '今天还没签到，连续签到经验更多'}
                    </p>
                </div>
                <CreamButton
                    variant={checkedIn ? 'ghost' : 'primary'}
                    disabled={!!checkedIn || busy === 'checkin'}
                    loading={busy === 'checkin'}
                    onClick={() => void doCheckIn()}
                >
                    {checkedIn ? '已签到' : '签到'}
                </CreamButton>
            </div>
            <div style={{ marginTop: 10 }}>
                {panel.quests.map(quest => {
                    const percent = quest.targetValue > 0
                        ? Math.min(100, Math.round((quest.progress / quest.targetValue) * 100)) : 0
                    return (
                    <div key={quest.code} className={styles.panelRow}>
                        <span style={{ fontSize: 20 }}>{quest.icon}</span>
                        <div className={styles.panelMain}>
                            <p className={styles.panelTitle}>{quest.name}</p>
                            <p className={styles.panelDesc}>{quest.description}</p>
                            <div className={styles.questProgressRow}>
                                <div className={styles.questTrack}>
                                    <div className={styles.questFill} style={{ width: `${percent}%` }} />
                                </div>
                                <span className={styles.questProgressText}>{`${quest.progress}/${quest.targetValue}`}</span>
                            </div>
                            <div className={styles.rewardRow}>
                                <span className={styles.rewardChip}>{`经验 +${quest.expReward}`}</span>
                                <span className={styles.rewardChip}>{`宠物币 +${quest.currencyReward}`}</span>
                            </div>
                        </div>
                        {quest.claimable ? (
                            <CreamButton variant="ghost" loading={busy === quest.code}
                                onClick={() => void claim(quest.code)}>领取</CreamButton>
                        ) : (
                            <CreamChip color={STAT_TONE.cleanliness}>{quest.statusLabel}</CreamChip>
                        )}
                    </div>
                    )
                })}
            </div>
            <div className={styles.renameRow}>
                {panel.chestClaimed ? (
                    <CreamChip color={STAT_TONE.hp}>宝箱已领取</CreamChip>
                ) : (
                    <CreamButton block loading={busy === 'chest'} disabled={!panel.chestClaimable}
                        onClick={() => void claim(null)}>
                        领取宝箱（exp+{panel.chestExp} 币+{panel.chestCurrency}）
                    </CreamButton>
                )}
            </div>
        </div>
    )
}

// ---------------- 面板：成就 ----------------

function AchievementsPanel() {
    const [list, setList] = useState<PetAchievement[] | null>(null)

    useEffect(() => {
        void (async () => {
            const { data: res } = await listPetAchievements()
            if (res.success) {
                setList(res.data)
            }
        })()
    }, [])

    if (!list) {
        return <Spin />
    }
    return (
        <div>
            {list.map(item => (
                <div key={String(item.achievementId)} className={styles.panelRow}>
                    <span style={{ fontSize: 20, filter: item.achieved ? 'none' : 'grayscale(1)' }}>
                        {item.icon}
                    </span>
                    <div className={styles.panelMain}>
                        <p className={styles.panelTitle}>{item.name}</p>
                        <p className={styles.panelDesc}>{item.description} · exp+{item.expReward}</p>
                    </div>
                    <CreamChip color={item.achieved ? STAT_TONE.energy : STAT_TONE.cleanliness}>
                        {item.achieved ? '已达成' : '未达成'}
                    </CreamChip>
                </div>
            ))}
        </div>
    )
}

// ---------------- 面板：提醒 ----------------

function RemindersPanel() {
    const [list, setList] = useState<PetReminder[] | null>(null)
    const [unread, setUnread] = useState<number | null>(null)

    const load = useCallback(async () => {
        const { data: res } = await listPetReminders()
        if (res.success) {
            setList(res.data)
        }
        const { data: unreadRes } = await getPetReminderUnreadCount()
        if (unreadRes.success) {
            setUnread(unreadRes.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const readAll = useCallback(async () => {
        const { data: res } = await markAllPetRemindersRead()
        if (res.success) {
            await load()
        }
    }, [load])

    if (!list) {
        return <Spin />
    }
    return (
        <div>
            <div className={styles.footerRow}>
                <p className={styles.panelDesc}>
                    未读 {unread === null ? '—' : unread} 条（入口角标与此一致）
                </p>
                <CreamButton variant="ghost" onClick={() => void readAll()}>全部标为已读</CreamButton>
            </div>
            <div style={{ marginTop: 8 }}>
                {list.length === 0 ? (
                    <p className={styles.panelDesc}>还没有提醒，陪陪它吧～</p>
                ) : list.map(item => (
                    <div key={String(item.notificationId)} className={styles.panelRow}>
                        <span style={{
                            width: 8, height: 8, borderRadius: '50%', flex: 'none',
                            background: item.isRead ? 'var(--cream-300)' : STAT_TONE.hp,
                        }} />
                        <div className={styles.panelMain}>
                            <p className={styles.panelTitle}>{item.title}</p>
                            <p className={styles.panelDesc}>{item.content}</p>
                        </div>
                        <span style={{ fontSize: 12, color: 'var(--mocha-light)' }}>
                            {item.priority ?? 'P1'}
                        </span>
                    </div>
                ))}
            </div>
        </div>
    )
}

// ---------------- 面板：排行榜 ----------------

const RANK_TABS: Array<{ type: PetRankingType | 'SEASON'; label: string }> = [
    { type: 'LEVEL', label: '等级' },
    { type: 'BATTLE_WIN', label: '对战' },
    { type: 'BOTTLE', label: '捞瓶' },
    { type: 'SEASON', label: '赛季' },
]

function RankingsPanel() {
    const [type, setType] = useState<PetRankingType | 'SEASON'>('LEVEL')
    const [result, setResult] = useState<{ top20: Array<{
        rank: number; petId: number | string; name: string; species: PetSpecies
        level: number; value: number; ownerNickname: string; isMe: boolean
    }>; myValue: number | null; myRank: number | null } | null>(null)
    const [season, setSeason] = useState<PetSeasonRanking | null>(null)
    const [seasonHistory, setSeasonHistory] = useState<PetSeasonHistoryItem[] | null>(null)

    const load = useCallback(async (t: PetRankingType | 'SEASON') => {
        if (t === 'SEASON') {
            const [{ data: seasonRes }, { data: historyRes }] = await Promise.all([
                getPetSeasonRanking(),
                getPetSeasonHistory(),
            ])
            if (seasonRes.success) {
                setSeason(seasonRes.data)
            }
            if (historyRes.success) {
                setSeasonHistory(historyRes.data)
            }
            return
        }
        const { data: res } = await getPetRankings(t)
        if (res.success) {
            setResult(res.data)
        }
    }, [])

    useEffect(() => {
        void load(type)
    }, [load, type])

    if (type === 'SEASON') {
        return (
            <div>
                <div className={styles.petRow}>
                    {RANK_TABS.map(tab => (
                        <button
                            key={tab.type}
                            type="button"
                            className={`${styles.petChip} ${type === tab.type ? styles.petChipActive : ''}`}
                            onClick={() => setType(tab.type)}
                        >
                            {tab.label}
                        </button>
                    ))}
                </div>
                {!season ? (
                    <div style={{ paddingTop: 12 }}><Spin /></div>
                ) : season.season === null ? (
                    <p className={styles.panelDesc}>当前没有进行中的赛季，开赛后这里会亮起来</p>
                ) : (
                    <div style={{ paddingTop: 10 }}>
                        <p className={styles.panelDesc}>
                            {season.season.name} · {season.season.startsAt?.slice(5, 10)} ~ {season.season.endsAt?.slice(5, 10)} ·
                            我的等级 {season.myLevel ?? '—'} · 名次 {season.myRank ?? '未上榜'}
                        </p>
                        {season.top50.map(item => (
                            <div key={String(item.petId)} className={`${styles.panelRow} ${item.isMe ? styles.rankMe : ''}`}>
                                <span className={`${styles.rankBadge} ${item.rank <= 3 ? styles.rankBadgeTop : ''}`}>
                                    {item.rank}
                                </span>
                                <div className={styles.panelMain}>
                                    <p className={styles.panelTitle}>
                                        {SPECIES_EMOJI[item.species] ?? '🐾'} {item.name}
                                    </p>
                                    <p className={styles.panelDesc}>{item.ownerNickname} · Lv.{item.level}</p>
                                </div>
                                <span style={{ fontSize: 13, color: 'var(--espresso)' }}>{item.value}</span>
                            </div>
                        ))}
                    </div>
                )}
                {seasonHistory && seasonHistory.length > 0 ? (
                    <div style={{ paddingTop: 12 }}>
                        <p className={styles.panelTitle}>历届我的名次</p>
                        {seasonHistory.map(item => (
                            <div key={String(item.seasonId)} className={styles.panelRow}>
                                <span className={styles.rankBadge}>{item.rankNo}</span>
                                <div className={styles.panelMain}>
                                    <p className={styles.panelTitle}>{item.seasonName}</p>
                                    <p className={styles.panelDesc}>
                                        结算于 {item.endedAt?.slice(0, 10) ?? '—'} · 等级 {item.level}
                                    </p>
                                </div>
                            </div>
                        ))}
                    </div>
                ) : null}
            </div>
        )
    }
    return (
        <div>
            <div className={styles.petRow}>
                {RANK_TABS.map(tab => (
                    <button
                        key={tab.type}
                        type="button"
                        className={`${styles.petChip} ${type === tab.type ? styles.petChipActive : ''}`}
                        onClick={() => setType(tab.type)}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>
            {!result ? (
                <div style={{ paddingTop: 12 }}><Spin /></div>
            ) : (
                <div style={{ paddingTop: 10 }}>
                    <p className={styles.panelDesc}>
                        我的{RANK_TABS.find(t => t.type === type)?.label}值：
                        {result.myValue ?? '—'} · 名次：{result.myRank ?? '未上榜'}
                    </p>
                    {result.top20.map(item => (
                        <div key={String(item.petId)} className={`${styles.panelRow} ${item.isMe ? styles.rankMe : ''}`}>
                            <span className={`${styles.rankBadge} ${item.rank <= 3 ? styles.rankBadgeTop : ''}`}>
                                {item.rank}
                            </span>
                            <div className={styles.panelMain}>
                                <p className={styles.panelTitle}>
                                    {SPECIES_EMOJI[item.species] ?? '🐾'} {item.name}
                                </p>
                                <p className={styles.panelDesc}>{item.ownerNickname} · Lv.{item.level}</p>
                            </div>
                            <span style={{ fontSize: 13, color: 'var(--espresso)' }}>{item.value}</span>
                        </div>
                    ))}
                </div>
            )}
        </div>
    )
}

// ---------------- 面板：钱包 ----------------

function WalletPanel() {
    const [wallet, setWallet] = useState<PetWalletVO | null>(null)

    useEffect(() => {
        void (async () => {
            const { data: res } = await getPetWallet()
            if (res.success) {
                setWallet(res.data)
            }
        })()
    }, [])

    if (!wallet) {
        return <Spin />
    }
    return (
        <div>
            <p style={{ fontSize: 34, margin: '6px 0 2px', color: 'var(--espresso)', fontVariantNumeric: 'tabular-nums' }}>
                {wallet.balance}
                <span style={{ fontSize: 14, color: 'var(--mocha)', marginLeft: 8 }}>宠物币</span>
            </p>
            <p className={styles.panelDesc}>
                账户 {wallet.accountId.slice(0, 12)}… · {wallet.status === 'ACTIVE' ? '正常' : '冻结'}
            </p>
            <p className={styles.panelDesc}>收支流水在商城购买/任务领取时自动记账。</p>
        </div>
    )
}

// ---------------- 面板：打工 · 读书 ----------------

/** PET-23 §6.3 活动中心：聚合摘要（忙碌/待领数/任务·事件·合作）+ 待领列表批领（逐项结果可重试） */
function ActivityCenterPanel({ onChanged }: { onChanged: () => void }) {
    const { message } = App.useApp()
    const [summary, setSummary] = useState<PetActivityCenterSummary | null>(null)
    const [activities, setActivities] = useState<Array<Record<string, unknown>>>([])
    const [selected, setSelected] = useState<Array<number | string>>([])
    const [busy, setBusy] = useState<string | null>(null)
    const [batchResult, setBatchResult] = useState<PetClaimBatchItem[] | null>(null)

    const load = useCallback(async () => {
        const [centerRes, actRes] = await Promise.all([
            getPetActivityCenter(),
            listPetActivities(),
        ])
        if (centerRes.data.success && centerRes.data.data) {
            setSummary(centerRes.data.data)
        }
        if (actRes.data.success) {
            setActivities(actRes.data.data ?? [])
            setSelected([])
            setBatchResult(null)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const claimable = activities.filter(item => String(item.status) === 'COMPLETED')

    const claimBatch = useCallback(async () => {
        if (selected.length === 0) {
            message.info('先勾选要领取的活动')
            return
        }
        setBusy('batch')
        try {
            const { data: res } = await claimPetActivitiesBatch(selected)
            if (res.success && res.data) {
                setBatchResult(res.data)
                const failed = res.data.filter(item => item.status === 'FAILED' || item.status === 'NOT_READY')
                if (failed.length === 0) {
                    message.success(`已领取 ${res.data.length} 项`)
                } else {
                    message.warning(`部分未成功（${failed.length} 项），明细见下方`)
                }
                onChanged()
                await load()
            } else {
                message.warning(res.error?.message ?? '批量领取未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged, selected])

    if (!summary) {
        return <Spin />
    }
    const busyText = [
        summary.accountBusyActivity.activity ? '活动进行中' : null,
        summary.accountBusyActivity.custody ? '托管中' : null,
        (summary.accountBusyActivity as { minigame?: boolean }).minigame ? '小游戏进行中' : null,
    ].filter(Boolean).join(' · ')
    return (
        <div>
            <p className={styles.panelDesc} style={{ margin: '0 0 8px' }}>
                业务日 {summary.businessDate}
                {busyText ? ` · ${busyText}` : ' · 空闲'}
                {summary.selectedPetActivity ? ` · 当前活动将于 ${String(summary.selectedPetActivity.finishedAt).slice(11, 16)} 结束` : ''}
            </p>
            <p className={styles.panelDesc} style={{ margin: '0 0 8px' }}>
                今日任务 {summary.dailySetSummary ? `${summary.dailySetSummary.claimedCount}/${summary.dailySetSummary.totalCount}` : '未生成'}
                {summary.dailySetSummary?.chestClaimable ? ' · 宝箱可领' : ''}
                {summary.eventSummary ? ` · 活动待领 ${summary.eventSummary.claimableCount}` : ''}
                {summary.cooperationSummary?.participated ? ` · 合作${summary.cooperationSummary.status === 'COMPLETED' ? '已达成' : '进行中'}` : ''}
            </p>
            {(() => {
                // PET-28/T60：恢复中心——可自助恢复的在途事项（跳原实体面板处理）
                const rec = summary.recoveries
                if (!rec) return null
                const items: string[] = []
                if (rec.bindingFailedAlbums > 0) items.push(`绑定失败相册 ${rec.bindingFailedAlbums}（更多·回忆页重试）`)
                if (rec.unsettledMinigameRounds > 0) items.push(`进行中对局 ${rec.unsettledMinigameRounds}（玩法页恢复）`)
                if (rec.processingPurchases > 0) items.push(`处理中购买 ${rec.processingPurchases}（稍后自动确认）`)
                return items.length > 0 ? (
                    <p className={styles.panelDesc} style={{ margin: '0 0 8px', color: '#b45309' }}>
                        待恢复：{items.join(' · ')}
                    </p>
                ) : null
            })()}
            <div className={styles.petRow} style={{ marginBottom: 8 }}>
                <span className={styles.panelDesc}>可领取活动 {claimable.length} 项</span>
                <span style={{ flex: 1 }} />
                <CreamButton loading={busy === 'batch'} onClick={() => void claimBatch()}
                    disabled={claimable.length === 0}>
                    批量领取{selected.length > 0 ? `（已选 ${selected.length}）` : ''}
                </CreamButton>
            </div>
            {claimable.length === 0 ? (
                <p className={styles.panelDesc}>暂无可领取的活动，完成后回到这里领奖。</p>
            ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                    {claimable.map(item => {
                        const id = item.activityId as number | string
                        const checked = selected.includes(id)
                        const itemResult = batchResult?.find(r => String(r.activityId) === String(id))
                        return (
                            <label key={String(id)} className={styles.petRow}
                                style={{ cursor: 'pointer', alignItems: 'center', gap: 8 }}>
                                <input type="checkbox" checked={checked}
                                    onChange={() => setSelected(prev => checked ? prev.filter(x => String(x) !== String(id)) : [...prev, id])} />
                                <span style={{ flex: 1 }}>
                                    {ACTIVITY_LABEL[String(item.activityType)] ?? String(item.activityType)}
                                    {itemResult ? ` · ${itemResult.status === 'CLAIMED' ? '已领取' : itemResult.status === 'ALREADY_CLAIMED' ? '此前已领' : '未成功'}` : ''}
                                </span>
                            </label>
                        )
                    })}
                </div>
            )}
            {batchResult && batchResult.some(r => r.status === 'FAILED') ? (
                <p className={styles.panelDesc} style={{ color: '#c00' }}>
                    存在失败项（{batchResult.filter(r => r.status === 'FAILED').map(r => String(r.errorCode ?? r.status)).join('、')}），可稍后重试。
                </p>
            ) : null}
        </div>
    )
}

const ACTIVITY_LABEL: Record<string, string> = {
    WORK: '打工', STUDY: '读书', FISHING: '捞瓶', REST: '休息', CAREER_WORK: '职业工作',
}

function ActivitiesPanel({ petStatus, onChanged }: { petStatus: string; onChanged: () => void }) {
    const { message } = App.useApp()
    const [tab, setTab] = useState<'work' | 'study'>('work')
    const [jobs, setJobs] = useState<PetJobItem[] | null>(null)
    const [studies, setStudies] = useState<PetStudyItem[] | null>(null)
    const [busy, setBusy] = useState<string | null>(null)

    const load = useCallback(async () => {
        const [jobRes, studyRes] = await Promise.all([listPetJobs(), listPetStudies()])
        if (jobRes.data.success) {
            setJobs(jobRes.data.data)
        }
        if (studyRes.data.success) {
            setStudies(studyRes.data.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const start = useCallback(async (kind: 'work' | 'study', configId: number | string) => {
        setBusy(`start-${configId}`)
        try {
            const run = kind === 'work' ? startPetWork : startPetStudy
            const { data: res } = await run(configId)
            if (res.success) {
                message.success(kind === 'work' ? '开始打工了，加油～' : '开始读书了，安静点～')
                onChanged()
            } else {
                message.warning(res.error?.message ?? '未能开始')
            }
        } finally {
            setBusy(null)
        }
    }, [message, onChanged])

    const claim = useCallback(async (kind: 'work' | 'study') => {
        setBusy(`claim-${kind}`)
        try {
            const run = kind === 'work' ? claimPetWork : claimPetStudy
            const { data: res } = await run()
            if (res.success) {
                message.success('收益已领取')
                onChanged()
            } else {
                message.warning(res.error?.message ?? '还没有可领取的收益')
            }
        } finally {
            setBusy(null)
        }
    }, [message, onChanged])

    if (!jobs || !studies) {
        return <Spin />
    }
    const items: PetJobItem[] | PetStudyItem[] = tab === 'work' ? jobs : studies
    const busyStatus = petStatus === 'WORKING' || petStatus === 'STUDYING' || petStatus === 'FISHING'
    return (
        <div>
            <div className={styles.petRow}>
                <button type="button" className={`${styles.petChip} ${tab === 'work' ? styles.petChipActive : ''}`}
                    onClick={() => setTab('work')}>💼 打工</button>
                <button type="button" className={`${styles.petChip} ${tab === 'study' ? styles.petChipActive : ''}`}
                    onClick={() => setTab('study')}>📚 读书</button>
                <span style={{ flex: 1 }} />
                <CreamButton variant="ghost" loading={busy === `claim-${tab}`}
                    onClick={() => void claim(tab)}>
                    领取{tab === 'work' ? '打工' : '读书'}收益
                </CreamButton>
            </div>
            <p className={styles.panelDesc} style={{ margin: '8px 0 2px' }}>
                当前状态：{STATUS_LABEL[petStatus] ?? '悠闲中'}{busyStatus ? '（进行中的活动结束后再开始新的）' : ''}
            </p>
            <div style={{ marginTop: 6 }}>
                {items.map(item => {
                    const cost = tab === 'work'
                        ? `精力-${item.energyCost} 饱食-${(item as PetJobItem).hungerCost}`
                        : `精力-${item.energyCost}`
                    const reward = tab === 'work'
                        ? `exp+${item.expReward} 币+${(item as PetJobItem).currencyReward}`
                        : `exp+${item.expReward} 智力+${(item as PetStudyItem).intelligenceReward}`
                    return (
                        <div key={String(item.configId)} className={styles.panelRow}>
                            <div className={styles.panelMain}>
                                <p className={styles.panelTitle}>{item.name}</p>
                                <p className={styles.panelDesc}>
                                    {item.description} · {fmtDuration(item.durationSeconds)} · {cost} · {reward}
                                </p>
                            </div>
                            <CreamButton
                                variant="ghost"
                                loading={busy === `start-${item.configId}`}
                                disabled={!item.eligible}
                                onClick={() => void start(tab, item.configId)}
                            >
                                {item.eligible ? '开始' : `Lv.${item.requiredLevel}`}
                            </CreamButton>
                        </div>
                    )
                })}
            </div>
        </div>
    )
}

// ---------------- 面板：职业 ----------------

function CareerPanel({ onChanged }: { onChanged: () => void }) {
    const { message } = App.useApp()
    const [panel, setPanel] = useState<PetCareerPanel | null>(null)
    const [busy, setBusy] = useState<string | null>(null)

    const load = useCallback(async () => {
        const { data: res } = await getPetCareer()
        if (res.success) {
            setPanel(res.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const act = useCallback(async (kind: 'start' | 'claim' | 'promote' | 'apply', code?: string) => {
        setBusy(kind + (code ?? ''))
        try {
            const runners = {
                start: startPetCareerWork,
                claim: claimPetCareerWork,
                promote: promotePetCareer,
                apply: () => applyPetCareer(code ?? ''),
            }
            const { data: res } = await runners[kind]()
            if (res.success) {
                message.success(kind === 'start' ? '上班了' : kind === 'claim' ? '工资已领取'
                    : kind === 'promote' ? '晋升成功！' : '入职成功')
                await load()
                onChanged()
            } else {
                message.warning(res.error?.message ?? '操作未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged])

    if (!panel) {
        return <Spin />
    }
    return (
        <div>
            <div className={styles.panelRow}>
                <span style={{ fontSize: 24 }}>{panel.icon ?? '👔'}</span>
                <div className={styles.panelMain}>
                    <p className={styles.panelTitle}>
                        {panel.careerName ?? '尚未入职'}
                        {panel.tier !== null ? ` · T${panel.tier}` : ''}
                    </p>
                    <p className={styles.panelDesc}>累计上班 {panel.workCount} 次</p>
                </div>
            </div>
            {panel.activeActivity ? (
                <p className={styles.panelDesc} style={{ margin: '6px 0' }}>
                    正在上岗中{panel.activeActivity.canClaim
                        ? ' —— 已完成，可以领取！'
                        : `，剩余 ${fmtDuration(panel.activeActivity.remainingSeconds)}`}
                </p>
            ) : null}
            <div className={styles.renameRow}>
                <CreamButton variant="ghost" loading={busy === 'claim'}
                    onClick={() => void act('claim')}>领取工资</CreamButton>
                <CreamButton loading={busy === 'start'} disabled={panel.activeActivity !== null}
                    onClick={() => void act('start')}>开始上班</CreamButton>
                <CreamButton variant="ghost" loading={busy === 'promote'} disabled={!panel.canPromote}
                    onClick={() => void act('promote')}>
                    {panel.canPromote && panel.promoteToName ? `晋升为${panel.promoteToName}` : '晋升'}
                </CreamButton>
            </div>
            {!panel.canPromote && panel.promoteLockReason ? (
                <p className={styles.panelDesc} style={{ marginTop: 6 }}>{panel.promoteLockReason}</p>
            ) : null}
            <p className={styles.panelTitle} style={{ margin: '14px 0 4px', fontFamily: 'var(--font-serif)' }}>
                职业路线
            </p>
            {panel.careers.map(item => (
                <div key={item.code} className={styles.panelRow}>
                    <span style={{ fontSize: 20 }}>{item.icon}</span>
                    <div className={styles.panelMain}>
                        <p className={styles.panelTitle}>
                            {item.name}
                            <span style={{ color: 'var(--mocha-light)', marginLeft: 8, fontSize: 12 }}>
                                T{item.tier} · {fmtDuration(item.durationSeconds)} · 币+{item.currencyReward}
                            </span>
                        </p>
                        <p className={styles.panelDesc}>
                            {item.description} · 需 Lv.{item.requiredLevel} 智力{item.requiredIntelligence}
                            {item.lockReason ? ` · ${item.lockReason}` : ''}
                        </p>
                    </div>
                    {!item.current && item.eligible ? (
                        <CreamButton variant="ghost" loading={busy === `apply${item.code}`}
                            onClick={() => void act('apply', item.code)}>申请</CreamButton>
                    ) : item.current ? (
                        <CreamChip color={STAT_TONE.energy}>现任</CreamChip>
                    ) : null}
                </div>
            ))}
        </div>
    )
}

// ---------------- 面板：漂流瓶 ----------------

function BottlePanel({ onChanged }: { onChanged: () => void }) {
    const { message } = App.useApp()
    const [status, setStatus] = useState<PetBottleStatus | null>(null)
    const [busy, setBusy] = useState<string | null>(null)

    const load = useCallback(async () => {
        const { data: res } = await getPetBottleStatus()
        if (res.success) {
            setStatus(res.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const act = useCallback(async (kind: 'start' | 'claim') => {
        setBusy(kind)
        try {
            const run = kind === 'start' ? startPetBottle : claimPetBottle
            const { data: res } = await run()
            if (res.success) {
                message.success(kind === 'start' ? '瓶子抛出去了，等消息吧～' : '漂流瓶结果已收入图鉴')
                await load()
                onChanged()
            } else {
                message.warning(res.error?.message ?? '操作未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged])

    if (!status) {
        return <Spin />
    }
    const stateText = status.canClaim ? '有瓶子可以领取！'
        : status.fishing ? `捕捞中，剩余 ${fmtDuration(status.remainingSeconds)}`
            : status.cooldownRemainingSeconds > 0 ? `冷却中，${fmtDuration(status.cooldownRemainingSeconds)} 后可再抛`
                : '海边很安静，抛一个瓶子试试'
    return (
        <div>
            <p style={{ fontSize: 16, margin: '4px 0 8px', color: 'var(--espresso)' }}>{stateText}</p>
            <p className={styles.panelDesc}>
                当前海域：{status.unlockedArea} · 估计成功率 {Math.round(status.estimatedSuccessRate * 100)}%
            </p>
            {status.lastOutcome ? (
                <p className={styles.panelDesc}>
                    上次结果：{status.lastOutcome === 'CAUGHT' ? '捞到了漂流瓶' : status.lastOutcome === 'EMPTY' ? '空瓶' : '失败'}
                </p>
            ) : null}
            <div className={styles.renameRow}>
                {status.canClaim ? (
                    <CreamButton block loading={busy === 'claim'} onClick={() => void act('claim')}>领取漂流瓶</CreamButton>
                ) : (
                    <CreamButton block loading={busy === 'start'} disabled={status.fishing || status.cooldownRemainingSeconds > 0}
                        onClick={() => void act('start')}>抛出漂流瓶</CreamButton>
                )}
            </div>
        </div>
    )
}

// ---------------- 面板：对战 ----------------

interface PendingBattle {
    battleId: number | string
    attackerPetName: string | null
    mode: string
}

/** 对战回合流水回放（rounds 为服务端返回的 JSON 字符串） */
function View_replayRounds({ raw }: { raw: string }) {
    let rounds: Array<{ round: number; actorName: string; action: string; damage: number; critical: boolean; dodged: boolean; targetName: string; targetRemainingHp: number }> = []
    try {
        const parsed = JSON.parse(raw)
        if (Array.isArray(parsed)) rounds = parsed
    } catch { /* 解析失败按空处理 */ }
    if (rounds.length === 0) return <p className={styles.panelDesc}>回放数据缺失</p>
    return (
        <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 4 }}>
            {rounds.map((r, index) => (
                <p key={`${r.round}-${index}`} className={styles.panelDesc} style={{ margin: 0 }}>
                    {`第 ${r.round} 回合：${r.actorName}${r.action === 'skill' ? ' 使用技能' : ''}${r.dodged ? ' 被闪避' : ` 造成 ${r.damage} 点伤害`}${r.critical ? '（暴击）' : ''} → ${r.targetName} 剩余 ${r.targetRemainingHp}`}
                </p>
            ))}
        </div>
    )
}

function BattlePanel({ myPetId, onChanged }: { myPetId: number | string; onChanged: () => void }) {
    const { message } = App.useApp()
    const [tab, setTab] = useState<'opponents' | 'pending' | 'history'>('opponents')
    const [replayId, setReplayId] = useState<string | number | null>(null)
    const [opponents, setOpponents] = useState<PetOpponent[] | null>(null)
    const [pending, setPending] = useState<PendingBattle[] | null>(null)
    const [history, setHistory] = useState<PetBattleItem[] | null>(null)
    const [busy, setBusy] = useState<string | null>(null)

    const load = useCallback(async () => {
        if (tab === 'opponents') {
            const { data: res } = await listPetOpponents()
            if (res.success) {
                setOpponents(res.data)
            }
        } else if (tab === 'pending') {
            const { data: res } = await listPendingBattles()
            if (res.success) {
                setPending(res.data as unknown as PendingBattle[])
            }
        } else {
            const { data: res } = await listPetBattleHistory({ pageSize: 10 })
            if (res.success) {
                setHistory(res.data)
            }
        }
    }, [tab])

    useEffect(() => {
        void load()
    }, [load])

    const challenge = useCallback(async (opponent: PetOpponent) => {
        setBusy(String(opponent.petId))
        try {
            const { data: res } = await challengePetBattle({
                mode: opponent.isWild ? 'PVE' : 'PVP',
                defenderPetId: opponent.isWild ? undefined : opponent.petId,
            })
            if (res.success) {
                message.success(res.data.status === 'FINISHED' ? '战斗结束，获得奖励已入账' : '挑战已发出，等对方应战')
                onChanged()
            } else {
                message.warning(res.error?.message ?? '挑战未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message, onChanged])

    const respond = useCallback(async (battleId: number | string, accept: boolean) => {
        setBusy(String(battleId))
        try {
            const run = accept ? acceptPetBattle : declinePetBattle
            const { data: res } = await run(battleId)
            if (res.success) {
                message.success(accept ? '接受挑战，战斗结算完成' : '已婉拒')
                await load()
                onChanged()
            } else {
                message.warning(res.error?.message ?? '操作未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged])

    if (tab === 'opponents' && !opponents) {
        return <Spin />
    }
    if (tab === 'pending' && !pending) {
        return <Spin />
    }
    if (tab === 'history' && !history) {
        return <Spin />
    }
    return (
        <div>
            <div className={styles.petRow}>
                {([['opponents', '对手'], ['pending', '待应战'], ['history', '历史']] as const).map(([key, label]) => (
                    <button key={key} type="button"
                        className={`${styles.petChip} ${tab === key ? styles.petChipActive : ''}`}
                        onClick={() => setTab(key)}>{label}</button>
                ))}
            </div>
            <div style={{ paddingTop: 8 }}>
                {tab === 'opponents' ? (opponents ?? []).map(item => (
                    <div key={String(item.petId)} className={styles.panelRow}>
                        <span style={{ fontSize: 20 }}>{SPECIES_EMOJI[item.species] ?? '🐾'}</span>
                        <div className={styles.panelMain}>
                            <p className={styles.panelTitle}>
                                {item.name}
                                {item.isWild ? <CreamChip color={STAT_TONE.energy}>野生</CreamChip> : null}
                            </p>
                            <p className={styles.panelDesc}>
                                Lv.{item.level} · {item.isWild ? '野外伙伴' : `主人：${item.ownerNickname}`}
                            </p>
                        </div>
                        <CreamButton variant="ghost" loading={busy === String(item.petId)}
                            onClick={() => void challenge(item)}>挑战</CreamButton>
                    </div>
                )) : null}
                {tab === 'pending' ? (pending ?? []).length === 0 ? (
                    <p className={styles.panelDesc}>暂时没有待应战的挑战</p>
                ) : (pending ?? []).map(item => (
                    <div key={String(item.battleId)} className={styles.panelRow}>
                        <div className={styles.panelMain}>
                            <p className={styles.panelTitle}>{item.attackerPetName ?? '对手'} 发起了{item.mode === 'PVE' ? '野外' : '友谊'}切磋</p>
                        </div>
                        <CreamButton variant="ghost" loading={busy === String(item.battleId)}
                            onClick={() => void respond(item.battleId, true)}>应战</CreamButton>
                        <CreamButton variant="ghost" loading={busy === String(item.battleId)}
                            onClick={() => void respond(item.battleId, false)}>婉拒</CreamButton>
                    </div>
                )) : null}
                {tab === 'history' ? (history ?? []).length === 0 ? (
                    <p className={styles.panelDesc}>还没有对战记录</p>
                ) : (history ?? []).map(item => {
                    const win = item.winnerPetId !== null && item.winnerPetId === myPetId
                    return (
                        <div key={String(item.battleId)} className={styles.panelRow} style={{ flexWrap: 'wrap' }}>
                            <div className={styles.panelMain}>
                                <p className={styles.panelTitle}>
                                    {item.attackerPetName ?? '?'} VS {item.defenderPetName ?? '?'}
                                </p>
                                <p className={styles.panelDesc}>{item.mode === 'PVE' ? '野外切磋' : '友谊切磋'}</p>
                            </div>
                            {item.status === 'FINISHED' && item.rounds ? (
                                <CreamButton variant="ghost"
                                    onClick={() => setReplayId(replayId === item.battleId ? null : item.battleId)}>
                                    {replayId === item.battleId ? '收起回放' : '回放'}
                                </CreamButton>
                            ) : null}
                            <CreamChip color={win ? STAT_TONE.energy : STAT_TONE.cleanliness}>
                                {item.status === 'FINISHED' ? (win ? '获胜' : '惜败') : '已取消'}
                            </CreamChip>
                            {replayId === item.battleId && item.rounds ? (
                                <View_replayRounds raw={item.rounds} />
                            ) : null}
                        </div>
                    )
                }) : null}
            </div>
        </div>
    )
}

// ---------------- 面板：商城 / 背包 ----------------

function ShopPanel({ onChanged }: { onChanged: () => void }) {
    const { message } = App.useApp()
    const [shop, setShop] = useState<{ balance: number | null; currency: string; items: Array<{
        itemType: PetItemType; code: string; name: string; description: string; icon: string
        rarity: string; priceStarlight: number
    }> } | null>(null)
    const [busy, setBusy] = useState<string | null>(null)

    const load = useCallback(async () => {
        const { data: res } = await getPetShop()
        if (res.success) {
            setShop(res.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const buy = useCallback(async (item: { itemType: PetItemType; code: string; name: string }) => {
        setBusy(item.code)
        try {
            const { data: res } = await buyPetItem({ itemType: item.itemType, itemCode: item.code })
            if (res.success) {
                message.success(`${item.name} 已入背包`)
                await load()
                onChanged()
            } else {
                message.warning(res.error?.message ?? '购买未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged])

    if (!shop) {
        return <Spin />
    }
    return (
        <div>
            <p className={styles.panelDesc}>
                宠物币余额：{shop.balance === null ? '服务暂不可用' : shop.balance} · 共 {shop.items.length} 件在售
            </p>
            <div style={{ paddingTop: 8 }}>
                {shop.items.map(item => (
                    <div key={item.code} className={styles.panelRow}>
                        <span style={{ fontSize: 20 }}>{item.icon}</span>
                        <div className={styles.panelMain}>
                            <p className={styles.panelTitle}>
                                {item.name}
                                <CreamChip color={RARITY_COLOR[item.rarity] ?? '#9C8D7E'}>
                                    {RARITY_LABEL[item.rarity] ?? item.rarity}
                                </CreamChip>
                            </p>
                            <p className={styles.panelDesc}>{item.description}</p>
                        </div>
                        <span style={{ fontSize: 13, color: 'var(--brass-600)', whiteSpace: 'nowrap' }}>
                            ⭐ {item.priceStarlight}
                        </span>
                        <CreamButton variant="ghost" loading={busy === item.code}
                            onClick={() => void buy(item)}>购买</CreamButton>
                    </div>
                ))}
            </div>
        </div>
    )
}

function InventoryPanel({ onChanged }: { onChanged: () => void }) {
    const { message } = App.useApp()
    const [list, setList] = useState<PetInventoryItem[] | null>(null)
    const [busy, setBusy] = useState<string | null>(null)
    /** 装备替换预览（B12）：按行内「预览」触发，展示替换增量 */
    const [preview, setPreview] = useState<PetEquipPreview | null>(null)

    const load = useCallback(async () => {
        const { data: res } = await listPetInventory()
        if (res.success) {
            setList(res.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const use = useCallback(async (item: PetInventoryItem) => {
        setBusy(item.code)
        try {
            const run = item.itemType === 'SKIN' ? () => wearPetSkin(item.code) : () => equipPetItem(item.code)
            const { data: res } = await run()
            if (res.success) {
                message.success(`${item.name} 已使用`)
                onChanged()
            } else {
                message.warning(res.error?.message ?? '使用未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message, onChanged])

    /** 卸下：装备按槽位、皮肤整只卸（都会改变属性/外观，需回写宠物状态） */
    const takeOff = useCallback(async (item: PetInventoryItem) => {
        setBusy(item.code)
        try {
            const run = item.itemType === 'SKIN'
                ? () => removePetSkin()
                : () => unequipPetItem(item.slot ?? '')
            const { data: res } = await run()
            if (res.success) {
                message.success(`${item.name} 已卸下`)
                onChanged()
            } else {
                message.warning(res.error?.message ?? '卸下未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message, onChanged])

    const showPreview = useCallback(async (item: PetInventoryItem) => {
        setBusy(`preview:${item.code}`)
        try {
            const { data: res } = await previewPetEquip(item.code)
            if (res.success) {
                setPreview(prev => (prev?.itemCode === item.code ? null : res.data))
            } else {
                message.warning(res.error?.message ?? '预览未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message])

    /** 喂养道具（F1）：食物堆叠入包，喂食直接消耗（效果服务端权威，不占免费次数） */
    const feedFood = useCallback(async (item: PetInventoryItem) => {
        setBusy(item.code)
        try {
            const { data: res } = await feedPetItem(item.code)
            if (res.success) {
                message.success(`${item.name} 吃掉了，状态好多了`)
                onChanged()
            } else {
                message.warning(res.error?.message ?? '喂食未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message, onChanged])

    if (!list) {
        return <Spin />
    }
    return (
        <div>
            {list.length === 0 ? (
                <p className={styles.panelDesc}>背包空空，去商城逛逛吧</p>
            ) : list.map(item => (
                <div key={item.code}>
                    <div className={styles.panelRow}>
                        <span style={{ fontSize: 20 }}>{item.icon}</span>
                        <div className={styles.panelMain}>
                            <p className={styles.panelTitle}>
                                {item.name}
                                <CreamChip color={RARITY_COLOR[item.rarity] ?? '#9C8D7E'}>
                                    {RARITY_LABEL[item.rarity] ?? item.rarity}
                                </CreamChip>
                                {item.equipped ? <CreamChip color="#7E9270">使用中</CreamChip> : null}
                            </p>
                            <p className={styles.panelDesc}>
                                {item.description || item.effect || item.itemType}
                            </p>
                        </div>
                        {item.itemType === 'FOOD' ? (
                            <CreamButton variant="ghost" loading={busy === item.code}
                                onClick={() => void feedFood(item)}>喂食</CreamButton>
                        ) : item.itemType === 'EQUIPMENT' || item.itemType === 'SKIN' ? (
                            <div style={{ display: 'flex', gap: 8 }}>
                                {item.equipped ? (
                                    <CreamButton variant="ghost" loading={busy === item.code}
                                        onClick={() => void takeOff(item)}>卸下</CreamButton>
                                ) : (
                                    <CreamButton variant="ghost" loading={busy === item.code}
                                        onClick={() => void use(item)}>
                                        {item.itemType === 'SKIN' ? '穿戴' : '装备'}
                                    </CreamButton>
                                )}
                                {item.itemType === 'EQUIPMENT' ? (
                                    <CreamButton variant="ghost" loading={busy === `preview:${item.code}`}
                                        onClick={() => void showPreview(item)}>预览</CreamButton>
                                ) : null}
                            </div>
                        ) : null}
                    </div>
                    {preview && preview.itemCode === item.code ? (
                        <p className={styles.panelDesc} style={{ paddingBottom: 8 }}>
                            替换后：HP {preview.after.maxHp}（{preview.delta.maxHp >= 0 ? '+' : ''}
                            {preview.delta.maxHp}）· 力 {preview.after.strength}（{preview.delta.strength >= 0 ? '+' : ''}
                            {preview.delta.strength}）· 智 {preview.after.intelligence}（{preview.delta.intelligence >= 0 ? '+' : ''}
                            {preview.delta.intelligence}）· 敏 {preview.after.agility}（{preview.delta.agility >= 0 ? '+' : ''}
                            {preview.delta.agility}）· 魅 {preview.after.charm}（{preview.delta.charm >= 0 ? '+' : ''}
                            {preview.delta.charm}）
                        </p>
                    ) : null}
                </div>
            ))}
        </div>
    )
}

// ---------------- 面板：技能 · 进化 ----------------

function SkillsPanel({ petLevel, onChanged }: { petLevel: number; onChanged: () => void }) {
    const { message } = App.useApp()
    const [skills, setSkills] = useState<PetSkillItem[] | null>(null)
    const [evo, setEvo] = useState<PetEvolutionStatus | null>(null)
    const [busy, setBusy] = useState<string | null>(null)

    const load = useCallback(async () => {
        const [skillRes, evoRes] = await Promise.all([listPetSkills(), getPetEvolution()])
        if (skillRes.data.success) {
            setSkills(skillRes.data.data)
        }
        if (evoRes.data.success) {
            setEvo(evoRes.data.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const learn = useCallback(async (item: PetSkillItem) => {
        setBusy(item.code)
        try {
            const { data: res } = await learnPetSkill(item.code)
            if (res.success) {
                message.success(`${item.name} 学习成功`)
                await load()
                onChanged()
            } else {
                message.warning(res.error?.message ?? '学习未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged])

    const evolve = useCallback(async () => {
        setBusy('evolve')
        try {
            const { data: res } = await evolvePet()
            if (res.success) {
                message.success('进化成功！')
                await load()
                onChanged()
            } else {
                message.warning(res.error?.message ?? '进化未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged])

    if (!skills || !evo) {
        return <Spin />
    }
    return (
        <div>
            <p className={styles.panelTitle} style={{ margin: '0 0 6px', fontFamily: 'var(--font-serif)' }}>
                进化 · 阶段 {evo.currentStage}/{evo.maxStage}
            </p>
            {evo.nextCode ? (
                <>
                    <p className={styles.panelDesc}>
                        下一形态：{evo.nextName} · 需 Lv.{evo.requiredLevel} · 宠物币 {evo.costStarlight}
                    </p>
                    <div className={styles.renameRow}>
                        <CreamButton block loading={busy === 'evolve'} disabled={petLevel < (evo.requiredLevel ?? 0)}
                            onClick={() => void evolve()}>
                            {petLevel < (evo.requiredLevel ?? 0) ? `Lv.${evo.requiredLevel} 解锁进化` : '立即进化'}
                        </CreamButton>
                    </div>
                </>
            ) : (
                <p className={styles.panelDesc}>已达最高进化阶段。</p>
            )}
            <p className={styles.panelTitle} style={{ margin: '14px 0 4px', fontFamily: 'var(--font-serif)' }}>
                技能
            </p>
            {skills.map(item => (
                <div key={item.code} className={styles.panelRow}>
                    <span style={{ fontSize: 20 }}>{item.icon}</span>
                    <div className={styles.panelMain}>
                        <p className={styles.panelTitle}>
                            {item.name}
                            <CreamChip color={item.skillType === 'ACTIVE' ? STAT_TONE.hp : STAT_TONE.energy}>
                                {item.skillType === 'ACTIVE' ? '主动' : '被动'}
                            </CreamChip>
                        </p>
                        <p className={styles.panelDesc}>{item.effectText}</p>
                    </div>
                    {item.learned ? (
                        <CreamChip color={STAT_TONE.energy}>已学习</CreamChip>
                    ) : (
                        <CreamButton variant="ghost" loading={busy === item.code}
                            disabled={petLevel < item.requiredLevel}
                            onClick={() => void learn(item)}>
                            {petLevel < item.requiredLevel ? `Lv.${item.requiredLevel}` : `⭐ ${item.priceStarlight}`}
                        </CreamButton>
                    )}
                </div>
            ))}
        </div>
    )
}

// ---------------- 面板：事件 ----------------

function EventsPanel({ onChanged }: { onChanged: () => void }) {
    const { message } = App.useApp()
    const [list, setList] = useState<PetEventItem[] | null>(null)
    const [busy, setBusy] = useState<string | null>(null)
    // PET-11/PET-23：进行中/待领奖/历史三视图——旧期次关闭后宽限期内仍可达可领
    const [view, setView] = useState<'AVAILABLE' | 'CLAIMABLE' | 'HISTORY'>('AVAILABLE')

    const load = useCallback(async () => {
        const { data: res } = await listPetEventsByStatus(view)
        if (res.success) {
            setList(res.data)
        }
    }, [view])

    useEffect(() => {
        void load()
    }, [load])

    const claim = useCallback(async (item: PetEventItem) => {
        setBusy(item.code)
        try {
            // R33：期次驱动活动按 occurrenceId 领取（历史期次不覆盖）；否则走旧 eventCode 入口
            const { data: res } = item.occurrenceId
                ? await claimPetEventOccurrence(item.occurrenceId)
                : await claimPetEvent(item.code)
            if (res.success) {
                message.success('活动奖励已领取')
                await load()
                onChanged()
            } else {
                message.warning(res.error?.message ?? '领取未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged])

    if (!list) {
        return <Spin />
    }
    return (
        <div>
            <div className={styles.petRow} style={{ marginBottom: 8 }}>
                {([['AVAILABLE', '进行中'], ['CLAIMABLE', '待领奖'], ['HISTORY', '历史']] as const).map(([key, label]) => (
                    <button key={key} type="button"
                        className={`${styles.petChip} ${view === key ? styles.petChipActive : ''}`}
                        onClick={() => setView(key)}>{label}</button>
                ))}
            </div>
            {list.length === 0 ? <p className={styles.panelDesc}>这一栏暂时没有活动。</p> : null}
            {list.map(item => (
                <div key={item.code} className={styles.panelRow}>
                    <div className={styles.panelMain}>
                        <p className={styles.panelTitle}>
                            {item.name}
                            <span style={{ color: 'var(--mocha-light)', marginLeft: 8, fontSize: 12 }}>
                                {item.progress}/{item.targetValue}
                            </span>
                        </p>
                        <p className={styles.panelDesc}>
                            {item.description} · ⭐{item.rewardStarlight} exp+{item.rewardExp}
                        </p>
                    </div>
                    {item.claimable ? (
                        <CreamButton variant="ghost" loading={busy === item.code}
                            onClick={() => void claim(item)}>领取</CreamButton>
                    ) : (
                        <CreamChip color={STAT_TONE.cleanliness}>
                            {item.claimed ? '已领取' : item.expired ? '已过期' : '进行中'}
                        </CreamChip>
                    )}
                </div>
            ))}
        </div>
    )
}

// ---------------- 面板：聊天 ----------------

function ChatPanel({ petName }: { petName: string }) {
    const { message } = App.useApp()
    const [messages, setMessages] = useState<PetChatMessage[] | null>(null)
    const [draft, setDraft] = useState('')
    const [busy, setBusy] = useState(false)
    const [persona, setPersona] = useState<PetPersona | null>(null)
    const [showPersona, setShowPersona] = useState(false)
    const bodyRef = useRef<HTMLDivElement | null>(null)

    const load = useCallback(async () => {
        const { data: res } = await listPetChatHistory({ pageSize: 30 })
        if (res.success) {
            setMessages(res.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    /** 人设卡按需加载：与注入 AI prompt 的身份信息同源（F8） */
    const loadPersona = useCallback(async () => {
        const { data: res } = await getPetChatPersona()
        if (res.success) {
            setPersona(res.data)
            setShowPersona(true)
        }
    }, [])

    useEffect(() => {
        const el = bodyRef.current
        if (el) {
            el.scrollTop = el.scrollHeight
        }
    }, [messages])

    const send = useCallback(async () => {
        const text = draft.trim()
        if (!text) {
            message.warning('说点什么吧')
            return
        }
        setBusy(true)
        try {
            const { data: res } = await sendPetChat(text)
            if (res.success) {
                setDraft('')
                await load()
            } else if (res.error?.code === 'PET_REQUEST_IN_PROGRESS') {
                // PET-23：在途——提示查询而非诱导重发（同键重放服务端收敛）
                message.info('它还在想回复…稍等片刻再查看结果')
            } else {
                message.warning(res.error?.message ?? '发送未成功')
            }
        } catch {
            // PET-14/§6.3：断网/超时（未知结果）——按原意图键查询终态，不生成新意图
            const requestKey = getPersistedIntentKey('/pet/chat', { message: text })
            if (!requestKey) {
                message.warning('网络不稳定，请重试')
                return
            }
            try {
                const { data: status } = await getPetChatRequestStatus(requestKey)
                if (status.success && status.data?.status === 'SUCCEEDED') {
                    setDraft('')
                    message.success('回复已送达')
                    await load()
                } else if (status.success && status.data?.status === 'PROCESSING') {
                    message.info('它还在想回复…稍后刷新即可看到')
                } else {
                    message.warning('发送未完成，可原样重发（不会重复计费）')
                }
            } catch {
                message.warning('网络不稳定，稍后可按原消息重试')
            }
        } finally {
            setBusy(false)
        }
    }, [draft, load, message])

    if (!messages) {
        return <Spin />
    }
    return (
        <div>
            <div className={styles.footerRow}>
                <p className={styles.panelDesc}>
                    {persona
                        ? `${persona.name} · ${persona.personalityText} · ${persona.careerName ?? '无业游民'} · ${persona.intimacyLevelName ?? `亲密度 Lv.${persona.intimacyLevel}`}`
                        : '它的性格、职业和口头禅，都和聊天时它"想"的一样'}
                </p>
                <CreamButton variant="ghost" onClick={() => (showPersona ? setShowPersona(false) : void loadPersona())}>
                    {showPersona ? '收起人设卡' : '看它的人设卡'}
                </CreamButton>
            </div>
            {showPersona && persona ? (
                <div className={styles.footerRow} style={{ paddingTop: 8 }}>
                    <p className={styles.panelDesc}>
                        {persona.ownerTitle ? `它叫你「${persona.ownerTitle}」` : '它还没学会怎么叫你'}
                        {persona.phrase ? ` · 口头禅：“${persona.phrase}”` : ''}
                    </p>
                </div>
            ) : null}
            <div ref={bodyRef} className={styles.chatBody}>
                {messages.map(item => (
                    <div key={String(item.messageId)} className={styles.bubbleRow}>
                        <div className={item.role === 'USER' ? styles.bubbleUser : styles.bubblePet}>
                            {item.role === 'PET' ? (
                                <span className={styles.bubbleWho}>{petName}</span>
                            ) : null}
                            {item.content}
                        </div>
                    </div>
                ))}
            </div>
            <div className={styles.renameRow}>
                <Input
                    className={styles.input}
                    value={draft}
                    onChange={event => setDraft(event.target.value)}
                    placeholder={`和${petName}说点什么…`}
                    maxLength={80}
                    onPressEnter={() => void send()}
                />
                <CreamButton loading={busy} onClick={() => void send()}>发送</CreamButton>
            </div>
        </div>
    )
}

// ---------------- 页面 ----------------

export default function PetCreamPage() {
    const { message } = App.useApp()
    const [pet, setPet] = useState<PetInfo | null>(null)
    const [pets, setPets] = useState<PetSummary[]>([])
    const [loading, setLoading] = useState(true)
    const [busy, setBusy] = useState<CareAction | null>(null)
    const [renameValue, setRenameValue] = useState('')
    // 主人称呼（宠物怎么叫主人）
    const [ownerTitleValue, setOwnerTitleValue] = useState('')
    const [adoptName, setAdoptName] = useState('')
    const [adoptSpecies, setAdoptSpecies] = useState<PetSpecies>('STRAWBERRY')
    // PET-23：URL 深链接驱动面板——?panel=center 直达对应面板（通知 payload/外部跳转用）
    const [activePanel, setActivePanel] = useState<PanelKey | null>(() => {
        const initial = new URLSearchParams(window.location.search).get('panel')
        return PANELS.some(item => item.key === initial) ? (initial as PanelKey) : null
    })
    /** 外观草稿（颜色/配饰），初始值来自 pet.appearance（服务端 JSON），保存后随宠物回写 */
    const [appearanceDraft, setAppearanceDraft] = useState<{ color: string; accessory: string } | null>(null)
    /** 动作可执行性（B06）：刷新随宠物一起拉取 */
    const [actions, setActions] = useState<PetActionItem[] | null>(null)
    /** R23/T36：加载代际——切宠/重载推进代际，迟到的旧响应不覆盖新状态 */
    const loadGenerationRef = useRef(0)
    /** R23：pet 镜像 ref——catch 分支读取当前值而无需把 pet 拉进 load 依赖（避免 effect 循环） */
    const petRef = useRef<typeof pet>(null)
    useEffect(() => {
        petRef.current = pet
    }, [pet])
    /** R23/T36：加载失败且无旧数据时展示重试页（500 不再伪装成未领养） */
    const [loadFailed, setLoadFailed] = useState(false)

    const load = useCallback(async () => {
        const generation = ++loadGenerationRef.current
        setLoading(true)
        try {
            const { data: mine } = await getMyPet()
            if (generation !== loadGenerationRef.current) {
                return // 迟到的旧响应：丢弃（服务端权威数据由更新的一次 load 承载）
            }
            setPet(mine.success ? mine.data : null)
            if (mine.success) {
                // 动作可执行性（B06）：喂食/玩耍等当前能否执行与不可执行原因，随宠物一起刷新
                const { data: actionsRes } = await getPetActions(mine.data.petId)
                if (actionsRes.success && generation === loadGenerationRef.current) {
                    setActions(actionsRes.data ?? [])
                }
            }
            const { data: list } = await listMyPets()
            if (list.success && generation === loadGenerationRef.current) {
                setPets(list.data)
            }
        } catch (error) {
            if (generation !== loadGenerationRef.current) {
                return
            }
            // R23/T36：仅"确实无宠物"（PET_NOT_FOUND）才显示领养入口；
            // 500/网络错误保留旧数据并提示可重试，不再伪装成"未领养"
            const code = (error as { code?: string })?.code
            if (code === 'PET_NOT_FOUND') {
                setPet(null)
            } else if (petRef.current !== null) {
                message.warning('刷新失败，显示的可能不是最新数据，请重试')
            } else {
                setLoadFailed(true)
            }
        } finally {
            if (generation === loadGenerationRef.current) {
                setLoading(false)
            }
        }
    }, [message])

    useEffect(() => {
        void load()
    }, [load])

    /** 外观草稿随宠物就位（切宠物/刷新后以服务端值为准） */
    useEffect(() => {
        if (pet && appearanceDraft === null) {
            setAppearanceDraft(parseAppearance(pet.appearance))
        }
    }, [appearanceDraft, pet])

    /** 保存外观：后端会同时卸下穿戴中的皮肤（避免"皮肤标记"与实际外观不一致） */
    const submitAppearance = useCallback(async () => {
        if (!appearanceDraft) {
            return
        }
        const { data: res } = await updateAppearance(appearanceDraft)
        if (res.success) {
            setPet(res.data)
            message.success('外观已更新，穿戴中的皮肤已卸下')
        }
    }, [appearanceDraft, message])

    const display = useMemo<PetDisplayState | null>(() => {
        if (!pet) {
            return null
        }
        return {
            name: pet.name,
            species: pet.species,
            gender: pet.gender,
            growthStage: pet.growthStage,
            level: pet.level,
            expPercent: pet.expToNext > 0 ? Math.round((pet.exp / pet.expToNext) * 100) : 0,
            hp: pet.hp,
            maxHp: pet.maxHp,
            hunger: pet.hunger,
            happiness: pet.happiness,
            energy: pet.energy,
            cleanliness: pet.cleanliness,
            status: pet.status,
            speech: STATUS_SPEECH[pet.status] ?? '今天也想和主人待在一起～',
        }
    }, [pet])

    const doCare = useCallback(async (action: CareAction) => {
        setBusy(action)
        try {
            const run = { feed: feedPet, play: playWithPet, clean: cleanPet, rest: restPet }[action]
            const { data: res } = await run()
            if (res.success) {
                setPet(res.data)
                message.success(`${CARE_LABEL[action]}完成`)
            } else {
                message.warning(res.error?.message ?? '操作未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message])

    /** 舞台意图：养成四动作就地执行；任务/成就/排行三类面板直接打开对应弹层 */
    const onIntent = useCallback((action: PetIntentAction) => {
        if (action === 'feed' || action === 'play' || action === 'clean' || action === 'rest') {
            void doCare(action)
            return
        }
        const mapped: Partial<Record<PetIntentAction, PanelKey>> = {
            openRoom: 'home',
            openSocial: 'social',
            openDaily: 'quests',
            openAchievements: 'achievements',
            openRankings: 'rankings',
            openWork: 'activities',
            openStudy: 'activities',
            openCareer: 'career',
            openBottle: 'bottle',
            openBattle: 'battle',
            openChat: 'chat',
            openCare: 'shop',
        }
        const target = mapped[action]
        if (target) {
            setActivePanel(target)
        }
    }, [doCare])

    const submitRename = useCallback(async () => {
        const name = renameValue.trim()
        if (!name) {
            message.warning('先写个名字吧')
            return
        }
        const { data: res } = await renamePet({ name })
        if (res.success) {
            setPet(res.data)
            setRenameValue('')
            message.success('改名成功')
        }
    }, [message, renameValue])

    const submitOwnerTitle = useCallback(async () => {
        const title = ownerTitleValue.trim()
        if (!title) {
            message.warning('先写个称呼吧（清空保存则恢复「主人」）')
            return
        }
        const { data: res } = await setOwnerTitle(title)
        if (res.success) {
            setPet(res.data)
            setOwnerTitleValue('')
            message.success('称呼已更新，宠物会这样叫你啦')
        }
    }, [message, ownerTitleValue])

    const togglePrivacy = useCallback(async (checked: boolean) => {
        const { data: res } = await updatePetPrivacy({ isPublic: checked })
        if (res.success) {
            setPet(prev => (prev ? { ...prev, isPublic: checked } : prev))
            message.success(checked ? '已公开到个人主页' : '已隐藏，只有你能看到它')
        }
    }, [message])

    const switchPet = useCallback(async (petId: number | string) => {
        const { data: res } = await activatePet(petId)
        if (res.success) {
            await load()
            message.success('已切换宠物')
        }
    }, [load, message])

    const adopt = useCallback(async () => {
        const name = adoptName.trim()
        if (!name) {
            message.warning('给新伙伴取个名字吧')
            return
        }
        const { data: res } = await createPet({
            name,
            species: adoptSpecies,
            personality: 'LIVELY',
            gender: 'MALE',
        })
        if (res.success) {
            setPet(res.data)
            message.success('领养成功，好好照顾它～')
            await load()
        }
    }, [adoptName, adoptSpecies, load, message])

    const activePanelMeta = PANELS.find(item => item.key === activePanel)

    if (loading) {
        return (
            <div className={`${styles.page} ${styles.centered}`}>
                <Spin size="large" />
            </div>
        )
    }

    const accent = fruitAccent(pet?.species)

    return (
        <ConfigProvider
            theme={{
                // 站点全局是暗色 antd token，宠物页必须强制回浅色，否则 Input/Spin 全是深蓝底
                algorithm: antdTheme.defaultAlgorithm,
                token: { colorPrimary: '#C89B5A', borderRadius: 14 },
            }}
        >
        <div className={styles.page}>
            <div className={styles.shell}>
                <header className={styles.masthead}>
                    <h1 className={styles.mastheadTitle}>Le Petit Jardin</h1>
                    <p className={styles.mastheadSub}>宠物小花园 · 五果相伴</p>
                    <div className={styles.mastheadRule} />
                </header>

                {/* R23/T36：加载失败（500/网络错误且无旧数据）显示重试页——不伪装成未领养 */}
                {loadFailed && !pet ? (
                    <CreamCard variant="arch" label="Adoption" title="加载失败" subtitle="服务暂时不可用，请稍后重试">
                        <CreamButton block onClick={() => { setLoadFailed(false); void load() }}>
                            重新加载
                        </CreamButton>
                    </CreamCard>
                ) : !pet ? (
                    <CreamCard variant="arch" label="Adoption" title="领养一只水果伙伴" subtitle="选一只，给它取个名字">
                        <div className={styles.fruitGrid}>
                            {FRUIT_OPTIONS.map(item => (
                                <button
                                    key={item.value}
                                    type="button"
                                    className={`${styles.fruitCard} ${adoptSpecies === item.value ? styles.fruitCardActive : ''}`}
                                    onClick={() => setAdoptSpecies(item.value)}
                                    style={adoptSpecies === item.value ? { borderColor: FRUIT_ACCENT[item.value] } : undefined}
                                >
                                    <span className={styles.fruitEmoji}>{item.emoji}</span>
                                    <span>{item.label}</span>
                                </button>
                            ))}
                        </div>
                        <Input
                            className={styles.input}
                            value={adoptName}
                            onChange={event => setAdoptName(event.target.value)}
                            placeholder="给它取个名字"
                            maxLength={12}
                        />
                        <div className={styles.renameRow}>
                            <CreamButton block onClick={() => void adopt()}>确认领养</CreamButton>
                        </div>
                    </CreamCard>
                ) : (
                    <>
                        <CreamCard variant="arch" label="Ma Maison" accent={accent}>
                            <div className={styles.identityStrip}>
                                <div className={styles.avatar} style={{ boxShadow: `0 6px 14px ${accent}33, inset 0 2px 0 rgba(255,255,255,.95), inset 0 -4px 0 ${accent}2E` }}>
                                    <span>{SPECIES_EMOJI[pet.species] ?? '🐾'}</span>
                                </div>
                                <div>
                                    <h2 className={styles.petName} style={{ color: accent }}>
                                        {pet.name}
                                        <span className={styles.gender}>{pet.gender === 'FEMALE' ? '♀' : '♂'}</span>
                                    </h2>
                                    <div className={styles.metaRow}>
                                        <CreamChip color={accent}>{FRUIT_OPTIONS.find(item => item.value === pet.species)?.label ?? pet.species}</CreamChip>
                                        <CreamChip color={STAT_TONE.hunger}>Lv.{pet.level}</CreamChip>
                                        <CreamChip color={STAT_TONE.energy}>{GROWTH_LABEL[pet.growthStage] ?? pet.growthStage}</CreamChip>
                                        <CreamChip color={STAT_TONE.cleanliness}>{STATUS_LABEL[pet.status] ?? '悠闲中'}</CreamChip>
                                    </div>
                                    <p className={styles.speech}>“{STATUS_SPEECH[pet.status] ?? '今天也想和主人待在一起～'}”</p>
                                </div>
                            </div>
                            <CreamStage>
                                <PetStage
                                    pet={display}
                                    onIntent={onIntent}
                                    fallback={
                                        <div style={{ display: 'grid', placeItems: 'center', fontSize: 64 }}>
                                            {SPECIES_EMOJI[pet.species] ?? '🐾'}
                                        </div>
                                    }
                                />
                            </CreamStage>
                        </CreamCard>

                        <CreamCard variant="menu" label="État" title="状态">
                            <div className={styles.statsGrid}>
                                <CreamStatBar variant="cell" name="生命" value={pet.hp} max={pet.maxHp} color={STAT_TONE.hp} />
                                <CreamStatBar variant="cell" name="饱食" value={pet.hunger} max={100} color={STAT_TONE.hunger} />
                                <CreamStatBar variant="cell" name="心情" value={pet.happiness} max={100} color={STAT_TONE.happiness} />
                                <CreamStatBar variant="cell" name="精力" value={pet.energy} max={100} color={STAT_TONE.energy} />
                                <CreamStatBar variant="cell" name="清洁" value={pet.cleanliness} max={100} color={STAT_TONE.cleanliness} />
                            </div>
                            {actions && actions.length > 0 ? (
                                <div className={styles.petRow} style={{ marginTop: 12 }}>
                                    {actions.map(item => (
                                        <span key={item.action} title={item.reasonText ?? ''}>
                                            <CreamChip color={item.allowed ? STAT_TONE.energy : STAT_TONE.cleanliness}>
                                                {ACTION_LABEL[item.action] ?? item.action}
                                                {item.allowed
                                                    ? (item.rewardRemainingToday !== null ? ` · 余${item.rewardRemainingToday}` : '')
                                                    : ' · 暂不可'}
                                            </CreamChip>
                                        </span>
                                    ))}
                                </div>
                            ) : null}
                        </CreamCard>

                        <div className={styles.actions}>
                            {(Object.keys(CARE_LABEL) as CareAction[]).map(action => (
                                <CreamButton
                                    key={action}
                                    onClick={() => void doCare(action)}
                                    loading={busy === action}
                                    disabled={busy !== null && busy !== action}
                                >
                                    {CARE_LABEL[action]}
                                </CreamButton>
                            ))}
                        </div>

                        <CreamCard variant="menu" label="Menu" title="功能面板">
                            {PANEL_GROUPS.map(group => {
                                const items = PANELS.filter(item => item.group === group.key)
                                if (items.length === 0) {
                                    return null
                                }
                                return (
                                    <div key={group.key} className={styles.menuGroup}>
                                        <div className={styles.menuGroupLabel}>
                                            <span className={styles.menuGroupTitle}>{group.label}</span>
                                            <span className={styles.menuGroupHint}>{group.hint}</span>
                                        </div>
                                        <div className={styles.menuGrid}>
                                            {items.map(item => (
                                                <button
                                                    key={item.key}
                                                    type="button"
                                                    className={styles.menuItem}
                                                    onClick={() => {
                                                        setActivePanel(item.key)
                                                        // PET-23：同步深链接（replaceState 避免回退栈污染）
                                                        const url = new URL(window.location.href)
                                                        url.searchParams.set('panel', item.key)
                                                        window.history.replaceState(null, '', url.toString())
                                                    }}
                                                >
                                                    <span className={styles.menuItemEmoji}>{item.emoji}</span>
                                                    <span>{item.label}</span>
                                                </button>
                                            ))}
                                        </div>
                                    </div>
                                )
                            })}
                        </CreamCard>

                        {pets.length > 1 ? (
                            <CreamCard variant="menu" label="Famille" title="我的宠物">
                                <div className={styles.petRow}>
                                    {pets.map(item => (
                                        <button
                                            key={String(item.petId)}
                                            type="button"
                                            className={`${styles.petChip} ${item.isActive ? styles.petChipActive : ''}`}
                                            onClick={() => void switchPet(item.petId)}
                                        >
                                            {SPECIES_EMOJI[item.species] ?? '🐾'} {item.name}
                                        </button>
                                    ))}
                                </div>
                            </CreamCard>
                        ) : null}

                        <CreamCard variant="menu" label="Réglages" title="设置">
                            <div className={styles.footerRow}>
                                <span className={styles.speech}>主页展示：{pet.isPublic ? '已公开' : '已隐藏'}</span>
                                <CreamButton variant="ghost" onClick={() => void togglePrivacy(!pet.isPublic)}>
                                    {pet.isPublic ? '设为隐藏' : '设为公开'}
                                </CreamButton>
                            </div>
                            <p className={styles.panelTitle} style={{ marginTop: 14 }}>外观</p>
                            <p className={styles.panelDesc}>
                                当前：{(appearanceDraft && APPEARANCE_COLORS.find(c => c.value === appearanceDraft.color)?.label) ?? '—'} ·
                                {(appearanceDraft && APPEARANCE_ACCESSORIES.find(a => a.value === appearanceDraft.accessory)?.label) ?? '—'}
                                （保存会卸下穿戴中的皮肤）
                            </p>
                            <div className={styles.petRow}>
                                {APPEARANCE_COLORS.map(item => (
                                    <button
                                        key={item.value}
                                        type="button"
                                        className={`${styles.petChip} ${appearanceDraft?.color === item.value ? styles.petChipActive : ''}`}
                                        onClick={() => setAppearanceDraft(prev => ({
                                            color: item.value,
                                            accessory: prev?.accessory ?? 'none',
                                        }))}
                                    >
                                        {item.label}
                                    </button>
                                ))}
                            </div>
                            <div className={styles.petRow}>
                                {APPEARANCE_ACCESSORIES.map(item => (
                                    <button
                                        key={item.value}
                                        type="button"
                                        className={`${styles.petChip} ${appearanceDraft?.accessory === item.value ? styles.petChipActive : ''}`}
                                        onClick={() => setAppearanceDraft(prev => ({
                                            color: prev?.color ?? 'orange',
                                            accessory: item.value,
                                        }))}
                                    >
                                        {item.label}
                                    </button>
                                ))}
                            </div>
                            <div className={styles.renameRow}>
                                <CreamButton variant="ghost" onClick={() => void submitAppearance()}>保存外观</CreamButton>
                            </div>
                            <div className={styles.renameRow}>
                                <Input
                                    className={styles.input}
                                    value={renameValue}
                                    onChange={event => setRenameValue(event.target.value)}
                                    placeholder="新名字"
                                    maxLength={12}
                                />
                                <CreamButton variant="ghost" onClick={() => void submitRename()}>改名</CreamButton>
                            </div>
                            <div className={styles.renameRow}>
                                <Input
                                    className={styles.input}
                                    value={ownerTitleValue}
                                    onChange={event => setOwnerTitleValue(event.target.value)}
                                    placeholder={pet.ownerTitle ?? '宠物怎么叫你（默认「主人」）'}
                                    maxLength={12}
                                />
                                <CreamButton variant="ghost" onClick={() => void submitOwnerTitle()}>称呼</CreamButton>
                            </div>
                        </CreamCard>

                        <CreamOrnament>❦</CreamOrnament>
                    </>
                )}
            </div>

            {activePanel && activePanelMeta ? (
                <CreamSheet title={activePanelMeta.title} onClose={() => setActivePanel(null)}>
                    {activePanel === 'home' ? <HomeBoard onChanged={() => void load()} /> : null}
                    {activePanel === 'social' && pet ? (
                        <SocialBoard myPetId={pet.petId} onChanged={() => void load()} />
                    ) : null}
                    {activePanel === 'companion' && pet ? (
                        <CompanionBoard myPetId={pet.petId} onChanged={() => void load()} />
                    ) : null}
                    {activePanel === 'play' && pet ? (
                        <PlayBoard myPetId={pet.petId} onChanged={() => void load()} />
                    ) : null}
                    {activePanel === 'misc' ? <MiscBoard onChanged={() => void load()} /> : null}
                    {activePanel === 'quests' ? <QuestsPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'activities' ? <ActivitiesPanel petStatus={pet?.status ?? 'IDLE'} onChanged={() => void load()} /> : null}
                    {activePanel === 'center' ? <ActivityCenterPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'career' ? <CareerPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'bottle' ? <BottlePanel onChanged={() => void load()} /> : null}
                    {activePanel === 'battle' ? <BattlePanel myPetId={pet?.petId ?? ''} onChanged={() => void load()} /> : null}
                    {activePanel === 'chat' && pet ? <ChatPanel petName={pet.name} /> : null}
                    {activePanel === 'shop' ? <ShopPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'inventory' ? <InventoryPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'skills' ? <SkillsPanel petLevel={pet?.level ?? 1} onChanged={() => void load()} /> : null}
                    {activePanel === 'events' ? <EventsPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'achievements' ? <AchievementsPanel /> : null}
                    {activePanel === 'reminders' ? <RemindersPanel /> : null}
                    {activePanel === 'rankings' ? <RankingsPanel /> : null}
                    {activePanel === 'wallet' ? <WalletPanel /> : null}
                </CreamSheet>
            ) : null}
        </div>
        </ConfigProvider>
    )
}
