import { useEffect, useRef } from 'react'
import { View } from '@tarojs/components'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { TreeEnvParticle, TreeFruit, TreeFruitsQuery } from '@/types'
import { DEFAULT_TREE_ENV_THEME, type TreeEnvTheme } from '@/utils/tree-env'
import styles from './index.module.scss'

export interface WorldTree3DProps {
  fruits: TreeFruit[]
  /** 动态环境主题（Sprint 2.2；null 时组件回退默认视觉） */
  theme: TreeEnvTheme | null
  onFruitSelect: (fruit: TreeFruit) => void
  /** H5 3D 专属：相机视口变化（弧度制 bounds，触发增量拉取） */
  onViewportChange?: (query: TreeFruitsQuery) => void
}

const TREE_RADIUS = 1.6
const MAX_RENDER_FRUITS = 400
const VIEWPORT_TRIGGER_ANGLE = 0.6
const VIEWPORT_THROTTLE_MS = 1500
const AUTO_ROTATE_RESUME_MS = 4000

const FRUIT_COLORS: Record<string, number> = {
  GLOW: 0x00d4ff,
  RESONANCE: 0x9370db,
  BLOOM: 0xff6b6b,
  SPARK: 0xffd700,
}

/**
 * 环境粒子运动规格（Sprint 2.2，按 wish_env_config.visual.particle 驱动）：
 * vy 负=下落正=上升；sway 为水平摆动幅度；still 仅慢速旋转（星辰）。
 * 移动端粒子量按 WEB 端 6 折裁剪（帧率优先）。
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
  RAIN: { count: 260, color: 0x9fd8ff, size: 0.03, vy: -3.2, vx: 0, sway: 0, still: false },
  SNOWFLAKE: { count: 160, color: 0xffffff, size: 0.05, vy: -0.55, vx: 0, sway: 0.3, still: false },
  PETAL: { count: 110, color: 0xffb7d5, size: 0.07, vy: -0.5, vx: 0, sway: 0.55, still: false },
  LEAF: { count: 110, color: 0xffb347, size: 0.07, vy: -0.65, vx: 0, sway: 0.45, still: false },
  SUNBURST: { count: 100, color: 0xffd700, size: 0.045, vy: 0.4, vx: 0, sway: 0.2, still: false },
  METEOR: { count: 70, color: 0xffffff, size: 0.06, vy: -5.0, vx: 2.4, sway: 0, still: false },
  AURORA: { count: 160, color: 0x7ef0c0, size: 0.05, vy: 0.3, vx: 0, sway: 1.0, still: false },
  STAR: { count: 150, color: 0xfff2b2, size: 0.04, vy: 0, vx: 0, sway: 0, still: true },
}

/** 粒子活动包围盒（覆盖树冠球 + 上下留空） */
const PARTICLE_BOX = { x: 2.6, yMin: -1.6, yMax: 2.8 }

function toCartesian(theta: number, phi: number, radius: number): THREE.Vector3 {
  const sinPhi = Math.sin(phi)
  return new THREE.Vector3(
      radius * sinPhi * Math.cos(theta),
      radius * Math.cos(phi),
      radius * sinPhi * Math.sin(theta),
  )
}

/** 与后端 bounds 契约对齐（lat→phi、lng→theta；跨 0/2π 时 minLng > maxLng 触发环绕窗口语义） */
function computeViewportBounds(camera: THREE.PerspectiveCamera): TreeFruitsQuery {
  const distance = camera.position.length()
  const phi = Math.acos(THREE.MathUtils.clamp(camera.position.y / distance, -1, 1))
  const theta = (Math.atan2(camera.position.z, camera.position.x) + Math.PI * 2) % (Math.PI * 2)
  const span = 1.25
  const twoPi = Math.PI * 2
  return {
    minLat: Math.max(0, phi - span),
    maxLat: Math.min(Math.PI, phi + span),
    minLng: (theta - span + twoPi) % twoPi,
    maxLng: (theta + span) % twoPi,
    pageSize: 200,
  }
}

/**
 * 世界树 3D 场景（H5 端 three.js 版）：
 * Taro H5 为 DOM 环境，直接挂载 canvas + WebGLRenderer；
 * 小程序端由 index.tsx 降级为伪 3D 星图（Taro 多端文件后缀机制自动切换）。
 */
