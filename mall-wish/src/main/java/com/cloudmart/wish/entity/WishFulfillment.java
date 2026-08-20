package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.AuditStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 还愿记录实体（与 wish 1:1，文档表⑨）。
 *
 * <p>软删语义：作者撤回还愿故事时仅置 deleted_at，保留审计轨迹；
 * 心愿本身已 FULFILLED 的状态与星光奖励不回滚（历史事实）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_fulfillment")
public class WishFulfillment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long wishId;

    /** 还愿用户 ID（即心愿作者） */
    private Long userId;

    /** 还愿故事（已 XSS 转义，≤5000 字） */
    private String story;

    /** 完成照片/视频 URL 列表（JSON） */
    private String mediaUrls;

    /** 感悟（≤1000 字，API 契约字段） */
    private String feeling;

    /** 审核状态（先发后审：提交即生效，PENDING 仅供管理端待审筛选） */
    private AuditStatus auditStatus;

    /** 是否可见（与 audit_status 解耦，便于先发后审） */
    private Boolean isVisible;

    /** 是否已传承推送（Sprint 2.7 愿望传承，当前恒 false） */
    private Boolean isInherited;

    @TableLogic
    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
