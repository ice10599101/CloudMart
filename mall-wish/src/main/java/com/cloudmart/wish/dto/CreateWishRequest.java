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
        @Size(max = 20000, message = "心愿描述不能超过20000字符")
        String description,

        List<String> mediaUrls,

        @NotNull(message = "心愿分类不能为空")
        Long categoryId,

        @Size(max = 5, message = "标签最多5个")
        List<String> tags,

        WishVisibility visibility,

        LocalDateTime expectedAt,

        Boolean enableAiReply,

        Boolean triggerEnvEmo,

        /** 纬度（可选，PUBLIC 心愿 LBS 用；服务端 geohash 编码，原始坐标不落库不落日志——Sprint 3.1 隐私验收） */
        Double latitude,

        /** 经度（可选，PUBLIC 心愿 LBS 用） */
        Double longitude
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
