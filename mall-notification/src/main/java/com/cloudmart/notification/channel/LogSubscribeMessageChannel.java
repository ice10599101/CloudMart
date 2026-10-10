package com.cloudmart.notification.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * N-1 log 通道（默认）：打印订阅消息意图，便于联调验证事件管线；
 * 生产接入微信订阅消息时配置 provider=wechat 并实现 WeChatSubscribeMessageChannel。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.subscribe-message.provider", havingValue = "log", matchIfMissing = true)
public class LogSubscribeMessageChannel implements SubscribeMessageChannel {

    @Value("${notification.subscribe-message.templates:}")
    private Map<String, String> templates;

    @Override
    public boolean send(Long userId, String templateKey, Map<String, String> data) {
        log.info("[N-1 订阅消息·log 通道] userId={}, templateKey={}, platformTemplate={}, data={}",
                userId, templateKey, templates.get(templateKey), data);
        return true;
    }
}
