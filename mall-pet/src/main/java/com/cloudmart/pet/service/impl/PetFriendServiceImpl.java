package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetFriend;
import com.cloudmart.pet.enums.PetFriendStatus;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.enums.PetRelationAction;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetFriendMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetFriendService;
import com.cloudmart.pet.service.PetHomeService;
import com.cloudmart.pet.service.PetRelationService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetFriendPanelVO;
import com.cloudmart.pet.vo.PetFriendVO;
import com.cloudmart.pet.vo.PetFriendVisitResultVO;
import com.cloudmart.pet.vo.PetRoomVisitVO;
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

/**
 * 宠物好友实现（三期）。
 *
 * <p>双向关系：申请只落一行 PENDING，确认时双方各落一行 ACTIVE（{@code uk_pet_friend} 幂等）；
 * 若对方早已向我发起申请，我发起申请时直接互相确认（避免"双方都在等对方同意"）。
 * 互访复用家园结算（{@link PetHomeService#visitFriendRoom}），再叠加好友层的
 * 互访次数、关系亲密度与每日任务进度；每日互访上限用 Redis 计数（Fail-Open）。</p>
 */
@Service
@Slf4j
public class PetFriendServiceImpl implements PetFriendService {

    static final String KEY_FRIEND_VISIT_DAILY = "pet:ratelimit:friendvisit:%d:%s";
    static final String KEY_FRIEND_REQUEST_DAILY = "pet:ratelimit:friend:%d:%s";

    private static final String NICKNAME_PLACEHOLDER = "好友";

    private final PetService petService;
    private final PetMapper petMapper;
    private final PetFriendMapper friendMapper;
    private final PetHomeService homeService;
    private final PetRelationService relationService;
    private final PetDailyQuestService dailyQuestService;
    private final PetAchievementService achievementService;
    private final WishFeignClient wishFeignClient;
    private final PetEventProducer eventProducer;
    private final PetProperties properties;
    private final StringRedisTemplate redisTemplate;

