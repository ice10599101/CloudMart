package com.cloudmart.software.dto.software;

import java.util.List;

/**
 * 订阅更新批量提醒（方案 9.4，P1 幂等边界）：mall-software fanout 按批次调用
 * mall-notification 内部端点；通知服务按 eventId（含 publicationSeq）+userId 稳定去重，
 * 重复投递不产生重复通知。
 */
public record SoftwareReleaseNotifyRequest(
        Long softwareId,
        Integer publicationSeq,
        Long revisionId,
        List<Long> userIds) {
}
