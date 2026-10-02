package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.enums.PetRelationAction;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.feign.UserFeignClient;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetRelationService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.service.PetVisitService;
import com.cloudmart.pet.vo.PetVisitResultVO;
import com.cloudmart.pet.vo.PetVisitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 宠物串门实现（原文档 §1.1）。
 *
 * <p>限频策略（Redis，全部 Fail-Open：限流服务故障不阻断主流程，串门本身有精力消耗封顶）：
 * 每日次数 {@code pet:ratelimit:visit:{userId}:{date}}；同一邻居冷却
 * {@code pet:visit:neighbor:{userId}:{petId}}（SETNX + TTL，天然幂等）。</p>
 *
 * <p>效果与经验由服务端结算，邻居主人收到宠物口吻提醒（MQ → 通知系统）。</p>
 */
@Service
@Slf4j
public class PetVisitServiceImpl implements PetVisitService {

    private static final int NEIGHBOR_LIMIT = 8;
    private static final String NICKNAME_PLACEHOLDER = "邻居";

    private final PetService petService;
    private final PetStateService stateService;
    private final PetMapper petMapper;
    private final PetActivityMapper activityMapper;
    private final PetAchievementService achievementService;
    private final PetEventProducer eventProducer;
    private final WishFeignClient wishFeignClient;
    private final com.cloudmart.pet.feign.UserFeignClient userFeignClient;
    private final PetProperties properties;
    private final PetDailyQuestService dailyQuestService;
    private final com.cloudmart.pet.service.PetUserBlockService userBlockService;
    private final PetIntimacyService intimacyService;
    private final PetRelationService relationService;
    private final com.cloudmart.pet.service.PetVisitApplicationService visitApplicationService;

    public PetVisitServiceImpl(PetService petService,
                               PetStateService stateService,
                               PetMapper petMapper,
                               PetActivityMapper activityMapper,
                               PetAchievementService achievementService,
                               PetEventProducer eventProducer,
                               WishFeignClient wishFeignClient,
                               UserFeignClient userFeignClient,
                               PetProperties properties,
                               PetDailyQuestService dailyQuestService,
                               PetIntimacyService intimacyService,
                               PetRelationService relationService,
            com.cloudmart.pet.service.PetUserBlockService userBlockService,
            com.cloudmart.pet.service.PetVisitApplicationService visitApplicationService) {
        this.petService = petService;
        this.stateService = stateService;
        this.petMapper = petMapper;
        this.activityMapper = activityMapper;
        this.achievementService = achievementService;
        this.eventProducer = eventProducer;
        this.wishFeignClient = wishFeignClient;
        this.userFeignClient = userFeignClient;
        this.properties = properties;
        this.userBlockService = userBlockService;
        this.dailyQuestService = dailyQuestService;
        this.intimacyService = intimacyService;
        this.relationService = relationService;
        this.visitApplicationService = visitApplicationService;
    }

    @Override
    public List<PetVisitVO> neighbors(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        List<Pet> neighbors = PetCandidateSampler.sample(petMapper, userId, NEIGHBOR_LIMIT,
                Math.max(1, pet.getLevel() - 10), pet.getLevel() + 10, null);
        if (neighbors.isEmpty()) {
            // 等级段内没有邻居时放宽（保证新服/新用户也能串门；P04：兜底走主键采样）
            neighbors = PetCandidateSampler.sample(petMapper, userId, NEIGHBOR_LIMIT, null, null, null);
        }
        Map<Long, String> nicknames = resolveNicknames(neighbors.stream().map(Pet::getUserId).toList());
        List<PetVisitVO> result = new ArrayList<>(neighbors.size());
        for (Pet neighbor : neighbors) {
            result.add(new PetVisitVO(neighbor.getId(), neighbor.getName(), neighbor.getSpecies(),
                    neighbor.getLevel(), neighbor.getGrowthStage(),
                    neighbor.getEvolutionStage() != null ? neighbor.getEvolutionStage() : 0,
                    neighbor.getSkinCode(), neighbor.getUserId(),
                    nicknames.getOrDefault(neighbor.getUserId(), NICKNAME_PLACEHOLDER),
                    visitApplicationService.visitedToday(userId, neighbor.getUserId()), null));
        }
        return result;
    }