    public PetFriendServiceImpl(PetService petService,
                                PetMapper petMapper,
                                PetFriendMapper friendMapper,
                                PetHomeService homeService,
                                PetRelationService relationService,
                                PetDailyQuestService dailyQuestService,
                                PetAchievementService achievementService,
                                WishFeignClient wishFeignClient,
                                PetEventProducer eventProducer,
                                PetProperties properties,
                                StringRedisTemplate redisTemplate) {
        this.petService = petService;
        this.petMapper = petMapper;
        this.friendMapper = friendMapper;
        this.homeService = homeService;
        this.relationService = relationService;
        this.dailyQuestService = dailyQuestService;
        this.achievementService = achievementService;
        this.wishFeignClient = wishFeignClient;
        this.eventProducer = eventProducer;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public PetFriendPanelVO panel(Long userId) {
        petService.requireOwnedPet(userId);
        List<PetFriend> rows = friendMapper.selectList(new LambdaQueryWrapper<PetFriend>()
                .eq(PetFriend::getUserId, userId)
                .ne(PetFriend::getStatus, PetFriendStatus.REJECTED.name())
                .orderByDesc(PetFriend::getId));
        List<Long> userIds = rows.stream().map(PetFriend::getFriendUserId).toList();
        Map<Long, String> nicknames = resolveNicknames(userIds);
        Map<Long, Pet> petMap = mainPets(userIds);

        List<PetFriendVO> friends = new ArrayList<>();
        List<PetFriendVO> outgoing = new ArrayList<>();
        for (PetFriend row : rows) {
            PetFriendVO vo = toVo(row, petMap.get(row.getFriendUserId()), nicknames, userId);
            if (PetFriendStatus.ACTIVE.name().equals(row.getStatus())) {
                friends.add(vo);
            } else {
                outgoing.add(vo);
            }
        }
        // 收到的申请：反向表里 PENDING 且我没有对应 ACTIVE 行
        List<PetFriend> incomingRows = friendMapper.selectList(new LambdaQueryWrapper<PetFriend>()
                .eq(PetFriend::getFriendUserId, userId)
                .eq(PetFriend::getStatus, PetFriendStatus.PENDING.name())
                .orderByDesc(PetFriend::getId));
        List<Long> incomingIds = incomingRows.stream().map(PetFriend::getUserId).toList();
        Map<Long, String> incomingNicknames = resolveNicknames(incomingIds);
        Map<Long, Pet> incomingPets = mainPets(incomingIds);
        List<PetFriendVO> incoming = incomingRows.stream()
                .filter(row -> friends.stream().noneMatch(f -> f.userId().equals(row.getUserId())))
                .map(row -> toVo(row, incomingPets.get(row.getUserId()), incomingNicknames, row.getUserId()))
                .map(vo -> new PetFriendVO(vo.userId(), vo.nickname(), vo.petId(), vo.petName(), vo.species(),
                        vo.level(), vo.evolutionStage(), vo.skinCode(), vo.status(), "INCOMING",
                        vo.visitCount(), vo.lastVisitAt(), vo.visitedToday()))
                .toList();

        PetProperties.Friend cfg = properties.getFriend();
        int todayVisits = todayVisitCount(userId);
        return new PetFriendPanelVO(friends, incoming, outgoing, cfg.getMaxFriends(),
                cfg.getDailyVisitLimit(), todayVisits,
                Math.max(0, cfg.getDailyVisitLimit() - todayVisits));
    }

    @Override
    @Transactional
    public PetFriendVO request(Long userId, Long friendUserId) {
        Pet pet = petService.requireOwnedPet(userId);
        if (friendUserId == null || friendUserId.equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_FRIEND_SELF, "不能加自己为好友哦");
        }
        Pet target = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, friendUserId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        if (target == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "对方还没有宠物，暂时不能加好友");
        }
        PetFriend existing = findRow(userId, friendUserId);
        if (existing != null && PetFriendStatus.ACTIVE.name().equals(existing.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_FRIEND_EXISTS, "你们已经是好友啦");
        }
        long friendCount = friendMapper.selectCount(new LambdaQueryWrapper<PetFriend>()
                .eq(PetFriend::getUserId, userId)
                .eq(PetFriend::getStatus, PetFriendStatus.ACTIVE.name()));
        if (friendCount >= properties.getFriend().getMaxFriends()) {
            throw new BusinessException(PetErrorCodes.PET_FRIEND_LIMIT,
                    "好友已经满 " + properties.getFriend().getMaxFriends() + " 位啦");
        }
        // 对方已向我发起申请 → 直接互相确认
        PetFriend reverse = findRow(friendUserId, userId);
        if (reverse != null && PetFriendStatus.PENDING.name().equals(reverse.getStatus())) {
            return accept(userId, friendUserId);
        }
        requireRequestQuota(userId);
        if (existing != null && PetFriendStatus.PENDING.name().equals(existing.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_FRIEND_EXISTS, "申请已经发出啦，等对方回应吧");
        }
        if (existing != null) {
            friendMapper.update(null, new LambdaUpdateWrapper<PetFriend>()
                    .set(PetFriend::getStatus, PetFriendStatus.PENDING.name())
                    .set(PetFriend::getSource, "VISIT")
                    .eq(PetFriend::getId, existing.getId()));
            existing.setStatus(PetFriendStatus.PENDING.name());
        } else {
            PetFriend row = new PetFriend();
            row.setUserId(userId);
            row.setFriendUserId(friendUserId);
            row.setStatus(PetFriendStatus.PENDING.name());
            row.setSource("VISIT");
            row.setVisitCount(0);
            try {
                friendMapper.insert(row);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(PetErrorCodes.PET_FRIEND_EXISTS, "申请已经发出啦，等对方回应吧");
            }
            existing = row;
        }
        eventProducer.publish(RocketMQConfig.PET_TAG_FRIEND, new PetEventProducer.PetEventMessage(
                "FRIEND_REQUEST:" + userId + ":" + friendUserId + ":"
                        + java.time.LocalDate.now(java.time.ZoneOffset.UTC),
                String.valueOf(friendUserId), "PET_FRIEND_REQUEST",
                "收到好友申请啦！",
                pet.getName() + " 的主人想和你做朋友，去宠物家园的社交页看看吧～",
                String.valueOf(pet.getId()), "PET_FRIEND_REQUEST"));
        return toVo(existing, target, resolveNicknames(List.of(friendUserId)), userId);
    }

    @Override
    @Transactional
    public PetFriendVO accept(Long userId, Long friendUserId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetFriend incoming = findRow(friendUserId, userId);
        if (incoming == null || !PetFriendStatus.PENDING.name().equals(incoming.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_FRIEND_NOT_FOUND, "没有待确认的好友申请");
        }
        upsertActive(userId, friendUserId);
        upsertActive(friendUserId, userId);
        friendMapper.update(null, new LambdaUpdateWrapper<PetFriend>()
                .set(PetFriend::getStatus, PetFriendStatus.ACTIVE.name())
                .eq(PetFriend::getUserId, friendUserId)
                .eq(PetFriend::getFriendUserId, userId));
        Pet target = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, friendUserId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        achievementService.evaluate(pet, PetAchievementService.Event.FRIEND);
        eventProducer.publish(RocketMQConfig.PET_TAG_FRIEND, new PetEventProducer.PetEventMessage(
                "FRIEND_ACCEPTED:" + userId + ":" + friendUserId,
                String.valueOf(friendUserId), "PET_FRIEND_REQUEST",
                "好友确认啦！",
                pet.getName() + " 的主人答应了你的好友申请，去互相串个门吧～",
                String.valueOf(pet.getId()), "PET_FRIEND_REQUEST"));
        return toVo(findRow(userId, friendUserId),
                target, resolveNicknames(List.of(friendUserId)), userId);
    }

    @Override
    @Transactional
    public PetFriendVO reject(Long userId, Long friendUserId) {
        petService.requireOwnedPet(userId);
        PetFriend incoming = findRow(friendUserId, userId);
        if (incoming == null || !PetFriendStatus.PENDING.name().equals(incoming.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_FRIEND_NOT_FOUND, "没有待确认的好友申请");
        }
        friendMapper.update(null, new LambdaUpdateWrapper<PetFriend>()
                .set(PetFriend::getStatus, PetFriendStatus.REJECTED.name())
                .eq(PetFriend::getUserId, friendUserId)
                .eq(PetFriend::getFriendUserId, userId));
        Pet target = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, friendUserId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        return toVo(incoming, target, resolveNicknames(List.of(friendUserId)), friendUserId);
    }

    @Override
    @Transactional
    public void remove(Long userId, Long friendUserId) {
        petService.requireOwnedPet(userId);
        friendMapper.delete(new LambdaQueryWrapper<PetFriend>()
                .eq(PetFriend::getUserId, userId)
                .eq(PetFriend::getFriendUserId, friendUserId));
        friendMapper.delete(new LambdaQueryWrapper<PetFriend>()
                .eq(PetFriend::getUserId, friendUserId)
                .eq(PetFriend::getFriendUserId, userId));
    }

    @Override
    @Transactional
    public PetFriendVisitResultVO visit(Long userId, Long friendUserId) {
        PetFriend relation = findRow(userId, friendUserId);
        if (relation == null || !PetFriendStatus.ACTIVE.name().equals(relation.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_FRIEND_NOT_FOUND, "还不是好友，先去加个好友吧");
        }
        requireVisitQuota(userId);
        PetRoomVisitVO room = homeService.visitFriendRoom(userId, friendUserId);
        friendMapper.update(null, new LambdaUpdateWrapper<PetFriend>()
                .setSql("visit_count = visit_count + 1")
                .set(PetFriend::getLastVisitAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetFriend::getUserId, userId)
                .eq(PetFriend::getFriendUserId, friendUserId));
        // 好友层埋点：关系亲密度（若两只宠物已建立关系）+ 每日任务
        Pet myPet = petService.requireOwnedPet(userId);
        Pet friendPet = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, friendUserId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        boolean relationIntimacy = false;
        if (friendPet != null) {
            relationService.gainBetween(myPet, friendPet, PetRelationAction.VISIT);
            relationIntimacy = true;
        }
        dailyQuestService.record(myPet, PetQuestType.FRIEND_VISIT, 1);
        dailyQuestService.record(myPet, PetQuestType.VISIT, 1);
        String nickname = resolveNicknames(List.of(friendUserId))
                .getOrDefault(friendUserId, NICKNAME_PLACEHOLDER);
        PetFriend after = findRow(userId, friendUserId);
        String message = room.visitedToday()
                ? "今天已经去过 " + nickname + " 家啦，明天再来吧"
                : myPet.getName() + " 去 " + nickname + " 家做客，心情 +" + room.rewardHappiness()
                        + "，经验 +" + room.rewardExp() + "～";
        return new PetFriendVisitResultVO(room, nickname,
                after != null && after.getVisitCount() != null ? after.getVisitCount() : 1,
                relationIntimacy, message);
    }

    // ---------------- 内部 ----------------

    private PetFriend findRow(Long userId, Long friendUserId) {
        return friendMapper.selectOne(new LambdaQueryWrapper<PetFriend>()
                .eq(PetFriend::getUserId, userId)
                .eq(PetFriend::getFriendUserId, friendUserId)
                .last("LIMIT 1"));
    }

    /** 双向落库：不存在则插入 ACTIVE，存在则置 ACTIVE（uk 幂等） */
    private void upsertActive(Long userId, Long friendUserId) {
        PetFriend row = findRow(userId, friendUserId);
        if (row == null) {
            PetFriend created = new PetFriend();
            created.setUserId(userId);
            created.setFriendUserId(friendUserId);
            created.setStatus(PetFriendStatus.ACTIVE.name());
            created.setSource("VISIT");
            created.setVisitCount(0);
            try {
                friendMapper.insert(created);
            } catch (DuplicateKeyException e) {
                log.debug("好友行并发生成，忽略: userId={}, friendUserId={}", userId, friendUserId);
            }
            return;
        }
        if (!PetFriendStatus.ACTIVE.name().equals(row.getStatus())) {
            friendMapper.update(null, new LambdaUpdateWrapper<PetFriend>()
                    .set(PetFriend::getStatus, PetFriendStatus.ACTIVE.name())
                    .eq(PetFriend::getId, row.getId()));
        }
    }

    private void requireRequestQuota(Long userId) {
        consumeQuota(String.format(KEY_FRIEND_REQUEST_DAILY, userId, LocalDate.now(ZoneId.of("UTC"))),
                properties.getFriend().getRequestDailyLimit(), "今天已经发过很多好友申请啦，明天再试试吧");
    }

    private void requireVisitQuota(Long userId) {
        consumeQuota(String.format(KEY_FRIEND_VISIT_DAILY, userId, LocalDate.now(ZoneId.of("UTC"))),
                properties.getFriend().getDailyVisitLimit(), "今天的好友互访次数用完啦，明天再来吧");
    }

    private void consumeQuota(String key, int limit, String message) {
        try {
            Long used = redisTemplate.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
            if (used != null && used > limit) {
                throw new BusinessException(PetErrorCodes.PET_INTERACTION_RATE_LIMITED, message);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("好友限频 Redis 故障，Fail-Open 放行: key={}", key, e);
        }
    }

    private int todayVisitCount(Long userId) {
        try {
            String value = redisTemplate.opsForValue().get(
                    String.format(KEY_FRIEND_VISIT_DAILY, userId, LocalDate.now(ZoneId.of("UTC"))));
            return value != null ? Integer.parseInt(value) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private PetFriendVO toVo(PetFriend row, Pet pet, Map<Long, String> nicknames, Long selfUserId) {
        boolean mine = selfUserId.equals(row.getUserId());
        String direction = PetFriendStatus.ACTIVE.name().equals(row.getStatus())
                ? "ACTIVE" : (mine ? "OUTGOING" : "INCOMING");
        return new PetFriendVO(
                mine ? row.getFriendUserId() : row.getUserId(),
                nicknames.getOrDefault(mine ? row.getFriendUserId() : row.getUserId(), NICKNAME_PLACEHOLDER),
                pet != null ? pet.getId() : null,
                pet != null ? pet.getName() : null,
                pet != null ? pet.getSpecies() : null,
                pet != null ? pet.getLevel() : null,
                pet != null && pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0,
                pet != null ? pet.getSkinCode() : null,
                row.getStatus(), direction,
                row.getVisitCount() != null ? row.getVisitCount() : 0,
                row.getLastVisitAt(),
                row.getLastVisitAt() != null && row.getLastVisitAt().toLocalDate()
                        .equals(LocalDate.now(ZoneId.of("UTC"))));
    }

    private Map<Long, Pet> mainPets(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Pet> result = new HashMap<>();
        petMapper.selectList(new LambdaQueryWrapper<Pet>()
                        .in(Pet::getUserId, userIds)
                        .eq(Pet::getIsActive, true))
                .forEach(pet -> result.put(pet.getUserId(), pet));
        return result;
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
