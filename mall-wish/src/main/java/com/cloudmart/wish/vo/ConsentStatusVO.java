package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.ConsentAction;
import com.cloudmart.wish.enums.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 同意状态 VO（按最新一条记录判定）。
 *
 * @param consentType  同意类型
 * @param granted      当前是否处于同意状态（最新记录 action=GRANT）
 * @param version      最新同意/撤回的协议版本号（无记录为 null）
 * @param latestAction 最新动作（无记录为 null）
 * @param updatedAt    最新记录时间（UTC，无记录为 null）
 */
@Schema(description = "同意状态")
public record ConsentStatusVO(
        @Schema(description = "同意类型") ConsentType consentType,
        @Schema(description = "当前是否同意") boolean granted,
        @Schema(description = "最新协议版本号") String version,
        @Schema(description = "最新动作") ConsentAction latestAction,
        @Schema(description = "最新记录时间（UTC）") LocalDateTime updatedAt
) {
}
