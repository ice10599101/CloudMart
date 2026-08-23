package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.NotificationChannel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户通知偏好矩阵（文档 1.2 节 ⑰，Sprint 2.5）。
 *
 * <p>用户 × 通知类型 × 渠道的开关记录；<b>无记录视为默认开启</b>。
 * 一键关闭所有提醒 = 对提醒类类型全渠道写入 enabled=0。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_notification_preference")
public class WishNotificationPreference {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 通知类型（13 类，见 WishNotificationType；DB 存 VARCHAR 由业务层校验） */
    private String notificationType;

    private NotificationChannel channel;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
