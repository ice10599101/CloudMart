package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.entity.WishAiConversation;
import com.cloudmart.wish.enums.AiConversationRole;
import com.cloudmart.wish.enums.AiPromptScene;
import com.cloudmart.wish.enums.AiScene;
import com.cloudmart.wish.repository.WishAiConversationMapper;
import com.cloudmart.wish.service.AiConfigService;
import com.cloudmart.wish.service.AiPromptService;
import com.cloudmart.wish.service.AssistantAiClient;
import com.cloudmart.wish.service.ConsentService;
import com.cloudmart.wish.vo.AnnualReportVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 年度报告 AI 生成器（Sprint 2.5，异步任务）。
 *
 * <p>职责：DashScope 生成 growthSummary → 写对话记录
 * （scene=ANNUAL_REPORT）→ 完整报告写 Redis 缓存。失败清任务锁，
 * 用户下次请求自动重试（可重试）；结果不持久化 DB。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnnualReportGenerator {

    /** 结果缓存 Key：wish:annual_report:user:{userId}:{year}（AI 完整版） */
    static final String CACHE_KEY_PREFIX = "wish:annual_report:user:";

    /** 任务锁 Key：生成中标记（SETNX，防重复提交） */
    static final String LOCK_KEY_PREFIX = "wish:annual_report:lock:user:";

    /** 任务锁 TTL：防进程崩溃后锁死（AI 15s 超时 × 3 次重试足够） */
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    /** 会话 ID 前缀：report-{userId}-{year}，一年一个会话 */
    private static final String SESSION_PREFIX = "report-";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AssistantAiClient assistantAiClient;
    private final AiPromptService aiPromptService;
    private final ConsentService consentService;
    private final AiConfigService aiConfigService;
    private final AiPrivacySanitizer privacySanitizer;
    private final WishAiConversationMapper conversationMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 尝试获取生成任务锁（幂等：同一用户同一年至多一个进行中任务）。
     *
     * @return true=抢锁成功，应执行生成；false=已有任务进行中
     */
    public boolean tryLock(Long userId, int year) {
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    LOCK_KEY_PREFIX + userId + ":" + year, "1", LOCK_TTL);
            return Boolean.TRUE.equals(locked);
        } catch (Exception ex) {
            // Redis 异常 Fail-Open：放行生成（重复生成只浪费成本，结果幂等覆盖）
            log.warn("年度报告任务锁不可用，降级放行（Fail-Open）, userId={}, year={}", userId, year);
            return true;
        }
    }

    /**
     * 异步生成 growthSummary 并缓存完整报告。
     *
     * <p>未同意 AI 数据处理协议：不调 DashScope（统计数字无 PII，
     * 但 growth 记录文案可能含个人信息），直接缓存模板版。</p>
     */
    @Async("annualReportExecutor")
    public void generateAsync(Long userId, int year, AnnualReportVO report) {
        try {
            String growthSummary;
            if (consentService.hasGrantedAiDataProcessing(userId)) {
                growthSummary = generateByAi(userId, year, report);
            } else {
                growthSummary = report.growthSummary();
                log.info("用户未同意AI协议，年度报告使用模板文案, userId={}, year={}", userId, year);
            }
            AnnualReportVO fullReport = new AnnualReportVO(report.year(), report.fulfilledCount(),
                    report.totalCheckinDays(), growthSummary, report.milestones(), report.topCategories());
            cacheReport(userId, year, fullReport);
        } catch (Exception ex) {
            // 失败清锁：用户下次请求自动重试（文档：可重试）
            log.warn("年度报告AI生成失败，清除任务锁待重试, userId={}, year={}", userId, year, ex);
            releaseLock(userId, year);
        }
    }

    /**
     * 读取缓存的 AI 完整版报告；未命中/解析失败返回 null。
     */
    public AnnualReportVO readCache(Long userId, int year) {
        try {
            String json = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + userId + ":" + year);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, AnnualReportVO.class);
        } catch (Exception ex) {
            log.warn("年度报告缓存读取失败，回退同步聚合, userId={}, year={}", userId, year, ex);
            return null;
        }
    }

    private String generateByAi(Long userId, int year, AnnualReportVO report) {
        String systemPrompt = aiPromptService.getActivePrompt(AiPromptScene.ANNUAL_REPORT, userId);
        String userContext = buildUserContext(report);
        String summary = assistantAiClient.generateText(systemPrompt, userContext);
        persistConversation(userId, year, userContext, summary);
        return summary;
    }

    /**
     * 构建发送给 DashScope 的年度数据摘要（统计数字 + 脱敏后的成长记录）。
     */
    private String buildUserContext(AnnualReportVO report) {
        StringBuilder context = new StringBuilder(256);
        context.append("年度数据摘要（").append(report.year()).append("年）：")
                .append("实现心愿 ").append(report.fulfilledCount()).append(" 个，")
                .append("打卡 ").append(report.totalCheckinDays()).append(" 天。");
        if (!report.milestones().isEmpty()) {
            context.append("成长里程碑：");
            report.milestones().forEach(m -> context.append('「')
                    .append(privacySanitizer.sanitize(m.title())).append('」'));
        }
        if (!report.topCategories().isEmpty()) {
            context.append("热门分类：");
            report.topCategories().forEach(c -> context.append(c.name())
                    .append('(').append(c.count()).append(") "));
        }
        return context.toString();
    }

    /**
     * AI 生成成功后写入对话记录（USER=数据摘要，ASSISTANT=growthSummary，
     * scene=ANNUAL_REPORT，文档 2.11 注）。
     */
    private void persistConversation(Long userId, int year, String userContext, String summary) {
        String sessionId = SESSION_PREFIX + userId + "-" + year;
        transactionTemplate.executeWithoutResult(status -> {
            WishAiConversation userRecord = new WishAiConversation();
            userRecord.setUserId(userId);
            userRecord.setSessionId(sessionId);
            userRecord.setScene(AiScene.ANNUAL_REPORT);
            userRecord.setRole(AiConversationRole.USER);
            userRecord.setContent(userContext);
            conversationMapper.insert(userRecord);

            WishAiConversation assistantRecord = new WishAiConversation();
            assistantRecord.setUserId(userId);
            assistantRecord.setSessionId(sessionId);
            assistantRecord.setScene(AiScene.ANNUAL_REPORT);
            assistantRecord.setRole(AiConversationRole.ASSISTANT);
            assistantRecord.setContent(summary);
            conversationMapper.insert(assistantRecord);
        });
    }

    private void cacheReport(Long userId, int year, AnnualReportVO report) {
        try {
            int ttlHours = aiConfigService.getIntConfig(
                    AiConfigService.KEY_ANNUAL_REPORT_TTL_HOURS, 168);
            String json = objectMapper.writeValueAsString(report);
            redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + userId + ":" + year,
                    json, ttlHours, TimeUnit.HOURS);
            log.info("年度报告生成完成并缓存, userId={}, year={}, ttlHours={}", userId, year, ttlHours);
        } catch (Exception ex) {
            // 缓存失败不影响报告可用性：下次请求重新聚合触发生成
            log.warn("年度报告缓存写入失败, userId={}, year={}", userId, year, ex);
        }
    }

    private void releaseLock(Long userId, int year) {
        try {
            redisTemplate.delete(LOCK_KEY_PREFIX + userId + ":" + year);
        } catch (Exception ex) {
            log.warn("年度报告任务锁释放失败（等待TTL自动过期）, userId={}, year={}", userId, year, ex);
        }
    }
}
