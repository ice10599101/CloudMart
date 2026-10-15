package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 评论点赞关系（方案 6.8）：唯一 (comment_id, user_id)，幂等 PUT/DELETE，物理删除 */
@Getter
@Setter
@TableName("software_comment_like")
public class SoftwareCommentLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long commentId;

    private Long userId;

    private LocalDateTime createdAt;
}
