package com.cloudmart.wish.entity;

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
 * AI 回复人工抽检（Sprint 2.8，文档 2.7：AI 质量抽检——抽检任务+问题分类）。
 *
 * <p>content 为抽检时快照（回复后续被删除不影响评分对象审计）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_ai_review")
public class AiReview {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 被抽检的 AI 回复 ID（wish_ai_conversation，uk 防重复抽样） */
    private Long conversationId;

    /** AI 场景（GOAL_BREAKDOWN/TREE_HOLE/ANNUAL_REPORT/EXPECTED_GUIDE） */
    private String scene;

    /** 抽检时回复内容快照 */
    private String content;

    /** 人工评分（null=未评） */
    private ReviewResult result;

    /** 问题分类（FAIL 时填写） */
    private IssueType issueType;

    /** 评语 */
    private String note;

    /** 评分人（管理后台用户 ID） */
    private Long reviewedBy;

    /** 评分时间（UTC） */
    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public enum ReviewResult {
        PASS,
        FAIL
    }

    public enum IssueType {
        /** 机械感（模板腔/空洞） */
        MECHANICAL,
        /** 错误信息（事实/安全/格式错误） */
        ERROR,
        /** 不相关（答非所问） */
        IRRELEVANT
    }
}
