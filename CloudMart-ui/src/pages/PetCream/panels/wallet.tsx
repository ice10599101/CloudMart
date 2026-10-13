import { useEffect, useState } from 'react'
import { Spin } from 'antd'
import {
    getPetWallet,
    type PetWalletVO,
} from '@/api/pet'
import styles from '../../PetCream.module.css'

// ---------------- 面板：钱包 ----------------

export function WalletPanel() {
    const [wallet, setWallet] = useState<PetWalletVO | null>(null)

    useEffect(() => {
        void (async () => {
            const { data: res } = await getPetWallet()
            if (res.success) {
                setWallet(res.data)
            }
        })()
    }, [])

    if (!wallet) {
        return <Spin />
    }
    return (
        <div>
            <p style={{ fontSize: 34, margin: '6px 0 2px', color: 'var(--espresso)', fontVariantNumeric: 'tabular-nums' }}>
                {wallet.balance}
                <span style={{ fontSize: 14, color: 'var(--mocha)', marginLeft: 8 }}>宠物币</span>
            </p>
            <p className={styles.panelDesc}>
                账户 {wallet.accountId.slice(0, 12)}… · {wallet.status === 'ACTIVE' ? '正常' : '冻结'}
            </p>
            <p className={styles.panelDesc}>收支流水在商城购买/任务领取时自动记账。</p>
        </div>
    )
}
