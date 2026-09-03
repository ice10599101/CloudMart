package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 数据导出任务（Sprint 3.6 补齐，合规 34.2）。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_data_export")
public class DataExport {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** PENDING/PROCESSING/SUCCESS/FAILED */
    private String status;

    private String downloadUrl;

    /** 导出内容 JSON（SUCCESS 后写入；过期任务由查询时惰性清理） */
    private String content;

    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
