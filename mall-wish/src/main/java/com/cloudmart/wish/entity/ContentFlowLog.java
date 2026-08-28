package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.ContentFlowStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 还愿内容流转日志（Sprint 2.7，wish → community 帖子）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_content_flow_log")
public class ContentFlowLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 心愿 ID */
    private Long wishId;

    /** 还愿记录 ID */
    private Long fulfillmentId;

    /** community 帖子 ID（成功后回填） */
    private Long postId;

    /** 流转状态 */
    private ContentFlowStatus status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最近一次失败原因 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
