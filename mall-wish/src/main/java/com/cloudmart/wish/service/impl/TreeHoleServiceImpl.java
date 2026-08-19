package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.config.WishAiProperties;
import com.cloudmart.wish.dto.AiConversationListQuery;
import com.cloudmart.wish.dto.TreeHoleMessageRequest;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishAiConversation;
import com.cloudmart.wish.enums.AiConversationRole;
import com.cloudmart.wish.enums.AiResourceType;
import com.cloudmart.wish.enums.AiScene;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.repository.WishAiConversationMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.ConsentService;
import com.cloudmart.wish.service.TreeHoleAiClient;
import com.cloudmart.wish.service.TreeHoleService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.service.impl.TreeHoleReplyParser.ParsedReply;
import com.cloudmart.wish.vo.AiConversationVO;
import com.cloudmart.wish.vo.AiResourceVO;
import com.cloudmart.wish.vo.TreeHoleReplyVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * 树洞治愈服务实现（Sprint 1.3，文档 2.11 / 30 章）。
 *
 * <p>关键设计与安全策略：</p>
 * <ul>
 *   <li>私密性：树洞心愿仅作者本人可对话（visibility=TREE_HOLE 为私密心愿）</li>
 *   <li>合规前置：未同意 AI 数据处理协议（GRANT 有效）→ 403 WISH_CONSENT_REQUIRED</li>
 *   <li>限频：单用户 10 次/日（用户时区），超限 429；Redis Fail-Open</li>
 *   <li>危机拦截：命中危机关键词 → 本地兜底回复 + 心理援助热线，
 *       绝不外发 DashScope（文档 30.4：高危内容不外发第三方）</li>
 *   <li>PII 脱敏：外发前移除手机号/邮箱/身份证号</li>
 *   <li>情感分数：AI 输出 -1.0~1.0，DB 存 -100~100 整数（㊲c TINYINT 契约）</li>
 *   <li>对话持久化：USER + ASSISTANT 两条记录同事务写入（含 session 维度会话）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TreeHoleServiceImpl implements TreeHoleService {

    /** 情感分数换算系数：DB 整数 -100~100 ↔ API 浮点 -1.0~1.0 */
    private static final int SENTIMENT_SCALE = 100;

    /** 会话 ID 前缀：tree-hole-{wishId}-{userId}，同一树洞心愿连续会话 */
    private static final String SESSION_PREFIX = "tree-hole-";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<AiResourceVO>> RESOURCE_LIST_TYPE = new TypeReference<>() {};

    private final WishMapper wishMapper;
    private final WishAiConversationMapper conversationMapper;
    private final ConsentService consentService;
    private final UserStatService userStatService;
    private final AiRateLimiter aiRateLimiter;
    private final AiPrivacySanitizer privacySanitizer;
    private final TreeHoleAiClient treeHoleAiClient;
    private final WishAiProperties aiProperties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public TreeHoleReplyVO sendTreeHoleMessage(Long userId, TreeHoleMessageRequest request) {
        Wish wish = requireTreeHoleWish(userId, request.wishId());

        // ---- 合规与限频前置（无 DB 写、无锁持有）----
        if (!consentService.hasGrantedAiDataProcessing(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_CONSENT_REQUIRED,
                    "使用 AI 功能前需同意 AI 数据处理协议");
        }
        ZoneId userZone = ZoneId.of(userStatService.getUserTimezone(userId));
        if (!aiRateLimiter.checkTreeHoleDailyLimit(userId, userZone)) {
            throw new BusinessException(WishErrorCodes.WISH_AI_RATE_LIMITED,
                    "今日树洞倾诉次数已达上限（" + aiProperties.getTreeHoleDailyLimit() + " 次/日）");
        }

        // ---- 危机词本地拦截：不外发第三方 AI，返回专业话术 + 援助热线 ----
        String rawMessage = request.message().trim();
        if (hitsCrisisKeyword(rawMessage)) {
            log.warn("树洞消息命中危机词，本地拦截不外发, userId={}, wishId={}", userId, request.wishId());
            List<AiResourceVO> hotlineResources = hotlineResources();
            persistConversation(userId, request.wishId(), rawMessage,
                    aiProperties.getCrisisFallbackReply(), -SENTIMENT_SCALE, hotlineResources);
            return new TreeHoleReplyVO(aiProperties.getCrisisFallbackReply(), -1.0, hotlineResources);
        }

        // ---- PII 脱敏后调用 DashScope（文档 30.4）----
        String sanitizedMessage = privacySanitizer.sanitize(rawMessage);
        ParsedReply reply = treeHoleAiClient.generateReply(
                aiProperties.getTreeHoleSystemPrompt(), sanitizedMessage);

        persistConversation(userId, request.wishId(), rawMessage,
                reply.reply(), toStoredSentiment(reply.sentimentScore()), reply.resources());
        return new TreeHoleReplyVO(reply.reply(), reply.sentimentScore(), reply.resources());
    }

    @Override
    public ConversationPage listConversations(Long userId, AiConversationListQuery query) {
        int pageSize = query.safePageSize();
        Long cursor = parseCursor(query.cursor());

        LambdaQueryWrapper<WishAiConversation> wrapper = new LambdaQueryWrapper<WishAiConversation>()
                .eq(WishAiConversation::getUserId, userId)
                .eq(WishAiConversation::getScene, query.safeScene())
                .orderByDesc(WishAiConversation::getId)
                .last("LIMIT " + (pageSize + 1));
        if (cursor != null) {
            wrapper.lt(WishAiConversation::getId, cursor);
        }

        List<WishAiConversation> records = conversationMapper.selectList(wrapper);
        boolean hasMore = records.size() > pageSize;
        List<WishAiConversation> pageItems = hasMore ? records.subList(0, pageSize) : records;
        List<AiConversationVO> vos = pageItems.stream().map(this::toConversationVO).toList();
        String nextCursor = hasMore ? String.valueOf(pageItems.getLast().getId()) : null;
        return new ConversationPage(vos, nextCursor, hasMore);
    }

    /**
     * 校验心愿存在、为树洞类型且调用者为作者。
     */
    private Wish requireTreeHoleWish(Long userId, Long wishId) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        if (wish.getVisibility() != WishVisibility.TREE_HOLE
                || !Boolean.TRUE.equals(wish.getEnableAiReply())) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "该心愿未启用树洞 AI 回复");
        }
        if (!wish.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_AUTHOR,
                    "树洞心愿仅作者本人可对话");
        }
        return wish;
    }

    /**
     * 危机关键词检测（大小写不敏感，命中任一即拦截）。
     */
    private boolean hitsCrisisKeyword(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return aiProperties.getCrisisKeywords().stream().anyMatch(lower::contains);
    }

    /**
     * 配置的热线资源转换为 VO。
     */
    private List<AiResourceVO> hotlineResources() {
        return aiProperties.getHotlineResources().stream()
                .map(res -> new AiResourceVO(
                        AiResourceType.HOTLINE.name(), res.getTitle(), res.getUrl()))
                .toList();
    }

    /**
     * 情感分数换算：-1.0~1.0 浮点 → -100~100 整数（㊲c 存储契约）。
     */
    private Integer toStoredSentiment(Double sentimentScore) {
        if (sentimentScore == null) {
            return null;
        }
        return Math.round((float) (sentimentScore * SENTIMENT_SCALE));
    }

    /**
     * 持久化一次对话（USER + ASSISTANT 同事务）。
     */
    private void persistConversation(Long userId, Long wishId, String userMessage,
                                     String reply, Integer storedSentiment,
                                     List<AiResourceVO> resources) {
        String sessionId = SESSION_PREFIX + wishId + "-" + userId;
        String resourcesJson = stringifyResources(resources);
        transactionTemplate.executeWithoutResult(status -> {
            WishAiConversation userRecord = new WishAiConversation();
            userRecord.setUserId(userId);
            userRecord.setSessionId(sessionId);
            userRecord.setScene(AiScene.TREE_HOLE);
            userRecord.setRole(AiConversationRole.USER);
            userRecord.setContent(userMessage);
            conversationMapper.insert(userRecord);

            WishAiConversation assistantRecord = new WishAiConversation();
            assistantRecord.setUserId(userId);
            assistantRecord.setSessionId(sessionId);
            assistantRecord.setScene(AiScene.TREE_HOLE);
            assistantRecord.setRole(AiConversationRole.ASSISTANT);
            assistantRecord.setContent(reply);
            assistantRecord.setSentimentScore(storedSentiment);
            assistantRecord.setResources(resourcesJson);
            conversationMapper.insert(assistantRecord);
        });
    }

    private AiConversationVO toConversationVO(WishAiConversation record) {
        Double sentimentScore = record.getSentimentScore() != null
                ? record.getSentimentScore() / (double) SENTIMENT_SCALE : null;
        return new AiConversationVO(record.getId(), record.getRole(), record.getContent(),
                sentimentScore, parseResources(record.getResources()), record.getCreatedAt());
    }

    private String stringifyResources(List<AiResourceVO> resources) {
        if (resources == null || resources.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(resources);
        } catch (Exception ex) {
            log.warn("推荐资源序列化失败，忽略存储: {}", ex.getMessage());
            return null;
        }
    }

    private List<AiResourceVO> parseResources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<AiResourceVO> resources = OBJECT_MAPPER.readValue(json, RESOURCE_LIST_TYPE);
            return resources != null ? resources : List.of();
        } catch (Exception ex) {
            log.warn("推荐资源解析失败: {}", ex.getMessage());
            return List.of();
        }
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "分页游标格式不正确");
        }
    }
}