export default function WorldTree3D({
                                      fruits,
                                      theme,
                                      onFruitSelect,
                                      onViewportChange,
                                    }: WorldTree3DProps) {
  const hostRef = useRef<HTMLElement | null>(null)
  const sceneRef = useRef<{
    setFruits: (items: TreeFruit[]) => void
    applyTheme: (next: TreeEnvTheme) => void
  } | null>(null)
  const viewportCallbackRef = useRef(onViewportChange)
  const fruitSelectRef = useRef(onFruitSelect)

  viewportCallbackRef.current = onViewportChange
  fruitSelectRef.current = onFruitSelect

  useEffect(() => {
    const host = hostRef.current
    if (!host) return

    const canvas = document.createElement('canvas')
    canvas.style.width = '100%'
    canvas.style.height = '100%'
    canvas.style.display = 'block'
    canvas.style.touchAction = 'none'
    host.appendChild(canvas)

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

    // 星空
    const starCount = 600
    const starPositions = new Float32Array(starCount * 3)
    for (let i = 0; i < starCount; i++) {
      const r = 10 + Math.random() * 12
      const u = Math.random() * 2 - 1
      const angle = Math.random() * Math.PI * 2
      const s = Math.sqrt(1 - u * u)
      starPositions[i * 3] = r * s * Math.cos(angle)
      starPositions[i * 3 + 1] = r * u
      starPositions[i * 3 + 2] = r * s * Math.sin(angle)
    }
    const starGeometry = new THREE.BufferGeometry()
    starGeometry.setAttribute('position', new THREE.BufferAttribute(starPositions, 3))
    const starMaterial = new THREE.PointsMaterial({
      color: 0xffffff,
      size: 0.05,
      transparent: true,
      opacity: 0.8,
      depthWrite: false,
    })
    const stars = new THREE.Points(starGeometry, starMaterial)
    scene.add(stars)

    // 树体（内球 + 线框树冠 + 核心光球）
    // 树干 + 底座（与 WEB 端 WorldTree3D 对齐，走查反馈 H5 缺失）
    const trunkGeometry = new THREE.CylinderGeometry(0.1, 0.22, 1.3, 10)
    const trunkMaterial = new THREE.MeshStandardMaterial({ color: 0x4a3728, roughness: 0.9 })
    const trunk = new THREE.Mesh(trunkGeometry, trunkMaterial)
    trunk.position.y = -TREE_RADIUS - 0.65
    scene.add(trunk)
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

    const innerMaterial = new THREE.MeshBasicMaterial({
      color: 0x0f3460,
      transparent: true,
      opacity: 0.55,
    })
    scene.add(new THREE.Mesh(new THREE.SphereGeometry(TREE_RADIUS * 0.96, 40, 40), innerMaterial))
    const canopyMaterial = new THREE.MeshBasicMaterial({
      color: DEFAULT_TREE_ENV_THEME.crownColor,
      wireframe: true,
      transparent: true,
      opacity: 0.18,
    })
    scene.add(new THREE.Mesh(new THREE.SphereGeometry(TREE_RADIUS, 32, 20), canopyMaterial))
    const coreMaterial = new THREE.MeshBasicMaterial({ color: DEFAULT_TREE_ENV_THEME.coreColor })
    scene.add(new THREE.Mesh(new THREE.SphereGeometry(0.3, 20, 20), coreMaterial))

    // 果实双层 InstancedMesh
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

    let currentFruits: TreeFruit[] = []
    const phases: number[] = []
    const dummy = new THREE.Object3D()
    const fruitColor = new THREE.Color()

    const writeInstance = (index: number, scale: number) => {
      const fruit = currentFruits[index]
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

    sceneRef.current = {
      setFruits: (items) => {
        currentFruits = items.slice(0, MAX_RENDER_FRUITS)
        phases.length = 0
        coreMesh.count = currentFruits.length
        haloMesh.count = currentFruits.length
        for (let i = 0; i < currentFruits.length; i++) {
          fruitColor.setHex(FRUIT_COLORS[currentFruits[i].fruitType] ?? 0xffffff)
          coreMesh.setColorAt(i, fruitColor)
          haloMesh.setColorAt(i, fruitColor)
          phases.push((currentFruits[i].id % 97) * 0.35)
          writeInstance(i, 1)
        }
        coreMesh.instanceMatrix.needsUpdate = true
        haloMesh.instanceMatrix.needsUpdate = true
        if (coreMesh.instanceColor) coreMesh.instanceColor.needsUpdate = true
        if (haloMesh.instanceColor) haloMesh.instanceColor.needsUpdate = true
      },
      applyTheme: (next) => {
        canopyMaterial.color.setStyle(next.crownColor)
        coreMaterial.color.setStyle(next.coreColor)
        setParticle(next.particle)
      },
    }

    // 尺寸自适应
    const resize = () => {
      const width = host.clientWidth
      const height = host.clientHeight
      if (width === 0 || height === 0) return
      renderer.setSize(width, height, false)
      camera.aspect = width / height
      camera.updateProjectionMatrix()
    }
    const resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(host)
    resize()

    // 点击拾取（位移 < 6px 视为点击）：屏幕空间最近邻 + 背面剔除，
    // raycaster 对 0.045 半径果实命中区仅约 2-3px 几乎不可点中
    const CLICK_RADIUS_PX = 28
    const screenPos = new THREE.Vector3()
    let downX = 0
    let downY = 0
    const handlePointerDown = (event: PointerEvent) => {
      downX = event.clientX
      downY = event.clientY
    }
    const handlePointerUp = (event: PointerEvent) => {
      if (Math.abs(event.clientX - downX) > 6 || Math.abs(event.clientY - downY) > 6) return
      const rect = canvas.getBoundingClientRect()
      const clickX = event.clientX - rect.left
      const clickY = event.clientY - rect.top
      const frontThreshold = TREE_RADIUS * TREE_RADIUS
      let bestIndex = -1
      let bestDist = CLICK_RADIUS_PX
      for (let i = 0; i < currentFruits.length; i++) {
        const fruit = currentFruits[i]
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
      if (bestIndex >= 0 && currentFruits[bestIndex]) {
        fruitSelectRef.current(currentFruits[bestIndex])
      }
    }
    canvas.addEventListener('pointerdown', handlePointerDown)
    canvas.addEventListener('pointerup', handlePointerUp)

    // 交互暂停自动旋转
    let idleTimer: ReturnType<typeof setTimeout> | null = null
    let disposed = false
    const pauseAutoRotate = () => {
      controls.autoRotate = false
      if (idleTimer) clearTimeout(idleTimer)
      idleTimer = setTimeout(() => {
        if (!disposed) controls.autoRotate = true
      }, AUTO_ROTATE_RESUME_MS)
    }
    controls.addEventListener('start', pauseAutoRotate)
    controls.addEventListener('end', pauseAutoRotate)

    // 渲染循环 + 视口变化检测
    const clock = new THREE.Clock()
    const currentSpherical = new THREE.Spherical()
    let lastRequestedSpherical = new THREE.Spherical().setFromVector3(camera.position)
    let lastTriggerTime = 0
    const renderLoop = () => {
      if (disposed) return
      requestAnimationFrame(renderLoop)
      const dt = clock.getDelta()
      const elapsed = clock.elapsedTime
      controls.update()

      for (let i = 0; i < currentFruits.length; i++) {
        writeInstance(i, 1 + 0.1 * Math.sin(elapsed * 2 + phases[i]))
      }
      coreMesh.instanceMatrix.needsUpdate = true
      haloMesh.instanceMatrix.needsUpdate = true
      stars.rotation.y = elapsed * 0.005

      // 环境粒子：运动型按速度推进并越界回绕；静止型（星辰）整体慢旋
      if (particlePoints && particleMotion && !particleMotion.still) {
        const positions = particlePoints.geometry.attributes.position as THREE.BufferAttribute
        for (let i = 0; i < positions.count; i++) {
          const y = positions.getY(i) + particleMotion.vy * dt
          if (y < PARTICLE_BOX.yMin || y > PARTICLE_BOX.yMax) {
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

      currentSpherical.setFromVector3(camera.position)
      const deltaTheta = Math.abs(currentSpherical.theta - lastRequestedSpherical.theta)
      const deltaPhi = Math.abs(currentSpherical.phi - lastRequestedSpherical.phi)
      const now = performance.now()
      const angleMoved = Math.min(deltaTheta, Math.PI * 2 - deltaTheta) + deltaPhi
      if (
          viewportCallbackRef.current &&
          angleMoved > VIEWPORT_TRIGGER_ANGLE &&
          now - lastTriggerTime > VIEWPORT_THROTTLE_MS
      ) {
        lastTriggerTime = now
        lastRequestedSpherical = currentSpherical.clone()
        viewportCallbackRef.current(computeViewportBounds(camera))
      }

      renderer.render(scene, camera)
    }
    requestAnimationFrame(renderLoop)

    return () => {
      disposed = true
      if (idleTimer) clearTimeout(idleTimer)
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
      starGeometry.dispose()
      starMaterial.dispose()
      scene.traverse((object) => {
        if (object instanceof THREE.Mesh) {
          object.geometry.dispose()
          const material = object.material
          if (Array.isArray(material)) material.forEach((m) => m.dispose())
          else material.dispose()
        }
      })
      renderer.dispose()
      if (canvas.parentElement === host) host.removeChild(canvas)
      sceneRef.current = null
    }
  }, [])

  useEffect(() => {
    sceneRef.current?.setFruits(fruits)
  }, [fruits])

  useEffect(() => {
    sceneRef.current?.applyTheme(theme ?? DEFAULT_TREE_ENV_THEME)
  }, [theme])

  return <View ref={hostRef as never} className={styles.canvasHost} aria-label='世界树 3D 场景' />
}
