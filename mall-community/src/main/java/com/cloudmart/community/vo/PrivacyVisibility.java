package com.cloudmart.community.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户资料敏感字段可见性结果。
 *
 * <p>由 mall-community 依据目标用户的可见性设置与查看者关系计算，
 * 供 mall-user 在他人资料接口中对生日/邮箱做脱敏，
 * 并供社区接口对粉丝/关注/收藏/帖子回复列表做访问控制。</p>
 */
@Schema(description = "资料字段可见性")
public record PrivacyVisibility(
        @Schema(description = "生日是否可见") boolean birthdayVisible,
        @Schema(description = "邮箱是否可见") boolean emailVisible,
        @Schema(description = "粉丝列表是否可见") boolean followersVisible,
        @Schema(description = "关注列表是否可见") boolean followingVisible,
        @Schema(description = "收藏列表是否可见") boolean collectionsVisible,
        @Schema(description = "帖子/回复是否可见") boolean postsVisible
) {}