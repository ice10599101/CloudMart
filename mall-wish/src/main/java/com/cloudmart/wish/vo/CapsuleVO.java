package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 时间胶囊视图（列表/详情/开启结果共用，文档 2.7）。
 *
 * <p>安全契约：status 非 OPENED 时 content/mediaUrls 恒为 null
 * （未到期不可见，防绕过；AVAILABLE 到期未开启同样隐藏，开启是拆信仪式）。</p>
 */
public record CapsuleVO(

        @Schema(description = "胶囊 ID")
        Long id,

        @Schema(description = "标题")
        String title,

        @Schema(description = "内容（未开启恒为 null）")
        String content,

        @Schema(description = "封存媒体 URL 列表（未开启恒为 null）")
        List<String> mediaUrls,

        @Schema(description = "状态: SEALED封印中/AVAILABLE已到期待开启/OPENED已开启/CANCELLED已取消")
        String status,

        @Schema(description = "预定开启时间（UTC，ISO 8601）")
        LocalDateTime openAt,

        @Schema(description = "创建时用户 IANA 时区（回溯展示创建时本地时间用）")
        String openAtTimezone,

        @Schema(description = "实际开启时间（UTC，未开启为 null）")
        LocalDateTime openedAt,

        @Schema(description = "创建时间（UTC）")
        LocalDateTime createdAt
) {
}
