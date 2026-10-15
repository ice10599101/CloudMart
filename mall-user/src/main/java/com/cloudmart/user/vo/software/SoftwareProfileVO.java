package com.cloudmart.user.vo.software;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 小答软件展示用最小资料（方案 9.6）：仅昵称/头像/账号可用性，
 * 不返回邮箱、手机号等敏感字段；请求中不存在的 ID 不会出现在结果里
 * （调用方据此识别逐 ID 缺失）。
 */
@Schema(description = "小答软件用户最小资料")
public record SoftwareProfileVO(
        @Schema(description = "用户ID") Long id,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "头像") String avatar,
        @Schema(description = "账号是否可用（status=1）") boolean accountAvailable) {
}
