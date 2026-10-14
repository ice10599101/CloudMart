import { useCallback, useEffect, useState } from 'react'
import { App, Spin } from 'antd'
import {
    claimPetEventOccurrence,
    listPetEventsByStatus,
    type PetEventItem,
} from '@/api/pet'
import { CreamButton, CreamChip, STAT_TONE } from '@/components/pet-cream/Cream'
import styles from '../../PetCream.module.css'

// ---------------- 面板：事件 ----------------

export function EventsPanel({ onChanged }: { onChanged: () => void }) {
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
        if (!item.occurrenceId) {
            message.warning('该活动暂不支持在此领取，请等待新一期开放')
            return
        }
        setBusy(item.occurrenceId)
        try {
            // C15：领奖唯一入口——按期次 occurrenceId（旧 eventCode 旁路已删除）
            const { data: res } = await claimPetEventOccurrence(item.occurrenceId)
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
                            {item.description} · ⭐{item.rewardCoins} exp+{item.rewardExp}
                        </p>
                    </div>
                    {item.claimable && item.occurrenceId ? (
                        <CreamButton variant="ghost" loading={busy === item.occurrenceId}
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
