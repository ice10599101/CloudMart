package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 软件更新提醒偏好（方案 4.5，P1）：update_notifications 总开关，默认开启 */
@Getter
@Setter
@TableName("software_user_preference")
public class SoftwareUserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Boolean updateNotifications;

    private Integer lockVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
