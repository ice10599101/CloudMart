package com.cloudmart.software.scheduler;

import com.cloudmart.common.async.compensation.CompensationTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 软件域补偿调度（ASYNC-01）：素材引用重绑/解绑等 compensation_task 的到期任务重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftwareCompensationScheduler {

    private final CompensationTaskService compensationTaskService;

    @Scheduled(fixedDelay = 60_000)
    public void runDueCompensations() {
        compensationTaskService.runDueTasks();
    }
}
