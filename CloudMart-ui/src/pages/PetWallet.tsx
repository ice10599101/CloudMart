/**
 * 宠物钱包独立页 · 法式奶油风（/pet/wallet）。
 *
 * 与 /pet 首页的"🪙钱包"面板互补：这里是完整收支流水视图（首页面板只看余额）。
 * 沿用 pet-cream 设计系统（奶油卡、挤出按钮、仪表色）。
 */
import { useCallback, useEffect, useState } from 'react'
import { App, ConfigProvider, Spin, theme as antdTheme } from 'antd'
import {
    getPetWallet,
    listPetWalletTransactions,
    type PetWalletTransactionVO,
    type PetWalletVO,
} from '@/api/pet'
import {
    CreamButton,
    CreamCard,
    CreamChip,
    CreamOrnament,
    STAT_TONE,
} from '@/components/pet-cream/Cream'
import styles from './PetWallet.module.css'

const DIRECTION_LABEL: Record<string, string> = {
    EARN: '收入',
    SPEND: '支出',
    REFUND: '退款',
    ADJUSTMENT: '调整',
}

const DIRECTION_COLOR: Record<string, string> = {
    EARN: '#4E9A34',
    SPEND: '#D98A8A',
    REFUND: '#5A7CC4',
    ADJUSTMENT: '#9C8D7E',
}

export default function PetWalletPage() {
    const [wallet, setWallet] = useState<PetWalletVO | null>(null)
    const [flows, setFlows] = useState<PetWalletTransactionVO[] | null>(null)
    const [loading, setLoading] = useState(true)

    const load = useCallback(async () => {
        setLoading(true)
        try {
            const [walletRes, flowRes] = await Promise.all([
                getPetWallet(),
                listPetWalletTransactions({ size: 30 }),
            ])
            if (walletRes.data.success) {
                setWallet(walletRes.data.data)
            }
            if (flowRes.data.success) {
                setFlows(flowRes.data.data)
            }
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    if (loading) {
        return (
            <div className={`${styles.page} ${styles.centered}`}>
                <Spin size="large" />
            </div>
        )
    }

    return (
        <ConfigProvider
            theme={{
                // 站点全局是暗色 antd token，宠物页必须强制回浅色
                algorithm: antdTheme.defaultAlgorithm,
                token: { colorPrimary: '#C89B5A', borderRadius: 14 },
            }}
        >
            <div className={styles.page}>
                <div className={styles.shell}>
                    <header className={styles.masthead}>
                        <h1 className={styles.mastheadTitle}>Porte-monnaie</h1>
                        <p className={styles.mastheadSub}>宠物钱包 · 星光收支</p>
                        <div className={styles.mastheadRule} />
                    </header>

                    <CreamCard variant="arch" label="Solde">
                        {wallet ? (
                            <>
                                <p className={styles.balance}>
                                    {wallet.balance}
                                    <span className={styles.balanceUnit}>宠物币</span>
                                </p>
                                <div className={styles.metaRow}>
                                    <CreamChip color={STAT_TONE.energy}>{wallet.currency}</CreamChip>
                                    <CreamChip color={wallet.status === 'ACTIVE' ? STAT_TONE.energy : STAT_TONE.hp}>
                                        {wallet.status === 'ACTIVE' ? '账户正常' : '账户冻结'}
                                    </CreamChip>
                                    <CreamChip color={STAT_TONE.cleanliness}>
                                        账户 {wallet.accountId.slice(0, 10)}…
                                    </CreamChip>
                                </div>
                            </>
                        ) : (
                            <p className={styles.empty}>钱包服务暂不可用</p>
                        )}
                        <div className={styles.renameRow}>
                            <CreamButton variant="ghost" onClick={() => void load()}>刷新</CreamButton>
                        </div>
                    </CreamCard>

                    <CreamCard variant="menu" label="Historique" title="收支流水">
                        {!flows || flows.length === 0 ? (
                            <p className={styles.empty}>还没有收支记录</p>
                        ) : flows.map(item => (
                            <div key={item.transactionId} className={styles.panelRow}>
                                <div className={styles.panelMain}>
                                    <p className={styles.panelTitle}>{item.bizType}</p>
                                    <p className={styles.panelDesc}>{item.occurredAt}</p>
                                </div>
                                <CreamChip color={DIRECTION_COLOR[item.direction] ?? '#9C8D7E'}>
                                    {DIRECTION_LABEL[item.direction] ?? item.direction}
                                </CreamChip>
                                <span className={styles.amount}>{item.amount}</span>
                            </div>
                        ))}
                    </CreamCard>

                    <CreamOrnament>❦</CreamOrnament>
                </div>
            </div>
        </ConfigProvider>
    )
}
