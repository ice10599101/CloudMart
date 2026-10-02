/**
 * 玩法面板（N 系列）：接球小游戏 / 寄养 / 协作 / 图鉴 / 离线摘要。
 *
 * 接球的真实规则（PetPlayFeatureService）：10 个窗口、每窗 3 秒（末窗到 deadline，另有 2 秒宽限），
 * **目标序列是展示数据**——服务端防滥用靠时窗/窗口序号前进/额度，不靠序列保密。
 * 所以界面把序列当提示显示，命中与否由服务端按"提交时刻是否落在该窗区间"判定，
 * 客户端算出来的窗口号只是给玩家看的参考，错窗提交会被服务端丢弃（不计成功数）。
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { App, Spin } from 'antd'
import {
    acceptCooperation,
    claimCooperationReward,
    confirmOfflineDigest,
    createCooperation,
    endCustody,
    getCustodyStatus,
    getCollection,
    getCollectionStats,
    getOfflineDigest,
    getPetFriends,
    leaveCooperation,
    listCooperations,
    listMinigameRounds,
    settleMinigame,
    startCustody,
    startMinigameRound,
    submitMinigameOps,
    type MinigameRoundVO,
    type PetMinigameRoundItem,
} from '@/api/pet'
import type { ApiResponse } from '@/types/api'
import { CreamButton, CreamChip } from './Cream'
import styles from './playBoard.module.css'

type PlayTab = 'minigame' | 'custody' | 'cooperation' | 'collection' | 'digest'

const PLAY_TABS: Array<{ key: PlayTab; label: string }> = [
    { key: 'minigame', label: '接球' },
    { key: 'custody', label: '寄养' },
    { key: 'cooperation', label: '协作' },
    { key: 'collection', label: '图鉴' },
    { key: 'digest', label: '离线摘要' },
]

/** 与服务端一致：10 个窗口、整局 30 秒 */
const CATCH_WINDOWS = 10
const ROUND_MS = 30_000
const WINDOW_MS = 3_000

const SLOT_LABEL: Record<string, string> = { LEFT: '左', CENTER: '中', RIGHT: '右' }
const SLOT_MARK: Record<string, string> = { LEFT: '←', CENTER: '●', RIGHT: '→' }
const SLOTS = ['LEFT', 'CENTER', 'RIGHT'] as const

/** 图鉴条目（后端返回 Map，前端按已知键收敛） */
interface CollectionItem {
    category: string
    itemCode: string
    rarity: string
    unlocked: boolean
    resourceKey?: string
    unlockCondition?: string
    hint?: string
}

/** 协作行（后端返回实体序列化，字段按需取值） */
interface CooperationRow {
    id: number | string
    inviterUserId?: number | string
    inviteeUserId?: number | string
    status?: string
    createdAt?: string
}

interface CustodyStatus {
    active: boolean
    endsAt?: string
    careFeedUsed?: number
    careCleanUsed?: number
    weekUsed?: boolean
    nextAvailableAt?: string
}

interface OfflineDigest {
    from: string
    throughAt: string
    offlineHours: number
    finishedTasks: number
    claimableTasks: number
    visits: number
    milestones: number
    hasCursor: boolean
}

const COOP_STATUS_LABEL: Record<string, string> = {
    INVITED: '待接受',
    ACTIVE: '进行中',
    COMPLETED: '已完成',
    EXPIRED: '已过期',
}

function fmtClock(iso: string | undefined): string {
    if (!iso) {
        return '—'
    }
    return iso.slice(5, 16).replace('T', ' ')
}

