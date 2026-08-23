package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.GoalStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 拆解目标（文档 1.2 节 ㊱b，Sprint 2.5）。
 *
 * <p>用户在 AI 助手拆解结果中勾选的步骤持久化（初始 PENDING）；
 * 状态流转 PENDING → IN_PROGRESS → COMPLETED，非终态可 CANCELLED。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_ai_goal")
public class WishAiGoal {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 关联心愿 ID（可空：允许无心愿的自由目标拆解） */
    private Long wishId;

    /** 步骤标题（≤100 字） */
    private String title;

    /** 步骤描述 */
    private String description;

    /** 预计完成天数 */
    private Integer estimatedDays;

    /** 优先级（1-5，1 最高） */
    private Integer priority;

    private GoalStatus status;

    /** AI 会话 ID（关联 wish_ai_conversation.session_id） */
    private String aiSessionId;

    /** 开始时间（UTC，首次流转 IN_PROGRESS 时写入） */
    private LocalDateTime startedAt;

    /** 完成时间（UTC，流转 COMPLETED 时写入） */
    private LocalDateTime completedAt;

    @TableLogic
    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
