import { useCallback, useEffect, useState } from 'react'
import { App, Spin } from 'antd'
import {
    claimPetBottle,
    getPetBottleStatus,
    startPetBottle,
    type PetBottleStatus,
} from '@/api/pet'
import { CreamButton } from '@/components/pet-cream/Cream'
import { fmtDuration } from '../constants'
import styles from '../../PetCream.module.css'

// ---------------- 面板：漂流瓶 ----------------

export function BottlePanel({ onChanged }: { onChanged: () => void }) {
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
