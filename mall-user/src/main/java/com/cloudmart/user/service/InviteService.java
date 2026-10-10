package com.cloudmart.user.service;

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
}
