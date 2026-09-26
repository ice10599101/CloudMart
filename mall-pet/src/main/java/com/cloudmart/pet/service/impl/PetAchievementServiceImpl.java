package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetAchievement;
import com.cloudmart.pet.entity.PetAchievementRecord;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetBattle;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.entity.PetChatMessage;
import com.cloudmart.pet.entity.PetChatSession;
import com.cloudmart.pet.entity.PetDailyQuest;
import com.cloudmart.pet.entity.PetFriend;
import com.cloudmart.pet.entity.PetRelation;
import com.cloudmart.pet.entity.PetRoom;
import com.cloudmart.pet.entity.PetWallMessage;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetBattleStatus;
import com.cloudmart.pet.enums.PetBottleOutcome;
import com.cloudmart.pet.enums.PetChatRole;
import com.cloudmart.pet.enums.PetFriendStatus;
import com.cloudmart.pet.enums.PetQuestStatus;
import com.cloudmart.pet.enums.PetRelationStatus;
import com.cloudmart.pet.enums.PetWallStatus;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetAchievementMapper;
import com.cloudmart.pet.repository.PetAchievementRecordMapper;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetBattleMapper;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetChatMessageMapper;
import com.cloudmart.pet.repository.PetChatSessionMapper;
import com.cloudmart.pet.repository.PetDailyQuestMapper;
import com.cloudmart.pet.repository.PetFriendMapper;
import com.cloudmart.pet.repository.PetRelationMapper;
import com.cloudmart.pet.repository.PetRoomMapper;
import com.cloudmart.pet.repository.PetWallMessageMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.vo.PetAchievementVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 成就服务实现：判定条件全部基于既有业务表计数（不另建计数器表），
 * 达成记录 uk_pet_ach_record 幂等——重复事件不重复发奖。
 *
 * <p>评估范围由 {@link com.cloudmart.pet.service.PetAchievementService.Event} 裁剪
 * （见 {@link #evaluate}）：喂食不会去 COUNT 捞瓶流水，避免 N+1 查询放大。</p>
 */
@Service
@Slf4j
public class PetAchievementServiceImpl implements PetAchievementService {

    private final PetAchievementMapper achievementMapper;
    private final PetAchievementRecordMapper recordMapper;
    private final PetActivityMapper activityMapper;
    private final PetBattleMapper battleMapper;
    private final PetBottleRecordMapper bottleRecordMapper;
    private final PetChatSessionMapper chatSessionMapper;
    private final PetChatMessageMapper chatMessageMapper;
    private final PetRelationMapper relationMapper;
    private final PetFriendMapper friendMapper;
    private final PetWallMessageMapper wallMessageMapper;
    private final PetRoomMapper roomMapper;
    private final PetDailyQuestMapper dailyQuestMapper;
    private final PetStateService stateService;
    private final PetEventProducer eventProducer;

    public PetAchievementServiceImpl(PetAchievementMapper achievementMapper,
                                     PetAchievementRecordMapper recordMapper,
                                     PetActivityMapper activityMapper,
                                     PetBattleMapper battleMapper,
                                     PetBottleRecordMapper bottleRecordMapper,
                                     PetChatSessionMapper chatSessionMapper,
                                     PetChatMessageMapper chatMessageMapper,
                                     PetRelationMapper relationMapper,
                                     PetFriendMapper friendMapper,
                                     PetWallMessageMapper wallMessageMapper,
                                     PetRoomMapper roomMapper,
                                     PetDailyQuestMapper dailyQuestMapper,
                                     PetStateService stateService,
                                     PetEventProducer eventProducer) {
        this.achievementMapper = achievementMapper;
        this.recordMapper = recordMapper;
        this.activityMapper = activityMapper;
        this.battleMapper = battleMapper;
        this.bottleRecordMapper = bottleRecordMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.relationMapper = relationMapper;
        this.friendMapper = friendMapper;
        this.wallMessageMapper = wallMessageMapper;
        this.roomMapper = roomMapper;
        this.dailyQuestMapper = dailyQuestMapper;
        this.stateService = stateService;
        this.eventProducer = eventProducer;
    }

    @Override
    public List<PetAchievementVO> listMy(Long userId) {
        Pet pet = stateService.findByUserId(userId);
        List<PetAchievement> all = achievementMapper.selectList(new LambdaQueryWrapper<PetAchievement>()
                .eq(PetAchievement::getEnabled, true)
                .orderByAsc(PetAchievement::getSort));
        if (pet == null) {
            return all.stream().map(a -> toVo(a, false, null)).toList();
        }
        java.util.Map<Long, LocalDateTime> achievedAtById = new java.util.HashMap<>();
        recordMapper.selectList(new LambdaQueryWrapper<PetAchievementRecord>()
                        .eq(PetAchievementRecord::getPetId, pet.getId()))
                .forEach(r -> achievedAtById.put(r.getAchievementId(), r.getAchievedAt()));
        return all.stream()
                .map(a -> toVo(a, achievedAtById.containsKey(a.getId()), achievedAtById.get(a.getId())))
                .toList();
    }

    @Override
    @Transactional
    public void evaluate(Pet pet, Event event) {
        Objects.requireNonNull(event, "成就评估事件不可为空");
        List<Long> achievedIds = recordMapper.selectList(new LambdaQueryWrapper<PetAchievementRecord>()
                        .eq(PetAchievementRecord::getPetId, pet.getId()))
                .stream().map(PetAchievementRecord::getAchievementId).toList();
        // 只保留本事件可能改变的成就：把"每次交互全表 COUNT"收敛成 O(相关成就)
        List<PetAchievement> candidates = achievementMapper.selectList(new LambdaQueryWrapper<PetAchievement>()
                        .eq(PetAchievement::getEnabled, true))
                .stream()
                .filter(achievement -> !achievedIds.contains(achievement.getId()))
                .filter(achievement -> event.concerns(achievement.getConditionType(),
                        achievement.getConditionSubtype()))
                .toList();
        for (PetAchievement achievement : candidates) {
            if (!matches(achievement, pet)) {
                continue;
            }
            award(pet, achievement);
        }
    }

    private boolean matches(PetAchievement achievement, Pet pet) {
        int threshold = achievement.getConditionValue();
        return switch (achievement.getConditionType()) {
            case "BOTTLE_COUNT" -> bottleRecordMapper.selectCount(new LambdaQueryWrapper<PetBottleRecord>()
                    .eq(PetBottleRecord::getPetId, pet.getId())
                    .eq(PetBottleRecord::getOutcome, PetBottleOutcome.CAUGHT.name())) >= threshold;
            case "BATTLE_WIN" -> battleMapper.selectCount(new LambdaQueryWrapper<PetBattle>()
                    .eq(PetBattle::getWinnerPetId, pet.getId())
                    .eq(PetBattle::getStatus, PetBattleStatus.FINISHED.name())) >= threshold;
            case "LEVEL" -> pet.getLevel() >= threshold;
            case "CHAT_COUNT" -> chatMessageCount(pet.getUserId()) >= threshold;
            case "STATS_FULL" -> Math.min(Math.min(pet.getStrength(), pet.getIntelligence()),
                    Math.min(pet.getAgility(), pet.getCharm())) >= threshold;
            case "ACTIVITY_COUNT" -> activityCount(pet.getId(), achievement.getConditionSubtype()) >= threshold;
            // 二期：串门次数（原文档 §1.1）/ 进化阶数（原文档 §89）
            case "VISIT_COUNT" -> activityCount(pet.getId(), PetActivityType.VISIT.name()) >= threshold;
            case "EVOLUTION" -> (pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0) >= threshold;
            // 三期：亲密度/关系/好友/留言/舒适度/每日任务/陪伴时长
            case "INTIMACY" -> (pet.getIntimacy() != null ? pet.getIntimacy() : 0) >= threshold;
            case "RELATION_COUNT" -> relationCount(pet) >= threshold;
            case "FRIEND_COUNT" -> friendCount(pet.getUserId()) >= threshold;
            case "WALL_MESSAGE_COUNT" -> wallMessageCount(pet.getUserId()) >= threshold;
            case "ROOM_COMFORT" -> roomComfort(pet.getId()) >= threshold;
            case "QUEST_COUNT" -> questCount(pet.getId()) >= threshold;
            case "COMPANION_HOURS" -> companionHours(pet) >= threshold;
            default -> {
                log.warn("未知成就判定类型: code={}, type={}", achievement.getCode(), achievement.getConditionType());
                yield false;
            }
        };
    }

    private long activityCount(Long petId, String subtype) {
        if (subtype == null || subtype.isBlank()) {
            return 0;
        }
        return activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getPetId, petId)
                .eq(PetActivity::getActivityType, subtype)
                .in(PetActivity::getStatus, Set.of(
                        PetActivityStatus.CLAIMED.name(), PetActivityStatus.COMPLETED.name())));
    }

    /** 关系数：ACTIVE 且（我是发起方或接收方）都算一段（三期） */
    private long relationCount(Pet pet) {
        return relationMapper.selectCount(new LambdaQueryWrapper<PetRelation>()
                .eq(PetRelation::getStatus, PetRelationStatus.ACTIVE.name())
                .and(w -> w.eq(PetRelation::getFromPetId, pet.getId())
                        .or()
                        .eq(PetRelation::getToPetId, pet.getId())));
    }

    /** 好友数：好友表双向各一行，按 user_id 单向统计即不重复（三期） */
    private long friendCount(Long userId) {
        return friendMapper.selectCount(new LambdaQueryWrapper<PetFriend>()
                .eq(PetFriend::getUserId, userId)
                .eq(PetFriend::getStatus, PetFriendStatus.ACTIVE.name()));
    }

    /** 我发出的留言数（不含已删除/被隐藏，三期） */
    private long wallMessageCount(Long userId) {
        return wallMessageMapper.selectCount(new LambdaQueryWrapper<PetWallMessage>()
                .eq(PetWallMessage::getAuthorUserId, userId)
                .eq(PetWallMessage::getStatus, PetWallStatus.NORMAL.name()));
    }

    private int roomComfort(Long petId) {
        PetRoom room = roomMapper.selectOne(new LambdaQueryWrapper<PetRoom>()
                .eq(PetRoom::getPetId, petId)
                .last("LIMIT 1"));
        return room != null && room.getComfort() != null ? room.getComfort() : 0;
    }

    /** 已领取的每日任务数（排除全清宝箱保留行，三期） */
    private long questCount(Long petId) {
        return dailyQuestMapper.selectCount(new LambdaQueryWrapper<PetDailyQuest>()
                .eq(PetDailyQuest::getPetId, petId)
                .eq(PetDailyQuest::getStatus, PetQuestStatus.CLAIMED.name())
                .ne(PetDailyQuest::getQuestCode, PetDailyQuestServiceImpl.CHEST_CODE));
    }

    /** 累计陪伴小时数（向下取整，三期） */
    private int companionHours(Pet pet) {
        long seconds = pet.getCompanionSeconds() != null ? pet.getCompanionSeconds() : 0L;
        return (int) (seconds / 3600);
    }

    private long chatMessageCount(Long userId) {
        PetChatSession session = chatSessionMapper.selectOne(new LambdaQueryWrapper<PetChatSession>()
                .eq(PetChatSession::getUserId, userId));
        if (session == null) {
            return 0;
        }
        return chatMessageMapper.selectCount(new LambdaQueryWrapper<PetChatMessage>()
                .eq(PetChatMessage::getSessionId, session.getId())
                .eq(PetChatMessage::getRole, PetChatRole.USER.name()));
    }

    private void award(Pet pet, PetAchievement achievement) {
        PetAchievementRecord record = new PetAchievementRecord();
        record.setPetId(pet.getId());
        record.setUserId(pet.getUserId());
        record.setAchievementId(achievement.getId());
        record.setAchievedAt(LocalDateTime.now(ZoneId.of("UTC")));
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            return;
        }
        stateService.grantExp(pet, achievement.getExpReward());
        eventProducer.publish(RocketMQConfig.PET_TAG_ACHIEVEMENT, new PetEventProducer.PetEventMessage(
                "ACHIEVEMENT:" + pet.getId() + ":" + achievement.getId(),
                String.valueOf(pet.getUserId()), "PET_ACHIEVEMENT",
                "成就达成：" + achievement.getName(),
                "恭喜！宠物达成了成就「" + achievement.getName() + "」：" + achievement.getDescription(),
                String.valueOf(achievement.getId()), "PET_ACHIEVEMENT"));
    }

    private PetAchievementVO toVo(PetAchievement achievement, boolean achieved, LocalDateTime achievedAt) {
        return new PetAchievementVO(achievement.getId(), achievement.getCode(), achievement.getName(),
                achievement.getDescription(), achievement.getIcon(), achievement.getConditionValue(),
                achievement.getExpReward(), achieved, achievedAt);
    }
}
