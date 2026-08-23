package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.CapsuleStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 时间胶囊实体（文档表⑦，Sprint 2.4）。
 *
 * <p>时区策略（文档 26.3）：openAt/openedAt 统一 UTC，到期判定直接比较
 * UTC openAt；openAtTimezone 仅记录创建时用户 IANA 时区，用于回溯展示
 * 与审计，不参与到期判定。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("time_capsule")
public class TimeCapsule {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 胶囊标题（≤100 字） */
    private String title;

    /** 胶囊内容（≤5000 字，未开启不返回） */
    private String content;

    /** 封存媒体 URL 列表（JSON） */
    private String mediaUrls;

    /** 预定开启时间（UTC，到期判定唯一依据） */
    private LocalDateTime openAt;

    /** 创建时用户 IANA 时区（仅回溯展示/审计） */
    private String openAtTimezone;

    /** 实际开启时间（UTC，未开启为 null） */
    private LocalDateTime openedAt;

    /** 状态机：SEALED/AVAILABLE/OPENED/CANCELLED */
    private CapsuleStatus status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
