package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 软件更新订阅（方案 4.5，P1）：与收藏分开，仅接收新增版本通知；
 * id 同时作为 fanout 游标键；无 updated_at（订阅为一次性事实）。
 */
@Getter
@Setter
@TableName("software_subscription")
public class SoftwareSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private Long userId;

    /** SF05：订阅平台偏好（逗号分隔；NULL=全部平台） */
    private String platforms;

    /** SF05：订阅渠道偏好（逗号分隔；NULL=全部渠道） */
    private String channels;

    private LocalDateTime createdAt;
}
