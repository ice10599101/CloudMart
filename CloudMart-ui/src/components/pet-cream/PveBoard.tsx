/**
 * R19 协作 PVE 副本面板（对照 Taro 端 pages/pve 移植，契约对齐 mall-pet PetPveController/VO）。
 *
 * 结构：招募大厅（Boss 列表发起 + OPEN 副本加入）/ 我的副本；点击卡片进详情。
 * 详情展示 Boss 与双方宠物 HP、回合流水；行动权完全以服务端 canAttack/blockedReason 为准。
 * OPEN/FIGHTING 期间 5 秒轮询详情，组件卸载或进入终态即清理定时器。
 */
import { useCallback, useEffect, useState } from 'react'
import { App, Spin } from 'antd'
import {
    attackPveRun,
    cancelPveRun,
    getPveRun,
    joinPveRun,
    listMyPveRuns,
    listOpenPveRuns,
    listPveBosses,
    startPveRun,
    type PetPveBoss,
    type PetPveRun,
} from '@/api/pet'
import { useAuthStore } from '@/stores/auth'
import { CreamButton, CreamChip, STAT_TONE } from './Cream'
import styles from './r19Board.module.css'

/** 轮询节奏对齐 M 端手动刷新体感：5 秒一拍，卸载/终态清理 */
const POLL_INTERVAL_MS = 5000
const PAGE_SIZE = 20

const STATUS_LABEL: Record<string, string> = {
    OPEN: '等待队友',
    FIGHTING: '战斗中',
    WON: '讨伐成功',
    FAILED: '副本失败',
    EXPIRED: '已过期',
    CANCELLED: '已取消',
}

const STATUS_TONE: Record<string, string> = {
    OPEN: '#5A7CC4',
    FIGHTING: '#E0A45C',
    WON: '#7E9270',
    FAILED: '#D98A8A',
    EXPIRED: '#9C8D7E',
    CANCELLED: '#9C8D7E',
}

const BLOCKED_LABEL: Record<string, string> = {
    NOT_YOUR_TURN: '还没轮到你行动',
    NOT_FIGHTING: '副本不在战斗中',
    RUN_OVER: '副本已结束',
}

function fmtTime(iso: string | null | undefined): string {
    if (!iso) {
        return '—'
    }
    return iso.slice(5, 16).replace('T', ' ')
}

function hpPercent(hp: number, maxHp: number): number {
    if (maxHp <= 0) {
        return 0
    }
    return Math.max(0, Math.min(100, Math.round((hp / maxHp) * 100)))
}

function RunCard({ run, busyJoin, onOpen, onJoin }: {
    run: PetPveRun
    busyJoin: boolean
    onOpen: () => void
    onJoin?: () => void
}) {
    return (
        <div className={styles.row} style={{ cursor: 'pointer' }} onClick={onOpen}>
            <span className={styles.rowIcon}>👹</span>
            <div className={styles.rowMain}>
                <p className={styles.rowTitle}>
                    {run.bossName}
                    <CreamChip color={STATUS_TONE[run.status] ?? STAT_TONE.cleanliness}>
                        {STATUS_LABEL[run.status] ?? run.status}
                    </CreamChip>
                </p>
                <p className={styles.rowDesc}>Boss HP {run.bossHp}/{run.bossMaxHp}</p>
                <p className={styles.rowDesc}>
                    发起人宠物 {run.initiatorPetName ?? '—'}
                    {run.partnerPetName ? ` × ${run.partnerPetName}` : ' · 等待队友'}
                </p>
            </div>
            {run.status === 'OPEN' && onJoin ? (
                <div className={styles.rowActions} onClick={event => event.stopPropagation()}>
                    <CreamButton variant="ghost" loading={busyJoin} onClick={onJoin}>加入战斗</CreamButton>
                </div>
            ) : null}
        </div>
    )
}

