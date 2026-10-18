package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.entity.SoftwareAccessRequest;

import java.util.Map;

/**
 * 受限来源访问申请（SF04）：申请 → 授权员人工核对 → 批准（同事务调用既有授权服务并回写
 * grant_id）→ 用户 resolve 成功。不变量：每用户每软件最多一条 PENDING；审批不替换申请人；
 * 申请存在不等于获得访问权；撤销授权保留申请与审计事实。
 */
public interface SoftwareAccessRequestService {

    /** 提交申请（幂等；软件需为 ADMIN_PAID 访问模式；已有 PENDING 返回既有记录） */
    Map<String, Object> submit(Long userId, Long softwareId, String idempotencyKey,
                               String message, String evidenceAssetIds);

    /** 我的申请列表（最新在前） */
    Page<Map<String, Object>> mine(Long userId, int page, int size);

    /** 撤回自己的 PENDING 申请 */
    boolean cancel(Long userId, Long applicationId, String idempotencyKey);

    /** 后台分页（状态筛选；软件名由代理层补齐） */
    Page<Map<String, Object>> adminPage(String status, int page, int size);

    /** 后台决定：批准同事务调用既有授权服务并回写 grant_id；拒绝写可公开原因 */
    boolean decide(Long adminId, Long applicationId, boolean approve,
                   String reason, String idempotencyKey);
}
