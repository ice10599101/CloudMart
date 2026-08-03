package com.cloudmart.seckill.mq;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.seckill.config.RocketMQConfig;
import com.cloudmart.seckill.dto.SeckillMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillMQProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public void sendSeckillMessage(SeckillMessage message) {
        try {
            String destination = RocketMQConfig.SECKILL_TOPIC + ":" + RocketMQConfig.SECKILL_TAG_ORDER;
            rocketMQTemplate.syncSend(destination, message);
            log.info("Seckill message sent: userId={}, activityId={}, skuId={}",
                    message.userId(), message.activityId(), message.skuId());
        } catch (Exception e) {
            log.error("Failed to send seckill message to MQ: userId={}, activityId={}, skuId={}",
                    message.userId(), message.activityId(), message.skuId(), e);
            throw new BusinessException("MQ_SEND_FAILED", "秒杀消息发送失败，请重试");
        }
    }
}
