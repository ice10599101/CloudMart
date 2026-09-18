package com.cloudmart.community.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 投票详情 VO（含聚合结果）。
 *
 * @param id           投票 ID
 * @param question     投票问题
 * @param multiple     是否多选
 * @param options      选项及票数
 * @param totalVotes   参与人数（去重用户数）
 * @param myOptionIds  当前用户选中的选项 ID（未登录/未投为空列表）
 */
@Schema(description = "投票详情")
public record PollVO(
        String id,
        String question,
        Boolean multiple,
        List<PollOptionVO> options,
        Long totalVotes,
        List<Long> myOptionIds
) {

    @Schema(description = "投票选项及票数")
    public record PollOptionVO(
            Long id,
            String content,
            Long voteCount
    ) {
    }
}
