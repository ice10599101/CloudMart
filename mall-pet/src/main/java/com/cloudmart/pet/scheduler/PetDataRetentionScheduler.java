package com.cloudmart.pet.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.repository.PetOutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * B22 数据保留策略：SENT outbox 事件保留 30 天后清理（审计依赖 pet_operation 不清理）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PetDataRetentionScheduler {

    private final PetOutboxEventMapper outboxMapper;

    @Scheduled(cron = "0 0 4 * * *")
    public void purgeSentOutbox() {
        int deleted = outboxMapper.delete(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetOutboxEvent>()
                .eq(com.cloudmart.pet.entity.PetOutboxEvent::getStatus, "SENT")
                .lt(com.cloudmart.pet.entity.PetOutboxEvent::getUpdatedAt,
                        LocalDateTime.now(ZoneOffset.UTC).minusDays(30)));
        if (deleted > 0) {
            log.info("outbox 已发送事件清理: {} rows", deleted);
        }
    }
}
