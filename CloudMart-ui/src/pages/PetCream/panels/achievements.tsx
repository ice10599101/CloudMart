import { useEffect, useState } from 'react'
import { Spin } from 'antd'
import {
    listPetAchievements,
    type PetAchievement,
} from '@/api/pet'
import { CreamChip, STAT_TONE } from '@/components/pet-cream/Cream'
import styles from '../../PetCream.module.css'

// ---------------- 面板：成就 ----------------

export function AchievementsPanel() {
    const [list, setList] = useState<PetAchievement[] | null>(null)

    useEffect(() => {
        void (async () => {
            const { data: res } = await listPetAchievements()
            if (res.success) {
                setList(res.data)
            }
        })()
    }, [])

    if (!list) {
        return <Spin />
    }
    return (
        <div>
            {list.map(item => (
                <div key={String(item.achievementId)} className={styles.panelRow}>
                    <span style={{ fontSize: 20, filter: item.achieved ? 'none' : 'grayscale(1)' }}>
                        {item.icon}
                    </span>
                    <div className={styles.panelMain}>
                        <p className={styles.panelTitle}>{item.name}</p>
                        <p className={styles.panelDesc}>{item.description} · exp+{item.expReward}</p>
                    </div>
                    <CreamChip color={item.achieved ? STAT_TONE.energy : STAT_TONE.cleanliness}>
                        {item.achieved ? '已达成' : '未达成'}
                    </CreamChip>
                </div>
            ))}
        </div>
    )
}
