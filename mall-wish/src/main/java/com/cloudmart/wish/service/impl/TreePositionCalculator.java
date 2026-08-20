package com.cloudmart.wish.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 世界树果实球面坐标计算器（Sprint 2.1，纯函数）。
 *
 * <p>文档 2.5 注：取消绝对三维坐标，服务端计算球面角度参数
 * {@code position: { theta, phi }}。本类仅在心愿<b>上树时调用一次</b>，
 * 结果持久化到 {@code wish.tree_theta / wish.tree_phi}，之后不再变更
 * （果实位置稳定不跳动）；V9 迁移的存量回填公式与本类相互独立
 * （坐标只赋值一次并落库，两公式无需对齐）。</p>
 *
 * <p>算法：雪花 id 与 64 位黄金比例常数相乘（long 溢出回绕，确定性散列），
 * 取乘积高 24 位与中 24 位作为两个均匀分数 u1/u2：</p>
 * <ul>
 *   <li>theta = 2π × u1，经度角 [0, 2π)</li>
 *   <li>phi = acos(1 − 2×u2)，纬度角 [0, π]，反余弦变换保证球面均匀
 *       （等面积分布，极区不堆积）</li>
 * </ul>
 */
final class TreePositionCalculator {

    /** 64 位黄金比例常数（Fibonacci hashing），乘法散列标准常数 */
    private static final long GOLDEN_RATIO_64 = 0x9E3779B97F4A7C15L;

    /** 落库小数位（与 V9 DECIMAL(9,7) 列精度对齐） */
    private static final int SCALE = 7;

    private TreePositionCalculator() {
    }

    /**
     * 由心愿 id 确定性计算球面坐标（同 id 恒同值）。
     *
     * @param wishId 雪花 id
     * @return theta ∈ [0,2π)、phi ∈ (0,π]，已按 7 位小数四舍五入
     */
    static TreePosition assign(long wishId) {
        long hashed = wishId * GOLDEN_RATIO_64;
        // 高 24 位 → u1；第 16..39 位 → u2（两个窗口经乘法散列充分混淆）
        double u1 = (hashed >>> 40) / (double) (1L << 24);
        double u2 = ((hashed << 24) >>> 40) / (double) (1L << 24);
        double theta = u1 * 2 * Math.PI;
        double phi = Math.acos(1 - 2 * u2);
        return new TreePosition(
                BigDecimal.valueOf(theta).setScale(SCALE, RoundingMode.HALF_UP),
                BigDecimal.valueOf(phi).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 果实球面坐标（弧度制）。
     *
     * @param theta 经度角 [0, 2π)
     * @param phi   纬度角 (0, π]，0=北极 π=南极
     */
    record TreePosition(BigDecimal theta, BigDecimal phi) {
    }
}
