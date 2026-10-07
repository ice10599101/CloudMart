package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.entity.DailyCheckIn;
import com.cloudmart.community.entity.ExpLog;
import com.cloudmart.community.entity.LevelConfig;
import com.cloudmart.community.entity.UserBadge;
import com.cloudmart.community.entity.UserLevel;
import com.cloudmart.community.repository.DailyCheckInMapper;
import com.cloudmart.community.repository.ExpLogMapper;
import com.cloudmart.community.repository.LevelConfigMapper;
import com.cloudmart.community.repository.UserBadgeMapper;
import com.cloudmart.community.repository.UserLevelMapper;
import com.cloudmart.community.service.CheckInBitMapService;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.service.RankingService;
import com.cloudmart.community.service.UserEnrichmentService;
import com.cloudmart.community.vo.CheckInResultVO;
import com.cloudmart.community.vo.ExpLogVO;
import com.cloudmart.community.vo.LevelConfigVO;
import com.cloudmart.community.vo.UserDecorationVO;
import com.cloudmart.community.vo.UserLevelVO;
import com.cloudmart.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrowthServiceImpl implements GrowthService {

    /** T09：DB 连续天数行走窗口（天）——覆盖里程碑 30 天语义与跨月历史 */
    private static final int DB_WALK_WINDOW_DAYS = 400;


    private static final int BASE_CHECK_IN_EXP = 10;
    private static final int CONTINUOUS_BONUS_PER_DAY = 5;
    private static final int MAX_CONTINUOUS_BONUS = 50;

    /**
     * P1-7：互动经验日累计上限——发帖/评论/被赞/获粉等全部计入，超限只记日志不再发放。
     * 防小号互刷评论/点赞刷等级与排行榜；签到奖励本身每日一次，同受此口径约束。
     * 日界与签到一致取服务器本地日（P1-15 时区统一时一并对齐用户时区）。
     */
    private static final int DAILY_EXP_CAP = 200;

    private final UserLevelMapper userLevelMapper;
    private final LevelConfigMapper levelConfigMapper;
    private final DailyCheckInMapper dailyCheckInMapper;
    private final ExpLogMapper expLogMapper;
    private final RankingService rankingService;
    private final CheckInBitMapService checkInBitMapService;
    private final UserBadgeMapper userBadgeMapper;
    private final UserEnrichmentService userEnrichmentService;

    /** 合法头像框 key（前端 AVATAR_FRAMES 与之对应） */
    private static final Set<String> ALLOWED_AVATAR_FRAMES =
            Set.of("none", "gold", "purple", "green", "pink", "rainbow");

    @Override
    @Transactional
    public CheckInResultVO checkIn(Long userId) {
        LocalDate today = LocalDate.now();

        // C05：DB 事实先行——uk(user_id, check_in_date) 判重（并发/重复签到只有一个赢家），
        // Redis Bitmap 降级为投影（失败不阻断签到，可由管理端重建）
        DailyCheckIn checkIn = new DailyCheckIn();
        checkIn.setUserId(userId);
        checkIn.setCheckInDate(today);
        try {
            dailyCheckInMapper.insert(checkIn);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new BusinessException("ALREADY_CHECKED_IN", "今日已签到");
        }

        // T09：连续天数以数据库事实计算（跨月/跨年/闰年由日期行走保证，不依赖
        // Redis 投影）；Redis 位图降级为提交后的可重建投影
        int continuousDays = countContinuousDaysFromDb(userId, today);
        // T09：Bitmap 置位移到事务提交后——旧实现同步写 Redis 在事务内，缓存异常
        // 会回滚签到主流程，DB 回滚后还会留下位图残影
        registerBitmapProjection(userId, today);

        int bonus = Math.min((continuousDays - 1) * CONTINUOUS_BONUS_PER_DAY, MAX_CONTINUOUS_BONUS);
        int expReward = BASE_CHECK_IN_EXP + bonus;
        checkIn.setContinuousDays(continuousDays);
        checkIn.setExpReward(expReward);
        dailyCheckInMapper.updateById(checkIn);

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

    /** T09：今日签到状态以数据库唯一事实为准（位图仅投影）。 */
    @Override
    public boolean isCheckedInToday(Long userId) {
        Long count = dailyCheckInMapper.selectCount(new LambdaQueryWrapper<DailyCheckIn>()
                .eq(DailyCheckIn::getUserId, userId)
                .eq(DailyCheckIn::getCheckInDate, LocalDate.now()));
        return count != null && count > 0;
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
    public void setAvatarFrame(Long userId, String frame) {
        if (frame == null || !ALLOWED_AVATAR_FRAMES.contains(frame)) {
            throw new BusinessException("INVALID_AVATAR_FRAME", "非法头像框");
        }
        UserLevel userLevel = getOrCreateUserLevel(userId);
        userLevel.setAvatarFrame(frame);
        userLevelMapper.updateById(userLevel);
    }

    @Override
    public Map<Long, UserDecorationVO> getUserDecorations(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctIds = userIds.stream().distinct().toList();

        Map<Long, UserLevel> levelByUser = userLevelMapper.selectList(
                        new LambdaQueryWrapper<UserLevel>().in(UserLevel::getUserId, distinctIds))
                .stream()
                .collect(Collectors.toMap(UserLevel::getUserId, l -> l, (a, b) -> a));

        Map<Long, Long> badgeCountByUser = userBadgeMapper.selectList(
                        new LambdaQueryWrapper<UserBadge>().in(UserBadge::getUserId, distinctIds))
                .stream()
                .collect(Collectors.groupingBy(UserBadge::getUserId, Collectors.counting()));

        LevelConfig level1Config = findLevelConfig(1);

        Map<Long, UserEnrichmentService.UserInfo> usersById =
                userEnrichmentService.batchGetUsers(Set.copyOf(distinctIds));

        Map<Long, UserDecorationVO> result = new LinkedHashMap<>();
        for (Long userId : distinctIds) {
            UserLevel ul = levelByUser.get(userId);
            int level = ul != null ? ul.getLevel() : 1;
            LevelConfig cfg = ul != null ? findLevelConfig(ul.getLevel()) : level1Config;
            UserEnrichmentService.UserInfo ui = usersById.get(userId);
            String avatar = ui != null ? ui.avatar() : null;
            result.put(userId, new UserDecorationVO(
                    userId,
                    level,
                    cfg != null ? cfg.getTitle() : "",
                    cfg != null ? cfg.getIcon() : "",
                    ul != null && ul.getAvatarFrame() != null ? ul.getAvatarFrame() : "none",
                    badgeCountByUser.getOrDefault(userId, 0L),
                    avatar
            ));
        }
        return result;
    }

    @Override
    @Transactional
    public void addExp(Long userId, int exp, String source, Long bizId, String description) {
        // P1-7：日累计上限——超限只记日志不发经验；并发窗口内可能轻微超出上限（非资金类不变量，可接受）
        if (exp > 0 && isDailyExpCapped(userId)) {
            log.info("P1-7 用户当日经验已达上限 {}，跳过发放: userId={}, source={}, bizId={}",
                    DAILY_EXP_CAP, userId, source, bizId);
            return;
        }

        // C05：奖励事实先行——uk(user_id, source, biz_id) 判重（并发/重试不双发奖）
        ExpLog expLog = new ExpLog();
        expLog.setUserId(userId);
        expLog.setExpChange(exp);
        expLog.setSource(source);
        expLog.setBizId(bizId);
        expLog.setDescription(description);
        try {
            expLogMapper.insert(expLog);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            log.info("经验奖励事实已存在（幂等跳过）, userId={}, source={}, bizId={}", userId, source, bizId);
            return;
        }

        // C05：原子增量替代实体读改写（并发丢更新缺陷修复）
        getOrCreateUserLevel(userId);
        userLevelMapper.incrementExp(userId, exp);

        // 重算等级（基于增量后的权威值；仅升级时条件推进）
        UserLevel latest = getOrCreateUserLevel(userId);
        int newLevel = calculateLevel(latest.getExp());
        if (newLevel > latest.getLevel()) {
            userLevelMapper.advanceLevel(userId, newLevel);
            log.info("User {} leveled up: {} -> {}, exp={}", userId, latest.getLevel(), newLevel, latest.getExp());
        }

        try {
            rankingService.addExpToRanking(userId, exp);
        } catch (Exception e) {
            log.warn("更新排行榜失败，不影响主流程: userId={}, exp={}", userId, exp, e);
        }
    }

    /** P1-7：统计当日已发放经验（ExpLog 按 user_id + createdAt 过滤），达到上限即不再发放 */
    private boolean isDailyExpCapped(Long userId) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        int todayExp = expLogMapper.selectList(new LambdaQueryWrapper<ExpLog>()
                        .eq(ExpLog::getUserId, userId)
                        .ge(ExpLog::getCreatedAt, dayStart))
                .stream()
                .mapToInt(l -> l.getExpChange() == null ? 0 : l.getExpChange())
                .sum();
        return todayExp >= DAILY_EXP_CAP;
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
            userLevel.setAvatarFrame("none");
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
    /** T09：日历/历史查询从数据库取得（35 天位图 TTL 无法支撑历史日历）。 */
    public List<LocalDate> getCheckInCalendar(Long userId, int year, int month) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        return dailyCheckInMapper.selectList(new LambdaQueryWrapper<DailyCheckIn>()
                        .eq(DailyCheckIn::getUserId, userId)
                        .ge(DailyCheckIn::getCheckInDate, firstDay)
                        .le(DailyCheckIn::getCheckInDate, lastDay)
                        .orderByAsc(DailyCheckIn::getCheckInDate))
                .stream()
                .map(DailyCheckIn::getCheckInDate)
                .toList();
    }

    /**
     * T09：数据库事实连续天数——从锚点（已签到日）向前按日期行走，跨月/跨年/
     * 闰年天然正确；查询窗口 400 天覆盖里程碑语义。
     */
    private int countContinuousDaysFromDb(Long userId, LocalDate anchor) {
        java.util.Set<LocalDate> dates = dailyCheckInMapper.selectList(
                        new LambdaQueryWrapper<DailyCheckIn>()
                                .eq(DailyCheckIn::getUserId, userId)
                                .ge(DailyCheckIn::getCheckInDate, anchor.minusDays(DB_WALK_WINDOW_DAYS))
                                .le(DailyCheckIn::getCheckInDate, anchor))
                .stream()
                .map(DailyCheckIn::getCheckInDate)
                .collect(java.util.stream.Collectors.toSet());
        int count = 0;
        LocalDate cursor = anchor;
        while (dates.contains(cursor)) {
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    /** 位图投影注册：事务提交后置位（失败仅记录，可由管理端重建）。 */
    private void registerBitmapProjection(Long userId, LocalDate date) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                checkInBitMapService.setBit(userId, date);
                            } catch (Exception ex) {
                                log.warn("签到位图投影失败（不影响签到事实，可重建）: userId={}, {}",
                                        userId, ex.getMessage());
                            }
                        }
                    });
        } else {
            try {
                checkInBitMapService.setBit(userId, date);
            } catch (Exception ex) {
                log.warn("签到位图投影失败（不影响签到事实，可重建）: userId={}, {}",
                        userId, ex.getMessage());
            }
        }
    }

    /**
     * T09：当前连续天数——今天已签到从今天起算；今天未签到但昨天已签到时展示
     * 可延续天数（签到后延续），连续中断返回 0。
     */
    @Override
    public int getContinuousDays(Long userId) {
        LocalDate today = LocalDate.now();
        if (isCheckedInToday(userId)) {
            return countContinuousDaysFromDb(userId, today);
        }
        if (dailyCheckInMapper.selectCount(new LambdaQueryWrapper<DailyCheckIn>()
                .eq(DailyCheckIn::getUserId, userId)
                .eq(DailyCheckIn::getCheckInDate, today.minusDays(1))) > 0) {
            return countContinuousDaysFromDb(userId, today.minusDays(1));
        }
        return 0;
    }
}
