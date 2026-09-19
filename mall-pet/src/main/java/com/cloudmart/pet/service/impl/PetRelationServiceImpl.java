package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.RequestRelationRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetRelation;
import com.cloudmart.pet.enums.PetRelationAction;
import com.cloudmart.pet.enums.PetRelationStatus;
import com.cloudmart.pet.enums.PetRelationType;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetRelationMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetRelationService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetIntimacyMath;
import com.cloudmart.pet.vo.PetRelationPanelVO;
import com.cloudmart.pet.vo.PetRelationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 宠物关系实现（三期）。
 *
 * <p>幂等与并发：{@code uk_pet_relation}（from,to,type）保证同一对宠物同一类型只有一条，
 * 重复申请命中唯一键后复用该行（REJECTED/DISSOLVED 可重新申请）；
 * 关系亲密度由 {@link #gainBetween} 在双方行为路径累加，每日上限用 Redis 计数（Fail-Open）。</p>
 *
 * <p>通知：申请 → 对方主人（PET_RELATION_REQUEST），确认 → 发起方（PET_RELATION_ACCEPTED），
 * 文案按宠物口吻生成，通知服务原样落库。</p>
 */
@Service
@Slf4j
public class PetRelationServiceImpl implements PetRelationService {

    /** 关系亲密度每日上限计数（防互刷） */
    static final String KEY_RELATION_INTIMACY = "pet:relation:intimacy:%d:%s";
    /** 每日申请次数上限计数 */
    static final String KEY_RELATION_REQUEST = "pet:ratelimit:relation:%d:%s";

    private static final int CANDIDATE_LIMIT = 8;
    private static final String NICKNAME_PLACEHOLDER = "邻居";

    private final PetService petService;
    private final PetMapper petMapper;
    private final PetRelationMapper relationMapper;
    private final WishFeignClient wishFeignClient;
    private final PetEventProducer eventProducer;
    private final PetAchievementService achievementService;
    private final PetProperties properties;
    private final StringRedisTemplate redisTemplate;

    public PetRelationServiceImpl(PetService petService,
                                  PetMapper petMapper,
                                  PetRelationMapper relationMapper,
                                  WishFeignClient wishFeignClient,
                                  PetEventProducer eventProducer,
                                  PetAchievementService achievementService,
                                  PetProperties properties,
                                  StringRedisTemplate redisTemplate) {
        this.petService = petService;
        this.petMapper = petMapper;
        this.relationMapper = relationMapper;
        this.wishFeignClient = wishFeignClient;
        this.eventProducer = eventProducer;
        this.achievementService = achievementService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public PetRelationPanelVO panel(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        List<PetRelation> all = relationMapper.selectList(new LambdaQueryWrapper<PetRelation>()
                .and(w -> w.eq(PetRelation::getFromPetId, pet.getId())
                        .or()
                        .eq(PetRelation::getToPetId, pet.getId()))
                .in(PetRelation::getStatus, PetRelationStatus.PENDING.name(), PetRelationStatus.ACTIVE.name())
                .orderByDesc(PetRelation::getId));
        Map<Long, Pet> counterpartCache = new HashMap<>();
        Map<Long, String> nicknames = resolveNicknames(all.stream()
                .map(relation -> counterpartOwner(relation, pet.getId()))
                .distinct()
                .toList());

        List<PetRelationVO> relations = new ArrayList<>();
        List<PetRelationVO> incoming = new ArrayList<>();
        List<PetRelationVO> outgoing = new ArrayList<>();
        for (PetRelation relation : all) {
            boolean fromMe = pet.getId().equals(relation.getFromPetId());
            Long counterpartPetId = fromMe ? relation.getToPetId() : relation.getFromPetId();
            Pet counterpart = counterpartCache.computeIfAbsent(counterpartPetId, petMapper::selectById);
            if (counterpart == null) {
                continue;
            }
            boolean active = PetRelationStatus.ACTIVE.name().equals(relation.getStatus());
            String direction = active ? "ACTIVE" : (fromMe ? "OUTGOING" : "INCOMING");
            PetRelationVO vo = toVo(relation, counterpart, direction, nicknames);
            if (active) {
                relations.add(vo);
            } else if (fromMe) {
                outgoing.add(vo);
            } else {
                incoming.add(vo);
            }
        }

        Set<Long> busyPetIds = all.stream()
                .map(relation -> counterpartPetIdOf(relation, pet.getId()))
                .collect(Collectors.toSet());
        List<Pet> candidates = petMapper.selectList(new LambdaQueryWrapper<Pet>()
                .ne(Pet::getUserId, userId)
                .eq(Pet::getIsPublic, true)
                .between(Pet::getLevel, Math.max(1, pet.getLevel() - 10), pet.getLevel() + 10)
                .last("ORDER BY RAND() LIMIT " + CANDIDATE_LIMIT));
        if (candidates.isEmpty()) {
            candidates = petMapper.selectList(new LambdaQueryWrapper<Pet>()
                    .ne(Pet::getUserId, userId)
                    .eq(Pet::getIsPublic, true)
                    .last("ORDER BY RAND() LIMIT " + CANDIDATE_LIMIT));
        }
        Map<Long, String> candidateNicknames = resolveNicknames(candidates.stream().map(Pet::getUserId).toList());
        List<PetRelationVO> candidateVos = candidates.stream()
                .filter(candidate -> !busyPetIds.contains(candidate.getId()))
                .map(candidate -> new PetRelationVO(null, null, null, null, "CANDIDATE",
                        0, 1, null, 0, candidate.getId(), candidate.getName(), candidate.getSpecies(),
                        candidate.getLevel(), candidate.getGrowthStage(),
                        candidate.getEvolutionStage() != null ? candidate.getEvolutionStage() : 0,
                        candidate.getSkinCode(),
                        candidateNicknames.getOrDefault(candidate.getUserId(), NICKNAME_PLACEHOLDER),
                        null, null, null))
                .toList();

        return new PetRelationPanelVO(relations, incoming, outgoing, candidateVos, typeLimits(relations));
    }

    @Override
    @Transactional
    public PetRelationVO request(Long userId, RequestRelationRequest request) {
        Pet pet = petService.requireOwnedPet(userId);
        Pet target = petMapper.selectById(request.toPetId());
        if (target == null || !Boolean.TRUE.equals(target.getIsPublic())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "对方的宠物不存在或未公开");
        }
        if (userId.equals(target.getUserId())) {
            throw new BusinessException(PetErrorCodes.PET_RELATION_SELF, "不能和自己的宠物建立关系哦");
        }
        PetRelationType type = PetRelationType.valueOf(request.relType());
        requireTypeQuota(pet, type);
        requireRequestQuota(userId);

        PetRelation existing = relationMapper.selectOne(new LambdaQueryWrapper<PetRelation>()
                .eq(PetRelation::getFromPetId, pet.getId())
                .eq(PetRelation::getToPetId, target.getId())
                .eq(PetRelation::getRelType, type.name())
                .last("LIMIT 1"));
        if (existing != null) {
            if (PetRelationStatus.ACTIVE.name().equals(existing.getStatus())
                    || PetRelationStatus.PENDING.name().equals(existing.getStatus())) {
                throw new BusinessException(PetErrorCodes.PET_RELATION_EXISTS,
                        PetRelationStatus.ACTIVE.name().equals(existing.getStatus())
                                ? "你们已经是" + type.label() + "啦"
                                : "申请已经发出啦，等对方回应吧");
            }
            // REJECTED/DISSOLVED 可重新申请：复用同一行（uk 唯一）
            relationMapper.update(null, new LambdaUpdateWrapper<PetRelation>()
                    .set(PetRelation::getStatus, PetRelationStatus.PENDING.name())
                    .set(PetRelation::getMessage, request.message())
                    .set(PetRelation::getFromUserId, userId)
                    .set(PetRelation::getFromPetId, pet.getId())
                    .set(PetRelation::getToUserId, target.getUserId())
                    .set(PetRelation::getToPetId, target.getId())
                    .set(PetRelation::getAcceptedAt, null)
                    .set(PetRelation::getIntimacy, 0)
                    .set(PetRelation::getLastIntimacyAt, null)
                    .eq(PetRelation::getId, existing.getId()));
            existing.setStatus(PetRelationStatus.PENDING.name());
            existing.setIntimacy(0);
            existing.setMessage(request.message());
            notifyRequest(pet, target, type, request.message());
            return toVo(existing, target, "OUTGOING", resolveNicknames(List.of(target.getUserId())));
        }

        PetRelation relation = new PetRelation();
        relation.setFromPetId(pet.getId());
        relation.setToPetId(target.getId());
        relation.setFromUserId(userId);
        relation.setToUserId(target.getUserId());
        relation.setRelType(type.name());
        relation.setStatus(PetRelationStatus.PENDING.name());
        relation.setIntimacy(0);
        relation.setMessage(request.message());
        try {
            relationMapper.insert(relation);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_RELATION_EXISTS, "申请已经发出啦，等对方回应吧");
        }
        notifyRequest(pet, target, type, request.message());
        return toVo(relation, target, "OUTGOING", resolveNicknames(List.of(target.getUserId())));
    }

    @Override
    @Transactional
    public PetRelationVO accept(Long userId, Long relationId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetRelation relation = requireIncoming(relationId, pet);
        int updated = relationMapper.update(null, new LambdaUpdateWrapper<PetRelation>()
                .set(PetRelation::getStatus, PetRelationStatus.ACTIVE.name())
                .set(PetRelation::getAcceptedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetRelation::getId, relation.getId())
                .eq(PetRelation::getStatus, PetRelationStatus.PENDING.name()));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_RELATION_NOT_PENDING, "这条申请已经处理过啦");
        }
        relation.setStatus(PetRelationStatus.ACTIVE.name());
        relation.setAcceptedAt(LocalDateTime.now(ZoneId.of("UTC")));
        Pet from = petMapper.selectById(relation.getFromPetId());
        PetRelationType type = PetRelationType.valueOf(relation.getRelType());
        if (from != null) {
            eventProducer.publish(RocketMQConfig.PET_TAG_RELATION, new PetEventProducer.PetEventMessage(
                    relation.getFromUserId(), "PET_RELATION_ACCEPTED",
                    "关系确认啦！",
                    from.getName() + "：主人，" + pet.getName() + " 答应了！我们正式成为"
                            + type.label() + "啦～",
                    relation.getId(), "PET_RELATION_ACCEPTED"));
        }
        achievementService.evaluate(pet, PetAchievementService.Event.RELATION);
        return toVo(relation, from != null ? from : pet, "ACTIVE",
                resolveNicknames(List.of(relation.getFromUserId())));
    }

    @Override
    @Transactional
    public PetRelationVO reject(Long userId, Long relationId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetRelation relation = requireIncoming(relationId, pet);
        int updated = relationMapper.update(null, new LambdaUpdateWrapper<PetRelation>()
                .set(PetRelation::getStatus, PetRelationStatus.REJECTED.name())
                .eq(PetRelation::getId, relation.getId())
                .eq(PetRelation::getStatus, PetRelationStatus.PENDING.name()));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_RELATION_NOT_PENDING, "这条申请已经处理过啦");
        }
        relation.setStatus(PetRelationStatus.REJECTED.name());
        Pet from = petMapper.selectById(relation.getFromPetId());
        return toVo(relation, from != null ? from : pet, "INCOMING",
                resolveNicknames(List.of(relation.getFromUserId())));
    }

    @Override
    @Transactional
    public PetRelationVO dissolve(Long userId, Long relationId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetRelation relation = relationMapper.selectById(relationId);
        if (relation == null || !PetRelationStatus.ACTIVE.name().equals(relation.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_RELATION_NOT_FOUND, "关系不存在或已解除");
        }
        boolean mine = userId.equals(relation.getFromUserId()) || userId.equals(relation.getToUserId());
        if (!mine) {
            throw new BusinessException(PetErrorCodes.PET_FORBIDDEN, "只能解除自己的关系");
        }
        relationMapper.update(null, new LambdaUpdateWrapper<PetRelation>()
                .set(PetRelation::getStatus, PetRelationStatus.DISSOLVED.name())
                .eq(PetRelation::getId, relation.getId())
                .eq(PetRelation::getStatus, PetRelationStatus.ACTIVE.name()));
        Pet counterpart = petMapper.selectById(pet.getId().equals(relation.getFromPetId())
                ? relation.getToPetId() : relation.getFromPetId());
        relation.setStatus(PetRelationStatus.DISSOLVED.name());
        return toVo(relation, counterpart != null ? counterpart : pet, "ACTIVE", Map.of());
    }

    @Override
    public void gainBetween(Pet pet, Pet otherPet, PetRelationAction action) {
        if (pet == null || otherPet == null || pet.getId().equals(otherPet.getId())) {
            return;
        }
        try {
            PetRelation relation = relationMapper.selectOne(new LambdaQueryWrapper<PetRelation>()
                    .eq(PetRelation::getStatus, PetRelationStatus.ACTIVE.name())
                    .and(w -> w.eq(PetRelation::getFromPetId, pet.getId())
                            .eq(PetRelation::getToPetId, otherPet.getId())
                            .or(inner -> inner.eq(PetRelation::getFromPetId, otherPet.getId())
                                    .eq(PetRelation::getToPetId, pet.getId())))
                    .last("LIMIT 1"));
            if (relation == null) {
                return;
            }
            int gain = switch (action) {
                case VISIT -> properties.getRelation().getIntimacyGainVisit();
                case WALL -> properties.getRelation().getIntimacyGainWall();
                case BATTLE -> properties.getRelation().getIntimacyGainBattle();
            };
            if (gain <= 0 || !consumeDailyQuota(pet.getUserId(), gain)) {
                return;
            }
            relationMapper.update(null, new LambdaUpdateWrapper<PetRelation>()
                    .setSql("intimacy = intimacy + " + gain)
                    .set(PetRelation::getLastIntimacyAt, LocalDateTime.now(ZoneId.of("UTC")))
                    .eq(PetRelation::getId, relation.getId()));
        } catch (Exception e) {
            // 关系亲密度是附带收益：失败不影响主玩法
            log.warn("关系亲密度埋点失败（忽略）: petId={}, otherPetId={}, action={}",
                    pet.getId(), otherPet.getId(), action, e);
        }
    }

    // ---------------- 内部 ----------------

    /** 每日关系亲密度上限（Redis 计数，Fail-Open 时放行） */
    private boolean consumeDailyQuota(Long userId, int gain) {
        try {
            String key = String.format(KEY_RELATION_INTIMACY, userId, LocalDate.now(ZoneId.of("UTC")));
            Long used = redisTemplate.opsForValue().increment(key, gain);
            if (used != null && used == gain) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
            return used == null || used <= properties.getRelation().getDailyIntimacyCap();
        } catch (Exception e) {
            log.warn("关系亲密度限频 Redis 故障，Fail-Open 放行: userId={}", userId, e);
            return true;
        }
    }

    /** 每日申请上限（Redis 计数，Fail-Open 放行） */
    private void requireRequestQuota(Long userId) {
        try {
            String key = String.format(KEY_RELATION_REQUEST, userId, LocalDate.now(ZoneId.of("UTC")));
            Long used = redisTemplate.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
            int limit = properties.getRelation().getRequestDailyLimit();
            if (used != null && used > limit) {
                throw new BusinessException(PetErrorCodes.PET_INTERACTION_RATE_LIMITED,
                        "今天已经发过 " + limit + " 次关系申请啦，明天再试试吧");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("关系申请限频 Redis 故障，Fail-Open 放行: userId={}", userId, e);
        }
    }

    /** 类型配额校验（独占类型至多 1 段，其余按配置上限） */
    private void requireTypeQuota(Pet pet, PetRelationType type) {
        long current = relationMapper.selectCount(new LambdaQueryWrapper<PetRelation>()
                .eq(PetRelation::getRelType, type.name())
                .eq(PetRelation::getStatus, PetRelationStatus.ACTIVE.name())
                .and(w -> w.eq(PetRelation::getFromPetId, pet.getId())
                        .or()
                        .eq(PetRelation::getToPetId, pet.getId())));
        int max = maxFor(type);
        if (current >= max) {
            throw new BusinessException(type.exclusive()
                    ? PetErrorCodes.PET_RELATION_EXCLUSIVE
                    : PetErrorCodes.PET_RELATION_LIMIT,
                    type.exclusive()
                            ? "已经有一段" + type.label() + "关系啦，要先解除才能开始新的"
                            : type.label() + "最多同时拥有 " + max + " 段哦");
        }
    }

    private int maxFor(PetRelationType type) {
        PetProperties.Relation cfg = properties.getRelation();
        return switch (type) {
            case COUPLE -> cfg.getMaxCouple();
            case BESTIE -> cfg.getMaxBestie();
            case BROTHER -> cfg.getMaxBrother();
            case CONFIDANT -> cfg.getMaxConfidant();
        };
    }

    private PetRelation requireIncoming(Long relationId, Pet pet) {
        PetRelation relation = relationMapper.selectById(relationId);
        if (relation == null || !PetRelationStatus.PENDING.name().equals(relation.getStatus())
                || !pet.getId().equals(relation.getToPetId())) {
            throw new BusinessException(PetErrorCodes.PET_RELATION_NOT_FOUND, "这条申请不存在或已处理");
        }
        return relation;
    }

    private Long counterpartOwner(PetRelation relation, Long myPetId) {
        return myPetId.equals(relation.getFromPetId()) ? relation.getToUserId() : relation.getFromUserId();
    }

    private Long counterpartPetIdOf(PetRelation relation, Long myPetId) {
        return myPetId.equals(relation.getFromPetId()) ? relation.getToPetId() : relation.getFromPetId();
    }

    private List<PetRelationPanelVO.TypeLimit> typeLimits(List<PetRelationVO> relations) {
        List<PetRelationPanelVO.TypeLimit> limits = new ArrayList<>();
        for (PetRelationType type : PetRelationType.values()) {
            int current = (int) relations.stream()
                    .filter(relation -> type.name().equals(relation.relType()))
                    .count();
            limits.add(new PetRelationPanelVO.TypeLimit(type.name(), type.label(), maxFor(type),
                    current, type.exclusive()));
        }
        return limits;
    }

    private PetRelationVO toVo(PetRelation relation, Pet counterpart, String direction,
                               Map<Long, String> nicknames) {
        PetRelationType type = PetRelationType.valueOf(relation.getRelType());
        int intimacy = relation.getIntimacy() != null ? relation.getIntimacy() : 0;
        List<Integer> thresholds = properties.getRelation().getLevelThresholds();
        int level = PetIntimacyMath.levelOf(intimacy, thresholds);
        return new PetRelationVO(
                relation.getId(), relation.getRelType(), type.label(), relation.getStatus(), direction,
                intimacy, level, PetIntimacyMath.levelName(level, properties.getRelation().getLevelNames()),
                PetIntimacyMath.toNext(intimacy, thresholds),
                counterpart.getId(), counterpart.getName(), counterpart.getSpecies(), counterpart.getLevel(),
                counterpart.getGrowthStage(),
                counterpart.getEvolutionStage() != null ? counterpart.getEvolutionStage() : 0,
                counterpart.getSkinCode(),
                nicknames.getOrDefault(counterpart.getUserId(), NICKNAME_PLACEHOLDER),
                relation.getMessage(), relation.getCreatedAt(), relation.getAcceptedAt());
    }

    private void notifyRequest(Pet from, Pet target, PetRelationType type, String message) {
        String extra = message != null && !message.isBlank() ? "（" + message + "）" : "";
        eventProducer.publish(RocketMQConfig.PET_TAG_RELATION, new PetEventProducer.PetEventMessage(
                target.getUserId(), "PET_RELATION_REQUEST",
                "收到关系申请啦！",
                from.getName() + " 想和 " + target.getName() + " 成为" + type.label() + "，去宠物页回应一下吧" + extra,
                from.getId(), "PET_RELATION_REQUEST"));
    }

    /** 昵称批量查询：展示型数据 Fail-Open（占位昵称） */
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
