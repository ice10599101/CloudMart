/**
 * R19 实物商品双倍喂食权益面板（对照 Taro 端 pages/pet 兑换码区块移植，
 * 契约对齐 mall-pet PetLinkedProductController/Service）。
 *
 * 兑换码核销为可恢复操作：coupon 不可达时后端留 PENDING/VERIFYING，
 * 前端凭 operationId 每 3 秒轮询 /linked-product/operations/{id}，终态/卸载清理定时器。
 * capabilities.linkedProduct===false 时隐藏兑换入口（整块仅展示未开放说明）。
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { App, Input, Spin } from 'antd'
import {
    getLinkedProductEntitlement,
    getLinkedProductOperation,
    redeemLinkedProduct,
    type PetLinkedProductEntitlement,
    type PetLinkedProductOperation,
} from '@/api/pet'
import { CreamButton, CreamChip, STAT_TONE } from './Cream'
import styles from './r19Board.module.css'

const POLL_INTERVAL_MS = 3000

const OPERATION_STATUS_LABEL: Record<string, string> = {
    VERIFYING: '核验中…',
    PENDING: '处理中…（兑换码核验排队，结果出来后自动刷新）',
    DELIVERED: '核销成功，双倍喂食权益已到账',
    REJECTED: '核销未通过',
}

const REJECT_REASON_LABEL: Record<string, string> = {
    PET_CODE_NOT_FOUND: '兑换码不存在',
    PET_CODE_DISABLED: '兑换码已停用',
    PET_CODE_NOT_EXCHANGED: '兑换码尚未在商城完成兑换，请先兑换后再来核销',
    PET_CODE_NOT_OWNED: '兑换码不属于当前账号',
}

const ENTITLEMENT_STATUS_LABEL: Record<string, string> = {
    AVAILABLE: '可用',
    CONSUMED: '已使用',
    EXPIRED: '已过期',
}

function fmtDateTime(iso: string | null | undefined): string {
    if (!iso) {
        return '—'
    }
    return iso.slice(5, 16).replace('T', ' ')
}

export default function LinkedProductBoard({ enabled }: { enabled: boolean }) {
    const { message } = App.useApp()
    const [entitlement, setEntitlement] = useState<PetLinkedProductEntitlement | null>(null)
    const [redeemCode, setRedeemCode] = useState('')
    const [redeeming, setRedeeming] = useState(false)
    const [operation, setOperation] = useState<PetLinkedProductOperation | null>(null)
    /** 终态提示去重（同一 operationId+status 只提示一次） */
    const notifiedRef = useRef<string | null>(null)

    const loadEntitlement = useCallback(async () => {
        const { data: res } = await getLinkedProductEntitlement()
        if (res.success) {
            setEntitlement(res.data)
        }
    }, [])

    useEffect(() => {
        if (enabled) {
            void loadEntitlement()
        }
    }, [enabled, loadEntitlement])

    // PENDING/VERIFYING：按 operationId 轮询终态（silentError 静默瞬时失败，下一拍再试）
    useEffect(() => {
        if (!operation || (operation.status !== 'PENDING' && operation.status !== 'VERIFYING')) {
            return
        }
        const timer = window.setInterval(() => {
            getLinkedProductOperation(operation.operationId, { silentError: true })
                .then(({ data: res }) => {
                    if (res.success) {
                        setOperation(res.data)
                    }
                })
                .catch(() => undefined)
        }, POLL_INTERVAL_MS)
        return () => window.clearInterval(timer)
    }, [operation])

    // 终态一次性提示 + 权益刷新（轮询与即时返回共用同一收口）
    useEffect(() => {
        if (!operation || (operation.status !== 'DELIVERED' && operation.status !== 'REJECTED')) {
            return
        }
        const signalKey = `${operation.operationId}:${operation.status}`
        if (notifiedRef.current === signalKey) {
            return
        }
        notifiedRef.current = signalKey
        if (operation.status === 'DELIVERED') {
            message.success('核销成功！下一次喂食效果翻倍')
            void loadEntitlement()
        } else {
            message.warning(REJECT_REASON_LABEL[operation.rejectReasonCode]
                ?? `兑换码无效或已核销（${operation.rejectReasonCode || '未知原因'}）`)
        }
    }, [operation, loadEntitlement, message])

    const redeem = useCallback(async () => {
        const code = redeemCode.trim()
        if (!code) {
            message.warning('请输入兑换码')
            return
        }
        if (redeeming) {
            return
        }
        setRedeeming(true)
        try {
            // requestKey 由 api 层为本次点击生成：断网/超时凭同键重放返回原操作，不重复发放
            const { data: res } = await redeemLinkedProduct(code)
            if (res.success) {
                setRedeemCode('')
                setOperation(res.data)
                if (res.data.status === 'DELIVERED') {
                    // 即时终态：占位去重键，避免终态 effect 二次提示
                    notifiedRef.current = `${res.data.operationId}:DELIVERED`
                    message.success('核销成功！下一次喂食效果翻倍')
                    void loadEntitlement()
                } else if (res.data.status === 'REJECTED') {
                    notifiedRef.current = `${res.data.operationId}:REJECTED`
                    message.warning(REJECT_REASON_LABEL[res.data.rejectReasonCode]
                        ?? `兑换码无效或已核销（${res.data.rejectReasonCode || '未知原因'}）`)
                } else {
                    message.info('兑换码核验中，结果出来后会自动刷新')
                }
            } else {
                message.warning(res.error?.message ?? '核销失败，请稍后再试')
            }
        } finally {
            setRedeeming(false)
        }
    }, [loadEntitlement, message, redeemCode, redeeming])

    if (!enabled) {
        return (
            <p className={styles.closed}>
                实物商品联动暂未开放<br />开放后可在此用实物零食随附兑换码兑换双倍喂食权益
            </p>
        )
    }

    if (!entitlement) {
        return <Spin />
    }

    const processing = operation?.status === 'PENDING' || operation?.status === 'VERIFYING'

    return (
        <div>
            <div className={styles.entitlementHero}>
                {entitlement.usableCount > 0 ? (
                    <>
                        <div>🎁 双倍喂食权益已就绪</div>
                        <div className={styles.entitlementCount}>× {entitlement.usableCount}</div>
                        <div>下一次喂食效果 x2</div>
                    </>
                ) : (
                    <div>🐱 暂无可用权益<br />购买实物零食并完成兑换后，在此输入兑换码核销</div>
                )}
            </div>

            <div className={styles.redeemRow}>
                <Input
                    className={styles.redeemInput}
                    value={redeemCode}
                    onChange={event => setRedeemCode(event.target.value)}
                    placeholder="输入实物零食随附兑换码"
                    maxLength={64}
                    onPressEnter={() => void redeem()}
                    allowClear
                />
                <CreamButton loading={redeeming} onClick={() => void redeem()}>核销</CreamButton>
            </div>

            {operation ? (
                <div className={styles.operationCard}>
                    {processing ? (
                        <span>
                            <Spin size="small" /> {OPERATION_STATUS_LABEL[operation.status] ?? operation.status}
                        </span>
                    ) : operation.status === 'DELIVERED' ? (
                        <span style={{ color: '#7E9270' }}>✓ {OPERATION_STATUS_LABEL.DELIVERED}</span>
                    ) : operation.status === 'REJECTED' ? (
                        <span style={{ color: '#c00' }}>
                            ✗ {OPERATION_STATUS_LABEL.REJECTED}：
                            {REJECT_REASON_LABEL[operation.rejectReasonCode]
                                ?? (operation.rejectReasonCode || '未知原因')}
                        </span>
                    ) : (
                        <span>{OPERATION_STATUS_LABEL[operation.status] ?? operation.status}</span>
                    )}
                </div>
            ) : null}

            <p className={styles.blockTitle}>权益明细</p>
            {entitlement.items.length === 0 ? (
                <p className={styles.empty}>还没有双倍喂食权益记录</p>
            ) : entitlement.items.map(item => (
                <div key={String(item.entitlementId)} className={styles.row}>
                    <span className={styles.rowIcon}>🎁</span>
                    <div className={styles.rowMain}>
                        <p className={styles.rowTitle}>
                            双倍喂食权益
                            <CreamChip color={item.status === 'AVAILABLE' ? STAT_TONE.energy : STAT_TONE.cleanliness}>
                                {ENTITLEMENT_STATUS_LABEL[item.status] ?? item.status}
                            </CreamChip>
                        </p>
                        <p className={styles.rowDesc}>
                            {item.status === 'AVAILABLE' && item.expiresAt
                                ? `有效期至 ${fmtDateTime(item.expiresAt)}`
                                : '下一次喂食时自动生效'}
                        </p>
                    </div>
                </div>
            ))}
            <p className={styles.note}>权益仅作用于下一次喂食，喂食完成后自动消耗一条；处理中结果无需重复提交。</p>
        </div>
    )
}
