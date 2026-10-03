package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetBattle;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.enums.PetBattleStatus;
import com.cloudmart.pet.enums.PetBottleOutcome;
import com.cloudmart.pet.feign.UserFeignClient;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.repository.PetBattleMapper;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetRankingService;
import com.cloudmart.pet.vo.PetRankingVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 排行榜服务实现（原文档 §80）。
 *
 * <p>P1-4：等级榜/胜场榜走 Redis ZSet 缓存（O(logN)），读路径缓存优先、Redis 异常
 * Fail-Open 回落 DB；捞瓶榜维持 DB 聚合（无 ZSet）。仅公开宠物入榜；缓存与 DB 间的
 * 中间态漂移（私密化/改名）由读路径 is_public 过滤 + 每日全量重建收敛。</p>
 */
@Service
@Slf4j
public class PetRankingServiceImpl implements PetRankingService {

    /** R06：赛季页需要真实 Top50（top20 字段名保留为已发布契约，内容扩为前 50） */
    private static final int TOP_N = 50;
    private static final String OWNER_PLACEHOLDER = "匿名训练家";

    private final PetMapper petMapper;
    private final PetBattleMapper battleMapper;
    private final PetBottleRecordMapper bottleRecordMapper;
    private final WishFeignClient wishFeignClient;
    private final com.cloudmart.pet.feign.UserFeignClient userFeignClient;
    private final PetRankingCache rankingCache;

    public PetRankingServiceImpl(PetMapper petMapper,
                                 PetBattleMapper battleMapper,
                                 PetBottleRecordMapper bottleRecordMapper,
                                 WishFeignClient wishFeignClient,
                                 com.cloudmart.pet.feign.UserFeignClient userFeignClient,
                                 PetRankingCache rankingCache) {
        this.petMapper = petMapper;
        this.battleMapper = battleMapper;
        this.bottleRecordMapper = bottleRecordMapper;
        this.wishFeignClient = wishFeignClient;
        this.userFeignClient = userFeignClient;
        this.rankingCache = rankingCache;
    }

    @Override
    public PetRankingResult ranking(RankingType type, Long userId) {
        List<PetRankingVO> top20 = switch (type) {
            case LEVEL -> levelRanking(userId);
            case BATTLE_WIN -> battleWinRanking(userId);
            case BOTTLE -> groupedRanking(type, userId);
        };
        Long myValue = myValue(type, userId);
        Integer myRank = myRank(type, userId, myValue);
        return new PetRankingResult(top20, myValue, myRank);
    }

    /** 等级榜：ZSet 缓存优先（复合分=level×1e9+exp），miss/降级回落 DB 排序 */
    private List<PetRankingVO> levelRanking(Long userId) {
        List<PetRankingCache.RankedEntry> cached = rankingCache.levelTop(TOP_N).orElse(null);
        if (cached != null) {
            List<Long> petIds = cached.stream().map(PetRankingCache.RankedEntry::petId).toList();
            if (petIds.isEmpty()) {
                return List.of();
            }
            Map<Long, Pet> petMap = petMapper.selectBatchIds(petIds).stream()
                    .filter(p -> Boolean.TRUE.equals(p.getIsPublic()))
                    .collect(HashMap::new, (m, p) -> m.put(p.getId(), p), HashMap::putAll);
            Map<Long, String> nicknames = resolveNicknames(
                    petMap.values().stream().map(Pet::getUserId).toList());
            List<PetRankingVO> result = new ArrayList<>();
            int rank = 1;
            for (Long petId : petIds) {
                Pet pet = petMap.get(petId);
                if (pet == null) {
                    continue;
                }
                result.add(new PetRankingVO(rank++, pet.getId(), pet.getName(), pet.getSpecies(),
                        pet.getLevel(), (long) pet.getLevel(), pet.getUserId(),
                        nicknames.getOrDefault(pet.getUserId(), OWNER_PLACEHOLDER),
                        pet.getUserId().equals(userId)));
            }
            return result;
        }
        return levelRankingFromDb(userId);
    }

