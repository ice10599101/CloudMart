package com.cloudmart.community.service;

import com.cloudmart.community.dto.CreatePollRequest;
import com.cloudmart.community.vo.PollVO;

/**
 * 投票服务（编辑器附件，V10）。
 *
 * <p>投票随编辑器正文发布幂等落库（主键为客户端 UUID）；投票按
 * (poll, user, option) 唯一约束防刷，单选场景业务层校验仅投 1 项。</p>
 */
public interface PollService {

    /**
     * 创建投票（幂等：同 ID 且同创建者重复提交返回既有数据；同 ID 不同创建者视为冲突）。
     *
     * @param userId  创建者用户 ID
     * @param request 创建请求
     * @return 投票详情
     */
    PollVO createPoll(Long userId, CreatePollRequest request);

    /**
     * 投票详情（含各选项票数与参与人数；myOptionIds 在未登录/未投时为空列表）。
     *
     * @param pollId 投票 ID
     * @param userId 当前用户 ID（可空=匿名查看）
     */
    PollVO getPoll(String pollId, Long userId);

    /**
     * 提交投票（单选恰好 1 项；多选 1-10 项；重复投票拒绝）。
     *
     * @param pollId 投票 ID
     * @param userId 投票用户 ID
     * @param optionIds 选中选项 ID
     */
    void vote(String pollId, Long userId, java.util.List<Long> optionIds);
}
