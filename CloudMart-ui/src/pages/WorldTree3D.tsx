import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button, Card, Spin, Tag } from 'antd'
import { ArrowLeftOutlined, NodeIndexOutlined } from '@ant-design/icons'
import { history } from 'umi'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { getTreeEnv, getWorldTree, listEnvConfigs, listTreeFruits } from '@/api/wish'
import type {
  EnvConfigItem,
  TreeEnvParticle,
  TreeEnvSnapshot,
  TreeFruit,
  TreeFruitsQuery,
  WorldTreeAggregation,
} from '@/api/wish'
import { resolveTreeEnvTheme, withAlpha } from '@/utils/tree-env'
import type { TreeEnvTheme } from '@/utils/tree-env'
import styles from './WorldTree3D.module.css'
import WishBGM from '@/components/WishBGM'

// ========== 常量 ==========

/** 树冠球半径（果实贴在球壳表面） */
const TREE_RADIUS = 1.6
/** InstancedMesh 容量：单屏渲染果实上限（超出按加载序截断） */
const MAX_RENDER_FRUITS = 600
/** 单页拉取上限（与后端 pageSize 上限对齐） */
const PAGE_SIZE = 200
/** 单次视口加载最多翻页数（防失控，200×5=1000 条足够覆盖任何单视口） */
const MAX_PAGES_PER_VIEWPORT = 5
/** 视角变化触发阈值（rad）：autoRotate 0.3 速度下约 5s 触发一次 */
const VIEWPORT_TRIGGER_ANGLE = 0.6
/** 视口请求节流（ms） */
const VIEWPORT_THROTTLE_MS = 1500
/** 用户交互后恢复自动旋转的等待时间（ms） */
const AUTO_ROTATE_RESUME_MS = 4000
/** 环境快照轮询间隔（ms）：特殊事件全站同步 + 情绪扫描 5min，1min 拉取足够实时 */
const TREE_ENV_POLL_MS = 60000

const FRUIT_LABELS: Record<string, string> = {
  GLOW: '微光',
  RESONANCE: '共鸣',
  BLOOM: '绽放',
  SPARK: '星火',
}

const FRUIT_COLORS: Record<string, number> = {
  GLOW: 0x00d4ff,
  RESONANCE: 0x9370db,
  BLOOM: 0xff6b6b,
  SPARK: 0xffd700,
}

const SEASON_LABELS: Record<string, string> = {
  SPRING: '春 · 萌芽',
  SUMMER: '夏 · 繁盛',
  AUTUMN: '秋 · 收获',
  WINTER: '冬 · 静待',
}

/** displayEnv 无匹配配置时的标签兜底（配置接口失败仍可展示） */
const DISPLAY_ENV_FALLBACK_LABELS: Record<string, string> = {
  SUNNY: '晴空',
  CLOUDY: '多云',
  RAIN: '细雨',
  SNOW: '落雪',
  RAINBOW: '彩虹',
  METEOR_SHOWER: '流星雨',
  AURORA: '极光',
  STAR_NIGHT: '星辰夜',
}

/**
 * 环境粒子运动规格（Sprint 2.2，按 wish_env_config.visual.particle 驱动）：
 * vy 负=下落正=上升；sway 为水平摆动幅度；still 仅慢速旋转（星辰）。
 */
interface ParticleMotion {
  count: number
  color: number
  size: number
  vy: number
  vx: number
  sway: number
  still: boolean
}