    /** 等级榜 DB 兜底：pet 表直接排序（等级同分比经验） */
    private List<PetRankingVO> levelRankingFromDb(Long userId) {
        List<Pet> top = petMapper.selectList(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getIsPublic, true)
                .orderByDesc(Pet::getLevel)
                .orderByDesc(Pet::getExp)
                .orderByAsc(Pet::getId)
                .last("LIMIT " + TOP_N));
        List<Long> ownerIds = top.stream().map(Pet::getUserId).toList();
        Map<Long, String> nicknames = resolveNicknames(ownerIds);
        List<PetRankingVO> result = new ArrayList<>();
        int rank = 1;
        for (Pet pet : top) {
            result.add(new PetRankingVO(rank++, pet.getId(), pet.getName(), pet.getSpecies(),
                    pet.getLevel(), (long) pet.getLevel(), pet.getUserId(),
                    nicknames.getOrDefault(pet.getUserId(), OWNER_PLACEHOLDER),
                    pet.getUserId().equals(userId)));
        }
        return result;
    }

    /** 胜场榜：ZSet 缓存优先，miss/降级回落 DB 分组聚合 */
    private List<PetRankingVO> battleWinRanking(Long userId) {
        List<PetRankingCache.RankedEntry> cached = rankingCache.battleWinTop(TOP_N).orElse(null);
        if (cached != null) {
            List<CountEntry> entries = cached.stream()
                    .map(entry -> new CountEntry(entry.petId(), (long) entry.score()))
                    .toList();
            return assembleGrouped(entries, userId);
        }
        return groupedRanking(RankingType.BATTLE_WIN, userId);
    }

    /** 胜场/捞瓶榜：分组聚合 Top N，再回填宠物信息 */
    private List<PetRankingVO> groupedRanking(RankingType type, Long userId) {
        List<Map<String, Object>> rows;
        if (type == RankingType.BATTLE_WIN) {
            // PERF-01：排序列是 winner_pet_id（原 pet_id 列不存在，真库 Error 1054）；
            // 公开过滤前置于候选筛选——先在 SQL 内限定公开宠物，防止 TopN 截断后再过滤导致缺项
            rows = battleMapper.selectMaps(new QueryWrapper<PetBattle>()
                    .select("winner_pet_id as petId", "COUNT(*) as cnt")
                    .eq("status", PetBattleStatus.FINISHED.name())
                    .isNotNull("winner_pet_id")
                    .inSql("winner_pet_id", "SELECT id FROM pet WHERE is_public = 1")
                    .groupBy("winner_pet_id")
                    .orderByDesc("cnt").orderByAsc("winner_pet_id")
                    .last("LIMIT " + TOP_N));
        } else {
            rows = bottleRecordMapper.selectMaps(new QueryWrapper<PetBottleRecord>()
                    .select("pet_id as petId", "COUNT(*) as cnt")
                    .eq("outcome", PetBottleOutcome.CAUGHT.name())
                    .groupBy("pet_id")
                    .orderByDesc("cnt").orderByAsc("pet_id")
                    .last("LIMIT " + TOP_N));
        }

        List<CountEntry> entries = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row.get("petId") instanceof Number n && row.get("cnt") instanceof Number c) {
                entries.add(new CountEntry(n.longValue(), c.longValue()));
            }
        }
        return assembleGrouped(entries, userId);
    }

    /** 计数榜条目（petId + 计数；缓存路径与 DB 聚合路径共用回填逻辑） */
    private record CountEntry(Long petId, long cnt) {
    }

    /** 聚合条目 → 榜单 VO（批量回填宠物信息 + 昵称；私密宠物在回填时过滤） */
    private List<PetRankingVO> assembleGrouped(List<CountEntry> entries, Long userId) {
        List<Long> petIds = entries.stream().map(CountEntry::petId).toList();
        Map<Long, Pet> petMap = petIds.isEmpty() ? Map.of()
                : petMapper.selectBatchIds(petIds).stream()
                        .filter(p -> Boolean.TRUE.equals(p.getIsPublic()))
                        .collect(HashMap::new, (m, p) -> m.put(p.getId(), p), HashMap::putAll);
        List<Long> ownerIds = petMap.values().stream().map(Pet::getUserId).toList();
        Map<Long, String> nicknames = resolveNicknames(ownerIds);

        List<PetRankingVO> result = new ArrayList<>();
        int rank = 1;
        for (CountEntry entry : entries) {
            Pet pet = petMap.get(entry.petId());
            if (pet == null) {
                continue;
            }
            result.add(new PetRankingVO(rank++, pet.getId(), pet.getName(), pet.getSpecies(),
                    pet.getLevel(), entry.cnt(), pet.getUserId(),
                    nicknames.getOrDefault(pet.getUserId(), OWNER_PLACEHOLDER),
                    pet.getUserId().equals(userId)));
        }
        return result;
    }

    /** 我的数值（未养宠物/不公开返回 0；多宠物下取主宠，与 myRank/榜单同口径——P1-1 多宠物共存后 selectOne 必须限定主宠） */
    private Long myValue(RankingType type, Long userId) {
        Pet pet = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        if (pet == null) {
            return 0L;
        }
        return switch (type) {
            case LEVEL -> (long) pet.getLevel();
            case BATTLE_WIN -> battleMapper.selectCount(new LambdaQueryWrapper<PetBattle>()
                    .eq(PetBattle::getWinnerPetId, pet.getId())
                    .eq(PetBattle::getStatus, PetBattleStatus.FINISHED.name()));
            case BOTTLE -> bottleRecordMapper.selectCount(new LambdaQueryWrapper<PetBottleRecord>()
                    .eq(PetBottleRecord::getPetId, pet.getId())
                    .eq(PetBottleRecord::getOutcome, PetBottleOutcome.CAUGHT.name()));
        };
    }

    /**
     * 我的名次：优先 ZSet ZREVRANK（P1-4）；不在榜/Redis 降级时回落 DB 计数。
     * 等级榜=数值更好的公开宠物数+1；计数榜同理。不公开的宠物 rank=null
     * （reason 由调用方以 PRIVATE 传达）。
     */
    private Integer myRank(RankingType type, Long userId, Long myValue) {
        // B20：与榜单同一可见性口径——取主宠；私密宠物 rank=null（reason 由调用方以 PRIVATE 传达）
        Pet pet = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        if (pet == null || myValue == null || myValue <= 0) {
            return null;
        }
        if (!Boolean.TRUE.equals(pet.getIsPublic())) {
            return null;
        }
        Long cachedRank = switch (type) {
            case LEVEL -> rankingCache.levelRankOf(pet.getId());
            case BATTLE_WIN -> rankingCache.battleWinRankOf(pet.getId());
            case BOTTLE -> null;
        };
        if (cachedRank != null) {
            // ZSet 内可能有已转私密的漂移条目（每日重建收敛）；名次是私有信息，误差可接受
            return (int) (cachedRank + 1);
        }
        long better = switch (type) {
            // 与榜单同规则：只统计合法公开宠物（剔除野生模板/私密/已删除）
            case LEVEL -> petMapper.selectCount(new LambdaQueryWrapper<Pet>()
                    .eq(Pet::getIsPublic, true)
                    .ne(Pet::getId, 0L)
                    .gt(Pet::getLevel, pet.getLevel())
                    .or(w -> w.eq(Pet::getLevel, pet.getLevel())
                            .eq(Pet::getIsPublic, true)
                            .gt(Pet::getExp, pet.getExp())));
            // GROUP BY 场景不能用 selectCount（语义冲突），用 selectMaps 行数表示"数值比我高的宠物数"
            case BATTLE_WIN -> battleMapper.selectMaps(new QueryWrapper<PetBattle>()
                    .select("winner_pet_id")
                    .eq("status", PetBattleStatus.FINISHED.name())
                    .ne("winner_pet_id", pet.getId())
                    .groupBy("winner_pet_id")
                    .having("COUNT(*) > {0}", myValue)).size();
            case BOTTLE -> bottleRecordMapper.selectMaps(new QueryWrapper<PetBottleRecord>()
                    .select("pet_id")
                    .eq("outcome", PetBottleOutcome.CAUGHT.name())
                    .ne("pet_id", pet.getId())
                    .groupBy("pet_id")
                    .having("COUNT(*) > {0}", myValue)).size();
        };
        return (int) (better + 1);
    }

    /** 每日全量重建（P1-4）：公开宠物等级榜 + 全量胜场榜（与 DB 聚合同口径，仅公开宠物） */
    @Override
    public boolean rebuildRankingCache() {
        List<PetRankingCache.LevelEntry> levelEntries = petMapper.selectList(new LambdaQueryWrapper<Pet>()
                        .select(Pet::getId, Pet::getLevel, Pet::getExp)
                        .eq(Pet::getIsPublic, true))
                .stream()
                .map(p -> new PetRankingCache.LevelEntry(p.getId(),
                        p.getLevel() != null ? p.getLevel() : 1,
                        p.getExp() != null ? p.getExp() : 0))
                .toList();
        List<PetRankingCache.RankedEntry> battleEntries = battleMapper.selectMaps(new QueryWrapper<PetBattle>()
                        .select("winner_pet_id as petId", "COUNT(*) as cnt")
                        .eq("status", PetBattleStatus.FINISHED.name())
                        .isNotNull("winner_pet_id")
                        .inSql("winner_pet_id", "SELECT id FROM pet WHERE is_public = 1")
                        .groupBy("winner_pet_id"))
                .stream()
                .filter(row -> row.get("petId") instanceof Number && row.get("cnt") instanceof Number)
                .map(row -> new PetRankingCache.RankedEntry(
                        ((Number) row.get("petId")).longValue(),
                        ((Number) row.get("cnt")).doubleValue()))
                .toList();
        return rankingCache.rebuild(levelEntries, battleEntries);
    }

    private Map<Long, String> resolveNicknames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> users = userFeignClient.batchGetUsers(userIds).data();
            if (users == null) {
                return Map.of();
            }
            Map<Long, String> result = new HashMap<>();
            for (Map<String, Object> user : users) {
                Object id = user.get("id");
                Object nickname = user.get("nickname");
                if (id instanceof Number numberId && nickname != null) {
                    result.put(numberId.longValue(), nickname.toString());
                }
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
