package com.cloudmart.software.service;

import com.cloudmart.software.dto.software.SoftwareReportCreateRequest;
import com.cloudmart.software.vo.software.SoftwareReportCreatedVO;

/**
 * 用户侧软件举报（方案 7.3/6.12）：登录用户举报可见软件或可见评论；
 * Idempotency-Key 纳入 software_operation，重复请求不重复创建；
 * 举报状态与被举报目标的可见性判定相互独立，处置由管理员在后台显式选择。
 */
public interface SoftwareReportService {

    /**
     * 创建举报。
     *
     * @param targetType SOFTWARE / SOFTWARE_COMMENT
     * @return reportId 与初始状态（OPEN）
     */
    SoftwareReportCreatedVO create(Long userId, Long softwareId, String idempotencyKey,
                                   SoftwareReportCreateRequest request);
}
