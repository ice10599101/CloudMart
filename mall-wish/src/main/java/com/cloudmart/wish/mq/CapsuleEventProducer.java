package com.cloudmart.wish.mq;

import com.cloudmart.wish.config.RocketMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;

/**
 * 时间胶囊事件生产者（Sprint 2.4，文档 9.2 到期扫描推送）。
 *
 * <p>仅当 SEALED→AVAILABLE CAS 流转成功后发送（每胶囊至多一次，
 * 满足"同一胶囊扫描 2 次仅推送 1 次"幂等要求）；发送失败 Fail-Open
 * 记日志不阻断扫描（推送缺口可由管理端推送记录核对，文档允许降级）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapsuleEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发布胶囊到期待开启事件（mall-notification WishEventConsumer 消费）。
     *
     * @param capsuleId 胶囊 ID
     * @param userId    归属用户 ID
     * @param title     胶囊标题
     */
    public void publishCapsuleAvailable(Long capsuleId, Long userId, String title) {
        CapsuleAvailableMessage message = new CapsuleAvailableMessage(capsuleId, userId, title);
        try {
            String destination = RocketMQConfig.WISH_TOPIC + ":" + RocketMQConfig.WISH_TAG_CAPSULE_AVAILABLE;
            rocketMQTemplate.syncSend(destination, message);
            log.debug("胶囊到期事件已发送, capsuleId={}, userId={}", capsuleId, userId);
        } catch (Exception e) {
            log.error("胶囊到期事件发送失败（Fail-Open，不阻断扫描）, capsuleId={}", capsuleId, e);
        }
    }

    /** 胶囊到期待开启事件消息。 */
    public record CapsuleAvailableMessage(Long capsuleId, Long userId, String title) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
