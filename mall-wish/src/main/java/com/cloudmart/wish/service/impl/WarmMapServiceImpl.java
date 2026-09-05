package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.config.WishMapProperties;
import com.cloudmart.wish.entity.FenceArrival;
import com.cloudmart.wish.entity.WarmEvent;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishFence;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.repository.FenceArrivalMapper;
import com.cloudmart.wish.repository.FenceMapper;
import com.cloudmart.wish.repository.WarmEventMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.WarmMapService;
import com.cloudmart.wish.util.GeoHashUtils;
import com.cloudmart.wish.service.impl.WishContentSanitizer;
import com.cloudmart.wish.vo.FenceCheckVO;
import com.cloudmart.wish.vo.MapClusterVO;
import com.cloudmart.wish.vo.WarmEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 城市幸福地图 + 地理围栏服务实现（Sprint 3.2）。
 *
 * <p>围栏判定：Haversine ≤ radius（含等号）+ 有效期/状态过滤（FenceJudge
 * 纯函数）；1000 围栏批量判定为内存距离计算（<1s，无需空间索引——
 * 有效围栏按 wish_id 索引过滤后逐条算距）。到达幂等：uk(fence,user,date)。</p>
 *
 * <p>隐私：围栏中心 geohash7 仅服务端存储；用户端响应无坐标字段；
 * 打卡坐标仅请求期内存计算不写 DB；温暖事件坐标 geohash7 编码 + 
 * eventId 种子确定性偏移展示。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarmMapServiceImpl implements WarmMapService {

    private static final int MIN_RADIUS_M = 10;
    private static final int DEFAULT_RADIUS_M = 5000;
    private static final int MAX_RADIUS_M = 50000;

    private final FenceMapper fenceMapper;
    private final FenceArrivalMapper arrivalMapper;
    private final WarmEventMapper warmEventMapper;
    private final WishMapper wishMapper;
    private final WishMapProperties mapProperties;
    private final WishContentSanitizer contentSanitizer;

    // ---------------- 围栏打卡 ----------------

    @Override
    public FenceCheckVO checkFence(Long userId, Long wishId, Double lat, Double lng) {
        if (userId == null) {
            throw new BusinessException(WishErrorCodes.WISH_FORBIDDEN, "请先登录");
        }
        if (lat == null || lng == null || isBlankCoordinate(lat, lng)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "无效的定位坐标");
        }
        // 仅本人心愿可触发绽放（防探测：非本人统一 404）
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null || !wish.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }

        LocalDateTime nowUtc = LocalDateTime.now(ZoneId.of("UTC"));
        List<WishFence> fences = fenceMapper.selectList(new LambdaQueryWrapper<WishFence>()
                .eq(WishFence::getWishId, wishId)
                .eq(WishFence::getIsActive, true));

        String hitName = null;
        int matched = 0;
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        for (WishFence fence : fences) {
            if (!FenceJudge.isEffective(fence, nowUtc)) {
                continue;
            }
            double[] center = GeoHashUtils.decodeCenter(fence.getCenterGeohash());
            if (!FenceJudge.isInside(center[0], center[1], fence.getRadiusM(), lat, lng)) {
                continue;
            }
            matched++;
            if (hitName == null) {
                hitName = fence.getName();
            }
            FenceArrival arrival = new FenceArrival();
            arrival.setFenceId(fence.getId());
            arrival.setUserId(userId);
            arrival.setWishId(wishId);
            arrival.setCheckinDate(today);
            try {
                arrivalMapper.insert(arrival);
            } catch (DuplicateKeyException ex) {
                // 当日已触发过：幂等（防重复刷绽放）
            }
        }

        boolean inside = matched > 0;
        log.info("围栏打卡判定, userId={}, wishId={}, inside={}, matched={}", userId, wishId, inside, matched);
        return new FenceCheckVO(wishId, inside, hitName, inside, matched);
    }

    // ---------------- 温暖事件 ----------------

    @Override
    public WarmEvent getEventDetail(Long eventId) {
        final WarmEvent event = warmEventMapper.selectById(eventId);
        if (event == null || Boolean.FALSE.equals(event.getIsVisible()) || event.getDeletedAt() != null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "温暖事件不存在");
        }
        return event;
    }

    @Override
    public void deleteEvent(Long userId, Long eventId) {
        final WarmEvent event = warmEventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "温暖事件不存在");
        }
        if (!event.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_AUTHOR, "仅发布者可删除");
        }
        final WarmEvent update = new WarmEvent();
        update.setId(eventId);
        update.setDeletedAt(LocalDateTime.now(ZoneId.of("UTC")));
        warmEventMapper.updateById(update);
    }

    @Override
    @Transactional
    public WarmEventVO publishWarmEvent(Long userId, String title, String content, Double lat, Double lng) {
        if (lat == null || lng == null || isBlankCoordinate(lat, lng)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "无效的定位坐标");
        }
        String safeTitle = contentSanitizer.escapeHtml(title.trim());
        String safeContent = contentSanitizer.escapeHtml(content.trim());

        String geohash = GeoHashUtils.encode(lat, lng, 7);
        WarmEvent event = new WarmEvent();
        event.setUserId(userId);
        event.setTitle(safeTitle);
        event.setContent(safeContent);
        event.setGeohash(geohash);
        event.setCityCode(geohash.substring(0, 4));
        // DFA 敏感词命中 → AUTO_HIDDEN 不可见；未命中 → PENDING 先发后审
        boolean sensitive = contentSanitizer.containsSensitiveWord(safeTitle + safeContent);
        event.setAuditStatus(sensitive ? AuditStatus.AUTO_HIDDEN : AuditStatus.PENDING);
        event.setIsVisible(!sensitive);
        warmEventMapper.insert(event);

        double[] center = GeoHashUtils.decodeCenter(geohash);
        double[] offset = GeoHashUtils.deterministicOffset(center[0], center[1], event.getId());
        return new WarmEventVO(event.getId(), safeTitle, safeContent,
                round6(offset[0]), round6(offset[1]), 0, geohash.substring(0, 6),
                event.getCityCode(), null, event.getCreatedAt());
    }

    @Override
    public List<WarmEventVO> listWarmEvents(Double lat, Double lng, Integer radius, String cityCode) {
        double[] center = resolveCenter(lat, lng);
        int radiusM = resolveRadius(radius);
        List<WarmEvent> events = warmEventMapper.selectList(new LambdaQueryWrapper<WarmEvent>()
                .eq(WarmEvent::getIsVisible, true)
                .in(WarmEvent::getAuditStatus, AuditStatus.PENDING, AuditStatus.APPROVED)
                .isNull(WarmEvent::getDeletedAt)
                .isNotNull(WarmEvent::getGeohash));
        if (cityCode != null && !cityCode.isBlank()) {
            events = events.stream()
                    .filter(e -> cityCode.trim().equals(e.getCityCode()))
                    .toList();
        }

        List<WarmEventVO> result = new ArrayList<>();
        for (WarmEvent event : events) {
            double[] cellCenter = GeoHashUtils.decodeCenter(event.getGeohash());
            double distance = GeoHashUtils.distanceMeters(center[0], center[1], cellCenter[0], cellCenter[1]);
            if (distance > radiusM) {
                continue;
            }
            double[] offset = GeoHashUtils.deterministicOffset(cellCenter[0], cellCenter[1], event.getId());
            result.add(new WarmEventVO(event.getId(), event.getTitle(), event.getContent(),
                    round6(offset[0]), round6(offset[1]), (int) Math.round(distance),
                    event.getGeohash().substring(0, 6), event.getCityCode(), null, event.getCreatedAt()));
        }
        result.sort(Comparator.comparingInt(WarmEventVO::distance));
        return result;
    }

    @Override
    public List<MapClusterVO> clusterWarmEvents(Double lat, Double lng, Integer radius, String cityCode) {
        List<WarmEventVO> events = listWarmEvents(lat, lng, radius, cityCode);
        Map<String, List<WarmEventVO>> byGrid = new HashMap<>();
        for (WarmEventVO event : events) {
            byGrid.computeIfAbsent(event.geohash6(), k -> new ArrayList<>()).add(event);
        }
        List<MapClusterVO> clusters = new ArrayList<>();
        for (Map.Entry<String, List<WarmEventVO>> entry : byGrid.entrySet()) {
            double[] center = GeoHashUtils.decodeCenter(entry.getKey());
            clusters.add(new MapClusterVO(entry.getKey(), round6(center[0]), round6(center[1]),
                    entry.getValue().size()));
        }
        clusters.sort(Comparator.comparingInt(MapClusterVO::count).reversed());
        return clusters;
    }

    // ---------------- 管理端 ----------------

    @Override
    public List<WishFence> listFences(Long wishId) {
        LambdaQueryWrapper<WishFence> query = new LambdaQueryWrapper<>();
        if (wishId != null) {
            query.eq(WishFence::getWishId, wishId);
        }
        query.orderByDesc(WishFence::getId);
        return fenceMapper.selectList(query);
    }

    @Override
    @Transactional
    public WishFence createFence(SaveFenceCommand command) {
        validateFenceCommand(command);
        WishFence fence = new WishFence();
        applyFence(fence, command);
        fence.setCreatedBy(command.adminUserId());
        fenceMapper.insert(fence);
        log.info("围栏已创建, fenceId={}, wishId={}, adminUserId={}", fence.getId(), command.wishId(), command.adminUserId());
        return fence;
    }

    @Override
    @Transactional
    public WishFence updateFence(Long fenceId, SaveFenceCommand command) {
        validateFenceCommand(command);
        WishFence fence = fenceMapper.selectById(fenceId);
        if (fence == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "围栏不存在");
        }
        applyFence(fence, command);
        fenceMapper.updateById(fence);
        return fenceMapper.selectById(fenceId);
    }

    @Override
    @Transactional
    public void toggleFence(Long fenceId, boolean active) {
        WishFence fence = fenceMapper.selectById(fenceId);
        if (fence == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "围栏不存在");
        }
        WishFence update = new WishFence();
        update.setId(fence.getId());
        update.setIsActive(active);
        fenceMapper.updateById(update);
    }

    @Override
    @Transactional
    public void deleteFence(Long fenceId) {
        WishFence fence = fenceMapper.selectById(fenceId);
        if (fence == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "围栏不存在");
        }
        fenceMapper.deleteById(fenceId);
        log.info("围栏已删除, fenceId={}", fenceId);
    }

    @Override
    public List<WarmEvent> listWarmEventsForAdmin(String auditStatus, int page, int size) {
        LambdaQueryWrapper<WarmEvent> query = new LambdaQueryWrapper<>();
        if (auditStatus != null && !auditStatus.isBlank()) {
            try {
                query.eq(WarmEvent::getAuditStatus, AuditStatus.valueOf(auditStatus.trim()));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "非法的审核状态: " + auditStatus);
            }
        }
        query.orderByDesc(WarmEvent::getId);
        return warmEventMapper.selectList(query
                .last("LIMIT " + Math.max(1, size) + " OFFSET " + Math.max(0, (page - 1) * size)));
    }

    @Override
    @Transactional
    public WarmEvent auditWarmEvent(Long eventId, String auditStatus) {
        AuditStatus status;
        try {
            status = AuditStatus.valueOf(auditStatus.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "非法的审核状态: " + auditStatus);
        }
        WarmEvent event = warmEventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "温暖事件不存在");
        }
        event.setAuditStatus(status);
        // REJECTED/AUTO_HIDDEN 不可见；PENDING/APPROVED 可见（先发后审语义）
        event.setIsVisible(status == AuditStatus.PENDING || status == AuditStatus.APPROVED);
        warmEventMapper.updateById(event);
        log.info("温暖事件审核, eventId={}, status={}", eventId, status);
        return event;
    }

    // ---------------- 工具 ----------------

    private void applyFence(WishFence fence, SaveFenceCommand command) {
        fence.setName(command.name().trim());
        fence.setWishId(command.wishId());
        fence.setCenterGeohash(GeoHashUtils.encode(command.centerLat(), command.centerLng(), 7));
        fence.setRadiusM(command.radiusM());
        fence.setValidFrom(command.validFrom());
        fence.setValidTo(command.validTo());
        fence.setIsActive(command.isActive() == null || command.isActive());
    }

    private void validateFenceCommand(SaveFenceCommand command) {
        if (command.radiusM() == null || command.radiusM() < MIN_RADIUS_M) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "围栏半径最小 " + MIN_RADIUS_M + " 米");
        }
        if (command.centerLat() < -90.0 || command.centerLat() > 90.0
                || command.centerLng() < -180.0 || command.centerLng() > 180.0) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "围栏中心坐标越界");
        }
        if (command.validFrom() != null && command.validTo() != null
                && command.validFrom().isAfter(command.validTo())) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "生效开始不能晚于结束");
        }
    }

    private double[] resolveCenter(Double lat, Double lng) {
        if (isBlankCoordinate(lat, lng)) {
            return new double[]{mapProperties.getDefaultLat(), mapProperties.getDefaultLng()};
        }
        return new double[]{lat, lng};
    }

    private int resolveRadius(Integer radius) {
        if (radius == null || radius <= 0 || radius > MAX_RADIUS_M) {
            return DEFAULT_RADIUS_M;
        }
        return radius;
    }

    private boolean isBlankCoordinate(Double lat, Double lng) {
        return lat == null || lng == null
                || (lat == 0.0 && lng == 0.0)
                || lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0;
    }

    private double round6(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
