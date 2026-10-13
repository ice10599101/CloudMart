import { useCallback, useEffect, useState } from 'react'
import { App, Spin } from 'antd'
import {
    claimPetActivitiesBatch,
    claimPetStudy,
    claimPetWork,
    getPetActivityCenter,
    listPetActivities,
    listPetJobs,
    listPetStudies,
    startPetStudy,
    startPetWork,
    type PetActivityCenterSummary,
    type PetClaimBatchItem,
    type PetJobItem,
    type PetStudyItem,
} from '@/api/pet'
import { CreamButton } from '@/components/pet-cream/Cream'
import { fmtDuration, STATUS_LABEL } from '../constants'
import styles from '../../PetCream.module.css'

// ---------------- 面板：打工 · 读书 ----------------

const ACTIVITY_LABEL: Record<string, string> = {
    WORK: '打工', STUDY: '读书', FISHING: '捞瓶', REST: '休息', CAREER_WORK: '职业工作',
}

/** PET-23 §6.3 活动中心：聚合摘要（忙碌/待领数/任务·事件·合作）+ 待领列表批领（逐项结果可重试） */
export function ActivityCenterPanel({ onChanged }: { onChanged: () => void }) {
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

export function ActivitiesPanel({ petStatus, onChanged }: { petStatus: string; onChanged: () => void }) {
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
