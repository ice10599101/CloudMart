package com.cloudmart.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理员域文件资产行（ADMIN-04）。
 *
 * <p>仅承载 owner_type=ADMIN 的运营资产；用户资产与私有内容不出现在后台列表。
 * fileId 对外为字符串（19 位安全）；refCount 为业务引用数，被引用文件不可删除。</p>
 */
@Schema(description = "管理员域文件资产")
public record AdminFileAssetRow(
        @Schema(description = "资产 ID（对外字符串）")
        String fileId,

        @Schema(description = "原始文件名")
        String originalName,

        @Schema(description = "实际嗅探的 MIME")
        String mime,

        @Schema(description = "文件大小（字节）")
        Long sizeBytes,

        @Schema(description = "可见性：PUBLIC/PRIVATE")
        String visibility,

        @Schema(description = "状态：READY/DELETING/DELETED")
        String status,

        @Schema(description = "业务用途（后台上传必填）")
        String purpose,

        @Schema(description = "上传管理员账号 ID")
        Long createdBy,

        @Schema(description = "业务引用数（>0 时不可删除）")
        Long refCount,

        @Schema(description = "PUBLIC 资产可预览 URL；PRIVATE 为 null")
        String url,

        @Schema(description = "上传时间")
        LocalDateTime createdAt,

        @Schema(description = "T27：删除失败原因（卡在 DELETING 时的最近一次错误）")
        String deleteError,

        @Schema(description = "T27：删除重试次数")
        Integer deleteAttempts,

        @Schema(description = "T27：下次重试时间（UTC）")
        LocalDateTime nextRetryAt) {
}