export default function PlayBoard({ myPetId, onChanged }: {
    myPetId: number | string
    onChanged?: () => void
}) {
    const { message } = App.useApp()
    const [tab, setTab] = useState<PlayTab>('minigame')
    const [busy, setBusy] = useState<string | null>(null)

    const [round, setRound] = useState<MinigameRoundVO | null>(null)
    const [history, setHistory] = useState<PetMinigameRoundItem[] | null>(null)
    const [remainMs, setRemainMs] = useState(0)
    const [windowNow, setWindowNow] = useState(1)
    const [hits, setHits] = useState(0)
    const [result, setResult] = useState<{
        status: string
        successCount: number
        rewardEligible: boolean
        validCompletion: boolean
        reward: Record<string, number>
    } | null>(null)
    const seqRef = useRef(0)

    const [custody, setCustody] = useState<CustodyStatus | null>(null)
    const [coops, setCoops] = useState<CooperationRow[] | null>(null)
    const [friends, setFriends] = useState<Array<{ userId: number; nickname: string }> | null>(null)

    const [items, setItems] = useState<CollectionItem[] | null>(null)
    const [stats, setStats] = useState<{ total: number; unlocked: number } | null>(null)
    const [category, setCategory] = useState<string | null>(null)
    const [page, setPage] = useState(1)

    const [digest, setDigest] = useState<OfflineDigest | null>(null)

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

    const loadCustody = useCallback(async () => {
        const { data: res } = await getCustodyStatus()
        if (res.success) {
            setCustody(res.data)
        }
    }, [])

    /** 对局历史（N04）：后端已切展示投影 VO，这里直接用强类型（offset 分页取第一页） */
    const loadHistory = useCallback(async () => {
        const { data: res } = await listMinigameRounds(1, 10)
        if (res.success) {
            setHistory(res.data ?? [])
        }
    }, [])

    const loadCoops = useCallback(async () => {
        const { data: res } = await listCooperations()
        if (res.success) {
            setCoops((res.data as unknown as CooperationRow[]) ?? [])
        }
    }, [])

    const loadFriends = useCallback(async () => {
        const { data: res } = await getPetFriends()
        if (res.success) {
            setFriends(res.data.friends.map(item => ({ userId: item.userId, nickname: item.nickname })))
        }
    }, [])

    const loadCollection = useCallback(async (nextPage: number, nextCategory: string | null) => {
        const [{ data: listRes }, { data: statsRes }] = await Promise.all([
            getCollection(nextCategory ?? undefined, nextPage, 20),
            getCollectionStats(),
        ])
        if (listRes.success) {
            setItems((listRes.data as unknown as CollectionItem[]) ?? [])
            setPage(nextPage)
        }
        if (statsRes.success) {
            const raw = statsRes.data as Record<string, unknown>
            setStats({
                total: Number(raw.total ?? 0),
                unlocked: Number(raw.unlocked ?? 0),
            })
        }
    }, [])

    const loadDigest = useCallback(async () => {
        const { data: res } = await getOfflineDigest()
        if (res.success) {
            setDigest(res.data)
        }
    }, [])

    useEffect(() => {
        if (tab === 'minigame' && history === null) {
            void loadHistory()
        }
        if (tab === 'custody' && custody === null) {
            void loadCustody()
        }
        if (tab === 'cooperation') {
            if (coops === null) {
                void loadCoops()
            }
            if (friends === null) {
                void loadFriends()
            }
        }
        if (tab === 'collection' && items === null) {
            void loadCollection(1, null)
        }
        if (tab === 'digest' && digest === null) {
            void loadDigest()
        }
    }, [coops, custody, digest, friends, history, items, loadCollection, loadCoops, loadCustody,
        loadDigest, loadFriends, loadHistory, tab])

    /** 回合倒计时：同时推进"当前窗口"（客户端参考值，判定向来在服务端） */
    useEffect(() => {
        if (!round) {
            return
        }
        const deadline = Date.parse(round.deadlineAt)
        const tick = () => {
            const left = Math.max(0, deadline - Date.now())
            setRemainMs(left)
            const elapsed = ROUND_MS - left
            setWindowNow(Math.min(CATCH_WINDOWS, Math.floor(elapsed / WINDOW_MS) + 1))
            if (left === 0) {
                setRound(null)
            }
        }
        tick()
        const timer = window.setInterval(tick, 200)
        return () => window.clearInterval(timer)
    }, [round])

    const startRound = useCallback(async () => {
        setResult(null)
        setHits(0)
        seqRef.current = 0
        const res = await run('round', () => startMinigameRound(myPetId))
        if (res?.success) {
            setRound(res.data)
            message.success(res.data.rewardEligible ? '开局！这局有收益' : '开局（今日收益局已用完，训练局）')
        }
    }, [message, myPetId, run])

    const tapSlot = useCallback(async (slot: string) => {
        if (!round) {
            return
        }
        seqRef.current += 1
        const res = await run('ops', () => submitMinigameOps(round.roundId, [{
            seq: seqRef.current,
            windowIndex: windowNow,
            slot,
        }]))
        if (res?.success) {
            const accepted = Number((res.data as { accepted?: number }).accepted ?? 0)
            if (accepted > 0) {
                setHits(prev => prev + accepted)
                message.success('接到了！')
            } else {
                message.warning('没接住 —— 窗口没对上或已过期')
            }
        }
    }, [message, round, run, windowNow])

    const settle = useCallback(async () => {
        if (!round) {
            return
        }
        const res = await run('settle', () => settleMinigame(round.roundId))
        if (res?.success) {
            setResult(res.data)
            setRound(null)
            await loadHistory()
            onChanged?.()
        }
    }, [loadHistory, onChanged, round, run])

    const toggleCustody = useCallback(async () => {
        if (custody?.active) {
            const res = await run('custody', () => endCustody())
            if (res?.success) {
                message.success('已接它回家')
                await loadCustody()
                onChanged?.()
            }
            return
        }
        const res = await run('custody', () => startCustody())
        if (res?.success) {
            message.success('已送去寄养，会有人照顾它')
            await loadCustody()
            onChanged?.()
        }
    }, [custody?.active, loadCustody, message, onChanged, run])

    const inviteCoop = useCallback(async (userId: number | string) => {
        const res = await run(`coop:${userId}`, () => createCooperation(userId))
        if (res?.success) {
            message.success('协作邀请已发出')
            await loadCoops()
        }
    }, [loadCoops, message, run])

    const acceptCoop = useCallback(async (id: number | string) => {
        const res = await run(`coop-accept:${id}`, () => acceptCooperation(id))
        if (res?.success) {
            message.success('已接受协作')
            await loadCoops()
        }
    }, [loadCoops, message, run])

    const leaveCoop = useCallback(async (id: number | string) => {
        const res = await run(`coop-leave:${id}`, () => leaveCooperation(id))
        if (res?.success) {
            await loadCoops()
        }
    }, [loadCoops, run])

    const claimCoop = useCallback(async (id: number | string) => {
        const res = await run(`coop-claim:${id}`, () => claimCooperationReward(id))
        if (res?.success) {
            const reward = (res.data as unknown as { reward?: { type?: string; amount?: number } }).reward
            message.success(reward ? `已领取 ${reward.type ?? '奖励'} ${reward.amount ?? ''}`.trim() : '已领取')
            await loadCoops()
            onChanged?.()
        }
    }, [loadCoops, message, onChanged, run])

    const confirmDigest = useCallback(async () => {
        const res = await run('digest', () => confirmOfflineDigest(digest?.hasCursor ? digest.throughAt : undefined))
        if (res?.success) {
            message.success('已确认')
            await loadDigest()
        }
    }, [digest?.hasCursor, digest?.throughAt, loadDigest, message, run])

    const categories = useMemo(() => {
        if (!items) {
            return [] as string[]
        }
        return Array.from(new Set(items.map(item => item.category)))
    }, [items])

        return (
        <div>
            <div className={styles.tabs}>
                {PLAY_TABS.map(item => (
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

            {tab === 'minigame' ? (
                <>
                    <p className={styles.blockDesc}>
                        球会依次落到十个窗口，看清提示、在窗口亮起时按对应的方向。
                        每日前 5 局有收益（扣 15 精力），之后是训练局。
                    </p>
                    {round ? (
                        <div className={styles.gameCard}>
                            <div className={styles.gameHead}>
                                <span>第 {windowNow} / {CATCH_WINDOWS} 窗</span>
                                <span className={styles.timer}>剩余 {(remainMs / 1000).toFixed(1)}s</span>
                                <span>已接到 {hits}</span>
                                {round.rewardEligible ? (
                                    <CreamChip color="#7E9270">有收益局</CreamChip>
                                ) : (
                                    <CreamChip color="#8AA5BC">训练局</CreamChip>
                                )}
                            </div>
                            <div className={styles.gameStrip}>
                                {round.sequence.map((slot, index) => {
                                    const windowIndex = index + 1
                                    const done = windowIndex < windowNow
                                    const now = windowIndex === windowNow
                                    return (
                                        <div
                                            key={windowIndex}
                                            className={`${styles.gameCell} ${done ? styles.gameCellDone : ''} ${now ? styles.gameCellNow : ''}`}
                                        >
                                            {SLOT_MARK[slot] ?? '?'}
                                        </div>
                                    )
                                })}
                            </div>
                            <div className={styles.gameSlots}>
                                {SLOTS.map(slot => (
                                    <button
                                        key={slot}
                                        type="button"
                                        className={styles.slotBtn}
                                        disabled={busy === 'ops'}
                                        onClick={() => void tapSlot(slot)}
                                    >
                                        {SLOT_MARK[slot]} {SLOT_LABEL[slot]}
                                    </button>
                                ))}
                            </div>
                            <div className={styles.gameMeta}>
                                <span>规则 {round.ruleVersion}</span>
                                <span>截止 {fmtClock(round.deadlineAt)}</span>
                            </div>
                            <div className={styles.rowActions} style={{ marginTop: 12 }}>
                                <CreamButton loading={busy === 'settle'} onClick={() => void settle()}>结算</CreamButton>
                            </div>
                        </div>
                    ) : (
                        <div className={styles.rowActions}>
                            <CreamButton loading={busy === 'round'} onClick={() => void startRound()}>
                                开始一局
                            </CreamButton>
                        </div>
                    )}
                    {result ? (
                        <div className={styles.resultCard}>
                            本局接住 {result.successCount} 个 · 状态 {result.status}
                            {result.rewardEligible ? ' · 有收益' : ' · 无收益'}
                            {result.validCompletion ? '' : ' · 未完成有效局'}
                            {Object.keys(result.reward).length > 0 ? (
                                <>
                                    {' · 奖励 '}
                                    {Object.entries(result.reward).map(([key, value]) => `${key} ${value}`).join('，')}
                                </>
                            ) : null}
                        </div>
                    ) : null}

                    {history && history.length > 0 ? (
                        <div className={styles.block}>
                            <p className={styles.blockTitle}>最近对局</p>
                            {history.map(item => (
                                <div key={String(item.roundId)} className={styles.row}>
                                    <div className={styles.rowMain}>
                                        <p className={styles.rowTitle}>
                                            对局 #{String(item.roundId)}
                                            <CreamChip color={item.rewardEligible ? '#7E9270' : '#8AA5BC'}>
                                                {item.rewardEligible ? '有收益' : '训练局'}
                                            </CreamChip>
                                            <CreamChip color={item.status === 'SETTLED' ? '#A97C50' : '#D89AA0'}>
                                                {item.status ?? '—'}
                                            </CreamChip>
                                        </p>
                                        <p className={styles.rowDesc}>
                                            接住 {item.successCount ?? 0} / 10
                                            {item.startedAt ? ` · ${item.startedAt.slice(5, 16).replace('T', ' ')}` : ''}
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : null}
                </>
            ) : null}

            {tab === 'custody' ? (
                custody === null ? <Spin /> : (
                    <div className={styles.gameCard}>
                        <div className={styles.gameHead}>
                            <span>{custody.active ? '寄养中' : '在家'}</span>
                            <span>结束时间 {fmtClock(custody.endsAt)}</span>
                        </div>
                        <div className={styles.gameMeta}>
                            <span>代喂 {custody.careFeedUsed ?? 0} 次</span>
                            <span>代清洁 {custody.careCleanUsed ?? 0} 次</span>
                            <span>本周名额{custody.weekUsed ? '已用' : '未用'}</span>
                            <span>下次可寄养 {fmtClock(custody.nextAvailableAt)}</span>
                        </div>
                        <div className={styles.rowActions} style={{ marginTop: 12 }}>
                            <CreamButton loading={busy === 'custody'} onClick={() => void toggleCustody()}>
                                {custody.active ? '接回家' : '送去寄养'}
                            </CreamButton>
                        </div>
                        <p className={styles.note}>寄养期间由系统代照顾，结束后可随时接回。</p>
                    </div>
                )
            ) : null}

            {tab === 'cooperation' ? (
                coops === null ? <Spin /> : (
                    <>
                        <div className={styles.block}>
                            <p className={styles.blockTitle}>协作任务</p>
                            <p className={styles.blockDesc}>
                                和好友一起照顾：双方各贡献满 3 个不同业务日即可完成并领奖
                            </p>
                            {coops.length === 0 ? (
                                <p className={styles.empty}>还没有协作，从下面挑个好友发起吧</p>
                            ) : coops.map(item => (
                                <div key={String(item.id)} className={styles.row}>
                                    <div className={styles.rowMain}>
                                        <p className={styles.rowTitle}>
                                            协作 #{String(item.id)}
                                            <CreamChip color={item.status === 'COMPLETED' ? '#7E9270' : '#A97C50'}>
                                                {COOP_STATUS_LABEL[item.status ?? ''] ?? item.status ?? '—'}
                                            </CreamChip>
                                        </p>
                                        <p className={styles.rowDesc}>发起于 {fmtClock(item.createdAt)}</p>
                                    </div>
                                    <div className={styles.rowActions}>
                                        {item.status === 'INVITED' ? (
                                            <CreamButton
                                                loading={busy === `coop-accept:${item.id}`}
                                                onClick={() => void acceptCoop(item.id)}
                                            >
                                                接受
                                            </CreamButton>
                                        ) : null}
                                        {item.status === 'COMPLETED' ? (
                                            <CreamButton
                                                loading={busy === `coop-claim:${item.id}`}
                                                onClick={() => void claimCoop(item.id)}
                                            >
                                                领奖
                                            </CreamButton>
                                        ) : null}
                                        <CreamButton
                                            variant="ghost"
                                            loading={busy === `coop-leave:${item.id}`}
                                            onClick={() => void leaveCoop(item.id)}
                                        >
                                            退出
                                        </CreamButton>
                                    </div>
                                </div>
                            ))}
                        </div>
                        <div className={styles.block}>
                            <p className={styles.blockTitle}>邀请好友协作</p>
                            {friends === null ? <Spin /> : friends.length === 0 ? (
                                <p className={styles.empty}>还没有好友，先去社交里加一个</p>
                            ) : friends.map(item => (
                                <div key={item.userId} className={styles.row}>
                                    <span className={styles.rowIcon}>🐾</span>
                                    <div className={styles.rowMain}>
                                        <p className={styles.rowTitle}>{item.nickname}</p>
                                    </div>
                                    <div className={styles.rowActions}>
                                        <CreamButton
                                            variant="ghost"
                                            loading={busy === `coop:${item.userId}`}
                                            onClick={() => void inviteCoop(item.userId)}
                                        >
                                            邀请
                                        </CreamButton>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </>
                )
            ) : null}

            {tab === 'collection' ? (
                items === null ? <Spin /> : (
                    <>
                        <div className={styles.gameMeta} style={{ marginTop: 0 }}>
                            <span>已解锁 <strong>{stats?.unlocked ?? 0}</strong> / {stats?.total ?? 0}</span>
                        </div>
                        <div className={styles.picks} style={{ margin: '10px 0' }}>
                            <button
                                type="button"
                                className={`${styles.pick} ${category === null ? styles.pickActive : ''}`}
                                onClick={() => {
                                    setCategory(null)
                                    void loadCollection(1, null)
                                }}
                            >
                                全部
                            </button>
                            {categories.map(item => (
                                <button
                                    key={item}
                                    type="button"
                                    className={`${styles.pick} ${category === item ? styles.pickActive : ''}`}
                                    onClick={() => {
                                        setCategory(item)
                                        void loadCollection(1, item)
                                    }}
                                >
                                    {item}
                                </button>
                            ))}
                        </div>
                        {items.length === 0 ? (
                            <p className={styles.empty}>这一类还没有条目</p>
                        ) : (
                            <div className={styles.gridCards}>
                                {items.map(item => (
                                    <div
                                        key={`${item.category}:${item.itemCode}`}
                                        className={`${styles.collectCard} ${item.unlocked ? '' : styles.collectLocked}`}
                                    >
                                        {item.itemCode}
                                        <span className={styles.collectMeta}>
                                            {item.category} · {item.rarity}
                                        </span>
                                        <span className={styles.collectMeta}>
                                            {item.unlocked
                                                ? (item.unlockCondition ?? '已解锁')
                                                : (item.hint ?? '未解锁')}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        )}
                        <div className={styles.pager}>
                            <CreamButton
                                variant="ghost"
                                disabled={page <= 1}
                                onClick={() => void loadCollection(page - 1, category)}
                            >
                                上一页
                            </CreamButton>
                            <span>第 {page} 页</span>
                            <CreamButton
                                variant="ghost"
                                disabled={items.length < 20}
                                onClick={() => void loadCollection(page + 1, category)}
                            >
                                下一页
                            </CreamButton>
                        </div>
                    </>
                )
            ) : null}

            {tab === 'digest' ? (
                digest === null ? <Spin /> : (
                    <div className={styles.gameCard}>
                        <div className={styles.gameHead}>
                            <span>离线 {digest.offlineHours} 小时</span>
                            <span>{fmtClock(digest.from)} → {fmtClock(digest.throughAt)}</span>
                        </div>
                        <div className={styles.gameMeta}>
                            <span>完成任务 {digest.finishedTasks}</span>
                            <span>待领 {digest.claimableTasks}</span>
                            <span>来访 {digest.visits}</span>
                            <span>里程碑 {digest.milestones}</span>
                        </div>
                        <div className={styles.rowActions} style={{ marginTop: 12 }}>
                            <CreamButton loading={busy === 'digest'} onClick={() => void confirmDigest()}>
                                确认摘要
                            </CreamButton>
                        </div>
                        <p className={styles.note}>
                            摘要只做回顾，待领奖励仍在各自的面板里领取。
                        </p>
                    </div>
                )
            ) : null}

        </div>
    )
}
