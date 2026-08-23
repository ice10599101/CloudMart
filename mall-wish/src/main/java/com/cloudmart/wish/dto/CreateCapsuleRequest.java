package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建时间胶囊请求（文档 2.7）。
 *
 * <p>openAt 为 ISO 8601 UTC 绝对时间（到期判定唯一依据）；openAtTz 为创建时
 * 用户 IANA 时区，服务端仅作格式校验与落库审计（文档 26.3 跨时区语义）。</p>
 */
public record CreateCapsuleRequest(

        @Schema(description = "胶囊标题", example = "写给一年后的自己")
        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题不能超过100字")
        String title,

        @Schema(description = "胶囊内容（开启前不可见）")
        @NotBlank(message = "内容不能为空")
        @Size(max = 5000, message = "内容不能超过5000字")
        String content,

        @Schema(description = "封存媒体 URL 列表（≤9 项，OSS Key）")
        @Size(max = 9, message = "媒体文件不能超过9个")
        List<String> mediaUrls,

        @Schema(description = "预定开启时间（ISO 8601 UTC）", example = "2027-08-23T00:00:00")
        @NotNull(message = "开启时间不能为空")
        LocalDateTime openAt,

        @Schema(description = "创建时用户 IANA 时区", example = "Asia/Shanghai")
        @NotBlank(message = "时区不能为空")
        @Size(max = 32, message = "时区格式不正确")
        String openAtTz
) {
}
