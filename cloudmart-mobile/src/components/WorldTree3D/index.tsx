import { View } from '@tarojs/components'
import type { TreeEnvironment, TreeFruit, TreeFruitsQuery, TreeSeason } from '@/types'
import styles from './index.module.scss'

export interface WorldTree3DProps {
  fruits: TreeFruit[]
  season: TreeSeason | null
  environment: TreeEnvironment | null
  onFruitSelect: (fruit: TreeFruit) => void
  /** 仅 H5 端（three.js 视口变化增量拉取）生效；小程序降级版忽略 */
  onViewportChange?: (query: TreeFruitsQuery) => void
}

const FRUIT_COLORS: Record<string, string> = {
  GLOW: '#00d4ff',
  RESONANCE: '#9370db',
  BLOOM: '#ff6b6b',
  SPARK: '#ffd700',
}

/** 季节 → 树冠描边色（与 WEB/APP 端主题一致） */
const SEASON_RING_COLORS: Record<string, string> = {
  SPRING: '#7ef0c0',
  SUMMER: '#3ddc97',
  AUTUMN: '#ffb347',
  WINTER: '#bfe8ff',
}

/** 环境 → 树心光色 */
const ENVIRONMENT_CORE_COLORS: Record<string, string> = {
  SUNNY: '#ffd700',
  RAIN: '#4facfe',
  RAINBOW: '#ff9ff3',
}

/** 小程序端降级渲染上限（投影 + setData 成本控制） */
const MAX_POINTS = 120

/** 背景星点数（纯 CSS 装饰层，无 JS 状态驱动） */
const STAR_COUNT = 36

interface BackgroundStar {
  left: number
  top: number
  size: number
  delay: number
}

/**
 * 确定性星空散布（模块级一次性生成，渲染期间恒定）：
 * 黄金角 + 等面积径向分布（与后端 TreePositionCalculator 同思路），
 * 尺寸/相位按序号散列错开，避免每次 render 重随导致星点跳变。
 */
const BACKGROUND_STARS: BackgroundStar[] = Array.from({ length: STAR_COUNT }, (_, i) => {
  const theta = (i * 2.399963229728653) % (Math.PI * 2)
  const radius = Math.sqrt((i + 0.5) / STAR_COUNT)
  return {
    left: 50 + Math.cos(theta) * radius * 48,
    top: 50 + Math.sin(theta) * radius * 48,
    size: 3 + ((i * 7) % 3),
    delay: (i % 8) * 0.45,
  }
})

interface ProjectedFruit {
  fruit: TreeFruit
  left: number
  top: number
  depth: number
}

/**
 * 固定视角正面半球投影（伪 3D 星图）：
 * 观察方向 +z，仅渲染 z>0 的果实；x/y 归一化映射到圆盘百分比坐标，
 * z 作为深度驱动大小与透明度（近大远小）。
 */
function projectFruits(fruits: TreeFruit[]): ProjectedFruit[] {
  const visible: ProjectedFruit[] = []
  for (const fruit of fruits) {
    const { theta, phi } = fruit.position
    const sinPhi = Math.sin(phi)
    const x = sinPhi * Math.cos(theta)
    const y = Math.cos(phi)
    const z = sinPhi * Math.sin(theta)
    if (z <= 0) continue
    visible.push({
      fruit,
      left: 50 + x * 44,
      top: 50 - y * 44,
      depth: z,
    })
    if (visible.length >= MAX_POINTS) break
  }
  return visible
}

/**
 * 世界树 3D 场景（小程序端降级版）：
 * 微信小程序 WebGL 能力受限且 three 依赖体积大，降级为 CSS 伪 3D 星图；
 * H5 端由 index.h5.tsx 提供 three.js 真 3D 渲染（Taro 多端文件后缀机制自动切换）。
 */
export default function WorldTree3D({ fruits, season, environment, onFruitSelect }: WorldTree3DProps) {
  const points = projectFruits(fruits)
  const ringColor = (season && SEASON_RING_COLORS[season]) || '#3ddc97'
  const coreColor = (environment && ENVIRONMENT_CORE_COLORS[environment]) || '#ffd700'

  return (
    <View className={styles.sphere} aria-label='世界树星图'>
      {BACKGROUND_STARS.map(({ left, top, size, delay }, index) => (
        <View
          key={`star-${index}`}
          className={styles.starDot}
          style={{
            left: `${left}%`,
            top: `${top}%`,
            width: `${size}rpx`,
            height: `${size}rpx`,
            animationDelay: `${delay}s`,
          }}
        />
      ))}
      <View className={styles.sphereGlow} style={{ borderColor: ringColor }} />
      <View className={styles.sphereInner} />
      <View className={styles.sphereCore} style={{ background: coreColor }} />
      {points.map(({ fruit, left, top, depth }, index) => {
        const color = FRUIT_COLORS[fruit.fruitType] || '#ffffff'
        return (
          <View
            key={fruit.id}
            className={styles.fruitDot}
            style={{
              left: `${left}%`,
              top: `${top}%`,
              background: color,
              transform: `translate(-50%, -50%) scale(${(0.6 + depth * 0.5).toFixed(2)})`,
              opacity: 0.45 + depth * 0.55,
              animationDelay: `${(index % 10) * 0.25}s`,
              boxShadow: `0 0 ${Math.round(8 + depth * 14)}rpx ${color}`,
            }}
            onClick={() => onFruitSelect(fruit)}
          />
        )
      })}
    </View>
  )
}
