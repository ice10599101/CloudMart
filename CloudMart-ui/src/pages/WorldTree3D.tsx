import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button, Card, Spin, Tag, Segmented } from 'antd'
import { ArrowLeftOutlined, NodeIndexOutlined } from '@ant-design/icons'
import { history } from 'umi'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js'
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
import { deviceTier, fetchFeatureFlags, isFeatureEnabled } from '@/utils/featureFlags'
import WishBGM from '@/components/WishBGM'
import { useThemeStore } from '@/stores/theme'

// ========== 常量 ==========

/** 树冠球半径（果实贴在球壳表面） */
const TREE_RADIUS = 1.6
/** InstancedMesh 容量：单屏渲染果实上限（超出按加载序截断） */
const MAX_RENDER_FRUITS = 600
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

/** 果型 → 低模几何体（视觉差异化：光球/钻石/花晶/星芒） */
const FRUIT_CORE_GEOMETRIES: Record<string, () => THREE.BufferGeometry> = {
  GLOW: () => new THREE.SphereGeometry(0.052, 14, 12),
  RESONANCE: () => new THREE.OctahedronGeometry(0.062),
  BLOOM: () => new THREE.IcosahedronGeometry(0.062, 0),
  SPARK: () => new THREE.TetrahedronGeometry(0.07),
}

/** 果型 → 同系多色渐变（同型不同色，按果实 id 确定性取色） */
const FRUIT_PALETTES: Record<string, string[]> = {
  GLOW: ['#00d4ff', '#7de3ff', '#00b4e6'],
  RESONANCE: ['#b39ddb', '#9370db', '#8a5fe0'],
  BLOOM: ['#ff8fab', '#ff6b6b', '#ff5a7a'],
  SPARK: ['#ffe98a', '#ffd700', '#ffb347'],
}

const SEASON_LABELS: Record<string, string> = {
  SPRING: '春 · 萌芽',
  SUMMER: '夏 · 繁盛',
  AUTUMN: '秋 · 收获',
  WINTER: '冬 · 静待',
}

/** 实时天气徽章（BUG#46：有定位=当地天气，未定位=北京天气） */
const WEATHER_BADGES: Record<string, string> = {
  SUNNY: '☀️ 晴',
  CLOUDY: '☁️ 多云',
  RAIN: '🌧️ 雨',
  SNOW: '❄️ 雪',
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
  return { minLat, maxLat, minLng, maxLng }
}

// ========== Three.js 场景封装（副作用与 React 解耦） ==========

interface TreeSceneHandle {
  setFruits(fruits: TreeFruit[]): void
  applyTheme(theme: TreeEnvTheme): void
  /** 页面 UI 主题（ocean/sakura）：sakura 时深蓝环境背景/星云换粉色，树本身不变 */
  setUiTheme(mode: 'ocean' | 'sakura'): void
  onViewportChange(callback: (query: TreeFruitsQuery) => void): void
  onFruitClick(callback: (fruit: TreeFruit) => void): void
  dispose(): void
}

interface StarFieldHandle {
  group: THREE.Group
  nearMaterial: THREE.PointsMaterial
}

function createStarfield(scene: THREE.Scene, glowTexture: THREE.CanvasTexture): StarFieldHandle {
  // 机型分档 + 灰度降级开关（Sprint 2.8）：低档/未命中灰度 → 星点减半
  const tier = deviceTier()
  const baseCount = tier === 'LOW' || !isFeatureEnabled('wish_world_tree_enhanced')
    ? 450
    : tier === 'MID'
      ? 700
      : 900

  const group = new THREE.Group()
  const twoPi = Math.PI * 2
  const makeLayer = (count: number, rMin: number, rMax: number, size: number, opacity: number, colored: boolean) => {
    const positions = new Float32Array(count * 3)
    const colors = new Float32Array(count * 3)
    const tints = [new THREE.Color(0xffffff), new THREE.Color(0xbfe3ff), new THREE.Color(0xffe9c9)]
    for (let i = 0; i < count; i++) {
      const r = rMin + Math.random() * (rMax - rMin)
      const u = Math.random() * 2 - 1
      const angle = Math.random() * twoPi
      const s = Math.sqrt(1 - u * u)
      positions[i * 3] = r * s * Math.cos(angle)
      positions[i * 3 + 1] = r * u
      positions[i * 3 + 2] = r * s * Math.sin(angle)
      if (colored) {
        const tint = tints[Math.floor(Math.random() * tints.length)]
        colors[i * 3] = tint.r
        colors[i * 3 + 1] = tint.g
        colors[i * 3 + 2] = tint.b
      }
    }
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    // vertexColors=true 时必须挂 color 属性，否则着色器回退黑色（粉底星空bug根因）
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))
    const material = new THREE.PointsMaterial({
      color: 0xffffff,
      vertexColors: colored,
      map: glowTexture,
      size,
      sizeAttenuation: true,
      transparent: true,
      opacity,
      depthWrite: false,
    })
    const points = new THREE.Points(geometry, material)
    group.add(points)
    return { points, material }
  }

  // 远层：细密小星；近层：稀疏亮星（带色温变化，供闪烁）
  makeLayer(baseCount, 12, 22, 0.05, 0.75, false)
  const near = makeLayer(Math.floor(baseCount * 0.28), 6, 11, 0.09, 0.85, true)
  scene.add(group)
  return { group, nearMaterial: near.material }
}

