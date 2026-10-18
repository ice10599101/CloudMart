package com.cloudmart.software.vo.software;

import java.time.LocalDateTime;

/** 我的软件条目（方案 7.3 GET /software/mine）：本人视角最小字段 */
public record SoftwareMineItemVO(
        Long softwareId,
        String name,
        String summary,
        /** 展示状态：PUBLISHED/UNLISTED/BANNED/DRAFT/PENDING/REJECTED */
        String status,
        Long revisionId,
        Integer favoriteCount,
        LocalDateTime updatedAt,
        /** 收藏占位：软件已不可见时 true 且 name/summary 为空（4.5 不泄露内容） */
        Boolean unavailable) {
}
