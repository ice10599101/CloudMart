package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.enums.PetGrowthStage;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.util.PetIntimacyMath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 宠物状态领域服务：懒更新结算 + 经验/等级成长。
 *
 * <p>懒更新（原文档 §9）：数据库不跑每秒定时器，任何读/写入口先按
 * {@code now - last_state_update_at} 结算自然变化并 CAS 落库（以游标相等为并发条件）。</p>
 *
 * <p>B04 精度：每个属性持有独立小数余量（hunger_frac 等 DECIMAL 列）——
 * total = 余量 + 时长×速率，整数部分入属性、小数部分回写余量；高频查询不再丢弃小数变化，
 * 一个属性的取整步长不吞掉其他属性的余量。达到上限/下限后余量归零，不积累"储备变化"；
 * 超出 maxIdleHours 的时间只结算一次（截断推进游标）。饱食跨阈值时心情衰减按分段速率计算。</p>
 */
@Component
@Slf4j
public class PetStateService {

    private static final int STATE_MAX = 100;
    private static final int ATTRIBUTE_MAX = 999;
    private static final int LEVEL_MAX = 100;

    /** F4：饥饿归零持续多久进入 WEAK */
    public static final int WEAK_AFTER_HOURS = 24;
    /** F4：心情归零持续多久进入 SICK */
    public static final int SICK_AFTER_HOURS = 48;
    /** F4：恢复阈值（饱食/心情回到该值以上清除 WEAK/SICK） */
    public static final int RECOVER_THRESHOLD = 50;

    private final PetMapper petMapper;
    private final PetProperties properties;
    private final PetClock petClock;
    private final PetRankingCache rankingCache;

    public PetStateService(PetMapper petMapper, PetProperties properties, PetClock petClock,
                           PetRankingCache rankingCache) {
        this.petMapper = petMapper;
        this.properties = properties;
        this.petClock = petClock;
        this.rankingCache = rankingCache;
    }

    /**
     * 结算自然变化（饥饿下降/心情下降/精力恢复/清洁下降），CAS 以 lastStateUpdateAt 为条件。
     * 更新未命中（并发写者已结算）时重读实体，保证调用方拿到最新状态。
     */
    /**
     * F4：是否虚弱（hunger=0 且归零起点距今 ≥24h）。以列实时判定而非状态快照，
     * 避免活动结算回写 IDLE 时弱化门禁失效。
     */
    /**
     * F4/R16：是否虚弱——归零满 24h，或状态列仍为 WEAK 且未恢复到阈值（迟滞：
     * 门禁与展示同一判据，部分喂食未达恢复阈值时不再放行打工/对战）。
     */
    public boolean isWeak(Pet pet) {
        boolean zeroCrossed = pet.getHunger() != null && pet.getHunger() == 0
                && pet.getHungerZeroSince() != null
                && Duration.between(pet.getHungerZeroSince(), petClock.nowUtc()).toHours() >= WEAK_AFTER_HOURS;
        return zeroCrossed || (PetStatus.WEAK.name().equals(pet.getStatus())
                && orZero(pet.getHunger()) < RECOVER_THRESHOLD);
    }

    /** F4/R16：是否生病——同虚弱迟滞语义（恢复到阈值才解除） */
    public boolean isSick(Pet pet) {
        boolean zeroCrossed = pet.getHappiness() != null && pet.getHappiness() == 0
                && pet.getHappinessZeroSince() != null
                && Duration.between(pet.getHappinessZeroSince(), petClock.nowUtc()).toHours() >= SICK_AFTER_HOURS;
        return zeroCrossed || (PetStatus.SICK.name().equals(pet.getStatus())
                && orZero(pet.getHappiness()) < RECOVER_THRESHOLD);
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }

