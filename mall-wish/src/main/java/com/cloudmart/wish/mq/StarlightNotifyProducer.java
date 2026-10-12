package com.cloudmart.wish.mq;

import com.cloudmart.wish.config.RocketMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;

/**
 * R19 星光到账通知生产者（转赠接收方 + 邀请奖励）。
 *
 * <p>消息信封与 {@code MatchEventProducer.MatchNotifyMessage} 一致，由
 * mall-notification 的 AiReminderEventConsumer 统一消费落站内信
 * （selectorExpression 覆盖 starlight-transfer / invite-reward 两 tag）。</p>
 *
 * <p>必须在业务事务提交后调用（afterCommit）——回滚不允许发出到账通知。
 * 发送失败 Fail-Open（资产事实已落库为权威，通知仅展示副作用，可由流水页自行核对）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StarlightNotifyProducer {

    public static final String NOTIFY_TYPE_TRANSFER = "STARLIGHT_RECEIVED";
    public static final String NOTIFY_TYPE_INVITE = "INVITE_REWARD";
    public static final String BIZ_TYPE_TRANSFER = "STARLIGHT_TRANSFER";
    public static final String BIZ_TYPE_INVITE = "INVITE_REWARD";

    private final RocketMQTemplate rocketMQTemplate;

    /** 转赠到账通知（接收方；bizId=转赠单 ID，供通知深链跳转转赠记录）。 */
    public void publishTransferReceived(Long receiverUserId, Long transferId, int amount, String message) {
        String content = "收到好友转赠的 " + amount + " 星光"
                + (message == null || message.isBlank() ? "" : "：" + message);
        publish(RocketMQConfig.WISH_TAG_STARLIGHT_TRANSFER,
                new StarlightNotifyMessage(NOTIFY_TYPE_TRANSFER, receiverUserId,
                        "收到一笔星光转赠", content, transferId, BIZ_TYPE_TRANSFER));
    }

    /** 邀请奖励到账通知（bizId=奖励流水关联业务 ID）。 */
    public void publishInviteReward(Long userId, Long refId, int amount) {
        publish(RocketMQConfig.WISH_TAG_INVITE_REWARD,
                new StarlightNotifyMessage(NOTIFY_TYPE_INVITE, userId,
                        "邀请奖励已到账",
                        "邀请好友奖励 " + amount + " 星光已发放，可在邀请中心查看明细",
                        refId, BIZ_TYPE_INVITE));
    }

    private void publish(String tag, StarlightNotifyMessage message) {
        try {
            rocketMQTemplate.syncSend(RocketMQConfig.WISH_TOPIC + ":" + tag, message);
            log.debug("星光到账通知已发送, tag={}, userId={}", tag, message.userId());
        } catch (Exception e) {
            log.error("星光到账通知发送失败（Fail-Open，不阻断资产事实）, tag={}, userId={}",
                    tag, message.userId(), e);
        }
    }

    /** 通知消息（mall-notification AiReminderEventConsumer 消费，字段对齐）。 */
    public record StarlightNotifyMessage(String notifyType, Long userId, String title,
                                         String content, Long bizId, String bizType) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
