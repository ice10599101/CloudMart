package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.entity.SoftwarePublisherApplication;

import java.util.Map;

/**
 * 发布者申请（SF03）：申请 → 后台审核 → 通过同事务授予资格并写 targetUserId 审计。
 * 不变量：每用户最多一条 PENDING（DB 生成列唯一键）；已授权者不可申请；
 * 决定与资格变更/审计同事务；结果经 Outbox 通知用户。
 */
public interface SoftwarePublisherApplicationService {

    /** 提交申请（幂等：同键返回原记录；已有 PENDING 冲突提示；已授权拒绝） */
    Map<String, Object> submit(Long userId, String idempotencyKey, String intro,
                               String intendedCategories, String evidenceAssetIds);

    /** 本人申请列表（最新在前） */
    Page<Map<String, Object>> mine(Long userId, int page, int size);

    /** 撤回自己的 PENDING 申请 */
    boolean cancel(Long userId, Long applicationId, String idempotencyKey);

    /** 后台分页（按状态筛选） */
    Page<Map<String, Object>> adminPage(String status, int page, int size);

    /** 后台决定：approve=true 授予资格（复用统一资格动作+审计），false 拒绝并写可公开原因 */
    boolean decide(Long adminId, Long applicationId, boolean approve,
                   String reason, String idempotencyKey);
}
