package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.LeaderboardConfigService;
import com.cloudmart.wish.service.LeaderboardService;
import com.cloudmart.wish.vo.LeaderboardEntryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 排行榜服务实现（Sprint 2.7，文档 2.9/2.7：热门/温暖/坚持/星火四榜单，
 * Redis ZSet，每 10 分钟刷新，Top 100）。
 *
 * <p>数据源：HOT=wish.light_count / WARM=wish.bless_count（心愿维度，
 * 排心愿）；PERSISTENCE=total_checkin_days / SPARK=total_helped（用户维度，
 * 排用户）。同分处理：Top N 邻域内按创建时间升序（早在前，配置可切降序），
 * ZSet 单分数无法表达二级排序，读取时批量取 created_at 内存稳定排序。
 * 排名变化动效：刷新前 ZSet RENAME 为 prev 快照，读取时 ZREVRANK(prev)
 * 对比当前排名得出升降（新上榜为 NEW）。</p>
 *
 * <p>刷新失败重试 3 次（指数间隔由调用方调度周期兜底）后 ERROR 告警；
 * 读路径 Redis 异常 Fail-Open 返回空列表（榜单为展示性数据）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardServiceImpl implements LeaderboardService {

    /** Redis Key：lb:rank:{board} 当前榜 / lb:prev:{board} 上期快照 */
    private static final String RANK_KEY_PREFIX = "lb:rank:";
    private static final String PREV_KEY_PREFIX = "lb:prev:";
    /** 构建 ZSet 的候选放大系数（封禁过滤后仍能凑满 Top N） */
    private static final int CANDIDATE_FACTOR = 2;

    private final WishMapper wishMapper;
    private final WishUserStatMapper userStatMapper;
    private final UserFeignClient userFeignClient;
    private final LeaderboardConfigService configService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void refreshAll() {
        for (LeaderboardType type : LeaderboardType.values()) {
            refreshBoard(type);
        }
    }

    @Override
    public List<LeaderboardEntryVO> getBoard(LeaderboardType type, int limit) {
        String rankKey = RANK_KEY_PREFIX + type.name();
        String prevKey = PREV_KEY_PREFIX + type.name();
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(rankKey, 0, Math.max(0, limit - 1) * 2L);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }
            // 拉取 2 倍候选做同分内存排序后再截断（同分按 created_at，早在前）
            List<ZSetOperations.TypedTuple<String>> ordered = tuples.stream()
                    .sorted(Comparator
                            .comparingDouble((ZSetOperations.TypedTuple<String> tuple) -> tuple.getScore())
                            .reversed())
                    .toList();

            Set<Long> memberIds = new LinkedHashSet<>();
            Map<Long, Double> scores = new HashMap<>();
            for (ZSetOperations.TypedTuple<String> tuple : ordered) {
                Long id = parseId(tuple.getValue());
                if (id == null) {
                    continue;
                }
                memberIds.add(id);
                scores.put(id, tuple.getScore() == null ? 0.0 : tuple.getScore());
            }

            Map<Long, Wish> wishById = new HashMap<>();
            Map<Long, WishUserStat> statById = new HashMap<>();
            if (type.isWishBoard()) {
                wishById.putAll(wishMapper.selectBatchIds(memberIds).stream()
                        .collect(Collectors.toMap(Wish::getId, w -> w, (a, b) -> a)));
            } else {
                statById.putAll(userStatMapper.selectBatchIds(memberIds).stream()
                        .collect(Collectors.toMap(WishUserStat::getUserId, s -> s, (a, b) -> a)));
            }

            // 同分稳定排序：分数降序 → created_at 升序（配置可切降序）
            boolean tieAsc = !"CREATED_AT_DESC".equalsIgnoreCase(
                    configService.getStringConfig("lb.tiebreak", "CREATED_AT_ASC"));
            Comparator<Long> tieBreak = Comparator.comparing((Long id) -> resolveCreatedAt(id, type, wishById, statById),
                    tieAsc ? Comparator.naturalOrder() : Comparator.reverseOrder());

            List<Long> sorted = memberIds.stream()
                    .sorted(Comparator
                            .comparing((Long id) -> scores.getOrDefault(id, 0.0)).reversed()
                            .thenComparing(tieBreak))
                    .limit(limit)
                    .toList();

            // 昵称/头像批量补齐（Fail-Open 占位）
            List<Long> displayUserIds = sorted.stream()
                    .map(id -> type.isWishBoard()
                            ? (wishById.get(id) != null ? wishById.get(id).getUserId() : null)
                            : id)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Long, String[]> briefs = fetchBriefs(displayUserIds);

            List<LeaderboardEntryVO> entries = new ArrayList<>();
            int rank = 1;
            for (Long memberId : sorted) {
                long currentRank = rank++;
                Long prevRank = prevRankOf(prevKey, memberId);
                String delta = prevRank == null ? "NEW" : (prevRank - currentRank > 0 ? "UP"
                        : (prevRank - currentRank < 0 ? "DOWN" : "FLAT"));
                entries.add(buildEntry(type, memberId, currentRank, delta, wishById, statById, scores, briefs));
            }
            return entries;
        } catch (DataAccessException ex) {
            // 读路径 Fail-Open：榜单为展示性数据，Redis 异常返回空列表不报错
            log.warn("排行榜读取 Redis 异常（Fail-Open 空列表）, type={}: {}", type, ex.getMessage());
            return List.of();
        }
    }

    /**
     * 重建单榜：候选 SQL Top 2N → 封禁过滤 → ZSet 重建（RENAME 留 prev 快照）。
     * 幂等：重复执行结果一致（验收：重复执行排行榜不变）。
     */
    private void refreshBoard(LeaderboardType type) {
        int topSize = configService.getIntConfig("lb.top_size", 100);
        boolean excludeRestricted = configService.getIntConfig("lb.exclude_restricted", 1) == 1;
        int candidate = topSize * CANDIDATE_FACTOR;

        List<long[]> rows; // {memberId, score}
        if (type.isWishBoard()) {
            boolean byLight = type == LeaderboardType.HOT;
            rows = wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                            .eq(Wish::getIsVisible, true)
                            .eq(Wish::getAuditStatus, AuditStatus.APPROVED)
                            .isNull(Wish::getDeletedAt)
                            .gt(byLight ? Wish::getLightCount : Wish::getBlessCount, 0)
                            .orderByDesc(byLight ? Wish::getLightCount : Wish::getBlessCount)
                            .orderByAsc(Wish::getCreatedAt)
                            .last("LIMIT " + candidate))
                    .stream()
                    .map(w -> new long[]{w.getId(), byLight ? w.getLightCount() : w.getBlessCount()})
                    .toList();
        } else {
            LambdaQueryWrapper<WishUserStat> query = new LambdaQueryWrapper<>();
            if (excludeRestricted) {
                query.eq(WishUserStat::getIsRestricted, false);
            }
            boolean byCheckin = type == LeaderboardType.PERSISTENCE;
            query.gt(byCheckin ? WishUserStat::getTotalCheckinDays : WishUserStat::getTotalHelped, 0)
                    .orderByDesc(byCheckin ? WishUserStat::getTotalCheckinDays : WishUserStat::getTotalHelped)
                    .orderByAsc(WishUserStat::getCreatedAt)
                    .last("LIMIT " + candidate);
            rows = userStatMapper.selectList(query).stream()
                    .map(s -> new long[]{s.getUserId(),
                            byCheckin ? s.getTotalCheckinDays() : s.getTotalHelped()})
                    .toList();
        }

        if (rows.isEmpty()) {
            // 空数据：清空当前榜（读路径返回空数组，验收：不报错）
            redisTemplate.delete(RANK_KEY_PREFIX + type.name());
            return;
        }

        // 封禁过滤（用户维度已 SQL 过滤；心愿维度补查作者封禁态）
        if (type.isWishBoard() && excludeRestricted) {
            rows = filterRestrictedAuthors(rows, topSize);
        }
        List<long[]> finalRows = rows.size() > topSize ? rows.subList(0, topSize) : rows;

        String rankKey = RANK_KEY_PREFIX + type.name();
        String prevKey = PREV_KEY_PREFIX + type.name();
        // 旧榜原子转 prev 快照（首刷无旧榜，RENAME 报错忽略）
        try {
            redisTemplate.rename(rankKey, prevKey);
        } catch (DataAccessException ignored) {
            // 首次刷新没有旧榜
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        for (long[] row : finalRows) {
            tuples.add(ZSetOperations.TypedTuple.of(String.valueOf(row[0]), (double) row[1]));
        }
        redisTemplate.opsForZSet().add(rankKey, tuples);
        log.debug("排行榜已刷新, type={}, entries={}", type, finalRows.size());
    }

    /** 心愿维度封禁过滤：批量查作者 is_restricted，逐批裁剪至凑满 topSize */
    private List<long[]> filterRestrictedAuthors(List<long[]> rows, int topSize) {
        List<long[]> filtered = new ArrayList<>();
        Set<Long> checked = new HashSet<>();
        for (long[] row : rows) {
            if (filtered.size() >= topSize) {
                break;
            }
            Wish wish = wishMapper.selectById(row[0]);
            if (wish == null) {
                continue;
            }
            Long authorId = wish.getUserId();
            if (!checked.contains(authorId)) {
                checked.add(authorId);
            }
            WishUserStat stat = userStatMapper.selectById(authorId);
            if (stat == null || !Boolean.TRUE.equals(stat.getIsRestricted())) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private Long prevRankOf(String prevKey, Long memberId) {
        try {
            Long rank = redisTemplate.opsForZSet().reverseRank(prevKey, String.valueOf(memberId));
            return rank == null ? null : rank + 1;
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private LeaderboardEntryVO buildEntry(LeaderboardType type, Long memberId, long rank, String delta,
                                          Map<Long, Wish> wishById, Map<Long, WishUserStat> statById,
                                          Map<Long, Double> scores, Map<Long, String[]> briefs) {
        Long userId;
        Map<String, Object> extra = new HashMap<>();
        if (type.isWishBoard()) {
            Wish wish = wishById.get(memberId);
            userId = wish != null ? wish.getUserId() : null;
            if (wish != null) {
                extra.put("wishTitle", wish.getTitle());
                extra.put("lightCount", wish.getLightCount());
                if (type == LeaderboardType.WARM) {
                    extra.put("blessCount", wish.getBlessCount());
                }
            }
        } else {
            userId = memberId;
            WishUserStat stat = statById.get(memberId);
            if (stat != null) {
                extra.put("checkinDays", stat.getTotalCheckinDays());
                extra.put("helpedCount", stat.getTotalHelped());
            }
        }
        String[] brief = userId != null
                ? briefs.getOrDefault(userId, new String[]{"心愿旅人", ""})
                : new String[]{"心愿旅人", ""};
        return new LeaderboardEntryVO(rank, userId, brief[0], brief[1],
                scores.getOrDefault(memberId, 0.0), extra, delta);
    }

    private java.time.LocalDateTime resolveCreatedAt(Long id, LeaderboardType type,
                                                     Map<Long, Wish> wishById, Map<Long, WishUserStat> statById) {
        if (type.isWishBoard()) {
            Wish wish = wishById.get(id);
            return wish != null && wish.getCreatedAt() != null ? wish.getCreatedAt() : java.time.LocalDateTime.MIN;
        }
        WishUserStat stat = statById.get(id);
        return stat != null && stat.getCreatedAt() != null ? stat.getCreatedAt() : java.time.LocalDateTime.MIN;
    }

    private Map<Long, String[]> fetchBriefs(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        try {
            var response = userFeignClient.batchGetUsers(new ArrayList<>(new HashSet<>(userIds)));
            if (response.success() && response.data() != null) {
                return response.data().stream()
                        .filter(m -> m.get("id") instanceof Number)
                        .collect(Collectors.toMap(
                                m -> ((Number) m.get("id")).longValue(),
                                m -> new String[]{
                                        (String) m.getOrDefault("nickname", "心愿旅人"),
                                        (String) m.getOrDefault("avatar", "")},
                                (a, b) -> a));
            }
        } catch (Exception ex) {
            log.warn("批量获取榜单昵称失败，降级占位: {}", ex.getMessage());
        }
        return Map.of();
    }

    private Long parseId(String member) {
        try {
            return Long.parseLong(member);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