    public Pet applyIdleDecay(Pet pet) {
        LocalDateTime now = petClock.nowUtc();
        LocalDateTime cursor = pet.getLastStateUpdateAt();
        if (cursor == null) {
            cursor = pet.getCreatedAt() != null ? pet.getCreatedAt() : now;
        }
        double hours = Duration.between(cursor, now).toMillis() / 3_600_000.0;
        if (hours <= 0) {
            return pet;
        }
        PetProperties.Decay cfg = properties.getDecay();
        double capped = Math.min(hours, cfg.getMaxIdleHours());

        // 饥饿（下降）
        double hungerTotal = frac(pet.getHungerFrac()) + capped * cfg.getHungerPerHour();
        // 精力（恢复）、清洁（下降）：与饥饿互相独立，取整互不影响
        double energyTotal = frac(pet.getEnergyFrac()) + capped * cfg.getEnergyRecoverPerHour();
        double cleanTotal = frac(pet.getCleanlinessFrac()) + capped * cfg.getCleanlinessPerHour();

        // 心情（下降）：饱食跨阈值分段——阈值前旧速率，之后旧+额外速率
        int hungerStart = pet.getHunger();
        double happinessTotal;
        if (hungerStart < cfg.getHungerMoodThreshold()) {
            happinessTotal = frac(pet.getHappinessFrac())
                    + capped * (cfg.getHappinessPerHour() + cfg.getHungerMoodExtraPerHour());
        } else {
            double hoursToThreshold = (hungerStart - cfg.getHungerMoodThreshold()) / cfg.getHungerPerHour();
            if (capped <= hoursToThreshold) {
                happinessTotal = frac(pet.getHappinessFrac()) + capped * cfg.getHappinessPerHour();
            } else {
                happinessTotal = frac(pet.getHappinessFrac())
                        + hoursToThreshold * cfg.getHappinessPerHour()
                        + (capped - hoursToThreshold)
                        * (cfg.getHappinessPerHour() + cfg.getHungerMoodExtraPerHour());
            }
        }

        return persistDecay(pet, now,
                (int) Math.floor(hungerTotal), hungerTotal - Math.floor(hungerTotal),
                (int) Math.floor(happinessTotal), happinessTotal - Math.floor(happinessTotal),
                (int) Math.floor(energyTotal), energyTotal - Math.floor(energyTotal),
                (int) Math.floor(cleanTotal), cleanTotal - Math.floor(cleanTotal));
    }

    /** 应用整数变化、回写独立余量（触界归零不储备），CAS 落库 */
    private Pet persistDecay(Pet pet, LocalDateTime now,
                             int hungerDelta, double hungerFrac,
                             int happinessDelta, double happinessFrac,
                             int energyDelta, double energyFrac,
                             int cleanDelta, double cleanFrac) {
        int hunger = clamp(pet.getHunger() - hungerDelta);
        double nextHungerFrac = (hunger == 0 || hunger == STATE_MAX) ? 0 : hungerFrac;
        int happiness = clamp(pet.getHappiness() - happinessDelta);
        double nextHappinessFrac = (happiness == 0 || happiness == STATE_MAX) ? 0 : happinessFrac;
        int energy = clamp(pet.getEnergy() + energyDelta);
        double nextEnergyFrac = (energy == 0 || energy == STATE_MAX) ? 0 : energyFrac;
        int cleanliness = clamp(pet.getCleanliness() - cleanDelta);
        double nextCleanFrac = (cleanliness == 0 || cleanliness == STATE_MAX) ? 0 : cleanFrac;

        // F4：归零起点维护——恢复正值清 NULL，持续归零首次记录起点
        LocalDateTime nextHungerZeroSince = hunger > 0 ? null
                : (pet.getHungerZeroSince() != null ? pet.getHungerZeroSince() : now);
        LocalDateTime nextHappinessZeroSince = happiness > 0 ? null
                : (pet.getHappinessZeroSince() != null ? pet.getHappinessZeroSince() : now);
        // F4：状态快照仅用于展示——WEAK/SICK 判定与解除都在这里惰性落列；
        // WORKING 等活动快照不被覆盖（resolveStatus 以活动行为权威）
        String currentStatus = pet.getStatus() != null ? pet.getStatus() : PetStatus.IDLE.name();
        String nextStatus = currentStatus;
        boolean weakNow = hunger == 0 && nextHungerZeroSince != null
                && Duration.between(nextHungerZeroSince, now).toHours() >= WEAK_AFTER_HOURS;
        boolean sickNow = happiness == 0 && nextHappinessZeroSince != null
                && Duration.between(nextHappinessZeroSince, now).toHours() >= SICK_AFTER_HOURS;
        if (weakNow) {
            nextStatus = PetStatus.WEAK.name();
        } else if (sickNow) {
            nextStatus = PetStatus.SICK.name();
        } else if (PetStatus.WEAK.name().equals(currentStatus) && hunger >= RECOVER_THRESHOLD) {
            nextStatus = PetStatus.IDLE.name();
        } else if (PetStatus.SICK.name().equals(currentStatus) && happiness >= RECOVER_THRESHOLD) {
            nextStatus = PetStatus.IDLE.name();
        }

        LambdaUpdateWrapper<Pet> decayUpdate = new LambdaUpdateWrapper<Pet>()
                .set(Pet::getHunger, hunger)
                .set(Pet::getEnergy, energy)
                .set(Pet::getCleanliness, cleanliness)
                .set(Pet::getHappiness, happiness)
                .set(Pet::getHungerFrac, nextHungerFrac)
                .set(Pet::getHappinessFrac, nextHappinessFrac)
                .set(Pet::getEnergyFrac, nextEnergyFrac)
                .set(Pet::getCleanlinessFrac, nextCleanFrac)
                .set(Pet::getHungerZeroSince, nextHungerZeroSince)
                .set(Pet::getHappinessZeroSince, nextHappinessZeroSince)
                .set(Pet::getLastStateUpdateAt, now)
                .eq(Pet::getId, pet.getId())
                .eq(Pet::getLastStateUpdateAt, pet.getLastStateUpdateAt());
        if (!nextStatus.equals(currentStatus)
                && (PetStatus.WEAK.name().equals(nextStatus) || PetStatus.SICK.name().equals(nextStatus)
                || PetStatus.WEAK.name().equals(currentStatus) || PetStatus.SICK.name().equals(currentStatus))) {
            // 只在进入/离开恶化态时写状态列，不覆盖其他快照语义
            decayUpdate.set(Pet::getStatus, nextStatus);
        }
        int updated = petMapper.update(null, decayUpdate);
        if (updated > 0) {
            pet.setHunger(hunger);
            pet.setEnergy(energy);
            pet.setCleanliness(cleanliness);
            pet.setHappiness(happiness);
            pet.setHungerFrac(nextHungerFrac);
            pet.setHappinessFrac(nextHappinessFrac);
            pet.setEnergyFrac(nextEnergyFrac);
            pet.setCleanlinessFrac(nextCleanFrac);
            pet.setHungerZeroSince(nextHungerZeroSince);
            pet.setHappinessZeroSince(nextHappinessZeroSince);
            pet.setLastStateUpdateAt(now);
            pet.setStatus(nextStatus);
        } else {
            Pet latest = petMapper.selectById(pet.getId());
            if (latest != null) {
                copyMutableState(latest, pet);
            }
        }
        return pet;
    }

