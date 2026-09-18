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
 * 问卷题目实体（V10 迁移，编辑器附件）。
 *
 * <p>{@code options} 存选项文本的 JSON 数组字符串（填空题为空串）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("community_survey_questions")
public class CommunitySurveyQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 问卷 ID */
    private String surveyId;

    /** 题干 */
    private String content;

    /** 题型: SINGLE/MULTI/TEXT */
    private String type;

    /** 选项文本 JSON 数组（填空题为空串） */
    private String options;

    /** 是否必答 */
    private Boolean isRequired;

    /** 排序（越小越靠前） */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
