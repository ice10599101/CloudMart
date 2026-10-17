package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import org.springframework.beans.factory.ObjectProvider;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.entity.ProductAnswer;
import com.cloudmart.product.entity.ProductQuestion;
import com.cloudmart.product.feign.OrderPurchaseFeignClient;
import com.cloudmart.product.repository.ProductAnswerMapper;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductQuestionMapper;
import com.cloudmart.product.service.ProductQuestionService;
import com.cloudmart.product.vo.ProductAnswerVO;
import com.cloudmart.product.vo.ProductQuestionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品问答（PRODUCT-01 两表模型：question 一问多答）。
 *
 * <p>关键不变量：</p>
 * <ul>
 *   <li>question 归属路径 productId（不同商品 questionId 不能混用）；</li>
 *   <li>答案在 product_answer——第二人不能覆盖第一人；同人重复提交为编辑本人答案；</li>
 *   <li>未回答问题也可见（answers 空数组）；隐藏（status=1）后所有端不可见；</li>
 *   <li>已购徽标为回答时快照（历史资格展示；订单服务不可用按未购落库，不阻塞回答）；</li>
 *   <li>自答不 500（HashSet 去重昵称集合）；内容 1..500 字 HTML 转义；</li>
 *   <li>回答成功发 QUESTION_ANSWERED Outbox 事件（提问人通知由通知域消费）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductQuestionServiceImpl implements ProductQuestionService {

    private static final int CONTENT_MAX = 500;

    private final ProductQuestionMapper questionMapper;
    private final ProductAnswerMapper answerMapper;
    private final ProductMapper productMapper;
    private final OrderPurchaseFeignClient orderPurchaseFeignClient;
    private final com.cloudmart.product.feign.UserFeignClient userFeignClient;
    /**
     * PRODUCT-01：回答通知事件走 Outbox（事实先行），但 mall-product 未配置
     * cloudmart.async.enabled（outbox 三表/MapperScan/投递器均为 order 等模块的装配），
     * 故用 ObjectProvider 条件注入——未装配时优雅跳过（通知是 best-effort 增强，
     * 事实已在 answer 表），不阻塞服务启动。
     */
    private final ObjectProvider<OutboxService> outboxServiceProvider;

    @Override
    @Transactional
    public ProductQuestionVO ask(Long userId, Long productId, String question) {
        if (productMapper.selectById(productId) == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在");
        }
        String content = requireSanitized(question, "问题");
        ProductQuestion entity = new ProductQuestion();
        entity.setProductId(productId);
        entity.setUserId(userId);
        entity.setQuestion(content);
        entity.setStatus(0);
        questionMapper.insert(entity);
        log.info("[PRODUCT01] 问答已提问: productId={}, userId={}, questionId={}", productId, userId, entity.getId());
        return toVO(entity, List.of(), resolveNicknames(Set.of(userId)));
    }

    @Override
    public Page<ProductQuestionVO> list(Long productId, int page, int size) {
        Page<ProductQuestion> result = questionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductQuestion>()
                        .eq(ProductQuestion::getProductId, productId)
                        .eq(ProductQuestion::getStatus, 0)
                        .orderByDesc(ProductQuestion::getId));
        List<ProductQuestion> questions = result.getRecords();
        List<ProductAnswer> answers = answersOf(questions);
        Map<Long, List<ProductAnswer>> byQuestion = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        for (ProductQuestion q : questions) {
            userIds.add(q.getUserId());
        }
        for (ProductAnswer a : answers) {
            byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
            userIds.add(a.getAuthorUserId());
        }
        Map<Long, String> nicknames = resolveNicknames(userIds);
        Page<ProductQuestionVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(questions.stream()
                .map(q -> toVO(q, byQuestion.getOrDefault(q.getId(), List.of()), nicknames))
                .toList());
        return voPage;
    }

    @Override
    @Transactional
    public ProductQuestionVO answer(Long userId, Long productId, Long questionId, String answer) {
        ProductQuestion question = questionMapper.selectById(questionId);
        if (question == null || question.getStatus() != 0) {
            throw new BusinessException("QUESTION_NOT_FOUND", "问题不存在或已隐藏");
        }
        // PRODUCT-01：归属校验——不同商品的 questionId 不能混用（路径 productId 必须一致）
        if (!question.getProductId().equals(productId)) {
            throw new BusinessException("QUESTION_PRODUCT_MISMATCH", "问题不属于该商品");
        }
        String content = requireSanitized(answer, "回答");
        // 已购快照（fail-open：订单服务不可用按未购落库，不阻塞回答；仅历史资格展示）
        boolean purchased = false;
        try {
            ApiResponse<Boolean> res = orderPurchaseFeignClient.hasPurchasedProduct(userId, productId);
            purchased = res != null && Boolean.TRUE.equals(res.data());
        } catch (Exception e) {
            log.warn("[PRODUCT01] 已购标识查询失败（fail-open 按未购）: userId={}, err={}", userId, e.getMessage());
        }

        // 一问多答：同人同题已有答案 → 编辑本人答案；否则新增（第二人不覆盖第一人）
        ProductAnswer existing = answerMapper.selectOne(new LambdaQueryWrapper<ProductAnswer>()
                .eq(ProductAnswer::getQuestionId, questionId)
                .eq(ProductAnswer::getAuthorUserId, userId));
        ProductAnswer saved;
        if (existing != null) {
            existing.setBody(content);
            existing.setPurchased(purchased);
            answerMapper.updateById(existing);
            saved = existing;
        } else {
            ProductAnswer entity = new ProductAnswer();
            entity.setQuestionId(questionId);
            entity.setProductId(productId);
            entity.setAuthorUserId(userId);
            entity.setBody(content);
            entity.setPurchased(purchased);
            entity.setStatus(0);
            answerMapper.insert(entity);
            saved = entity;
        }

        // 回答成功 → Outbox 事件（提问人通知由通知域消费；事实先行不阻塞回答主流程）
        publishAnsweredEvent(saved, question, content);

        List<ProductAnswer> answers = answersOf(List.of(question));
        log.info("[PRODUCT01] 问答已回答: questionId={}, userId={}, purchased={}, edit={}",
                questionId, userId, purchased, existing != null);
        return toVO(question, answers, resolveNicknames(new HashSet<>(Set.of(userId, question.getUserId()))));
    }

    @Override
    public Page<ProductQuestionVO> myQuestions(Long userId, int page, int size) {
        // 我的问答：本人提问的问题（含状态）；本人回答过的问题亦在答案列表可见
        Page<ProductQuestion> result = questionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductQuestion>()
                        .eq(ProductQuestion::getUserId, userId)
                        .orderByDesc(ProductQuestion::getId));
        List<ProductQuestion> questions = result.getRecords();
        List<ProductAnswer> answers = answersOf(questions);
        Map<Long, List<ProductAnswer>> byQuestion = new HashMap<>();
        Set<Long> userIds = new HashSet<>(Set.of(userId));
        for (ProductAnswer a : answers) {
            byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
            userIds.add(a.getAuthorUserId());
        }
        Map<Long, String> nicknames = resolveNicknames(userIds);
        Page<ProductQuestionVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(questions.stream()
                .map(q -> toVO(q, byQuestion.getOrDefault(q.getId(), List.of()), nicknames))
                .toList());
        return voPage;
    }

    private List<ProductAnswer> answersOf(List<ProductQuestion> questions) {
        if (questions.isEmpty()) {
            return List.of();
        }
        List<Long> ids = questions.stream().map(ProductQuestion::getId).toList();
        return answerMapper.selectList(new LambdaQueryWrapper<ProductAnswer>()
                .in(ProductAnswer::getQuestionId, ids)
                .eq(ProductAnswer::getStatus, 0)
                .orderByAsc(ProductAnswer::getId));
    }

    private void publishAnsweredEvent(ProductAnswer answer, ProductQuestion question, String content) {
        try {
            String payload = "{\"answerId\":" + answer.getId()
                    + ",\"questionId\":" + question.getId()
                    + ",\"productId\":" + question.getProductId()
                    + ",\"askerUserId\":" + question.getUserId()
                    + ",\"authorUserId\":" + answer.getAuthorUserId()
                    + ",\"preview\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
            OutboxService outbox = outboxServiceProvider.getIfAvailable();
            if (outbox == null) {
                // outbox 未装配：跳过事件（通知增强缺失，回答事实不受影响）
                log.debug("[PRODUCT01] outbox 未装配，跳过 QUESTION_ANSWERED 事件: answerId={}", answer.getId());
                return;
            }
            outbox.record(EventEnvelope.of(
                    "QUESTION_ANSWERED", 1, String.valueOf(answer.getId()), 1, null, payload));
        } catch (Exception e) {
            // 通知事件失败不阻断回答主流程（事实已在 answer 表；补偿可由对账重建）
            log.warn("[PRODUCT01] QUESTION_ANSWERED 事件登记失败（不阻塞回答）: answerId={}, err={}",
                    answer.getId(), e.getMessage());
        }
    }

    /** 内容 HTML 转义 + 非空 + 长度 1..500 */
    private String requireSanitized(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", label + "内容不能为空");
        }
        String sanitized = raw.trim()
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        if (sanitized.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", label + "内容不能为空");
        }
        if (sanitized.length() > CONTENT_MAX) {
            throw new BusinessException("VALIDATION_ERROR", label + "内容不能超过 " + CONTENT_MAX + " 字");
        }
        return sanitized;
    }

    /** 展示型昵称批量解析：失败落占位（fail-open）；返回 ID 为字符串时也能映射 */
    private Map<Long, String> resolveNicknames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        try {
            ApiResponse<List<Map<String, Object>>> res = userFeignClient.batchGetUsers(List.copyOf(userIds));
            if (res != null && res.data() != null) {
                for (Map<String, Object> u : res.data()) {
                    Object idRaw = u.get("id");
                    Long id = null;
                    if (idRaw instanceof Number n) {
                        id = n.longValue();
                    } else if (idRaw instanceof String s && !s.isBlank()) {
                        try {
                            id = Long.parseLong(s.trim());
                        } catch (NumberFormatException ignore) {
                            id = null;
                        }
                    }
                    if (id != null) {
                        result.put(id, u.get("nickname") != null ? String.valueOf(u.get("nickname")) : "用户" + id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[PRODUCT01] 昵称批量解析失败（占位）: err={}", e.getMessage());
        }
        return result;
    }

    private ProductQuestionVO toVO(ProductQuestion q, List<ProductAnswer> answers,
                                   Map<Long, String> nicknames) {
        List<ProductAnswerVO> answerVOs = answers.stream()
                .map(a -> new ProductAnswerVO(
                        a.getId(),
                        a.getAuthorUserId(),
                        nicknames.getOrDefault(a.getAuthorUserId(), "用户" + a.getAuthorUserId()),
                        a.getBody(),
                        Boolean.TRUE.equals(a.getPurchased()),
                        a.getCreatedAt()))
                .toList();
        return new ProductQuestionVO(
                q.getId(),
                q.getProductId(),
                q.getUserId(),
                nicknames.getOrDefault(q.getUserId(), "用户" + q.getUserId()),
                q.getQuestion(),
                answerVOs,
                q.getCreatedAt());
    }

    // ===== PRODUCT-01 管理端 =====

    @Override
    public Page<ProductQuestionVO> adminPage(Long productId, Integer status, int page, int size) {
        Page<ProductQuestion> result = questionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductQuestion>()
                        .eq(productId != null, ProductQuestion::getProductId, productId)
                        .eq(status != null, ProductQuestion::getStatus, status)
                        .orderByDesc(ProductQuestion::getId));
        List<ProductQuestion> questions = result.getRecords();
        // 管理视图含隐藏答案（处置需要看到全部）
        List<ProductAnswer> answers = questions.isEmpty() ? List.of()
                : answerMapper.selectList(new LambdaQueryWrapper<ProductAnswer>()
                        .in(ProductAnswer::getQuestionId, questions.stream().map(ProductQuestion::getId).toList())
                        .orderByAsc(ProductAnswer::getId));
        Map<Long, List<ProductAnswer>> byQuestion = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        for (ProductAnswer a : answers) {
            byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
            userIds.add(a.getAuthorUserId());
        }
        for (ProductQuestion q : questions) {
            userIds.add(q.getUserId());
        }
        Map<Long, String> nicknames = resolveNicknames(userIds);
        Page<ProductQuestionVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(questions.stream()
                .map(q -> toVO(q, byQuestion.getOrDefault(q.getId(), List.of()), nicknames))
                .toList());
        return voPage;
    }

    @Override
    @Transactional
    public void adminHideQuestion(Long questionId, String reason) {
        ProductQuestion question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException("QUESTION_NOT_FOUND", "问题不存在");
        }
        question.setStatus(1);
        questionMapper.updateById(question);
        log.info("[PRODUCT01] 问题已隐藏: questionId={}, reason={}", questionId, reason);
    }

    @Override
    @Transactional
    public void adminRestoreQuestion(Long questionId) {
        ProductQuestion question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException("QUESTION_NOT_FOUND", "问题不存在");
        }
        question.setStatus(0);
        questionMapper.updateById(question);
        log.info("[PRODUCT01] 问题已恢复: questionId={}", questionId);
    }

    @Override
    @Transactional
    public void adminHideAnswer(Long answerId, String reason) {
        ProductAnswer answer = answerMapper.selectById(answerId);
        if (answer == null) {
            throw new BusinessException("ANSWER_NOT_FOUND", "答案不存在");
        }
        answer.setStatus(1);
        answerMapper.updateById(answer);
        log.info("[PRODUCT01] 答案已隐藏: answerId={}, reason={}", answerId, reason);
    }

    @Override
    @Transactional
    public void adminRestoreAnswer(Long answerId) {
        ProductAnswer answer = answerMapper.selectById(answerId);
        if (answer == null) {
            throw new BusinessException("ANSWER_NOT_FOUND", "答案不存在");
        }
        answer.setStatus(0);
        answerMapper.updateById(answer);
        log.info("[PRODUCT01] 答案已恢复: answerId={}", answerId);
    }
}
