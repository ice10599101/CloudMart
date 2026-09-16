package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 投瓶请求：content（自由匿名富文本，与发帖同款编辑器产出 HTML）与 wishId（关联心愿）二选一。
 */
@Schema(description = "投瓶请求")
public record ThrowBottleRequest(
        @Schema(description = "自由匿名文字（富文本 HTML，与 wishId 二选一）")
        @Size(max = 10000, message = "漂流瓶文字最长 10000 字")
        String content,

        @Schema(description = "是否匿名投瓶（默认 true；false 实名时捞起者可见投瓶人身份）")
        Boolean isAnonymous,

        @Schema(description = "关联心愿 ID（与 content 二选一）")
        Long wishId
) {
}