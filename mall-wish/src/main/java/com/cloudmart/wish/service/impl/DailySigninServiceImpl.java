package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.WishDailySignin;
import com.cloudmart.wish.entity.WishSigninMilestoneClaim;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.feign.CommunityFeignClient;
import com.cloudmart.wish.repository.WishDailySigninMapper;
import com.cloudmart.wish.repository.WishSigninMilestoneClaimMapper;
import com.cloudmart.wish.service.DailySigninService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.DailySigninVO;
import com.cloudmart.wish.vo.LevelUpVO;
import com.cloudmart.wish.vo.SigninCalendarVO;
import com.cloudmart.wish.vo.SigninMilestoneClaimVO;
import com.cloudmart.wish.vo.SigninMilestoneVO;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /** 每日签到经验奖励（基础值，经 mall-community 成长体系发放） */
    static final int SIGNIN_EXP_REWARD = 10;

    /** 连续签到里程碑奖励配置（天数 → 星光/经验） */
    private record MilestoneReward(int days, int starlight, int exp) {}

    private static final List<MilestoneReward> MILESTONES = List.of(
            new MilestoneReward(7, 30, 100),
            new MilestoneReward(14, 80, 200),
            new MilestoneReward(30, 200, 500));

    /** 签到经验发放来源（与 mall-community 原生签到 CHECK_IN 区分） */
    private static final String EXP_SOURCE_SIGNIN = "WISH_SIGNIN";
    private static final String EXP_SOURCE_MILESTONE = "WISH_SIGNIN_MILESTONE";

    private final WishDailySigninMapper wishDailySigninMapper;
    private final WishSigninMilestoneClaimMapper wishSigninMilestoneClaimMapper;
    private final UserStatService userStatService;
    private final CommunityFeignClient communityFeignClient;

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
        // 经验 +10（mall-community 成长体系；community 不可用时不影响星光）
        boolean expGranted = grantExp(userId, SIGNIN_EXP_REWARD, EXP_SOURCE_SIGNIN, "每日签到");
        // 签到瞬间等级提升检测（文档 6.5，只升不降；未提升返回 null）
        LevelUpVO levelUp = userStatService.checkAndLevelUp(userId);
        int consecutiveDays = wishDailySigninMapper.countConsecutiveDays(userId, today);

        log.info("每日签到成功, userId={}, date={}, consecutive={}, credited={}, expGranted={}, levelUp={}",
                userId, today, consecutiveDays, credited, expGranted, levelUp != null);
        return new DailySigninVO(true, consecutiveDays, credited, SIGNIN_REWARD,
                SIGNIN_EXP_REWARD, expGranted, levelUp);
    }

    @Override
    public List<SigninMilestoneVO> listMilestones(Long userId) {
        LocalDate today = LocalDate.now(ZoneId.of(userStatService.getUserTimezone(userId)));
        int consecutiveDays = wishDailySigninMapper.countConsecutiveDays(userId, today);

        Set<Integer> claimedDays = wishSigninMilestoneClaimMapper.selectList(
                        new LambdaQueryWrapper<WishSigninMilestoneClaim>()
                                .eq(WishSigninMilestoneClaim::getUserId, userId))
                .stream()
                .map(WishSigninMilestoneClaim::getMilestoneDays)
                .collect(Collectors.toSet());

        return MILESTONES.stream()
                .map(m -> new SigninMilestoneVO(
                        m.days(),
                        m.starlight(),
                        m.exp(),
                        claimedDays.contains(m.days()),
                        !claimedDays.contains(m.days()) && consecutiveDays >= m.days()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SigninMilestoneClaimVO claimMilestone(Long userId, int milestoneDays) {
        MilestoneReward milestone = MILESTONES.stream()
                .filter(m -> m.days() == milestoneDays)
                .findFirst()
                .orElseThrow(() -> new BusinessException(WishErrorCodes.WISH_MILESTONE_INVALID, "里程碑天数非法"));

        LocalDate today = LocalDate.now(ZoneId.of(userStatService.getUserTimezone(userId)));
        int consecutiveDays = wishDailySigninMapper.countConsecutiveDays(userId, today);
        if (consecutiveDays < milestone.days()) {
            throw new BusinessException(WishErrorCodes.WISH_MILESTONE_NOT_REACHED,
                    "连续签到未满 " + milestone.days() + " 天");
        }

        WishSigninMilestoneClaim claim = new WishSigninMilestoneClaim();
        claim.setUserId(userId);
        claim.setMilestoneDays(milestone.days());
        claim.setStarlightReward(milestone.starlight());
        claim.setExpReward(milestone.exp());
        try {
            wishSigninMilestoneClaimMapper.insert(claim);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(WishErrorCodes.WISH_MILESTONE_ALREADY_CLAIMED, "该里程碑奖励已领取");
        }

        int credited = userStatService.earnStarlight(
                userId, milestone.starlight(), ResourceLogSource.SIGNIN_MILESTONE, claim.getId());
        boolean expGranted = grantExp(userId, milestone.exp(), EXP_SOURCE_MILESTONE,
                "连续签到 " + milestone.days() + " 天里程碑");
        LevelUpVO levelUp = userStatService.checkAndLevelUp(userId);

        log.info("里程碑领取成功, userId={}, days={}, starlight={}, expGranted={}, levelUp={}",
                userId, milestone.days(), credited, expGranted, levelUp != null);
        return new SigninMilestoneClaimVO(milestone.days(), credited, milestone.exp(), expGranted, levelUp);
    }

    /**
     * 经 mall-community 内部接口发放经验；community 不可用/失败时返回 false，
     * 不阻断签到与星光主链路（经验为增强奖励，可下轮重试由用户手动触发补发）。
     */
    private boolean grantExp(Long userId, int exp, String source, String description) {
        try {
            ApiResponse<Map<String, Object>> res = communityFeignClient.grantExp(
                    Map.of("userId", userId, "exp", exp, "source", source, "description", description));
            return res != null && res.success();
        } catch (Exception e) {
            log.warn("经验发放失败, userId={}, exp={}, source={}, err={}", userId, exp, source, e.getMessage());
            return false;
        }
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
