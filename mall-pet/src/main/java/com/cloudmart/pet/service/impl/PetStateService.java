package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.enums.PetGrowthStage;
import com.cloudmart.pet.repository.PetMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 宠物状态领域服务：懒更新结算 + 经验/等级成长。
 *
 * <p>懒更新（原文档 §9）：数据库不跑每秒定时器，任何读/写入口先按
 * {@code now - last_state_update_at} 推算自然变化并 CAS 落库（以游标相等为并发条件，
 * 多端并发只有一个写者生效，其余读方拿到已结算数据）。数值速率来自
 * {@link PetProperties}（Nacos 可热更），客户端不参与任何计算。</p>
 */
@Component
@Slf4j
public class PetStateService {

    private static final int STATE_MAX = 100;
    private static final int ATTRIBUTE_MAX = 999;
    private static final int LEVEL_MAX = 100;

    private final PetMapper petMapper;
    private final PetProperties properties;

    public PetStateService(PetMapper petMapper, PetProperties properties) {
        this.petMapper = petMapper;
        this.properties = properties;
    }

    /**
     * 结算自然变化（饥饿下降/心情下降/精力恢复/清洁下降），CAS 以 lastStateUpdateAt 为条件。
     * 更新未命中（并发写者已结算）时重读实体，保证调用方拿到最新状态。
     */
    public Pet applyIdleDecay(Pet pet) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime cursor = pet.getLastStateUpdateAt();
        if (cursor == null) {
            cursor = pet.getCreatedAt() != null ? pet.getCreatedAt() : now;
        }
        double hours = Duration.between(cursor, now).toMillis() / 3_600_000.0;
        if (hours <= 0) {
            return pet;
        }
        double capped = Math.min(hours, properties.getDecay().getMaxIdleHours());

        int hunger = clamp(pet.getHunger() - (int) Math.floor(capped * properties.getDecay().getHungerPerHour()));
        int energy = clamp(pet.getEnergy() + (int) Math.floor(capped * properties.getDecay().getEnergyRecoverPerHour()));
        int cleanliness = clamp(pet.getCleanliness() - (int) Math.floor(capped * properties.getDecay().getCleanlinessPerHour()));
        double happinessPerHour = properties.getDecay().getHappinessPerHour();
        if (pet.getHunger() < properties.getDecay().getHungerMoodThreshold()) {
            // 饿肚子心情额外下滑（原文档 §7 养成联动）
            happinessPerHour += properties.getDecay().getHungerMoodExtraPerHour();
        }
        int happiness = clamp(pet.getHappiness() - (int) Math.floor(capped * happinessPerHour));

        int updated = petMapper.update(null, new LambdaUpdateWrapper<Pet>()
                .set(Pet::getHunger, hunger)
                .set(Pet::getEnergy, energy)
                .set(Pet::getCleanliness, cleanliness)
                .set(Pet::getHappiness, happiness)
                .set(Pet::getLastStateUpdateAt, now)
                .eq(Pet::getId, pet.getId())
                .eq(Pet::getLastStateUpdateAt, pet.getLastStateUpdateAt()));
        if (updated > 0) {
            pet.setHunger(hunger);
            pet.setEnergy(energy);
            pet.setCleanliness(cleanliness);
            pet.setHappiness(happiness);
            pet.setLastStateUpdateAt(now);
        } else {
            Pet latest = petMapper.selectById(pet.getId());
            if (latest != null) {
                copyMutableState(latest, pet);
            }
        }
        return pet;
    }

    /**
     * 增加经验并处理升级（可跨多级）。属性成长：每级力量/智力/敏捷/魅力 +1、上限 +5。
     * 直接在传入实体上变更并落库（乐观锁 version 条件由 MP @Version 自动附加）。
     *
     * @return 实际升到的等级数（0 表示未升级）
     */
    public int grantExp(Pet pet, int expGain) {
        if (expGain <= 0) {
            return 0;
        }
        int exp = pet.getExp() + expGain;
        int level = pet.getLevel();
        int levelups = 0;
        while (level < LEVEL_MAX && exp >= expToNext(level)) {
            exp -= expToNext(level);
            level++;
            levelups++;
        }
        if (level >= LEVEL_MAX) {
            exp = Math.min(exp, expToNext(LEVEL_MAX));
        }

        pet.setExp(exp);
        pet.setLevel(level);
        if (levelups > 0) {
            pet.setMaxHp(pet.getMaxHp() + 5 * levelups);
            pet.setHp(Math.min(pet.getMaxHp(), pet.getHp() + 5 * levelups));
            pet.setStrength(grow(pet.getStrength(), levelups));
            pet.setIntelligence(grow(pet.getIntelligence(), levelups));
            pet.setAgility(grow(pet.getAgility(), levelups));
            pet.setCharm(grow(pet.getCharm(), levelups));
            pet.setGrowthStage(growthStageFor(level));
        }
        petMapper.updateById(pet);
        return levelups;
    }

    /** 升到下一级所需经验：expBase * level^1.5 */
    public int expToNext(int level) {
        return (int) Math.round(properties.getLevel().getExpBase() * Math.pow(level, 1.5));
    }

    /** 按等级推进成长阶段：Lv1-9 幼年、Lv10-19 成长、Lv20+ 成年 */
    public String growthStageFor(int level) {
        if (level >= 20) {
            return PetGrowthStage.ADULT.name();
        }
        if (level >= 10) {
            return PetGrowthStage.YOUNG.name();
        }
        return PetGrowthStage.BABY.name();
    }

    private int grow(int value, int times) {
        return Math.min(ATTRIBUTE_MAX, value + times);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(STATE_MAX, value));
    }

    private void copyMutableState(Pet source, Pet target) {
        target.setHunger(source.getHunger());
        target.setEnergy(source.getEnergy());
        target.setCleanliness(source.getCleanliness());
        target.setHappiness(source.getHappiness());
        target.setLastStateUpdateAt(source.getLastStateUpdateAt());
        target.setExp(source.getExp());
        target.setLevel(source.getLevel());
        target.setHp(source.getHp());
        target.setMaxHp(source.getMaxHp());
        target.setStrength(source.getStrength());
        target.setIntelligence(source.getIntelligence());
        target.setAgility(source.getAgility());
        target.setCharm(source.getCharm());
        target.setGrowthStage(source.getGrowthStage());
        target.setVersion(source.getVersion());
    }

    /** 按用户加载宠物（软删过滤由 @TableLogic 处理），不存在抛 PET_NOT_FOUND 语义由调用方处理 */
    public Pet findByUserId(Long userId) {
        return petMapper.selectOne(new LambdaQueryWrapper<Pet>().eq(Pet::getUserId, userId));
    }
}
