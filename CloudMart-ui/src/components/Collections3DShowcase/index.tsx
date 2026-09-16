import { useEffect, useRef } from 'react'
import * as THREE from 'three'
import type { CollectionGroup } from '@/api/wish'

/**
 * 收藏馆 3D 展示厅（Sprint 3.6 验收，四AB WEB P0-5）：
 * Three.js 展示收藏资产（图标平面 + 呼吸浮动 + 缓慢自转 + 中央光核 + 彩色流星雨背景），
 * ≤1024px 视口自动降级隐藏（由父级 matchMedia 控制，降级为分组列表）。
 */

interface ShowcaseItem {
    key: string
    name: string
    icon: string
}

interface Meteor {
    group: THREE.Group
    mesh: THREE.Mesh
    head: THREE.Sprite
    velocity: THREE.Vector3
    speed: number
    baseOpacity: number
    phase: number
}

const ACCENT = 0xffd97a

// 流星颜色盘：金/珊瑚红/青/紫/绿/橙/粉/蓝/白
const METEOR_COLORS = [
    0xffd700, 0xff6b6b, 0x00e5ff, 0x9c6cff, 0x00e676,
    0xff9100, 0xff5edb, 0x7cb8ff, 0xffffff,
]

const METEOR_COUNT = 16
const BOUND_X = 5.5
const BOUND_Y = 3.2

function makeIconTexture(icon: string): THREE.Texture {
    const size = 128
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')
    if (ctx) {
        // 圆盘底 + 金色描边，符号居中
        ctx.fillStyle = 'rgba(11, 16, 38, 0.88)'
        ctx.beginPath()
        ctx.arc(size / 2, size / 2, size / 2 - 4, 0, Math.PI * 2)
        ctx.fill()
        ctx.strokeStyle = 'rgba(255, 217, 122, 0.8)'
        ctx.lineWidth = 3
        ctx.stroke()
        ctx.font = `${size * 0.52}px "PingFang SC", "Segoe UI Emoji", sans-serif`
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'
        ctx.fillText(icon || '✦', size / 2, size / 2 + 2)
    }
    const texture = new THREE.CanvasTexture(canvas)
    texture.colorSpace = THREE.SRGBColorSpace
    return texture
}

/** 白色径向光晕纹理（边缘透明），通过 material.color 上色，供光核/图标/流星头复用 */
function makeGlowTexture(): THREE.Texture {
    const size = 256
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')
    if (ctx) {
        const g = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
        g.addColorStop(0, 'rgba(255, 255, 255, 1)')
        g.addColorStop(0.3, 'rgba(255, 255, 255, 0.5)')
        g.addColorStop(1, 'rgba(255, 255, 255, 0)')
        ctx.fillStyle = g
        ctx.fillRect(0, 0, size, size)
    }
    const texture = new THREE.CanvasTexture(canvas)
    texture.colorSpace = THREE.SRGBColorSpace
    return texture
}

/** 流星拖尾纹理：横向由左（透明长尾）到右（明亮头部）渐变，纵向中间亮两头透明 */
function makeStreakTexture(): THREE.Texture {
    const w = 256
    const h = 64
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    if (ctx) {
        const hg = ctx.createLinearGradient(0, 0, w, 0)
        hg.addColorStop(0, 'rgba(255, 255, 255, 0)')
        hg.addColorStop(1, 'rgba(255, 255, 255, 1)')
        ctx.fillStyle = hg
        ctx.fillRect(0, 0, w, h)
        ctx.globalCompositeOperation = 'destination-in'
        const vg = ctx.createLinearGradient(0, 0, 0, h)
        vg.addColorStop(0, 'rgba(0, 0, 0, 0)')
        vg.addColorStop(0.5, 'rgba(0, 0, 0, 1)')
        vg.addColorStop(1, 'rgba(0, 0, 0, 0)')
        ctx.fillStyle = vg
        ctx.fillRect(0, 0, w, h)
    }
    const texture = new THREE.CanvasTexture(canvas)
    texture.colorSpace = THREE.SRGBColorSpace
    return texture
}

