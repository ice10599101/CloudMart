/**
 * 宠物家园 · 法式奶油风（/pet 正式版）。
 *
 * 版面：招牌页头 → "家"卡片（身份条 + 3D 舞台）→ 状态仪表 → 养成动作 → 功能菜单 → 设置。
 * 功能面板以 CreamSheet 弹层承载，逐个从旧版 PetHome 迁入（迁完删除 PetHome.tsx）。
 * 本期已迁：每日任务 / 成就 / 提醒 / 排行榜 / 钱包。养成四动作与多宠物、领养向导为页面内建。
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
    claimPetStudy,
    claimPetWork,
    cleanPet,
    createPet,
    declinePetBattle,
    equipPetItem,
    evolvePet,
    feedPet,
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
    renamePet,
    setOwnerTitle,
    restPet,
    sendPetChat,
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
    type PetDailyQuestPanel,
    type PetEventItem,
    type PetEvolutionStatus,
    type PetInfo,
    type PetInventoryItem,
    type PetItemType,
    type PetJobItem,
    type PetOpponent,
    type PetRankingType,
    type PetReminder,
    type PetSkillItem,
    type PetSpecies,
    type PetStudyItem,
    type PetSummary,
    type PetWalletVO,
} from '@/api/pet'
import PetStage, { type PetDisplayState, type PetIntentAction } from '@/components/PetStage'
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
    WORKING: '我正在打工赚星光呢！',
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

/** 功能面板注册表：只登记已迁入奶油风的面板，其余随批次补充 */
type PanelKey = 'quests' | 'activities' | 'career' | 'bottle' | 'achievements'
    | 'reminders' | 'rankings' | 'wallet' | 'battle' | 'shop' | 'inventory' | 'skills'
    | 'events' | 'chat'

