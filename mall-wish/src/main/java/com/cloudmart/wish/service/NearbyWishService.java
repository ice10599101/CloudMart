package com.cloudmart.wish.service;

import com.cloudmart.wish.vo.NearbyWishVO;
import com.cloudmart.wish.vo.MapClusterVO;

import java.util.List;

/**
 * LBS 附近心愿服务（Sprint 3.1，文档 2.10/3.1）。
 */
public interface NearbyWishService {

    /**
     * 附近心愿（模糊化坐标，PRIVATE/TREE_HOLE 天然排除）。
     *
     * <p>radius：null/<=0/>50000 → 默认 5000（验收：异常 radius 兜底）；
     * lat/lng 为 null 或 0,0 → 默认城市兜底；geohash 参数优先于 lat/lng
     * （长度<6 或非法字符 → 拒绝）。</p>
     *
     * @param userId  当前用户（可空=匿名浏览；仅影响缓存无个性化）
     * @param lat     纬度（可空）
     * @param lng     经度（可空）
     * @param radius  半径（米）
     * @param geohash 直接指定查询格（可空，geohash6/7）
     */
    List<NearbyWishVO> nearby(Long userId, Double lat, Double lng, Integer radius, String geohash);

    /**
     * 网格聚合：附近结果按 geohash6 分组（数量角标；Redis 缓存命中 P95<300ms）。
     */
    List<MapClusterVO> cluster(Long userId, Double lat, Double lng, Integer radius, String geohash);
}
