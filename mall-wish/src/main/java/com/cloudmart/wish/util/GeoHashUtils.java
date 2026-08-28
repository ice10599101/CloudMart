package com.cloudmart.wish.util;

/**
 * GeoHash 编解码纯函数工具（Sprint 3.1，隐私重点：服务端仅存 geohash，
 * 原始坐标只在请求处理期间内存存在）。
 *
 * <p>精度对照（文档 3.1 隐私保护算法）：geohash7 ≈ 153m × 152m 网格
 * （存储精度）、geohash6 ≈ 1.2km（聚合网格）、geohash5 ≈ 4.9km（查询窗）、
 * geohash4 ≈ 39km（同城代理）。</p>
 *
 * <p>偏移算法（文档：±50m 随机偏移 + 种子可复现）：以 wishId 为种子，
 * 黄金角散列生成确定性偏移（0-50m）——同一心愿每次查询返回同一模糊点，
 * 满足"结果可复现性验证"验收；他人无法从模糊点反推原始坐标。</p>
 */
import java.util.Set;

public final class GeoHashUtils {

    /** base32 字符表（geohash 标准，无易混淆的 a/i/l/o） */
    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    /** 最大偏移距离（米，文档 3.1：±50m 偏移） */
    private static final double MAX_OFFSET_METERS = 50.0;

    /** 地球半径（米，Haversine） */
    private static final double EARTH_RADIUS_M = 6371000.0;

    private GeoHashUtils() {
    }

    /**
     * 编码 geohash（指定精度位数）。
     *
     * @throws IllegalArgumentException lat/lng 越界
     */
    public static String encode(double latitude, double longitude, int precision) {
        if (precision < 1 || precision > 12) {
            throw new IllegalArgumentException("geohash 精度须为 1-12: " + precision);
        }
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("纬度越界: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("经度越界: " + longitude);
        }
        double latMin = -90.0;
        double latMax = 90.0;
        double lngMin = -180.0;
        double lngMax = 180.0;
        StringBuilder hash = new StringBuilder(precision);
        boolean isEvenBit = true;
        int bit = 0;
        int chIndex = 0;
        while (hash.length() < precision) {
            if (isEvenBit) {
                // 经度位
                double mid = (lngMin + lngMax) / 2;
                if (longitude >= mid) {
                    chIndex = (chIndex << 1) | 1;
                    lngMin = mid;
                } else {
                    chIndex <<= 1;
                    lngMax = mid;
                }
            } else {
                // 纬度位
                double mid = (latMin + latMax) / 2;
                if (latitude >= mid) {
                    chIndex = (chIndex << 1) | 1;
                    latMin = mid;
                } else {
                    chIndex <<= 1;
                    latMax = mid;
                }
            }
            isEvenBit = !isEvenBit;
            bit++;
            if (bit == 5) {
                hash.append(BASE32.charAt(chIndex));
                bit = 0;
                chIndex = 0;
            }
        }
        return hash.toString();
    }

    /**
     * 解码网格中心点。
     *
     * @return [lat, lng]（网格中心）
     * @throws IllegalArgumentException geohash 非法（长度<1/字符不在 base32）
     */
    public static double[] decodeCenter(String geohash) {
        validate(geohash, 1);
        double latMin = -90.0;
        double latMax = 90.0;
        double lngMin = -180.0;
        double lngMax = 180.0;
        boolean isEvenBit = true;
        for (char c : geohash.toCharArray()) {
            int cd = BASE32.indexOf(Character.toLowerCase(c));
            for (int bit = 4; bit >= 0; bit--) {
                int mask = 1 << bit;
                if (isEvenBit) {
                    double mid = (lngMin + lngMax) / 2;
                    if ((cd & mask) != 0) {
                        lngMin = mid;
                    } else {
                        lngMax = mid;
                    }
                } else {
                    double mid = (latMin + latMax) / 2;
                    if ((cd & mask) != 0) {
                        latMin = mid;
                    } else {
                        latMax = mid;
                    }
                }
                isEvenBit = !isEvenBit;
            }
        }
        return new double[]{(latMin + latMax) / 2, (lngMin + lngMax) / 2};
    }

