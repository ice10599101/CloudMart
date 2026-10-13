import { useCallback, useEffect, useState } from 'react'
import { App, Spin } from 'antd'
import {
    acceptPetBattle,
    challengePetBattle,
    declinePetBattle,
    listPendingBattles,
    listPetBattleHistory,
    listPetOpponents,
    type PetBattleItem,
    type PetOpponent,
} from '@/api/pet'
import { CreamButton, CreamChip, SPECIES_EMOJI, STAT_TONE } from '@/components/pet-cream/Cream'
import type { PendingBattle } from '../types'
import styles from '../../PetCream.module.css'

// ---------------- 面板：对战 ----------------

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

export function BattlePanel({ myPetId, onChanged }: { myPetId: number | string; onChanged: () => void }) {
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