const PARTICLE_MOTIONS: Record<Exclude<TreeEnvParticle, 'NONE'>, ParticleMotion> = {
  RAIN: { count: 420, color: 0x9fd8ff, size: 0.03, vy: -3.4, vx: 0, sway: 0, still: false },
  SNOWFLAKE: { count: 260, color: 0xffffff, size: 0.05, vy: -0.55, vx: 0, sway: 0.3, still: false },
  PETAL: { count: 170, color: 0xffb7d5, size: 0.07, vy: -0.5, vx: 0, sway: 0.55, still: false },
  LEAF: { count: 170, color: 0xffb347, size: 0.07, vy: -0.65, vx: 0, sway: 0.45, still: false },
  SUNBURST: { count: 150, color: 0xffd700, size: 0.045, vy: 0.4, vx: 0, sway: 0.2, still: false },
  METEOR: { count: 100, color: 0xffffff, size: 0.06, vy: -5.2, vx: 2.4, sway: 0, still: false },
  AURORA: { count: 240, color: 0x7ef0c0, size: 0.05, vy: 0.3, vx: 0, sway: 1.0, still: false },
  STAR: { count: 220, color: 0xfff2b2, size: 0.04, vy: 0, vx: 0, sway: 0, still: true },
}

/** 粒子活动包围盒（覆盖树冠球 + 上下留空） */
const PARTICLE_BOX = { x: 2.6, yMin: -1.6, yMax: 2.8 }

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

/** 球面角 → 笛卡尔坐标（与后端 theta/phi 契约对齐：phi=0 北极朝 +y） */
function toCartesian(theta: number, phi: number, radius: number): THREE.Vector3 {
  const sinPhi = Math.sin(phi)
  return new THREE.Vector3(
      radius * sinPhi * Math.cos(theta),
      radius * Math.cos(phi),
      radius * sinPhi * Math.sin(theta),
  )
}

/**
 * 由相机位置计算当前视口 bounds（弧度制，lat→phi、lng→theta 契约）。
 * span 固定 1.25rad（约 72°）覆盖 fov60° 视野并留余量；经度跨 0/2π 时
 * 归一化后 minLng > maxLng，恰好命中后端环绕窗口语义。
 */
function computeViewportBounds(camera: THREE.PerspectiveCamera): TreeFruitsQuery {
  const distance = camera.position.length()
  const phi = Math.acos(THREE.MathUtils.clamp(camera.position.y / distance, -1, 1))
  const theta = (Math.atan2(camera.position.z, camera.position.x) + Math.PI * 2) % (Math.PI * 2)
  const span = 1.25
  const twoPi = Math.PI * 2
  const minLat = Math.max(0, phi - span)
  const maxLat = Math.min(Math.PI, phi + span)
  const minLng = (theta - span + twoPi) % twoPi
  const maxLng = (theta + span) % twoPi
  return { minLat, maxLat, minLng, maxLng, pageSize: PAGE_SIZE }
}

// ========== Three.js 场景封装（副作用与 React 解耦） ==========

interface TreeSceneHandle {
  setFruits(fruits: TreeFruit[]): void
  applyTheme(theme: TreeEnvTheme): void
  onViewportChange(callback: (query: TreeFruitsQuery) => void): void
  onFruitClick(callback: (fruit: TreeFruit) => void): void
  dispose(): void
}

function createStarfield(scene: THREE.Scene): THREE.Points {
  const starCount = 900
  const positions = new Float32Array(starCount * 3)
  for (let i = 0; i < starCount; i++) {
    // 随机球壳分布（半径 10-22），营造深空包围感
    const r = 10 + Math.random() * 12
    const u = Math.random() * 2 - 1
    const angle = Math.random() * Math.PI * 2
    const s = Math.sqrt(1 - u * u)
    positions[i * 3] = r * s * Math.cos(angle)
    positions[i * 3 + 1] = r * u
    positions[i * 3 + 2] = r * s * Math.sin(angle)
  }
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  const material = new THREE.PointsMaterial({
    color: 0xffffff,
    size: 0.05,
    sizeAttenuation: true,
    transparent: true,
    opacity: 0.8,
    depthWrite: false,
  })
  const stars = new THREE.Points(geometry, material)
  scene.add(stars)
  return stars
}

interface TreeBodyParts {
  canopyMaterial: THREE.MeshBasicMaterial
  coreMaterial: THREE.MeshBasicMaterial
  haloMaterial: THREE.MeshBasicMaterial
}

