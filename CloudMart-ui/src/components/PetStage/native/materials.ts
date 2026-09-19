import * as THREE from 'three'

/**
 * 原生 3D 舞台的材质与网格工具（视觉重构 v3）。
 *
 * 渲染语言（对齐成熟商业休闲宠物游戏的角色质感）：
 *  - 主体：MeshToonMaterial + 程序化生成的 4 阶卡通渐变贴图 —— 明暗分层清晰、颜色饱和，
 *    比物理 PBR 更贴近"手绘动画"观感，也避免了金属/粗糙度参数带来的塑料感；
 *  - 描边：背面外扩壳（BackSide + 顶点沿法线外扩），轮廓线让角色从背景中"跳"出来；
 *  - 光影：半球天光 + 带软阴影的主平行光 + 暖色补光，地面接收真实投影（立体感的关键）；
 *  - 半透明：腮红/影子/光斑走 transparent 材质且不写深度。
 *
 * 所有贴图/形状均程序化生成（零外部美术资产），与 pet-game 的"零资产"约束保持一致。
 */

/** 4 阶卡通渐变（暗→亮），NearestFilter 保证台阶感 */
export function createToonGradient(): THREE.DataTexture {
    const steps = new Uint8Array([118, 176, 226, 255])
    const texture = new THREE.DataTexture(steps, steps.length, 1, THREE.RedFormat)
    texture.minFilter = THREE.NearestFilter
    texture.magFilter = THREE.NearestFilter
    texture.generateMipmaps = false
    texture.needsUpdate = true
    return texture
}

export interface ToonStyle {
    /** 自发光强度（0-1）：用于耳内/腮红等需要透光感的部位 */
    emissive?: number
    /** 描边宽度（未指定则不描边） */
    outline?: number
    /** 描边颜色 */
    outlineColor?: number
    /** 透明度（0-1，<1 时使用半透明混合） */
    opacity?: number
    /** 是否投影（默认 true） */
    castShadow?: boolean
    /** 是否接收阴影（默认 false） */
    receiveShadow?: boolean
    /** 纯发光体（不吃光照，用于灯串/光点） */
    glow?: boolean
}

/** 舞台材质工厂：统一管理渐变贴图与材质缓存 */
export class PetMaterialKit {

    private readonly gradient = createToonGradient()
    private readonly toonCache = new Map<string, THREE.MeshToonMaterial>()
    private readonly outlineCache = new Map<string, THREE.ShaderMaterial>()

    /** 卡通主体材质（按颜色缓存，避免同类零件重复创建） */
    toon(color: THREE.ColorRepresentation, style: ToonStyle = {}): THREE.MeshToonMaterial {
        const key = `${new THREE.Color(color).getHexString()}|${style.emissive ?? 0}|${style.opacity ?? 1}`
        const cached = this.toonCache.get(key)
        if (cached) {
            return cached
        }
        const material = new THREE.MeshToonMaterial({
            color,
            gradientMap: this.gradient,
            transparent: style.opacity !== undefined && style.opacity < 1,
            opacity: style.opacity ?? 1,
        })
        if (style.emissive) {
            material.emissive = new THREE.Color(color)
            material.emissiveIntensity = style.emissive
        }
        this.toonCache.set(key, material)
        return material
    }

    /** 发光材质（灯泡/光点/窗玻璃：不吃光照，自带亮度） */
    glow(color: THREE.ColorRepresentation, opacity = 1): THREE.MeshBasicMaterial {
        return new THREE.MeshBasicMaterial({
            color,
            transparent: opacity < 1,
            opacity,
            toneMapped: false,
        })
    }

    /** 描边材质：背面外扩壳（width 为模型空间宽度，需按零件尺度给出） */
    outline(width: number, color = 0x2a2030): THREE.ShaderMaterial {
        const key = `${width}|${color}`
        const cached = this.outlineCache.get(key)
        if (cached) {
            return cached
        }
        const material = new THREE.ShaderMaterial({
            uniforms: {
                uWidth: { value: width },
                uColor: { value: new THREE.Color(color) },
            },
            vertexShader: /* glsl */ `
                uniform float uWidth;
                void main() {
                    vec3 expanded = position + normalize(normal) * uWidth;
                    gl_Position = projectionMatrix * modelViewMatrix * vec4(expanded, 1.0);
                }
            `,
            fragmentShader: /* glsl */ `
                uniform vec3 uColor;
                void main() {
                    gl_FragColor = vec4(uColor, 1.0);
                }
            `,
            side: THREE.BackSide,
        })
        this.outlineCache.set(key, material)
        return material
    }

    /** 释放缓存的渐变贴图与材质（组件卸载时调用，避免 GPU 资源泄漏） */
    dispose(): void {
        this.gradient.dispose()
        for (const material of this.toonCache.values()) {
            material.dispose()
        }
        for (const material of this.outlineCache.values()) {
            material.dispose()
        }
        this.toonCache.clear()
        this.outlineCache.clear()
    }
}

export interface MeshOptions extends ToonStyle {
    name?: string
    position?: [number, number, number]
    rotation?: [number, number, number]
    scale?: [number, number, number]
}

/**
 * 创建带卡通描边的网格（返回 Group：主体 + 可选描边壳）。
 *
 * 描边壳与主体共享几何体（不复制顶点数据），因此开销仅为一次额外绘制调用；
 * 对球体/胶囊等圆润形体，背面外扩即可得到干净的外轮廓。
 */
export function createToonMesh(
    geometry: THREE.BufferGeometry,
    material: THREE.Material,
    kit: PetMaterialKit,
    style: MeshOptions = {},
): THREE.Group {
    const group = new THREE.Group()
    group.name = style.name ?? 'part'
    if (style.position) {
        group.position.set(...style.position)
    }
    if (style.rotation) {
        group.rotation.set(...style.rotation)
    }
    if (style.scale) {
        group.scale.set(...style.scale)
    }

    const body = new THREE.Mesh(geometry, material)
    body.castShadow = style.castShadow !== false && !style.glow
    body.receiveShadow = style.receiveShadow === true
    group.add(body)

    if (style.outline && style.outline > 0 && !style.glow) {
        const shell = new THREE.Mesh(geometry, kit.outline(style.outline, style.outlineColor))
        shell.castShadow = false
        shell.receiveShadow = false
        group.add(shell)
    }
    return group
}
