package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.ContentFlowLog;
import com.cloudmart.wish.entity.FulfillmentInherit;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishFulfillment;
import com.cloudmart.wish.entity.WishGrowthRecord;
import com.cloudmart.wish.entity.WishInteraction;
import com.cloudmart.wish.entity.WishProgress;
import com.cloudmart.wish.enums.ContentFlowStatus;
import com.cloudmart.wish.enums.InteractionType;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.feign.CommunityFeignClient;
import com.cloudmart.wish.mq.LegacyEventProducer;
import com.cloudmart.wish.repository.ContentFlowLogMapper;
import com.cloudmart.wish.repository.FulfillmentInheritMapper;
import com.cloudmart.wish.repository.WishFulfillmentMapper;
import com.cloudmart.wish.repository.WishGrowthRecordMapper;
import com.cloudmart.wish.repository.WishInteractionMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishProgressMapper;
import com.cloudmart.wish.service.LegacyFlowService;
import com.cloudmart.wish.util.WishJsonUtils;
import com.cloudmart.wish.vo.InheritResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 还愿传承 + 内容流转服务实现（Sprint 2.7）。
 *
 * <p>传承：作者对 FULFILLED 心愿定向推送曾同求用户（SAME_WISH 且未取消），
 * 通知文案三端一致（"你的同愿实现了"）；一次还愿仅一次传承
 * （uk_inherit_fulfillment 兜底）。</p>
 *
 * <p>内容流转：还愿事务提交后经独立线程池异步执行（不占用请求线程、
 * 不阻断还愿主链路）；community 不可用时指数退避重试 3 次后记 FAILED
 * （管理端可重试），故事撤回时帖子同步隐藏（状态同步规则）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyFlowServiceImpl implements LegacyFlowService {

    /** 图文模板成就标签（文档 2.7：community.post.tags 自动打标） */
    private static final String LEGACY_TAG = "✨ 心愿完成";
    private static final int FLOW_MAX_RETRY = 3;
    private static final long[] FLOW_BACKOFF_MS = {500L, 1000L, 2000L};
    private static final int STORY_SUMMARY_LEN = 60;

    private final WishMapper wishMapper;
    private final WishFulfillmentMapper fulfillmentMapper;
    private final WishInteractionMapper interactionMapper;
    private final WishGrowthRecordMapper growthRecordMapper;
    private final WishProgressMapper progressMapper;
    private final ContentFlowLogMapper flowLogMapper;
    private final FulfillmentInheritMapper inheritMapper;
    private final CommunityFeignClient communityFeignClient;
    private final LegacyEventProducer legacyEventProducer;

    // ---------------- 传承推送 ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InheritResultVO pushInherit(Long userId, Long wishId, String message) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        if (!wish.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_AUTHOR, "仅作者可发起传承");
        }
        if (wish.getStatus() != WishStatus.FULFILLED) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FULFILLED, "心愿还未实现，无法发起传承");
        }

        WishFulfillment fulfillment = fulfillmentMapper.selectOne(new LambdaQueryWrapper<WishFulfillment>()
                .eq(WishFulfillment::getWishId, wishId)
                .isNull(WishFulfillment::getDeletedAt)
                .last("LIMIT 1"));
        if (fulfillment == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FULFILLED, "还愿记录不存在");
        }
        long inheritCount = inheritMapper.selectCount(new LambdaQueryWrapper<FulfillmentInherit>()
                .eq(FulfillmentInherit::getFulfillmentId, fulfillment.getId()));
        if (inheritCount > 0) {
            throw new BusinessException(WishErrorCodes.WISH_ALREADY_INHERITED, "这条心愿已经传承过了");
        }

        // 定向目标：曾同求（SAME_WISH）且未取消的用户（去重、排除作者）
        Set<Long> targets = new HashSet<>(interactionMapper.selectList(new LambdaQueryWrapper<WishInteraction>()
                        .eq(WishInteraction::getWishId, wishId)
                        .eq(WishInteraction::getType, InteractionType.SAME_WISH)
                        .isNull(WishInteraction::getDeletedAt))
                .stream().map(WishInteraction::getUserId).toList());
        targets.remove(userId);

        String summary = storySummary(fulfillment.getStory());
        for (Long target : targets) {
            legacyEventProducer.publishLegacyPush(target, wishId, wish.getTitle(), summary, message);
        }

        com.cloudmart.wish.entity.FulfillmentInherit inherit = new com.cloudmart.wish.entity.FulfillmentInherit();
        inherit.setWishId(wishId);
        inherit.setFulfillmentId(fulfillment.getId());
        inherit.setUserId(userId);
        inherit.setTargetCount(targets.size());
        inherit.setPushedCount(targets.size());
        inherit.setMessage(message);
        try {
            inheritMapper.insert(inherit);
        } catch (DuplicateKeyException ex) {
            // uk_inherit_fulfillment 并发兜底：一次还愿仅一次传承
            throw new BusinessException(WishErrorCodes.WISH_ALREADY_INHERITED, "这条心愿已经传承过了");
        }

        fulfillment.setIsInherited(true);
        fulfillmentMapper.updateById(fulfillment);

        log.info("传承推送完成, wishId={}, targets={}, inheritId={}", wishId, targets.size(), inherit.getId());
        return new InheritResultVO(inherit.getId(), targets.size(),
                LocalDateTime.now(ZoneId.of("UTC")));
    }

    // ---------------- 内容流转 ----------------

    @Override
    @Async("contentFlowExecutor")
    public void submitContentFlow(Long wishId, Long fulfillmentId) {
        ContentFlowLog logRow = new ContentFlowLog();
        logRow.setWishId(wishId);
        logRow.setFulfillmentId(fulfillmentId);
        logRow.setStatus(ContentFlowStatus.FAILED);
        logRow.setRetryCount(0);
        flowLogMapper.insert(logRow);
        attemptFlow(logRow, true);
    }

    @Override
    public void retryFlow(Long logId) {
        ContentFlowLog logRow = flowLogMapper.selectById(logId);
        if (logRow == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "流转日志不存在");
        }
        if (logRow.getStatus() == ContentFlowStatus.SUCCESS) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "该流转已成功，无需重试");
        }
        if (logRow.getStatus() == ContentFlowStatus.HIDDEN) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "还愿故事已撤回，帖子已隐藏");
        }
        attemptFlow(logRow, false);
    }

    @Override
    public void hideFlow(Long fulfillmentId) {
        ContentFlowLog logRow = flowLogMapper.selectOne(new LambdaQueryWrapper<ContentFlowLog>()
                .eq(ContentFlowLog::getFulfillmentId, fulfillmentId)
                .last("LIMIT 1"));
        if (logRow == null || logRow.getStatus() != ContentFlowStatus.SUCCESS || logRow.getPostId() == null) {
            return;
        }
        var response = communityFeignClient.hideLegacyPost(logRow.getPostId());
        if (response.success()) {
            logRow.setStatus(ContentFlowStatus.HIDDEN);
            flowLogMapper.updateById(logRow);
            log.info("状态同步: 还愿故事撤回, 帖子已隐藏, postId={}", logRow.getPostId());
        } else {
            log.warn("状态同步: 帖子隐藏失败（community 不可用），日志保留 FAILED 口径, fulfillmentId={}", fulfillmentId);
        }
    }

    /**
     * 执行流转：构建《我的梦想实现记录》图文模板 → Feign 生成帖子；
     * 失败指数退避重试（独立线程池内执行，不阻塞请求线程）。
     */
    private void attemptFlow(ContentFlowLog logRow, boolean withBackoff) {
        Wish wish = wishMapper.selectById(logRow.getWishId());
        WishFulfillment fulfillment = fulfillmentMapper.selectById(logRow.getFulfillmentId());
        if (wish == null || fulfillment == null) {
            logRow.setErrorMsg("心愿或还愿记录不存在");
            flowLogMapper.updateById(logRow);
            return;
        }

        Map<String, Object> payload = buildLegacyPostPayload(wish, fulfillment);
        String lastError = null;
        int attempts = withBackoff ? FLOW_MAX_RETRY : 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                var response = communityFeignClient.createLegacyPost(payload);
                if (response.success() && response.data() != null
                        && response.data().get("postId") != null) {
                    long postId = ((Number) response.data().get("postId")).longValue();
                    logRow.setPostId(postId);
                    logRow.setStatus(ContentFlowStatus.SUCCESS);
                    logRow.setErrorMsg(null);
                    logRow.setRetryCount(attempt - 1);
                    flowLogMapper.updateById(logRow);
                    log.info("内容流转成功, wishId={}, postId={}, attempts={}", logRow.getWishId(), postId, attempt);
                    return;
                }
                lastError = response.error() != null ? response.error().message() : "community 返回失败";
            } catch (Exception ex) {
                lastError = ex.getMessage();
            }
            if (attempt < attempts && withBackoff) {
                try {
                    Thread.sleep(FLOW_BACKOFF_MS[Math.min(attempt - 1, FLOW_BACKOFF_MS.length - 1)]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        logRow.setStatus(ContentFlowStatus.FAILED);
        logRow.setErrorMsg(truncate(lastError));
        logRow.setRetryCount(logRow.getRetryCount() == null ? 0 : logRow.getRetryCount());
        flowLogMapper.updateById(logRow);
        log.error("内容流转失败（已重试，管理端可人工重试）, wishId={}, error={}", logRow.getWishId(), lastError);
    }

    /**
     * 字段映射（文档 2.7）：wish.title → post.title、fulfillment.story →
     * post.content、media_urls → post.mediaUrls；内容为图文模板
     * （还愿故事 + 成长记录时间轴 + 进度变化），成就标签"✨ 心愿完成"。
     */
    private Map<String, Object> buildLegacyPostPayload(Wish wish, WishFulfillment fulfillment) {
        StringBuilder content = new StringBuilder();
        content.append("✨ 我实现了「").append(wish.getTitle()).append("」！\n\n");
        content.append("【还愿故事】\n").append(storySummary(fulfillment.getStory(), 2000)).append("\n");

        List<WishGrowthRecord> records = growthRecordMapper.selectList(new LambdaQueryWrapper<WishGrowthRecord>()
                .eq(WishGrowthRecord::getWishId, wish.getId())
                .orderByDesc(WishGrowthRecord::getCreatedAt)
                .last("LIMIT 10"));
        if (!records.isEmpty()) {
            content.append("\n【成长记录时间轴】\n");
            for (WishGrowthRecord record : records) {
                content.append("- ").append(record.getCreatedAt().toLocalDate())
                        .append(" ").append(record.getContent()).append("\n");
            }
        }

        WishProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<WishProgress>()
                .eq(WishProgress::getWishId, wish.getId())
                .last("LIMIT 1"));
        if (progress != null && progress.getTargetValue() != null && progress.getTargetValue() > 0) {
            int percent = Math.min(100, Math.round(progress.getCurrentValue() * 100.0f
                    / progress.getTargetValue()));
            content.append("\n【进度变化】").append(progress.getCurrentValue())
                    .append("/").append(progress.getTargetValue())
                    .append("（").append(percent).append("%）\n");
        }

        List<String> mediaUrls = WishJsonUtils.parseStringList(fulfillment.getMediaUrls());
        List<String> tagNames = List.of(LEGACY_TAG);

        return Map.of(
                "userId", wish.getUserId(),
                "title", wish.getTitle(),
                "content", content.toString(),
                "mediaUrls", mediaUrls != null ? mediaUrls : List.of(),
                "tagNames", tagNames);
    }

    // ---------------- 管理端 ----------------

    @Override
    public List<ContentFlowLog> listFlowLogs(String status, int page, int size) {
        LambdaQueryWrapper<ContentFlowLog> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            try {
                query.eq(ContentFlowLog::getStatus, ContentFlowStatus.valueOf(status.trim()));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "非法的流转状态: " + status);
            }
        }
        query.orderByDesc(ContentFlowLog::getId);
        return flowLogMapper.selectList(query
                .last("LIMIT " + Math.max(1, size) + " OFFSET " + Math.max(0, (page - 1) * size)));
    }

    @Override
    public LegacyStats getLegacyStats() {
        long inheritCount = inheritMapper.selectCount(null);
        List<FulfillmentInherit> inherits = inheritMapper.selectList(new LambdaQueryWrapper<FulfillmentInherit>()
                .orderByDesc(FulfillmentInherit::getId)
                .last("LIMIT 1000"));
        long totalTargets = inherits.stream().mapToLong(i -> i.getTargetCount() == null ? 0 : i.getTargetCount()).sum();
        long totalPushed = inherits.stream().mapToLong(i -> i.getPushedCount() == null ? 0 : i.getPushedCount()).sum();
        long flowSuccess = flowLogMapper.selectCount(new LambdaQueryWrapper<ContentFlowLog>()
                .eq(ContentFlowLog::getStatus, ContentFlowStatus.SUCCESS));
        long flowFailed = flowLogMapper.selectCount(new LambdaQueryWrapper<ContentFlowLog>()
                .eq(ContentFlowLog::getStatus, ContentFlowStatus.FAILED));
        long flowHidden = flowLogMapper.selectCount(new LambdaQueryWrapper<ContentFlowLog>()
                .eq(ContentFlowLog::getStatus, ContentFlowStatus.HIDDEN));
        double rate = totalTargets == 0 ? 0.0 : Math.round(totalPushed * 1000.0 / totalTargets) / 1000.0;
        return new LegacyStats(inheritCount, totalTargets, totalPushed, rate,
                flowSuccess, flowFailed, flowHidden);
    }

    // ---------------- 工具 ----------------

    private String storySummary(String story) {
        return storySummary(story, STORY_SUMMARY_LEN);
    }

    /** 故事摘要：去 HTML 标签 + 截断（通知/帖子引用共用，不泄露敏感字段） */
    private String storySummary(String story, int maxLen) {
        if (story == null) {
            return "";
        }
        String plain = story.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
        return plain.length() <= maxLen ? plain : plain.substring(0, maxLen) + "…";
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
