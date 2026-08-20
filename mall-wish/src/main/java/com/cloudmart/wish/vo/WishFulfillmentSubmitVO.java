package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交还愿结果 VO（文档 2.4 节）。
 *
 * <p>产品决策（2026-08-20）：统一即时生效——提交后心愿直接 FULFILLED
 * 并生成绽放果实，还愿故事走先发后审；响应 status 恒为 FULFILLED，
 * FULFILLING 保留给后续 STRICT 审核流。</p>
 *
 * @param id              还愿记录 ID
 * @param wishId          心愿 ID
 * @param status          提交后心愿状态（即时生效恒为 FULFILLED）
 * @param fruitType       果实类型（还愿生成绽放果实 BLOOM）
 * @param badgeAwarded    本次新获得的徽章列表（首次还愿触发 FIRST_FULFILL）
 * @param starlightReward 还愿奖励星光（文档 6.1：还愿完成 +50）
 * @param createdAt       提交时间
 */
@Schema(description = "提交还愿结果")
public record WishFulfillmentSubmitVO(
        @Schema(description = "还愿记录 ID") Long id,
        @Schema(description = "心愿 ID") Long wishId,
        @Schema(description = "提交后心愿状态") WishStatus status,
        @Schema(description = "果实类型") FruitType fruitType,
        @Schema(description = "本次新获得的徽章列表") List<BadgeAwardedVO> badgeAwarded,
        @Schema(description = "还愿奖励星光") int starlightReward,
        @Schema(description = "提交时间") LocalDateTime createdAt
) {

    /**
     * 新获得徽章项。
     *
     * @param id   徽章 ID
     * @param name 徽章名称
     */
    @Schema(description = "新获得徽章")
    public record BadgeAwardedVO(
            @Schema(description = "徽章 ID") Long id,
            @Schema(description = "徽章名称") String name
    ) {
    }
}
