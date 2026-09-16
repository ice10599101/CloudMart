package com.cloudmart.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 浏览足迹上报请求。targetType ∈ {PRODUCT, POST, WISH}；
 * title/cover 为展示用标题与封面快照，可为空。
 */
public record RecordBrowseRequest(
        @NotBlank @Pattern(regexp = "PRODUCT|POST|WISH", message = "targetType 必须为 PRODUCT/POST/WISH")
        String targetType,
        @NotNull @Positive Long targetId,
        @Size(max = 200) String title,
        @Size(max = 500) String cover
) {}