function spawnMeteor(streakTexture: THREE.Texture, glowTexture: THREE.Texture, index: number): Meteor {
    const color = METEOR_COLORS[index % METEOR_COLORS.length]
    // 速度越快拖尾越长，形成流动的层次感
    const speed = 2.5 + Math.random() * 4
    const trailLength = 0.5 + speed * 0.28
    const angle = Math.random() * Math.PI * 2
    const baseOpacity = 0.55 + Math.random() * 0.4

    const group = new THREE.Group()
    group.position.set(
        (Math.random() - 0.5) * 2 * BOUND_X,
        (Math.random() - 0.5) * 2 * BOUND_Y,
        -1.5 + Math.random() * 3,
    )
    group.rotation.z = angle

    const mesh = new THREE.Mesh(
        new THREE.PlaneGeometry(trailLength, 0.14),
        new THREE.MeshBasicMaterial({
            map: streakTexture,
            color,
            transparent: true,
            opacity: baseOpacity,
            blending: THREE.AdditiveBlending,
            depthWrite: false,
            side: THREE.DoubleSide,
        }),
    )
    group.add(mesh)

    const head = new THREE.Sprite(
        new THREE.SpriteMaterial({
            map: glowTexture,
            color,
            transparent: true,
            opacity: baseOpacity,
            blending: THREE.AdditiveBlending,
            depthWrite: false,
        }),
    )
    head.position.set(trailLength / 2, 0, 0)
    head.scale.set(0.3, 0.3, 1)
    group.add(head)

    return {
        group,
        mesh,
        head,
        velocity: new THREE.Vector3(Math.cos(angle), Math.sin(angle), 0),
        speed,
        baseOpacity,
        phase: index * 1.7,
    }
}

