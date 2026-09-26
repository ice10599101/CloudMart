package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 宠物星光交易操作结果（B01 幂等契约）。
 *
 * <p>{@code duplicate=true} 表示同 operationId 的重复请求命中了已完成的原操作，
 * 返回的是原结果而非重新执行——重试方据此可安全地继续后续本地流程。</p>
 *
 * @param operationId    业务操作唯一键
 * @param operationType  EARN / SPEND
 * @param amount         请求金额
 * @param creditedAmount 实际入账/扣减量（EARN 封顶截断后可能小于 amount）
 * @param balanceAfter   操作后余额快照
 * @param source         流水来源
 * @param refId          关联业务 ID
 * @param status         固定 COMPLETED（表内仅存成功结果）
 * @param duplicate      是否为重复请求命中原结果
 */
@Schema(description = "宠物星光交易幂等结果")
public record PetWalletOperationVO(
        @Schema(description = "业务操作唯一键") String operationId,
        @Schema(description = "操作类型 EARN/SPEND") String operationType,
        @Schema(description = "请求金额") Integer amount,
        @Schema(description = "实际入账量") Integer creditedAmount,
        @Schema(description = "操作后余额") Integer balanceAfter,
        @Schema(description = "流水来源") String source,
        @Schema(description = "关联业务ID") Long refId,
        @Schema(description = "状态") String status,
        @Schema(description = "是否重复请求命中原结果") boolean duplicate) {
}
