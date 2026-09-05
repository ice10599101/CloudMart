import { useCallback } from 'react'
import { Button } from 'antd'
import { ReloadOutlined, WifiOutlined, CloudServerOutlined } from '@ant-design/icons'
import styles from './index.module.css'

/**
 * 统一状态组件（Sprint 1.6 验收：EmptyState/ErrorState/LoadingState）。
 * 替代散落在各页面的裸 Empty/文字加载，提供空态/错误态(含重试)/弱网提示。
 */

export function ErrorState({ message = '加载失败', onRetry }: { message?: string; onRetry?: () => void }) {
    return (
        <div className={styles.wrapper}>
            <CloudServerOutlined style={{ fontSize: 42, color: 'var(--color-text-tertiary)', marginBottom: 12 }} />
            <p style={{ color: 'var(--color-text-secondary)', fontSize: 14, marginBottom: 12 }}>{message}</p>
            {onRetry && (
                <Button size="small" icon={<ReloadOutlined />} onClick={onRetry}>重试</Button>
            )}
        </div>
    )
}

export function WeakNetworkBanner() {
    return (
        <div className={styles.weakBanner}>
            <WifiOutlined style={{ marginRight: 6 }} />
            当前网络较慢，已降低图片质量与加载数量
        </div>
    )
}

/** 检测弱网（3G/慢速）；连接 API 不可用时返回 false */
export function isWeakNetwork(): boolean {
    const conn = (navigator as unknown as { connection?: { effectiveType?: string; saveData?: boolean } }).connection
    if (!conn) return false
    if (conn.saveData) return true
    return ['slow-2g', '2g', '3g'].includes(conn.effectiveType ?? '')
}

/** 按弱网状态返回 pageSize（3G 降为 10） */
export function pageSizeForNetwork(normal: number): number {
    return isWeakNetwork() ? Math.min(normal, 10) : normal
}

/** 图片懒加载属性（IntersectionObserver 语义） */
export const lazyImageProps = {
    loading: 'lazy' as const,
    decoding: 'async' as const,
}

export { default as SkeletonState } from '@/components/Skeleton'
