/**
 * 手写网格工厂（视觉重构 v4）—— 用连续曲面替代"球体拼装"。
 *
 * 为什么需要它：
 *  - primitives 只有球/柱/锥等简单体，拼装出的角色剪影破碎、接缝穿插、法线不连续，
 *    这正是上一版"零件感、廉价感"的根本原因；
 *  - 引擎提供 utils.MeshUtils.createMesh(IGeometry)，允许直接提交任意顶点数据，
 *    因此这里用"旋转体放样(lathe) + 扫掠(sweep) + 平滑法线"生成一体化连续曲面。
 *
 * 约定：
 *  - 坐标系为 Cocos 右手系，+Y 向上，+Z 为角色正前方（与 PetModelBuilder 一致）；
 *  - 几何在本地空间生成，由调用方用节点变换定位（与旧版 part/ball 用法保持一致）；
 *  - 输出结构与 primitives.IGeometry 兼容，可直接喂给 createMesh。
 */

/** 与 primitives.IGeometry 兼容的几何数据（仅使用渲染必需字段） */
export interface Geo {
    positions: number[]
    normals: number[]
    uvs: number[]
    indices: number[]
}

/** 三维点（数组形式，避免在数学层引入引擎依赖） */
export type P3 = [number, number, number]

/** 扫掠路径节点：位置 + 截面半径（可分别缩放 x/y 形成椭圆截面） */
export interface SweepNode {
    p: P3
    r: number
    /** 截面横向缩放（默认 1；用于扁耳、扁尾等） */
    sx?: number
    /** 截面纵向缩放（默认 1） */
    sy?: number
}

/** 顶点变换参数（位置/欧拉角(弧度)/缩放） */
export interface GeoTransform {
    pos?: P3
    /** 欧拉角（弧度），按 X→Y→Z 顺序 */
    rot?: P3
    scale?: P3
}

const EPS = 1e-6

/**
 * 旋转体：把 (半径, 高度) 侧轮廓绕 Y 轴旋转成连续曲面。
 *
 * 这是本项目最重要的建模原语：头、躯干、耳朵、四肢全部由它生成，
 * 因为轮廓可以任意精细，且法线由轮廓切线解析求出（天然光滑、无接缝）。
 *
 * @param profile 侧轮廓，从下到上 [radius, y]；半径 0 表示收口（自动形成极点）
 * @param segments 圆周分段（建议 28-48，越低越"低多边形"）
 */
export function lathe(profile: Array<[number, number]>, segments = 32): Geo {
    const rings = profile.length
    const positions: number[] = []
    const normals: number[] = []
    const uvs: number[] = []
    const indices: number[] = []

    // 预先求每层轮廓的 2D 法线：轮廓切线顺时针旋转 90° 指向外侧
    const rimNormal: Array<[number, number]> = []
    for (let i = 0; i < rings; i++) {
        const prev = profile[Math.max(0, i - 1)]
        const next = profile[Math.min(rings - 1, i + 1)]
        let tr = next[0] - prev[0]
        let ty = next[1] - prev[1]
        const len = Math.hypot(tr, ty)
        if (len < EPS) {
            tr = 0
            ty = 1
        } else {
            tr /= len
            ty /= len
        }
        rimNormal.push([ty, -tr])
    }

    for (let i = 0; i < rings; i++) {
        const [radius, y] = profile[i]
        const [nr, ny] = rimNormal[i]
        const v = rings > 1 ? i / (rings - 1) : 0
        for (let j = 0; j <= segments; j++) {
            const u = j / segments
            const theta = u * Math.PI * 2
            const cos = Math.cos(theta)
            const sin = Math.sin(theta)
            positions.push(radius * cos, y, radius * sin)
            normals.push(nr * cos, ny, nr * sin)
            uvs.push(u, v)
        }
    }

    const stride = segments + 1
    for (let i = 0; i < rings - 1; i++) {
        for (let j = 0; j < segments; j++) {
            const a = i * stride + j
            const b = a + 1
            const c = a + stride
            const d = c + 1
            indices.push(a, c, b, b, c, d)
        }
    }
    return { positions, normals, uvs, indices }
}

/**
 * 椭球：三轴半径独立（Q 版头/身体/眼睛的基本体）。
 * 法线按椭球解析式求出（比球面法线更准确，暗部过渡更自然）。
 */
