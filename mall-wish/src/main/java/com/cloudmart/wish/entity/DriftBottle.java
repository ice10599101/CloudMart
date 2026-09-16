package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.DriftBottleStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 漂流瓶（替代相遇信笺用户侧体验）：投瓶人匿名投出，捞起者随机捞取。
 *
 * <p>content 与 wishId 二选一（自由文字 / 关联愿望）；wishTitle/wishTags
 * 为关联心愿快照，避免列表 N+1；thrower/picker 字段服务端内部使用，VO 层不
 * 外露投瓶人身份（匿名验收）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_drift_bottle")
public class DriftBottle {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 投瓶人用户 ID（对捞起者匿名） */
    private Long throwerUserId;

    /** 自由匿名文字（与 wishId 二选一） */
    private String content;

    /** 关联心愿 ID（与 content 二选一） */
    private Long wishId;

    /** 关联心愿标题快照 */
    private String wishTitle;

    /** 关联心愿标签快照（JSON 数组） */
    private String wishTags;

    /** 状态机 */
    private DriftBottleStatus status;

    /** 捞起人用户 ID */
    private Long pickerUserId;

    /** 投瓶时间（UTC） */
    private LocalDateTime thrownAt;

    /** 捞瓶时间（UTC） */
    private LocalDateTime pickedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}