/** 径向渐变光斑纹理（星云/流星/地面辉光共用） */
function makeRadialGlowTexture(): THREE.CanvasTexture {
  const size = 128
  const canvas2d = document.createElement('canvas')
  canvas2d.width = size
  canvas2d.height = size
  const ctx = canvas2d.getContext('2d')
  if (ctx) {
    const grad = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
    grad.addColorStop(0, 'rgba(255,255,255,1)')
    grad.addColorStop(0.35, 'rgba(255,255,255,0.45)')
    grad.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.fillStyle = grad
    ctx.fillRect(0, 0, size, size)
  }
  return new THREE.CanvasTexture(canvas2d)
}

interface TreeBodyParts {
  canopyMaterial: THREE.MeshBasicMaterial
  coreMaterial: THREE.MeshBasicMaterial
  haloMaterial: THREE.MeshBasicMaterial
  glowLight: THREE.PointLight
  nebulaMaterials: THREE.SpriteMaterial[]
  groundGlowMaterial: THREE.MeshBasicMaterial
  foliageMaterials: THREE.MeshBasicMaterial[]
}

function createTreeBody(scene: THREE.Scene, glowTexture: THREE.CanvasTexture): TreeBodyParts {
  const tier = deviceTier()
  const tree = new THREE.Group()
  scene.add(tree)

  // ===== 树干：有机弯曲主干（分段堆叠 + 树皮顶点扰动）+ 根蔸外扩 =====
  const barkMaterial = new THREE.MeshStandardMaterial({ color: 0x4a3728, roughness: 0.95 })
  const gnarl = (geometry: THREE.BufferGeometry, seed: number) => {
    // 顶点径向扰动：模拟树皮疙瘩的自然不规则
    const pos = geometry.attributes.position as THREE.BufferAttribute
    for (let i = 0; i < pos.count; i++) {
      const x = pos.getX(i)
      const y = pos.getY(i)
      const z = pos.getZ(i)
      const wobble = 0.012 * Math.sin(y * 16 + seed) + 0.006 * Math.sin(x * 21 + seed * 2)
      const len = Math.hypot(x, z)
      if (len > 0.001) {
        pos.setX(i, x + (x / len) * wobble)
        pos.setZ(i, z + (z / len) * wobble)
      }
    }
    geometry.computeVertexNormals()
    return geometry
  }
  const trunkGroup = new THREE.Group()
  tree.add(trunkGroup)
  // 主干：单根连续几何体——环面沿「收径 + 微弯」脊线扫掠，无任何分段接缝
  const trunkHeight = 2.05
  const trunkRings = 26
  const trunkRadial = 16
  const trunkPositions: number[] = []
  const trunkIndices: number[] = []
  for (let r = 0; r <= trunkRings; r++) {
    const t = r / trunkRings
    const y = t * trunkHeight
    const bendX = 0.14 * t * t
    // 半径：顶 0.055 → 底 0.2，基部二次方鼓出（根收过渡自然）
    const radius = 0.055 + (1 - t) * (1 - t) * 0.15
    for (let a = 0; a < trunkRadial; a++) {
      const ang = (a / trunkRadial) * Math.PI * 2
      const nx = Math.cos(ang)
      const nz = Math.sin(ang)
      const wobble = 0.011 * Math.sin(ang * 3 + t * 9) + 0.005 * Math.sin(t * 27 + ang * 2)
      trunkPositions.push(bendX + nx * (radius + wobble), y, nz * (radius + wobble))
    }
  }
  for (let r = 0; r < trunkRings; r++) {
    for (let a = 0; a < trunkRadial; a++) {
      const i0 = r * trunkRadial + a
      const i1 = r * trunkRadial + ((a + 1) % trunkRadial)
      const j0 = (r + 1) * trunkRadial + a
      const j1 = (r + 1) * trunkRadial + ((a + 1) % trunkRadial)
      trunkIndices.push(i0, j0, i1, i1, j0, j1)
    }
  }
  const trunkGeometry = new THREE.BufferGeometry()
  trunkGeometry.setAttribute('position', new THREE.Float32BufferAttribute(trunkPositions, 3))
  trunkGeometry.setIndex(trunkIndices)
  trunkGeometry.computeVertexNormals()
  const trunk = new THREE.Mesh(trunkGeometry, barkMaterial)
  trunk.position.y = -TREE_RADIUS - 1.66
  trunkGroup.add(trunk)
  // ===== 树杈：4 根短枝从主干上部向外上方伸出（剪影在树冠下缘外，不穿入冠内）=====
  const upVec = new THREE.Vector3(0, 1, 0)
  for (let i = 0; i < 4; i++) {
    const angle = (i / 4) * Math.PI * 2 + 0.4
    const from = new THREE.Vector3(
        Math.cos(angle) * 0.075,
        -2.3 - (i % 2) * 0.16,
        Math.sin(angle) * 0.075,
    )
    const to = new THREE.Vector3(
        Math.cos(angle) * 0.98,
        -1.42 - (i % 2) * 0.14,
        Math.sin(angle) * 0.98,
    )
    const dir = to.clone().sub(from)
    const len = dir.length()
    const geo = gnarl(new THREE.CylinderGeometry(0.022, 0.05, len, 8), i * 4 + 3)
    const mesh = new THREE.Mesh(geo, barkMaterial)
    mesh.position.copy(from).add(to).multiplyScalar(0.5)
    mesh.quaternion.setFromUnitVectors(upVec, dir.normalize())
    trunkGroup.add(mesh)
  }
  // 根蔸：5 条近水平外扩的锥形根，扎入草地
  for (let i = 0; i < 5; i++) {
    const angle = (i / 5) * Math.PI * 2 + 0.3
    const root = new THREE.Mesh(
        gnarl(new THREE.CylinderGeometry(0.012, 0.06, 0.45, 6), i * 5 + 2),
        barkMaterial,
    )
    root.position.set(Math.cos(angle) * 0.16, -TREE_RADIUS - 1.52, Math.sin(angle) * 0.16)
    root.rotation.z = Math.cos(angle) * 1.22
    root.rotation.x = -Math.sin(angle) * 1.22
    trunkGroup.add(root)
  }

  // ===== 大地：夜色草地 + 呼吸辉光 =====
  const ground = new THREE.Mesh(
      new THREE.CircleGeometry(2.1, 48),
      new THREE.MeshStandardMaterial({ color: 0x14281e, roughness: 1, transparent: true, opacity: 0.95 }),
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -TREE_RADIUS - 1.66
  tree.add(ground)
  const groundGlowMaterial = new THREE.MeshBasicMaterial({
    color: 0x2a9d8f,
    transparent: true,
    opacity: 0.2,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  })
  const groundGlow = new THREE.Mesh(new THREE.CircleGeometry(2.5, 48), groundGlowMaterial)
  groundGlow.rotation.x = -Math.PI / 2
  groundGlow.position.y = -TREE_RADIUS - 1.655
  tree.add(groundGlow)

  // ===== 草丛：一丛 = 4 片叶从同根向四周散射（像真草的丛生形）=====
  const grassCount = tier === 'LOW' ? 90 : tier === 'MID' ? 140 : 190
  const grassBlades: THREE.BufferGeometry[] = []
  for (let b = 0; b < 4; b++) {
    const blade = new THREE.ConeGeometry(0.02, 0.22 + (b % 2) * 0.09, 5)
    blade.translate(0, 0.12, 0)
    const tilt = 0.34 + b * 0.07
    const yaw = (b / 4) * Math.PI * 2 + 0.35
    const rotationMatrix = new THREE.Matrix4().makeRotationFromEuler(
        new THREE.Euler(tilt, yaw, 0, 'YXZ'),
    )
    blade.applyMatrix4(rotationMatrix)
    grassBlades.push(blade)
  }
  const grassGeometry = mergeGeometries(grassBlades) ?? grassBlades[0]
  grassBlades.forEach((blade) => blade.dispose())
  const grassMaterial = new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 1 })
  const grass = new THREE.InstancedMesh(grassGeometry, grassMaterial, grassCount)
  const grassDummy = new THREE.Object3D()
  const grassColor = new THREE.Color()
  const grassTints = [0x2f7a4d, 0x3b8f5a, 0x276b45, 0x46a06a]
  for (let i = 0; i < grassCount; i++) {
    const r = Math.sqrt(Math.random()) * 2.0
    const a = Math.random() * Math.PI * 2
    grassDummy.position.set(Math.cos(a) * r, -TREE_RADIUS - 1.66, Math.sin(a) * r)
    grassDummy.rotation.set((Math.random() - 0.5) * 0.18, Math.random() * Math.PI, (Math.random() - 0.5) * 0.18)
    grassDummy.scale.setScalar(0.85 + Math.random() * 0.7)
    grassDummy.updateMatrix()
    grass.setMatrixAt(i, grassDummy.matrix)
    grassColor.setHex(grassTints[i % grassTints.length]).multiplyScalar(0.8 + Math.random() * 0.35)
    grass.setColorAt(i, grassColor)
  }
  grass.instanceMatrix.needsUpdate = true
  if (grass.instanceColor) grass.instanceColor.needsUpdate = true
  tree.add(grass)

  // ===== 花丛：5 瓣真花形（花瓣 + 黄色花芯 + 绿色花茎，三个实例网格合成）=====
  const flowerCount = tier === 'LOW' ? 16 : tier === 'MID' ? 24 : 32
  const flowerTints = [0xffb3c8, 0xffe28a, 0xf5f5ff, 0xc9a0ff, 0xffb26b]

  // 花茎（绿色细柱，底端落地）
  const stemGeometry = new THREE.CylinderGeometry(0.006, 0.01, 0.18, 6)
  stemGeometry.translate(0, 0.09, 0)
  const stemMaterial = new THREE.MeshStandardMaterial({ color: 0x3b8f5a, roughness: 1 })
  const stems = new THREE.InstancedMesh(stemGeometry, stemMaterial, flowerCount)

  // 花瓣：扁椭球长轴朝外、微微上翘，5 瓣环绕
  const petalGeometry = new THREE.SphereGeometry(0.038, 10, 8)
  const petalMaterial = new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.6 })
  const petals = new THREE.InstancedMesh(petalGeometry, petalMaterial, flowerCount * 5)

  // 花芯（黄色小球，盖在花瓣中心上方）
  const centerGeometry = new THREE.SphereGeometry(0.02, 8, 6)
  const centerMaterial = new THREE.MeshStandardMaterial({ color: 0xffd700, roughness: 0.5 })
  const centers = new THREE.InstancedMesh(centerGeometry, centerMaterial, flowerCount)

  const flowerColor = new THREE.Color()
  for (let f = 0; f < flowerCount; f++) {
    const r = Math.sqrt(Math.random()) * 1.9
    const baseA = Math.random() * Math.PI * 2
    const baseX = Math.cos(baseA) * r
    const baseZ = Math.sin(baseA) * r
    const baseY = -TREE_RADIUS - 1.66

    grassDummy.position.set(baseX, baseY, baseZ)
    grassDummy.rotation.set(0, 0, 0)
    grassDummy.scale.setScalar(1)
    grassDummy.updateMatrix()
    stems.setMatrixAt(f, grassDummy.matrix)

    flowerColor.setHex(flowerTints[f % flowerTints.length])
    const headY = baseY + 0.19
    for (let pIdx = 0; pIdx < 5; pIdx++) {
      const yaw = baseA + (pIdx / 5) * Math.PI * 2
      grassDummy.position.set(baseX + Math.cos(yaw) * 0.05, headY - 0.008, baseZ + Math.sin(yaw) * 0.05)
      grassDummy.rotation.order = 'YXZ'
      grassDummy.rotation.set(0, -yaw + Math.PI / 2, 0)
      grassDummy.rotation.x = -0.5
      grassDummy.scale.set(1, 0.45, 1.5)
      grassDummy.updateMatrix()
      petals.setMatrixAt(f * 5 + pIdx, grassDummy.matrix)
      petals.setColorAt(f * 5 + pIdx, flowerColor)
    }
    grassDummy.rotation.order = 'XYZ'
    grassDummy.position.set(baseX, headY + 0.012, baseZ)
    grassDummy.rotation.set(0, 0, 0)
    grassDummy.scale.setScalar(1)
    grassDummy.updateMatrix()
    centers.setMatrixAt(f, grassDummy.matrix)
    stems.instanceMatrix.needsUpdate = true
    petals.instanceMatrix.needsUpdate = true
    centers.instanceMatrix.needsUpdate = true
    if (petals.instanceColor) petals.instanceColor.needsUpdate = true
  }
  tree.add(stems)
  tree.add(petals)
  tree.add(centers)

  // ===== 冠层内胆（深色实体挡透视）=====
  const inner = new THREE.Mesh(
      new THREE.SphereGeometry(TREE_RADIUS * 0.96, 48, 48),
      new THREE.MeshBasicMaterial({ color: 0x0a1830, transparent: true, opacity: 0.62 }),
  )
  tree.add(inner)

  // ===== 叶层：两片错位半透明叶球（主题色）+ 加性外晕 =====
  const foliageMaterials: THREE.MeshBasicMaterial[] = []
  const foliageSpecs: Array<[number, number, number, number, number]> = [
    // [半径, x 偏移, y 偏移, z 偏移, 透明度]
    [TREE_RADIUS * 0.9, 0.05, 0.06, -0.04, 0.22],
    [TREE_RADIUS * 0.79, -0.06, -0.04, 0.05, 0.17],
  ]
  for (const [radius, ox, oy, oz, opacity] of foliageSpecs) {
    const material = new THREE.MeshBasicMaterial({
      color: 0x3ddc97,
      transparent: true,
      opacity,
      depthWrite: false,
    })
    foliageMaterials.push(material)
    const mesh = new THREE.Mesh(new THREE.SphereGeometry(radius, 36, 26), material)
    mesh.position.set(ox, oy, oz)
    tree.add(mesh)
  }
  const outerGlow = new THREE.Mesh(
      new THREE.SphereGeometry(TREE_RADIUS * 1.13, 32, 24),
      new THREE.MeshBasicMaterial({
        color: 0x3ddc97,
        transparent: true,
        opacity: 0.055,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
      }),
  )
  tree.add(outerGlow)

  // ===== 叶簇亮点（沿冠层球面散布的小光点，档位定数）=====
  const leafCount = tier === 'LOW' ? 70 : tier === 'MID' ? 110 : 160
  const leafPositions = new Float32Array(leafCount * 3)
  for (let i = 0; i < leafCount; i++) {
    const u = Math.random() * 2 - 1
    const angle = Math.random() * Math.PI * 2
    const s = Math.sqrt(1 - u * u)
    const r = TREE_RADIUS * 1.005
    leafPositions[i * 3] = r * s * Math.cos(angle)
    leafPositions[i * 3 + 1] = r * u
    leafPositions[i * 3 + 2] = r * s * Math.sin(angle)
  }
  const leafGeometry = new THREE.BufferGeometry()
  leafGeometry.setAttribute('position', new THREE.BufferAttribute(leafPositions, 3))
  const leaves = new THREE.Points(leafGeometry, new THREE.PointsMaterial({
    color: 0x86e8ad,
    size: 0.024,
    sizeAttenuation: true,
    transparent: true,
    opacity: 0.55,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  }))
  tree.add(leaves)

  // ===== 冠层线框壳（主题色，applyTheme 更新）=====
  const canopyMaterial = new THREE.MeshBasicMaterial({
    color: 0x3ddc97,
    wireframe: true,
    transparent: true,
    opacity: 0.14,
  })
  tree.add(new THREE.Mesh(new THREE.SphereGeometry(TREE_RADIUS, 40, 28), canopyMaterial))

  // ===== 世界树之心：内核光球 + 光晕 + 呼吸点光源（照亮枝干与地面）=====
  const coreMaterial = new THREE.MeshBasicMaterial({ color: 0xffd700 })
  tree.add(new THREE.Mesh(new THREE.SphereGeometry(0.3, 24, 24), coreMaterial))
  const haloMaterial = new THREE.MeshBasicMaterial({
    color: 0xffd700,
    transparent: true,
    opacity: 0.15,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  })
  tree.add(new THREE.Mesh(new THREE.SphereGeometry(0.44, 24, 24), haloMaterial))
  const glowLight = new THREE.PointLight(0xffd700, 2.4, 9)
  glowLight.position.set(0, 0.15, 0)
  tree.add(glowLight)

  // ===== 星云光斑（3 片远端加性光斑，主题色染色，营造深空氛围）=====
  const nebulaMaterials: THREE.SpriteMaterial[] = []
  const nebulaSpecs: Array<[number, number, number, number]> = [
    // [x, y, z, scale]
    [-15, 7, -26, 34],
    [13, -5, -28, 30],
    [2, 11, -30, 38],
  ]
  for (const [x, y, z, scale] of nebulaSpecs) {
    const material = new THREE.SpriteMaterial({
      map: glowTexture,
      color: 0x1b2a52,
      transparent: true,
      opacity: 0.13,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    })
    const sprite = new THREE.Sprite(material)
    sprite.position.set(x, y, z)
    sprite.scale.setScalar(scale)
    scene.add(sprite)
    nebulaMaterials.push(material)
  }

  return { canopyMaterial, coreMaterial, haloMaterial, glowLight, nebulaMaterials, groundGlowMaterial, foliageMaterials }
}

