import { View } from '@tarojs/components'
import type { TreeEnvParticle, TreeFruit, TreeFruitsQuery } from '@/types'
import type { TreeEnvTheme } from '@/utils/tree-env'
import { withAlpha } from '@/utils/tree-env'
import styles from './index.module.scss'

export interface WorldTree3DProps {
  fruits: TreeFruit[]
  /** 动态环境主题（Sprint 2.2；null 时组件回退默认视觉） */
  theme: TreeEnvTheme | null
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

/** 环境粒子降级规格（Sprint 2.2）：mode fall=竖落 / meteor=斜落 / twinkle=原地闪烁 */
interface ParticleSpec {
  color: string
  /** 粒子尺寸（rpx；宽度 = size/4 时为雨线细条） */
  size: number
  /** 动画周期（s） */
  duration: number
  mode: 'fall' | 'meteor' | 'twinkle'
  /** 雨线细条形态（RAIN/METEOR） */
  streak: boolean
}

const PARTICLE_SPECS: Record<Exclude<TreeEnvParticle, 'NONE'>, ParticleSpec> = {
  RAIN: { color: '#9fd8ff', size: 14, duration: 1.1, mode: 'fall', streak: true },
  SNOWFLAKE: { color: '#ffffff', size: 6, duration: 4.5, mode: 'fall', streak: false },
  PETAL: { color: '#ffb7d5', size: 9, duration: 5, mode: 'fall', streak: false },
  LEAF: { color: '#ffb347', size: 9, duration: 4.2, mode: 'fall', streak: false },
  SUNBURST: { color: '#ffd700', size: 6, duration: 3, mode: 'twinkle', streak: false },
  METEOR: { color: '#ffffff', size: 18, duration: 1.4, mode: 'meteor', streak: true },
  AURORA: { color: '#7ef0c0', size: 8, duration: 3.4, mode: 'twinkle', streak: false },
  STAR: { color: '#fff2b2', size: 6, duration: 2.8, mode: 'twinkle', streak: false },
}

/** 粒子数（CSS 层成本远低于 JS 驱动，18 颗即可铺满圆盘） */
const PARTICLE_COUNT = 18

interface ParticleSpot {
  left: number
  top: number
  delay: number
}

/** 确定性粒子散布（模块级常量，渲染恒定；top 从圆盘上方入场） */
const PARTICLE_SPOTS: ParticleSpot[] = Array.from({ length: PARTICLE_COUNT }, (_, i) => ({
  left: (i * 53 + 8) % 96 + 2,
  top: (i * 29) % 36 - 12,
  delay: ((i * 17) % 10) * 0.42,
}))

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
 * 环境主题（Sprint 2.2）消费后端 displayEnv 仲裁结果——树环色/树心光色/
 * 天空底色 + 粒子 CSS 动画层（雨/雪/花瓣/落叶/流星/闪烁系）。
 * H5 端由 index.h5.tsx 提供 three.js 真 3D 渲染（Taro 多端文件后缀机制自动切换）。
 */
export default function WorldTree3D({ fruits, theme, onFruitSelect }: WorldTree3DProps) {
  const points = projectFruits(fruits)
  const ringColor = theme?.crownColor ?? '#3ddc97'
  const coreColor = theme?.coreColor ?? '#ffd700'
  const particleSpec = theme && theme.particle !== 'NONE' ? PARTICLE_SPECS[theme.particle] : null

  return (
      <View
          className={styles.sphere}
          aria-label='世界树星图'
          style={
            theme
                ? {
                  background: `radial-gradient(circle at 50% 42%, ${withAlpha(theme.skyColor, 0.55)}, rgba(4, 7, 15, 0.95) 78%)`,
                }
                : undefined
          }
      >
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
        {particleSpec &&
            PARTICLE_SPOTS.map(({ left, top, delay }, index) => (
                <View
                    key={`particle-${index}`}
                    className={`${styles.particle} ${
                        particleSpec.mode === 'fall'
                            ? styles.particleFall
                            : particleSpec.mode === 'meteor'
                                ? styles.particleMeteor
                                : styles.particleTwinkle
                    }`}
                    style={{
                      left: `${left}%`,
                      top: `${top}%`,
                      width: particleSpec.streak ? '3rpx' : `${particleSpec.size}rpx`,
                      height: `${particleSpec.size}rpx`,
                      background: particleSpec.color,
                      boxShadow: `0 0 8rpx ${particleSpec.color}`,
                      animationDuration: `${particleSpec.duration}s`,
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
