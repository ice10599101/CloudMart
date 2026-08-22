import { useEffect, useMemo, useRef, useState } from 'react'
import { Animated, Easing, PanResponder, StyleSheet, TouchableOpacity, View } from 'react-native'
import type { ViewStyle } from 'react-native'
import type { TreeEnvParticle, TreeFruit } from '@/types'
import type { TreeEnvTheme } from '@/utils/tree-env'
import { withAlpha } from '@/utils/tree-env'
import { FRUIT_COLORS } from '@/constants/wish-theme'

export interface WorldTree3DProps {
  fruits: TreeFruit[]
  /** 动态环境主题（Sprint 2.2；null 时组件回退默认视觉） */
  theme: TreeEnvTheme | null
  onFruitSelect: (fruit: TreeFruit) => void
}

/** 渲染上限（PanResponder 旋转时全量重渲染成本控制） */
const MAX_POINTS = 80
/** 拖拽旋转手势接管阈值（px，低于此为点按果实不接管） */
const PAN_ACTIVATE_THRESHOLD = 8
/** 旋转重渲染节流间隔（ms，约 30fps） */
const ROTATE_THROTTLE_MS = 32
/** 垂直视角偏移夹紧（弧度） */
const MAX_PHI_OFFSET = 0.7

/** 背景星点数（静态装饰层，不随拖拽重渲染变化） */
const STAR_COUNT = 36

/** 降级默认主题（与 resolveTreeEnvTheme 的 DEFAULT 对齐，theme=null 时使用） */
const FALLBACK_THEME: TreeEnvTheme = {
  skyColor: '#0d1b2e',
  crownColor: '#3ddc97',
  coreColor: '#ffd700',
  particle: 'NONE',
}

