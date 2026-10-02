package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.repository.PetMapper;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * P04：候选池主键随机起点采样——替代大表 ORDER BY RAND() 全表扫描。
 *
 * <p>原理：取 MAX(id) 后生成随机起点，{@code WHERE id >= 起点 ORDER BY id LIMIT n}
 * 走主键索引；起点之后取不满时自动回卷表头补齐（两段覆盖全表，段间按 id 去重）。
 * 随机分布均匀性弱于全表 RAND，对"随机对手/邻居/候选人"场景足够，
 * 复杂度 O(log n + n) 且不随表容量线性恶化。</p>
 */
public final class PetCandidateSampler {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PetCandidateSampler() {
    }

    /**
     * @param excludeUserId 排除自己
     * @param limit         期望数量
     * @param levelMin/levelMax 等级段过滤（null=不过滤）
     * @param excludeIds    额外排除的 pet id（调用方去重续采）
     */
    public static List<Pet> sample(PetMapper mapper, Long excludeUserId, int limit,
                                   Integer levelMin, Integer levelMax, List<Long> excludeIds) {
        if (limit <= 0) {
            return List.of();
        }
        Long maxId = mapper.selectMaxId();
        if (maxId == null || maxId <= 0) {
            return List.of();
        }
        long start = maxId > limit ? RANDOM.nextLong(maxId + 1) : 0;
        Set<Long> excluded = new HashSet<>(excludeIds == null ? List.of() : excludeIds);
        List<Pet> result = new ArrayList<>(limit);
        for (int pass = 0; pass < 2 && result.size() < limit; pass++) {
            LambdaQueryWrapper<Pet> wrapper = new LambdaQueryWrapper<Pet>()
                    .ne(Pet::getUserId, excludeUserId)
                    .eq(Pet::getIsPublic, true)
                    .ge(Pet::getId, pass == 0 ? start : 0)
                    .lt(pass == 1 && start > 0, Pet::getId, start)
                    .orderByAsc(Pet::getId)
                    .last("LIMIT " + (limit - result.size()));
            if (levelMin != null && levelMax != null) {
                wrapper.between(Pet::getLevel, levelMin, levelMax);
            }
            if (!excluded.isEmpty()) {
                wrapper.notIn(Pet::getId, excluded);
            }
            for (Pet pet : mapper.selectList(wrapper)) {
                if (result.size() < limit && excluded.add(pet.getId())) {
                    result.add(pet);
                }
            }
        }
        return result;
    }
}