export function ellipsoid(rx: number, ry: number, rz: number, segments = 32, rings = 22): Geo {
    const positions: number[] = []
    const normals: number[] = []
    const uvs: number[] = []
    const indices: number[] = []

    for (let i = 0; i <= rings; i++) {
        const phi = (i / rings) * Math.PI           // 0(顶) → π(底)
        const sp = Math.sin(phi)
        const cp = Math.cos(phi)
        for (let j = 0; j <= segments; j++) {
            const theta = (j / segments) * Math.PI * 2
            const st = Math.sin(theta)
            const ct = Math.cos(theta)
            const x = rx * sp * ct
            const y = ry * cp
            const z = rz * sp * st
            positions.push(x, y, z)
            // 椭球隐式梯度 (x/rx², y/ry², z/rz²)
            let nx = x / (rx * rx)
            let ny = y / (ry * ry)
            let nz = z / (rz * rz)
            const len = Math.hypot(nx, ny, nz) || 1
            nx /= len; ny /= len; nz /= len
            normals.push(nx, ny, nz)
            uvs.push(j / segments, 1 - i / rings)
        }
    }
    const stride = segments + 1
    for (let i = 0; i < rings; i++) {
        for (let j = 0; j < segments; j++) {
            const a = i * stride + j
            const b = a + 1
            const c = a + stride
            const d = c + 1
            indices.push(a, b, c, b, d, c)
        }
    }
    return { positions, normals, uvs, indices }
}

/** 归一化向量 */
function norm(v: P3): P3 {
    const len = Math.hypot(v[0], v[1], v[2]) || 1
    return [v[0] / len, v[1] / len, v[2] / len]
}

/** 叉乘 */
function cross(a: P3, b: P3): P3 {
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
}

/**
 * 扫掠管：沿 3D 路径扫掠椭圆截面（耳朵/尾巴/四肢）。
 *
 * 使用"平行传输"框架逐节点推进截面，避免路径转弯时截面扭转撕裂；
 * 端点按半径收口，形成圆润的封头（Q 版造型里所有末端都应该是圆的）。
 */
export function sweep(nodes: SweepNode[], radial = 18): Geo {
    const count = nodes.length
    const positions: number[] = []
    const normals: number[] = []
    const uvs: number[] = []
    const indices: number[] = []

    // 每个节点的切线与截面基向量
    const tangents: P3[] = []
    for (let i = 0; i < count; i++) {
        const prev = nodes[Math.max(0, i - 1)].p
        const next = nodes[Math.min(count - 1, i + 1)].p
        tangents.push(norm([next[0] - prev[0], next[1] - prev[1], next[2] - prev[2]]))
    }

    // 初始参考向量：与首切线不平行的任意轴
    const ref: P3 = Math.abs(tangents[0][1]) > 0.92 ? [1, 0, 0] : [0, 1, 0]
    let normal = norm(cross(tangents[0], ref))

    for (let i = 0; i < count; i++) {
        const t = tangents[i]
        // 平行传输：把上一节点的法线投影到当前切线的垂直平面
        const dot = normal[0] * t[0] + normal[1] * t[1] + normal[2] * t[2]
        let n: P3 = norm([normal[0] - t[0] * dot, normal[1] - t[1] * dot, normal[2] - t[2] * dot])
        if (!isFinite(n[0]) || !isFinite(n[1]) || !isFinite(n[2])) {
            n = norm(cross(t, ref))
        }
        normal = n
        const binormal = norm(cross(t, normal))

        const node = nodes[i]
        const sx = node.sx === undefined ? 1 : node.sx
        const sy = node.sy === undefined ? 1 : node.sy
        const v = count > 1 ? i / (count - 1) : 0
        for (let j = 0; j <= radial; j++) {
            const u = j / radial
            const theta = u * Math.PI * 2
            const cos = Math.cos(theta) * sx
            const sin = Math.sin(theta) * sy
            const px = node.p[0] + (normal[0] * cos + binormal[0] * sin) * node.r
            const py = node.p[1] + (normal[1] * cos + binormal[1] * sin) * node.r
            const pz = node.p[2] + (normal[2] * cos + binormal[2] * sin) * node.r
            positions.push(px, py, pz)
            const nrm = norm([
                normal[0] * Math.cos(theta) * sy + binormal[0] * Math.sin(theta) * sx,
                normal[1] * Math.cos(theta) * sy + binormal[1] * Math.sin(theta) * sx,
                normal[2] * Math.cos(theta) * sy + binormal[2] * Math.sin(theta) * sx,
            ])
            normals.push(nrm[0], nrm[1], nrm[2])
            uvs.push(u, v)
        }
    }

    const stride = radial + 1
    for (let i = 0; i < count - 1; i++) {
        for (let j = 0; j < radial; j++) {
            const a = i * stride + j
            const b = a + 1
            const c = a + stride
            const d = c + 1
            // 绕序说明：binormal = cross(tangent, normal)，截面 θ 方向与 outward(normal) 相反，
            // 因此顺序需与 lathe 相反，才能保证正面朝外（否则会被背面剔除）。
            indices.push(a, b, c, b, d, c)
        }
    }
    return { positions, normals, uvs, indices }
}

/** 欧拉角（XYZ 顺序，弧度）→ 3×3 旋转矩阵（行主序） */
function eulerMatrix(rot: P3): number[] {
    const [rx, ry, rz] = rot
    const cx = Math.cos(rx), sx = Math.sin(rx)
    const cy = Math.cos(ry), sy = Math.sin(ry)
    const cz = Math.cos(rz), sz = Math.sin(rz)
    return [
        cy * cz, -cy * sz, sy,
        sx * sy * cz + cx * sz, -sx * sy * sz + cx * cz, -sx * cy,
        -cx * sy * cz + sx * sz, cx * sy * sz + sx * cz, cx * cy,
    ]
}

