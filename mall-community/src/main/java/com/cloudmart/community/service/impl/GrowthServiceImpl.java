package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.entity.DailyCheckIn;
import com.cloudmart.community.entity.ExpLog;
import com.cloudmart.community.entity.LevelConfig;
import com.cloudmart.community.entity.UserLevel;
import com.cloudmart.community.repository.DailyCheckInMapper;
import com.cloudmart.community.repository.ExpLogMapper;
import com.cloudmart.community.repository.LevelConfigMapper;
import com.cloudmart.community.repository.UserLevelMapper;
import com.cloudmart.community.service.CheckInBitMapService;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.service.RankingService;
import com.cloudmart.community.vo.CheckInResultVO;
import com.cloudmart.community.vo.ExpLogVO;
import com.cloudmart.community.vo.LevelConfigVO;
import com.cloudmart.community.vo.UserLevelVO;
import com.cloudmart.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrowthServiceImpl implements GrowthService {

    private static final int BASE_CHECK_IN_EXP = 10;
    private static final int CONTINUOUS_BONUS_PER_DAY = 5;
    private static final int MAX_CONTINUOUS_BONUS = 50;

    private final UserLevelMapper userLevelMapper;
    private final LevelConfigMapper levelConfigMapper;
    private final DailyCheckInMapper dailyCheckInMapper;
    private final ExpLogMapper expLogMapper;
    private final RankingService rankingService;
    private final CheckInBitMapService checkInBitMapService;

    @Override
    @Transactional
    public CheckInResultVO checkIn(Long userId) {
        LocalDate today = LocalDate.now();

        // SETBIT 返回旧值：true 表示已签到（重复签到）
        boolean alreadyCheckedIn = checkInBitMapService.setBit(userId, today);
        if (alreadyCheckedIn) {
            throw new BusinessException("ALREADY_CHECKED_IN", "今日已签到");
        }

        // 基于 BitMap 计算连续签到天数（支持跨月）
        int continuousDays = checkInBitMapService.countContinuousDays(userId, today);

        int bonus = Math.min((continuousDays - 1) * CONTINUOUS_BONUS_PER_DAY, MAX_CONTINUOUS_BONUS);
        int expReward = BASE_CHECK_IN_EXP + bonus;

        // DB 持久化（管理后台统计依赖）
        DailyCheckIn checkIn = new DailyCheckIn();
        checkIn.setUserId(userId);
        checkIn.setCheckInDate(today);
        checkIn.setContinuousDays(continuousDays);
        checkIn.setExpReward(expReward);
        dailyCheckInMapper.insert(checkIn);

        addExp(userId, expReward, "CHECK_IN", checkIn.getId(), "每日签到");

        UserLevel userLevel = getOrCreateUserLevel(userId);
        LevelConfig currentConfig = findLevelConfig(userLevel.getLevel());

        return new CheckInResultVO(
                true,
                continuousDays,
                expReward,
                userLevel.getTotalExp(),
                userLevel.getLevel(),
                currentConfig != null ? currentConfig.getTitle() : "",
                currentConfig != null ? currentConfig.getIcon() : ""
        );
    }

    @Override
    public boolean isCheckedInToday(Long userId) {
        return checkInBitMapService.getBit(userId, LocalDate.now());
    }

    @Override
    public UserLevelVO getUserLevel(Long userId) {
        UserLevel userLevel = getOrCreateUserLevel(userId);
        LevelConfig currentConfig = findLevelConfig(userLevel.getLevel());

        LevelConfig nextConfig = levelConfigMapper.selectOne(
                new LambdaQueryWrapper<LevelConfig>()
                        .eq(LevelConfig::getLevel, userLevel.getLevel() + 1)
                        .eq(LevelConfig::getStatus, 1)
        );

        int currentMinExp = currentConfig != null ? currentConfig.getMinExp() : 0;
        int nextLevelExp = nextConfig != null ? nextConfig.getMinExp() : currentMinExp;
        String nextLevelTitle = nextConfig != null ? nextConfig.getTitle() : null;

        double expProgress;
        if (nextConfig == null) {
            expProgress = 100.0;
        } else if (nextLevelExp == currentMinExp) {
            expProgress = 100.0;
        } else {
            expProgress = Math.min(
                    (double) (userLevel.getExp() - currentMinExp) / (nextLevelExp - currentMinExp) * 100,
                    100.0
            );
        }

        return new UserLevelVO(
                userLevel.getUserId(),
                userLevel.getLevel(),
                userLevel.getExp(),
                userLevel.getTotalExp(),
                currentConfig != null ? currentConfig.getTitle() : "",
                currentConfig != null ? currentConfig.getIcon() : "",
                nextLevelExp,
                nextLevelTitle,
                Math.max(0, expProgress)
        );
    }

    @Override
    @Transactional
    public void addExp(Long userId, int exp, String source, Long bizId, String description) {
        UserLevel userLevel = getOrCreateUserLevel(userId);

        int oldLevel = userLevel.getLevel();
        userLevel.setExp(userLevel.getExp() + exp);
        userLevel.setTotalExp(userLevel.getTotalExp() + exp);

        int newLevel = calculateLevel(userLevel.getExp());
        if (newLevel > oldLevel) {
            userLevel.setLevel(newLevel);
            log.info("User {} leveled up: {} -> {}, exp={}", userId, oldLevel, newLevel, userLevel.getExp());
        }

        userLevelMapper.updateById(userLevel);

        ExpLog expLog = new ExpLog();
        expLog.setUserId(userId);
        expLog.setExpChange(exp);
        expLog.setSource(source);
        expLog.setBizId(bizId);
        expLog.setDescription(description);
        expLogMapper.insert(expLog);

        try {
            rankingService.addExpToRanking(userId, exp);
        } catch (Exception e) {
            log.warn("更新排行榜失败，不影响主流程: userId={}, exp={}", userId, exp, e);
        }
    }

    @Override
    public Page<ExpLogVO> getExpLogs(Long userId, int page, int size) {
        LambdaQueryWrapper<ExpLog> wrapper = new LambdaQueryWrapper<ExpLog>()
                .eq(ExpLog::getUserId, userId)
                .orderByDesc(ExpLog::getCreatedAt);

        Page<ExpLog> expLogPage = expLogMapper.selectPage(new Page<>(page, size), wrapper);

        List<ExpLogVO> voList = expLogPage.getRecords().stream()
                .map(this::toExpLogVO)
                .toList();

        Page<ExpLogVO> resultPage = new Page<>(expLogPage.getCurrent(), expLogPage.getSize(), expLogPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public List<LevelConfigVO> getLevelConfigs() {
        return levelConfigMapper.selectList(
                        new LambdaQueryWrapper<LevelConfig>()
                                .eq(LevelConfig::getStatus, 1)
                                .orderByAsc(LevelConfig::getLevel)
                ).stream()
                .map(this::toLevelConfigVO)
                .toList();
    }

    private UserLevel getOrCreateUserLevel(Long userId) {
        UserLevel userLevel = userLevelMapper.selectOne(
                new LambdaQueryWrapper<UserLevel>().eq(UserLevel::getUserId, userId)
        );
        if (userLevel == null) {
            userLevel = new UserLevel();
            userLevel.setUserId(userId);
            userLevel.setLevel(1);
            userLevel.setExp(0);
            userLevel.setTotalExp(0L);
            userLevelMapper.insert(userLevel);
        }
        return userLevel;
    }

    private int calculateLevel(int exp) {
        List<LevelConfig> configs = levelConfigMapper.selectList(
                new LambdaQueryWrapper<LevelConfig>()
                        .eq(LevelConfig::getStatus, 1)
                        .orderByDesc(LevelConfig::getMinExp)
        );
        for (LevelConfig config : configs) {
            if (exp >= config.getMinExp()) {
                return config.getLevel();
            }
        }
        return 1;
    }

    private LevelConfig findLevelConfig(int level) {
        return levelConfigMapper.selectOne(
                new LambdaQueryWrapper<LevelConfig>()
                        .eq(LevelConfig::getLevel, level)
                        .eq(LevelConfig::getStatus, 1)
        );
    }

    private ExpLogVO toExpLogVO(ExpLog expLog) {
        return new ExpLogVO(
                expLog.getId(),
                expLog.getExpChange(),
                expLog.getSource(),
                expLog.getBizId(),
                expLog.getDescription(),
                expLog.getCreatedAt()
        );
    }

    private LevelConfigVO toLevelConfigVO(LevelConfig config) {
        return new LevelConfigVO(
                config.getId(),
                config.getLevel(),
                config.getTitle(),
                config.getMinExp(),
                config.getIcon(),
                config.getBenefits(),
                config.getStatus()
        );
    }

    @Override
    public List<LocalDate> getCheckInCalendar(Long userId, int year, int month) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        int daysInMonth = firstDay.lengthOfMonth();

        // 如果查询的是当前月份，只返回到今天为止的记录
        LocalDate today = LocalDate.now();
        int dayCount = (year == today.getYear() && month == today.getMonthValue())
                ? today.getDayOfMonth()
                : daysInMonth;

        List<Integer> bits = checkInBitMapService.getMonthBits(userId, year, month, dayCount);

        List<LocalDate> checkInDates = new java.util.ArrayList<>(dayCount);
        for (int i = 0; i < bits.size(); i++) {
            if (bits.get(i) == 1) {
                checkInDates.add(firstDay.plusDays(i));
            }
        }
        return checkInDates;
    }

    @Override
    public int getContinuousDays(Long userId) {
        LocalDate today = LocalDate.now();
        // 今天已签到 → 从今天向前统计
        if (checkInBitMapService.getBit(userId, today)) {
            return checkInBitMapService.countContinuousDays(userId, today);
        }
        // 今天未签到 → 返回 0（连续签到中断）
        return 0;
    }
}
