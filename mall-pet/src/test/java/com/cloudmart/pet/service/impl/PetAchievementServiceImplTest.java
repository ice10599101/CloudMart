package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetAchievement;
import com.cloudmart.pet.entity.PetAchievementRecord;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetBattle;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.entity.PetChatMessage;
import com.cloudmart.pet.entity.PetChatSession;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetAchievementMapper;
import com.cloudmart.pet.repository.PetAchievementRecordMapper;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetBattleMapper;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetChatMessageMapper;
import com.cloudmart.pet.repository.PetChatSessionMapper;
import com.cloudmart.pet.service.PetAchievementService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 成就事件裁剪测试：验证「喂食不去 COUNT 捞瓶流水」「打工只评估 ACTIVITY_COUNT/WORK」，
 * 即 event 参与候选集过滤，而不是每次交互全量扫描全部成就（修复前的 N+1 放大）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetAchievementServiceImpl 成就事件裁剪")
class PetAchievementServiceImplTest {

    @Mock
    private PetAchievementMapper achievementMapper;
    @Mock
    private PetAchievementRecordMapper recordMapper;
    @Mock
    private PetActivityMapper activityMapper;
    @Mock
    private PetBattleMapper battleMapper;
    @Mock
    private PetBottleRecordMapper bottleRecordMapper;
    @Mock
    private PetChatSessionMapper chatSessionMapper;
    @Mock
    private PetChatMessageMapper chatMessageMapper;
    @Mock
    private PetStateService stateService;
    @Mock
    private PetEventProducer eventProducer;

    @InjectMocks
    private PetAchievementServiceImpl achievementService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetAchievement.class);
        TableInfoHelper.initTableInfo(assistant, PetAchievementRecord.class);
        TableInfoHelper.initTableInfo(assistant, PetActivity.class);
        TableInfoHelper.initTableInfo(assistant, PetBattle.class);
        TableInfoHelper.initTableInfo(assistant, PetBottleRecord.class);
        TableInfoHelper.initTableInfo(assistant, PetChatSession.class);
        TableInfoHelper.initTableInfo(assistant, PetChatMessage.class);
    }

    /** 默认「没有已达成记录」，个别用例（幂等验证）会自行覆盖；故用 lenient 避免误报冗余桩 */
    @BeforeEach
    void setUp() {
        lenient().when(recordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setLevel(10);
        return pet;
    }

    private PetAchievement achievement(String code, String type, String subtype, int value) {
        PetAchievement achievement = new PetAchievement();
        achievement.setId(9003001L);
        achievement.setCode(code);
        achievement.setName(code);
        achievement.setConditionType(type);
        achievement.setConditionSubtype(subtype);
        achievement.setConditionValue(value);
        achievement.setExpReward(20);
        return achievement;
    }

    @Test
    @DisplayName("喂食事件：不触发捞瓶计数查询（候选集被裁剪）")
    void feedEventSkipsBottleCountQuery() {
        when(achievementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(achievement("BOTTLE_10", "BOTTLE_COUNT", null, 10)));

        achievementService.evaluate(pet(), PetAchievementService.Event.FEED);

        verify(bottleRecordMapper, never()).selectCount(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("喂食事件：命中 ACTIVITY_COUNT/FEED 且达标即发奖")
    void feedEventAwardsFeedAchievement() {
        when(achievementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(achievement("FEED_50", "ACTIVITY_COUNT", "FEED", 50)));
        when(activityMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(50L);

        achievementService.evaluate(pet(), PetAchievementService.Event.FEED);

        verify(recordMapper).insert(any(PetAchievementRecord.class));
        verify(eventProducer).publish(any(String.class), any(PetEventProducer.PetEventMessage.class));
    }

    @Test
    @DisplayName("打工领取事件：不匹配 CLEAN 子类型成就")
    void workClaimedIgnoresCleanAchievement() {
        when(achievementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(achievement("CLEAN_50", "ACTIVITY_COUNT", "CLEAN", 50)));

        achievementService.evaluate(pet(), PetAchievementService.Event.WORK_CLAIMED);

        verify(activityMapper, never()).selectCount(any(LambdaQueryWrapper.class));
        verify(recordMapper, never()).insert(any(PetAchievementRecord.class));
    }

    @Test
    @DisplayName("升级事件：命中等级门槛成就")
    void levelUpAwardsLevelAchievement() {
        when(achievementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(achievement("LEVEL_10", "LEVEL", null, 10)));

        achievementService.evaluate(pet(), PetAchievementService.Event.LEVEL_UP);

        verify(recordMapper).insert(any(PetAchievementRecord.class));
    }

    @Test
    @DisplayName("已达成成就不再重复发奖（幂等）")
    void achievedAchievementNotAwardedAgain() {
        PetAchievementRecord achieved = new PetAchievementRecord();
        achieved.setAchievementId(9003001L);
        when(recordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(achieved));
        when(achievementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(achievement("LEVEL_10", "LEVEL", null, 10)));

        achievementService.evaluate(pet(), PetAchievementService.Event.LEVEL_UP);

        verify(recordMapper, never()).insert(any(PetAchievementRecord.class));
    }

    @Test
    @DisplayName("捞瓶事件：命中后按 achievement tag 发送 MQ 通知")
    void bottleSettledPublishesAchievementEvent() {
        when(achievementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(achievement("FIRST_BOTTLE", "BOTTLE_COUNT", null, 1)));
        when(bottleRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        achievementService.evaluate(pet(), PetAchievementService.Event.BOTTLE_SETTLED);

        verify(eventProducer).publish(eq(RocketMQConfig.PET_TAG_ACHIEVEMENT),
                any(PetEventProducer.PetEventMessage.class));
    }
}