function createTreeScene(canvas: HTMLCanvasElement): TreeSceneHandle {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))

  const scene = new THREE.Scene()
  const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 60)
  // 默认机位在树冠中线下方稍远处：打开页面即可见「树冠 + 树干 + 草地」完整构图
  camera.position.set(2.8, -1.2, 3.4)

  const controls = new OrbitControls(camera, canvas)
  controls.enableDamping = true
  controls.dampingFactor = 0.06
  controls.enablePan = false
  controls.minDistance = 2.4
  controls.maxDistance = 9
  // 俯仰限制：禁止镜头钻到地面以下（否则花草/树杈从地底仰视会完全穿帮）
  controls.maxPolarAngle = Math.PI * 0.6
  // 灰度降级开关（Sprint 2.8）：未命中时关闭自动旋转（中低端机减负）
  controls.autoRotate = isFeatureEnabled('wish_world_tree_enhanced')
  controls.autoRotateSpeed = 0.3
  // 构图：目标点上移，树占画面主体（消除顶部空白）

  controls.target.set(0, -0.3, 0)

  scene.add(new THREE.AmbientLight(0xffffff, 0.8))
  const directional = new THREE.DirectionalLight(0xffffff, 1.2)
  directional.position.set(3, 5, 2)
  scene.add(directional)

  const glowTexture = makeRadialGlowTexture()
  const starField = createStarfield(scene, glowTexture)
  const treeBody = createTreeBody(scene, glowTexture)

  // 页面 UI 主题（BUG#46 需求延伸）：sakura 时仅环境背景/星云换粉，树本身不变
  let sakuraSky = false
  let lastEnvTheme: TreeEnvTheme | null = null
  const applySkyBackground = (theme: TreeEnvTheme) => {
    if (sakuraSky) {
      canvas.style.background =
          'radial-gradient(circle at 50% 42%, #ffd4e4 0%, #e295b8 34%, #8f4570 68%, #2a0f1c 100%)'
      treeBody.nebulaMaterials.forEach((material, index) =>
          material.color.setStyle(index % 2 === 0 ? '#ff9ec7' : '#ff7eb3'))
    } else {
      canvas.style.background = `radial-gradient(circle at 50% 42%, ${withAlpha(theme.skyColor, 0.5)} 0%, #04070f 78%)`
      treeBody.nebulaMaterials.forEach((material, index) =>
          material.color.setStyle(index % 2 === 0 ? theme.skyColor : theme.crownColor))
    }
  }

  // ===== 流星（每隔 8~17s 划过一枚，1.1s 生命周期）=====
  const shootMaterial = new THREE.SpriteMaterial({
    map: glowTexture,
    color: 0xeaf6ff,
    transparent: true,
    opacity: 0,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    rotation: -0.6,
  })
  const shootSprite = new THREE.Sprite(shootMaterial)
  shootSprite.scale.set(2.6, 0.5, 1)
  shootSprite.visible = false
  scene.add(shootSprite)
  let shootStartAt = 5 + Math.random() * 6
  let shootEndAt = -1
  const shootFrom = new THREE.Vector3()
  const shootVel = new THREE.Vector3()

  // ===== 果实分型：每种果型独立 InstancedMesh（几何体差异化），核心/光晕各共享材质 =====
  const FRUIT_TYPE_KEYS = ['GLOW', 'RESONANCE', 'BLOOM', 'SPARK']
  const coreGeometryByType = FRUIT_TYPE_KEYS.map((t) => FRUIT_CORE_GEOMETRIES[t]?.() ?? new THREE.SphereGeometry(0.05, 12, 10))
  const haloGeometryShared = new THREE.SphereGeometry(0.07, 12, 10)
  const coreMaterialShared = new THREE.MeshBasicMaterial()
  const haloMaterialShared = new THREE.MeshBasicMaterial({
    transparent: true,
    opacity: 0.14,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  })
  const coreMeshByType: THREE.InstancedMesh[] = []
  const haloMeshByType: THREE.InstancedMesh[] = []
  FRUIT_TYPE_KEYS.forEach((_, t) => {
    const core = new THREE.InstancedMesh(coreGeometryByType[t], coreMaterialShared, MAX_RENDER_FRUITS)
    const halo = new THREE.InstancedMesh(haloGeometryShared, haloMaterialShared, MAX_RENDER_FRUITS)
    core.count = 0
    halo.count = 0
    scene.add(core)
    scene.add(halo)
    coreMeshByType.push(core)
    haloMeshByType.push(halo)
  })

  // ===== 状态 =====
  let fruits: TreeFruit[] = []
  /** 果实 → 所属果型 mesh 组内的实例槽位（分型渲染的核心映射） */
  let fruitSlots: Array<{ typeIdx: number; instIdx: number }> = []
  const instancePhases: number[] = []
  let viewportCallback: ((query: TreeFruitsQuery) => void) | null = null
  let fruitClickCallback: ((fruit: TreeFruit) => void) | null = null
  let lastRequestedSpherical = new THREE.Spherical().setFromVector3(camera.position)
  let lastTriggerTime = 0
  let idleTimer: ReturnType<typeof setTimeout> | null = null
  let disposed = false

  const dummy = new THREE.Object3D()
  const fruitColor = new THREE.Color()
  // 第二章：共鸣果实体积/光效随互动量（点亮数）变大——每实例基础缩放 1.0~1.8
  const instanceScales: number[] = []
  const scaleForLight = (lightCount: number | null | undefined) =>
      1 + Math.min(lightCount ?? 0, 50) / 50 * 0.8

  /** 果实 id → 调色板取色索引（确定性，同果恒同色） */
  const colorIndexFor = (fruit: TreeFruit, paletteLen: number) => {
    const key = String(fruit.id)
    let hash = 0
    for (let i = 0; i < key.length; i++) hash = (hash * 31 + key.charCodeAt(i)) | 0
    return Math.abs(hash) % paletteLen
  }

  /** 写入单颗果实的矩阵（自转 + 呼吸缩放，核心/光晕双层） */
  const writeFruitInstance = (index: number, scale: number, elapsed: number) => {
    const fruit = fruits[index]
    const slot = fruitSlots[index]
    if (!fruit || !slot) return
    const base = instanceScales[index] ?? 1
    const phase = instancePhases[index] ?? 0
    const position = toCartesian(fruit.position.theta, fruit.position.phi, TREE_RADIUS)
    dummy.position.copy(position)
    // 分型几何体缓慢自转（球体不转也无妨，统一转更生动）
    dummy.rotation.set(elapsed * 0.45 + phase * 0.3, elapsed * 0.32 + phase, phase * 0.5)
    dummy.scale.setScalar(scale * base)
    dummy.updateMatrix()
    coreMeshByType[slot.typeIdx].setMatrixAt(slot.instIdx, dummy.matrix)
    dummy.scale.setScalar(scale * base * 1.5)
    dummy.updateMatrix()
    haloMeshByType[slot.typeIdx].setMatrixAt(slot.instIdx, dummy.matrix)
  }

  const handleSetFruits = (nextFruits: TreeFruit[]) => {
    fruits = nextFruits.slice(0, MAX_RENDER_FRUITS)
    fruitSlots = []
    instancePhases.length = 0
    instanceScales.length = 0
    const counts = FRUIT_TYPE_KEYS.map(() => 0)
    for (let i = 0; i < fruits.length; i++) {
      const fruit = fruits[i]
      const typeIdx = Math.max(0, FRUIT_TYPE_KEYS.indexOf(fruit.fruitType))
      const instIdx = counts[typeIdx]++
      fruitSlots.push({ typeIdx, instIdx })
      // 同系多色渐变：调色板按 id 确定性取色
      const palette = FRUIT_PALETTES[fruit.fruitType] ?? ['#ffffff']
      fruitColor.set(palette[colorIndexFor(fruit, palette.length)])
      coreMeshByType[typeIdx].setColorAt(instIdx, fruitColor)
      haloMeshByType[typeIdx].setColorAt(instIdx, fruitColor)
      instancePhases.push((Number(fruit.id) % 97) * 0.35)
      instanceScales[i] = scaleForLight(fruit.lightCount)
      writeFruitInstance(i, 1, 0)
    }
    FRUIT_TYPE_KEYS.forEach((_, t) => {
      coreMeshByType[t].count = counts[t]
      haloMeshByType[t].count = counts[t]
      coreMeshByType[t].instanceMatrix.needsUpdate = true
      haloMeshByType[t].instanceMatrix.needsUpdate = true
      if (coreMeshByType[t].instanceColor) coreMeshByType[t].instanceColor!.needsUpdate = true
      if (haloMeshByType[t].instanceColor) haloMeshByType[t].instanceColor!.needsUpdate = true
    })
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
    lastEnvTheme = theme
    treeBody.canopyMaterial.color.setStyle(theme.crownColor)
    treeBody.coreMaterial.color.setStyle(theme.coreColor)
    treeBody.haloMaterial.color.setStyle(theme.coreColor)
    treeBody.glowLight.color.setStyle(theme.coreColor)
    treeBody.groundGlowMaterial.color.setStyle(theme.coreColor)
    treeBody.foliageMaterials.forEach((material) => material.color.setStyle(theme.crownColor))
    applySkyBackground(theme)
    setParticle(theme.particle)
  }

  const handleSetUiTheme = (mode: 'ocean' | 'sakura') => {
    const next = mode === 'sakura'
    if (sakuraSky === next) return
    sakuraSky = next
    // 星云仅在环境主题就绪时随背景重染；未就绪时仅翻标记（applyTheme 首次执行即生效）
    if (lastEnvTheme) {
      applySkyBackground(lastEnvTheme)
    } else if (sakuraSky) {
      canvas.style.background =
          'radial-gradient(circle at 50% 42%, #ffd4e4 0%, #e295b8 34%, #8f4570 68%, #2a0f1c 100%)'
    }
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

    // 果实呼吸脉动 + 缓慢自转（基准为互动量缩放）
    if (fruits.length > 0) {
      for (let i = 0; i < fruits.length; i++) {
        writeFruitInstance(i, (instanceScales[i] ?? 1) + 0.12 * Math.sin(elapsed * 2 + instancePhases[i]), elapsed)
      }
      FRUIT_TYPE_KEYS.forEach((_, t) => {
        coreMeshByType[t].instanceMatrix.needsUpdate = true
        haloMeshByType[t].instanceMatrix.needsUpdate = true
      })
    }

    // 双星层差速自转 + 近层亮星闪烁（纯氛围，不参与视口计算）
    starField.group.rotation.y = elapsed * 0.005
    starField.nearMaterial.opacity = 0.6 + 0.28 * Math.sin(elapsed * 1.7)

    // 树心呼吸光 + 地面辉光呼吸（呼应树心脉动）
    treeBody.glowLight.intensity = 2.2 + 0.5 * Math.sin(elapsed * 1.5)
    treeBody.groundGlowMaterial.opacity = 0.15 + 0.07 * (0.5 + 0.5 * Math.sin(elapsed * 1.2))

    // 流星：间歇划过（1.1s 生命周期，透明度渐入渐出）
    if (shootEndAt < 0 && elapsed > shootStartAt) {
      shootEndAt = elapsed + 1.1
      shootFrom.set((Math.random() * 2 - 1) * 8, 4 + Math.random() * 4, -12 - Math.random() * 6)
      shootVel.set(-6 - Math.random() * 3, -2.5 - Math.random() * 1.5, 0)
      shootSprite.position.copy(shootFrom)
      shootSprite.visible = true
    }
    if (shootEndAt > 0 && elapsed <= shootEndAt) {
      const lifeT = 1 - (shootEndAt - elapsed) / 1.1
      const tSec = elapsed - (shootEndAt - 1.1)
      shootSprite.position.set(shootFrom.x + shootVel.x * tSec, shootFrom.y + shootVel.y * tSec, shootFrom.z)
      shootMaterial.opacity = Math.sin(lifeT * Math.PI) * 0.85
    } else if (shootEndAt > 0 && elapsed > shootEndAt) {
      shootSprite.visible = false
      shootMaterial.opacity = 0
      shootEndAt = -1
      shootStartAt = elapsed + 8 + Math.random() * 9
    }

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
    setUiTheme: handleSetUiTheme,
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
      coreMeshByType.forEach((mesh) => mesh.dispose())
      haloMeshByType.forEach((mesh) => mesh.dispose())
      coreGeometryByType.forEach((geometry) => geometry.dispose())
      haloGeometryShared.dispose()
      coreMaterialShared.dispose()
      haloMaterialShared.dispose()
      starField.group.children.forEach((child) => {
        const points = child as THREE.Points
        points.geometry.dispose()
        ;(points.material as THREE.PointsMaterial).dispose()
      })
      shootMaterial.map?.dispose()
      shootMaterial.dispose()
      glowTexture.dispose()
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
  const fruitsMapRef = useRef<Map<number | string, TreeFruit>>(new Map())
  const [aggregation, setAggregation] = useState<WorldTreeAggregation | null>(null)
  const [envSnapshot, setEnvSnapshot] = useState<TreeEnvSnapshot | null>(null)
  /** 页面 UI 主题（sakura 时 3D 环境背景换粉） */
  const themeMode = useThemeStore((state) => state.mode)
  /** 用户定位（BUG#46：天气优先按用户定位，拒绝/失败为 null → 快照回退北京） */
  const [userPos, setUserPos] = useState<{ lat: number; lng: number } | null>(null)
  const [envConfigs, setEnvConfigs] = useState<EnvConfigItem[]>([])
  const [loading, setLoading] = useState(true)
  const [viewportLoading, setViewportLoading] = useState(false)
  const [selectedFruit, setSelectedFruit] = useState<TreeFruit | null>(null)
  const [flagsReady, setFlagsReady] = useState(false)
  // Sprint 2.1 验收：手动强制 2D/3D 切换（2D = 果实列表瀑布，低端机/无 WebGL 可用）
  const [viewMode, setViewMode] = useState<'3D' | '2D'>('3D')
  const [fruitList, setFruitList] = useState<TreeFruit[]>([])

  /** 环境主题（displayEnv 仲裁；快照与配置均失败时保持 null 不覆盖既有视觉） */
  const envTheme = useMemo(
      () => (envSnapshot || envConfigs.length > 0 ? resolveTreeEnvTheme(envSnapshot, envConfigs) : null),
      [envSnapshot, envConfigs],
  )

  /** 快照替换：后端返回的是树上全量封顶列表（含新旧更替），整表替换使离树果实同步消失 */
  const replaceFruits = useCallback((items: TreeFruit[]) => {
    fruitsMapRef.current = new Map(items.map((item) => [item.id, item]))
    sceneRef.current?.setFruits(items)
    setFruitList(items)
  }, [])

  /** 拉取树上果实快照（后端容量封顶单页全量，cursor/bounds 参数已不被服务端消费） */
  const loadFruitsSnapshot = useCallback(
      async () => {
        setViewportLoading(true)
        try {
          const res = await listTreeFruits({ pageSize: 100 })
          if (res.data.success) {
            replaceFruits(res.data.data)
          }
        } catch {
          // 动态加载失败静默降级（已渲染果实不受影响）
        } finally {
          setViewportLoading(false)
        }
      },
      [replaceFruits],
  )

  // 预取灰度开关：场景创建以 flags 就绪为门控（机型分档 + 降级开关同步读取）
  useEffect(() => {
    fetchFeatureFlags().then(() => setFlagsReady(true))
  }, [])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !flagsReady) return
    const scene = createTreeScene(canvas)
    sceneRef.current = scene
    // 首屏数据可能先于场景创建到达（fetchInitial 不受 flagsReady 门控），
    // 此时 replaceFruits 的 sceneRef 为 null 静默跳过，这里创建后立即补挂
    if (fruitsMapRef.current.size > 0) {
      scene.setFruits(Array.from(fruitsMapRef.current.values()))
    }
    // 相机转动触发重新拉取快照：果实集已容量封顶，重复拉取为幂等快照替换
    scene.onViewportChange(() => {
      loadFruitsSnapshot()
    })
    scene.onFruitClick((fruit) => {
      setSelectedFruit(fruit)
    })
    return () => {
      scene.dispose()
      sceneRef.current = null
    }
  }, [flagsReady, loadFruitsSnapshot])

  useEffect(() => {
    if (envTheme) sceneRef.current?.applyTheme(envTheme)
  }, [envTheme])

  /** UI 主题切换：sakura → 3D 环境背景/星云换粉（树不变）；场景就绪后立即应用 */
  useEffect(() => {
    if (flagsReady && sceneRef.current) {
      sceneRef.current.setUiTheme(themeMode)
    }
  }, [themeMode, flagsReady])

  useEffect(() => {
    const fetchInitial = async () => {
      try {
        const [treeRes, fruitsRes, envRes, configsRes] = await Promise.all([
          getWorldTree(),
          listTreeFruits({ pageSize: 100 }),
          getTreeEnv(undefined, userPos?.lat, userPos?.lng),
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
          replaceFruits(fruitsRes.data.data)
        }
      } catch {
        // 错误已由 request 拦截器处理
      } finally {
        setLoading(false)
      }
    }
    fetchInitial()
  }, [replaceFruits])

  /** 用户定位（BUG#46）：授权成功 → 带坐标刷新环境快照（weather=当地实时天气）；
   *  拒绝/失败 → userPos 为 null，快照保持全站天气（北京），与地图兜底语义一致 */
  useEffect(() => {
    if (!navigator.geolocation) return
    navigator.geolocation.getCurrentPosition(
        (pos) => setUserPos({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
        () => undefined,
        { timeout: 8000 },
    )
  }, [])

  /** 定位就绪后立即刷新环境快照（不等轮询周期） */
  useEffect(() => {
    if (!userPos) return
    getTreeEnv(undefined, userPos.lat, userPos.lng)
        .then((res) => {
          if (res.data.success && res.data.data) setEnvSnapshot(res.data.data)
        })
        .catch(() => undefined)
  }, [userPos])

  /** 环境快照轮询：特殊事件全站同步 + 情绪环境 5 分钟扫描，1 分钟拉取足够实时 */
  useEffect(() => {
    const timer = setInterval(async () => {
      try {
        const res = await getTreeEnv(undefined, userPos?.lat, userPos?.lng)
        if (res.data.success && res.data.data) setEnvSnapshot(res.data.data)
      } catch {
        // 轮询失败静默降级，保留上一轮环境
      }
    }, TREE_ENV_POLL_MS)
    return () => clearInterval(timer)
  }, [userPos])

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
              <Tag
                  color="geekblue"
                  className={styles.tag}
                  title={userPos ? '定位城市的实时天气' : '未定位，显示北京天气（授权定位后切换当地）'}
              >
                {WEATHER_BADGES[envSnapshot.weather] ?? envSnapshot.weather}
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

        {/* 3D 画布（2D 降级开关：Sprint 2.1 验收） */}
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 12 }}>
          <Segmented
              value={viewMode}
              onChange={(v) => setViewMode(v as '3D' | '2D')}
              options={[
                { value: '3D', label: '3D 星空' },
                { value: '2D', label: '2D 列表（降级）' },
              ]}
          />
        </div>
        <div className={styles.canvasWrap} style={viewMode === '2D' ? { display: 'none' } : undefined}>
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
                  <span className={styles.fruitAuthor} style={{ cursor: 'pointer' }} onClick={(e) => { e.stopPropagation(); history.push(`/user/${selectedFruit.authorId}`) }}>{selectedFruit.authorNickname}</span>
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
        {viewMode === '2D' && (
            <div className={styles.canvasWrap} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 12 }}>
              {fruitList.length === 0 ? (
                  <div className={styles.hint}>暂无心愿果实</div>
              ) : (
                  fruitList.map((fruit) => (
                      <Card
                          key={fruit.id}
                          size="small"
                          hoverable
                          onClick={() => history.push(`/wish/${fruit.id}`)}
                          title={fruit.title}
                          extra={<Tag color="cyan">{FRUIT_LABELS[fruit.fruitType] ?? fruit.fruitType}</Tag>}
                      >
                        <div className={styles.fruitAuthor} style={{ cursor: 'pointer' }} onClick={(e) => { e.stopPropagation(); history.push(`/user/${fruit.authorId}`) }}>{fruit.authorNickname}</div>
                        <span className={styles.fruitLight}>✦ {formatCount(fruit.lightCount)} 点亮</span>
                      </Card>
                  ))
              )}
            </div>
        )}
        <WishBGM />
      </div>
  )
}
