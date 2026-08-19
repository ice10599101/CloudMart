package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.WishResourceLog;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.ResourceLogType;
import com.cloudmart.wish.repository.WishResourceLogMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.BadgeService;
import com.cloudmart.wish.service.UserStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
}
