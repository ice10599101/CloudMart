package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 加入小组请求（Sprint 2.6，文档 2.8：POST /wish/match/groups/{id}/members）。
 *
 * @param message 入组留言（可空，≤200 字）
 */
@Schema(description = "加入小组请求")
public record JoinGroupRequest(
        @Schema(description = "入组留言（可选）")
        @Size(max = 200, message = "入组留言最长 200 字")
        String message
) {
}
