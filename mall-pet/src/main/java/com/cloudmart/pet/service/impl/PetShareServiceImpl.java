package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetAchievementRecord;
import com.cloudmart.pet.entity.PetAchievement;
import com.cloudmart.pet.entity.PetBattle;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.enums.PetBattleStatus;
import com.cloudmart.pet.enums.PetBottleOutcome;
import com.cloudmart.pet.repository.PetAchievementMapper;
import com.cloudmart.pet.repository.PetAchievementRecordMapper;
import com.cloudmart.pet.repository.PetBattleMapper;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.service.PetShareService;
import com.cloudmart.pet.vo.PetShareCardVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分享卡片实现（文案服务端生成；发布动作由用户在社区发帖页完成）。
 */
@Service
@Slf4j
public class PetShareServiceImpl implements PetShareService {

    private final PetService petService;
    private final PetMapper petMapper;
    private final PetAchievementRecordMapper achievementRecordMapper;
    private final PetAchievementMapper achievementMapper;
    private final PetBattleMapper battleMapper;
    private final PetBottleRecordMapper bottleRecordMapper;

    public PetShareServiceImpl(PetService petService,
                               PetMapper petMapper,
                               PetAchievementRecordMapper achievementRecordMapper,
                               PetAchievementMapper achievementMapper,
                               PetBattleMapper battleMapper,
                               PetBottleRecordMapper bottleRecordMapper) {
        this.petService = petService;
        this.petMapper = petMapper;
        this.achievementRecordMapper = achievementRecordMapper;
        this.achievementMapper = achievementMapper;
        this.battleMapper = battleMapper;
        this.bottleRecordMapper = bottleRecordMapper;
    }

    @Override
    public PetShareCardVO buildCard(Long userId, String type) {
        Pet pet = petService.requireOwnedPet(userId);
        String cardType = type == null || type.isBlank() ? "DAILY" : type.toUpperCase();
        return switch (cardType) {
            case "LEVEL_UP" -> levelUpCard(pet);
            case "ACHIEVEMENT" -> achievementCard(pet);
            case "BOTTLE" -> bottleCard(pet);
            case "BATTLE" -> battleCard(pet);
            case "DAILY" -> dailyCard(pet);
            default -> throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "分享卡片类型非法");
        };
    }

    /** 成长卡片：等级 + 四维亮点 */
    private PetShareCardVO levelUpCard(Pet pet) {
        String title = pet.getName() + " 的成长日记";
        String content = "我的宠物「" + pet.getName() + "」已经Lv." + pet.getLevel() + "啦！"
                + "力量" + pet.getStrength() + " · 智力" + pet.getIntelligence()
                + " · 敏捷" + pet.getAgility() + " · 魅力" + pet.getCharm() + "，"
                + "正在" + "社区里茁壮成长～";
        return new PetShareCardVO("LEVEL_UP", title, content, "Lv." + pet.getLevel());
    }

    /** 最近成就卡片 */
    private PetShareCardVO achievementCard(Pet pet) {
        List<PetAchievementRecord> records = achievementRecordMapper.selectList(
                new LambdaQueryWrapper<PetAchievementRecord>()
                        .eq(PetAchievementRecord::getPetId, pet.getId())
                        .orderByDesc(PetAchievementRecord::getAchievedAt)
                        .last("LIMIT 1"));
        if (records.isEmpty()) {
            return new PetShareCardVO("ACHIEVEMENT", pet.getName() + " 的成就之路",
                    "「" + pet.getName() + "」正在努力解锁第一枚成就徽章，敬请期待！", "0 枚成就");
        }
        PetAchievement achievement = achievementMapper.selectById(records.get(0).getAchievementId());
        String name = achievement != null ? achievement.getName() : "神秘成就";
        String icon = achievement != null ? achievement.getIcon() : "🏆";
        long total = achievementRecordMapper.selectCount(new LambdaQueryWrapper<PetAchievementRecord>()
                .eq(PetAchievementRecord::getPetId, pet.getId()));
        String content = icon + " 「" + pet.getName() + "」刚刚达成了成就「" + name + "」！"
                + "目前已收集 " + total + " 枚成就徽章～";
        return new PetShareCardVO("ACHIEVEMENT", pet.getName() + " 达成新成就", content, total + " 枚成就");
    }

    /** 捞瓶战果卡片 */
    private PetShareCardVO bottleCard(Pet pet) {
        long caught = bottleRecordMapper.selectCount(new LambdaQueryWrapper<PetBottleRecord>()
                .eq(PetBottleRecord::getPetId, pet.getId())
                .eq(PetBottleRecord::getOutcome, PetBottleOutcome.CAUGHT.name()));
        long rare = bottleRecordMapper.selectCount(new LambdaQueryWrapper<PetBottleRecord>()
                .eq(PetBottleRecord::getPetId, pet.getId())
                .eq(PetBottleRecord::getOutcome, PetBottleOutcome.CAUGHT.name())
                .in(PetBottleRecord::getRarity, "RARE", "PET", "EASTER_EGG"));
        String content = "🍾 「" + pet.getName() + "」已经帮主人捞到了 " + caught + " 只漂流瓶"
                + (rare > 0 ? "，其中还包含 " + rare + " 只特殊瓶子！" : "！大海的秘密正在被一点点揭开～");
        return new PetShareCardVO("BOTTLE", pet.getName() + " 的捞瓶战果", content, caught + " 只瓶子");
    }

    /** 对战战绩卡片 */
    private PetShareCardVO battleCard(Pet pet) {
        long wins = battleMapper.selectCount(new LambdaQueryWrapper<PetBattle>()
                .eq(PetBattle::getWinnerPetId, pet.getId())
                .eq(PetBattle::getStatus, PetBattleStatus.FINISHED.name()));
        long total = battleMapper.selectCount(new LambdaQueryWrapper<PetBattle>()
                .and(q -> q.eq(PetBattle::getAttackerPetId, pet.getId())
                        .or().eq(PetBattle::getDefenderPetId, pet.getId()))
                .eq(PetBattle::getStatus, PetBattleStatus.FINISHED.name()));
        String content = wins >= total && total > 0
                ? "⚔️ 「" + pet.getName() + "」保持不败战绩 " + wins + " 胜 0 负，谁来挑战？"
                : "⚔️ 「" + pet.getName() + "」战绩：" + wins + " 胜 " + Math.max(0, total - wins) + " 负，越战越勇！";
        return new PetShareCardVO("BATTLE", pet.getName() + " 的对战战绩", content, wins + "/" + total);
    }

    /** 日常卡片 */
    private PetShareCardVO dailyCard(Pet pet) {
        String mood = pet.getHappiness() >= 80 ? "元气满满" : pet.getHappiness() >= 50 ? "心情不错" : "需要陪伴";
        String content = "🐾 今日份的宠物日常：「" + pet.getName() + "」" + mood
                + "（心情 " + pet.getHappiness() + "），快来社区养一只属于你的宠物吧！";
        return new PetShareCardVO("DAILY", pet.getName() + " 的宠物日常", content, mood);
    }
}