/** 几何体变换（位置/旋转/缩放；法线用旋转矩阵变换，随后归一化） */
export function transformed(geo: Geo, t: GeoTransform): Geo {
    const rot = t.rot || [0, 0, 0]
    const scale: P3 = t.scale || [1, 1, 1]
    const pos: P3 = t.pos || [0, 0, 0]
    const m = eulerMatrix(rot)
    const positions: number[] = []
    const normals: number[] = []
    const count = geo.positions.length / 3
    for (let i = 0; i < count; i++) {
        const x = geo.positions[i * 3] * scale[0]
        const y = geo.positions[i * 3 + 1] * scale[1]
        const z = geo.positions[i * 3 + 2] * scale[2]
        positions.push(
            m[0] * x + m[1] * y + m[2] * z + pos[0],
            m[3] * x + m[4] * y + m[5] * z + pos[1],
            m[6] * x + m[7] * y + m[8] * z + pos[2],
        )
        const nx = geo.normals[i * 3]
        const ny = geo.normals[i * 3 + 1]
        const nz = geo.normals[i * 3 + 2]
        const tx = m[0] * nx + m[1] * ny + m[2] * nz
        const ty = m[3] * nx + m[4] * ny + m[5] * nz
        const tz = m[6] * nx + m[7] * ny + m[8] * nz
        const len = Math.hypot(tx, ty, tz) || 1
        normals.push(tx / len, ty / len, tz / len)
    }
    return { positions, normals, uvs: geo.uvs.slice(), indices: geo.indices.slice() }
}

/** 合并多个几何体为一个（同一材质下减少 draw call；索引自动偏移） */
export function merge(...geos: Geo[]): Geo {
    const out: Geo = { positions: [], normals: [], uvs: [], indices: [] }
    let base = 0
    for (const geo of geos) {
        out.positions.push(...geo.positions)
        out.normals.push(...geo.normals)
        out.uvs.push(...geo.uvs)
        for (const index of geo.indices) {
            out.indices.push(index + base)
        }
        base += geo.positions.length / 3
    }
    return out
}

/**
 * 平滑法线：按位置把重合顶点分组求平均后写回。
 *
 * 用途：把多个部件（如头 + 腮 + 额头）焊接处的法线统一，
 * 消除"两个球相交处出现硬边/亮线"的廉价感。
 */
export function smoothNormals(geo: Geo, precision = 4): Geo {
    const map = new Map<string, number[]>()
    const count = geo.positions.length / 3
    const key = (i: number): string =>
        geo.positions[i * 3].toFixed(precision) + ',' +
        geo.positions[i * 3 + 1].toFixed(precision) + ',' +
        geo.positions[i * 3 + 2].toFixed(precision)
    for (let i = 0; i < count; i++) {
        const k = key(i)
        const acc = map.get(k)
        if (acc) {
            acc[0] += geo.normals[i * 3]
            acc[1] += geo.normals[i * 3 + 1]
            acc[2] += geo.normals[i * 3 + 2]
        } else {
            map.set(k, [geo.normals[i * 3], geo.normals[i * 3 + 1], geo.normals[i * 3 + 2]])
        }
    }
    const normals = geo.normals.slice()
    for (let i = 0; i < count; i++) {
        const acc = map.get(key(i))!
        const len = Math.hypot(acc[0], acc[1], acc[2]) || 1
        normals[i * 3] = acc[0] / len
        normals[i * 3 + 1] = acc[1] / len
        normals[i * 3 + 2] = acc[2] / len
    }
    return { positions: geo.positions.slice(), normals, uvs: geo.uvs.slice(), indices: geo.indices.slice() }
}

/**
 * 由"半径-高度"控制点生成顺滑的旋转体轮廓（Catmull-Rom 插值）。
 *
 * 好处：造型只写少量关键点（如头部的额/颊/下巴宽度），
 * 由插值生成几十层轮廓，保证曲面连续（不会出现折角）。
 */
export function profileFrom(control: Array<[number, number]>, steps = 18): Array<[number, number]> {
    if (control.length < 2) {
        return control.slice()
    }
    const out: Array<[number, number]> = []
    const at = (i: number): [number, number] => control[Math.max(0, Math.min(control.length - 1, i))]
    for (let i = 0; i < control.length - 1; i++) {
        const p0 = at(i - 1)
        const p1 = at(i)
        const p2 = at(i + 1)
        const p3 = at(i + 2)
        const last = i === control.length - 2
        const n = last ? steps : steps - 1
        for (let s = 0; s <= n; s++) {
            const t = s / n
            const t2 = t * t
            const t3 = t2 * t
            const r = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t
                + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            const y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t
                + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            out.push([Math.max(0, r), y])
        }
    }
    return out
}
