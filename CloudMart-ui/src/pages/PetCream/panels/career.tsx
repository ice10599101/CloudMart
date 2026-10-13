import { useCallback, useEffect, useState } from 'react'
import { App, Spin } from 'antd'
import {
    applyPetCareer,
    claimPetCareerWork,
    getPetCareer,
    promotePetCareer,
    startPetCareerWork,
    type PetCareerPanel,
} from '@/api/pet'
import { CreamButton, CreamChip, STAT_TONE } from '@/components/pet-cream/Cream'
import { fmtDuration } from '../constants'
import styles from '../../PetCream.module.css'

// ---------------- 面板：职业 ----------------

export function CareerPanel({ onChanged }: { onChanged: () => void }) {
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
