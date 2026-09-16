import { useEffect, useRef } from 'react'
import * as THREE from 'three'
import type { CollectionGroup } from '@/api/wish'

/**
 * 收藏馆 3D 展示厅（Sprint 3.6 验收，四AB WEB P0-5）：
 * Three.js 悬浮展示收藏资产（图标平面 + 呼吸浮动 + 缓慢自转 + 中央光核 + 星河粒子），
 * ≤1024px 视口自动降级隐藏（由父级 matchMedia 控制，降级为分组列表）。
 */

interface ShowcaseItem {
    key: string
    name: string
    icon: string
}

const ACCENT = 0xffd97a

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

/** 径向渐变光晕纹理（中央光核 + 图标底衬） */
function makeGlowTexture(inner: string, outer: string): THREE.Texture {
    const size = 256
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')
    if (ctx) {
        const g = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
        g.addColorStop(0, inner)
        g.addColorStop(0.4, inner.replace('1)', '0.35)'))
        g.addColorStop(1, outer)
        ctx.fillStyle = g
        ctx.fillRect(0, 0, size, size)
    }
    const texture = new THREE.CanvasTexture(canvas)
    texture.colorSpace = THREE.SRGBColorSpace
    return texture
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

        // 环形排布悬浮图标平面
        const group = new THREE.Group()
        const radius = Math.min(3.2, 1.4 + items.length * 0.24)
        const planes: THREE.Mesh[] = []
        const glowSprites: THREE.Sprite[] = []
        const glowTexture = makeGlowTexture('rgba(255, 217, 122, 1)', 'rgba(255, 217, 122, 1)')
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
                color: 0xffd97a,
                transparent: true,
                opacity: 0.22,
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
            color: 0xffd97a,
            transparent: true,
            opacity: 0.5,
            blending: THREE.AdditiveBlending,
            depthWrite: false,
        })
        const core = new THREE.Sprite(coreMat)
        core.position.set(0, 0, 0)
        core.scale.set(2.2, 2.2, 1)
        scene.add(core)

        // 星河粒子背景
        const starCount = 120
        const starGeo = new THREE.BufferGeometry()
        const starPos = new Float32Array(starCount * 3)
        for (let i = 0; i < starCount; i++) {
            starPos[i * 3] = (Math.random() - 0.5) * 14
            starPos[i * 3 + 1] = (Math.random() - 0.5) * 8
            starPos[i * 3 + 2] = (Math.random() - 0.5) * 8
        }
        starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3))
        const stars = new THREE.Points(
            starGeo,
            new THREE.PointsMaterial({
                color: 0xcfe6ff,
                size: 0.04,
                transparent: true,
                opacity: 0.7,
                depthWrite: false,
            }),
        )
        scene.add(stars)

        let disposed = false
        let raf = 0
        const clock = new THREE.Clock()
        const renderLoop = () => {
            if (disposed) return
            raf = requestAnimationFrame(renderLoop)
            const t = clock.getElapsedTime()
            group.rotation.y = t * 0.35
            stars.rotation.y = t * 0.04
            stars.rotation.x = t * 0.02
            core.scale.setScalar(2.2 + Math.sin(t * 1.6) * 0.35)
            for (let i = 0; i < planes.length; i++) {
                const plane = planes[i]
                plane.position.y = (plane.userData.baseY as number) + Math.sin(t * 1.4 + (plane.userData.phase as number)) * 0.18
                plane.lookAt(camera.position)
                const sprite = glowSprites[i]
                if (sprite) {
                    sprite.position.copy(plane.position).multiplyScalar(1.06)
                    const mat = sprite.material as THREE.SpriteMaterial
                    mat.opacity = 0.18 + Math.sin(t * 1.4 + (plane.userData.phase as number)) * 0.08
                }
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
            glowTexture.dispose()
            ringOuter.geometry.dispose()
            ringInner.geometry.dispose()
            coreMat.dispose()
            starGeo.dispose()
            ;(stars.material as THREE.PointsMaterial).dispose()
            renderer.dispose()
            if (mount.contains(renderer.domElement)) {
                mount.removeChild(renderer.domElement)
            }
        }
    }, [groups])

    return <div ref={mountRef} style={{ width: '100%', height: 320, marginBottom: 16 }} aria-label="收藏馆 3D 展示厅" />
}