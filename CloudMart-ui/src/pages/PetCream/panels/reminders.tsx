import { useCallback, useEffect, useState } from 'react'
import { Spin } from 'antd'
import {
    getPetReminderUnreadCount,
    listPetReminders,
    markAllPetRemindersRead,
    type PetReminder,
} from '@/api/pet'
import { CreamButton, STAT_TONE } from '@/components/pet-cream/Cream'
import styles from '../../PetCream.module.css'

// ---------------- 面板：提醒 ----------------

export function RemindersPanel() {
    const [list, setList] = useState<PetReminder[] | null>(null)
    const [unread, setUnread] = useState<number | null>(null)

    const load = useCallback(async () => {
        const { data: res } = await listPetReminders()
        if (res.success) {
            setList(res.data)
        }
        const { data: unreadRes } = await getPetReminderUnreadCount()
        if (unreadRes.success) {
            setUnread(unreadRes.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const readAll = useCallback(async () => {
        const { data: res } = await markAllPetRemindersRead()
        if (res.success) {
            await load()
        }
    }, [load])

    if (!list) {
        return <Spin />
    }
    return (
        <div>
            <div className={styles.footerRow}>
                <p className={styles.panelDesc}>
                    未读 {unread === null ? '—' : unread} 条（入口角标与此一致）
                </p>
                <CreamButton variant="ghost" onClick={() => void readAll()}>全部标为已读</CreamButton>
            </div>
            <div style={{ marginTop: 8 }}>
                {list.length === 0 ? (
                    <p className={styles.panelDesc}>还没有提醒，陪陪它吧～</p>
                ) : list.map(item => (
                    <div key={String(item.notificationId)} className={styles.panelRow}>
                        <span style={{
                            width: 8, height: 8, borderRadius: '50%', flex: 'none',
                            background: item.isRead ? 'var(--cream-300)' : STAT_TONE.hp,
                        }} />
                        <div className={styles.panelMain}>
                            <p className={styles.panelTitle}>{item.title}</p>
                            <p className={styles.panelDesc}>{item.content}</p>
                        </div>
                        <span style={{ fontSize: 12, color: 'var(--mocha-light)' }}>
                            {item.priority ?? 'P1'}
                        </span>
                    </div>
                ))}
            </div>
        </div>
    )
}
