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

    @Override
    public void onMessage(WishStatEventProducer.HelpedEventMessage message) {
        Long userId = message.userId();
        if (userId == null) {
            log.warn("帮助统计事件缺少 userId，跳过: {}", message);
            return;
        }
        userStatService.incrementTotalHelped(userId);
        log.debug("帮助统计已累加, userId={}", userId);
    }
}
