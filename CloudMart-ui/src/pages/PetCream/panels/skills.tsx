import { useCallback, useEffect, useState } from 'react'
import { App, Spin } from 'antd'
import {
    evolvePet,
    getPetEvolution,
    learnPetSkill,
    listPetSkills,
    type PetEvolutionStatus,
    type PetSkillItem,
} from '@/api/pet'
import { CreamButton, CreamChip, STAT_TONE } from '@/components/pet-cream/Cream'
import styles from '../../PetCream.module.css'

// ---------------- 面板：技能 · 进化 ----------------

export function SkillsPanel({ petId, petLevel, onChanged }: { petId: number | string; petLevel: number; onChanged: () => void }) {
    const { message } = App.useApp()
    const [skills, setSkills] = useState<PetSkillItem[] | null>(null)
    const [evo, setEvo] = useState<PetEvolutionStatus | null>(null)
    const [busy, setBusy] = useState<string | null>(null)

    const load = useCallback(async () => {
        const [skillRes, evoRes] = await Promise.all([listPetSkills(), getPetEvolution()])
        if (skillRes.data.success) {
            setSkills(skillRes.data.data)
        }
        if (evoRes.data.success) {
            setEvo(evoRes.data.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const learn = useCallback(async (item: PetSkillItem) => {
        setBusy(item.code)
        try {
            const { data: res } = await learnPetSkill(item.code)
            if (res.success) {
                message.success(`${item.name} 学习成功`)
                await load()
                onChanged()
            } else {
                message.warning(res.error?.message ?? '学习未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged])

    const evolve = useCallback(async () => {
        if (!evo || !petId) {
            return
        }
        setBusy('evolve')
        try {
            const { data: res } = await evolvePet(petId, evo.currentStage)
            if (res.success) {
                message.success('进化成功！')
                await load()
                onChanged()
            } else {
                message.warning(res.error?.message ?? '进化未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [evo, petId, load, message, onChanged])

    if (!skills || !evo) {
        return <Spin />
    }
    return (
        <div>
            <p className={styles.panelTitle} style={{ margin: '0 0 6px', fontFamily: 'var(--font-serif)' }}>
                进化 · 阶段 {evo.currentStage}/{evo.maxStage}
            </p>
            {evo.nextCode ? (
                <>
                    <p className={styles.panelDesc}>
                        下一形态：{evo.nextName} · 需 Lv.{evo.requiredLevel} · 宠物币 {evo.costCoins}
                    </p>
                    <div className={styles.renameRow}>
                        <CreamButton block loading={busy === 'evolve'} disabled={petLevel < (evo.requiredLevel ?? 0)}
                            onClick={() => void evolve()}>
                            {petLevel < (evo.requiredLevel ?? 0) ? `Lv.${evo.requiredLevel} 解锁进化` : '立即进化'}
                        </CreamButton>
                    </div>
                </>
            ) : (
                <p className={styles.panelDesc}>已达最高进化阶段。</p>
            )}
            <p className={styles.panelTitle} style={{ margin: '14px 0 4px', fontFamily: 'var(--font-serif)' }}>
                技能
            </p>
            {skills.map(item => (
                <div key={item.code} className={styles.panelRow}>
                    <span style={{ fontSize: 20 }}>{item.icon}</span>
                    <div className={styles.panelMain}>
                        <p className={styles.panelTitle}>
                            {item.name}
                            <CreamChip color={item.skillType === 'ACTIVE' ? STAT_TONE.hp : STAT_TONE.energy}>
                                {item.skillType === 'ACTIVE' ? '主动' : '被动'}
                            </CreamChip>
                        </p>
                        <p className={styles.panelDesc}>{item.effectText}</p>
                    </div>
                    {item.learned ? (
                        <CreamChip color={STAT_TONE.energy}>已学习</CreamChip>
                    ) : (
                        <CreamButton variant="ghost" loading={busy === item.code}
                            disabled={petLevel < item.requiredLevel}
                            onClick={() => void learn(item)}>
                            {petLevel < item.requiredLevel ? `Lv.${item.requiredLevel}` : `⭐ ${item.priceCoins}`}
                        </CreamButton>
                    )}
                </div>
            ))}
        </div>
    )
}
