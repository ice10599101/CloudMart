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
    /** 导出内容（仅下载端点经 loadContent 输出；状态视图脱敏，B19） */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String content;

    /** 加密导出内容（W04：AES-GCM，AAD 绑定 taskId；明文不落库） */
    private String contentEnc;

    /** 密文 SHA-256（下载时完整性校验） */
    private String contentSha256;

    /** 租约持有者（实例+线程；接管时轮换） */
    private String leaseOwner;

    /** 租约到期（过期可被其他实例接管） */
    private LocalDateTime leaseUntil;

    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
