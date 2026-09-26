package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.PostWallMessageRequest;
import com.cloudmart.pet.dto.ReplyWallMessageRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetRoom;
import com.cloudmart.pet.entity.PetWallLike;
import com.cloudmart.pet.entity.PetWallMessage;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.enums.PetRelationAction;
import com.cloudmart.pet.enums.PetWallStatus;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetRoomMapper;
import com.cloudmart.pet.repository.PetWallLikeMapper;
import com.cloudmart.pet.repository.PetWallMessageMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetRelationService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.service.PetWallService;
import com.cloudmart.pet.vo.PetWallLikeVO;
import com.cloudmart.pet.vo.PetWallMessageVO;
import com.cloudmart.pet.vo.PetWallPageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 宠物留言墙实现（三期）。
 *
 * <p>审核与治理：留言软删（作者/墙主人）+ 管理员隐藏（{@code HIDDEN}），
 * 用户端只读 {@code NORMAL}，管理端可见全量（保留溯源能力）；
 * 点赞走 {@code uk_pet_wall_like} 幂等，重复点击不重复计数。</p>
 *
 * <p>社交收益：留言/回复给"我的宠物"加亲密度（带宠物出门社交），
 * 若与墙主人宠物已建立关系则同时加关系亲密度；被留言方收到宠物口吻提醒。</p>
 */
@Service
@Slf4j
public class PetWallServiceImpl implements PetWallService {

    static final String KEY_WALL_POST_DAILY = "pet:ratelimit:wallpost:%d:%s";
    static final String KEY_WALL_LIKE_DAILY = "pet:ratelimit:walllike:%d:%s";

    private static final String NICKNAME_PLACEHOLDER = "邻居";
    private static final int MAX_SIZE = 20;

    private final PetService petService;
    private final PetMapper petMapper;
    private final PetWallMessageMapper wallMessageMapper;
    private final PetWallLikeMapper wallLikeMapper;
    private final PetRoomMapper roomMapper;
    private final PetRelationService relationService;
    private final PetIntimacyService intimacyService;
    private final PetAchievementService achievementService;
    private final PetDailyQuestService dailyQuestService;
    private final PetEventProducer eventProducer;
    private final WishFeignClient wishFeignClient;
    private final PetProperties properties;
    private final StringRedisTemplate redisTemplate;

