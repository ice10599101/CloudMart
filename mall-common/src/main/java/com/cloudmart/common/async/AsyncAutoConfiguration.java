package com.cloudmart.common.async;

import com.cloudmart.common.async.compensation.CompensationHandler;
import com.cloudmart.common.async.mapper.CompensationTaskMapper;
import com.cloudmart.common.async.compensation.CompensationTaskService;
import com.cloudmart.common.async.mapper.InboxRecordMapper;
import com.cloudmart.common.async.inbox.InboxService;
import com.cloudmart.common.async.outbox.OutboxDelivery;
import com.cloudmart.common.async.mapper.OutboxEventMapper;
import com.cloudmart.common.async.outbox.OutboxPublisher;
import com.cloudmart.common.async.outbox.OutboxRetryPolicy;
import com.cloudmart.common.async.outbox.OutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * 交易异步可靠性自动装配（ASYNC-01）：Outbox / Inbox / 补偿任务。
 *
 * <p>装配条件：classpath 存在 MyBatis-Plus（接入模块自带）且
 * {@code cloudmart.async.enabled=true}。接入模块还需：</p>
 * <ol>
 *   <li>{@code @MapperScan} 加入 {@code com.cloudmart.common.async} 扫描（仅 Mapper 接口所在子包）；</li>
 *   <li>提供 {@link OutboxDelivery} Bean（发往本模块 MQ 目的地）；</li>
 *   <li>建表迁移（outbox_event / inbox_record / compensation_task）。</li>
 * </ol>
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
@ConditionalOnProperty(name = "cloudmart.async.enabled", havingValue = "true")
@EnableScheduling
public class AsyncAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxRetryPolicy outboxRetryPolicy(
            @Value("${cloudmart.async.outbox.max-attempts:12}") int maxAttempts,
            @Value("${cloudmart.async.outbox.base-backoff-ms:1000}") long baseBackoffMillis,
            @Value("${cloudmart.async.outbox.max-backoff-ms:300000}") long maxBackoffMillis) {
        return new OutboxRetryPolicy(maxAttempts, baseBackoffMillis, maxBackoffMillis);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxService outboxService(OutboxEventMapper mapper) {
        return new OutboxService(mapper);
    }

    @Bean
    @ConditionalOnBean(OutboxDelivery.class)
    public OutboxPublisher outboxPublisher(OutboxEventMapper mapper, OutboxDelivery delivery,
                                           OutboxRetryPolicy retryPolicy,
                                           @Value("${cloudmart.async.outbox.batch-size:50}") int batchSize,
                                           @Value("${cloudmart.async.outbox.lease-seconds:60}") int leaseSeconds) {
        return new OutboxPublisher(mapper, delivery, retryPolicy, batchSize, leaseSeconds);
    }

    /** T16 异常处理中心：仅真正接入 outbox 投递的模块装配（OutboxDelivery 为各域必供 bean，
     *  mall-admin 等无 outbox 的模块自动跳过）。mapper 缺失由惰性解析兜底。 */
    @Bean
    @ConditionalOnBean(OutboxDelivery.class)
    public com.cloudmart.common.async.outbox.OutboxOperationsService outboxOperationsService(
            org.springframework.beans.factory.ObjectProvider<OutboxEventMapper> mapper) {
        return new com.cloudmart.common.async.outbox.OutboxOperationsService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public InboxService inboxService(InboxRecordMapper mapper,
                                     @Value("${cloudmart.async.inbox.lease-seconds:300}") int leaseSeconds) {
        return new InboxService(mapper, leaseSeconds);
    }

    @Bean
    @ConditionalOnMissingBean
    public CompensationTaskService compensationTaskService(CompensationTaskMapper mapper,
                                                           List<CompensationHandler> handlers,
                                                           OutboxRetryPolicy retryPolicy,
                                                           @Value("${cloudmart.async.compensation.batch-size:50}") int batchSize,
                                                           @Value("${cloudmart.async.compensation.lease-seconds:60}") int leaseSeconds) {
        return new CompensationTaskService(mapper, handlers, retryPolicy, batchSize, leaseSeconds);
    }
}
