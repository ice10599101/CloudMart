package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 事务性发件箱（B13，任务书 §6.3）。
 *
 * <p>不变量：业务事实与事件行原子提交；eventId 重试不变化；
 * 幂等正文不进通用事件总线（payload 只含 ID/版本/展示字段）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_outbox")
public class WishOutboxEvent {

    @TableId(type = IdType.INPUT)
    private String eventId;

    private String aggregateType;

    private Long aggregateId;

    private Long aggregateVersion;

    private String eventType;

    private String payload;

    /** PENDING / PUBLISHED / DEAD */
    private String status;

    private Integer attempts;

    private LocalDateTime nextAttemptAt;

    private String leaseOwner;

    private LocalDateTime leaseUntil;

    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;
}
