package com.cloudmart.admin.dto.feign;

public record CreateLiveRoomRequest(
    String title,
    String description,
    Long anchorUserId,
    String anchorName,
    String coverImage,
    String streamUrl,
    Long productId,
    Long seckillActivityId,
    Integer maxViewers
) {}
