package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.config.WishMapProperties;
import com.cloudmart.wish.entity.EncounterLetter;
import com.cloudmart.wish.entity.LbsFreeze;
import com.cloudmart.wish.entity.LbsSuspicious;
import com.cloudmart.wish.entity.LetterInteraction;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.EncounterLetterStatus;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.mq.EncounterEventProducer;
import com.cloudmart.wish.repository.EncounterLetterMapper;
import com.cloudmart.wish.repository.LbsFreezeMapper;
import com.cloudmart.wish.repository.LbsSuspiciousMapper;
import com.cloudmart.wish.repository.LetterInteractionMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.EncounterService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.util.GeoHashUtils;
import com.cloudmart.wish.vo.EncounterLetterVO;
import com.cloudmart.wish.vo.MapClusterVO;
import com.cloudmart.wish.util.WishJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 擦肩而过服务实现（Sprint 3.3）。
 *
 * <p>轨迹 Redis 布局：lbs:trace:{bucketEpochMin}:{geohash6} →
 * HSET userId→tagsJSON，TTL 25h（24h 数据期 + 1h 桶宽）；附近模式开关
 * lbs:mode:{userId} TTL 24h（上报续期）；最近轨迹 lbs:last:{userId}
 * （geohash7 中心 + 时间戳，供伪造检测，无原始坐标）。</p>
 *
 * <p>伪造检测：速度 >15km/h → 可疑记录（hub geohash4 放宽）；
 * 24h 内连续 3 次 → 冻结 24h（DB）+ 清除附近模式。频率限制：
 * 5 分钟滑动 >10 次 → 429。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EncounterServiceImpl implements EncounterService {

    private static final String TRACE_KEY = "lbs:trace:";
    private static final String MODE_KEY = "lbs:mode:";
    private static final String LAST_KEY = "lbs:last:";
    private static final String RATE_KEY = "lbs:rate:";
    private static final Duration TRACE_TTL = Duration.ofHours(25);
    private static final Duration MODE_TTL = Duration.ofHours(24);
    private static final int BUCKET_MINUTES = 30;
    private static final int RATE_WINDOW_MINUTES = 5;
    private static final int RATE_LIMIT = 10;
    private static final int SUSPICIOUS_FREEZE_THRESHOLD = 3;
    private static final int LIGHT_COST = 2;

    private final EncounterLetterMapper letterMapper;
    private final LetterInteractionMapper interactionMapper;
    private final LbsSuspiciousMapper suspiciousMapper;
    private final LbsFreezeMapper freezeMapper;
    private final WishMapper wishMapper;
    private final UserStatService userStatService;
    private final WishMapProperties mapProperties;
    private final EncounterEventProducer encounterEventProducer;
    private final StringRedisTemplate redisTemplate;

    private static final ObjectMapper JSON = new ObjectMapper();

    // ---------------- 附近模式 ----------------

    @Override
    public void setNearbyMode(Long userId, boolean enabled) {
        try {
            if (enabled) {
                redisTemplate.opsForValue().set(MODE_KEY + userId, "1", MODE_TTL);
            } else {
                redisTemplate.delete(MODE_KEY + userId);
            }
        } catch (DataAccessException ex) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "开关设置失败，请稍后重试");
        }
    }

    private boolean isModeEnabled(Long userId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(MODE_KEY + userId));
        } catch (DataAccessException ex) {
            return false;
        }
    }

    // ---------------- 轨迹上报 ----------------

    @Override
    public void reportTrace(Long userId, Double lat, Double lng) {
        // 冻结检查（验收：连续 3 次可疑冻结 24h）
        LbsFreeze freeze = freezeMapper.selectOne(new LambdaQueryWrapper<LbsFreeze>()
                .eq(LbsFreeze::getUserId, userId)
                .gt(LbsFreeze::getFrozenUntil, LocalDateTime.now(ZoneId.of("UTC")))
                .last("LIMIT 1"));
        if (freeze != null) {
            throw new BusinessException(WishErrorCodes.WISH_FORBIDDEN, "位置服务已被临时冻结");
        }
        if (!isModeEnabled(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_FORBIDDEN, "附近模式未开启");
        }
        if (lat == null || lng == null || (lat == 0.0 && lng == 0.0)
                || lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "无效的定位坐标");
        }

        // 频率限制：5 分钟窗口 >10 次 → 429（验收）
        try {
            Long count = redisTemplate.opsForValue().increment(RATE_KEY + userId);
            if (count != null && count == 1L) {
                redisTemplate.expire(RATE_KEY + userId, Duration.ofMinutes(RATE_WINDOW_MINUTES));
            }
            if (count != null && count > RATE_LIMIT) {
                throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED, "上报太频繁，请稍后再试");
            }
        } catch (DataAccessException ex) {
            log.warn("上报限频 Redis 异常（Fail-Open）: {}", ex.getMessage());
        }

        LocalDateTime nowUtc = LocalDateTime.now(ZoneId.of("UTC"));
        String cell7 = GeoHashUtils.encode(lat, lng, 7);
        double[] cellCenter = GeoHashUtils.decodeCenter(cell7);
        String cell6 = GeoHashUtils.encode(lat, lng, 6);

        // 伪造检测（验收：1 分钟 2km >15km/h → 可疑；枢纽放宽；连续 3 次 → 冻结）
        detectSpoofing(userId, cell7, cellCenter, nowUtc);

        // 轨迹入库（Redis）：geohash6 + 30 分钟桶 + 公开心愿标签；原始坐标丢弃
        String tagsJson = collectPublicTags(userId);
        String bucketKey = bucketKey(nowUtc, cell6);
        try {
            redisTemplate.opsForHash().put(bucketKey, String.valueOf(userId), tagsJson);
            redisTemplate.expire(bucketKey, TRACE_TTL);
            // 最近轨迹（伪造检测用；仅存 geohash7 中心，无原始坐标）
            redisTemplate.opsForValue().set(LAST_KEY + userId,
                    cellCenter[0] + "," + cellCenter[1] + "," + nowUtc.toEpochSecond(java.time.ZoneOffset.UTC),
                    TRACE_TTL);
        } catch (DataAccessException ex) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "轨迹上报失败，请稍后重试");
        }
    }

    /** 伪造检测：可疑 → 记录；24h 内第 3 次 → 冻结 24h 并关闭附近模式 */
    private void detectSpoofing(Long userId, String currentCell7, double[] currentCenter, LocalDateTime nowUtc) {
        String last;
        try {
            last = redisTemplate.opsForValue().get(LAST_KEY + userId);
        } catch (DataAccessException ex) {
            return;
        }
        if (last == null || last.isBlank()) {
            return;
        }
        String[] parts = last.split(",");
        if (parts.length != 3) {
            return;
        }
        try {
            double lastLat = Double.parseDouble(parts[0]);
            double lastLng = Double.parseDouble(parts[1]);
            long lastTs = Long.parseLong(parts[2]);
            long durationMinutes = Math.max(1,
                    (nowUtc.toEpochSecond(java.time.ZoneOffset.UTC) - lastTs) / 60);
            double distance = GeoHashUtils.distanceMeters(lastLat, lastLng, currentCenter[0], currentCenter[1]);
            Integer speedKmh = EncounterMatcher.spoofingVerdict(distance, durationMinutes,
                    lastCellOf(lastLat, lastLng), currentCell7, mapProperties.getHubGeohash4());
            if (speedKmh == null) {
                return;
            }
            // 可疑记录 + 冻结判定
            LbsSuspicious record = new LbsSuspicious();
            record.setUserId(userId);
            record.setSpeedKmh(speedKmh);
            record.setFromCell(lastCellOf(lastLat, lastLng));
            record.setToCell(currentCell7);
            suspiciousMapper.insert(record);
            long recent = suspiciousMapper.selectCount(new LambdaQueryWrapper<LbsSuspicious>()
                    .eq(LbsSuspicious::getUserId, userId)
                    .gt(LbsSuspicious::getCreatedAt, nowUtc.minusHours(24)));
            if (recent >= SUSPICIOUS_FREEZE_THRESHOLD) {
                LbsFreeze freeze = freezeMapper.selectOne(new LambdaQueryWrapper<LbsFreeze>()
                        .eq(LbsFreeze::getUserId, userId)
                        .last("LIMIT 1"));
                LocalDateTime until = nowUtc.plusHours(24);
                if (freeze == null) {
                    LbsFreeze insert = new LbsFreeze();
                    insert.setUserId(userId);
                    insert.setReason("24h 内连续 " + recent + " 次位置异常跳跃");
                    insert.setFrozenUntil(until);
                    freezeMapper.insert(insert);
                } else {
                    LbsFreeze update = new LbsFreeze();
                    update.setId(freeze.getId());
                    update.setFrozenUntil(until);
                    update.setReason("24h 内连续 " + recent + " 次位置异常跳跃");
                    freezeMapper.updateById(update);
                }
                redisTemplate.delete(MODE_KEY + userId);
                log.warn("LBS 冻结, userId={}, suspiciousCount={}", userId, recent);
            } else {
                log.info("位置可疑标记, userId={}, speedKmh={}", userId, speedKmh);
            }
        } catch (NumberFormatException ignored) {
            // 旧格式数据，忽略
        }
    }

    private String lastCellOf(double lat, double lng) {
        return GeoHashUtils.encode(lat, lng, 7);
    }

    // ---------------- 匹配 + 投递 ----------------

    @Override
    public EncounterService.MatchStats matchAndDeliver() {
        LocalDateTime nowUtc = LocalDateTime.now(ZoneId.of("UTC"));
        // SCAN 收集：cell → (bucket → {userId: tagsJson})
        Map<String, Map<String, Map<Long, String>>> cells = new HashMap<>();
        Set<String> deadKeys = new HashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(TRACE_KEY + "*").count(500).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Map<Object, Object> members = redisTemplate.opsForHash().entries(key);
                if (members.isEmpty()) {
                    deadKeys.add(key);
                    continue;
                }
                String suffix = key.substring(TRACE_KEY.length());
                int split = suffix.lastIndexOf(':');
                if (split <= 0) {
                    continue;
                }
                String cell = suffix.substring(split + 1);
                String bucketKey = suffix.substring(0, split);
                Map<String, Map<Long, String>> buckets = cells.computeIfAbsent(cell, k -> new HashMap<>());
                Map<Long, String> users = buckets.computeIfAbsent(bucketKey, k -> new HashMap<>());
                for (Map.Entry<Object, Object> entry : members.entrySet()) {
                    users.put(Long.parseLong(String.valueOf(entry.getKey())), String.valueOf(entry.getValue()));
                }
            }
        } catch (DataAccessException ex) {
            log.warn("轨迹 SCAN 失败（本轮匹配跳过）: {}", ex.getMessage());
            return new EncounterService.MatchStats(0, 0, 0);
        }

        int generated = 0;
        Set<String> seenPairs = new HashSet<>();
        for (Map.Entry<String, Map<String, Map<Long, String>>> cellEntry : cells.entrySet()) {
            String cell6 = cellEntry.getKey();
            Map<String, Map<Long, String>> buckets = cellEntry.getValue();
            List<String> bucketKeys = new ArrayList<>(buckets.keySet());
            bucketKeys.sort(Comparator.naturalOrder());
            for (int i = 0; i < bucketKeys.size(); i++) {
                String bucketKey = bucketKeys.get(i);
                Map<Long, String> pool = new HashMap<>(buckets.get(bucketKey));
                // 相邻桶并入候选池（桶差 ≤1 桶）
                if (i + 1 < bucketKeys.size()) {
                    pool.putAll(buckets.get(bucketKeys.get(i + 1)));
                }
                if (i > 0) {
                    pool.putAll(buckets.get(bucketKeys.get(i - 1)));
                }
                // 匿名人群阈值（39.9）：合并池用户数 < 5 不生成
                if (!EncounterMatcher.meetsAnonCrowdThreshold(pool.size())) {
                    continue;
                }
                LocalDateTime bucketTime = parseBucket(bucketKey);
                generated += pairWithinPool(cell6, bucketTime, bucketKey, pool, seenPairs, nowUtc);
            }
        }

        // 投递：到期 PENDING → DELIVERED + 匿名通知
        int delivered = deliverDue(nowUtc);

        // 兜底清理空 key
        if (!deadKeys.isEmpty()) {
            redisTemplate.delete(deadKeys);
        }
        EncounterService.MatchStats stats = new EncounterService.MatchStats(cells.size(), generated, delivered);
        log.info("擦肩而过匹配完成, cells={}, generated={}, delivered={}",
                cells.size(), generated, delivered);
        return stats;
    }

    /** 池内两两配对（tags 交集非空 + 镜像去重），生成两条镜像信笺 */
    private int pairWithinPool(String cell6, LocalDateTime bucketTime, String bucketKey,
                               Map<Long, String> pool, Set<String> seenPairs, LocalDateTime nowUtc) {
        int generated = 0;
        List<Long> users = new ArrayList<>(pool.keySet());
        for (int a = 0; a < users.size(); a++) {
            for (int b = a + 1; b < users.size(); b++) {
                Long userA = users.get(a);
                Long userB = users.get(b);
                String pairKey = cell6 + ":" + bucketKey + ":" + Math.min(userA, userB) + ":" + Math.max(userA, userB);
                if (seenPairs.contains(pairKey)) {
                    continue;
                }
                if (!EncounterMatcher.tagsIntersect(pool.get(userA), pool.get(userB))) {
                    continue;
                }
                seenPairs.add(pairKey);
                generated += createMirrorLetters(userA, userB, cell6, bucketTime, nowUtc,
                        pool.get(userA), pool.get(userB));
            }
        }
        return generated;
    }

    /** 生成两条镜像信笺（uk_letter_pair 幂等；deliver_after 随机 1-24h） */
    private int createMirrorLetters(Long userA, Long userB, String cell6, LocalDateTime bucketTime,
                                    LocalDateTime nowUtc, String tagsA, String tagsB) {
        LocalDateTime encounterTime = bucketTime.plusMinutes(BUCKET_MINUTES / 2L);
        int created = 0;
        created += createLetter(userA, userB, cell6, bucketTime, encounterTime, tagsB, nowUtc);
        created += createLetter(userB, userA, cell6, bucketTime, encounterTime, tagsA, nowUtc);
        return created;
    }

    private int createLetter(Long owner, Long peer, String cell6, LocalDateTime bucketTime,
                             LocalDateTime encounterTime, String peerTags, LocalDateTime nowUtc) {
        Long peerWishId = latestPublicWishId(peer);
        if (peerWishId == null) {
            return 0;
        }
        // 重叠标签在配对时已确认非空；信笺文案取对方标签首个
        String peerFirstTag = EncounterMatcher.parseTags(peerTags).stream().findFirst().orElse("同一个愿望");
        String content = "在「" + peerFirstTag + "」的路上，"
                + encounterTime.toLocalDate() + " " + encounterTime.toLocalTime().withSecond(0)
                + " 你和 TA 在同一片星空下许愿";
        EncounterLetter letter = new EncounterLetter();
        letter.setOwnerUserId(owner);
        letter.setPeerUserId(peer);
        letter.setPeerWishId(peerWishId);
        letter.setGeohash6(cell6);
        letter.setTimeBucket(bucketTime);
        letter.setWishTags(WishJsonUtils.stringifyList(List.of(peerFirstTag)));
        letter.setEncounterTime(encounterTime);
        letter.setContent(content);
        letter.setStatus(EncounterLetterStatus.PENDING);
        letter.setDeliverAfter(nowUtc.plusMinutes(ThreadLocalRandom.current().nextLong(60, 24 * 60)));
        try {
            letterMapper.insert(letter);
            return 1;
        } catch (DuplicateKeyException ex) {
            // 同对用户同格同桶重复匹配：幂等跳过
            return 0;
        }
    }

    /** 投递：deliver_after 到期 PENDING → DELIVERED + 匿名通知 */
    private int deliverDue(LocalDateTime nowUtc) {
        List<EncounterLetter> due = letterMapper.selectList(new LambdaQueryWrapper<EncounterLetter>()
                .eq(EncounterLetter::getStatus, EncounterLetterStatus.PENDING)
                .le(EncounterLetter::getDeliverAfter, nowUtc)
                .last("LIMIT 200"));
        for (EncounterLetter letter : due) {
            letterMapper.update(null, new LambdaUpdateWrapper<EncounterLetter>()
                    .set(EncounterLetter::getStatus, EncounterLetterStatus.DELIVERED)
                    .set(EncounterLetter::getDeliveredAt, nowUtc)
                    .eq(EncounterLetter::getId, letter.getId())
                    .eq(EncounterLetter::getStatus, EncounterLetterStatus.PENDING));
            encounterEventProducer.publishLetterDelivered(letter.getOwnerUserId(), letter.getId());
        }
        return due.size();
    }

    // ---------------- 清理 ----------------

    @Override
    public EncounterService.CleanupStats cleanupTraces() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneId.of("UTC")).minusHours(24);
        int live = 0;
        int deleted = 0;
        List<String> expired = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(TRACE_KEY + "*").count(500).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String suffix = key.substring(TRACE_KEY.length());
                int split = suffix.lastIndexOf(':');
                if (split <= 0) {
                    continue;
                }
                LocalDateTime bucketTime = parseBucket(suffix.substring(0, split));
                if (bucketTime.isBefore(cutoff)) {
                    expired.add(key);
                    deleted++;
                } else {
                    live++;
                }
            }
        } catch (DataAccessException ex) {
            log.warn("轨迹清理 SCAN 失败: {}", ex.getMessage());
            return new EncounterService.CleanupStats(0, 0);
        }
        if (!expired.isEmpty()) {
            redisTemplate.delete(expired);
        }
        log.info("轨迹清理完成, live={}, deleted={}", live, deleted);
        return new EncounterService.CleanupStats(live, deleted);
    }

    // ---------------- 管理端：伪造检测面板 ----------------

    @Override
    public List<LbsSuspicious> listSuspicious(Long userId) {
        LambdaQueryWrapper<LbsSuspicious> query = new LambdaQueryWrapper<>();
        if (userId != null) {
            query.eq(LbsSuspicious::getUserId, userId);
        }
        query.orderByDesc(LbsSuspicious::getId);
        return suspiciousMapper.selectList(query.last("LIMIT 200"));
    }

    @Override
    public List<LbsFreeze> listFreezes() {
        return freezeMapper.selectList(new LambdaQueryWrapper<LbsFreeze>()
                .gt(LbsFreeze::getFrozenUntil, LocalDateTime.now(ZoneId.of("UTC")))
                .orderByDesc(LbsFreeze::getId));
    }

    @Override
    @Transactional
    public void unfreeze(Long userId) {
        freezeMapper.delete(new LambdaQueryWrapper<LbsFreeze>()
                .eq(LbsFreeze::getUserId, userId));
        log.info("LBS 解冻, userId={}", userId);
    }

    // ---------------- 信笺列表 / 拆信 / 互动 ----------------

    @Override
    public List<EncounterLetterVO> listLetters(Long userId) {
        List<EncounterLetter> letters = letterMapper.selectList(new LambdaQueryWrapper<EncounterLetter>()
                .eq(EncounterLetter::getOwnerUserId, userId)
                .orderByDesc(EncounterLetter::getDeliverAfter)
                .last("LIMIT 100"));
        List<EncounterLetterVO> result = new ArrayList<>();
        for (EncounterLetter letter : letters) {
            result.add(new EncounterLetterVO(
                    letter.getId(),
                    EncounterMatcher.parseTags(letter.getWishTags()).stream().toList(),
                    letter.getEncounterTime(),
                    letter.getGeohash6(),
                    letter.getStatus().name(),
                    // 契约：PENDING 时 content 返回 null
                    letter.getStatus() == EncounterLetterStatus.PENDING ? null : letter.getContent(),
                    letter.getDeliveredAt()));
        }
        return result;
    }

    @Override
    public EncounterLetterVO markRead(Long userId, Long letterId) {
        EncounterLetter letter = requireOwnedLetter(userId, letterId);
        if (letter.getStatus() == EncounterLetterStatus.PENDING) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "信笺还未送达");
        }
        if (letter.getStatus() == EncounterLetterStatus.DELIVERED) {
            letterMapper.update(null, new LambdaUpdateWrapper<EncounterLetter>()
                    .set(EncounterLetter::getStatus, EncounterLetterStatus.READ)
                    .set(EncounterLetter::getReadAt, LocalDateTime.now(ZoneId.of("UTC")))
                    .eq(EncounterLetter::getId, letterId)
                    .eq(EncounterLetter::getStatus, EncounterLetterStatus.DELIVERED));
            letter.setStatus(EncounterLetterStatus.READ);
        }
        return toVo(letter);
    }

    @Override
    @Transactional
    public EncounterLetterVO interact(Long userId, Long letterId, String type, String content) {
        EncounterLetter letter = requireOwnedLetter(userId, letterId);
        if (letter.getStatus() == EncounterLetterStatus.PENDING) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "信笺还未送达");
        }
        boolean isLight = "LIGHT".equalsIgnoreCase(type);
        if (!isLight && !"BLESS".equalsIgnoreCase(type)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "互动类型非法");
        }

        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        LetterInteraction interaction = new LetterInteraction();
        interaction.setLetterId(letterId);
        interaction.setUserId(userId);
        interaction.setType(isLight ? "LIGHT" : "BLESS");
        interaction.setPeerWishId(letter.getPeerWishId());
        interaction.setInteractDate(today);
        try {
            interactionMapper.insert(interaction);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED, "这封信笺今天已经互动过啦");
        }

        if (isLight) {
            // 点亮对方心愿：扣星光 2（LIGHT_OTHER 流水）+ 对方心愿 light_count+1
            int credited = userStatService.spendStarlight(userId, LIGHT_COST,
                    com.cloudmart.wish.enums.ResourceLogSource.LIGHT_OTHER, letter.getId());
            if (credited < LIGHT_COST) {
                throw new BusinessException(WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT, "星光不足，无法点亮");
            }
            wishMapper.update(null, new LambdaUpdateWrapper<Wish>()
                    .setSql("light_count = light_count + 1")
                    .eq(Wish::getId, letter.getPeerWishId()));
        }
        // 匿名通知对方（不含 letterId/userId，无法反查——验收）
        encounterEventProducer.publishAnonInteraction(letter.getPeerUserId(), isLight);

        return toVo(letter);
    }

    // ---------------- 工具 ----------------

    private EncounterLetter requireOwnedLetter(Long userId, Long letterId) {
        EncounterLetter letter = letterMapper.selectById(letterId);
        if (letter == null || !letter.getOwnerUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "信笺不存在");
        }
        return letter;
    }

    private EncounterLetterVO toVo(EncounterLetter letter) {
        return new EncounterLetterVO(
                letter.getId(),
                EncounterMatcher.parseTags(letter.getWishTags()).stream().toList(),
                letter.getEncounterTime(),
                letter.getGeohash6(),
                letter.getStatus().name(),
                letter.getStatus() == EncounterLetterStatus.PENDING ? null : letter.getContent(),
                letter.getDeliveredAt());
    }

    private Long latestPublicWishId(Long userId) {
        Wish wish = wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                        .eq(Wish::getUserId, userId)
                        .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                        .eq(Wish::getStatus, WishStatus.ACTIVE)
                        .eq(Wish::getAuditStatus, com.cloudmart.wish.enums.AuditStatus.APPROVED)
                        .eq(Wish::getIsVisible, true)
                        .orderByDesc(Wish::getId)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        return wish == null ? null : wish.getId();
    }

    /** 用户当前公开 ACTIVE 心愿标签并集（JSON 数组，供轨迹匹配） */
    private String collectPublicTags(Long userId) {
        List<Wish> wishes = wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                .eq(Wish::getUserId, userId)
                .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                .eq(Wish::getStatus, WishStatus.ACTIVE)
                .isNotNull(Wish::getTags)
                .last("LIMIT 20"));
        Set<String> tags = new HashSet<>();
        for (Wish wish : wishes) {
            tags.addAll(WishJsonUtils.parseStringList(wish.getTags()));
        }
        return WishJsonUtils.stringifyList(new ArrayList<>(tags));
    }

    private String bucketKey(LocalDateTime time, String cell6) {
        LocalDateTime bucket = EncounterMatcher.bucketOf(time);
        long epochMin = bucket.toEpochSecond(java.time.ZoneOffset.UTC) / 60;
        return TRACE_KEY + epochMin + ":" + cell6;
    }

    private LocalDateTime parseBucket(String bucketKey) {
        long epochMin = Long.parseLong(bucketKey);
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(epochMin * 60), ZoneId.of("UTC"));
    }
}
