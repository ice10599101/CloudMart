package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.WishBadge;
import com.cloudmart.wish.entity.WishResourceLog;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.ResourceLogType;
import com.cloudmart.wish.repository.WishResourceLogMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.BadgeService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.LevelRequirementVO;
import com.cloudmart.wish.vo.LevelUpVO;
import com.cloudmart.wish.vo.MyLevelVO;
import com.cloudmart.wish.vo.MyResourcesVO;
import com.cloudmart.wish.vo.ResourceLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户心愿统计服务实现。
 *
 * <p>使用 {@code @Transactional(propagation = Propagation.MANDATORY)} 强制要求
 * 调用方事务上下文，确保统计更新与业务操作在同一事务中完成（原子性）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserStatServiceImpl implements UserStatService {

    /** 星光余额上限（文档 6.3 防囤积：超出不再累加） */
    static final int STARLIGHT_BALANCE_CAP = 5000;

    /** 默认时区（用户统计记录不存在时） */
    static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    /** 等级标题（文档 6.5：L1 追梦新人 → L5 宇宙守护者） */
    static final Map<Integer, String> LEVEL_TITLES = Map.of(
            1, "追梦新人",
            2, "梦想家",
            3, "追光者",
            4, "星火引路人",
            5, "宇宙守护者");

    /** 晋级指标中文名（四端进度条展示文案，与 metric 键一一对应） */
    static final Map<String, String> LEVEL_METRIC_LABELS = Map.of(
            "totalWishes", "累计许愿",
            "totalCheckinDays", "累计打卡",
            "totalFulfilled", "累计还愿",
            "totalHelped", "累计帮助");

    /**
     * 晋级条件表（文档 6.5：等级判定与进度查询共用同一数据源，避免双写漂移）。
     * LinkedHashMap 保持展示顺序；未出现的指标表示该级不要求。
     */
    static final Map<Integer, Map<String, Integer>> LEVEL_REQUIREMENTS = buildLevelRequirements();

    private static Map<Integer, Map<String, Integer>> buildLevelRequirements() {
        Map<Integer, Map<String, Integer>> requirements = new LinkedHashMap<>();
        requirements.put(2, new LinkedHashMap<>(Map.of("totalWishes", 3, "totalCheckinDays", 7)));
        requirements.put(3, new LinkedHashMap<>(Map.of("totalWishes", 10, "totalFulfilled", 1, "totalHelped", 50)));
        requirements.put(4, new LinkedHashMap<>(Map.of("totalWishes", 30, "totalFulfilled", 5, "totalHelped", 200)));
        requirements.put(5, new LinkedHashMap<>(Map.of("totalWishes", 100, "totalFulfilled", 20, "totalHelped", 1000)));
        return requirements;
    }

    private final WishUserStatMapper wishUserStatMapper;
    private final WishResourceLogMapper wishResourceLogMapper;
    private final BadgeService badgeService;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void initUserStat(Long userId) {
        WishUserStat existing = wishUserStatMapper.selectById(userId);
        if (existing != null) {
            return; // 幂等：已存在则跳过
        }

        WishUserStat stat = new WishUserStat();
        stat.setUserId(userId);
        stat.setTimezone("Asia/Shanghai"); // 默认时区，用户上报后更新
        stat.setLevel((byte) 1);
        stat.setLevelTitle("追梦新人");
        stat.setHighestLevel((byte) 1);
        stat.setStarlightBalance(0);
        stat.setTotalWishes(0);
        stat.setActiveWishes(0);
        stat.setTotalFulfilled(0);
        stat.setTotalHelped(0);
        stat.setTotalCheckinDays(0);
        stat.setLastActiveAt(LocalDateTime.now());
        stat.setRiskScore(0);
        stat.setIsRestricted(false);
        wishUserStatMapper.insert(stat);
        log.debug("初始化用户统计记录, userId={}", userId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void incrementOnWishCreated(Long userId) {
        initUserStat(userId); // 确保记录存在

        wishUserStatMapper.update(null,
                new LambdaUpdateWrapper<WishUserStat>()
                        .eq(WishUserStat::getUserId, userId)
                        .setSql("total_wishes = total_wishes + 1")
                        .setSql("active_wishes = active_wishes + 1")
                        .set(WishUserStat::getLastActiveAt, LocalDateTime.now())
        );
        log.debug("心愿创建统计+1, userId={}", userId);
        // 同事务判定徽章（FIRST_WISH 等），回滚时授予一并撤销
        badgeService.evaluateAndAward(userId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void decrementOnWishDeleted(Long userId) {
        int affected = wishUserStatMapper.update(null,
                new LambdaUpdateWrapper<WishUserStat>()
                        .eq(WishUserStat::getUserId, userId)
                        .setSql("active_wishes = GREATEST(active_wishes - 1, 0)")
                        .set(WishUserStat::getLastActiveAt, LocalDateTime.now())
        );
        if (affected == 0) {
            log.warn("软删统计-1失败（用户统计记录不存在）, userId={}", userId);
        } else {
            log.debug("心愿软删统计-1, userId={}", userId);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<WishBadge> incrementOnFulfilled(Long userId) {
        initUserStat(userId); // 确保记录存在

        wishUserStatMapper.update(null,
                new LambdaUpdateWrapper<WishUserStat>()
                        .eq(WishUserStat::getUserId, userId)
                        .setSql("total_fulfilled = total_fulfilled + 1")
                        .setSql("active_wishes = GREATEST(active_wishes - 1, 0)")
                        .set(WishUserStat::getLastActiveAt, LocalDateTime.now())
        );
        log.debug("还愿统计更新, userId={}", userId);
        // 同事务判定徽章（FIRST_FULFILL 等），回滚时授予一并撤销
        return badgeService.evaluateAndAward(userId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public int spendStarlight(Long userId, int cost, ResourceLogSource source, Long refId) {
        if (cost <= 0) {
            throw new IllegalArgumentException("星光扣减数量必须为正整数: " + cost);
        }
        initUserStat(userId);

        // 条件 UPDATE 原子扣减：WHERE balance >= cost 保证并发下不超扣
        int affected = wishUserStatMapper.update(null,
                new LambdaUpdateWrapper<WishUserStat>()
                        .eq(WishUserStat::getUserId, userId)
                        .ge(WishUserStat::getStarlightBalance, cost)
                        .setSql("starlight_balance = starlight_balance - " + cost)
                        .set(WishUserStat::getLastActiveAt, LocalDateTime.now())
        );
        if (affected == 0) {
            throw new BusinessException(WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT, "星光余额不足");
        }

        // 同事务内行锁未释放，读取本事务写入的最新余额作为流水快照
        int balanceAfter = requireBalance(userId);
        insertResourceLog(userId, -cost, ResourceLogType.SPEND, source, refId, balanceAfter);
        log.debug("星光扣减, userId={}, cost={}, source={}, balanceAfter={}", userId, cost, source, balanceAfter);
        return balanceAfter;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public int earnStarlight(Long userId, int amount, ResourceLogSource source, Long refId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("星光发放数量必须为正整数: " + amount);
        }
        initUserStat(userId);

        // FOR UPDATE 串行化读取：需精确计算上限截断后的实际入账量，保证流水求和等于余额（文档 6.4 对账）
        WishUserStat stat = wishUserStatMapper.selectOne(
                new LambdaQueryWrapper<WishUserStat>()
                        .eq(WishUserStat::getUserId, userId)
                        .last("FOR UPDATE"));
        int currentBalance = stat.getStarlightBalance();
        int credited = Math.min(amount, STARLIGHT_BALANCE_CAP - currentBalance);
        if (credited <= 0) {
            log.debug("星光已达上限不再累加, userId={}, balance={}", userId, currentBalance);
            return 0;
        }

        wishUserStatMapper.update(null,
                new LambdaUpdateWrapper<WishUserStat>()
                        .eq(WishUserStat::getUserId, userId)
                        .setSql("starlight_balance = starlight_balance + " + credited)
                        .set(WishUserStat::getLastActiveAt, LocalDateTime.now())
        );

        int balanceAfter = currentBalance + credited;
        insertResourceLog(userId, credited, ResourceLogType.EARN, source, refId, balanceAfter);
        log.debug("星光发放, userId={}, amount={}, credited={}, balanceAfter={}", userId, amount, credited, balanceAfter);
        return credited;
    }

    @Override
    public int getStarlightBalance(Long userId) {
        WishUserStat stat = wishUserStatMapper.selectById(userId);
        return stat != null ? stat.getStarlightBalance() : 0;
    }

    @Override
    public String getUserTimezone(Long userId) {
        WishUserStat stat = wishUserStatMapper.selectById(userId);
        return stat != null && stat.getTimezone() != null ? stat.getTimezone() : DEFAULT_TIMEZONE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementTotalHelped(Long userId) {
        int affected = wishUserStatMapper.update(null,
                new LambdaUpdateWrapper<WishUserStat>()
                        .eq(WishUserStat::getUserId, userId)
                        .setSql("total_helped = total_helped + 1")
                        .set(WishUserStat::getLastActiveAt, LocalDateTime.now())
        );
        if (affected == 0) {
            log.warn("帮助统计+1失败（用户统计记录不存在）, userId={}", userId);
            return;
        }
        // 同事务判定徽章（HELP_100 等）；MQ 重复消费时幂等授予不重复
        badgeService.evaluateAndAward(userId);
    }

    @Override
    public MyResourcesVO getMyResources(Long userId) {
        int balance = getStarlightBalance(userId);
        // 今日边界：与流水 createdAt 的 MetaObjectHandler 填充时区一致（同为服务器默认时区），避免错位
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return new MyResourcesVO(balance,
                sumTodayDelta(userId, ResourceLogType.EARN, todayStart),
                Math.abs(sumTodayDelta(userId, ResourceLogType.SPEND, todayStart)));
    }

    @Override
    public List<ResourceLogVO> listResourceLogs(Long userId, ResourceLogType type, Long cursor, Integer pageSize) {
        int size = pageSize == null ? 20 : Math.min(pageSize, 50);
        if (size <= 0) {
            size = 20;
        }
        LambdaQueryWrapper<WishResourceLog> wrapper = new LambdaQueryWrapper<WishResourceLog>()
                .eq(WishResourceLog::getUserId, userId)
                .orderByDesc(WishResourceLog::getId)
                .last("LIMIT " + size);
        if (type != null) {
            wrapper.eq(WishResourceLog::getType, type);
        }
        if (cursor != null) {
            wrapper.lt(WishResourceLog::getId, cursor);
        }
        return wishResourceLogMapper.selectList(wrapper).stream()
                .map(ResourceLogVO::from)
                .toList();
    }

    /** 聚合指定类型今日流水 delta 之和（SPEND 的 delta 为负数，取绝对值前先求和） */
    private int sumTodayDelta(Long userId, ResourceLogType type, LocalDateTime todayStart) {
        QueryWrapper<WishResourceLog> wrapper = new QueryWrapper<WishResourceLog>()
                .select("COALESCE(SUM(delta), 0)")
                .eq("user_id", userId)
                .eq("type", type.name())
                .ge("created_at", todayStart);
        List<Object> result = wishResourceLogMapper.selectObjs(wrapper);
        if (result.isEmpty() || result.get(0) == null) {
            return 0;
        }
        return ((Number) result.get(0)).intValue();
    }

    private int requireBalance(Long userId) {
        WishUserStat stat = wishUserStatMapper.selectById(userId);
        if (stat == null) {
            throw new IllegalStateException("用户统计记录不存在, userId=" + userId);
        }
        return stat.getStarlightBalance();
    }

    private void insertResourceLog(Long userId, int delta, ResourceLogType type,
                                   ResourceLogSource source, Long refId, int balanceAfter) {
        WishResourceLog logEntry = new WishResourceLog();
        logEntry.setUserId(userId);
        logEntry.setDelta(delta);
        logEntry.setType(type);
        logEntry.setSource(source.name());
        logEntry.setRefId(refId);
        logEntry.setBalanceAfter(balanceAfter);
        wishResourceLogMapper.insert(logEntry);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void incrementOnWishCheckin(Long userId) {
        int affected = wishUserStatMapper.update(null,
                new LambdaUpdateWrapper<WishUserStat>()
                        .eq(WishUserStat::getUserId, userId)
                        .setSql("total_checkin_days = total_checkin_days + 1")
                        .set(WishUserStat::getLastActiveAt, LocalDateTime.now())
        );
        if (affected == 0) {
            log.warn("打卡统计+1失败（用户统计记录不存在）, userId={}", userId);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public LevelUpVO checkAndLevelUp(Long userId) {
        // FOR UPDATE：与统计写入串行化，避免并发判定互相覆盖（只升不降下最坏为漏报，下轮扫描兜底）
        WishUserStat stat = wishUserStatMapper.selectOne(
                new LambdaQueryWrapper<WishUserStat>()
                        .eq(WishUserStat::getUserId, userId)
                        .last("FOR UPDATE"));
        if (stat == null) {
            return null;
        }

        int earnedLevel = evaluateLevel(
                nullSafe(stat.getTotalWishes()),
                nullSafe(stat.getTotalCheckinDays()),
                nullSafe(stat.getTotalFulfilled()),
                nullSafe(stat.getTotalHelped()));
        int currentHighest = stat.getHighestLevel() != null ? stat.getHighestLevel() : 1;
        if (earnedLevel <= currentHighest) {
            return null; // 只升不降：未达更高等级保持现状
        }

        String newTitle = LEVEL_TITLES.get(earnedLevel);
        wishUserStatMapper.update(null,
                new LambdaUpdateWrapper<WishUserStat>()
                        .eq(WishUserStat::getUserId, userId)
                        .set(WishUserStat::getLevel, (byte) earnedLevel)
                        .set(WishUserStat::getHighestLevel, (byte) earnedLevel)
                        .set(WishUserStat::getLevelTitle, newTitle));
        log.info("等级提升, userId={}, {} -> {} ({})", userId, currentHighest, earnedLevel, newTitle);
        return new LevelUpVO(currentHighest, earnedLevel, newTitle);
    }

    /**
     * 等级判定（文档 6.5）：基于累计行为指标，与星光余额独立。
     * 自高向低取首个满足等级（等级间无前置依赖，与原硬编码判定语义一致）；
     * 条件阈值见 {@link #LEVEL_REQUIREMENTS}（表驱动，进度查询共用）。
     */
    /** 等级标题查询（表驱动；供运维任务/外部调用） */
    static String levelTitleOf(int level) {
        return LEVEL_TITLES.getOrDefault(level, LEVEL_TITLES.get(1));
    }

    static int evaluateLevel(int totalWishes, int totalCheckinDays, int totalFulfilled, int totalHelped) {
        Map<String, Integer> metrics = Map.of(
                "totalWishes", totalWishes,
                "totalCheckinDays", totalCheckinDays,
                "totalFulfilled", totalFulfilled,
                "totalHelped", totalHelped);
        for (int level = 5; level >= 2; level--) {
            Map<String, Integer> requirements = LEVEL_REQUIREMENTS.get(level);
            boolean satisfied = requirements.entrySet().stream()
                    .allMatch(entry -> metrics.getOrDefault(entry.getKey(), 0) >= entry.getValue());
            if (satisfied) {
                return level;
            }
        }
        return 1;
    }

    @Override
    public MyLevelVO getMyLevel(Long userId) {
        WishUserStat stat = wishUserStatMapper.selectById(userId);
        if (stat == null) {
            // 只读接口不落统计记录：无记录按 L1 初始态返回
            return buildMyLevelVO(1, 0, 0, 0, 0);
        }
        int highest = stat.getHighestLevel() != null ? stat.getHighestLevel() : 1;
        return buildMyLevelVO(highest,
                nullSafe(stat.getTotalWishes()),
                nullSafe(stat.getTotalCheckinDays()),
                nullSafe(stat.getTotalFulfilled()),
                nullSafe(stat.getTotalHelped()));
    }

    /** 组装等级进度 VO：nextLevel 取 current+1（满级为 null），进度按各维度 threshold 计算百分比 */
    private MyLevelVO buildMyLevelVO(int currentLevel, int totalWishes, int totalCheckinDays,
                                     int totalFulfilled, int totalHelped) {
        Map<String, Integer> metrics = Map.of(
                "totalWishes", totalWishes,
                "totalCheckinDays", totalCheckinDays,
                "totalFulfilled", totalFulfilled,
                "totalHelped", totalHelped);
        boolean maxLevel = currentLevel >= 5;
        Integer nextLevel = maxLevel ? null : currentLevel + 1;

        List<LevelRequirementVO> requirements = maxLevel ? List.of()
                : LEVEL_REQUIREMENTS.get(nextLevel).entrySet().stream()
                        .map(entry -> {
                            int current = metrics.getOrDefault(entry.getKey(), 0);
                            int percentage = (int) Math.min(100L, 100L * current / entry.getValue());
                            return new LevelRequirementVO(entry.getKey(),
                                    LEVEL_METRIC_LABELS.getOrDefault(entry.getKey(), entry.getKey()),
                                    current, entry.getValue(), percentage);
                        })
                        .toList();

        return new MyLevelVO(currentLevel, LEVEL_TITLES.get(currentLevel),
                totalWishes, totalCheckinDays, totalFulfilled, totalHelped,
                nextLevel, maxLevel ? null : LEVEL_TITLES.get(nextLevel), requirements);
    }

    private static int nullSafe(Integer value) {
        return value != null ? value : 0;
    }
}
