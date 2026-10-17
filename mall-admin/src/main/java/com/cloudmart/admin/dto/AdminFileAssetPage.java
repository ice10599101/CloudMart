package com.cloudmart.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 管理员域文件资产分页（ADMIN-04）。 */
@Schema(description = "管理员域文件资产分页")
public record AdminFileAssetPage(
        @Schema(description = "当前页资产")
        List<AdminFileAssetRow> items,

        @Schema(description = "同过滤条件总数")
        long total,

        @Schema(description = "页码（≥1）")
        int page,

        @Schema(description = "每页数量（1~100）")
        int size) {
}
