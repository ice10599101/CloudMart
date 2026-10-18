package com.cloudmart.software.vo.software;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作者编辑器完整工作数据（方案 7.3 GET /software/{id}/editor）：
 * 仅归属人/管理员可读（独立鉴权），含链接、提取码、解压密码与审核原因——
 * 与公开 DTO 是两套结构，禁止复用公开 VO 偷懒带出秘密字段。
 */
public record SoftwareEditorVO(
        Long softwareId,
        Long revisionId,
        String reviewStatus,
        String name,
        String summary,
        Long categoryId,
        Long iconAssetId,
        String descriptionHtml,
        String licenseType,
        String developerName,
        String officialUrl,
        String attributionType,
        String attributionNote,
        String systemRequirements,
        List<String> languages,
        String accessMode,
        Integer minCommunityLevel,
        Long accessContactId,
        String accessNote,
        List<Long> tagIds,
        List<MediaVO> screenshots,
        List<ReleaseVO> releases,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        Long reviewerId,
        String reviewReason,
        /** 主体状态（作者整理视角需要 lifecycle：UNLISTED/BANNED 等） */
        String lifecycle,
        Long publishedRevisionId,
        Long workingRevisionId) {

    /** 编辑器截图（素材 ID，非渲染 URL） */
    public record MediaVO(Long assetId, String altText, Integer displayOrder) {
    }

    /** 编辑器版本（含来源完整秘密字段） */
    public record ReleaseVO(Long releaseId, String releaseKey, String versionName, String channel,
                            java.time.LocalDate releaseDate, String changelog, Boolean recommended,
                            Integer displayOrder, List<SourceVO> sources) {
    }

    /** 编辑器来源：链接/提取码/解压密码仅此接口与 resolve 授权响应可返回 */
    public record SourceVO(Long sourceId, String sourceKey, String sourceType, String provider,
                           String platform, String arch, String label, String url,
                           String extractionCode, String archivePassword, String sizeLabel,
                           String claimedSha256, String note, Integer displayOrder) {
    }
}
