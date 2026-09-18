package com.cloudmart.community.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 问卷答案实体（V10 迁移，编辑器附件）。
 *
 * <p>uk(survey_id, question_id, user_id)：重复提交覆盖更新（可修改答案）。
 * {@code optionIds} 存选中选项 ID 的 JSON 数组（选择题）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("community_survey_answers")
public class CommunitySurveyAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 问卷 ID */
    private String surveyId;

    /** 题目 ID */
    private Long questionId;

    /** 答卷用户 ID */
    private Long userId;

    /** 选中选项 ID 的 JSON 数组（选择题） */
    private String optionIds;

    /** 填空内容（填空题） */
    private String textContent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
