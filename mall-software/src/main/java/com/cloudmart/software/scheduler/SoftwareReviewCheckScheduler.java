package com.cloudmart.software.scheduler;

import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.service.SoftwareReviewCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SW04：提交自动检查调度。每轮最多处理 10 个到期任务，租约与 fencing 保证
 * 多实例下同一提交只有一个执行者；模块总开关关闭时不推进任何检查。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftwareReviewCheckScheduler {

    private static final int MAX_TASKS_PER_TICK = 10;

    private final SoftwareReviewCheckService checkService;
    private final SoftwareProperties softwareProperties;

    @Scheduled(fixedDelay = 15_000)
    public void run() {
        if (!softwareProperties.isEnabled()) {
            return;
        }
        for (int i = 0; i < MAX_TASKS_PER_TICK; i++) {
            if (!checkService.runOnce()) {
                return;
            }
        }
    }
}
