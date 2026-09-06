package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.WishVisibility;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 更新心愿请求 DTO。
 *
 * <p>对应 API: PUT /wish/wishes/{id}（仅作者可操作）</p>
 * <p>所有字段可选，null 表示不更新。</p>
 */
public record UpdateWishRequest(

        @Size(max = 120, message = "心愿标题不能超过120字符")
        String title,

        @Size(max = 20000, message = "心愿描述不能超过20000字符")
        String description,

        List<String> mediaUrls,

        Long categoryId,

        @Size(max = 5, message = "标签最多5个")
        List<String> tags,

        WishVisibility visibility,

        LocalDateTime expectedAt,

        String expectedTimezone,

        /** 纬度（可选，PUBLIC 心愿 LBS 用；服务端 geohash 编码，原始坐标不落库——Sprint 3.1） */
        Double latitude,

        /** 经度（可选，PUBLIC 心愿 LBS 用） */
        Double longitude
) {}