function PetHpLine({ name, hp, maxHp }: { name: string; hp: number; maxHp: number }) {
    return (
        <div className={styles.petHpRow}>
            <span style={{ minWidth: 96 }}>{name}</span>
            <div className={styles.hpTrackMini}>
                <div className={styles.hpFillMini} style={{ width: `${hpPercent(hp, maxHp)}%` }} />
            </div>
            <span>{hp}/{maxHp}</span>
        </div>
    )
}

function PveDetail({ run, busy, isInitiator, isParticipant, onBack, onJoin, onAttack, onCancel }: {
    run: PetPveRun
    busy: string | null
    isInitiator: boolean
    isParticipant: boolean
    onBack: () => void
    onJoin: () => void
    onAttack: () => void
    onCancel: () => void
}) {
    const fighting = run.status === 'FIGHTING'
    const terminal = ['WON', 'FAILED', 'EXPIRED', 'CANCELLED'].includes(run.status)
    return (
        <div>
            <div className={styles.detailBack}>
                <CreamButton variant="ghost" onClick={onBack}>← 返回列表</CreamButton>
            </div>
            <p className={styles.detailTitle}>
                👹 {run.bossName}
                <CreamChip color={STATUS_TONE[run.status] ?? STAT_TONE.cleanliness}>
                    {STATUS_LABEL[run.status] ?? run.status}
                </CreamChip>
            </p>
            <div className={styles.hpTrack}>
                <div className={styles.hpFill} style={{ width: `${hpPercent(run.bossHp, run.bossMaxHp)}%` }} />
            </div>
            <p className={styles.hpText}>Boss HP {run.bossHp}/{run.bossMaxHp}</p>

            <PetHpLine name={run.initiatorPetName ?? '发起人'} hp={run.initiatorPetHp} maxHp={run.initiatorPetMaxHp} />
            {run.partnerPetHp >= 0 ? (
                <PetHpLine name={run.partnerPetName ?? '队友'} hp={run.partnerPetHp} maxHp={run.partnerPetMaxHp} />
            ) : (
                <p className={styles.detailMeta}>队友：等待加入</p>
            )}

            <p className={styles.detailMeta}>
                已完成回合：{run.turnNo}
                {run.turnDeadlineAt ? ` · 本回合截止 ${fmtTime(run.turnDeadlineAt)}` : ''}
                {run.runExpiresAt ? ` · 副本时限至 ${fmtTime(run.runExpiresAt)}` : ''}
            </p>
            {fighting && run.canAttack ? (
                <p className={styles.detailMeta} style={{ color: '#7E9270' }}>轮到你行动了</p>
            ) : fighting && run.blockedReason ? (
                <p className={styles.detailMeta}>{BLOCKED_LABEL[run.blockedReason] ?? run.blockedReason}</p>
            ) : null}

            <div className={styles.rounds}>
                {run.recentRounds.length === 0 ? (
                    <p className={styles.roundLine}>战斗尚未开始</p>
                ) : run.recentRounds.map((round, index) => (
                    <p key={`${round.round}-${index}`} className={styles.roundLine}>
                        {`R${round.round} ${round.actorName} ${round.action}${round.critical ? '（暴击）' : ''}${round.dodged ? ' 被闪避' : ''} → ${round.targetName}，造成 ${round.damage} 点伤害（余 ${round.targetRemainingHp}）`}
                    </p>
                ))}
            </div>

            {run.status === 'WON' ? (
                <p className={styles.note} style={{ color: '#7E9270' }}>
                    {run.rewardStatus === 'SETTLED' || run.rewardGranted
                        ? '🎉 讨伐成功，奖励已发放'
                        : run.rewardStatus === 'PENDING' ? '讨伐成功，奖励发放中…' : '讨伐成功'}
                </p>
            ) : null}
            {terminal && run.finishedAt ? (
                <p className={styles.note}>结束时间：{fmtTime(run.finishedAt)}</p>
            ) : null}

            <div className={styles.detailActions}>
                {fighting && isParticipant && run.canAttack ? (
                    <CreamButton loading={busy === 'attack'} onClick={onAttack}>⚔️ 攻击</CreamButton>
                ) : null}
                {run.status === 'OPEN' && !isParticipant ? (
                    <CreamButton loading={busy === `join:${String(run.runId)}`} onClick={onJoin}>
                        ⚔️ 加入战斗
                    </CreamButton>
                ) : null}
                {run.status === 'OPEN' && isInitiator ? (
                    <CreamButton variant="ghost" loading={busy === 'cancel'} onClick={onCancel}>取消副本</CreamButton>
                ) : null}
            </div>
            <p className={styles.note}>双方轮流行动；Boss 倒下后奖励自动发放，详情每 5 秒自动刷新。</p>
        </div>
    )
}

