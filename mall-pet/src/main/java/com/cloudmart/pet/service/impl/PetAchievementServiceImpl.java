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
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetBattleStatus;
import com.cloudmart.pet.enums.PetBottleOutcome;
import com.cloudmart.pet.enums.PetChatRole;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetAchievementMapper;
import com.cloudmart.pet.repository.PetAchievementRecordMapper;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetBattleMapper;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetChatMessageMapper;
import com.cloudmart.pet.repository.PetChatSessionMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.impl.PetStateService;
import com.cloudmart.pet.vo.PetAchievementVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * 成就服务实现：判定条件全部基于既有业务表计数（不另建计数器表），
 * 达成记录 uk_pet_ach_record 幂等——重复事件不重复发奖。
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
    private final PetStateService stateService;
    private final PetEventProducer eventProducer;

    public PetAchievementServiceImpl(PetAchievementMapper achievementMapper,
                                     PetAchievementRecordMapper recordMapper,
                                     PetActivityMapper activityMapper,
                                     PetBattleMapper battleMapper,
                                     PetBottleRecordMapper bottleRecordMapper,
                                     PetChatSessionMapper chatSessionMapper,
                                     PetChatMessageMapper chatMessageMapper,
                                     PetStateService stateService,
                                     PetEventProducer eventProducer) {
        this.achievementMapper = achievementMapper;
        this.recordMapper = recordMapper;
        this.activityMapper = activityMapper;
        this.battleMapper = battleMapper;
        this.bottleRecordMapper = bottleRecordMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
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
        List<PetAchievement> candidates = achievementMapper.selectList(new LambdaQueryWrapper<PetAchievement>()
                .eq(PetAchievement::getEnabled, true));
        List<Long> achievedIds = recordMapper.selectList(new LambdaQueryWrapper<PetAchievementRecord>()
                        .eq(PetAchievementRecord::getPetId, pet.getId()))
                .stream().map(PetAchievementRecord::getAchievementId).toList();
        for (PetAchievement achievement : candidates) {
            if (achievedIds.contains(achievement.getId())) {
                continue;
            }
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
                pet.getUserId(), "PET_ACHIEVEMENT",
                "成就达成：" + achievement.getName(),
                "恭喜！宠物达成了成就「" + achievement.getName() + "」：" + achievement.getDescription(),
                achievement.getId(), "PET_ACHIEVEMENT"));
    }

    private PetAchievementVO toVo(PetAchievement achievement, boolean achieved, LocalDateTime achievedAt) {
        return new PetAchievementVO(achievement.getId(), achievement.getCode(), achievement.getName(),
                achievement.getDescription(), achievement.getIcon(), achievement.getConditionValue(),
                achievement.getExpReward(), achieved, achievedAt);
    }
}
