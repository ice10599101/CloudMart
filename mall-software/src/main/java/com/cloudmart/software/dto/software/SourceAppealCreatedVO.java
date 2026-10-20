package com.cloudmart.software.dto.software;

/**
 * SF08 补批：申诉提交结果（幂等重放只含 ID/状态）。
 */
public record SourceAppealCreatedVO(Long appealId, String status) {
}
