package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.WishVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建心愿请求 DTO。
 *
 * <p>对应 API: POST /wish/wishes</p>
 */
public record CreateWishRequest(

        @NotBlank(message = "心愿标题不能为空")
        @Size(max = 120, message = "心愿标题不能超过120字符")
        String title,

        @NotBlank(message = "心愿描述不能为空")
        @Size(max = 2000, message = "心愿描述不能超过2000字符")
        String description,

        List<String> mediaUrls,

        @NotNull(message = "心愿分类不能为空")
        Long categoryId,

        @Size(max = 5, message = "标签最多5个")
        List<String> tags,

        WishVisibility visibility,

        LocalDateTime expectedAt,

        Boolean enableAiReply,

        Boolean triggerEnvEmo
) {
        public CreateWishRequest {
                if (visibility == null) {
                        visibility = WishVisibility.PUBLIC;
                }
                if (enableAiReply == null) {
                        enableAiReply = false;
                }
                if (triggerEnvEmo == null) {
                        triggerEnvEmo = false;
                }
        }
}
