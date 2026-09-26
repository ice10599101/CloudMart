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
 * <p>仅公开宠物入榜；榜单 Top 20 + 我的数值/名次。胜场/捞瓶榜用分组聚合查询
 * （不走 N+1）；主人昵称批量 Feign（Fail-Open 占位）。</p>
 */
@Service
@Slf4j
public class PetRankingServiceImpl implements PetRankingService {

    private static final int TOP_N = 20;
    private static final String OWNER_PLACEHOLDER = "匿名训练家";

    private final PetMapper petMapper;
    private final PetBattleMapper battleMapper;
    private final PetBottleRecordMapper bottleRecordMapper;
    private final WishFeignClient wishFeignClient;

    public PetRankingServiceImpl(PetMapper petMapper,
                                 PetBattleMapper battleMapper,
                                 PetBottleRecordMapper bottleRecordMapper,
                                 WishFeignClient wishFeignClient) {
        this.petMapper = petMapper;
        this.battleMapper = battleMapper;
        this.bottleRecordMapper = bottleRecordMapper;
        this.wishFeignClient = wishFeignClient;
    }

    @Override
    public PetRankingResult ranking(RankingType type, Long userId) {
        List<PetRankingVO> top20 = switch (type) {
            case LEVEL -> levelRanking(userId);
            case BATTLE_WIN -> groupedRanking(type, userId);
            case BOTTLE -> groupedRanking(type, userId);
        };
        Long myValue = myValue(type, userId);
        Integer myRank = myRank(type, userId, myValue);
        return new PetRankingResult(top20, myValue, myRank);
    }

    /** 等级榜：pet 表直接排序（等级同分比经验） */
    private List<PetRankingVO> levelRanking(Long userId) {
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

    /** 胜场/捞瓶榜：分组聚合 Top N，再回填宠物信息 */
    private List<PetRankingVO> groupedRanking(RankingType type, Long userId) {
        List<Map<String, Object>> rows;
        if (type == RankingType.BATTLE_WIN) {
            rows = battleMapper.selectMaps(new QueryWrapper<PetBattle>()
                    .select("winner_pet_id as petId", "COUNT(*) as cnt")
                    .eq("status", PetBattleStatus.FINISHED.name())
                    .isNotNull("winner_pet_id")
                    .groupBy("winner_pet_id")
                    .orderByDesc("cnt").orderByAsc("pet_id")
                    .last("LIMIT " + TOP_N));
        } else {
            rows = bottleRecordMapper.selectMaps(new QueryWrapper<PetBottleRecord>()
                    .select("pet_id as petId", "COUNT(*) as cnt")
                    .eq("outcome", PetBottleOutcome.CAUGHT.name())
                    .groupBy("pet_id")
                    .orderByDesc("cnt").orderByAsc("pet_id")
                    .last("LIMIT " + TOP_N));
        }

        record Entry(Long petId, long cnt) {}
        List<Entry> entries = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object petId = row.get("petId");
            Object cnt = row.get("cnt");
            if (petId instanceof Number n && cnt instanceof Number c) {
                entries.add(new Entry(n.longValue(), c.longValue()));
            }
        }
        List<Long> petIds = entries.stream().map(Entry::petId).toList();
        Map<Long, Pet> petMap = petIds.isEmpty() ? Map.of()
                : petMapper.selectBatchIds(petIds).stream()
                        .filter(p -> Boolean.TRUE.equals(p.getIsPublic()))
                        .collect(HashMap::new, (m, p) -> m.put(p.getId(), p), HashMap::putAll);
        List<Long> ownerIds = petMap.values().stream().map(Pet::getUserId).toList();
        Map<Long, String> nicknames = resolveNicknames(ownerIds);

        List<PetRankingVO> result = new ArrayList<>();
        int rank = 1;
        for (Entry entry : entries) {
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

    /** 我的数值（未养宠物/不公开返回 0） */
    private Long myValue(RankingType type, Long userId) {
        Pet pet = petMapper.selectOne(new LambdaQueryWrapper<Pet>().eq(Pet::getUserId, userId));
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
     * 我的名次：等级榜=数值更好的公开宠物数+1；计数榜同理（全表计数，宠物量级可控）。
     * 不公开的宠物也参与名次计算（名次是私有信息，公开性只影响是否上 Top 榜）。
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

    private Map<Long, String> resolveNicknames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> users = wishFeignClient.batchGetUsers(userIds).data();
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
