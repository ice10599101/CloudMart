package com.cloudmart.software.vo.software;

/**
 * 创建软件结果（SW01）：只返回软件与工作快照 ID，不含任何发布版本号。
 */
public record SoftwareCreatedVO(
        Long softwareId,
        Long workingRevisionId) {
}
