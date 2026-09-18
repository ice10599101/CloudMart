package com.cloudmart.community.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 问卷详情 VO（含题目与聚合结果）。
 *
 * @param id            问卷 ID
 * @param title         问卷标题
 * @param questions     题目列表
 * @param myAnswers     当前用户每题答案（questionId → 选项 ID 列表或填空文本）
 * @param responseCount 答卷人数（去重用户数）
 */
@Schema(description = "问卷详情")
public record SurveyVO(
        String id,
        String title,
        List<SurveyQuestionVO> questions,
        List<MyAnswerVO> myAnswers,
        Long responseCount
) {

    @Schema(description = "问卷题目")
    public record SurveyQuestionVO(
            Long id,
            String text,
            String type,
            List<String> options,
            Boolean required,
            /** 选择题：各选项票数（与 options 按下标一一对应；选项无独立 DB 行，答案存选项下标） */
            List<Long> optionCounts,
            /** 填空题：有效答卷数 */
            Long textAnswerCount
    ) {
    }

    @Schema(description = "我的单题答案")
    public record MyAnswerVO(
            Long questionId,
            /** 选择题答案：选项下标（0 基，对应 options 数组位置） */
            List<Long> optionIds,
            String text
    ) {
    }
}
