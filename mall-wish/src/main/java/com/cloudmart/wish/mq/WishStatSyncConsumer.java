package com.cloudmart.wish.mq;

import com.cloudmart.wish.config.RocketMQConfig;
import com.cloudmart.wish.service.UserStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 统计同步事件消费者（文档 6.5：total_helped 异步累加，避免互动接口阻塞）。
 *
 * <p>消费语义：</p>
 * <ul>
 *   <li>幂等风险：RocketMQ 至少一次投递可能重复消费，total_helped 属可容忍的统计口径
 *       （误差由每日对账任务修正）；核心资金字段（星光）不走 MQ，无重复消费风险</li>
 *   <li>消费失败：抛出异常触发 Broker 重试，重试耗尽进入 DLQ（%DLQ%消费者组）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.WISH_TOPIC,
        consumerGroup = RocketMQConfig.CG_WISH_STAT_SYNC,
        selectorExpression = RocketMQConfig.WISH_TAG_STAT_SYNC
)
public class WishStatSyncConsumer implements RocketMQListener<WishStatEventProducer.HelpedEventMessage> {

    private final UserStatService userStatService;
    private final com.cloudmart.wish.service.impl.WishEventInboxService inboxService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Override
    public void onMessage(WishStatEventProducer.HelpedEventMessage message) {
        Long userId = message.userId();
        if (userId == null) {
            log.warn("帮助统计事件缺少 userId，跳过: {}", message);
            return;
        }
        // B13：eventId 去重行与统计累加同事务——重复投递只累加一次；
        // 去重行随副作用回滚，消息可安全重投
        transactionTemplate.execute(status -> {
            if (!inboxService.tryConsume("wish-stat-sync-helped", message.eventId())) {
                log.info("帮助统计事件重复投递，跳过 eventId={}", message.eventId());
                return null;
            }
            userStatService.incrementTotalHelped(userId);
            return null;
        });
        log.debug("帮助统计已处理, userId={}, eventId={}", userId, message.eventId());
    }
}
