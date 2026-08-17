package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.GrowthRecordType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 心愿成长记录 VO（嵌套于 {@link WishVO} 详情接口）。
 *
 * @param id            成长记录 ID
 * @param type          记录类型
 * @param content       记录内容
 * @param mediaUrls     媒体资源 URL 列表
 * @param progressDelta 本次进度增量
 * @param createdAt     创建时间（UTC）
 */
@Schema(name = "WishGrowthRecordVO", description = "心愿成长记录")
public record WishGrowthRecordVO(
        @Schema(description = "成长记录 ID") Long id,
        @Schema(description = "记录类型") GrowthRecordType type,
        @Schema(description = "记录内容") String content,
        @Schema(description = "媒体资源 URL 列表") List<String> mediaUrls,
        @Schema(description = "本次进度增量") Short progressDelta,
        @Schema(description = "创建时间") LocalDateTime createdAt
) {}
