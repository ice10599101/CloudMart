package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.ConsentAction;
import com.cloudmart.wish.enums.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 同意记录 VO（文档 1.2 节 ⑳）。
 *
 * @param id          记录 ID
 * @param consentType 同意类型
 * @param version     协议版本号
 * @param action      动作
 * @param createdAt   创建时间（UTC）
 */
@Schema(description = "同意记录")
public record ConsentRecordVO(
        @Schema(description = "记录 ID") Long id,
        @Schema(description = "同意类型") ConsentType consentType,
        @Schema(description = "协议版本号") String version,
        @Schema(description = "动作") ConsentAction action,
        @Schema(description = "创建时间（UTC）") LocalDateTime createdAt
) {
}
