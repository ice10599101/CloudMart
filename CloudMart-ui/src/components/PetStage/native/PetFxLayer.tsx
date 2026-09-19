import { useMemo } from 'react'
import type { FxEvent } from './stageEngine'
import styles from './stageFx.module.css'

/**
 * 粒子特效层（DOM 实现）。
 *
 * 引擎只推"在什么位置发生了什么"（FxEvent，坐标已是舞台像素），
 * 本层负责形状、数量、随机漂移与生命周期；动画结束即回调移除，不残留节点。
 *
 * count > 1 时按索引扇形铺开（爱心/星星/泡泡群），避免全部重叠在一个点。
 */

interface PetFxLayerProps {
    items: FxEvent[]
    onDone: (id: number) => void
}

interface ParticlePlan {
    dx: number
    dy: number
    drift: number
    rise: number
    delay: number
    scale: number
    size: number
}

/** 按索引生成稳定的铺开参数（不用随机数，保证同一事件多次渲染不抖动） */
function planParticle(kind: FxEvent['kind'], index: number, count: number): ParticlePlan {
    const spread = count > 1 ? index - (count - 1) / 2 : 0
    const wave = Math.sin(index * 2.399)     // 黄金角伪随机
    switch (kind) {
        case 'heart':
            return { dx: spread * 26, dy: -6 + Math.abs(spread) * 4, drift: spread * 12 + wave * 8, rise: 92 + index * 10, delay: index * 0.08, scale: 1, size: 22 }
        case 'star':
            return { dx: Math.cos((index / Math.max(1, count)) * Math.PI * 2) * (44 + index * 4), dy: -18, drift: wave * 16, rise: 70 + index * 6, delay: index * 0.05, scale: 1, size: 22 }
        case 'bubble':
            return { dx: spread * 20 + wave * 10, dy: 12, drift: wave * 20, rise: 120 + index * 8, delay: index * 0.07, scale: 1, size: 16 + (index % 4) * 5 }
        case 'zzz':
            return { dx: 26, dy: 0, drift: 26, rise: 76, delay: 0, scale: 1, size: 24 }
        case 'food':
            // 从画面外侧飞入并精确落在发射点（宠物嘴边）：起点偏移 = 位移量取反
            return { dx: -150, dy: 72, drift: 150, rise: -72, delay: 0, scale: 1, size: 40 }
        case 'text':
            return { dx: 0, dy: 6, drift: -6, rise: 78, delay: 0, scale: 1, size: 19 }
        case 'ring':
            return { dx: 0, dy: 0, drift: 0, rise: 0, delay: 0, scale: 1, size: 34 }
        default:
            return { dx: wave * 34, dy: wave * 20, drift: wave * 22, rise: 46 + index * 6, delay: index * 0.04, scale: 1, size: 12 }
    }
}

export default function PetFxLayer({ items, onDone }: PetFxLayerProps) {
    return (
        <div className={styles.layer}>
            {items.map((item) => (
                <FxGroup key={item.id} event={item} onDone={() => onDone(item.id)} />
            ))}
        </div>
    )
}

function FxGroup({ event, onDone }: { event: FxEvent; onDone: () => void }) {
    const count = event.kind === 'ring' || event.kind === 'text' || event.kind === 'zzz' || event.kind === 'food'
        ? 1
        : Math.max(1, event.count ?? 1)

    const plans = useMemo(
        () => Array.from({ length: count }, (_, index) => planParticle(event.kind, index, count)),
        [count, event.kind],
    )

    const styleFor = (plan: ParticlePlan): React.CSSProperties => ({
        '--fx-x': `${event.x + plan.dx}px`,
        '--fx-y': `${event.y + plan.dy}px`,
        '--fx-drift': `${plan.drift}px`,
        '--fx-rise': `${plan.rise}px`,
        '--fx-delay': `${plan.delay}s`,
        '--fx-duration': `${event.kind === 'text' ? 1.6
            : event.kind === 'heart' ? 1.5
                : event.kind === 'food' ? 0.62 : 1.25}s`,
        '--fx-size': `${plan.size}px`,
        '--fx-font': `${plan.size}px`,
        ...(event.color ? { '--fx-color': event.color } : {}),
    } as React.CSSProperties)

    return (
        <>
            {plans.map((plan, index) => (
                <span
                    key={`${event.id}-${index}`}
                    className={styles.particle}
                    style={styleFor(plan)}
                    onAnimationEnd={onDone}
                >
                    {renderShape(event, plan)}
                </span>
            ))}
        </>
    )
}

function renderShape(event: FxEvent, plan: ParticlePlan) {
    switch (event.kind) {
        case 'heart':
            return <span className={styles.heart} />
        case 'star':
            return <span className={styles.star} />
        case 'bubble':
            return <span className={styles.bubble} style={{ '--fx-size': `${plan.size}px` } as React.CSSProperties} />
        case 'zzz':
            return <span className={styles.text} style={{ '--fx-font': '22px', '--fx-color': '#8B74B8' } as React.CSSProperties}>Z</span>
        case 'food':
            return <span className={styles.emoji}>{event.text || '🍖'}</span>
        case 'text':
            return <span className={styles.text}>{event.text}</span>
        case 'ring':
            return <span className={styles.ring} />
        default:
            return <span className={styles.spark} />
    }
}