export default function Collections3DShowcase({ groups }: { groups: CollectionGroup }) {
    const mountRef = useRef<HTMLDivElement>(null)

    useEffect(() => {
        const mount = mountRef.current
        if (!mount) return

        // 收集展示项（每分组最多 6 个，含图标的优先）
        const items: ShowcaseItem[] = []
        for (const [type, list] of Object.entries(groups)) {
            for (const item of list.slice(0, 6)) {
                items.push({ key: `${type}-${item.id}`, name: item.name, icon: item.icon || '✦' })
            }
        }
        if (items.length === 0) return

        const width = mount.clientWidth || 600
        const height = 320

        const scene = new THREE.Scene()
        const camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 100)
        camera.position.set(0, 0, 7)

        const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
        renderer.setSize(width, height)
        renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
        mount.appendChild(renderer.domElement)

        const glowTexture = makeGlowTexture()
        const streakTexture = makeStreakTexture()

        // 环形排布悬浮图标平面
        const group = new THREE.Group()
        const radius = Math.min(3.2, 1.4 + items.length * 0.24)
        const planes: THREE.Mesh[] = []
        const glowSprites: THREE.Sprite[] = []
        items.forEach((item, i) => {
            const angle = (i / items.length) * Math.PI * 2
            const mesh = new THREE.Mesh(
                new THREE.PlaneGeometry(1.15, 1.15),
                new THREE.MeshBasicMaterial({
                    map: makeIconTexture(item.icon),
                    transparent: true,
                    side: THREE.DoubleSide,
                }),
            )
            mesh.position.set(Math.cos(angle) * radius, Math.sin(angle * 2) * 0.5, Math.sin(angle) * radius)
            mesh.userData.baseY = mesh.position.y
            mesh.userData.phase = i * 0.7
            group.add(mesh)
            planes.push(mesh)

            // 图标后方柔光（轻微呼吸）
            const spriteMat = new THREE.SpriteMaterial({
                map: glowTexture,
                color: ACCENT,
                transparent: true,
                opacity: 0.3,
                blending: THREE.AdditiveBlending,
                depthWrite: false,
            })
            const sprite = new THREE.Sprite(spriteMat)
            sprite.position.copy(mesh.position)
            sprite.position.multiplyScalar(1.06)
            sprite.scale.set(2.0, 2.0, 1)
            sprite.userData.phase = mesh.userData.phase
            group.add(sprite)
            glowSprites.push(sprite)
        })
        scene.add(group)

        // 环形底座双圈（装饰）
        const ringOuter = new THREE.Mesh(
            new THREE.TorusGeometry(radius, 0.015, 8, 80),
            new THREE.MeshBasicMaterial({ color: ACCENT, transparent: true, opacity: 0.35 }),
        )
        ringOuter.rotation.x = Math.PI / 2
        scene.add(ringOuter)
        const ringInner = new THREE.Mesh(
            new THREE.TorusGeometry(radius * 0.6, 0.008, 8, 80),
            new THREE.MeshBasicMaterial({ color: ACCENT, transparent: true, opacity: 0.2 }),
        )
        ringInner.rotation.x = Math.PI / 2
        scene.add(ringInner)

        // 中央光核
        const coreMat = new THREE.SpriteMaterial({
            map: glowTexture,
            color: ACCENT,
            transparent: true,
            opacity: 0.55,
            blending: THREE.AdditiveBlending,
            depthWrite: false,
        })
        const core = new THREE.Sprite(coreMat)
        core.position.set(0, 0, 0)
        core.scale.set(2.2, 2.2, 1)
        scene.add(core)

        // 彩色流星雨背景
        const meteors: Meteor[] = Array.from({ length: METEOR_COUNT }, (_, i) => spawnMeteor(streakTexture, glowTexture, i))
        for (const meteor of meteors) {
            scene.add(meteor.group)
        }

        let disposed = false
        let raf = 0
        const clock = new THREE.Clock()
        const renderLoop = () => {
            if (disposed) return
            raf = requestAnimationFrame(renderLoop)
            const dt = Math.min(clock.getDelta(), 0.1)
            const t = clock.getElapsedTime()
            group.rotation.y = t * 0.35
            core.scale.setScalar(2.2 + Math.sin(t * 1.6) * 0.35)
            for (let i = 0; i < planes.length; i++) {
                const plane = planes[i]
                plane.position.y = (plane.userData.baseY as number) + Math.sin(t * 1.4 + (plane.userData.phase as number)) * 0.18
                plane.lookAt(camera.position)
                const sprite = glowSprites[i]
                if (sprite) {
                    sprite.position.copy(plane.position).multiplyScalar(1.06)
                    const mat = sprite.material as THREE.SpriteMaterial
                    mat.opacity = 0.22 + Math.sin(t * 1.4 + (plane.userData.phase as number)) * 0.1
                }
            }
            // 流星流动 + 头部明暗闪烁
            for (const meteor of meteors) {
                meteor.group.position.x += meteor.velocity.x * meteor.speed * dt
                meteor.group.position.y += meteor.velocity.y * meteor.speed * dt
                const headMat = meteor.head.material as THREE.SpriteMaterial
                headMat.opacity = meteor.baseOpacity * (0.65 + 0.35 * Math.sin(t * 2 + meteor.phase))
                if (meteor.group.position.x > BOUND_X) meteor.group.position.x = -BOUND_X
                if (meteor.group.position.x < -BOUND_X) meteor.group.position.x = BOUND_X
                if (meteor.group.position.y > BOUND_Y) meteor.group.position.y = -BOUND_Y
                if (meteor.group.position.y < -BOUND_Y) meteor.group.position.y = BOUND_Y
            }
            renderer.render(scene, camera)
        }
        renderLoop()

        const onResize = () => {
            const w = mount.clientWidth || 600
            camera.aspect = w / height
            camera.updateProjectionMatrix()
            renderer.setSize(w, height)
        }
        const observer = new ResizeObserver(onResize)
        observer.observe(mount)

        return () => {
            disposed = true
            cancelAnimationFrame(raf)
            observer.disconnect()
            for (const plane of planes) {
                plane.geometry.dispose()
                const mat = plane.material as THREE.MeshBasicMaterial
                mat.map?.dispose()
                mat.dispose()
            }
            for (const sprite of glowSprites) {
                ;(sprite.material as THREE.SpriteMaterial).dispose()
            }
            for (const meteor of meteors) {
                meteor.mesh.geometry.dispose()
                ;(meteor.mesh.material as THREE.MeshBasicMaterial).dispose()
                ;(meteor.head.material as THREE.SpriteMaterial).dispose()
            }
            glowTexture.dispose()
            streakTexture.dispose()
            ringOuter.geometry.dispose()
            ringInner.geometry.dispose()
            coreMat.dispose()
            renderer.dispose()
            if (mount.contains(renderer.domElement)) {
                mount.removeChild(renderer.domElement)
            }
        }
    }, [groups])

    return <div ref={mountRef} style={{ width: '100%', height: 320, marginBottom: 16 }} aria-label="收藏馆 3D 展示厅" />
}