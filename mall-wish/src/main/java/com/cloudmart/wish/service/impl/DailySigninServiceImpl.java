package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.WishDailySignin;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.repository.WishDailySigninMapper;
import com.cloudmart.wish.service.DailySigninService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.DailySigninVO;
import com.cloudmart.wish.vo.LevelUpVO;
import com.cloudmart.wish.vo.SigninCalendarVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 用户每日签到服务实现（文档 2.6 / 6.1）。
 *
 * <p>签到日期按用户时区去重（{@code wish_user_stat.timezone}，缺省
 * Asia/Shanghai）；并发幂等由 {@code uk_signin_daily} 唯一键兜底；
 * 签到 + 星光发放（+5，SIGNIN 流水）+ 等级提升检测同事务（文档 6.4）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailySigninServiceImpl implements DailySigninService {

    /** 每日签到星光奖励（文档 6.1：每日签到 +5，固定值无递增） */
    static final int SIGNIN_REWARD = 5;

    private final WishDailySigninMapper wishDailySigninMapper;
    private final UserStatService userStatService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DailySigninVO signin(Long userId) {
        LocalDate today = LocalDate.now(ZoneId.of(userStatService.getUserTimezone(userId)));

        // 预查快速失败；并发双击由 uk_signin_daily 唯一键兜底（409）
        Long existing = wishDailySigninMapper.selectCount(new LambdaQueryWrapper<WishDailySignin>()
                .eq(WishDailySignin::getUserId, userId)
                .eq(WishDailySignin::getSigninDate, today));
        if (existing != null && existing > 0) {
            throw new BusinessException(WishErrorCodes.WISH_ALREADY_SIGNED_IN, "今日已签到");
        }

        WishDailySignin signin = new WishDailySignin();
        signin.setUserId(userId);
        signin.setSigninDate(today);
        signin.setStarlightGranted(true);
        try {
            wishDailySigninMapper.insert(signin);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(WishErrorCodes.WISH_ALREADY_SIGNED_IN, "今日已签到");
        }

        // 星光 +5（SIGNIN 流水，refId=签到记录 ID；余额达 5000 上限时截断入账）
        int credited = userStatService.earnStarlight(
                userId, SIGNIN_REWARD, ResourceLogSource.SIGNIN, signin.getId());
        // 签到瞬间等级提升检测（文档 6.5，只升不降；未提升返回 null）
        LevelUpVO levelUp = userStatService.checkAndLevelUp(userId);
        int consecutiveDays = wishDailySigninMapper.countConsecutiveDays(userId, today);

        log.info("每日签到成功, userId={}, date={}, consecutive={}, credited={}, levelUp={}",
                userId, today, consecutiveDays, credited, levelUp != null);
        return new DailySigninVO(true, consecutiveDays, credited, SIGNIN_REWARD, levelUp);
    }

    @Override
    public SigninCalendarVO getCalendar(Long userId, String month) {
        YearMonth yearMonth = parseMonth(month);

        List<String> signedDates = wishDailySigninMapper.selectList(new LambdaQueryWrapper<WishDailySignin>()
                        .eq(WishDailySignin::getUserId, userId)
                        .ge(WishDailySignin::getSigninDate, yearMonth.atDay(1))
                        .le(WishDailySignin::getSigninDate, yearMonth.atEndOfMonth())
                        .orderByAsc(WishDailySignin::getSigninDate))
                .stream()
                .map(s -> s.getSigninDate().toString())
                .toList();

        Long totalDays = wishDailySigninMapper.selectCount(new LambdaQueryWrapper<WishDailySignin>()
                .eq(WishDailySignin::getUserId, userId));

        return new SigninCalendarVO(
                signedDates,
                computeCurrentStreak(userId),
                totalDays == null ? 0 : totalDays.intValue());
    }

    /**
     * 当前连续签到天数：最近一次签到为今日/昨日时统计其所在连续段；
     * 已断签（最近签到早于昨日）返回 0，等待下次签到重新起算。
     */
    private int computeCurrentStreak(Long userId) {
        WishDailySignin latest = wishDailySigninMapper.selectOne(new LambdaQueryWrapper<WishDailySignin>()
                .eq(WishDailySignin::getUserId, userId)
                .orderByDesc(WishDailySignin::getSigninDate)
                .last("LIMIT 1"));
        if (latest == null) {
            return 0;
        }
        LocalDate today = LocalDate.now(ZoneId.of(userStatService.getUserTimezone(userId)));
        LocalDate anchor = latest.getSigninDate();
        if (anchor.isBefore(today.minusDays(1))) {
            return 0;
        }
        return wishDailySigninMapper.countConsecutiveDays(userId, anchor);
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "month 不能为空（yyyy-MM）");
        }
        try {
            // ISO yyyy-MM（月份必须两位），拒绝 2026-9 之类的宽松输入
            return YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "month 格式非法（yyyy-MM）");
        }
    }
}
