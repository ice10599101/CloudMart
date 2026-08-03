package com.cloudmart.admin.dto.feign;

import java.time.LocalDateTime;

public record LiveRoomDTO(
    Long id,
    String title,
    String description,
    Long anchorUserId,
    String anchorName,
    String coverImage,
    String streamUrl,
    Long productId,
    Long seckillActivityId,
    Integer maxViewers,
    Integer currentViewers,
    Long totalViewers,
    String status,
    LocalDateTime startTime,
    LocalDateTime endTime,
    LocalDateTime createdAt
) {}
