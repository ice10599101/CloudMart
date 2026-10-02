/**
 * 收尾面板：纪念日 / 分享卡片 / 战报详情 / 限时活动 / 屏蔽与举报。
 *
 * 后端语义（已核实）：
 * - 纪念日是纯计算端点（领养日 = pet.created_at，连续陪伴 = companion_streak），无新表；
 * - 分享卡片文案由服务端生成，前端复制后跳转发帖页（不直接代发）；
 * - 举报 targetType 白名单：WALL_MESSAGE / BOTTLE_CONTENT / NICKNAME（进入管理员处理队列）；
 * - 屏蔽名单接口只返回被拉黑的 userId 数组（最小化暴露）；
 * - 限时活动（/pet/activities）与常驻活动（/pet/events）是两套体系，本页只做列表与领取。
 */
import { useCallback, useEffect, useState } from 'react'
import { App, Input, Spin } from 'antd'
import {
    blockPetUser,
    claimPetActivity,
    getPetAnniversaries,
    getPetBattle,
    getPetShareCard,
    listPetActivities,
    listPetBattleHistory,
    listPetBlocks,
    reportPetTarget,
    unblockPetUser,
    type PetAnniversary,
    type PetBattleItem,
    type PetShareCard,
} from '@/api/pet'
import type { ApiResponse } from '@/types/api'
import { CreamButton, CreamChip } from './Cream'
import styles from './miscBoard.module.css'

type MiscTab = 'anniversary' | 'share' | 'battle' | 'activities' | 'safety'

const MISC_TABS: Array<{ key: MiscTab; label: string }> = [
    { key: 'anniversary', label: '纪念日' },
    { key: 'share', label: '分享卡片' },
    { key: 'battle', label: '战报' },
    { key: 'activities', label: '限时活动' },
    { key: 'safety', label: '屏蔽与举报' },
]

/** 分享卡片类型（getPetShareCard 的入参联合；PetShareCard.type 是回显字符串，不能反推入参） */
type ShareType = 'LEVEL_UP' | 'ACHIEVEMENT' | 'BOTTLE' | 'BATTLE' | 'DAILY'

const SHARE_TYPES: Array<{ value: ShareType; label: string }> = [
    { value: 'DAILY', label: '日常' },
    { value: 'LEVEL_UP', label: '升级' },
    { value: 'ACHIEVEMENT', label: '成就' },
    { value: 'BOTTLE', label: '捞瓶' },
    { value: 'BATTLE', label: '对战' },
]

/** 举报目标类型白名单（与 PetBlockReportController 的 allowed 集合一致） */
const REPORT_TYPES: Array<{ value: string; label: string }> = [
    { value: 'WALL_MESSAGE', label: '留言墙内容' },
    { value: 'BOTTLE_CONTENT', label: '漂流瓶内容' },
    { value: 'NICKNAME', label: '昵称' },
]

/** 限时活动行（PetActivityVO 的前端收敛） */
interface ActivityRow {
    activityId: number | string
    petName: string | null
    activityType: string
    configName: string | null
    status: string
    remainingSeconds: number
    canClaim: boolean
    claimedAt: string | null
}

/** 战报回合流水（rounds JSON 的行结构） */
interface BattleRoundRow {
    round?: number
    actorName?: string
    damage?: number
    critical?: boolean
    dodged?: boolean
    targetName?: string
    targetRemainingHp?: number
}

const ACTIVITY_STATUS: Record<string, string> = {
    ACTIVE: '进行中',
    COMPLETED: '可领取',
    CLAIMED: '已领取',
    EXPIRED: '已过期',
}

