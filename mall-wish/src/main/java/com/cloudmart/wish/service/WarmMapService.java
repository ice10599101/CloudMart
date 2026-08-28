package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.WarmEvent;
import com.cloudmart.wish.entity.WishFence;
import com.cloudmart.wish.vo.FenceCheckVO;
import com.cloudmart.wish.vo.MapClusterVO;
import com.cloudmart.wish.vo.WarmEventVO;

import java.util.List;

/**
 * 城市幸福地图 + 地理围栏服务（Sprint 3.2，文档 2.10/十二/3.2）。
 */
public interface WarmMapService {

    /**
     * 围栏打卡判定（契约 POST /wish/map/fence/check）：
     * 服务端判定用户坐标是否命中该心愿的活跃围栏（Haversine ≤ radius
     * 含等号），命中记录到达（每围栏每用户每日幂等）并触发绽放；
     * 响应不含任何围栏坐标（隐私验收）。
     *
     * @param wishId 用户本人心愿（非本人 404 防探测）
     */
    FenceCheckVO checkFence(Long userId, Long wishId, Double lat, Double lng);

    /**
     * 发布温暖事件（DFA 敏感词命中 → AUTO_HIDDEN 不可见；未命中 →
     * PENDING 先发后审）。坐标服务端 geohash7 编码，原始坐标不留存。
     */
    WarmEventVO publishWarmEvent(Long userId, String title, String content, Double lat, Double lng);

    /**
     * 温暖事件附近列表（模糊化坐标 + 距离升序；cityCode 可选按城市过滤）。
     */
    List<WarmEventVO> listWarmEvents(Double lat, Double lng, Integer radius, String cityCode);

    /** 温暖事件网格聚合（geohash6 数量角标） */
    List<MapClusterVO> clusterWarmEvents(Double lat, Double lng, Integer radius, String cityCode);

    // ---------------- 管理端 ----------------

    /** 围栏列表（含未启用；仅管理端可见，含中心坐标回显） */
    List<WishFence> listFences(Long wishId);

    /** 创建围栏（半径最小 10m；center 服务端 geohash7 编码） */
    WishFence createFence(SaveFenceCommand command);

    /** 更新围栏（字段覆盖式） */
    WishFence updateFence(Long fenceId, SaveFenceCommand command);

    /** 启用/停用围栏 */
    void toggleFence(Long fenceId, boolean active);

    /** 删除围栏（配置数据物理删除；到达记录保留审计） */
    void deleteFence(Long fenceId);

    /** 温暖事件审核列表（全状态分页） */
    List<WarmEvent> listWarmEventsForAdmin(String auditStatus, int page, int size);

    /** 审核温暖事件（APPROVED/REJECTED/AUTO_HIDDEN，同步 is_visible） */
    WarmEvent auditWarmEvent(Long eventId, String auditStatus);

    /**
     * 围栏保存命令（管理端提交 center 经纬度，服务端 geohash7 编码存储）。
     */
    record SaveFenceCommand(
            String name,
            Long wishId,
            Double centerLat,
            Double centerLng,
            Integer radiusM,
            java.time.LocalDateTime validFrom,
            java.time.LocalDateTime validTo,
            Boolean isActive,
            Long adminUserId) {
    }
}
