package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 小组详情/我的小组（Sprint 2.6 小组页）。
 *
 * <p>成员仅暴露昵称/头像/活跃度（安全验收：不泄露手机号/邮箱，
 * 批量用户信息接口本身只返回昵称头像）。</p>
 */
@Schema(description = "小组详情")
public record MatchGroupDetailVO(
        Long groupId,
        String keyword,
        Integer memberCount,
        Integer maxMembers,
        String status,
        String cityCode,
        LocalDateTime createdAt,
        /** 当前查看者角色（非成员为 null） */
        String viewerRole,
        List<MemberItem> members
) {

    /**
     * 成员项。
     *
     * @param idleDays      距最近活跃的天数（null=从未活跃/查不到统计；用于"提醒未打卡组员"）
     */
    @Schema(description = "小组成员")
    public record MemberItem(
            Long userId,
            String nickname,
            String avatar,
            String role,
            String status,
            LocalDateTime joinedAt,
            Long idleDays
    ) {
    }
}
