package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateCapsuleRequest;
import com.cloudmart.wish.entity.TimeCapsule;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.enums.CapsuleStatus;
import com.cloudmart.wish.mq.CapsuleEventProducer;
import com.cloudmart.wish.repository.TimeCapsuleMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.CapsuleService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.util.WishJsonUtils;
import com.cloudmart.wish.vo.CapsuleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 时间胶囊服务实现（文档 2.7 / 9.2 / 26.3，Sprint 2.4）。
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>到期判定唯一依据 UTC openAt（26.3 跨时区语义：用户旅行不影响到期，
 *       openAtTimezone 仅回溯展示/审计）</li>
 *   <li>内容防绕过：非 OPENED 状态 content/mediaUrls 恒不返回（安全测试要求），
 *       开启是唯一可见路径（拆信仪式感）</li>
 *   <li>并发安全：开启/取消/扫描流转均为单条 CAS UPDATE，天然幂等
 *       （并发双开仅一次生效；同一胶囊扫描 2 次仅推送 1 次通知）</li>
 *   <li>扫描批量 500 条/批循环至取尽（10000 条 &lt; 30s 验收）；逐条 CAS
 *       独立提交，MQ 事件在行级提交后发送（避免事务回滚后通知已外发）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CapsuleServiceImpl implements CapsuleService {

    /** 扫描批量（文档验收：分批 500 条） */
    static final int SCAN_BATCH_SIZE = 500;
    /** 自定义开启时间上界（文档边界：最大 10 年） */
    static final Duration MAX_OPEN_AHEAD = Duration.ofDays(3650);
    /** 到期判定容差：openAt 恰为当前时刻视为到期（含=） */
    private static final int PAGE_SIZE_DEFAULT = 20;
    private static final int PAGE_SIZE_MAX = 50;

    private final TimeCapsuleMapper timeCapsuleMapper;
    private final WishUserStatMapper wishUserStatMapper;
    private final UserStatService userStatService;
    private final CapsuleEventProducer capsuleEventProducer;
    private final WishContentSanitizer contentSanitizer;

    @Override
    public CapsuleVO createCapsule(Long userId, CreateCapsuleRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (!request.openAt().isAfter(now)) {
            throw new BusinessException(WishErrorCodes.WISH_OPEN_AT_PAST, "开启时间不能早于当前时间");
        }
        if (request.openAt().isAfter(now.plus(MAX_OPEN_AHEAD))) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "开启时间最远不能超过10年");
        }
        String timezone = requireValidIanaZone(request.openAtTz());
        String title = contentSanitizer.escapeHtml(request.title().trim());
        if (!contentSanitizer.isFreeOfPathTraversal(request.content())) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "胶囊内容包含非法字符");
        }

        TimeCapsule capsule = new TimeCapsule();
        capsule.setUserId(userId);
        capsule.setTitle(title);
        capsule.setContent(contentSanitizer.escapeHtml(request.content()));
        capsule.setMediaUrls(WishJsonUtils.stringifyList(request.mediaUrls()));
        capsule.setOpenAt(request.openAt());
        capsule.setOpenAtTimezone(timezone);
        capsule.setStatus(CapsuleStatus.SEALED);
        timeCapsuleMapper.insert(capsule);

        log.info("胶囊创建成功, capsuleId={}, userId={}, openAt={}", capsule.getId(), userId, capsule.getOpenAt());
        return toVO(capsule);
    }

    @Override
    public CapsulePage listMyCapsules(Long userId, String status, String cursor, Integer pageSize) {
        CapsuleStatus statusFilter = parseStatusOrNull(status);
        int safeSize = pageSize == null || pageSize < 1 ? PAGE_SIZE_DEFAULT : Math.min(pageSize, PAGE_SIZE_MAX);
        Long cursorId = parseCursor(cursor);

        LambdaQueryWrapper<TimeCapsule> wrapper = new LambdaQueryWrapper<TimeCapsule>()
                .eq(TimeCapsule::getUserId, userId)
                .orderByDesc(TimeCapsule::getId)
                .last("LIMIT " + (safeSize + 1));
        if (statusFilter != null) {
            wrapper.eq(TimeCapsule::getStatus, statusFilter);
        }
        if (cursorId != null) {
            wrapper.lt(TimeCapsule::getId, cursorId);
        }

        List<TimeCapsule> capsules = timeCapsuleMapper.selectList(wrapper);
        boolean hasMore = capsules.size() > safeSize;
        List<TimeCapsule> pageItems = hasMore ? capsules.subList(0, safeSize) : capsules;
        String nextCursor = hasMore && !pageItems.isEmpty()
                ? String.valueOf(pageItems.get(pageItems.size() - 1).getId()) : null;
        return new CapsulePage(pageItems.stream().map(this::toVO).toList(), nextCursor, hasMore);
    }

    @Override
    public CapsuleVO getCapsuleDetail(Long userId, Long capsuleId) {
        return toVO(requireOwnedCapsule(userId, capsuleId));
    }

    @Override
    public CapsuleVO openCapsule(Long userId, Long capsuleId) {
        TimeCapsule capsule = requireOwnedCapsule(userId, capsuleId);
        if (capsule.getStatus() == CapsuleStatus.OPENED) {
            // 幂等：重复点击开启直接返回已开启内容
            return toVO(capsule);
        }
        if (capsule.getStatus() == CapsuleStatus.CANCELLED) {
            throw new BusinessException(WishErrorCodes.WISH_CAPSULE_NOT_AVAILABLE, "胶囊已取消，无法开启");
        }

        // CAS：SEALED/AVAILABLE 且已到期 → OPENED（并发双开仅一次生效；
        // SEALED+已到期同样放行，容忍扫描间隙/时钟偏移的"创建即到期"边界）
        LocalDateTime now = LocalDateTime.now();
        int affected = timeCapsuleMapper.update(null,
                new LambdaUpdateWrapper<TimeCapsule>()
                        .eq(TimeCapsule::getId, capsuleId)
                        .in(TimeCapsule::getStatus, CapsuleStatus.SEALED, CapsuleStatus.AVAILABLE)
                        .le(TimeCapsule::getOpenAt, now)
                        .set(TimeCapsule::getStatus, CapsuleStatus.OPENED)
                        .set(TimeCapsule::getOpenedAt, now));
        if (affected == 0) {
            if (capsule.getOpenAt().isAfter(now)) {
                throw new BusinessException(WishErrorCodes.WISH_CAPSULE_NOT_AVAILABLE, "胶囊尚未到期，还不能开启");
            }
            // 并发已开启：重查返回内容
            return toVO(requireOwnedCapsule(userId, capsuleId));
        }

        log.info("胶囊开启成功, capsuleId={}, userId={}", capsuleId, userId);
        return toVO(requireOwnedCapsule(userId, capsuleId));
    }

    @Override
    public CapsuleVO cancelCapsule(Long userId, Long capsuleId) {
        TimeCapsule capsule = requireOwnedCapsule(userId, capsuleId);
        if (capsule.getStatus() == CapsuleStatus.OPENED) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "已开启的胶囊不可取消");
        }
        if (capsule.getStatus() == CapsuleStatus.CANCELLED) {
            return toVO(capsule);
        }
        int affected = timeCapsuleMapper.update(null,
                new LambdaUpdateWrapper<TimeCapsule>()
                        .eq(TimeCapsule::getId, capsuleId)
                        .in(TimeCapsule::getStatus, CapsuleStatus.SEALED, CapsuleStatus.AVAILABLE)
                        .set(TimeCapsule::getStatus, CapsuleStatus.CANCELLED));
        if (affected == 0) {
            return toVO(requireOwnedCapsule(userId, capsuleId));
        }
        log.info("胶囊已取消, capsuleId={}, userId={}", capsuleId, userId);
        return toVO(requireOwnedCapsule(userId, capsuleId));
    }

    @Override
    public ScanResult scanAvailableCapsules() {
        LocalDateTime now = LocalDateTime.now();
        int scannedTotal = 0;
        int availableTotal = 0;
        while (true) {
            List<TimeCapsule> batch = timeCapsuleMapper.selectList(
                    new LambdaQueryWrapper<TimeCapsule>()
                            .eq(TimeCapsule::getStatus, CapsuleStatus.SEALED)
                            .le(TimeCapsule::getOpenAt, now)
                            .orderByAsc(TimeCapsule::getOpenAt)
                            .last("LIMIT " + SCAN_BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            scannedTotal += batch.size();
            for (TimeCapsule capsule : batch) {
                // 逐条 CAS 独立提交（无外层事务）：流转成功才发通知 → 重复扫描天然去重
                int affected = timeCapsuleMapper.update(null,
                        new LambdaUpdateWrapper<TimeCapsule>()
                                .eq(TimeCapsule::getId, capsule.getId())
                                .eq(TimeCapsule::getStatus, CapsuleStatus.SEALED)
                                .set(TimeCapsule::getStatus, CapsuleStatus.AVAILABLE));
                if (affected == 1) {
                    availableTotal++;
                    capsuleEventProducer.publishCapsuleAvailable(capsule.getId(), capsule.getUserId(), capsule.getTitle());
                }
            }
            if (batch.size() < SCAN_BATCH_SIZE) {
                break;
            }
        }
        log.info("胶囊到期扫描完成, scanned={}, available={}", scannedTotal, availableTotal);
        return new ScanResult(scannedTotal, availableTotal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportTimezone(Long userId, String timezone, Integer offsetMinutes) {
        // Bean Validation 已兜底 offsetMinutes 非空与范围；此处仅校验 IANA 合法性
        String validZone = requireValidIanaZone(timezone);
        int affected = wishUserStatMapper.update(null,
                new LambdaUpdateWrapper<WishUserStat>()
                        .eq(WishUserStat::getUserId, userId)
                        .set(WishUserStat::getTimezone, validZone));
        if (affected == 0) {
            // 统计行不存在（未创建过心愿的用户）：初始化后重试，保证幂等落库
            userStatService.initUserStat(userId);
            wishUserStatMapper.update(null,
                    new LambdaUpdateWrapper<WishUserStat>()
                            .eq(WishUserStat::getUserId, userId)
                            .set(WishUserStat::getTimezone, validZone));
        }
        log.debug("时区上报成功, userId={}, timezone={}", userId, validZone);
        return Map.of("timezone", validZone, "updated", true);
    }

    @Override
    public Map<String, Object> getAdminStats() {
        Map<String, Object> stats = new HashMap<>();
        long total = timeCapsuleMapper.selectCount(null);
        stats.put("total", total);
        for (CapsuleStatus status : CapsuleStatus.values()) {
            stats.put(status.name().toLowerCase(Locale.ROOT),
                    timeCapsuleMapper.selectCount(new LambdaQueryWrapper<TimeCapsule>()
                            .eq(TimeCapsule::getStatus, status)));
        }
        LocalDateTime dayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        stats.put("todayCreated", timeCapsuleMapper.selectCount(new LambdaQueryWrapper<TimeCapsule>()
                .ge(TimeCapsule::getCreatedAt, dayStart)));
        return stats;
    }

    // ---------------- 私有方法 ----------------

    /** 归属校验：不存在/非本人统一 404（防存在性探测，与心愿域同模式）。 */
    private TimeCapsule requireOwnedCapsule(Long userId, Long capsuleId) {
        TimeCapsule capsule = timeCapsuleMapper.selectById(capsuleId);
        if (capsule == null || !capsule.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "胶囊不存在");
        }
        return capsule;
    }

    /** VO 转换：非 OPENED 状态内容恒为 null（防绕过）。 */
    private CapsuleVO toVO(TimeCapsule capsule) {
        boolean opened = capsule.getStatus() == CapsuleStatus.OPENED;
        return new CapsuleVO(
                capsule.getId(),
                capsule.getTitle(),
                opened ? capsule.getContent() : null,
                opened ? WishJsonUtils.parseStringList(capsule.getMediaUrls()) : null,
                capsule.getStatus().name(),
                capsule.getOpenAt(),
                capsule.getOpenAtTimezone(),
                capsule.getOpenedAt(),
                capsule.getCreatedAt()
        );
    }

    /** IANA 时区格式校验（非法抛 400）。 */
    private String requireValidIanaZone(String timezone) {
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException | IllegalArgumentException e) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "时区格式不正确（须为 IANA 如 Asia/Shanghai）");
        }
    }

    private CapsuleStatus parseStatusOrNull(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CapsuleStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "胶囊状态过滤值不合法: " + status);
        }
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "分页游标不合法");
        }
    }
}
