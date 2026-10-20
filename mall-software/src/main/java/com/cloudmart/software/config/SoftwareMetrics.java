package com.cloudmart.software.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.async.mapper.OutboxEventMapper;
import com.cloudmart.software.entity.SoftwareFanoutTask;
import com.cloudmart.software.entity.SoftwareLinkFeedback;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.LinkFeedbackStatus;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.repository.SoftwareFanoutTaskMapper;
import com.cloudmart.software.repository.SoftwareLinkFeedbackMapper;
import com.cloudmart.software.repository.SoftwareReviewCheckTaskMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 软件模块业务指标（手册 13/16：审核积压、resolve 拒绝原因、反馈积压、
 * Outbox 死信、广播队列/失败/投递耗时——"监控已接入并验证"的落地）。
 *
 * <p>gauge 采用 30s 定时采样（避免每次抓取打库）；计数/计时实时上报。
 * 指标命名遵循 micrometer 习惯（点分，Prometheus 输出为下划线）。</p>
 */
@Slf4j
@Component
public class SoftwareMetrics {

    private final MeterRegistry registry;
    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareLinkFeedbackMapper feedbackMapper;
    private final SoftwareFanoutTaskMapper fanoutTaskMapper;
    private final SoftwareReviewCheckTaskMapper checkTaskMapper;
    private final OutboxEventMapper outboxEventMapper;

    private final AtomicLong reviewPending = new AtomicLong();
    private final AtomicLong reviewChecking = new AtomicLong();
    private final AtomicLong reviewCheckQueued = new AtomicLong();
    private final AtomicLong reviewCheckDeadLetter = new AtomicLong();
    private final AtomicLong reviewCheckingOldestSeconds = new AtomicLong();
    private final AtomicLong feedbackPending = new AtomicLong();
    private final AtomicLong outboxDeadLetter = new AtomicLong();
    private final AtomicLong fanoutQueued = new AtomicLong();
    private final AtomicLong fanoutFailed = new AtomicLong();

    public SoftwareMetrics(MeterRegistry registry,
                           SoftwareRevisionMapper revisionMapper,
                           SoftwareLinkFeedbackMapper feedbackMapper,
                           SoftwareFanoutTaskMapper fanoutTaskMapper,
                           SoftwareReviewCheckTaskMapper checkTaskMapper,
                           OutboxEventMapper outboxEventMapper) {
        this.registry = registry;
        this.revisionMapper = revisionMapper;
        this.feedbackMapper = feedbackMapper;
        this.fanoutTaskMapper = fanoutTaskMapper;
        this.checkTaskMapper = checkTaskMapper;
        this.outboxEventMapper = outboxEventMapper;
        Gauge.builder("software.review.pending", reviewPending, AtomicLong::doubleValue)
                .description("待审快照数（30s 采样）").register(registry);
        Gauge.builder("software.review.checking", reviewChecking, AtomicLong::doubleValue)
                .description("自动检查中的提交数（30s 采样）").register(registry);
        Gauge.builder("software.review.check.queued", reviewCheckQueued, AtomicLong::doubleValue)
                .description("自动检查排队/执行中任务数（30s 采样）").register(registry);
        Gauge.builder("software.review.check.dead.letter", reviewCheckDeadLetter,
                        AtomicLong::doubleValue)
                .description("自动检查死信任务数（30s 采样）").register(registry);
        Gauge.builder("software.review.checking.oldest.seconds", reviewCheckingOldestSeconds,
                        AtomicLong::doubleValue)
                .description("最早进入 CHECKING 的提交等待秒数（30s 采样）").register(registry);
        Gauge.builder("software.feedback.pending", feedbackPending, AtomicLong::doubleValue)
                .description("待处理失效反馈数（OPEN/VERIFYING，30s 采样）").register(registry);
        Gauge.builder("software.outbox.dead.letter", outboxDeadLetter, AtomicLong::doubleValue)
                .description("Outbox 死信事件数（30s 采样）").register(registry);
        Gauge.builder("software.fanout.queued", fanoutQueued, AtomicLong::doubleValue)
                .description("订阅广播排队/进行中任务数（30s 采样）").register(registry);
        Gauge.builder("software.fanout.failed", fanoutFailed, AtomicLong::doubleValue)
                .description("订阅广播失败任务数（30s 采样）").register(registry);
    }

    /** 30s 采样一次（抓取不打库） */
    @Scheduled(fixedDelay = 30_000)
    public void refreshGauges() {
        try {
            reviewPending.set(revisionMapper.selectCount(new LambdaQueryWrapper<SoftwareRevision>()
                    .eq(SoftwareRevision::getReviewStatus, SoftwareReviewStatus.PENDING)));
            reviewChecking.set(revisionMapper.selectCount(new LambdaQueryWrapper<SoftwareRevision>()
                    .eq(SoftwareRevision::getReviewStatus, SoftwareReviewStatus.CHECKING)));
            reviewCheckQueued.set(checkTaskMapper.countQueued());
            reviewCheckDeadLetter.set(checkTaskMapper.countDeadLetter());
            reviewCheckingOldestSeconds.set(checkTaskMapper.oldestCheckingSeconds(
                    java.time.LocalDateTime.now()));
            feedbackPending.set(feedbackMapper.selectCount(
                    new LambdaQueryWrapper<SoftwareLinkFeedback>()
                            .in(SoftwareLinkFeedback::getStatus,
                                    LinkFeedbackStatus.OPEN, LinkFeedbackStatus.VERIFYING)));
            outboxDeadLetter.set(outboxEventMapper.countDeadLetters());
            fanoutQueued.set(fanoutTaskMapper.selectCount(
                    new LambdaQueryWrapper<SoftwareFanoutTask>()
                            .in(SoftwareFanoutTask::getStatus, "PENDING", "RUNNING")));
            fanoutFailed.set(fanoutTaskMapper.selectCount(
                    new LambdaQueryWrapper<SoftwareFanoutTask>()
                            .eq(SoftwareFanoutTask::getStatus, "FAILED")));
        } catch (Exception e) {
            log.warn("软件指标采样失败（不影响业务）: {}", e.getMessage());
        }
    }

    /** resolve 被拒计数（按错误码分维度） */
    public void resolveRejected(String code) {
        Counter.builder("software.resolve.rejected")
                .tag("reason", code == null ? "UNKNOWN" : code)
                .description("获取链接被拒次数（按错误码）")
                .register(registry)
                .increment();
    }

    /** 订阅广播投递耗时与结果（deliver 一次 = 一次记录） */
    public void recordFanoutDelivery(boolean sent, long millis) {
        Timer.builder("software.fanout.delivery")
                .description("订阅广播投递耗时（mall-notification 内部调用）")
                .register(registry)
                .record(millis, TimeUnit.MILLISECONDS);
        if (!sent) {
            Counter.builder("software.fanout.delivery.failure")
                    .description("订阅广播投递失败次数")
                    .register(registry)
                    .increment();
        }
    }
}
