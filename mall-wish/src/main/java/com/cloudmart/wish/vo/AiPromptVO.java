package com.cloudmart.wish.vo;

import com.cloudmart.wish.entity.WishAiPrompt;
import com.cloudmart.wish.enums.AiPromptScene;
import com.cloudmart.wish.enums.AiPromptStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Prompt 模板 VO（管理端，Sprint 2.5）。
 *
 * @param id             模板 ID
 * @param scene          AI 场景
 * @param version        版本号（scene 内递增）
 * @param name           模板名称
 * @param content        Prompt 正文
 * @param abGroup        A/B 分组
 * @param trafficPercent 流量百分比
 * @param status         状态
 * @param remark         变更说明
 * @param createdAt      创建时间（UTC）
 * @param updatedAt      更新时间（UTC）
 */
@Schema(description = "Prompt 模板")
public record AiPromptVO(
        @Schema(description = "模板 ID") Long id,
        @Schema(description = "AI 场景") AiPromptScene scene,
        @Schema(description = "版本号") Integer version,
        @Schema(description = "模板名称") String name,
        @Schema(description = "Prompt 正文") String content,
        @Schema(description = "A/B 分组：ALL/A/B") String abGroup,
        @Schema(description = "流量百分比") Integer trafficPercent,
        @Schema(description = "状态") AiPromptStatus status,
        @Schema(description = "变更说明") String remark,
        @Schema(description = "创建时间（UTC）") LocalDateTime createdAt,
        @Schema(description = "更新时间（UTC）") LocalDateTime updatedAt
) {
    public static AiPromptVO from(WishAiPrompt prompt) {
        return new AiPromptVO(prompt.getId(), prompt.getScene(), prompt.getVersion(),
                prompt.getName(), prompt.getContent(), prompt.getAbGroup(),
                prompt.getTrafficPercent(), prompt.getStatus(), prompt.getRemark(),
                prompt.getCreatedAt(), prompt.getUpdatedAt());
    }
}
