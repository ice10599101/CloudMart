package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "评价VO")
public record ReviewVO(
    @Schema(description = "评价ID") Long id,
    @Schema(description = "用户名") String username,
    @Schema(description = "评分") Integer rating,
    @Schema(description = "评价内容") String content,
    @Schema(description = "评价图片") List<String> images,
    @Schema(description = "状态") Integer status,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
