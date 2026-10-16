package com.cloudmart.software.vo.software;

import java.time.LocalDateTime;

/**
 * 创建软件结果（方案 7.3）：只含 ID 与版本号。
 * lockVersion 是工作快照（revision）的版本，调用方直接用作 saveDraft 的 expectedVersion。
 */
public record SoftwareCreatedVO(
        Long softwareId,
        Long workingRevisionId,
        Integer lockVersion) {
}
