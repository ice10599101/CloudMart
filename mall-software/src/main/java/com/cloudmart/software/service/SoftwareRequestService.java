package com.cloudmart.software.service;

import com.cloudmart.software.dto.software.SoftwareRequestCreateInput;

import java.util.List;
import java.util.Map;

/**
 * UX06：找软件需求（SOFTWARE_DISCOVERY）。
 * 独立轻表无奖励系统；审核走软件域审核流；状态显式映射（OPEN/ANSWERED/CLOSED）。
 * "已找到"目标必须为当前可见软件；下架后前台标注可解释，不给有效获取承诺。
 */
public interface SoftwareRequestService {

    /** 提交需求（登录；从搜索无结果进入时保留关键词） */
    Long create(Long userId, SoftwareRequestCreateInput input);

    /** "大家在找什么"：APPROVED 且 OPEN/ANSWERED，≤3 条真实内容（无内容返回空，不造假活跃） */
    List<Map<String, Object>> publicFeed();

    /** 我的需求（含审核状态；REJECTED 可见原因） */
    List<Map<String, Object>> mine(Long userId);

    /** 发起人关闭 */
    boolean close(Long userId, Long requestId);

    /** 管理分页（auditStatus/status 筛选） */
    Map<String, Object> adminPage(String auditStatus, String status, int page, int size);

    /** 管理审核（PENDING→APPROVED/REJECTED；必须意见当 REJECTED） */
    boolean audit(Long adminUserId, Long requestId, boolean approved,
                  String reason, String idempotencyKey);

    /** 管理标记"已找到"（目标必须可见；ANSWERED） */
    boolean markMatched(Long adminUserId, Long requestId, Long softwareId,
                        String reason, String idempotencyKey);
}
