package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 订阅更新已读台账（UX05）：阅读状态只影响未读展示，不影响投递事实。 */
@Data
@TableName("software_update_read")
public class SoftwareUpdateRead {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 更新事件（software_subscription_delivery.id） */
    private Long deliveryId;

    private LocalDateTime readAt;
}
