package com.cloudmart.software.scheduler;

import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.service.SoftwareReleaseFanoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订阅广播调度（方案 9.4，P1）：推进 fanout 任务游标与投递重试；
 * 模块关闭（software.enabled=false）时不产生任何通知副作用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftwareSubscriptionFanoutScheduler {

    private final SoftwareReleaseFanoutService fanoutService;
    private final SoftwareProperties softwareProperties;

    @Scheduled(fixedDelay = 30_000)
    public void run() {
        // 总开关关闭（模块不可用）或 P1 订阅开关关闭时不产生任何通知副作用
        if (!softwareProperties.isEnabled() || !softwareProperties.isSubscriptionEnabled()) {
            return;
        }
        fanoutService.runOnce();
    }
}
