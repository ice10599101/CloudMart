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
    /** R21 日配额行锁原子占用 */
    private final com.cloudmart.community.repository.ExpDailyQuotaMapper expDailyQuotaMapper;
    private final RankingService rankingService;
    private final CheckInBitMapService checkInBitMapService;
    private final UserBadgeMapper userBadgeMapper;
    private final UserEnrichmentService userEnrichmentService;
    /** R22 用户业务时区（权威来源 mall-wish，短缓存 fail-open） */
    private final com.cloudmart.community.service.support.UserTimezoneResolver userTimezoneResolver;

    /** 合法头像框 key（前端 AVATAR_FRAMES 与之对应） */
    private static final Set<String> ALLOWED_AVATAR_FRAMES =
            Set.of("none", "gold", "purple", "green", "pink", "rainbow");

    @Override
    @Transactional
    public CheckInResultVO checkIn(Long userId) {
        // R22：业务日按用户 IANA 时区归属（不再取服务器日）
        java.time.ZoneId businessZone = userTimezoneResolver.resolve(userId);
        LocalDate today = com.cloudmart.common.time.UserBusinessCalendar.today(businessZone);
        java.time.LocalDateTime dayStartUtc =
                com.cloudmart.common.time.UserBusinessCalendar.businessDayStartUtcDateTime(today, businessZone);

        // R22 防跨时区双签：以 UTC 事实时刻对当前业务日界判定——用户改时区后只要当前业务日界
        // （0 点 UTC）之后已有成功签到即拒绝；历史签到事实不回溯
        if (dailyCheckInMapper.countCheckedInSinceUtc(userId, dayStartUtc) > 0) {
            throw new BusinessException("ALREADY_CHECKED_IN", "今日已签到");
        }

        // C05：DB 事实先行——uk(user_id, check_in_date) 判重（并发同业务日二次插入撞键兜底），
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

        // R21：签到经验同样受日配额原子约束，VO 回传真实到账（不再把理论值当已发放）
        com.cloudmart.community.service.ExpGrantResult grant =
                grantExp(userId, expReward, "CHECK_IN", checkIn.getId(), "每日签到");

        UserLevel userLevel = getOrCreateUserLevel(userId);
        LevelConfig currentConfig = findLevelConfig(userLevel.getLevel());

        return new CheckInResultVO(
                true,
                continuousDays,
                grant.credited(),
                userLevel.getTotalExp(),
                userLevel.getLevel(),
                currentConfig != null ? currentConfig.getTitle() : "",
                currentConfig != null ? currentConfig.getIcon() : ""
        );
    }

    /** R22：今日签到状态以"用户当前业务日界 UTC 之后是否有事实"判定（防跨时区口径不一致）。 */
    @Override
    public boolean isCheckedInToday(Long userId) {
        java.time.ZoneId businessZone = userTimezoneResolver.resolve(userId);
        LocalDate today = com.cloudmart.common.time.UserBusinessCalendar.today(businessZone);
        java.time.LocalDateTime dayStartUtc =
                com.cloudmart.common.time.UserBusinessCalendar.businessDayStartUtcDateTime(today, businessZone);
        return dailyCheckInMapper.countCheckedInSinceUtc(userId, dayStartUtc) > 0;
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
        grantExp(userId, exp, source, bizId, description);
    }

    /**
     * R21 经验发放（权威实现）：
     * 1) 对当日配额行 SELECT ... FOR UPDATE——同用户当天所有发奖串行，100 并发合计不突破 200；
     * 2) 锁内按 (source,bizId) 判重（并发同事实请求第二个拿锁后必见首笔事实），幂等返回 credited=0；
     * 3) credited = min(requested, 200 - 已占用)，配额原子累加；
     * 4) 零收益（被 cap 截断到 0）且 bizId 非空也写 exp_logs 留事实，重复请求不再重放；
     * 5) 排行榜 ZINCRBY 延迟到事务提交后（afterCommit）按 credited 累加，事务回滚不留投影残影。
     */
    @Override
    @Transactional
    public com.cloudmart.community.service.ExpGrantResult grantExp(
            Long userId, int exp, String source, Long bizId, String description) {
        if (exp <= 0) {
            return new com.cloudmart.community.service.ExpGrantResult(exp, 0, false, null);
        }

        // R22：业务日按用户时区归属；日界 UTC 时刻用于跨时区事实兜底
        java.time.ZoneId businessZone = userTimezoneResolver.resolve(userId);
        LocalDate businessDate = com.cloudmart.common.time.UserBusinessCalendar.today(businessZone);
        java.time.LocalDateTime dayStartUtc =
                com.cloudmart.common.time.UserBusinessCalendar.businessDayStartUtcDateTime(businessDate, businessZone);
        com.cloudmart.community.entity.ExpDailyQuota quota = lockOrCreateTodayQuota(userId, businessDate);

        // 行锁内判重：锁已串行同用户当天发奖，此处可见并发赢家已提交的奖励事实
        if (bizId != null && existsRewardFact(userId, source, bizId)) {
            log.info("R21 经验奖励事实已存在（幂等不重发）: userId={}, source={}, bizId={}", userId, source, bizId);
            return com.cloudmart.community.service.ExpGrantResult.duplicate(exp);
        }

        // 已占用基准：R22 取「当日配额行 granted」与「当前业务日界 UTC 起 exp_logs 事实总和」的较大者——
        // 跨时区切换后配额行可能落在新 business_date（granted=0），事实总和兜底保证跨区窗口合计不超 200
        int rowGranted = quota.getGranted() == null ? 0 : quota.getGranted();
        int factSum = expLogMapper.sumCreditedSinceUtc(userId, dayStartUtc);
        int alreadyGranted = Math.max(rowGranted, factSum);
        int remaining = Math.max(0, DAILY_EXP_CAP - alreadyGranted);
        int credited = Math.min(exp, remaining);
        boolean capped = credited < exp;
        if (capped) {
            log.info("R21 用户当日经验触顶截断: userId={}, source={}, requested={}, credited={}, alreadyGranted={}",
                    userId, source, exp, credited, alreadyGranted);
        }

        // 零收益但有事实锚点（bizId 非空）也要落一条 exp_change=0 的事实——重复请求被 uk 拦截不再重放；
        // bizId 为空（如被赞/获粉聚合事件）且 credited=0 时无判重意义，不刷 0 行
        if (credited > 0 || bizId != null) {
            ExpLog expLog = new ExpLog();
            expLog.setUserId(userId);
            expLog.setExpChange(credited);
            expLog.setSource(source);
            expLog.setBizId(bizId);
            expLog.setDescription(description);
            expLogMapper.insert(expLog);
        }

        if (credited > 0) {
            // 行锁内原子累加占用额度
            expDailyQuotaMapper.addGranted(userId, businessDate, credited);

            // C05：原子增量替代实体读改写（并发丢更新缺陷修复）
            getOrCreateUserLevel(userId);
            userLevelMapper.incrementExp(userId, credited);

            // 重算等级（基于增量后的权威值；仅升级时条件推进）
            UserLevel latest = getOrCreateUserLevel(userId);
            int newLevel = calculateLevel(latest.getExp());
            if (newLevel > latest.getLevel()) {
                userLevelMapper.advanceLevel(userId, newLevel);
                log.info("User {} leveled up: {} -> {}, exp={}", userId, latest.getLevel(), newLevel, latest.getExp());
            }

            // R21：排行榜投影延迟到事务提交后，按实际 credited 累加（回滚不留残影）
            registerRankingProjection(userId, credited);
        }

        return com.cloudmart.community.service.ExpGrantResult.granted(exp, credited);
    }

    /** R21：取当日配额行并加行锁；不存在则首建 granted=0 后重新加锁（并发首建撞 uk 后重读） */
    private com.cloudmart.community.entity.ExpDailyQuota lockOrCreateTodayQuota(Long userId, LocalDate businessDate) {
        com.cloudmart.community.entity.ExpDailyQuota quota =
                expDailyQuotaMapper.selectForUpdate(userId, businessDate);
        if (quota != null) {
            return quota;
        }
        com.cloudmart.community.entity.ExpDailyQuota created = new com.cloudmart.community.entity.ExpDailyQuota();
        created.setUserId(userId);
        created.setBusinessDate(businessDate);
        created.setGranted(0);
        try {
            expDailyQuotaMapper.insert(created);
        } catch (org.springframework.dao.DuplicateKeyException concurrentCreate) {
            // 并发首建：另一事务已插入，落入下方重读加锁
            log.debug("R21 日配额行并发首建，重读加锁: userId={}, date={}", userId, businessDate);
        }
        return expDailyQuotaMapper.selectForUpdate(userId, businessDate);
    }

    /** (user_id, source, biz_id) 奖励事实是否已存在（bizId 非空时才有判重意义） */
    private boolean existsRewardFact(Long userId, String source, Long bizId) {
        Long count = expLogMapper.selectCount(new LambdaQueryWrapper<ExpLog>()
                .eq(ExpLog::getUserId, userId)
                .eq(ExpLog::getSource, source)
                .eq(ExpLog::getBizId, bizId));
        return count != null && count > 0;
    }

    /** R21：排行榜投影仅在事务提交后按实际 credited 累加（失败仅告警，可由后台重建投影） */
    private void registerRankingProjection(Long userId, int credited) {
        Runnable projection = () -> {
            try {
                rankingService.addExpToRanking(userId, credited);
            } catch (Exception e) {
                log.warn("R21 排行榜投影失败（不影响经验事实，可重建）: userId={}, exp={}", userId, credited, e);
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            projection.run();
                        }
                    });
        } else {
            projection.run();
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
        // R22：连续天数按用户业务日行走（与 checkIn 归属日同一口径）
        java.time.ZoneId businessZone = userTimezoneResolver.resolve(userId);
        LocalDate today = com.cloudmart.common.time.UserBusinessCalendar.today(businessZone);
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
