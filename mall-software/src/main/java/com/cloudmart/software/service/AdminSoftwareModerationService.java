package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.dto.software.AdminFeedbackMarkRequest;
import com.cloudmart.software.dto.software.AdminReportHandleRequest;

import java.util.Map;

/**
 * 举报与链接反馈处置（方案 5.3/6.12）：仅标记成立不隐式封禁，
 * 处置动作显式选择且与状态、审计、Outbox 同软件库事务提交。
 */
public interface AdminSoftwareModerationService {

    /** 举报列表（status 过滤） */
    Page<Map<String, Object>> reportPage(String status, int page, int size);

    /**
     * 举报处置：RESOLVED+action（NONE/BAN_SOFTWARE/HIDE_COMMENT）或 REJECTED；
     * BAN_SOFTWARE→软件 BANNED（审计）；HIDE_COMMENT→评论 HIDDEN（计数-1）。
     */
    boolean handleReport(Long adminId, Long reportId, AdminReportHandleRequest request);

    /** 失效反馈列表 */
    Page<Map<String, Object>> feedbackPage(String status, int page, int size);

    /** 反馈标记：VERIFYING/RESOLVED（必须携带修复 revisionId，保留 original 指纹）/REJECTED */
    boolean markFeedback(Long adminId, Long feedbackId, AdminFeedbackMarkRequest request);
}
