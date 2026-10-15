package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.software.enums.SoftwareCommentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 软件评论（方案 6.8）：两层展示（根评论+楼中楼）；parent_id 恒指向根楼；
 * replyToUserId 由服务端从目标评论推导；软删保留占位且不再回传原 content。
 */
@Getter
@Setter
@TableName("software_comment")
public class SoftwareComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private Long userId;

    /** 根评论 ID（NULL=根楼） */
    private Long parentId;

    /** 精确回复目标（同软件同根楼内） */
    private Long replyToCommentId;

    private Long replyToUserId;

    /** 评论正文（纯文本+表情，1~1000 字） */
    private String content;

    private SoftwareCommentStatus status;

    private Integer likeCount;

    /** 作者置顶（P1：每软件最多一个可见根评论） */
    private Boolean pinned;

    private String reviewReason;

    private LocalDateTime deletedAt;

    private Integer lockVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
