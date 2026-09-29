package com.cloudmart.pet.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 排行榜 Redis ZSet 缓存（P1-4）：读路径 O(logN)，替代每次请求的全表 filesort/GROUP BY。
 *
 * <p>Key 规范 {@code pet:rank:{type}}；member = petId，score 见 {@link #levelScore}（等级榜
 * 复合分：level × 1e9 + exp，与 DB 排序规则"等级同分比经验"一致）。写路径埋点：
 * 经验发放（grantExp 成功分支）/ 对战胜场（结算奖励处）/ 领养初始分；每日全量重建校准漂移
 * （私密化/改名等中间态漂移在重建时收敛，读路径同时按 is_public 过滤兜底）。</p>
 *
 * <p>降级策略（显式声明，Fail-Open）：任何 Redis 异常读路径返回 empty、写路径静默跳过，
 * 排行榜接口整体回落 DB 查询——榜单是展示型数据，Redis 故障不阻断主流程。
 * 另有 {@code pet_rank_cache_degraded} 指标。</p>
 */
@Component
@Slf4j
public class PetRankingCache {

    public static final String KEY_LEVEL = "pet:rank:level";
    public static final String KEY_BATTLE_WINS = "pet:rank:battle_wins";

    /** 等级复合分隔离基数：单级 exp 上限远小于 1e9，跨级不会串分（double 精确整数上限 2^53，安全） */
    static final double LEVEL_SCORE_UNIT = 1_000_000_000.0;

    private final StringRedisTemplate redisTemplate;
    private final com.cloudmart.pet.config.PetMetrics metrics;

    public PetRankingCache(StringRedisTemplate redisTemplate,
                           com.cloudmart.pet.config.PetMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
    }

    /** 等级榜复合分（与"等级同分比经验"DB 排序规则一致） */
    public static double levelScore(int level, int exp) {
        return level * LEVEL_SCORE_UNIT + exp;
    }

    /** 经验发放成功后同步等级榜（私密宠物不入榜） */
    public void onExpGranted(Long petId, int level, int exp, boolean isPublic) {
        if (!isPublic) {
            return;
        }
        try {
            redisTemplate.opsForZSet().add(KEY_LEVEL, String.valueOf(petId), levelScore(level, exp));
        } catch (Exception e) {
            degraded("level_zadd", e);
        }
    }

    /** 领养初始分（新宠物立即具备正确名次，不等重建） */
    public void onPetCreated(Long petId, boolean isPublic) {
        onExpGranted(petId, 1, 0, isPublic);
    }

    /** 对战胜利后胜场榜 +1（仅公开宠物在榜，私密由读路径过滤兜底） */
    public void onBattleWin(Long petId) {
        try {
            redisTemplate.opsForZSet().incrementScore(KEY_BATTLE_WINS, String.valueOf(petId), 1);
        } catch (Exception e) {
            degraded("battle_zincrby", e);
        }
    }

    /** 等级榜 Top N（按分降序）；Redis 不可用返回 empty（调用方回落 DB） */
    public Optional<List<RankedEntry>> levelTop(int topN) {
        return top(KEY_LEVEL, topN);
    }

    /** 胜场榜 Top N；Redis 不可用返回 empty（调用方回落 DB） */
    public Optional<List<RankedEntry>> battleWinTop(int topN) {
        return top(KEY_BATTLE_WINS, topN);
    }

    /** 我的等级名次（0 基；不在榜返回 null——私密/未入榜，由调用方回落 DB 或置 null） */
    public Long levelRankOf(Long petId) {
        try {
            return redisTemplate.opsForZSet().reverseRank(KEY_LEVEL, String.valueOf(petId));
        } catch (Exception e) {
            degraded("level_zrevrank", e);
            return null;
        }
    }

    /** 我的胜场名次（0 基；语义同 levelRankOf） */
    public Long battleWinRankOf(Long petId) {
        try {
            return redisTemplate.opsForZSet().reverseRank(KEY_BATTLE_WINS, String.valueOf(petId));
        } catch (Exception e) {
            degraded("battle_zrevrank", e);
            return null;
        }
    }

    /**
     * 每日全量重建（校准漂移）：DEL 后管道批量 ZADD。任意失败放弃本次重建（读路径继续
     * 用旧数据/DB，不出现空榜窗口——直接 DEL+重建，失败时调用方回落 DB）。
     *
     * @return 是否重建成功
     */
    public boolean rebuild(List<LevelEntry> levelEntries, List<RankedEntry> battleWinEntries) {
        try {
            redisTemplate.delete(List.of(KEY_LEVEL, KEY_BATTLE_WINS));
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                var zset = connection.zSetCommands();
                for (LevelEntry entry : levelEntries) {
                    zset.zAdd(KEY_LEVEL.getBytes(), levelScore(entry.level(), entry.exp()),
                            entry.petId().toString().getBytes());
                }
                for (RankedEntry entry : battleWinEntries) {
                    zset.zAdd(KEY_BATTLE_WINS.getBytes(), entry.score(), entry.petId().toString().getBytes());
                }
                return null;
            });
            log.info("排行榜缓存重建完成: level={}, battleWins={}", levelEntries.size(), battleWinEntries.size());
            return true;
        } catch (Exception e) {
            degraded("rebuild", e);
            return false;
        }
    }

    private Optional<List<RankedEntry>> top(String key, int topN) {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, topN - 1L);
            if (tuples == null || tuples.isEmpty()) {
                // 空榜可能是尚未重建，也可能是真实无数据——统一视为 miss，调用方回落 DB
                return Optional.empty();
            }
            List<RankedEntry> entries = new ArrayList<>(tuples.size());
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() != null && tuple.getScore() != null) {
                    entries.add(new RankedEntry(Long.valueOf(tuple.getValue()), tuple.getScore()));
                }
            }
            return Optional.of(entries);
        } catch (Exception e) {
            degraded(key, e);
            return Optional.empty();
        }
    }

    private void degraded(String operation, Exception e) {
        metrics.increment("pet_rank_cache_degraded", "op", operation);
        log.warn("排行榜缓存降级（Fail-Open 回落 DB）: op={}", operation, e);
    }

    /** 榜单项：petId + 分值（等级榜为复合分，胜场榜为胜场数） */
    public record RankedEntry(Long petId, double score) {
    }

    /** 等级榜重建条目（petId + 等级 + 经验，复合分在缓存内计算） */
    public record LevelEntry(Long petId, int level, int exp) {
    }
}
