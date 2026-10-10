package com.cloudmart.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.user.entity.UserInviteCode;
import com.cloudmart.user.entity.UserInviteRelation;
import com.cloudmart.user.feign.WishStarlightFeignClient;
import com.cloudmart.user.repository.UserInviteCodeMapper;
import com.cloudmart.user.repository.UserInviteRelationMapper;
import com.cloudmart.user.service.InviteService;
import com.cloudmart.user.vo.InviteInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
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
    private final WishStarlightFeignClient wishStarlightFeignClient;
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
        return new InviteInfoVO(code.getCode(), invited == null ? 0 : invited.intValue(), rewardInviter);
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

        // 双向发奖（事务提交后执行更稳，但 mall-user 无编程式事务钩子依赖——
        // 直接同事务调用：奖励失败仅记日志不回滚绑定（fail-open），幂等键可补发）
        grantReward(inviteCode.getUserId(), relation.getId(), rewardInviter);
        grantReward(userId, relation.getId(), rewardInvitee);
        log.info("N-3 邀请绑定成功: inviter={}, invitee={}, relationId={}",
                inviteCode.getUserId(), userId, relation.getId());
        return relation.getId();
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

    /** 双向奖励发放（幂等键含角色角色名，互不冲突）；失败仅记日志（fail-open） */
    private void grantReward(Long userId, Long relationId, int amount) {
        if (amount <= 0) {
            return;
        }
        try {
            wishStarlightFeignClient.earn(userId, amount, relationId,
                    SOURCE_INVITE + ":" + relationId + ":" + userId, SOURCE_INVITE);
        } catch (Exception e) {
            log.warn("N-3 邀请奖励发放失败（fail-open 跳过，幂等键可补发）: userId={}, relationId={}, err={}",
                    userId, relationId, e.getMessage());
        }
    }
}