    @Override
    @Transactional
    public PetVisitResultVO visit(Long userId, Long neighborPetId) {
        Pet pet = petService.requireOwnedPet(userId);
        if (neighborPetId == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "请选择要串门的邻居");
        }
        Pet neighbor = petMapper.selectById(neighborPetId);
        if (neighbor == null || !Boolean.TRUE.equals(neighbor.getIsPublic())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "邻居家的宠物不存在或未公开");
        }
        if (userId.equals(neighbor.getUserId())) {
            throw new BusinessException(PetErrorCodes.PET_VISIT_SELF, "这是自己的宠物，不能串门哦");
        }
        // B14：屏蔽检查前置（先于冷却/精力校验，给出明确拒绝信号）
        if (userBlockService.isBlockedEitherWay(userId, neighbor.getUserId())) {
            throw new BusinessException(PetErrorCodes.PET_BLOCKED, "无法拜访该用户");
        }
        PetProperties.Visit cfg = properties.getVisit();
        if (pet.getEnergy() < cfg.getEnergyCost()) {
            throw new BusinessException(PetErrorCodes.PET_VISIT_ENERGY_INSUFFICIENT,
                    "宠物没力气出门了，先休息一下吧");
        }
        // B01/BE-06：统一拜访事实（数据库唯一键冷却）+ 数据库额度裁决收益；Redis 不再参与资格判定
        com.cloudmart.pet.service.PetVisitApplicationService.VisitGrant grant =
                visitApplicationService.recordVisit(userId, neighbor.getUserId(), pet.getId(),
                        neighbor.getId(), com.cloudmart.pet.service.PetVisitApplicationService.VisitSource.NEIGHBOR);
        if (!grant.factCreated()) {
            throw new BusinessException(PetErrorCodes.PET_VISIT_COOLDOWN,
                    "今天已经去过这家啦，换一家走走吧");
        }

        pet.setEnergy(Math.max(0, pet.getEnergy() - cfg.getEnergyCost()));
        pet.setHappiness(Math.min(100, pet.getHappiness() + cfg.getHappinessGain()));
        pet.setStatus(PetStatus.IDLE.name());
        recordVisitActivity(pet, neighbor);
        // 收益门控：有收益才推进亲密度/经验/任务/关系/成就（无收益拜访仅消耗精力）
        if (grant.rewardGranted()) {
            // 三期埋点：亲密度（与经验同一次写入）+ 每日任务 + 关系亲密度
            intimacyService.gain(pet, PetIntimacySource.VISIT);
            int levelups = stateService.grantExp(pet, cfg.getExpGain());
            dailyQuestService.record(pet, PetQuestType.VISIT, 1);
            relationService.gainBetween(pet, neighbor, PetRelationAction.VISIT);
            if (levelups > 0) {
                achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
            }
            achievementService.evaluate(pet, PetAchievementService.Event.VISIT);
        }

        String nickname = resolveNicknames(List.of(neighbor.getUserId()))
                .getOrDefault(neighbor.getUserId(), NICKNAME_PLACEHOLDER);
        eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_VISIT, new PetEventProducer.PetEventMessage(
                "VISIT:" + pet.getId() + ":" + neighbor.getId() + ":"
                        + java.time.LocalDate.now(java.time.ZoneOffset.UTC),
                String.valueOf(neighbor.getUserId()), "PET_VISIT",
                "有小伙伴来串门啦！",
                pet.getName() + " 来家里和 " + neighbor.getName() + " 玩了一会儿，主人也去回访一下吧！",
                String.valueOf(pet.getId()), "PET_VISIT"));

        String message = pet.getName() + " 去 " + nickname + " 家找 " + neighbor.getName()
                + " 玩啦，心情 +" + cfg.getHappinessGain() + "，经验 +" + cfg.getExpGain() + "～";
        return new PetVisitResultVO(neighbor.getName(), nickname,
                cfg.getHappinessGain(), cfg.getExpGain(), message, petService.getMyPet(userId));
    }

    private void recordVisitActivity(Pet pet, Pet neighbor) {
        PetActivity activity = new PetActivity();
        activity.setPetId(pet.getId());
        activity.setUserId(pet.getUserId());
        activity.setActivityType(PetActivityType.VISIT.name());
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        activity.setStartedAt(now);
        activity.setFinishedAt(now);
        activity.setClaimedAt(now);
        activity.setResult("{\"neighborPetId\":" + neighbor.getId() + "}");
        activityMapper.insert(activity);
    }

    /** 昵称批量查询：展示型数据 Fail-Open（占位昵称） */
    private Map<Long, String> resolveNicknames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> users = userFeignClient.batchGetUsers(userIds).data();
            if (users == null) {
                return Map.of();
            }
            Map<Long, String> result = new java.util.HashMap<>();
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
