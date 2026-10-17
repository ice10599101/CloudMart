package com.cloudmart.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.user.entity.UserInviteCode;
import com.cloudmart.user.entity.UserInviteRelation;
import com.cloudmart.user.entity.UserRewardObligation;
import com.cloudmart.user.repository.UserInviteCodeMapper;
import com.cloudmart.user.repository.UserInviteRelationMapper;
import com.cloudmart.user.repository.UserMapper;
import com.cloudmart.user.config.UserInviteProperties;
import com.cloudmart.user.service.InviteService;
import com.cloudmart.user.vo.InviteInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.List;

/**
 * 邀请裂变（N-3）。
 *
 * <p>一人一码（首查生成，8 位大写去混淆字符集）；受邀人注册后凭码绑定，
 * 一人只可被邀请一次。绑定成功后双向发奖：操作键 INVITE_RELATION:{relationId}
 * 幂等（B01），发奖失败不回滚绑定（fail-open，幂等键保留可安全补发）——
 * 奖励是促销性质，绑定关系才是主事实。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    /** Crockford 风格去混淆字符集（无 I/L/O/U） */
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int CODE_LENGTH = 8;
    private static final int INVITER_REWARD = 50;
    private static final int INVITEE_REWARD = 30;
    private static final String SOURCE_INVITE = "INVITE_REWARD";

    private final UserInviteCodeMapper inviteCodeMapper;
    private final UserInviteRelationMapper inviteRelationMapper;
    private final UserRewardObligationService rewardObligationService;
    private final UserMapper userMapper;
    private final UserInviteProperties inviteProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${invite.reward-inviter:" + INVITER_REWARD + "}")
    private int rewardInviter;

    @Value("${invite.reward-invitee:" + INVITEE_REWARD + "}")
    private int rewardInvitee;

    @Override
    public InviteInfoVO myInvites(Long userId) {
        UserInviteCode code = requireCode(userId);
        Long invited = inviteRelationMapper.selectCount(
                new LambdaQueryWrapper<UserInviteRelation>().eq(UserInviteRelation::getInviterId, userId));
        // WISH-04：展示规则版本与本人资格进度（my-joins 口径一致：状态/原因）
        final var myRelation = inviteRelationMapper.selectOne(
                new LambdaQueryWrapper<UserInviteRelation>()
                        .eq(UserInviteRelation::getInviteeId, userId)
                        .last("LIMIT 1"));
        return new InviteInfoVO(code.getCode(), invited == null ? 0 : invited.intValue(), rewardInviter,
                inviteProperties.getRuleVersion(),
                myRelation == null ? "NO_RELATION"
                        : (myRelation.getRewardStatus() == null ? "PENDING_ELIGIBILITY" : myRelation.getRewardStatus()),
                myRelation == null ? "未绑定邀请人"
                        : (myRelation.getRewardReason() == null ? "" : myRelation.getRewardReason()));
    }

    @Override
    @Transactional
    public Long bind(Long userId, String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("INVITE_CODE_INVALID", "请输入邀请码");
        }
        UserInviteCode inviteCode = inviteCodeMapper.selectOne(
                new LambdaQueryWrapper<UserInviteCode>().eq(UserInviteCode::getCode, code.trim().toUpperCase()));
        if (inviteCode == null) {
            throw new BusinessException("INVITE_CODE_INVALID", "邀请码不存在");
        }
        if (inviteCode.getUserId().equals(userId)) {
            throw new BusinessException("INVITE_SELF", "不能绑定自己的邀请码");
        }
        Long exists = inviteRelationMapper.selectCount(
                new LambdaQueryWrapper<UserInviteRelation>().eq(UserInviteRelation::getInviteeId, userId));
        if (exists != null && exists > 0) {
            throw new BusinessException("INVITE_ALREADY_BOUND", "你已绑定过邀请人");
        }

        UserInviteRelation relation = new UserInviteRelation();
        relation.setInviterId(inviteCode.getUserId());
        relation.setInviteeId(userId);
        try {
            inviteRelationMapper.insert(relation);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException("INVITE_ALREADY_BOUND", "你已绑定过邀请人");
        }

        // WISH-04：绑定不即发——关系登记 PENDING_ELIGIBILITY；受邀人完成
        // requiredDistinctSigninDays 个不同业务日正常签到且账号满 24h 后，
        // 由签到回调 eligibilityCheck 触发资格判定与一次性发奖（防批量注册薅奖励）。
        relation.setRewardStatus("PENDING_ELIGIBILITY");
        relation.setRuleVersion(inviteProperties.getRuleVersion());
        inviteRelationMapper.updateById(relation);
        log.info("[WISH04] 邀请绑定成功（待资格）: inviter={}, invitee={}, relationId={}, ruleV={}",
                inviteCode.getUserId(), userId, relation.getId(), inviteProperties.getRuleVersion());
        return relation.getId();
    }

    /**
     * WISH-04：资格检查与一次性发奖（由 mall-wish 签到事务提交后回调）。
     * 资格 = requiredDistinctSigninDays 个不同业务日正常签到 + 账号满 24h；
     * 异常/受限账号 → HOLD；额度超限 → REJECTED_LIMIT（原因台账可查）；
     * 达到资格仅发一次（GRANTED 幂等）。
     */
    @Override
    @Transactional
    public Map<String, Object> eligibilityCheck(Long inviteeId, int distinctSigninDays) {
        final UserInviteRelation relation = inviteRelationMapper.selectOne(
                new LambdaQueryWrapper<UserInviteRelation>()
                        .eq(UserInviteRelation::getInviteeId, inviteeId)
                        .last("LIMIT 1"));
        if (relation == null) {
            return Map.of("result", "skipped", "reason", "NO_RELATION");
        }
        final String status = relation.getRewardStatus() == null ? "PENDING_ELIGIBILITY" : relation.getRewardStatus();
        if (!"PENDING_ELIGIBILITY".equals(status) && !"ELIGIBLE".equals(status)) {
            return Map.of("result", "skipped", "reason", "ALREADY_" + status);
        }

        final var invitee = userMapper.selectById(inviteeId);
        if (invitee == null) {
            return Map.of("result", "skipped", "reason", "USER_NOT_FOUND");
        }
        if (invitee.getStatus() != null && invitee.getStatus() != 1) {
            relation.setRewardStatus("HOLD");
            relation.setRewardReason("账号状态受限（status=" + invitee.getStatus() + "），待运营审核");
            inviteRelationMapper.updateById(relation);
            return Map.of("result", "hold", "reason", relation.getRewardReason());
        }
        final LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        if (invitee.getCreatedAt() != null
                && invitee.getCreatedAt().isAfter(now.minusHours(inviteProperties.getAccountAgeHours()))) {
            relation.setRewardStatus("PENDING_ELIGIBILITY");
            relation.setRewardReason("账号注册未满 " + inviteProperties.getAccountAgeHours() + " 小时");
            inviteRelationMapper.updateById(relation);
            return Map.of("result", "pending", "reason", "ACCOUNT_TOO_YOUNG");
        }
        if (distinctSigninDays < inviteProperties.getRequiredDistinctSigninDays()) {
            relation.setRewardStatus("PENDING_ELIGIBILITY");
            relation.setRewardReason("有效签到 " + distinctSigninDays + "/"
                    + inviteProperties.getRequiredDistinctSigninDays() + " 个不同业务日");
            inviteRelationMapper.updateById(relation);
            return Map.of("result", "pending", "reason", "SIGNIN_NOT_ENOUGH");
        }

        relation.setRewardStatus("ELIGIBLE");
        relation.setEligibleAt(now);
        relation.setRewardReason(null);
        inviteRelationMapper.updateById(relation);

        final var grantedList = inviteRelationMapper.selectList(
                new LambdaQueryWrapper<UserInviteRelation>()
                        .eq(UserInviteRelation::getInviterId, relation.getInviterId())
                        .eq(UserInviteRelation::getRewardStatus, "GRANTED"));
        final var shanghai = java.time.ZoneId.of("Asia/Shanghai");
        long dailyGranted = grantedList.stream()
                .filter(r -> r.getEligibleAt() != null
                        && LocalDate.now(shanghai).equals(r.getEligibleAt().atZone(shanghai).toLocalDate()))
                .count();
        long monthlyGranted = grantedList.stream()
                .filter(r -> r.getEligibleAt() != null
                        && YearMonth.from(r.getEligibleAt().atZone(shanghai)).equals(YearMonth.now(shanghai)))
                .count();
        if (dailyGranted >= inviteProperties.getDailyLimit()
                || monthlyGranted >= inviteProperties.getMonthlyLimit()) {
            relation.setRewardStatus("REJECTED_LIMIT");
            relation.setRewardReason("邀请人当日/当月额度已满（日 " + dailyGranted + "/"
                    + inviteProperties.getDailyLimit() + "，月 " + monthlyGranted + "/"
                    + inviteProperties.getMonthlyLimit() + "）");
            inviteRelationMapper.updateById(relation);
            return Map.of("result", "rejected", "reason", "QUOTA_EXCEEDED");
        }

        rewardObligationService.enqueueWithCommitDelivery(
                SOURCE_INVITE + ":" + relation.getId() + ":" + relation.getInviterId(),
                SOURCE_INVITE, String.valueOf(relation.getId()), relation.getInviterId(),
                UserRewardObligation.TYPE_STARLIGHT, rewardInviter, "邀请好友奖励（邀请人，资格达标 v"
                        + relation.getRuleVersion() + "）");
        rewardObligationService.enqueueWithCommitDelivery(
                SOURCE_INVITE + ":" + relation.getId() + ":" + inviteeId,
                SOURCE_INVITE, String.valueOf(relation.getId()), inviteeId,
                UserRewardObligation.TYPE_STARLIGHT, rewardInvitee, "受邀奖励（资格达标）");
        relation.setRewardStatus("GRANTED");
        inviteRelationMapper.updateById(relation);
        log.info("[WISH04] 邀请奖励发放: relationId={}, inviter={}, invitee={}, daily={}/{}",
                relation.getId(), relation.getInviterId(), inviteeId, dailyGranted,
                inviteProperties.getDailyLimit());
        return Map.of("result", "granted", "relationId", relation.getId());
    }

    /** 我的邀请码（不存在则生成；生成撞码重试最多 3 次） */
    private UserInviteCode requireCode(Long userId) {
        UserInviteCode code = inviteCodeMapper.selectOne(
                new LambdaQueryWrapper<UserInviteCode>().eq(UserInviteCode::getUserId, userId));
        if (code != null) {
            return code;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            UserInviteCode created = new UserInviteCode();
            created.setUserId(userId);
            created.setCode(generateCode());
            try {
                inviteCodeMapper.insert(created);
                return created;
            } catch (DuplicateKeyException e) {
                // 码撞车重试；用户唯一键冲突说明并发首查，回查返回
                UserInviteCode existing = inviteCodeMapper.selectOne(
                        new LambdaQueryWrapper<UserInviteCode>().eq(UserInviteCode::getUserId, userId));
                if (existing != null) {
                    return existing;
                }
            }
        }
        throw new BusinessException("INVITE_CODE_GENERATE_FAILED", "邀请码生成失败，请稍后重试");
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }


    /** WISH-04：邀请关系台账分页（脱敏事实视图） */
    @Override
    public Map<String, Object> pageRelationsForOps(String rewardStatus, int page, int size) {
        var result = inviteRelationMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(page, 1), size),
                new LambdaQueryWrapper<UserInviteRelation>()
                        .eq(rewardStatus != null && !rewardStatus.isBlank(),
                                UserInviteRelation::getRewardStatus, rewardStatus)
                        .orderByDesc(UserInviteRelation::getId));
        var records = result.getRecords().stream().map(r -> java.util.Map.<String, Object>of(
                "id", r.getId(),
                "inviterId", r.getInviterId(),
                "inviteeId", r.getInviteeId(),
                "rewardStatus", r.getRewardStatus() == null ? "PENDING_ELIGIBILITY" : r.getRewardStatus(),
                "rewardReason", r.getRewardReason() == null ? "" : r.getRewardReason(),
                "eligibleAt", r.getEligibleAt() == null ? "" : r.getEligibleAt().toString(),
                "ruleVersion", r.getRuleVersion() == null ? 1 : r.getRuleVersion())).toList();
        // BASE-02：分页统一 {items,total,page,size}
        return Map.of("items", records, "total", result.getTotal(),
                "page", Math.max(page, 1), "size", size);
    }
}