package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.entity.ProductQuestion;
import com.cloudmart.product.feign.OrderPurchaseFeignClient;
import com.cloudmart.product.repository.ProductQuestionMapper;
import com.cloudmart.product.service.ProductQuestionService;
import com.cloudmart.product.vo.ProductQuestionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品问答（N-5 问大家）。
 *
 * <p>提问/回答均需登录；内容 HTML 转义（对齐还愿感悟口径）。
 * 回答人"已购"徽标为回答时快照（fail-open：订单服务不可用按未购落库，不阻塞回答）。
 * 昵称为展示型数据，批量解析失败落占位值。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductQuestionServiceImpl implements ProductQuestionService {

    private final ProductQuestionMapper questionMapper;
    private final com.cloudmart.product.repository.ProductMapper productMapper;
    private final OrderPurchaseFeignClient orderPurchaseFeignClient;
    private final com.cloudmart.product.feign.UserFeignClient userFeignClient;

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
        log.info("N-5 问答已提问: productId={}, userId={}, questionId={}", productId, userId, entity.getId());
        return toVO(entity, resolveNicknames(Set.of(userId)));
    }

    @Override
    public Page<ProductQuestionVO> list(Long productId, int page, int size) {
        Page<ProductQuestion> result = questionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductQuestion>()
                        .eq(ProductQuestion::getProductId, productId)
                        .eq(ProductQuestion::getStatus, 0)
                        .isNotNull(ProductQuestion::getAnswer)
                        .orderByDesc(ProductQuestion::getId));
        Set<Long> userIds = new HashSet<>();
        for (ProductQuestion q : result.getRecords()) {
            userIds.add(q.getUserId());
            if (q.getAnswerUserId() != null) {
                userIds.add(q.getAnswerUserId());
            }
        }
        Map<Long, String> nicknames = resolveNicknames(userIds);
        Page<ProductQuestionVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(q -> toVO(q, nicknames)).toList());
        return voPage;
    }

    @Override
    @Transactional
    public ProductQuestionVO answer(Long userId, Long questionId, String answer) {
        ProductQuestion question = questionMapper.selectById(questionId);
        if (question == null || question.getStatus() != 0) {
            throw new BusinessException("QUESTION_NOT_FOUND", "问题不存在或已隐藏");
        }
        String content = requireSanitized(answer, "回答");
        // N-5：已购徽标快照（fail-open：订单服务不可用按未购落库，不阻塞回答）
        boolean purchased = false;
        try {
            ApiResponse<Boolean> res = orderPurchaseFeignClient.hasPurchasedProduct(userId, question.getProductId());
            purchased = res != null && Boolean.TRUE.equals(res.data());
        } catch (Exception e) {
            log.warn("N-5 已购标识查询失败（fail-open 按未购）: userId={}, err={}", userId, e.getMessage());
        }
        int updated = questionMapper.update(null, new LambdaUpdateWrapper<ProductQuestion>()
                .eq(ProductQuestion::getId, questionId)
                .eq(ProductQuestion::getStatus, 0)
                .set(ProductQuestion::getAnswer, content)
                .set(ProductQuestion::getAnswerUserId, userId)
                .set(ProductQuestion::getAnswerPurchased, purchased)
                .set(ProductQuestion::getAnsweredAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("QUESTION_NOT_FOUND", "问题不存在或已隐藏");
        }
        log.info("N-5 问答已回答: questionId={}, userId={}, purchased={}", questionId, userId, purchased);
        question.setAnswer(content);
        question.setAnswerUserId(userId);
        question.setAnswerPurchased(purchased);
        question.setAnsweredAt(LocalDateTime.now());
        return toVO(question, resolveNicknames(new HashSet<>(Set.of(userId, question.getUserId()))));
    }

    /** 内容 HTML 转义 + 非空校验（对齐还愿感悟口径） */
    private String requireSanitized(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", label + "内容不能为空");
        }
        String sanitized = raw.trim()
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        if (sanitized.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", label + "内容不能为空");
        }
        return sanitized;
    }

    /** 展示型昵称批量解析：失败落占位（fail-open） */
    private Map<Long, String> resolveNicknames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        try {
            ApiResponse<List<Map<String, Object>>> res = userFeignClient.batchGetUsers(List.copyOf(userIds));
            if (res != null && res.data() != null) {
                for (Map<String, Object> u : res.data()) {
                    if (u.get("id") instanceof Number n) {
                        result.put(n.longValue(), u.get("nickname") != null ? String.valueOf(u.get("nickname")) : "用户" + n);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("N-5 昵称批量解析失败（占位）: err={}", e.getMessage());
        }
        return result;
    }

    private ProductQuestionVO toVO(ProductQuestion q, Map<Long, String> nicknames) {
        return new ProductQuestionVO(
                q.getId(),
                q.getProductId(),
                q.getUserId(),
                nicknames.getOrDefault(q.getUserId(), "用户" + q.getUserId()),
                q.getQuestion(),
                q.getAnswer(),
                q.getAnswerUserId(),
                q.getAnswerUserId() != null ? nicknames.getOrDefault(q.getAnswerUserId(), "用户" + q.getAnswerUserId()) : null,
                Boolean.TRUE.equals(q.getAnswerPurchased()),
                q.getCreatedAt(),
                q.getAnsweredAt());
    }
}
