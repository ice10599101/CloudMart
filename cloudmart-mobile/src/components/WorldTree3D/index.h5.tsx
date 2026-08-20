import { useEffect, useRef } from 'react'
import { View } from '@tarojs/components'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { TreeEnvironment, TreeFruit, TreeFruitsQuery, TreeSeason } from '@/types'
import styles from './index.module.scss'

export interface WorldTree3DProps {
  fruits: TreeFruit[]
  season: TreeSeason | null
  environment: TreeEnvironment | null
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

const SEASON_CANOPY_COLORS: Record<string, number> = {
  SPRING: 0x7ef0c0,
  SUMMER: 0x3ddc97,
  AUTUMN: 0xffb347,
  WINTER: 0xbfe8ff,
}

const ENVIRONMENT_CORE_COLORS: Record<string, number> = {
  SUNNY: 0xffd700,
  RAIN: 0x4facfe,
  RAINBOW: 0xff9ff3,
}

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
  season,
  environment,
  onFruitSelect,
  onViewportChange,
}: WorldTree3DProps) {
  const hostRef = useRef<HTMLElement | null>(null)
  const sceneRef = useRef<{
    setFruits: (items: TreeFruit[]) => void
    applyTheme: (seasonKey: string, envKey: string) => void
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
    const innerMaterial = new THREE.MeshBasicMaterial({
      color: 0x0f3460,
      transparent: true,
      opacity: 0.55,
    })
    scene.add(new THREE.Mesh(new THREE.SphereGeometry(TREE_RADIUS * 0.96, 40, 40), innerMaterial))
    const canopyMaterial = new THREE.MeshBasicMaterial({
      color: SEASON_CANOPY_COLORS.SUMMER,
      wireframe: true,
      transparent: true,
      opacity: 0.18,
    })
    scene.add(new THREE.Mesh(new THREE.SphereGeometry(TREE_RADIUS, 32, 20), canopyMaterial))
    const coreMaterial = new THREE.MeshBasicMaterial({ color: ENVIRONMENT_CORE_COLORS.SUNNY })
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
      applyTheme: (seasonKey, envKey) => {
        canopyMaterial.color.setHex(SEASON_CANOPY_COLORS[seasonKey] ?? 0x3ddc97)
        coreMaterial.color.setHex(ENVIRONMENT_CORE_COLORS[envKey] ?? 0xffd700)
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
      const elapsed = clock.getElapsedTime()
      controls.update()

      for (let i = 0; i < currentFruits.length; i++) {
        writeInstance(i, 1 + 0.1 * Math.sin(elapsed * 2 + phases[i]))
      }
      coreMesh.instanceMatrix.needsUpdate = true
      haloMesh.instanceMatrix.needsUpdate = true
      stars.rotation.y = elapsed * 0.005

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
    sceneRef.current?.applyTheme(season ?? '', environment ?? '')
  }, [season, environment])

  return <View ref={hostRef as never} className={styles.canvasHost} aria-label='世界树 3D 场景' />
}
