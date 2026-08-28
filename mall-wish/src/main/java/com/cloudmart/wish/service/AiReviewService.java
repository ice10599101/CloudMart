package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.AiReview;

import java.util.List;

/**
 * AI 回复质量抽检服务（Sprint 2.8，文档 2.7/2.8 管理后台）。
 */
public interface AiReviewService {

    /**
     * 生成抽检任务：随机抽取指定场景的 ASSISTANT 回复生成待评样本
     * （已被抽检过的回复不重复；uk_review_conversation 兜底）。
     *
     * @param scenes    场景清单（空=全部）
     * @param sampleSize 抽样数量（1-100，默认 20）
     * @param adminUserId 发起管理员
     * @return 本次新生成的样本数（不足时按实际返回）
     */
    int generateSamples(List<String> scenes, int sampleSize, Long adminUserId);

    /** 待评/已评样本列表（id 倒序） */
    List<AiReview> listSamples(String scene, AiReview.ReviewResult result, int page, int size);

    /** 人工评分（PASS 或 FAIL+问题分类） */
    AiReview scoreSample(Long id, AiReview.ReviewResult result, AiReview.IssueType issueType,
                         String note, Long adminUserId);

    /** 合格率与问题分类统计 */
    AiReviewStats stats();

    /**
     * 抽检统计（文档 2.7：合格率 ≥ 90% + 问题分类统计）。
     */
    record AiReviewStats(
            long totalSamples,
            long reviewedCount,
            long passCount,
            long failCount,
            double passRate,
            long issueMechanical,
            long issueError,
            long issueIrrelevant) {
    }
}
