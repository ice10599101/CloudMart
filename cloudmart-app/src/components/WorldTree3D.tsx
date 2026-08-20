import { useEffect, useMemo, useRef, useState } from 'react'
import { Animated, PanResponder, StyleSheet, TouchableOpacity, View } from 'react-native'
import type { TreeEnvironment, TreeFruit, TreeSeason } from '@/types'
import { FRUIT_COLORS } from '@/constants/wish-theme'

export interface WorldTree3DProps {
  fruits: TreeFruit[]
  season: TreeSeason | null
  environment: TreeEnvironment | null
  onFruitSelect: (fruit: TreeFruit) => void
}

/** 季节 → 树冠描边色（与 WEB/Mobile 端主题一致） */
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

/** 渲染上限（PanResponder 旋转时全量重渲染成本控制） */
const MAX_POINTS = 80
/** 拖拽旋转手势接管阈值（px，低于此为点按果实不接管） */
const PAN_ACTIVATE_THRESHOLD = 8
/** 旋转重渲染节流间隔（ms，约 30fps） */
const ROTATE_THROTTLE_MS = 32
/** 垂直视角偏移夹紧（弧度） */
const MAX_PHI_OFFSET = 0.7

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

/**
 * 世界树 3D 场景（APP 端 RN 原生版）：
 * 伪 3D 星图降级渲染（与小程序端视觉一致）+ PanResponder 拖拽旋转视角。
 * 不引入 expo-three/three：expo-three 维护滞后且 three 的 OrbitControls 依赖 DOM，
 * Metro 打包 three 体积与兼容风险高；PanResponder 为 RN 核心 API 零新增依赖。
 */
export default function WorldTree3D({ fruits, season, environment, onFruitSelect }: WorldTree3DProps) {
  const [thetaOffset, setThetaOffset] = useState(0)
  const [phiOffset, setPhiOffset] = useState(0)
  const rotationRef = useRef({ theta: 0, phi: 0, lastX: 0, lastY: 0, lastEmit: 0 })
  const corePulse = useRef(new Animated.Value(0.75)).current

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(corePulse, { toValue: 1, duration: 1600, useNativeDriver: true }),
        Animated.timing(corePulse, { toValue: 0.75, duration: 1600, useNativeDriver: true }),
      ]),
    )
    loop.start()
    return () => loop.stop()
  }, [corePulse])

  const panResponder = useMemo(
    () =>
      PanResponder.create({
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
  const ringColor = (season && SEASON_RING_COLORS[season]) || '#3ddc97'
  const coreColor = (environment && ENVIRONMENT_CORE_COLORS[environment]) || '#ffd700'

  return (
    <View style={styles.sphere} {...panResponder.panHandlers} accessibilityLabel="世界树星图">
      <View style={[styles.sphereGlow, { borderColor: ringColor }]} />
      <View style={styles.sphereInner} />
      <Animated.View style={[styles.sphereCore, { backgroundColor: coreColor, opacity: corePulse }]} />
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
