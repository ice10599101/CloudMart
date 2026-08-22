package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.SpecialEventStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 全站特殊事件实体（Sprint 2.2，文档第二章 2. 特殊事件 / V10 迁移）。
 *
 * <p>管理员触发"流星雨/极光/星辰夜"等全站同步环境视觉事件；单活跃
 * 事件语义由应用层保证（触发新事件自动结束旧事件）。{@code expiresAt}
 * 为 NULL 表示持续至手动结束；过期未标记的行读取方惰性判定视同 ENDED。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_special_event")
public class WishSpecialEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 事件代码（关联 wish_env_config.env_code，如 METEOR_SHOWER） */
    private String eventCode;

    private String title;

    private String description;

    private SpecialEventStatus status;

    /** 触发管理员用户 ID（审计） */
    private Long triggeredBy;

    /** 触发时间（UTC，全站同步展示起点） */
    private LocalDateTime triggeredAt;

    /** 过期时间（UTC；NULL=持续至手动结束） */
    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