    public PetWallServiceImpl(PetService petService,
                              PetMapper petMapper,
                              PetWallMessageMapper wallMessageMapper,
                              PetWallLikeMapper wallLikeMapper,
                              PetRoomMapper roomMapper,
                              PetRelationService relationService,
                              PetIntimacyService intimacyService,
                              PetAchievementService achievementService,
                              PetDailyQuestService dailyQuestService,
                              PetEventProducer eventProducer,
                              WishFeignClient wishFeignClient,
                              PetProperties properties,
                              StringRedisTemplate redisTemplate) {
        this.petService = petService;
        this.petMapper = petMapper;
        this.wallMessageMapper = wallMessageMapper;
        this.wallLikeMapper = wallLikeMapper;
        this.roomMapper = roomMapper;
        this.relationService = relationService;
        this.intimacyService = intimacyService;
        this.achievementService = achievementService;
        this.dailyQuestService = dailyQuestService;
        this.eventProducer = eventProducer;
        this.wishFeignClient = wishFeignClient;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public PetWallPageVO list(Long userId, Long petId, Integer page, Integer size) {
        Pet owner = requireWallPet(petId);
        PetRoom room = roomMapper.selectOne(new LambdaQueryWrapper<PetRoom>()
                .eq(PetRoom::getPetId, owner.getId())
                .last("LIMIT 1"));
        boolean isOwner = userId != null && userId.equals(owner.getUserId());
        if (!isOwner && room != null && !Boolean.TRUE.equals(room.getIsPublic())) {
            throw new BusinessException(PetErrorCodes.PET_ROOM_PRIVATE, "对方还没有开放留言墙");
        }
        int safePage = page != null && page > 0 ? page : 1;
        int safeSize = size != null && size > 0
                ? Math.min(size, properties.getWall().getMaxPageSize())
                : MAX_SIZE;
        long total = wallMessageMapper.selectCount(new LambdaQueryWrapper<PetWallMessage>()
                .eq(PetWallMessage::getPetId, owner.getId())
                .eq(PetWallMessage::getStatus, PetWallStatus.NORMAL.name())
                .isNull(PetWallMessage::getParentId));
        List<PetWallMessage> roots = wallMessageMapper.selectList(new LambdaQueryWrapper<PetWallMessage>()
                .eq(PetWallMessage::getPetId, owner.getId())
                .eq(PetWallMessage::getStatus, PetWallStatus.NORMAL.name())
                .isNull(PetWallMessage::getParentId)
                .orderByDesc(PetWallMessage::getId)
                .last("LIMIT " + safeSize + " OFFSET " + (long) (safePage - 1) * safeSize));
        Map<Long, List<PetWallMessage>> repliesByParent = new HashMap<>();
        if (!roots.isEmpty()) {
            List<Long> rootIds = roots.stream().map(PetWallMessage::getId).toList();
            wallMessageMapper.selectList(new LambdaQueryWrapper<PetWallMessage>()
                            .in(PetWallMessage::getParentId, rootIds)
                            .eq(PetWallMessage::getStatus, PetWallStatus.NORMAL.name())
                            .orderByAsc(PetWallMessage::getId))
                    .forEach(reply -> repliesByParent
                            .computeIfAbsent(reply.getParentId(), key -> new ArrayList<>())
                            .add(reply));
        }
        Set<Long> likedIds = likedMessageIds(userId, roots);
        Map<Long, String> nicknames = resolveNicknames(authorIds(roots));
        Map<Long, Pet> authorPets = authorPets(roots);
        List<PetWallMessageVO> messages = roots.stream()
                .map(root -> toVo(root, owner, userId, likedIds, nicknames, authorPets,
                        repliesByParent.getOrDefault(root.getId(), List.of())))
                .toList();
        String ownerNickname = resolveNicknames(List.of(owner.getUserId()))
                .getOrDefault(owner.getUserId(), NICKNAME_PLACEHOLDER);
        return new PetWallPageVO(owner.getId(), owner.getName(), owner.getSpecies(), owner.getLevel(),
                owner.getEvolutionStage() != null ? owner.getEvolutionStage() : 0, owner.getSkinCode(),
                ownerNickname,
                room != null ? room.getWelcomeMessage() : null,
                room == null || Boolean.TRUE.equals(room.getIsPublic()),
                safePage, safeSize, total,
                properties.getWall().getDailyPostLimit(), messages);
    }

    @Override
    @Transactional
    public PetWallMessageVO post(Long userId, PostWallMessageRequest request) {
        Pet me = petService.requireOwnedPet(userId);
        Pet owner = requireWallPet(request.petId());
        if (userId.equals(owner.getUserId())) {
            throw new BusinessException(PetErrorCodes.PET_WALL_FORBIDDEN, "这是自己的留言墙，回复访客就好啦");
        }
        requireRoomPublic(owner, false);
        String content = normalize(request.content());
        requirePostQuota(userId, owner.getId());

        PetWallMessage message = new PetWallMessage();
        message.setPetId(owner.getId());
        message.setUserId(owner.getUserId());
        message.setAuthorUserId(userId);
        message.setAuthorPetId(me.getId());
        message.setContent(content);
        message.setMood(request.mood());
        message.setStatus(PetWallStatus.NORMAL.name());
        message.setLikeCount(0);
        message.setReplyCount(0);
        wallMessageMapper.insert(message);

        intimacyService.gain(me, PetIntimacySource.WALL);
        dailyQuestService.record(me, PetQuestType.WALL_MESSAGE, 1);
        achievementService.evaluate(me, PetAchievementService.Event.WALL);
        relationService.gainBetween(me, owner, PetRelationAction.WALL);
        eventProducer.publish(RocketMQConfig.PET_TAG_WALL, new PetEventProducer.PetEventMessage(
                "WALL_MESSAGE:" + message.getId(),
                String.valueOf(owner.getUserId()), "PET_WALL_MESSAGE",
                "留言墙有新留言！",
                me.getName() + " 在 " + owner.getName() + " 的留言墙写下：「" + content + "」",
                String.valueOf(message.getId()), "PET_WALL_MESSAGE"));
        return toVo(message, owner, userId, Set.of(),
                resolveNicknames(List.of(userId)), authorPets(List.of(message)), List.of());
    }

    @Override
    @Transactional
    public PetWallMessageVO reply(Long userId, ReplyWallMessageRequest request) {
        Pet me = petService.requireOwnedPet(userId);
        PetWallMessage root = wallMessageMapper.selectById(request.messageId());
        if (root == null || !PetWallStatus.NORMAL.name().equals(root.getStatus()) || root.getParentId() != null) {
            throw new BusinessException(PetErrorCodes.PET_WALL_MESSAGE_NOT_FOUND, "这条留言不存在或已删除");
        }
        if (!userId.equals(root.getUserId())) {
            throw new BusinessException(PetErrorCodes.PET_WALL_FORBIDDEN, "只有墙主人可以回复留言");
        }
        Pet author = root.getAuthorPetId() != null ? petMapper.selectById(root.getAuthorPetId()) : null;
        PetWallMessage reply = new PetWallMessage();
        reply.setPetId(root.getPetId());
        reply.setUserId(userId);
        reply.setAuthorUserId(userId);
        reply.setAuthorPetId(me.getId());
        reply.setParentId(root.getId());
        reply.setContent(normalize(request.content()));
        reply.setStatus(PetWallStatus.NORMAL.name());
        reply.setLikeCount(0);
        reply.setReplyCount(0);
        wallMessageMapper.insert(reply);
        wallMessageMapper.update(null, new LambdaUpdateWrapper<PetWallMessage>()
                .setSql("reply_count = reply_count + 1")
                .eq(PetWallMessage::getId, root.getId()));

        intimacyService.gain(me, PetIntimacySource.WALL);
        dailyQuestService.record(me, PetQuestType.WALL_MESSAGE, 1);
        if (author != null) {
            relationService.gainBetween(me, author, PetRelationAction.WALL);
            eventProducer.publish(RocketMQConfig.PET_TAG_WALL, new PetEventProducer.PetEventMessage(
                    "WALL_REPLY:" + reply.getId(),
                    String.valueOf(author.getUserId()), "PET_WALL_MESSAGE",
                    "主人回复了你的留言！",
                    me.getName() + "：" + "谢谢你来看我！「" + reply.getContent() + "」",
                    String.valueOf(reply.getId()), "PET_WALL_MESSAGE"));
        }
        Pet owner = requireWallPet(root.getPetId());
        return toVo(reply, owner, userId, Set.of(),
                resolveNicknames(List.of(userId)), authorPets(List.of(reply)),
                List.of());
    }

    @Override
    @Transactional
    public void delete(Long userId, Long messageId) {
        PetWallMessage message = wallMessageMapper.selectById(messageId);
        if (message == null || !PetWallStatus.NORMAL.name().equals(message.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_WALL_MESSAGE_NOT_FOUND, "这条留言不存在或已删除");
        }
        boolean mine = userId.equals(message.getAuthorUserId());
        boolean owner = userId.equals(message.getUserId());
        if (!mine && !owner) {
            throw new BusinessException(PetErrorCodes.PET_WALL_FORBIDDEN, "只能删除自己的留言");
        }
        wallMessageMapper.update(null, new LambdaUpdateWrapper<PetWallMessage>()
                .set(PetWallMessage::getStatus, PetWallStatus.DELETED.name())
                .eq(PetWallMessage::getId, messageId)
                .eq(PetWallMessage::getStatus, PetWallStatus.NORMAL.name()));
        if (message.getParentId() != null) {
            // 回复被删：父留言回复数 -1（不小于 0）
            wallMessageMapper.update(null, new LambdaUpdateWrapper<PetWallMessage>()
                    .setSql("reply_count = GREATEST(reply_count - 1, 0)")
                    .eq(PetWallMessage::getId, message.getParentId()));
        }
    }

    @Override
    @Transactional
    public PetWallLikeVO like(Long userId, Long messageId) {
        petService.requireOwnedPet(userId);
        PetWallMessage message = wallMessageMapper.selectById(messageId);
        if (message == null || !PetWallStatus.NORMAL.name().equals(message.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_WALL_MESSAGE_NOT_FOUND, "这条留言不存在或已删除");
        }
        requireLikeQuota(userId);
        boolean newlyLiked = false;
        PetWallLike like = new PetWallLike();
        like.setMessageId(messageId);
        like.setUserId(userId);
        try {
            wallLikeMapper.insert(like);
            newlyLiked = true;
        } catch (DuplicateKeyException e) {
            // uk 幂等：已点赞则取消（再次点击 = 取消点赞）
            wallLikeMapper.delete(new LambdaQueryWrapper<PetWallLike>()
                    .eq(PetWallLike::getMessageId, messageId)
                    .eq(PetWallLike::getUserId, userId));
            wallMessageMapper.update(null, new LambdaUpdateWrapper<PetWallMessage>()
                    .setSql("like_count = GREATEST(like_count - 1, 0)")
                    .eq(PetWallMessage::getId, messageId));
            PetWallMessage latest = wallMessageMapper.selectById(messageId);
            return new PetWallLikeVO(messageId,
                    latest != null && latest.getLikeCount() != null ? latest.getLikeCount() : 0,
                    false, false, "已取消点赞");
        }
        if (newlyLiked) {
            wallMessageMapper.update(null, new LambdaUpdateWrapper<PetWallMessage>()
                    .setSql("like_count = like_count + 1")
                    .eq(PetWallMessage::getId, messageId));
        }
        PetWallMessage latest = wallMessageMapper.selectById(messageId);
        return new PetWallLikeVO(messageId,
                latest != null && latest.getLikeCount() != null ? latest.getLikeCount() : 0,
                true, newlyLiked, newlyLiked ? "点了个赞～" : "已经点过赞啦");
    }

    // ---------------- 内部 ----------------

    private Pet requireWallPet(Long petId) {
        if (petId == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "请选择要查看的宠物");
        }
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !Boolean.TRUE.equals(pet.getIsPublic())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "这只宠物不存在或未公开");
        }
        return pet;
    }

    private void requireRoomPublic(Pet owner, boolean isOwner) {
        PetRoom room = roomMapper.selectOne(new LambdaQueryWrapper<PetRoom>()
                .eq(PetRoom::getPetId, owner.getId())
                .last("LIMIT 1"));
        if (!isOwner && room != null && !Boolean.TRUE.equals(room.getIsPublic())) {
            throw new BusinessException(PetErrorCodes.PET_ROOM_PRIVATE, "对方还没有开放留言墙");
        }
    }

    private String normalize(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_WALL_MESSAGE_INVALID, "留言不能为空");
        }
        String trimmed = content.trim();
        if (trimmed.length() > properties.getWall().getMaxLength()) {
            throw new BusinessException(PetErrorCodes.PET_WALL_MESSAGE_INVALID,
                    "留言最多 " + properties.getWall().getMaxLength() + " 字");
        }
        return trimmed;
    }

    private void requirePostQuota(Long userId, Long targetPetId) {
        // 跨房间累计的每日留言上限 + 同一面墙 5 分钟内不重复刷（SETNX，Fail-Open）
        try {
            String key = String.format(KEY_WALL_POST_DAILY, userId, LocalDate.now());
            Long used = redisTemplate.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
            int limit = properties.getWall().getDailyPostLimit();
            if (used != null && used > limit) {
                throw new BusinessException(PetErrorCodes.PET_WALL_RATE_LIMITED,
                        "今天已经留言 " + limit + " 条啦，明天再聊吧");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("留言限频 Redis 故障，Fail-Open 放行: userId={}", userId, e);
        }
    }

    private void requireLikeQuota(Long userId) {
        try {
            String key = String.format(KEY_WALL_LIKE_DAILY, userId, LocalDate.now());
            Long used = redisTemplate.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
            int limit = properties.getWall().getDailyLikeLimit();
            if (used != null && used > limit) {
                throw new BusinessException(PetErrorCodes.PET_WALL_RATE_LIMITED,
                        "今天已经点了很多赞啦，明天再继续吧");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("点赞限频 Redis 故障，Fail-Open 放行: userId={}", userId, e);
        }
    }

    private Set<Long> likedMessageIds(Long userId, List<PetWallMessage> roots) {
        if (userId == null || roots.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = roots.stream().map(PetWallMessage::getId).toList();
        Set<Long> liked = new HashSet<>();
        wallLikeMapper.selectList(new LambdaQueryWrapper<PetWallLike>()
                        .eq(PetWallLike::getUserId, userId)
                        .in(PetWallLike::getMessageId, ids))
                .forEach(like -> liked.add(like.getMessageId()));
        return liked;
    }

    private List<Long> authorIds(List<PetWallMessage> messages) {
        return messages.stream().map(PetWallMessage::getAuthorUserId).distinct().toList();
    }

    private Map<Long, Pet> authorPets(List<PetWallMessage> messages) {
        List<Long> petIds = messages.stream()
                .map(PetWallMessage::getAuthorPetId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (petIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Pet> result = new HashMap<>();
        petMapper.selectList(new LambdaQueryWrapper<Pet>().in(Pet::getId, petIds))
                .forEach(pet -> result.put(pet.getId(), pet));
        return result;
    }

    private PetWallMessageVO toVo(PetWallMessage message, Pet owner, Long viewerId,
                                  Set<Long> likedIds, Map<Long, String> nicknames,
                                  Map<Long, Pet> authorPets, List<PetWallMessage> replies) {
        Pet authorPet = message.getAuthorPetId() != null ? authorPets.get(message.getAuthorPetId()) : null;
        List<PetWallMessageVO> replyVos = replies.stream()
                .map(reply -> toVo(reply, owner, viewerId, likedIds, nicknames, authorPets, List.of()))
                .toList();
        return new PetWallMessageVO(
                message.getId(), message.getPetId(), message.getParentId(),
                message.getAuthorUserId(),
                nicknames.getOrDefault(message.getAuthorUserId(), NICKNAME_PLACEHOLDER),
                message.getAuthorPetId(),
                authorPet != null ? authorPet.getName() : null,
                authorPet != null ? authorPet.getSpecies() : null,
                message.getContent(), message.getMood(), message.getStatus(),
                message.getLikeCount() != null ? message.getLikeCount() : 0,
                message.getReplyCount() != null ? message.getReplyCount() : 0,
                likedIds.contains(message.getId()),
                viewerId != null && viewerId.equals(message.getAuthorUserId()),
                viewerId != null && viewerId.equals(owner.getUserId()),
                message.getParentId() != null,
                message.getCreatedAt(), replyVos);
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
