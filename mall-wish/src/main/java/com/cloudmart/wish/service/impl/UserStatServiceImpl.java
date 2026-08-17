package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.repository.WishUserStatMapper;
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

    private final WishUserStatMapper wishUserStatMapper;

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
}