export default function MiscBoard({ onChanged }: { onChanged?: () => void }) {
    const { message } = App.useApp()
    const [tab, setTab] = useState<MiscTab>('anniversary')
    const [busy, setBusy] = useState<string | null>(null)

    const [anniversary, setAnniversary] = useState<PetAnniversary | null>(null)
    const [share, setShare] = useState<PetShareCard | null>(null)
    const [history, setHistory] = useState<PetBattleItem[] | null>(null)
    const [battle, setBattle] = useState<PetBattleItem | null>(null)
    const [activities, setActivities] = useState<ActivityRow[] | null>(null)

    const [blocks, setBlocks] = useState<number[] | null>(null)
    const [blockInput, setBlockInput] = useState('')
    const [reportType, setReportType] = useState('WALL_MESSAGE')
    const [reportTargetId, setReportTargetId] = useState('')
    const [reportReason, setReportReason] = useState('')

    const run = useCallback(async <T,>(
        key: string,
        task: () => Promise<{ data: ApiResponse<T> }>,
    ): Promise<ApiResponse<T> | null> => {
        setBusy(key)
        try {
            const { data: res } = await task()
            if (!res.success) {
                message.warning(res.error?.message ?? '操作未成功')
            }
            return res
        } finally {
            setBusy(null)
        }
    }, [message])

    const loadAnniversary = useCallback(async () => {
        const { data: res } = await getPetAnniversaries()
        if (res.success) {
            setAnniversary(res.data)
        }
    }, [])

    const loadHistory = useCallback(async () => {
        const { data: res } = await listPetBattleHistory({ page: 1, pageSize: 10 })
        if (res.success) {
            setHistory(res.data)
        }
    }, [])

    const loadActivities = useCallback(async () => {
        const { data: res } = await listPetActivities()
        if (res.success) {
            setActivities((res.data as unknown as ActivityRow[]) ?? [])
        }
    }, [])

    const loadBlocks = useCallback(async () => {
        const { data: res } = await listPetBlocks()
        if (res.success) {
            setBlocks(res.data ?? [])
        }
    }, [])

    useEffect(() => {
        if (tab === 'anniversary' && anniversary === null) {
            void loadAnniversary()
        }
        if (tab === 'battle' && history === null) {
            void loadHistory()
        }
        if (tab === 'activities' && activities === null) {
            void loadActivities()
        }
        if (tab === 'safety' && blocks === null) {
            void loadBlocks()
        }
    }, [activities, anniversary, blocks, history, loadActivities, loadAnniversary,
        loadBlocks, loadHistory, tab])

    const loadShare = useCallback(async (type: ShareType) => {
        setShare(null)
        const res = await run(`share:${type}`, () => getPetShareCard(type))
        if (res?.success) {
            setShare(res.data)
        }
    }, [run])

    const copyShare = useCallback(async () => {
        if (!share) {
            return
        }
        const text = [share.title, share.content, share.highlight].filter(Boolean).join('\n')
        try {
            await navigator.clipboard.writeText(text)
            message.success('文案已复制，去发帖页粘贴吧')
        } catch {
            message.warning('复制失败，请手动选择文本复制')
        }
    }, [message, share])

    const openBattle = useCallback(async (battleId: number | string) => {
        const res = await run(`battle:${battleId}`, () => getPetBattle(battleId))
        if (res?.success) {
            setBattle(res.data)
        }
    }, [run])

    const claimActivity = useCallback(async (activityId: number | string) => {
        const res = await run(`activity:${activityId}`, () => claimPetActivity(activityId))
        if (res?.success) {
            message.success('已领取')
            await loadActivities()
            onChanged?.()
        }
    }, [loadActivities, message, onChanged, run])

    const addBlock = useCallback(async () => {
        const target = blockInput.trim()
        if (!target) {
            message.warning('填一下要拉黑的用户 ID')
            return
        }
        const res = await run('block', () => blockPetUser(target))
        if (res?.success) {
            setBlockInput('')
            await loadBlocks()
        }
    }, [blockInput, loadBlocks, message, run])

    const removeBlock = useCallback(async (userId: number | string) => {
        const res = await run(`unblock:${userId}`, () => unblockPetUser(userId))
        if (res?.success) {
            await loadBlocks()
        }
    }, [loadBlocks, run])

    const submitReport = useCallback(async () => {
        const targetId = reportTargetId.trim()
        const reason = reportReason.trim()
        if (!targetId || !reason) {
            message.warning('目标 ID 和理由都要填')
            return
        }
        const res = await run('report', () => reportPetTarget({
            targetType: reportType,
            targetId,
            reason,
        }))
        if (res?.success) {
            setReportTargetId('')
            setReportReason('')
            message.success('已提交，管理员会处理')
        }
    }, [message, reportReason, reportTargetId, reportType, run])

    const milestoneText = (daysToGo: number): string => {
        if (daysToGo === 0) {
            return '就是今天！'
        }
        return daysToGo > 0 ? `还有 ${daysToGo} 天` : `已达成 ${-daysToGo} 天`
    }

    const rounds = (() => {
        if (!battle?.rounds) {
            return [] as BattleRoundRow[]
        }
        try {
            return JSON.parse(battle.rounds) as BattleRoundRow[]
        } catch {
            return [] as BattleRoundRow[]
        }
    })()

    return (
        <div>
            <div className={styles.tabs}>
                {MISC_TABS.map(item => (
                    <button
                        key={item.key}
                        type="button"
                        className={`${styles.tab} ${tab === item.key ? styles.tabActive : ''}`}
                        onClick={() => setTab(item.key)}
                    >
                        {item.label}
                    </button>
                ))}
            </div>

            {tab === 'anniversary' ? (
                anniversary === null ? <Spin /> : (
                    <>
                        <div className={styles.annCard}>
                            <p className={styles.annDays}>{anniversary.adoptionDays} 天</p>
                            <p className={styles.annLabel}>领养至今 · 连续陪伴 {anniversary.companionStreak} 天</p>
                        </div>
                        <div className={styles.block}>
                            <div className={styles.milestoneRow}>
                                <span>当前里程碑</span>
                                <span>
                                    {anniversary.currentMilestone
                                        ? `${anniversary.currentMilestone.title}（${milestoneText(anniversary.currentMilestone.daysToGo)}）`
                                        : '暂无'}
                                </span>
                            </div>
                            <div className={styles.milestoneRow}>
                                <span>下一个</span>
                                <span>
                                    {anniversary.nextMilestone
                                        ? `${anniversary.nextMilestone.title}（${milestoneText(anniversary.nextMilestone.daysToGo)}）`
                                        : '全部达成'}
                                </span>
                            </div>
                        </div>
                        <p className={styles.note}>百日与周年当天会有提醒推送。</p>
                    </>
                )
            ) : null}

            {tab === 'share' ? (
                <>
                    <div className={styles.picks}>
                        {SHARE_TYPES.map(item => (
                            <button
                                key={item.value}
                                type="button"
                                className={styles.pick}
                                onClick={() => void loadShare(item.value)}
                            >
                                {item.label}
                            </button>
                        ))}
                    </div>
                    {share === null ? (
                        <p className={styles.empty}>选一种卡片生成文案</p>
                    ) : (
                        <div className={styles.block}>
                            <div className={styles.shareCard}>
                                <p className={styles.shareTitle}>{share.title}</p>
                                <p className={styles.shareContent}>{share.content}</p>
                                {share.highlight ? (
                                    <p className={styles.shareHighlight}>{share.highlight}</p>
                                ) : null}
                            </div>
                            <div className={styles.rowActions} style={{ marginTop: 12 }}>
                                <CreamButton onClick={() => void copyShare()}>复制文案</CreamButton>
                            </div>
                            <p className={styles.note}>复制后到社区发帖页粘贴即可分享。</p>
                        </div>
                    )}
                </>
            ) : null}

            {tab === 'battle' ? (
                <>
                    {battle ? (
                        <div className={styles.block}>
                            <p className={styles.blockTitle}>战报详情</p>
                            <div className={styles.battleHead}>
                                <span>{battle.attackerPetName ?? '未知'}</span>
                                <span className={styles.battleVs}>VS</span>
                                <span>{battle.defenderPetName ?? '野生'}</span>
                            </div>
                            <div className={styles.battleMeta}>
                                <span>{battle.mode === 'PVE' ? 'PVE' : 'PVP'}</span>
                                <span>状态 {battle.status}</span>
                                <span>胜者 {battle.winnerPetId ? (battle.winnerPetId === battle.attackerPetId ? '进攻方' : '防守方') : '无'}</span>
                                <span>经验 +{battle.expReward}</span>
                                <span>宠物币 +{battle.currencyReward}</span>
                            </div>
                            {rounds.length > 0 ? (
                                <div className={styles.block}>
                                    {rounds.map((row, index) => (
                                        <div key={index} className={styles.row}>
                                            <div className={styles.rowMain}>
                                                <p className={styles.rowTitle}>
                                                    第 {row.round ?? index + 1} 回合 · {row.actorName ?? '—'}
                                                    {row.critical ? <CreamChip color="#E93B72">暴击</CreamChip> : null}
                                                    {row.dodged ? <CreamChip color="#8AA5BC">闪避</CreamChip> : null}
                                                </p>
                                                <p className={styles.rowDesc}>
                                                    {row.dodged ? '被闪避了' : `造成 ${row.damage ?? 0} 伤害`}
                                                    {row.targetName ? ` → ${row.targetName}（剩余 ${row.targetRemainingHp ?? '?'}）` : ''}
                                                </p>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            ) : null}
                            <div className={styles.rowActions} style={{ marginTop: 12 }}>
                                <CreamButton variant="ghost" onClick={() => setBattle(null)}>返回列表</CreamButton>
                            </div>
                        </div>
                    ) : history === null ? <Spin /> : history.length === 0 ? (
                        <p className={styles.empty}>还没有对战记录</p>
                    ) : (
                        <div className={styles.block}>
                            <p className={styles.blockTitle}>最近对局</p>
                            {history.map(item => (
                                <div key={String(item.battleId)} className={styles.row}>
                                    <div className={styles.rowMain}>
                                        <p className={styles.rowTitle}>
                                            {item.attackerPetName ?? '未知'} VS {item.defenderPetName ?? '野生'}
                                            <CreamChip color={item.mode === 'PVE' ? '#8AA5BC' : '#D89AA0'}>
                                                {item.mode}
                                            </CreamChip>
                                        </p>
                                        <p className={styles.rowDesc}>
                                            {item.status === 'FINISHED' ? '已结束' : item.status}
                                            {item.finishedAt ? ` · ${item.finishedAt.slice(5, 16).replace('T', ' ')}` : ''}
                                        </p>
                                    </div>
                                    <div className={styles.rowActions}>
                                        <CreamButton
                                            variant="ghost"
                                            loading={busy === `battle:${item.battleId}`}
                                            onClick={() => void openBattle(item.battleId)}
                                        >
                                            详情
                                        </CreamButton>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </>
            ) : null}

            {tab === 'activities' ? (
                activities === null ? <Spin /> : (
                    <div className={styles.block}>
                        <p className={styles.blockTitle}>限时活动</p>
                        <p className={styles.blockDesc}>与常驻活动是两套体系；结束后 72 小时内可领奖</p>
                        {activities.length === 0 ? (
                            <p className={styles.empty}>当前没有进行中的活动</p>
                        ) : activities.map(item => (
                            <div key={String(item.activityId)} className={styles.row}>
                                <div className={styles.rowMain}>
                                    <p className={styles.rowTitle}>
                                        {item.configName ?? item.activityType}
                                        <CreamChip color={item.canClaim ? '#7E9270' : '#A97C50'}>
                                            {ACTIVITY_STATUS[item.status] ?? item.status}
                                        </CreamChip>
                                    </p>
                                    <p className={styles.rowDesc}>
                                        {item.petName ? `宠物：${item.petName}` : ''}
                                        {item.remainingSeconds > 0 ? ` · 剩余 ${Math.ceil(item.remainingSeconds / 60)} 分钟` : ''}
                                    </p>
                                </div>
                                <div className={styles.rowActions}>
                                    {item.canClaim ? (
                                        <CreamButton
                                            loading={busy === `activity:${item.activityId}`}
                                            onClick={() => void claimActivity(item.activityId)}
                                        >
                                            领取
                                        </CreamButton>
                                    ) : null}
                                </div>
                            </div>
                        ))}
                    </div>
                )
            ) : null}

            {tab === 'safety' ? (
                blocks === null ? <Spin /> : (
                    <>
                        <div className={styles.block}>
                            <p className={styles.blockTitle}>屏蔽名单</p>
                            <p className={styles.blockDesc}>
                                屏蔽后双方不能新增拜访收益、挑战、留言与好友申请
                            </p>
                            {blocks.length === 0 ? (
                                <p className={styles.empty}>名单是空的</p>
                            ) : blocks.map(userId => (
                                <div key={userId} className={styles.row}>
                                    <div className={styles.rowMain}>
                                        <p className={styles.rowTitle}>用户 #{userId}</p>
                                    </div>
                                    <div className={styles.rowActions}>
                                        <CreamButton
                                            variant="ghost"
                                            loading={busy === `unblock:${userId}`}
                                            onClick={() => void removeBlock(userId)}
                                        >
                                            解除
                                        </CreamButton>
                                    </div>
                                </div>
                            ))}
                            <div className={styles.inputRow}>
                                <Input
                                    className={styles.input}
                                    value={blockInput}
                                    onChange={event => setBlockInput(event.target.value)}
                                    placeholder="用户 ID"
                                />
                                <CreamButton loading={busy === 'block'} onClick={() => void addBlock()}>
                                    拉黑
                                </CreamButton>
                            </div>
                        </div>
                        <div className={styles.block}>
                            <p className={styles.blockTitle}>举报</p>
                            <p className={styles.blockDesc}>进入管理员处理队列，处理结果以站内通知为准</p>
                            <div className={styles.picks}>
                                {REPORT_TYPES.map(item => (
                                    <button
                                        key={item.value}
                                        type="button"
                                        className={`${styles.pick} ${reportType === item.value ? styles.pickActive : ''}`}
                                        onClick={() => setReportType(item.value)}
                                    >
                                        {item.label}
                                    </button>
                                ))}
                            </div>
                            <div className={styles.inputRow}>
                                <Input
                                    className={styles.input}
                                    value={reportTargetId}
                                    onChange={event => setReportTargetId(event.target.value)}
                                    placeholder="目标 ID（留言/瓶子/用户）"
                                />
                            </div>
                            <div className={styles.inputRow}>
                                <Input
                                    className={styles.input}
                                    value={reportReason}
                                    onChange={event => setReportReason(event.target.value)}
                                    placeholder="理由（200 字内）"
                                    maxLength={200}
                                />
                                <CreamButton loading={busy === 'report'} onClick={() => void submitReport()}>
                                    提交
                                </CreamButton>
                            </div>
                        </div>
                    </>
                )
            ) : null}
        </div>
    )
}
