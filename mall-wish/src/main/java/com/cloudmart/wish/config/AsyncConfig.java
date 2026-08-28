package com.cloudmart.wish.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 心愿模块异步任务线程池（Sprint 2.5 年度报告 AI 生成）。
 *
 * <p>年度报告 growthSummary 调用 DashScope 耗时可达 15s，
 * 不能占用 Web 请求线程；独立线程池隔离，队列满时由调用方
 * 降级模板文案（报告必达，AI 文案是增强）。</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 年度报告生成线程池：IO 密集型（DashScope HTTP 调用），
     * 核心线程 2 / 最大 4 / 有界队列 100，拒绝策略 CallerRuns
     * 兜底（提交线程执行，请求侧仍受任务锁幂等保护）。
     */
    @Bean("annualReportExecutor")
    public Executor annualReportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("wish-annual-report-");
        // 队列满时由提交线程执行：单用户幂等锁保证不重复生成
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 内容流转线程池（Sprint 2.7 还愿 → community 帖子）：IO 密集型 Feign
     * 调用 + 失败重试退避，核心 1 / 最大 2 / 有界队列 50，拒绝策略 CallerRuns
     * 兜底（流转为增强功能，不反压还愿主链路——事务已提交）。
     */
    @Bean("contentFlowExecutor")
    public Executor contentFlowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("wish-content-flow-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
