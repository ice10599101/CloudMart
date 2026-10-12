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