/** 环境粒子降级规格（Sprint 2.2，与小程序端 PARTICLE_SPECS 对齐） */
interface ParticleSpec {
  color: string
  size: number
  duration: number
  mode: 'fall' | 'meteor' | 'twinkle'
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

/** 粒子数（Animated 原生驱动，14 颗 + 确定性散布即可铺满圆盘） */
const PARTICLE_COUNT = 14

interface ParticleSpot {
  left: number
  top: number
  delay: number
}

/** 确定性粒子散布（序号散列错开相位，与小程序端同思路避免随机跳变） */
const PARTICLE_SPOTS: ParticleSpot[] = Array.from({ length: PARTICLE_COUNT }, (_, i) => ({
  left: 6 + ((i * 37) % 86),
  top: 8 + ((i * 53) % 78),
  delay: (i % 7) * 0.6,
}))

interface BackgroundStar {
  left: number
  top: number
  size: number
  opacity: number
}

/**
 * 确定性星空散布（模块级一次性生成）：
 * 黄金角 + 等面积径向分布；尺寸/透明度按序号散列分档制造层次，
 * 静态渲染零动画成本（APP 端拖拽时全量重渲染，星点为常量不参与计算）。
 */
const BACKGROUND_STARS: BackgroundStar[] = Array.from({ length: STAR_COUNT }, (_, i) => {
  const theta = (i * 2.399963229728653) % (Math.PI * 2)
  const radius = Math.sqrt((i + 0.5) / STAR_COUNT)
  return {
    left: 50 + Math.cos(theta) * radius * 48,
    top: 50 + Math.sin(theta) * radius * 48,
    size: 1.5 + ((i * 7) % 3),
    opacity: 0.2 + ((i * 13) % 5) * 0.13,
  }
})

interface ProjectedFruit {
  fruit: TreeFruit
  left: number
  top: number
  depth: number
}

/**
 * 正面半球投影（伪 3D 星图）+ 用户拖拽旋转视角偏移：
 * 观察方向 +z，仅渲染 z>0 的果实；x/y 归一化映射到圆盘百分比坐标，
 * z 作为深度驱动大小与透明度（近大远小）。
 */
function projectFruits(fruits: TreeFruit[], thetaOffset: number, phiOffset: number): ProjectedFruit[] {
  const visible: ProjectedFruit[] = []
  for (const fruit of fruits) {
    const theta = fruit.position.theta + thetaOffset
    const phi = fruit.position.phi + phiOffset
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

/** 单颗粒子：useNativeDriver 驱动 translate/opacity，拖拽重渲染不中断动画 */
function EnvParticle({
                       spec,
                       spot,
                       index,
                       containerHeight,
                     }: {
  spec: ParticleSpec
  spot: ParticleSpot
  index: number
  containerHeight: number
}) {
  const progress = useRef(new Animated.Value(0)).current

  useEffect(() => {
    const loop = Animated.loop(
        Animated.timing(progress, {
          toValue: 1,
          duration: spec.duration * 1000,
          delay: spot.delay * 1000,
          easing: Easing.linear,
          useNativeDriver: true,
        }),
    )
    loop.start()
    return () => loop.stop()
  }, [progress, spec.duration, spot.delay])

  // fall：从盘外上方竖落穿出盘外下方；meteor：斜落半程；twinkle：原地明暗
  const travel = containerHeight + 24
  const translateFall = progress.interpolate({ inputRange: [0, 1], outputRange: [-24, travel] })
  const translateMeteorY = progress.interpolate({ inputRange: [0, 1], outputRange: [-24, travel * 0.6] })
  const translateMeteorX = progress.interpolate({ inputRange: [0, 1], outputRange: [0, 56] })
  const twinkleOpacity = progress.interpolate({ inputRange: [0, 0.5, 1], outputRange: [0.15, 0.9, 0.15] })

  const baseStyle: ViewStyle = {
    position: 'absolute',
    left: `${spot.left}%`,
    top: spec.mode === 'twinkle' ? `${spot.top}%` : '0%',
    width: spec.streak ? 2 : spec.size,
    height: spec.streak ? spec.size * 1.6 : spec.size,
    borderRadius: spec.streak ? 1 : spec.size / 2,
    backgroundColor: spec.color,
    marginLeft: -(spec.streak ? 1 : spec.size / 2),
    marginTop: -(spec.streak ? spec.size * 0.8 : spec.size / 2),
  }
  const motionStyle =
      spec.mode === 'twinkle'
          ? { opacity: twinkleOpacity }
          : spec.mode === 'meteor'
              ? { opacity: 0.85, transform: [{ translateY: translateMeteorY }, { translateX: translateMeteorX }] }
              : { opacity: 0.85, transform: [{ translateY: translateFall }] }

  return <Animated.View key={`particle-${index}`} style={[baseStyle, motionStyle]} />
}

/**
 * 世界树 3D 场景（APP 端 RN 原生版）：
 * 伪 3D 星图降级渲染（与小程序端视觉一致）+ PanResponder 拖拽旋转视角
 * + 动态环境主题（Sprint 2.2：天空/树冠/树心/粒子由 theme 驱动）。
 * 不引入 expo-three/three：expo-three 维护滞后且 three 的 OrbitControls 依赖 DOM，
 * Metro 打包 three 体积与兼容风险高；PanResponder/Animated 为 RN 核心 API 零新增依赖。
 */
export default function WorldTree3D({ fruits, theme, onFruitSelect }: WorldTree3DProps) {
  const [thetaOffset, setThetaOffset] = useState(0)
  const [phiOffset, setPhiOffset] = useState(0)
  const [sphereHeight, setSphereHeight] = useState(0)
  const rotationRef = useRef({ theta: 0, phi: 0, lastX: 0, lastY: 0, lastEmit: 0 })
  const corePulse = useRef(new Animated.Value(0.75)).current

  const activeTheme = theme ?? FALLBACK_THEME
  const particleSpec = activeTheme.particle !== 'NONE' ? PARTICLE_SPECS[activeTheme.particle] : null

  useEffect(() => {
    // 树心脉冲：scale 为主 opacity 为辅（纯透明度变化视觉过弱，走查反馈「无动画」）
    const loop = Animated.loop(
        Animated.sequence([
          Animated.timing(corePulse, { toValue: 1, duration: 1600, useNativeDriver: true }),
          Animated.timing(corePulse, { toValue: 0.6, duration: 1600, useNativeDriver: true }),
        ]),
    )
    loop.start()
    return () => loop.stop()
  }, [corePulse])

  const panResponder = useMemo(
      () =>
          PanResponder.create({
            // capture 阶段接管：优先于子元素 TouchableOpacity 的 responder 协商，
            // react-native-web 下普通 move 协商可能被子组件拦截导致拖不动
            onMoveShouldSetPanResponderCapture: (_e, gestureState) =>
                Math.abs(gestureState.dx) > PAN_ACTIVATE_THRESHOLD ||
                Math.abs(gestureState.dy) > PAN_ACTIVATE_THRESHOLD,
            onMoveShouldSetPanResponder: (_e, gestureState) =>
                Math.abs(gestureState.dx) > PAN_ACTIVATE_THRESHOLD ||
                Math.abs(gestureState.dy) > PAN_ACTIVATE_THRESHOLD,
            onPanResponderGrant: (e) => {
              rotationRef.current.lastX = e.nativeEvent.pageX
              rotationRef.current.lastY = e.nativeEvent.pageY
              rotationRef.current.lastEmit = 0
            },
            onPanResponderMove: (e) => {
              const now = Date.now()
              if (now - rotationRef.current.lastEmit < ROTATE_THROTTLE_MS) return
              rotationRef.current.lastEmit = now
              const dx = e.nativeEvent.pageX - rotationRef.current.lastX
              const dy = e.nativeEvent.pageY - rotationRef.current.lastY
              rotationRef.current.lastX = e.nativeEvent.pageX
              rotationRef.current.lastY = e.nativeEvent.pageY
              rotationRef.current.theta -= dx * 0.01
              rotationRef.current.phi = Math.max(
                  -MAX_PHI_OFFSET,
                  Math.min(MAX_PHI_OFFSET, rotationRef.current.phi + dy * 0.005),
              )
              setThetaOffset(rotationRef.current.theta)
              setPhiOffset(rotationRef.current.phi)
            },
          }),
      [],
  )

  const points = projectFruits(fruits, thetaOffset, phiOffset)

  return (
      <View
          style={[styles.sphere, { backgroundColor: withAlpha(activeTheme.skyColor, 0.92) }]}
          onLayout={(e) => setSphereHeight(e.nativeEvent.layout.height)}
          {...panResponder.panHandlers}
          accessibilityLabel="世界树星图"
      >
        {BACKGROUND_STARS.map(({ left, top, size, opacity }, index) => (
            <View
                key={`star-${index}`}
                style={{
                  position: 'absolute',
                  left: `${left}%`,
                  top: `${top}%`,
                  width: size,
                  height: size,
                  borderRadius: size / 2,
                  marginLeft: -size / 2,
                  marginTop: -size / 2,
                  backgroundColor: '#ffffff',
                  opacity,
                }}
            />
        ))}
        {/* 环境粒子层：key 绑定粒子类型，切换环境时重建动画 */}
        {particleSpec && sphereHeight > 0 && (
            <View key={`layer-${activeTheme.particle}`} style={StyleSheet.absoluteFill} pointerEvents="none">
              {PARTICLE_SPOTS.map((spot, index) => (
                  <EnvParticle
                      key={`particle-${index}`}
                      spec={particleSpec}
                      spot={spot}
                      index={index}
                      containerHeight={sphereHeight}
                  />
              ))}
            </View>
        )}
        <View style={[styles.sphereGlow, { borderColor: activeTheme.crownColor }]} />
        <View style={styles.sphereInner} />
        <Animated.View
            style={[
              styles.sphereCore,
              {
                backgroundColor: activeTheme.coreColor,
                opacity: corePulse.interpolate({ inputRange: [0.6, 1], outputRange: [0.75, 1] }),
                transform: [
                  { scale: corePulse.interpolate({ inputRange: [0.6, 1], outputRange: [0.85, 1.15] }) },
                ],
              },
            ]}
        />
        {points.map(({ fruit, left, top, depth }) => {
          const color = FRUIT_COLORS[fruit.fruitType] || '#ffffff'
          const size = 8 + depth * 6
          return (
              <TouchableOpacity
                  key={fruit.id}
                  activeOpacity={0.6}
                  onPress={() => onFruitSelect(fruit)}
                  hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}
                  style={{
                    position: 'absolute',
                    left: `${left}%`,
                    top: `${top}%`,
                    width: size,
                    height: size,
                    borderRadius: size / 2,
                    marginLeft: -size / 2,
                    marginTop: -size / 2,
                    backgroundColor: color,
                    opacity: 0.45 + depth * 0.55,
                    shadowColor: color,
                    shadowOffset: { width: 0, height: 0 },
                    shadowOpacity: 0.6,
                    shadowRadius: 4 + depth * 6,
                    elevation: 4,
                  }}
              />
          )
        })}
      </View>
  )
}

const styles = StyleSheet.create({
  sphere: {
    width: '100%',
    aspectRatio: 1,
    borderRadius: 9999,
    overflow: 'hidden',
    backgroundColor: '#0d1b2e',
  },
  sphereGlow: {
    position: 'absolute',
    left: '6%',
    right: '6%',
    top: '6%',
    bottom: '6%',
    borderRadius: 9999,
    borderWidth: 1.5,
    backgroundColor: 'rgba(61, 220, 151, 0.04)',
  },
  sphereInner: {
    position: 'absolute',
    left: '16%',
    right: '16%',
    top: '16%',
    bottom: '16%',
    borderRadius: 9999,
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: 'rgba(255,255,255,0.18)',
  },
  sphereCore: {
    position: 'absolute',
    left: '42%',
    top: '42%',
    width: '16%',
    height: '16%',
    borderRadius: 9999,
  },
})
