import { Color } from 'cc'

/**
 * 房间造型通用颜色工具（非界面令牌、非宠物配色表）。
 *
 * 职责边界：
 *  - 只提供颜色明暗插值 {@link shift}，供 PetRoomBuilder 等房间/道具造型生成阴影与高光；
 *  - 宠物本体调色板（resolvePalette 及各 SPECIES 色表、SKIN_BODY）与 ACCENT_GOLD
 *    已随 C35 删除：实际水果形象走 GLB 模型，代码侧不再维护物种色表；
 *  - 界面层令牌由设计稿定稿后在独立模块重建，本文件不承载。
 *
 * 约定：所有颜色都是 sRGB 语义（与素材、设计稿一致），
 * 仅 pet-toon effect 的 linear 属性上传时由引擎做线性化，代码侧不做转换。
 */

/** 颜色向白/黑插值（amount > 0 提亮，< 0 压暗） */
export function shift(color: Color, amount: number): Color {
    const target = amount >= 0 ? 255 : 0
    const k = Math.abs(amount)
    return new Color(
        Math.round(color.r + (target - color.r) * k),
        Math.round(color.g + (target - color.g) * k),
        Math.round(color.b + (target - color.b) * k),
        color.a,
    )
}