function createTreeBody(scene: THREE.Scene): TreeBodyParts {
  // 树干（从下方承托树冠球）
  const trunkGeometry = new THREE.CylinderGeometry(0.1, 0.22, 1.3, 10)
  const trunkMaterial = new THREE.MeshStandardMaterial({ color: 0x4a3728, roughness: 0.9 })
  const trunk = new THREE.Mesh(trunkGeometry, trunkMaterial)
  trunk.position.y = -TREE_RADIUS - 0.65
  scene.add(trunk)

  // 底座圆盘（大地）
  const groundGeometry = new THREE.CircleGeometry(1.1, 40)
  const groundMaterial = new THREE.MeshStandardMaterial({
    color: 0x16213e,
    roughness: 1,
    transparent: true,
    opacity: 0.85,
  })
  const ground = new THREE.Mesh(groundGeometry, groundMaterial)
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -TREE_RADIUS - 1.3
  scene.add(ground)

  // 内层能量球（深色实体，避免果实透视穿帮）
  const innerGeometry = new THREE.SphereGeometry(TREE_RADIUS * 0.96, 48, 48)
  const innerMaterial = new THREE.MeshBasicMaterial({
    color: 0x0f3460,
    transparent: true,
    opacity: 0.55,
  })
  scene.add(new THREE.Mesh(innerGeometry, innerMaterial))

  // 树冠线框壳（季节主题色，applyTheme 时按环境配置更新）
  const canopyGeometry = new THREE.SphereGeometry(TREE_RADIUS, 36, 24)
  const canopyMaterial = new THREE.MeshBasicMaterial({
    color: 0x3ddc97,
    wireframe: true,
    transparent: true,
    opacity: 0.18,
  })
  scene.add(new THREE.Mesh(canopyGeometry, canopyMaterial))

  // 世界树之心（内核光球，环境主题色）
  const coreGeometry = new THREE.SphereGeometry(0.3, 24, 24)
  const coreMaterial = new THREE.MeshBasicMaterial({ color: 0xffd700 })
  scene.add(new THREE.Mesh(coreGeometry, coreMaterial))

  // 光晕外壳
  const haloGeometry = new THREE.SphereGeometry(0.42, 24, 24)
  const haloMaterial = new THREE.MeshBasicMaterial({
    color: 0xffd700,
    transparent: true,
    opacity: 0.15,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  })
  scene.add(new THREE.Mesh(haloGeometry, haloMaterial))

  return { canopyMaterial, coreMaterial, haloMaterial }
}

