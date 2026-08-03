package com.cloudmart.community.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePostRequest(
    @Size(max = 200) String title,
    String content,
    String coverImage,
    List<String> mediaUrls,
    String mediaType,
    Long categoryId,
    Long productId,
    List<Long> tagIds,
    Integer status
) {}
