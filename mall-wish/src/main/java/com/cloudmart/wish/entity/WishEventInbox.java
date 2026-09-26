package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 消费端事件去重行（B13）：与本地副作用同事务写入——事务回滚时去重行一并回滚，
 * 消息可安全重投；重复投递时唯一键冲突即跳过。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_event_inbox")
public class WishEventInbox {

    @TableId(type = IdType.INPUT)
    private String consumerName;

    private String eventId;

    private LocalDateTime processedAt;
}
