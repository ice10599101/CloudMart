package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 投瓶请求：content（自由匿名文字）与 wishId（关联心愿）二选一。
 */
@Schema(description = "投瓶请求")
public record ThrowBottleRequest(
        @Schema(description = "自由匿名文字（与 wishId 二选一）")
        @Size(max = 500, message = "漂流瓶文字最长 500 字")
        String content,

        @Schema(description = "关联心愿 ID（与 content 二选一）")
        Long wishId
) {
}