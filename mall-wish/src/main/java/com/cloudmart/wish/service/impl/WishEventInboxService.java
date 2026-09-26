package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.entity.WishEventInbox;
import com.cloudmart.wish.repository.WishEventInboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 消费端事件去重（B13）：消费者以 (consumerName, eventId) 与本地副作用同事务写入去重行。
 * 必须在调用方事务内使用——副作用失败回滚时去重行一并回滚，消息可安全重投。
 */
@Service
@RequiredArgsConstructor
public class WishEventInboxService {

    private final WishEventInboxMapper inboxMapper;

    /**
     * @return true=首次处理（调用方应执行本地副作用）；false=重复投递（跳过）
     */
    public boolean tryConsume(String consumerName, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            // 旧格式消息（无 eventId）：无法去重，放行处理（部署过渡期语义）
            return true;
        }
        WishEventInbox row = new WishEventInbox();
        row.setConsumerName(consumerName);
        row.setEventId(eventId);
        row.setProcessedAt(LocalDateTime.now(ZoneId.of("UTC")));
        try {
            inboxMapper.insert(row);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }
}