    private double frac(Double value) {
        return value != null ? value : 0.0;
    }

    /**
     * 增加经验并处理升级（可跨多级）。属性成长：每级力量/智力/敏捷/魅力 +1、上限 +5。
     * 经实体乐观锁更新（@Version 自动附加版本条件）；版本冲突显式抛 PET_STATE_CONFLICT
     *（B02：可重试，禁止静默丢更新产生虚假奖励）。
     *
     * @return 实际升到的等级数（0 表示未升级）
     */
    public int grantExp(Pet pet, int expGain) {
        if (expGain <= 0) {
            return 0;
        }
        // 亲密度加成（三期）：所有经验都从这里发，加成只需在这一处生效
        int effectiveGain = expGain
                + (int) Math.round(expGain * PetIntimacyMath.expBonus(pet, properties.getIntimacy()));
        // F4：SICK（心情归零连续超 48h）经验获取减半——以归零起点列实时判定，不信展示快照
        if (isSick(pet)) {
            effectiveGain = effectiveGain / 2;
        }

        // 计算（不直接改实体）：CAS 失败时基于最新行重算一次，再失败抛冲突回滚本次业务
        Integer version = pet.getVersion();
        int currentExp = pet.getExp();
        int currentLevel = pet.getLevel();
        int currentMaxHp = pet.getMaxHp();
        int currentHp = pet.getHp();
        int currentStrength = pet.getStrength();
        int currentIntelligence = pet.getIntelligence();
        int currentAgility = pet.getAgility();
        int currentCharm = pet.getCharm();

        for (int attempt = 0; attempt < 2; attempt++) {
            int nextExp = currentExp + effectiveGain;
            int nextLevel = currentLevel;
            int nextLevelups = 0;
            while (nextLevel < LEVEL_MAX && nextExp >= expToNext(nextLevel)) {
                nextExp -= expToNext(nextLevel);
                nextLevel++;
                nextLevelups++;
            }
            if (nextLevel >= LEVEL_MAX) {
                nextExp = Math.min(nextExp, expToNext(LEVEL_MAX));
            }
            LambdaUpdateWrapper<Pet> wrapper = new LambdaUpdateWrapper<Pet>()
                    .set(Pet::getExp, nextExp)
                    .set(Pet::getLevel, nextLevel)
                    // B02：限定列 + 显式版本 CAS——不经全实体覆盖，避免旧状态值回写
                    .setSql("version = version + 1")
                    .eq(Pet::getId, pet.getId())
                    .eq(Pet::getVersion, version != null ? version : 0);
            if (nextLevelups > 0) {
                wrapper.set(Pet::getMaxHp, currentMaxHp + 5 * nextLevelups)
                        .set(Pet::getHp, Math.min(currentMaxHp + 5 * nextLevelups, currentHp + 5 * nextLevelups))
                        .set(Pet::getStrength, grow(currentStrength, nextLevelups))
                        .set(Pet::getIntelligence, grow(currentIntelligence, nextLevelups))
                        .set(Pet::getAgility, grow(currentAgility, nextLevelups))
                        .set(Pet::getCharm, grow(currentCharm, nextLevelups))
                        .set(Pet::getGrowthStage, growthStageFor(nextLevel));
            }
            int updated = petMapper.update(null, wrapper);
            if (updated > 0) {
                // 同步实体供调用方响应（版本随 CAS 前进）
                pet.setExp(nextExp);
                pet.setLevel(nextLevel);
                pet.setVersion(version != null ? version + 1 : 1);
                if (nextLevelups > 0) {
                    pet.setMaxHp(currentMaxHp + 5 * nextLevelups);
                    pet.setHp(Math.min(currentMaxHp + 5 * nextLevelups, currentHp + 5 * nextLevelups));
                    pet.setStrength(grow(currentStrength, nextLevelups));
                    pet.setIntelligence(grow(currentIntelligence, nextLevelups));
                    pet.setAgility(grow(currentAgility, nextLevelups));
                    pet.setCharm(grow(currentCharm, nextLevelups));
                    pet.setGrowthStage(growthStageFor(nextLevel));
                }
                // P1-4：等级榜 ZSet 埋点（Fail-Open，Redis 异常不影响业务事务）
                rankingCache.onExpGranted(pet.getId(), nextLevel, nextExp,
                        Boolean.TRUE.equals(pet.getIsPublic()));
                return nextLevelups;
            }
            // CAS 未命中：重读最新行，重算后重试一次（纯经验运算，重放安全）
            Pet latest = petMapper.selectById(pet.getId());
            if (latest == null) {
                throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "宠物不存在");
            }
            version = latest.getVersion();
            currentExp = latest.getExp();
            currentLevel = latest.getLevel();
            currentMaxHp = latest.getMaxHp();
            currentHp = latest.getHp();
            currentStrength = latest.getStrength();
            currentIntelligence = latest.getIntelligence();
            currentAgility = latest.getAgility();
            currentCharm = latest.getCharm();
        }
        throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                "宠物状态被并发修改，请稍后重试");
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
        target.setHungerFrac(source.getHungerFrac());
        target.setHappinessFrac(source.getHappinessFrac());
        target.setEnergyFrac(source.getEnergyFrac());
        target.setCleanlinessFrac(source.getCleanlinessFrac());
        // F4：归零起点列随状态一起收敛到最新行
        target.setHungerZeroSince(source.getHungerZeroSince());
        target.setHappinessZeroSince(source.getHappinessZeroSince());
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

    /**
     * 加载主宠并结算懒更新，不存在抛 {@code PET_NOT_FOUND}。
     *
     * <p>供<b>不依赖 PetService</b> 的组件使用（如活动服务被提醒服务依赖，
     * 若反向依赖 PetService 会形成 Spring 循环依赖）；PetService 内部同语义入口
     * 直接委托本方法，保证"取主宠"只有一处实现。</p>
     */
    public Pet requireActivePet(Long userId) {
        Pet pet = findByUserId(userId);
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND,
                    "你还没有宠物，先去领养一只吧");
        }
        return applyIdleDecay(pet);
    }

    /**
     * PET-07/T10：动作事务专用——FOR UPDATE 锁定宠物行后结算懒衰减。
     * 持锁期间 lastStateUpdateAt 不可能被并发修改，衰减 CAS 必命中；
     * 并发衰减读者（getMyPet 等无锁路径）在动作提交后 CAS 落空自动重读，
     * 消除"衰减绝对值写覆盖刚发生的动作增量"窗口。必须在外层事务内调用。
     */
    public Pet requireActivePetForUpdate(Long userId) {
        Pet pet = petMapper.selectActiveForUpdate(userId);
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND,
                    "你还没有宠物，先去领养一只吧");
        }
        return applyIdleDecay(pet);
    }

    /** 按用户加载主宠（软删过滤由 @TableLogic 处理）；不存在返回 null */
    public Pet findByUserId(Long userId) {
        return petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
    }

    /** 按 ID 加载宠物（多宠物归属查询用，B03）；不存在返回 null */
    public Pet findById(Long petId) {
        return petMapper.selectById(petId);
    }

    /** 用户全部宠物（主宠优先，其次按等级/ID 倒序），多宠物切换列表用 */
    public java.util.List<Pet> listByUserId(Long userId) {
        return petMapper.selectList(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .orderByDesc(Pet::getIsActive)
                .orderByDesc(Pet::getLevel)
                .orderByDesc(Pet::getId));
    }

    /** 宠物数量（多宠物上限校验用） */
    public long countByUserId(Long userId) {
        return petMapper.selectCount(new LambdaQueryWrapper<Pet>().eq(Pet::getUserId, userId));
    }
}
