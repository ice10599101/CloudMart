/**
 * R19 赛季通行证面板（对照 Taro 端 pages/seasonPass 移植，契约对齐 mall-pet PetSeasonPassController/VO）。
 *
 * 档位阶梯、达标与领取状态全部服务端权威：CLAIM_PENDING 不展示为已领取（发放中可稍后刷新补发）。
 * capabilities.seasonPass===false 时仅展示不可领取说明，不加载数据、不渲染领取按钮。
 */
import { useCallback, useEffect, useState } from 'react'
import { App, Spin } from 'antd'
import {
    claimSeasonPassTier,
    getSeasonPass,
    getSeasonPassSeasons,
    type PetSeason,
    type PetSeasonPass,
    type PetSeasonPassTier,
} from '@/api/pet'
import { CreamButton, CreamChip, STAT_TONE } from './Cream'
import styles from './r19Board.module.css'

function fmtDate(iso: string | null | undefined): string {
    if (!iso) {
        return '—'
    }
    return iso.slice(0, 10)
}

function TierRow({ tier, passExp, claiming, onClaim }: {
    tier: PetSeasonPassTier
    passExp: number
    claiming: boolean
    onClaim: () => void
}) {
    const pending = tier.claimStatus === 'CLAIM_PENDING'
    return (
        <div className={styles.tierCard}>
            <div className={styles.tierInfo}>
                <span className={styles.tierNo}>第 {tier.tier} 档</span>
                <span className={styles.tierReq}>
                    {`${tier.requiredExp} 经验`}
                    {tier.reached
                        ? ' · 已达标'
                        : ` · 还差 ${Math.max(0, tier.requiredExp - passExp)}`}
                </span>
                <span className={styles.tierReward}>{tier.rewardDesc}</span>
            </div>
            {tier.claimed ? (
                <CreamChip color={STAT_TONE.energy}>已领取</CreamChip>
            ) : pending ? (
                <CreamChip color={STAT_TONE.hunger}>发放中</CreamChip>
            ) : (
                <CreamButton variant="ghost" disabled={!tier.reached} loading={claiming} onClick={onClaim}>
                    领取
                </CreamButton>
            )}
        </div>
    )
}

export default function SeasonPassBoard({ enabled }: { enabled: boolean }) {
    const { message } = App.useApp()
    const [pass, setPass] = useState<PetSeasonPass | null>(null)
    const [claiming, setClaiming] = useState<number | null>(null)
    // T19：历史赛季（API 已就绪——查看快照进度；宽限期内补领由服务端判定）
    const [mySeasons, setMySeasons] = useState<PetSeason[] | null>(null)
    const [viewingSeasonId, setViewingSeasonId] = useState<number | string | null>(null)

    const load = useCallback(async () => {
        const { data: res } = await getSeasonPass()
        if (res.success) {
            setPass(res.data)
        }
    }, [])

    useEffect(() => {
        if (enabled) {
            void load()
        }
    }, [enabled, load])

    const claim = useCallback(async (tier: number) => {
        if (claiming !== null) {
            return
        }
        setClaiming(tier)
        try {
            // requestKey 由 api 层为本次点击生成；同档重复提交服务端返回原状态
            const { data: res } = await claimSeasonPassTier(tier)
            if (res.success) {
                setPass(res.data)
                message.success(`第 ${tier} 档奖励已领取`)
            } else {
                message.warning(res.error?.message ?? '领取失败，请稍后再试')
            }
        } finally {
            setClaiming(null)
        }
    }, [claiming, message])

    /** T19：查看历史赛季（快照进度；已领不可重领由服务端判定） */
    const viewSeason = async (seasonId: number | string) => {
        const { data: res } = await getSeasonPass(seasonId)
        if (res.success) {
            setPass(res.data)
            setViewingSeasonId(seasonId)
        }
    }

    const backToCurrent = async () => {
        setViewingSeasonId(null)
        await load()
    }

    const toggleSeasons = async () => {
        if (mySeasons != null) {
            setMySeasons(null)
            return
        }
        try {
            const { data: res } = await getSeasonPassSeasons()
            setMySeasons(res.success ? (res.data ?? []) : [])
        } catch {
            setMySeasons([])
        }
    }

    if (!enabled) {
        return (
            <p className={styles.closed}>
                赛季通行证暂未开放<br />档位奖励当前不可领取，开赛后可在此查看进度与领奖
            </p>
        )
    }

    if (!pass) {
        return <Spin />
    }

    if (pass.seasonId === null) {
        return <p className={styles.empty}>当前没有进行中的赛季，开赛后这里会亮起来</p>
    }

    return (
        <div>
            <div className={styles.hero}>
                <p className={styles.heroTitle}>🎫 {pass.seasonName ?? '当前赛季'}</p>
                <p className={styles.heroExp}>通行证经验 {pass.passExp}</p>
                <p className={styles.heroMeta}>
                    赛季结束 {fmtDate(pass.seasonEndsAt)}
                    {pass.claimUntil ? ` · 领奖截止 ${fmtDate(pass.claimUntil)}` : ''}
                </p>
                {/* T19：历史赛季选择（查看快照进度） */}
                <p className={styles.heroMeta} style={{ cursor: 'pointer', textDecoration: 'underline' }}
                    onClick={() => void toggleSeasons()}>
                    {mySeasons != null ? '收起历史赛季' : '查看历史赛季'}
                    {viewingSeasonId != null ? '（正在查看历史）' : ''}
                </p>
                {mySeasons != null ? (
                    mySeasons.length === 0
                        ? <p className={styles.heroMeta}>暂无参与过的历史赛季</p>
                        : mySeasons.map(season => (
                            <p key={String(season.id)} className={styles.heroMeta} style={{ cursor: 'pointer' }}
                                onClick={() => void viewSeason(season.id)}>
                                {season.name}（{fmtDate(season.endsAt)} 结束）
                                {String(season.id) === String(viewingSeasonId) ? ' · 当前查看' : ''}
                            </p>
                        ))
                ) : null}
                {viewingSeasonId != null ? (
                    <p className={styles.heroMeta} style={{ cursor: 'pointer', color: '#3d5afe' }}
                        onClick={() => void backToCurrent()}>
                        ← 返回当前赛季
                    </p>
                ) : null}
            </div>
            {pass.tiers.map(tier => (
                <TierRow key={tier.tier} tier={tier} passExp={pass.passExp}
                    claiming={claiming === tier.tier}
                    onClaim={() => void claim(tier.tier)} />
            ))}
            <p className={styles.note}>通行证经验通过每日任务与全清宝箱累积；档位奖励逐档领取。</p>
        </div>
    )
}