function createTreeScene(canvas: HTMLCanvasElement): TreeSceneHandle {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))

  const scene = new THREE.Scene()
  const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 60)
  camera.position.set(2.6, 1.3, 3.2)

  const controls = new OrbitControls(camera, canvas)
  controls.enableDamping = true
  controls.dampingFactor = 0.06
  controls.enablePan = false
  controls.minDistance = 2.4
  controls.maxDistance = 9
  controls.autoRotate = true
  controls.autoRotateSpeed = 0.3

  scene.add(new THREE.AmbientLight(0xffffff, 0.8))
  const directional = new THREE.DirectionalLight(0xffffff, 1.2)
  directional.position.set(3, 5, 2)
  scene.add(directional)

  const stars = createStarfield(scene)
  const treeBody = createTreeBody(scene)

  // 果实双层：核心实心球 + 光晕壳（加性混合）
  const fruitGeometry = new THREE.SphereGeometry(0.045, 12, 10)
  const fruitMaterial = new THREE.MeshBasicMaterial()
  const haloGeometry = new THREE.SphereGeometry(0.085, 12, 10)
  const haloMaterial = new THREE.MeshBasicMaterial({
    transparent: true,
    opacity: 0.16,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  })
  const coreMesh = new THREE.InstancedMesh(fruitGeometry, fruitMaterial, MAX_RENDER_FRUITS)
  const haloMesh = new THREE.InstancedMesh(haloGeometry, haloMaterial, MAX_RENDER_FRUITS)
  coreMesh.count = 0
  haloMesh.count = 0
  scene.add(coreMesh)
  scene.add(haloMesh)

  // ===== 状态 =====
  let fruits: TreeFruit[] = []
  const instancePhases: number[] = []
  let viewportCallback: ((query: TreeFruitsQuery) => void) | null = null
  let fruitClickCallback: ((fruit: TreeFruit) => void) | null = null
  let lastRequestedSpherical = new THREE.Spherical().setFromVector3(camera.position)
  let lastTriggerTime = 0
  let idleTimer: ReturnType<typeof setTimeout> | null = null
  let disposed = false

  const dummy = new THREE.Object3D()
  const fruitColor = new THREE.Color()

  const writeInstance = (index: number, scale: number) => {
    const fruit = fruits[index]
    if (!fruit) return
    const position = toCartesian(fruit.position.theta, fruit.position.phi, TREE_RADIUS)
    dummy.position.copy(position)
    dummy.scale.setScalar(scale)
    dummy.updateMatrix()
    coreMesh.setMatrixAt(index, dummy.matrix)
    dummy.scale.setScalar(scale * 1.9)
    dummy.updateMatrix()
    haloMesh.setMatrixAt(index, dummy.matrix)
  }

  const handleSetFruits = (nextFruits: TreeFruit[]) => {
    fruits = nextFruits.slice(0, MAX_RENDER_FRUITS)
    instancePhases.length = 0
    coreMesh.count = fruits.length
    haloMesh.count = fruits.length
    for (let i = 0; i < fruits.length; i++) {
      fruitColor.setHex(FRUIT_COLORS[fruits[i].fruitType] ?? 0xffffff)
      coreMesh.setColorAt(i, fruitColor)
      haloMesh.setColorAt(i, fruitColor)
      instancePhases.push((fruits[i].id % 97) * 0.35)
      writeInstance(i, 1)
    }
    coreMesh.instanceMatrix.needsUpdate = true
    haloMesh.instanceMatrix.needsUpdate = true
    if (coreMesh.instanceColor) coreMesh.instanceColor.needsUpdate = true
    if (haloMesh.instanceColor) haloMesh.instanceColor.needsUpdate = true
  }

  // ===== 环境粒子层（Sprint 2.2，visual.particle 驱动；NONE 时移除） =====
  let particlePoints: THREE.Points | null = null
  let particleMotion: ParticleMotion | null = null
  let lastParticle: TreeEnvParticle = 'NONE'

  const disposeParticles = () => {
    if (!particlePoints) return
    scene.remove(particlePoints)
    particlePoints.geometry.dispose()
    ;(particlePoints.material as THREE.PointsMaterial).dispose()
    particlePoints = null
    particleMotion = null
  }

  const setParticle = (particle: TreeEnvParticle) => {
    if (particle === lastParticle) return
    lastParticle = particle
    disposeParticles()
    if (particle === 'NONE') return
    const motion = PARTICLE_MOTIONS[particle]
    const positions = new Float32Array(motion.count * 3)
    for (let i = 0; i < motion.count; i++) {
      positions[i * 3] = (Math.random() * 2 - 1) * PARTICLE_BOX.x
      positions[i * 3 + 1] =
          PARTICLE_BOX.yMin + Math.random() * (PARTICLE_BOX.yMax - PARTICLE_BOX.yMin)
      positions[i * 3 + 2] = (Math.random() * 2 - 1) * PARTICLE_BOX.x
    }
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    const material = new THREE.PointsMaterial({
      color: motion.color,
      size: motion.size,
      transparent: true,
      opacity: 0.75,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    })
    particlePoints = new THREE.Points(geometry, material)
    particleMotion = motion
    scene.add(particlePoints)
  }

  const handleApplyTheme = (theme: TreeEnvTheme) => {
    treeBody.canopyMaterial.color.setStyle(theme.crownColor)
    treeBody.coreMaterial.color.setStyle(theme.coreColor)
    treeBody.haloMaterial.color.setStyle(theme.coreColor)
    canvas.style.background = `radial-gradient(circle at 50% 42%, ${withAlpha(theme.skyColor, 0.5)} 0%, #04070f 78%)`
    setParticle(theme.particle)
  }

  // ===== 尺寸自适应（ResizeObserver 而非 window.resize，容器尺寸独立于窗口） =====
  const resize = () => {
    const width = canvas.clientWidth
    const height = canvas.clientHeight
    if (width === 0 || height === 0) return
    renderer.setSize(width, height, false)
    camera.aspect = width / height
    camera.updateProjectionMatrix()
  }
  const resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(canvas)
  resize()

  // ===== 点击拾取（区分拖拽与点击：位移 < 6px 视为点击） =====
  // 屏幕空间最近邻拾取：raycaster 对 0.045 半径果实的精确命中区仅约 2-3px，
  // 实际几乎不可点中；改为果实投影坐标与点击点的像素距离（容差内取最近）。
  const CLICK_RADIUS_PX = 28
  const screenPos = new THREE.Vector3()
  let pointerDownX = 0
  let pointerDownY = 0
  const handlePointerDown = (event: PointerEvent) => {
    pointerDownX = event.clientX
    pointerDownY = event.clientY
  }
  const handlePointerUp = (event: PointerEvent) => {
    if (Math.abs(event.clientX - pointerDownX) > 6 || Math.abs(event.clientY - pointerDownY) > 6) {
      return
    }
    const rect = canvas.getBoundingClientRect()
    const clickX = event.clientX - rect.left
    const clickY = event.clientY - rect.top
    // 背面剔除阈值：果实世界坐标与相机位置的点积 > R² 为朝向相机的正面
    const frontThreshold = TREE_RADIUS * TREE_RADIUS
    let bestIndex = -1
    let bestDist = CLICK_RADIUS_PX
    for (let i = 0; i < fruits.length; i++) {
      const fruit = fruits[i]
      if (!fruit) continue
      const world = toCartesian(fruit.position.theta, fruit.position.phi, TREE_RADIUS)
      if (world.dot(camera.position) < frontThreshold) continue
      screenPos.copy(world).project(camera)
      const sx = (screenPos.x * 0.5 + 0.5) * rect.width
      const sy = (-screenPos.y * 0.5 + 0.5) * rect.height
      const dist = Math.hypot(sx - clickX, sy - clickY)
      if (dist < bestDist) {
        bestDist = dist
        bestIndex = i
      }
    }
    if (bestIndex >= 0 && fruits[bestIndex]) {
      fruitClickCallback?.(fruits[bestIndex])
    }
  }
  canvas.addEventListener('pointerdown', handlePointerDown)
  canvas.addEventListener('pointerup', handlePointerUp)

  // ===== 交互暂停自动旋转（交互 4s 后恢复，避免视口请求风暴） =====
  const pauseAutoRotate = () => {
    controls.autoRotate = false
    if (idleTimer) clearTimeout(idleTimer)
    idleTimer = setTimeout(() => {
      if (!disposed) controls.autoRotate = true
    }, AUTO_ROTATE_RESUME_MS)
  }
  controls.addEventListener('start', pauseAutoRotate)
  controls.addEventListener('end', pauseAutoRotate)

  // ===== 渲染循环 + 视口变化检测 =====
  const clock = new THREE.Clock()
  const currentSpherical = new THREE.Spherical()
  const renderLoop = () => {
    if (disposed) return
    requestAnimationFrame(renderLoop)
    const dt = clock.getDelta()
    const elapsed = clock.elapsedTime
    controls.update()

    // 果实呼吸脉动
    for (let i = 0; i < fruits.length; i++) {
      writeInstance(i, 1 + 0.1 * Math.sin(elapsed * 2 + instancePhases[i]))
    }
    coreMesh.instanceMatrix.needsUpdate = true
    haloMesh.instanceMatrix.needsUpdate = true

    // 星空缓慢自转（不参与视口计算，纯氛围）
    stars.rotation.y = elapsed * 0.005

    // 环境粒子：运动型按速度推进并越界回绕；静止型（星辰）整体慢旋
    if (particlePoints && particleMotion && !particleMotion.still) {
      const positions = particlePoints.geometry.attributes.position as THREE.BufferAttribute
      for (let i = 0; i < positions.count; i++) {
        const y = positions.getY(i) + particleMotion.vy * dt
        if (y < PARTICLE_BOX.yMin || y > PARTICLE_BOX.yMax) {
          // 回绕时重随机水平位置，避免粒子轨迹可预测成列
          positions.setXYZ(
              i,
              (Math.random() * 2 - 1) * PARTICLE_BOX.x,
              particleMotion.vy < 0 ? PARTICLE_BOX.yMax : PARTICLE_BOX.yMin,
              (Math.random() * 2 - 1) * PARTICLE_BOX.x,
          )
          continue
        }
        let x = positions.getX(i) + particleMotion.vx * dt
        if (particleMotion.vx !== 0 && (x > PARTICLE_BOX.x || x < -PARTICLE_BOX.x)) {
          x = -Math.sign(particleMotion.vx) * PARTICLE_BOX.x
        }
        positions.setXYZ(i, x + Math.sin(elapsed * 1.6 + i) * particleMotion.sway * dt, y, positions.getZ(i))
      }
      positions.needsUpdate = true
    } else if (particlePoints) {
      particlePoints.rotation.y = elapsed * 0.02
    }

    // 视口变化：方位/极角偏移超阈值且过了节流窗口 → 通知宿主按 bounds 增量拉取
    currentSpherical.setFromVector3(camera.position)
    const deltaTheta = Math.abs(currentSpherical.theta - lastRequestedSpherical.theta)
    const deltaPhi = Math.abs(currentSpherical.phi - lastRequestedSpherical.phi)
    const now = performance.now()
    const angleMoved = Math.min(deltaTheta, Math.PI * 2 - deltaTheta) + deltaPhi
    if (
        viewportCallback &&
        angleMoved > VIEWPORT_TRIGGER_ANGLE &&
        now - lastTriggerTime > VIEWPORT_THROTTLE_MS
    ) {
      lastTriggerTime = now
      lastRequestedSpherical = currentSpherical.clone()
      viewportCallback(computeViewportBounds(camera))
    }

    renderer.render(scene, camera)
  }
  requestAnimationFrame(renderLoop)

  return {
    setFruits: handleSetFruits,
    applyTheme: handleApplyTheme,
    onViewportChange: (callback) => {
      viewportCallback = callback
    },
    onFruitClick: (callback) => {
      fruitClickCallback = callback
    },
    dispose: () => {
      disposed = true
      if (idleTimer) clearTimeout(idleTimer)
      disposeParticles()
      resizeObserver.disconnect()
      canvas.removeEventListener('pointerdown', handlePointerDown)
      canvas.removeEventListener('pointerup', handlePointerUp)
      controls.removeEventListener('start', pauseAutoRotate)
      controls.removeEventListener('end', pauseAutoRotate)
      controls.dispose()
      coreMesh.dispose()
      haloMesh.dispose()
      fruitGeometry.dispose()
      haloGeometry.dispose()
      fruitMaterial.dispose()
      haloMaterial.dispose()
      stars.geometry.dispose()
      ;(stars.material as THREE.PointsMaterial).dispose()
      scene.traverse((object: THREE.Object3D) => {
        if (object instanceof THREE.Mesh) {
          object.geometry.dispose()
          const material = object.material
          if (Array.isArray(material)) material.forEach((m) => m.dispose())
          else material.dispose()
        }
      })
      renderer.dispose()
    },
  }
}

