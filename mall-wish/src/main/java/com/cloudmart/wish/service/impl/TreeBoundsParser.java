package com.cloudmart.wish.service.impl;

import java.util.Optional;

/**
 * 世界树果实分页 bounds 视口参数解析器（Sprint 2.1，纯函数）。
 *
 * <p>文档 2.5 GET /wish/tree/fruits 的 {@code bounds} 视口过滤：四端 3D
 * 场景按摄像机视角范围动态加载果实，服务端仅返回视口内数据。
 * 球面坐标映射：lat → phi 纬度角 [0,π]，lng → theta 经度角 [0,2π)。</p>
 *
 * <p>解析规则（文档验收："bounds 参数异常（负数/超范围）→ 默认值兜底，不报错"）：</p>
 * <ul>
 *   <li>四参数全空 → 无视口过滤（返回全量分页，即默认兜底）</li>
 *   <li>四参数齐全且合法 → 视口过滤；{@code minLng > maxLng} 视为
 *       跨 0/2π 经度环绕窗口（theta ≥ minLng OR theta ≤ maxLng）</li>
 *   <li>部分提供（1-3 个）、数值超范围（lat∉[0,π]、lng∉[0,2π]）、
 *       {@code minLat ≥ maxLat}（纬度不环绕，等值零宽窗口无业务意义）
 *       或 {@code minLng == maxLng}（零宽经度窗口）→ 整组忽略，
 *       退化为全量分页，不报错</li>
 * </ul>
 */
final class TreeBoundsParser {

    private TreeBoundsParser() {
    }

    /**
     * 解析 bounds 参数。
     *
     * @param minLat 最小纬度角（phi 下界，含）
     * @param maxLat 最大纬度角（phi 上界，含）
     * @param minLng 最小经度角（theta 下界，含；大于 maxLng 时表示环绕窗口）
     * @param maxLng 最大经度角（theta 上界，含）
     * @return 合法视口返回 {@link Optional#of}，无效/未提供返回 {@link Optional#empty()}
     */
    static Optional<TreeBounds> parse(Double minLat, Double maxLat,
                                      Double minLng, Double maxLng) {
        boolean anyProvided = minLat != null || maxLat != null || minLng != null || maxLng != null;
        if (!anyProvided) {
            return Optional.empty();
        }
        // 部分提供视为整组无效（半过滤会产生不可预期的部分结果）
        if (minLat == null || maxLat == null || minLng == null || maxLng == null) {
            return Optional.empty();
        }
        boolean latValid = minLat >= 0 && maxLat <= Math.PI && minLat < maxLat;
        boolean lngValid = minLng >= 0 && maxLng <= 2 * Math.PI && !minLng.equals(maxLng);
        if (!latValid || !lngValid) {
            return Optional.empty();
        }
        return Optional.of(new TreeBounds(minLat, maxLat, minLng, maxLng, minLng > maxLng));
    }

    /**
     * 有效视口（弧度制，含边界）。
     *
     * @param minPhi     phi 下界（含）
     * @param maxPhi     phi 上界（含）
     * @param minTheta   theta 下界（含；环绕时为较大一侧边界）
     * @param maxTheta   theta 上界（含；环绕时为较小一侧边界）
     * @param wrapTheta  true 表示经度跨 0/2π 环绕窗口（theta ≥ minTheta OR theta ≤ maxTheta）
     */
    record TreeBounds(double minPhi, double maxPhi,
                      double minTheta, double maxTheta, boolean wrapTheta) {
    }
}