const PANELS: Array<{ key: PanelKey; emoji: string; label: string; title: string }> = [
    { key: 'quests', emoji: '📋', label: '任务', title: '每日任务' },
    { key: 'activities', emoji: '💼', label: '打工·读书', title: '打工 · 读书' },
    { key: 'career', emoji: '👔', label: '职业', title: '职业生涯' },
    { key: 'bottle', emoji: '🍾', label: '捞瓶', title: '漂流瓶' },
    { key: 'battle', emoji: '⚔️', label: '对战', title: '对战' },
    { key: 'chat', emoji: '💬', label: '聊天', title: '和它聊聊' },
    { key: 'shop', emoji: '🛍️', label: '商城', title: '商城' },
    { key: 'inventory', emoji: '🎒', label: '背包', title: '背包' },
    { key: 'skills', emoji: '✨', label: '技能·进化', title: '技能 · 进化' },
    { key: 'events', emoji: '🎈', label: '事件', title: '活动事件' },
    { key: 'achievements', emoji: '🏆', label: '成就', title: '成就' },
    { key: 'reminders', emoji: '🔔', label: '提醒', title: '提醒' },
    { key: 'rankings', emoji: '📊', label: '排行', title: '排行榜' },
    { key: 'wallet', emoji: '🪙', label: '钱包', title: '钱包' },
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
            const { data: res } = code
                ? await claimPetDailyQuest(code)
                : await claimPetDailyQuestChest()
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

    if (!panel) {
        return <Spin />
    }
    return (
        <div>
            <p className={styles.panelDesc}>
                {panel.questDate} · 已完成 {panel.completedCount}/{panel.totalCount} ·
                已领 {panel.claimedCount}
            </p>
            <div style={{ marginTop: 10 }}>
                {panel.quests.map(quest => (
                    <div key={quest.code} className={styles.panelRow}>
                        <span style={{ fontSize: 20 }}>{quest.icon}</span>
                        <div className={styles.panelMain}>
                            <p className={styles.panelTitle}>
                                {quest.name}
                                <span style={{ color: 'var(--mocha-light)', marginLeft: 8, fontSize: 12 }}>
                                    {quest.progress}/{quest.targetValue}
                                </span>
                            </p>
                            <p className={styles.panelDesc}>
                                {quest.description} · {quest.statusLabel} ·
                                exp+{quest.expReward} 币+{quest.currencyReward}
                            </p>
                        </div>
                        {quest.claimable ? (
                            <CreamButton variant="ghost" loading={busy === quest.code}
                                onClick={() => void claim(quest.code)}>领取</CreamButton>
                        ) : (
                            <CreamChip color={STAT_TONE.cleanliness}>{quest.statusLabel}</CreamChip>
                        )}
                    </div>
                ))}
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

    const load = useCallback(async () => {
        const { data: res } = await listPetReminders()
        if (res.success) {
            setList(res.data)
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
            <div className={styles.renameRow}>
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

const RANK_TABS: Array<{ type: PetRankingType; label: string }> = [
    { type: 'LEVEL', label: '等级' },
    { type: 'BATTLE_WIN', label: '对战' },
    { type: 'BOTTLE', label: '捞瓶' },
]

function RankingsPanel() {
    const [type, setType] = useState<PetRankingType>('LEVEL')
    const [result, setResult] = useState<{ top20: Array<{
        rank: number; petId: number | string; name: string; species: PetSpecies
        level: number; value: number; ownerNickname: string; isMe: boolean
    }>; myValue: number | null; myRank: number | null } | null>(null)

    const load = useCallback(async (t: PetRankingType) => {
        const { data: res } = await getPetRankings(t)
        if (res.success) {
            setResult(res.data)
        }
    }, [])

    useEffect(() => {
        void load(type)
    }, [load, type])

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

function BattlePanel({ myPetId, onChanged }: { myPetId: number | string; onChanged: () => void }) {
    const { message } = App.useApp()
    const [tab, setTab] = useState<'opponents' | 'pending' | 'history'>('opponents')
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
                        <div key={String(item.battleId)} className={styles.panelRow}>
                            <div className={styles.panelMain}>
                                <p className={styles.panelTitle}>
                                    {item.attackerPetName ?? '?'} VS {item.defenderPetName ?? '?'}
                                </p>
                                <p className={styles.panelDesc}>{item.mode === 'PVE' ? '野外切磋' : '友谊切磋'}</p>
                            </div>
                            <CreamChip color={win ? STAT_TONE.energy : STAT_TONE.cleanliness}>
                                {item.status === 'FINISHED' ? (win ? '获胜' : '惜败') : '已取消'}
                            </CreamChip>
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
                星光余额：{shop.balance === null ? '服务暂不可用' : shop.balance} · 共 {shop.items.length} 件在售
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

    if (!list) {
        return <Spin />
    }
    return (
        <div>
            {list.length === 0 ? (
                <p className={styles.panelDesc}>背包空空，去商城逛逛吧</p>
            ) : list.map(item => (
                <div key={item.code} className={styles.panelRow}>
                    <span style={{ fontSize: 20 }}>{item.icon}</span>
                    <div className={styles.panelMain}>
                        <p className={styles.panelTitle}>
                            {item.name}
                            <CreamChip color={RARITY_COLOR[item.rarity] ?? '#9C8D7E'}>
                                {RARITY_LABEL[item.rarity] ?? item.rarity}
                            </CreamChip>
                        </p>
                        <p className={styles.panelDesc}>
                            {item.description || item.effect || item.itemType}
                        </p>
                    </div>
                    {item.itemType === 'EQUIPMENT' || item.itemType === 'SKIN' ? (
                        <CreamButton variant="ghost" loading={busy === item.code}
                            onClick={() => void use(item)}>
                            {item.itemType === 'SKIN' ? '穿戴' : '装备'}
                        </CreamButton>
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
                        下一形态：{evo.nextName} · 需 Lv.{evo.requiredLevel} · 星光 {evo.costStarlight}
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

    const load = useCallback(async () => {
        const { data: res } = await listPetEvents()
        if (res.success) {
            setList(res.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const claim = useCallback(async (code: string) => {
        setBusy(code)
        try {
            const { data: res } = await claimPetEvent(code)
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
                            onClick={() => void claim(item.code)}>领取</CreamButton>
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
            } else {
                message.warning(res.error?.message ?? '发送未成功')
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
    const [activePanel, setActivePanel] = useState<PanelKey | null>(null)

    const load = useCallback(async () => {
        setLoading(true)
        try {
            const { data: mine } = await getMyPet()
            setPet(mine.success ? mine.data : null)
            const { data: list } = await listMyPets()
            if (list.success) {
                setPets(list.data)
            }
        } catch {
            setPet(null)
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

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

                {!pet ? (
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
                            <div className={styles.menuGrid}>
                                {PANELS.map(item => (
                                    <button
                                        key={item.key}
                                        type="button"
                                        className={styles.menuItem}
                                        onClick={() => setActivePanel(item.key)}
                                    >
                                        <span className={styles.menuItemEmoji}>{item.emoji}</span>
                                        <span>{item.label}</span>
                                    </button>
                                ))}
                            </div>
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
                    {activePanel === 'quests' ? <QuestsPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'activities' ? <ActivitiesPanel petStatus={pet?.status ?? 'IDLE'} onChanged={() => void load()} /> : null}
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
