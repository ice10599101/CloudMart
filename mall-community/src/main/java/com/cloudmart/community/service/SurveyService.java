package com.cloudmart.community.service;

import com.cloudmart.community.dto.CreateSurveyRequest;
import com.cloudmart.community.dto.SurveyResponseRequest;
import com.cloudmart.community.vo.SurveyVO;

/**
 * 问卷服务（编辑器附件，V10）。
 *
 * <p>问卷随编辑器正文发布幂等落库（主键为客户端 UUID）；
 * 答卷按 (survey, question, user) 唯一，重复提交覆盖更新。</p>
 */
public interface SurveyService {

    /**
     * 创建问卷（幂等：同 ID 且同创建者重复提交返回既有数据；同 ID 不同创建者视为冲突）。
     */
    SurveyVO createSurvey(Long userId, CreateSurveyRequest request);

    /**
     * 问卷详情（题目 + 各题聚合结果；myAnswers 未登录/未答为空列表）。
     */
    SurveyVO getSurvey(String surveyId, Long userId);

    /**
     * 提交答卷（必答题缺失拒绝；重复提交覆盖更新）。
     */
    void submitResponse(String surveyId, Long userId, SurveyResponseRequest request);
}
