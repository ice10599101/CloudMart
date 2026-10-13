import { useCallback, useEffect, useState } from 'react'
import { Spin } from 'antd'
import {
    getPetRankings,
    getPetSeasonHistory,
    getPetSeasonRanking,
    type PetRankingType,
    type PetSeasonHistoryItem,
    type PetSeasonRanking,
    type PetSpecies,
} from '@/api/pet'
import { SPECIES_EMOJI } from '@/components/pet-cream/Cream'
import styles from '../../PetCream.module.css'

// ---------------- 面板：排行榜 ----------------

const RANK_TABS: Array<{ type: PetRankingType | 'SEASON'; label: string }> = [
    { type: 'LEVEL', label: '等级' },
    { type: 'BATTLE_WIN', label: '对战' },
    { type: 'BOTTLE', label: '捞瓶' },
    { type: 'SEASON', label: '赛季' },
]

export function RankingsPanel() {
    const [type, setType] = useState<PetRankingType | 'SEASON'>('LEVEL')
    const [result, setResult] = useState<{ top20: Array<{
        rank: number; petId: number | string; name: string; species: PetSpecies
        level: number; value: number; ownerNickname: string; isMe: boolean
    }>; myValue: number | null; myRank: number | null } | null>(null)
    const [season, setSeason] = useState<PetSeasonRanking | null>(null)
    const [seasonHistory, setSeasonHistory] = useState<PetSeasonHistoryItem[] | null>(null)

    const load = useCallback(async (t: PetRankingType | 'SEASON') => {
        if (t === 'SEASON') {
            const [{ data: seasonRes }, { data: historyRes }] = await Promise.all([
                getPetSeasonRanking(),
                getPetSeasonHistory(),
            ])
            if (seasonRes.success) {
                setSeason(seasonRes.data)
            }
            if (historyRes.success) {
                setSeasonHistory(historyRes.data)
            }
            return
        }
        const { data: res } = await getPetRankings(t)
        if (res.success) {
            setResult(res.data)
        }
    }, [])

    useEffect(() => {
        void load(type)
    }, [load, type])

    if (type === 'SEASON') {
        return (
            <div>
                <div className={styles.petRow}>
                    {RANK_TABS.map(tab => (
                        <button
                            key={tab.type}
                            type="button"
                            className={`${styles.petChip} ${type === tab.type ? styles.petChipActive : ''}`}
                            onClick={() => setType(tab.type)}
                        >
                            {tab.label}
                        </button>
                    ))}
                </div>
                {!season ? (
                    <div style={{ paddingTop: 12 }}><Spin /></div>
                ) : season.season === null ? (
                    <p className={styles.panelDesc}>当前没有进行中的赛季，开赛后这里会亮起来</p>
                ) : (
                    <div style={{ paddingTop: 10 }}>
                        <p className={styles.panelDesc}>
                            {season.season.name} · {season.season.startsAt?.slice(5, 10)} ~ {season.season.endsAt?.slice(5, 10)} ·
                            我的等级 {season.myLevel ?? '—'} · 名次 {season.myRank ?? '未上榜'}
                        </p>
                        {season.top50.map(item => (
                            <div key={String(item.petId)} className={`${styles.panelRow} ${item.isMe ? styles.rankMe : ''}`}>
                                <span className={`${styles.rankBadge} ${item.rank <= 3 ? styles.rankBadgeTop : ''}`}>
                                    {item.rank}
                                </span>
                                <div className={styles.panelMain}>
                                    <p className={styles.panelTitle}>
                                        {SPECIES_EMOJI[item.species] ?? '🐾'} {item.name}
                                    </p>
                                    <p className={styles.panelDesc}>{item.ownerNickname} · Lv.{item.level}</p>
                                </div>
                                <span style={{ fontSize: 13, color: 'var(--espresso)' }}>{item.value}</span>
                            </div>
                        ))}
                    </div>
                )}
                {seasonHistory && seasonHistory.length > 0 ? (
                    <div style={{ paddingTop: 12 }}>
                        <p className={styles.panelTitle}>历届我的名次</p>
                        {seasonHistory.map(item => (
                            <div key={String(item.seasonId)} className={styles.panelRow}>
                                <span className={styles.rankBadge}>{item.rankNo}</span>
                                <div className={styles.panelMain}>
                                    <p className={styles.panelTitle}>{item.seasonName}</p>
                                    <p className={styles.panelDesc}>
                                        结算于 {item.endedAt?.slice(0, 10) ?? '—'} · 等级 {item.level}
                                    </p>
                                </div>
                            </div>
                        ))}
                    </div>
                ) : null}
            </div>
        )
    }
    return (
        <div>
            <div className={styles.petRow}>
                {RANK_TABS.map(tab => (
                    <button
                        key={tab.type}
                        type="button"
                        className={`${styles.petChip} ${type === tab.type ? styles.petChipActive : ''}`}
                        onClick={() => setType(tab.type)}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>
            {!result ? (
                <div style={{ paddingTop: 12 }}><Spin /></div>
            ) : (
                <div style={{ paddingTop: 10 }}>
                    <p className={styles.panelDesc}>
                        我的{RANK_TABS.find(t => t.type === type)?.label}值：
                        {result.myValue ?? '—'} · 名次：{result.myRank ?? '未上榜'}
                    </p>
                    {result.top20.map(item => (
                        <div key={String(item.petId)} className={`${styles.panelRow} ${item.isMe ? styles.rankMe : ''}`}>
                            <span className={`${styles.rankBadge} ${item.rank <= 3 ? styles.rankBadgeTop : ''}`}>
                                {item.rank}
                            </span>
                            <div className={styles.panelMain}>
                                <p className={styles.panelTitle}>
                                    {SPECIES_EMOJI[item.species] ?? '🐾'} {item.name}
                                </p>
                                <p className={styles.panelDesc}>{item.ownerNickname} · Lv.{item.level}</p>
                            </div>
                            <span style={{ fontSize: 13, color: 'var(--espresso)' }}>{item.value}</span>
                        </div>
                    ))}
                </div>
            )}
        </div>
    )
}
