package com.cloudmart.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 活动报名审核请求（ADMIN-05）。
 *
 * <p>{@code approved} 必须显式给出：缺省参数不能默认成“驳回”，
 * 也不接受客户端自报审核人（操作者取认证上下文，服务端审计）。</p>
 */
@Schema(description = "活动报名审核请求")
public record WishActivityReviewRequest(
        @Schema(description = "true=批准进组；false=驳回", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "approved 不能为空")
        Boolean approved) {
}
