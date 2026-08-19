package com.cloudmart.wish.mq;

import com.cloudmart.wish.config.RocketMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;

/**
 * 心愿统计事件生产者（文档 6.5 节：total_helped 通过 MQ 异步解耦，避免互动接口阻塞）。
 *
 * <p>发送失败不阻断主流程（Fail-Open）：统计偏差由每日对账任务修正。
 * 消息在事务提交后发送（调用方保证），避免事务回滚后统计多加。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WishStatEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 点亮/匿名星光后异步累计 total_helped（累计帮助他人次数）。
     *
     * @param userId 点亮者用户 ID
     */
    public void publishHelpedEvent(Long userId) {
        HelpedEventMessage message = new HelpedEventMessage(userId);
        try {
            String destination = RocketMQConfig.WISH_TOPIC + ":" + RocketMQConfig.WISH_TAG_STAT_SYNC;
            rocketMQTemplate.syncSend(destination, message);
            log.debug("帮助统计事件已发送, userId={}", userId);
        } catch (Exception e) {
            log.error("帮助统计事件发送失败（对账任务兜底）, userId={}", userId, e);
        }
    }

    /**
     * 帮助统计事件消息。
     *
     * @param userId 点亮者用户 ID
     */
    public record HelpedEventMessage(Long userId) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
