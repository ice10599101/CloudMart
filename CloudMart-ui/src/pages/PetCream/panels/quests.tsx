import { useCallback, useEffect, useState } from 'react'
import { App, Spin } from 'antd'
import {
    claimAllPetQuestsInSet,
    claimPetQuestChestInSet,
    claimPetQuestInSet,
    getPetDailyQuests,
    type PetDailyQuestPanel,
} from '@/api/pet'
import { checkIn, getCheckInStatus, type CheckInResult } from '@/api/growth'
import { CreamButton, CreamChip, STAT_TONE } from '@/components/pet-cream/Cream'
import styles from '../../PetCream.module.css'

// ---------------- 面板：每日任务 ----------------

export function QuestsPanel({ onChanged }: { onChanged: () => void }) {
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

    const claim = useCallback(async (questId: string | null) => {
        if (!panel?.setId) {
            message.warning('任务集数据未就绪，请刷新后重试')
            return
        }
        setBusy(questId ?? 'chest')
        try {
            // C14：领奖唯一路径——按任务集实例（setId/questId 均为实体 ID）
            const { data: res } = questId
                ? await claimPetQuestInSet(panel.setId, questId)
                : await claimPetQuestChestInSet(panel.setId)
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
    }, [load, message, onChanged, panel?.setId])

    /** 一键领取：把所有可领的奖励一次收掉（宝箱由服务端规则判定，不可领时不给按钮） */
    const claimAll = useCallback(async () => {
        if (!panel?.setId) {
            message.warning('任务集数据未就绪，请刷新后重试')
            return
        }
        setBusy('all')
        try {
            const { data: res } = await claimAllPetQuestsInSet(panel.setId)
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
                        {quest.claimable && quest.id ? (
                            <CreamButton variant="ghost" loading={busy === quest.id}
                                onClick={() => void claim(quest.id)}>领取</CreamButton>
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
