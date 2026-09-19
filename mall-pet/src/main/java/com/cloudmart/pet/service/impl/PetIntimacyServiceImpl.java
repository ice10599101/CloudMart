package com.cloudmart.pet.service.impl;

import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.util.PetIntimacyMath;
import com.cloudmart.pet.vo.PetIntimacyVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 亲密度与陪伴实现（三期）。
 *
 * <p>亲密度来源与数值全部集中在 {@link PetProperties.Intimacy}（Nacos 可热更）：
 * 一次性互动（喂食/玩耍/清洁/休息/聊天/打工/读书/捞瓶/对战/串门/家园/留言/任务）
 * 由 {@link #gain} 直接加；陪伴时长按心跳累计，每 {@code companionSecondsPerPoint} 秒 +1 点，
 * 每日点数与秒数双重封顶（防挂机）。</p>
 *
 * <p>并发：{@link #gain} 不落库（调用方一次写），{@link #heartbeat} 自己落库；
 * 陪伴日计数用 Redis（Fail-Open），跨天以 {@code lastCompanionDate} 惰性重置。</p>
 */
@Service
@Slf4j
public class PetIntimacyServiceImpl implements PetIntimacyService {

    /** 陪伴：今日已计入的亲密度点数（Redis 计数，防止一天刷满） */
    static final String KEY_COMPANION_POINTS = "pet:companion:points:%d:%s";

    private final PetMapper petMapper;
    private final PetProperties properties;
    private final PetEventProducer eventProducer;
    private final PetAchievementService achievementService;
    private final StringRedisTemplate redisTemplate;

    public PetIntimacyServiceImpl(PetMapper petMapper,
                                  PetProperties properties,
                                  PetEventProducer eventProducer,
                                  PetAchievementService achievementService,
                                  StringRedisTemplate redisTemplate) {
        this.petMapper = petMapper;
        this.properties = properties;
        this.eventProducer = eventProducer;
        this.achievementService = achievementService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int gain(Pet pet, PetIntimacySource source) {
        int gain = switchGain(source);
        if (gain <= 0 || pet == null) {
            return 0;
        }
        return applyIntimacy(pet, gain);
    }

    @Override
    @Transactional
    public int heartbeat(Long userId, int seconds) {
        if (seconds <= 0) {
            return 0;
        }
        Pet pet = petMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        if (pet == null) {
            return 0;
        }
        PetProperties.Intimacy cfg = properties.getIntimacy();
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        // 跨天惰性重置：今日秒数归零；陪伴天数 +1；连续天数按"上次日期是否昨天"判定（断签重置 1）
        int todaySeconds = pet.getTodayCompanionSeconds() != null ? pet.getTodayCompanionSeconds() : 0;
        LocalDate last = pet.getLastCompanionDate();
        if (last == null || !today.equals(last)) {
            boolean consecutive = last != null && last.plusDays(1).equals(today);
            pet.setCompanionStreak(consecutive
                    ? (pet.getCompanionStreak() != null ? pet.getCompanionStreak() + 1 : 1)
                    : 1);
            pet.setCompanionDays((pet.getCompanionDays() != null ? pet.getCompanionDays() : 0) + 1);
            todaySeconds = 0;
            pet.setTodayCompanionSeconds(0);
            pet.setLastCompanionDate(today);
        }
        int capped = Math.min(cfg.getCompanionDailyCapSeconds(), todaySeconds + seconds);
        int accepted = Math.max(0, capped - todaySeconds);
        pet.setTodayCompanionSeconds(capped);
        pet.setCompanionSeconds((pet.getCompanionSeconds() != null ? pet.getCompanionSeconds() : 0) + accepted);

        // 陪伴换算亲密度：每 N 秒 1 点，再按当日点数上限截断
        int points = accepted / Math.max(1, cfg.getCompanionSecondsPerPoint());
        int availablePoints = points - usedCompanionPoints(userId);
        int levelups = 0;
        if (availablePoints > 0) {
            int granted = Math.min(availablePoints, cfg.getCompanionDailyPointCap());
            consumeCompanionPoints(userId, granted);
            levelups = applyIntimacy(pet, granted);
        }
        petMapper.updateById(pet);
        if (levelups > 0) {
            achievementService.evaluate(pet, PetAchievementService.Event.INTIMACY);
        }
        return availablePoints > 0
                ? Math.min(availablePoints, cfg.getCompanionDailyPointCap())
                : 0;
    }

    @Override
    public int levelOf(int intimacy) {
        return PetIntimacyMath.levelOf(intimacy, properties.getIntimacy().getLevelThresholds());
    }

    @Override
    public String levelName(int level) {
        return PetIntimacyMath.levelName(level, properties.getIntimacy().getLevelNames());
    }

    @Override
    public int toNext(int intimacy) {
        return PetIntimacyMath.toNext(intimacy, properties.getIntimacy().getLevelThresholds());
    }

    @Override
    public double expBonus(Pet pet) {
        return PetIntimacyMath.expBonus(pet, properties.getIntimacy());
    }

    @Override
    public PetIntimacyVO overview(Long userId) {
        Pet pet = petMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        PetProperties.Intimacy cfg = properties.getIntimacy();
        int intimacy = pet != null && pet.getIntimacy() != null ? pet.getIntimacy() : 0;
        int level = levelOf(intimacy);
        List<Integer> thresholds = cfg.getLevelThresholds();
        List<String> names = cfg.getLevelNames();
        List<PetIntimacyVO.LevelItem> levels = new ArrayList<>();
        for (int i = 0; i < thresholds.size(); i++) {
            levels.add(new PetIntimacyVO.LevelItem(i + 1,
                    i < names.size() ? names.get(i) : "Lv." + (i + 1),
                    thresholds.get(i), intimacy >= thresholds.get(i)));
        }
        int floor = thresholds.isEmpty() ? 0 : thresholds.get(Math.min(level - 1, thresholds.size() - 1));
        Integer nextAt = level < thresholds.size() ? thresholds.get(level) : null;
        int bonusPercent = (int) Math.round(expBonus(pet) * 100);
        return new PetIntimacyVO(intimacy, level, levelName(level), floor, nextAt, toNext(intimacy),
                bonusPercent,
                pet != null && pet.getCompanionSeconds() != null ? pet.getCompanionSeconds() : 0L,
                pet != null && pet.getTodayCompanionSeconds() != null ? pet.getTodayCompanionSeconds() : 0,
                cfg.getCompanionDailyCapSeconds(),
                pet != null && pet.getCompanionDays() != null ? pet.getCompanionDays() : 0,
                pet != null && pet.getCompanionStreak() != null ? pet.getCompanionStreak() : 0,
                levels);
    }

    /** 亲密度来源 → 配置数值 */
    private int switchGain(PetIntimacySource source) {
        PetProperties.Intimacy cfg = properties.getIntimacy();
        return switch (source) {
            case FEED -> cfg.getFeedGain();
            case PLAY -> cfg.getPlayGain();
            case CLEAN -> cfg.getCleanGain();
            case REST -> cfg.getRestGain();
            case CHAT -> cfg.getChatGain();
            case WORK -> cfg.getWorkGain();
            case STUDY -> cfg.getStudyGain();
            case BOTTLE -> cfg.getBottleGain();
            case BATTLE -> cfg.getBattleGain();
            case VISIT -> cfg.getVisitGain();
            case ROOM -> cfg.getRoomGain();
            case WALL -> cfg.getWallGain();
            case QUEST -> cfg.getQuestGain();
            // 陪伴时长走 heartbeat 的秒数换算，不走单次加成
            case COMPANION -> 0;
        };
    }

    /** 只改内存实体（调用方一次写库）；升级发宠物口吻通知，返回提升等级数 */
    private int applyIntimacy(Pet pet, int gain) {
        int before = pet.getIntimacy() != null ? pet.getIntimacy() : 0;
        int after = before + gain;
        pet.setIntimacy(after);
        int levelBefore = levelOf(before);
        int levelAfter = levelOf(after);
        int levelups = levelAfter - levelBefore;
        if (levelups > 0) {
            eventProducer.publish(RocketMQConfig.PET_TAG_INTIMACY, new PetEventProducer.PetEventMessage(
                    pet.getUserId(), "PET_INTIMACY_LEVEL_UP",
                    "我们更亲密啦！",
                    pet.getName() + "：主人，我们已经到「" + levelName(levelAfter) + "」啦，谢谢你一直陪着我～",
                    pet.getId(), "PET_INTIMACY_LEVEL_UP"));
        }
        return levelups;
    }

    /** 今日已计入的陪伴亲密度点数（Redis，Fail-Open 视为未计入） */
    private int usedCompanionPoints(Long userId) {
        try {
            String key = String.format(KEY_COMPANION_POINTS, userId, LocalDate.now(ZoneId.of("UTC")));
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Integer.parseInt(value) : 0;
        } catch (Exception e) {
            log.warn("陪伴亲密度计数读取降级（Fail-Open）: userId={}", userId, e);
            return 0;
        }
    }

    /** 累加今日陪伴点数（Redis，Fail-Open 放行） */
    private void consumeCompanionPoints(Long userId, int points) {
        try {
            String key = String.format(KEY_COMPANION_POINTS, userId, LocalDate.now(ZoneId.of("UTC")));
            Long after = redisTemplate.opsForValue().increment(key, points);
            if (after != null && after == points) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
        } catch (Exception e) {
            log.warn("陪伴亲密度计数写入降级（Fail-Open）: userId={}", userId, e);
        }
    }
}
