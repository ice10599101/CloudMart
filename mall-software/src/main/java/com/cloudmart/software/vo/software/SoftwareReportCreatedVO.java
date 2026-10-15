package com.cloudmart.software.vo.software;

/**
 * 软件举报创建结果（方案 6.12）：只返回 ID/状态，不含证据与处置信息。
 */
public record SoftwareReportCreatedVO(Long reportId, String status) {
}