export default function PveBoard({ enabled }: { enabled: boolean }) {
    const { message } = App.useApp()
    const currentUserId = useAuthStore(state => state.user?.id ?? null)
    const [tab, setTab] = useState<'lobby' | 'mine'>('lobby')
    const [bosses, setBosses] = useState<PetPveBoss[] | null>(null)
    const [openRuns, setOpenRuns] = useState<PetPveRun[]>([])
    const [myRuns, setMyRuns] = useState<PetPveRun[]>([])
    const [detail, setDetail] = useState<PetPveRun | null>(null)
    const [busy, setBusy] = useState<string | null>(null)
    const [reloadSeq, setReloadSeq] = useState(0)

    const loadLobby = useCallback(async () => {
        const [bossRes, openRes] = await Promise.all([
            listPveBosses(),
            listOpenPveRuns({ page: 1, size: PAGE_SIZE }),
        ])
        if (bossRes.data.success) {
            setBosses(bossRes.data.data)
        }
        if (openRes.data.success) {
            setOpenRuns(openRes.data.data.records)
        }
    }, [])

    const loadMine = useCallback(async () => {
        const { data: res } = await listMyPveRuns({ page: 1, size: PAGE_SIZE })
        if (res.success) {
            setMyRuns(res.data.records)
        }
    }, [])

    useEffect(() => {
        if (!enabled) {
            return
        }
        if (tab === 'lobby') {
            void loadLobby()
        } else {
            void loadMine()
        }
    }, [enabled, tab, reloadSeq, loadLobby, loadMine])

    const openDetail = useCallback(async (runId: number | string) => {
        const { data: res } = await getPveRun(runId)
        if (res.success) {
            setDetail(res.data)
        }
    }, [])

    // OPEN/FIGHTING 期间轮询详情；终态/卸载清理（silentError：瞬时失败等下一拍，不弹全局错误）
    useEffect(() => {
        if (!detail || (detail.status !== 'OPEN' && detail.status !== 'FIGHTING')) {
            return
        }
        const timer = window.setInterval(() => {
            getPveRun(detail.runId, { silentError: true })
                .then(({ data: res }) => {
                    if (res.success) {
                        setDetail(res.data)
                    }
                })
                .catch(() => undefined)
        }, POLL_INTERVAL_MS)
        return () => window.clearInterval(timer)
    }, [detail])

    const start = useCallback(async (bossCode: string) => {
        setBusy(`start:${bossCode}`)
        try {
            const { data: res } = await startPveRun(bossCode)
            if (res.success) {
                message.success('副本已发起，等待队友加入')
                setDetail(res.data)
                setReloadSeq(seq => seq + 1)
            } else {
                message.warning(res.error?.message ?? '发起失败（可能已有进行中副本）')
            }
        } finally {
            setBusy(null)
        }
    }, [message])

    const join = useCallback(async (runId: number | string) => {
        setBusy(`join:${String(runId)}`)
        try {
            const { data: res } = await joinPveRun(runId)
            if (res.success) {
                message.success('已加入战斗！')
                setDetail(res.data)
                setReloadSeq(seq => seq + 1)
            } else {
                message.warning(res.error?.message ?? '加入失败（可能刚被别人加入）')
            }
        } finally {
            setBusy(null)
        }
    }, [message])

    const attack = useCallback(async (run: PetPveRun) => {
        setBusy('attack')
        try {
            // requestKey 由 api 层为本次点击生成：同键重放服务端返回原回合，不产生重复行动
            const { data: res } = await attackPveRun(run.runId)
            if (res.success) {
                setDetail(res.data)
                if (res.data.status === 'WON') {
                    message.success('🎉 Boss 讨伐成功！奖励已发放')
                } else if (res.data.status === 'FAILED') {
                    message.info('副本失败，下次再战')
                }
                setReloadSeq(seq => seq + 1)
            } else {
                message.warning(res.error?.message ?? '攻击失败（可能还没轮到你）')
            }
        } finally {
            setBusy(null)
        }
    }, [message])

    const cancel = useCallback(async (run: PetPveRun) => {
        setBusy('cancel')
        try {
            const { data: res } = await cancelPveRun(run.runId)
            if (res.success) {
                message.success('副本已取消')
                setDetail(res.data)
                setReloadSeq(seq => seq + 1)
            } else {
                message.warning(res.error?.message ?? '取消未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message])

    if (!enabled) {
        return <p className={styles.closed}>协作副本暂未开放<br />开放后可在此发起双人协战 Boss 副本</p>
    }

    if (detail) {
        return (
            <PveDetail
                run={detail}
                busy={busy}
                isInitiator={currentUserId !== null && String(detail.initiatorUserId) === String(currentUserId)}
                isParticipant={currentUserId !== null && (
                    String(detail.initiatorUserId) === String(currentUserId)
                    || (detail.partnerUserId !== null && String(detail.partnerUserId) === String(currentUserId))
                )}
                onBack={() => setDetail(null)}
                onJoin={() => void join(detail.runId)}
                onAttack={() => void attack(detail)}
                onCancel={() => void cancel(detail)}
            />
        )
    }

    const loading = tab === 'lobby' ? bosses === null : false

    return (
        <div>
            <div className={styles.tabs}>
                <button type="button"
                    className={`${styles.tab} ${tab === 'lobby' ? styles.tabActive : ''}`}
                    onClick={() => setTab('lobby')}>招募大厅</button>
                <button type="button"
                    className={`${styles.tab} ${tab === 'mine' ? styles.tabActive : ''}`}
                    onClick={() => setTab('mine')}>我的副本</button>
                <span style={{ flex: 1 }} />
                <CreamButton variant="ghost" onClick={() => setReloadSeq(seq => seq + 1)}>刷新</CreamButton>
            </div>

            {loading ? <Spin /> : tab === 'lobby' ? (
                <>
                    <p className={styles.blockTitle}>发起副本</p>
                    {(bosses ?? []).map(boss => (
                        <div key={boss.bossCode} className={styles.bossCard}>
                            <span className={styles.rowIcon}>👹</span>
                            <div className={styles.bossInfo}>
                                <p className={styles.bossName}>{boss.name}</p>
                                <p className={styles.bossMeta}>
                                    HP {boss.maxHp} · 攻击 {boss.attack} · 防御 {boss.defense}
                                </p>
                            </div>
                            <CreamButton variant="ghost" loading={busy === `start:${boss.bossCode}`}
                                onClick={() => void start(boss.bossCode)}>发起</CreamButton>
                        </div>
                    ))}
                    <p className={styles.blockTitle}>等待队友的副本</p>
                    {openRuns.length === 0 ? (
                        <p className={styles.empty}>暂无等待中的副本，发起一个吧</p>
                    ) : openRuns.map(run => (
                        <RunCard key={String(run.runId)} run={run}
                            busyJoin={busy === `join:${String(run.runId)}`}
                            onOpen={() => void openDetail(run.runId)}
                            onJoin={() => void join(run.runId)} />
                    ))}
                </>
            ) : (
                <>
                    {myRuns.length === 0 ? (
                        <p className={styles.empty}>还没有参与过副本</p>
                    ) : myRuns.map(run => (
                        <RunCard key={String(run.runId)} run={run} busyJoin={false}
                            onOpen={() => void openDetail(run.runId)} />
                    ))}
                </>
            )}
        </div>
    )
}