    /**
     * 校验 geohash（Sprint 3.1 验收：长度 < 6 或含非法字符 → 拒绝）。
     *
     * @param minLength 最低长度（聚合展示用 6）
     */
    public static void validate(String geohash, int minLength) {
        if (geohash == null || geohash.length() < minLength) {
            throw new IllegalArgumentException("geohash 长度须 >= " + minLength);
        }
        String lower = geohash.toLowerCase();
        for (char c : lower.toCharArray()) {
            if (BASE32.indexOf(c) < 0) {
                throw new IllegalArgumentException("geohash 含非法字符: " + c);
            }
        }
    }

    /**
     * 8 邻格 + 本格前缀（附近查询窗口：同 geohash5 前缀匹配用完整前缀列表）。
     *
     * <p>实现：decode 本格中心 → 对 8 个方向微偏移重新 encode 同精度 →
     * 去重集合（经典近似法，格边界的跨格覆盖由 9 格窗口保证）。</p>
     */
    public static Set<String> neighbors(String geohash) {
        validate(geohash, 1);
        double[] center = decodeCenter(geohash);
        // 精确格尺寸（位运算）：5p 位中偶数位为经度、奇数位为纬度
        // lngCell = 360/2^lngBits、latCell = 180/2^latBits——从格中心移动
        // 恰好一格尺寸必落入相邻格（采样步长精确，避免近似法同格去重为 1）
        int totalBits = 5 * geohash.length();
        int latBits = totalBits / 2;
        int lngBits = totalBits - latBits;
        double stepLat = 180.0 / Math.pow(2, latBits);
        double stepLng = 360.0 / Math.pow(2, lngBits);
        Set<String> cells = new java.util.LinkedHashSet<>();
        cells.add(geohash);
        for (int dLat = -1; dLat <= 1; dLat++) {
            for (int dLng = -1; dLng <= 1; dLng++) {
                if (dLat == 0 && dLng == 0) {
                    continue;
                }
                double lat = clampLat(center[0] + dLat * stepLat);
                double lng = clampLng(center[1] + dLng * stepLng);
                cells.add(encode(lat, lng, geohash.length()));
            }
        }
        return cells;
    }

    /**
     * 确定性偏移（文档验收：偏移范围 0-50m，结果可复现）。
     *
     * <p>黄金角散列：angle = 2π × frac(seed × 0.618034)、
     * dist = MAX_OFFSET × frac(seed × 0.381966)——同 seed 恒同偏移，
     * 不同 seed 均匀散布。</p>
     *
     * @return [offsetLat, offsetLng]（米转度，纬度向 1m≈1/111320°）
     */
    public static double[] deterministicOffset(double lat, double lng, long seed) {
        double fracA = frac(seed * 0.6180339887);
        double fracB = frac(seed * 0.3819660113);
        double angle = 2 * Math.PI * fracA;
        double dist = MAX_OFFSET_METERS * fracB;
        double dLat = dist * Math.cos(angle) / 111320.0;
        double dLng = dist * Math.sin(angle) / (111320.0 * Math.max(0.1, Math.cos(Math.toRadians(lat))));
        return new double[]{clampLat(lat + dLat), clampLng(lng + dLng)};
    }

    /** Haversine 距离（米，取整由调用方处理） */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double frac(double value) {
        double v = Math.abs(value);
        return v - Math.floor(v);
    }

    private static double clampLat(double lat) {
        return Math.max(-90.0, Math.min(90.0, lat));
    }

    private static double clampLng(double lng) {
        // 经度环绕（跨 ±180 处理）
        if (lng > 180.0) {
            return lng - 360.0;
        }
        if (lng < -180.0) {
            return lng + 360.0;
        }
        return lng;
    }
}
