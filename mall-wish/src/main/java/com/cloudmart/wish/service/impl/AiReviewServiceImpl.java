package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.AiReview;
import com.cloudmart.wish.entity.WishAiConversation;
import com.cloudmart.wish.enums.AiConversationRole;
import com.cloudmart.wish.repository.AiReviewMapper;
import com.cloudmart.wish.repository.WishAiConversationMapper;
import com.cloudmart.wish.service.AiReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 回复质量抽检服务实现（Sprint 2.8，文档 2.7 管理后台）。
 *
 * <p>抽样：随机抽 ASSISTANT 回复（排除已抽样与软删；uk_review_conversation
 * 兜底）；评分：PASS / FAIL+问题分类（MECHANICAL/ERROR/IRRELEVANT）；
 * 统计：合格率 + 分类计数（文档验收：人工抽检合格率 ≥ 90%）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiReviewServiceImpl implements AiReviewService {

    private static final int MAX_SAMPLE = 100;

    private final AiReviewMapper reviewMapper;
    private final WishAiConversationMapper conversationMapper;

    @Override
    @Transactional
    public int generateSamples(List<String> scenes, int sampleSize, Long adminUserId) {
        int size = Math.max(1, Math.min(MAX_SAMPLE, sampleSize));

        Set<Long> sampledIds = new HashSet<>(reviewMapper.selectList(new LambdaQueryWrapper<AiReview>()
                        .select(AiReview::getConversationId))
                .stream().map(AiReview::getConversationId).toList());

        LambdaQueryWrapper<WishAiConversation> query = new LambdaQueryWrapper<WishAiConversation>()
                .eq(WishAiConversation::getRole, AiConversationRole.ASSISTANT)
                .isNull(WishAiConversation::getDeletedAt)
                .orderByDesc(WishAiConversation::getId)
                .last("LIMIT " + Math.min(size * 3 + sampledIds.size() + 10, 1000));
        if (scenes != null && !scenes.isEmpty()) {
            query.in(WishAiConversation::getScene, scenes);
        }
        List<WishAiConversation> candidates = conversationMapper.selectList(query).stream()
                .filter(c -> !sampledIds.contains(c.getId()))
                .toList();

        // 近期优先随机抽样：候选打乱后取前 N（Random 以时间种子，抽检任务本身允许非确定）
        java.util.Collections.shuffle(candidates, new java.util.Random());
        int created = 0;
        for (WishAiConversation candidate : candidates) {
            if (created >= size) {
                break;
            }
            AiReview review = new AiReview();
            review.setConversationId(candidate.getId());
            review.setScene(candidate.getScene() == null ? null : candidate.getScene().name());
            review.setContent(truncate(candidate.getContent()));
            try {
                reviewMapper.insert(review);
                created++;
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                // 并发生成任务的唯一约束兜底：跳过已抽样回复
            }
        }
        log.info("AI 抽检样本已生成, scenes={}, requested={}, created={}, adminUserId={}",
                scenes, size, created, adminUserId);
        return created;
    }

    @Override
    public List<AiReview> listSamples(String scene, AiReview.ReviewResult result, int page, int size) {
        LambdaQueryWrapper<AiReview> query = new LambdaQueryWrapper<>();
        if (scene != null && !scene.isBlank()) {
            query.eq(AiReview::getScene, scene.trim());
        }
        if (result != null) {
            query.eq(AiReview::getResult, result);
        }
        query.orderByDesc(AiReview::getId);
        return reviewMapper.selectList(query
                .last("LIMIT " + Math.max(1, size) + " OFFSET " + Math.max(0, (page - 1) * size)));
    }

    @Override
    public AiReview scoreSample(Long id, AiReview.ReviewResult result, AiReview.IssueType issueType,
                                String note, Long adminUserId) {
        if (result == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "评分结果不能为空");
        }
        if (result == AiReview.ReviewResult.FAIL && issueType == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "不合格样本必须填写问题分类");
        }
        if (result == AiReview.ReviewResult.PASS) {
            issueType = null;
        }
        AiReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "抽检样本不存在");
        }
        review.setResult(result);
        review.setIssueType(issueType);
        review.setNote(note);
        review.setReviewedBy(adminUserId);
        review.setReviewedAt(LocalDateTime.now(ZoneId.of("UTC")));
        reviewMapper.updateById(review);
        log.info("AI 抽检评分, id={}, result={}, issueType={}, adminUserId={}", id, result, issueType, adminUserId);
        return review;
    }

    @Override
    public AiReviewStats stats() {
        long total = reviewMapper.selectCount(null);
        long pass = reviewMapper.selectCount(new LambdaQueryWrapper<AiReview>()
                .eq(AiReview::getResult, AiReview.ReviewResult.PASS));
        long fail = reviewMapper.selectCount(new LambdaQueryWrapper<AiReview>()
                .eq(AiReview::getResult, AiReview.ReviewResult.FAIL));
        long issueMechanical = countIssue(AiReview.IssueType.MECHANICAL);
        long issueError = countIssue(AiReview.IssueType.ERROR);
        long issueIrrelevant = countIssue(AiReview.IssueType.IRRELEVANT);
        double passRate = (pass + fail) == 0 ? 0.0 : Math.round(pass * 1000.0 / (pass + fail)) / 1000.0;
        return new AiReviewStats(total, pass + fail, pass, fail, passRate,
                issueMechanical, issueError, issueIrrelevant);
    }

    private long countIssue(AiReview.IssueType issueType) {
        return reviewMapper.selectCount(new LambdaQueryWrapper<AiReview>()
                .eq(AiReview::getResult, AiReview.ReviewResult.FAIL)
                .eq(AiReview::getIssueType, issueType));
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 2000 ? content : content.substring(0, 2000);
    }
}
