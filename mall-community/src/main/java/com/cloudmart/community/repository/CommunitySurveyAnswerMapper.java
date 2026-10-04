package com.cloudmart.community.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.community.entity.CommunitySurveyAnswer;
import org.apache.ibatis.annotations.Mapper;

/** 问卷答案 Mapper（V10，编辑器附件；T21 统计聚合）。 */
@Mapper
public interface CommunitySurveyAnswerMapper extends BaseMapper<CommunitySurveyAnswer> {

    /** T21：去重参与人数（GROUP BY 聚合，不随答卷总量线性增长） */
    @org.apache.ibatis.annotations.Select("SELECT COUNT(DISTINCT user_id) "
            + "FROM community_survey_answers WHERE survey_id = #{surveyId}")
    long countRespondents(@org.apache.ibatis.annotations.Param("surveyId") String surveyId);

    /** T21：逐题答案行数 */
    @org.apache.ibatis.annotations.Select("SELECT question_id AS questionId, COUNT(*) AS cnt "
            + "FROM community_survey_answers WHERE survey_id = #{surveyId} GROUP BY question_id")
    java.util.List<java.util.Map<String, Object>> countByQuestion(
            @org.apache.ibatis.annotations.Param("surveyId") String surveyId);

    /** T21：选择题逐选项下标计数（optionIds JSON 数组经 JSON_TABLE 展开，MySQL 9） */
    @org.apache.ibatis.annotations.Select("SELECT a.question_id AS questionId, jt.idx AS optionIndex, "
            + "COUNT(*) AS cnt FROM community_survey_answers a "
            + "JOIN JSON_TABLE(a.option_ids, '$[*]' COLUMNS (idx BIGINT PATH '$')) jt "
            + "WHERE a.survey_id = #{surveyId} AND JSON_VALID(a.option_ids) "
            + "GROUP BY a.question_id, jt.idx")
    java.util.List<java.util.Map<String, Object>> countChoiceByIndex(
            @org.apache.ibatis.annotations.Param("surveyId") String surveyId);

    /** T21：文本题非空作答数 */
    @org.apache.ibatis.annotations.Select("SELECT question_id AS questionId, COUNT(*) AS cnt "
            + "FROM community_survey_answers "
            + "WHERE survey_id = #{surveyId} AND text_content IS NOT NULL AND text_content <> '' "
            + "GROUP BY question_id")
    java.util.List<java.util.Map<String, Object>> countTextByQuestion(
            @org.apache.ibatis.annotations.Param("surveyId") String surveyId);
}