// ========== 页面组件 ==========

export default function WorldTree3D() {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const sceneRef = useRef<TreeSceneHandle | null>(null)
  const fruitsMapRef = useRef<Map<number, TreeFruit>>(new Map())
  const [aggregation, setAggregation] = useState<WorldTreeAggregation | null>(null)
  const [envSnapshot, setEnvSnapshot] = useState<TreeEnvSnapshot | null>(null)
  const [envConfigs, setEnvConfigs] = useState<EnvConfigItem[]>([])
  const [loading, setLoading] = useState(true)
  const [viewportLoading, setViewportLoading] = useState(false)
  const [selectedFruit, setSelectedFruit] = useState<TreeFruit | null>(null)

  /** 环境主题（displayEnv 仲裁；快照与配置均失败时保持 null 不覆盖既有视觉） */
  const envTheme = useMemo(
      () => (envSnapshot || envConfigs.length > 0 ? resolveTreeEnvTheme(envSnapshot, envConfigs) : null),
      [envSnapshot, envConfigs],
  )

  /** 合并去重（果实位置一经写入不变更，仅新增）并同步到 3D 场景 */
  const mergeFruits = useCallback((items: TreeFruit[]) => {
    const map = fruitsMapRef.current
    let hasNew = false
    for (const item of items) {
      if (!map.has(item.id)) {
        map.set(item.id, item)
        hasNew = true
      }
    }
    if (hasNew) {
      sceneRef.current?.setFruits([...map.values()])
    }
  }, [])

  /** 视口增量加载：游标翻页直到 hasMore=false 或达单次上限 */
  const loadViewport = useCallback(
      async (query: TreeFruitsQuery) => {
        setViewportLoading(true)
        try {
          let cursor: string | undefined
          for (let page = 0; page < MAX_PAGES_PER_VIEWPORT; page++) {
            const res = await listTreeFruits({ ...query, cursor })
            if (!res.data.success) break
            mergeFruits(res.data.data)
            const meta = res.data.meta
            if (!meta?.hasMore || !meta.nextCursor) break
            cursor = meta.nextCursor
          }
        } catch {
          // 动态加载失败静默降级（全局拦截器已提示，已渲染果实不受影响）
        } finally {
          setViewportLoading(false)
        }
      },
      [mergeFruits],
  )

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const scene = createTreeScene(canvas)
    sceneRef.current = scene
    scene.onViewportChange((query) => {
      loadViewport(query)
    })
    scene.onFruitClick((fruit) => {
      setSelectedFruit(fruit)
    })
    return () => {
      scene.dispose()
      sceneRef.current = null
    }
  }, [loadViewport])

  useEffect(() => {
    if (envTheme) sceneRef.current?.applyTheme(envTheme)
  }, [envTheme])

  useEffect(() => {
    const fetchInitial = async () => {
      try {
        const [treeRes, fruitsRes, envRes, configsRes] = await Promise.all([
          getWorldTree(),
          listTreeFruits({ pageSize: PAGE_SIZE }),
          getTreeEnv(),
          listEnvConfigs(),
        ])
        if (treeRes.data.success && treeRes.data.data) {
          setAggregation(treeRes.data.data)
        }
        if (envRes.data.success && envRes.data.data) {
          setEnvSnapshot(envRes.data.data)
        }
        if (configsRes.data.success && Array.isArray(configsRes.data.data)) {
          setEnvConfigs(configsRes.data.data)
        }
        if (fruitsRes.data.success) {
          mergeFruits(fruitsRes.data.data)
          // 全量首屏同样翻页补齐（无 bounds 视口）
          const meta = fruitsRes.data.meta
          if (meta?.hasMore && meta.nextCursor) {
            loadViewport({ cursor: meta.nextCursor, pageSize: PAGE_SIZE })
          }
        }
      } catch {
        // 错误已由 request 拦截器处理
      } finally {
        setLoading(false)
      }
    }
    fetchInitial()
  }, [loadViewport, mergeFruits])

  /** 环境快照轮询：特殊事件全站同步 + 情绪环境 5 分钟扫描，1 分钟拉取足够实时 */
  useEffect(() => {
    const timer = setInterval(async () => {
      try {
        const res = await getTreeEnv()
        if (res.data.success && res.data.data) setEnvSnapshot(res.data.data)
      } catch {
        // 轮询失败静默降级，保留上一轮环境
      }
    }, TREE_ENV_POLL_MS)
    return () => clearInterval(timer)
  }, [])

  return (
      <div className={`${styles.container} wish-universe-theme`}>
        {/* 顶部信息栏 */}
        <div className={styles.header}>
          <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={() => history.push('/wish')}
              aria-label="返回心愿宇宙首页"
              className={styles.backBtn}
          >
            返回
          </Button>
          <div className={styles.titleWrap}>
            <NodeIndexOutlined className={styles.titleIcon} />
            <h1 className={styles.title}>世界生命树</h1>
            {envSnapshot ? (
                <span className={styles.envTags}>
              <Tag color="cyan" className={styles.tag}>
                {SEASON_LABELS[envSnapshot.season] ?? envSnapshot.season}
              </Tag>
              <Tag color={envSnapshot.specialEvent ? 'gold' : 'purple'} className={styles.tag}>
                {envSnapshot.specialEvent
                    ? `✦ ${envSnapshot.specialEvent.title}`
                    : (envConfigs.find((config) => config.envCode === envSnapshot.displayEnv)?.name ??
                        DISPLAY_ENV_FALLBACK_LABELS[envSnapshot.displayEnv] ??
                        envSnapshot.displayEnv)}
              </Tag>
            </span>
            ) : (
                aggregation && (
                    <span className={styles.envTags}>
                <Tag color="cyan" className={styles.tag}>
                  {SEASON_LABELS[aggregation.season] ?? aggregation.season}
                </Tag>
                <Tag color="purple" className={styles.tag}>
                  {DISPLAY_ENV_FALLBACK_LABELS[aggregation.environment] ?? aggregation.environment}
                </Tag>
              </span>
                )
            )}
          </div>
          <div className={styles.stats}>
            {aggregation ? (
                <>
              <span className={styles.statItem}>
                果实 <strong>{formatCount(aggregation.totalFruits)}</strong>
              </span>
                  <span className={styles.statItem}>
                绽放 <strong>{formatCount(aggregation.totalBloom)}</strong>
              </span>
                  <span className={styles.statItem}>
                星光 <strong>{formatCount(aggregation.totalLight)}</strong>
              </span>
                </>
            ) : (
                <span className={styles.statItem}>树语暂不可读</span>
            )}
          </div>
        </div>

        {/* 3D 画布 */}
        <div className={styles.canvasWrap}>
          <canvas
              ref={canvasRef}
              className={styles.canvas}
              aria-label="世界生命树 3D 场景，可拖拽旋转查看心愿果实"
          />
          {loading && (
              <div className={styles.loadingMask}>
                <Spin size="large" description="世界树苏醒中…" />
              </div>
          )}
          {viewportLoading && !loading && (
              <div className={styles.viewportIndicator}>
                <Spin size="small" />
              </div>
          )}
          {/* 果实类型图例 */}
          <div className={styles.legend}>
            {Object.keys(FRUIT_LABELS).map((type) => (
                <span key={type} className={styles.legendItem}>
              <span
                  className={styles.legendDot}
                  style={{ background: `#${FRUIT_COLORS[type].toString(16).padStart(6, '0')}` }}
              />
                  {FRUIT_LABELS[type]}
            </span>
            ))}
          </div>
          <div className={styles.hint}>拖拽旋转 · 滚轮缩放 · 点击果实查看心愿</div>

          {/* 选中果实信息卡 */}
          {selectedFruit && (
              <Card
                  className={styles.fruitCard}
                  title={selectedFruit.title}
                  extra={
                    <Button
                        type="text"
                        size="small"
                        onClick={() => setSelectedFruit(null)}
                        aria-label="关闭果实信息"
                    >
                      ×
                    </Button>
                  }
              >
                <div className={styles.fruitCardMeta}>
                  <Tag color="cyan">{FRUIT_LABELS[selectedFruit.fruitType] ?? selectedFruit.fruitType}</Tag>
                  <span className={styles.fruitAuthor}>{selectedFruit.authorNickname}</span>
                  <span className={styles.fruitLight}>
                ✦ {formatCount(selectedFruit.lightCount)} 点亮
              </span>
                </div>
                <Button
                    type="primary"
                    block
                    onClick={() => history.push(`/wish/${selectedFruit.id}`)}
                    className={styles.viewBtn}
                >
                  查看心愿
                </Button>
              </Card>
          )}
        </div>
        <WishBGM />
      </div>
  )
}
