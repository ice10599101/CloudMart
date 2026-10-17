package com.cloudmart.user.service;

import java.util.Map;

import com.cloudmart.user.vo.InviteInfoVO;

/**
 * 邀请裂变（N-3）：邀请码查询（首查生成）与绑定（双向奖励）。
 */
public interface InviteService {

    /** 我的邀请信息（码 + 已邀请人数 + 各状态奖励额）；码不存在时生成 */
    InviteInfoVO myInvites(Long userId);

    /**
     * 绑定邀请关系（受邀人调用）。约束：码存在、非自邀、受邀人未绑定过；
     * 成功后双向发奖（幂等键 INVITE_RELATION:{id}，失败可重试补发）。
     *
     * @return 绑定关系 ID（幂等依据）
     */
    Long bind(Long userId, String code);

    /** WISH-04：资格检查与一次性发奖（签到回调；达到资格仅发一次） */
    Map<String, Object> eligibilityCheck(Long inviteeId, int distinctSigninDays);

    /** WISH-04：邀请关系台账分页（后台查询：资格事实/奖励状态/限制原因） */
    Map<String, Object> pageRelationsForOps(String rewardStatus, int page, int size);
}