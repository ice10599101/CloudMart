package com.cloudmart.software.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.dto.software.SoftwareSnapshotInput;
import com.cloudmart.software.entity.SoftwareAccessContact;
import com.cloudmart.software.entity.SoftwareCategory;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareRevisionMedia;
import com.cloudmart.software.entity.SoftwareRevisionTag;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareTag;
import com.cloudmart.software.enums.SoftwareAccessMode;
import com.cloudmart.software.enums.SoftwareArch;
import com.cloudmart.software.enums.SoftwareChannel;
import com.cloudmart.software.enums.SoftwareLicenseType;
import com.cloudmart.software.enums.SoftwarePlatform;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.enums.SoftwareSourceType;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.repository.SoftwareAccessContactMapper;
import com.cloudmart.software.repository.SoftwareCategoryMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import com.cloudmart.software.repository.SoftwareRevisionTagMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareTagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 快照装配与校验（方案 4.3/6.3/6.4/7.3）：白名单字段、全量替换子项、
 * 稳定键（release_key/source_key）保留、三查看条件字段组合校验、
 * 草稿结构校验与提交完整性校验分离。
 *
 * <p>全量替换语义：子项按输入整体重建；releaseKey/sourceKey 携带则沿用
 * （更新识别/统计/禁用继承的稳定标识），null 则服务端生成 UUID，不接受客户端伪造。</p>
 */
@Component
@RequiredArgsConstructor
public class SoftwareSnapshotAssembler {

    private static final int MAX_DESCRIPTION_LENGTH = 50_000;
    private static final int MAX_CHANGELOG_LENGTH = 10_000;
    private static final Set<String> ATTRIBUTION_TYPES =
            Set.of("ORIGINAL", "AUTHORIZED", "OFFICIAL_PUBLIC");

    private final SoftwareCategoryMapper categoryMapper;
    private final SoftwareTagMapper tagMapper;
    private final SoftwareAccessContactMapper accessContactMapper;
    private final SoftwareReleaseMapper releaseMapper;
    private final SoftwareSourceMapper sourceMapper;
    private final SoftwareRevisionTagMapper revisionTagMapper;
    private final SoftwareRevisionMediaMapper revisionMediaMapper;
    private final SoftwareHtmlSanitizer sanitizer;
    private final SoftwareLinkValidator linkValidator;
    private final CommunitySupportFeignClient communitySupportFeignClient;
    private final SoftwareProperties softwareProperties;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // 草稿保存：字段白名单 + 结构校验（可保存不完整内容，见 validateForSubmit）
    // ------------------------------------------------------------------

    /** 应用完整快照输入（标量 + 子项全量替换）；同一事务内由调用方调用 */
    public void applyInput(SoftwareRevision revision, SoftwareSnapshotInput input) {
        if (input == null) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "快照输入不能为空");
        }
        applyScalars(revision, input);
        replaceTags(revision.getId(), input.getTagIds());
        replaceMedia(revision.getId(), input.getScreenshots());
        replaceReleasesAndSources(revision.getId(), input.getReleases());
    }

    private void applyScalars(SoftwareRevision revision, SoftwareSnapshotInput input) {
        if (input.getName() != null && input.getName().length() > 80) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "软件名最长 80 字");
        }
        revision.setName(input.getName() == null ? "" : input.getName().trim());

        if (input.getSummary() != null && input.getSummary().length() > 160) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "简介最长 160 字");
        }
        revision.setSummary(input.getSummary() == null ? "" : input.getSummary().trim());

        if (input.getCategoryId() != null) {
            SoftwareCategory category = categoryMapper.selectById(input.getCategoryId());
            if (category == null || !Boolean.TRUE.equals(category.getEnabled())) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "分类不存在或已停用");
            }
        }
        revision.setCategoryId(input.getCategoryId());

        revision.setIconAssetId(input.getIconAssetId());

        if (input.getDescriptionHtml() != null && input.getDescriptionHtml().length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                    "富文本介绍过长（≤" + MAX_DESCRIPTION_LENGTH + " 字符）");
        }
        String sanitizedHtml = sanitizer.sanitize(input.getDescriptionHtml());
        revision.setDescriptionHtml(sanitizedHtml);
        revision.setDescriptionText(sanitizer.extractText(sanitizedHtml));

        if (input.getDeveloperName() != null && input.getDeveloperName().length() > 80) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "开发者名称最长 80 字");
        }
        revision.setDeveloperName(trimToNull(input.getDeveloperName()));

        if (input.getOfficialUrl() != null && !input.getOfficialUrl().isBlank()) {
            String officialUrl = input.getOfficialUrl().trim();
            if (!officialUrl.startsWith("https://") || officialUrl.length() > 2048) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "官网链接须为 https 且 ≤2048 字符");
            }
            revision.setOfficialUrl(officialUrl);
        } else {
            revision.setOfficialUrl(null);
        }

        revision.setLicenseType(parseEnum(input.getLicenseType(), SoftwareLicenseType.class, "授权类型"));
        if (input.getAttributionType() != null && !input.getAttributionType().isBlank()
                && !ATTRIBUTION_TYPES.contains(input.getAttributionType())) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                    "来源声明须为 ORIGINAL/AUTHORIZED/OFFICIAL_PUBLIC");
        }
        revision.setAttributionType(trimToNull(input.getAttributionType()));
        if (input.getAttributionNote() != null && input.getAttributionNote().length() > 1000) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "来源说明最长 1000 字");
        }
        revision.setAttributionNote(trimToNull(input.getAttributionNote()));
        if (input.getSystemRequirements() != null && input.getSystemRequirements().length() > 300) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "系统要求最长 300 字");
        }
        revision.setSystemRequirements(trimToNull(input.getSystemRequirements()));
        revision.setLanguages(serializeLanguages(input.getLanguages()));

        applyAccessMode(revision, input);
        if (input.getAccessNote() != null && input.getAccessNote().length() > 500) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "查看条件说明最长 500 字");
        }
        revision.setAccessNote(trimToNull(input.getAccessNote()));
    }

    /** 三查看条件字段组合（6.3 CHECK 约束的服务端镜像；模式互斥，付款授权不提升等级） */
    private void applyAccessMode(SoftwareRevision revision, SoftwareSnapshotInput input) {
        SoftwareAccessMode mode = input.getAccessMode() == null || input.getAccessMode().isBlank()
                ? SoftwareAccessMode.REGISTERED
                : SoftwareAccessMode.valueOf(input.getAccessMode().toUpperCase(Locale.ROOT));
        revision.setAccessMode(mode);
        switch (mode) {
            case REGISTERED -> {
                revision.setMinCommunityLevel(null);
                revision.setAccessContactId(null);
            }
            case COMMUNITY_LEVEL -> {
                revision.setMinCommunityLevel(input.getMinCommunityLevel());
                revision.setAccessContactId(null);
            }
            case ADMIN_PAID -> {
                revision.setMinCommunityLevel(null);
                revision.setAccessContactId(input.getAccessContactId());
            }
        }
    }

    private String serializeLanguages(List<String> languages) {
        if (languages == null || languages.isEmpty()) {
            return null;
        }
        if (languages.size() > 20) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "语言列表最多 20 项");
        }
        for (String language : languages) {
            if (language == null || language.isBlank() || language.length() > 10) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "语言代码非法");
            }
        }
        try {
            return objectMapper.writeValueAsString(
                    languages.stream().map(String::trim).toList());
        } catch (Exception e) {
            throw new IllegalStateException("语言列表序列化失败", e);
        }
    }

    /** 标签全量替换：≤5、须为启用标签（6.7） */
    private void replaceTags(Long revisionId, List<Long> tagIds) {
        revisionTagMapper.delete(new LambdaQueryWrapper<SoftwareRevisionTag>()
                .eq(SoftwareRevisionTag::getRevisionId, revisionId));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        if (tagIds.size() > 5) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "标签最多 5 个");
        }
        List<SoftwareTag> tags = tagMapper.selectBatchIds(tagIds.stream().distinct().toList());
        if (tags.size() < (int) tagIds.stream().distinct().count()
                || tags.stream().anyMatch(tag -> !Boolean.TRUE.equals(tag.getEnabled()))) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "标签不存在或已停用");
        }
        for (Long tagId : tagIds.stream().distinct().toList()) {
            SoftwareRevisionTag relation = new SoftwareRevisionTag();
            relation.setRevisionId(revisionId);
            relation.setTagId(tagId);
            revisionTagMapper.insert(relation);
        }
    }

    /** 截图全量替换：≤8（软件属性 screenshot-max-count）；正文媒体 P1 扩展同通道 */
    private void replaceMedia(Long revisionId, List<SoftwareSnapshotInput.MediaInput> screenshots) {
        revisionMediaMapper.delete(new LambdaQueryWrapper<SoftwareRevisionMedia>()
                .eq(SoftwareRevisionMedia::getRevisionId, revisionId));
        if (screenshots == null || screenshots.isEmpty()) {
            return;
        }
        if (screenshots.size() > softwareProperties.getScreenshotMaxCount()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                    "截图最多 " + softwareProperties.getScreenshotMaxCount() + " 张");
        }
        int order = 0;
        for (SoftwareSnapshotInput.MediaInput media : screenshots) {
            if (media.getAssetId() == null || media.getAssetId() <= 0) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "截图素材 ID 非法");
            }
            SoftwareRevisionMedia row = new SoftwareRevisionMedia();
            row.setRevisionId(revisionId);
            row.setAssetId(media.getAssetId());
            row.setKind("SCREENSHOT");
            row.setDisplayOrder(media.getDisplayOrder() != null ? media.getDisplayOrder() : order);
            row.setAltText(trimToNull(media.getAltText()));
            revisionMediaMapper.insert(row);
            order++;
        }
    }

    // ------------------------------------------------------------------
    // 版本与来源：全量替换、稳定键保留、结构约束（6.4/6.5/4.3）
    // ------------------------------------------------------------------

    private void replaceReleasesAndSources(Long revisionId,
                                           List<SoftwareSnapshotInput.ReleaseInput> releases) {
        List<SoftwareRelease> existing = releaseMapper.selectList(
                new LambdaQueryWrapper<SoftwareRelease>()
                        .eq(SoftwareRelease::getRevisionId, revisionId));
        for (SoftwareRelease stale : existing) {
            sourceMapper.delete(new LambdaQueryWrapper<SoftwareSource>()
                    .eq(SoftwareSource::getReleaseId, stale.getId()));
            releaseMapper.deleteById(stale.getId());
        }
        if (releases == null || releases.isEmpty()) {
            return;
        }
        if (releases.size() > softwareProperties.getRevisionsMaxReleases()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                    "版本最多 " + softwareProperties.getRevisionsMaxReleases() + " 个");
        }
        Set<String> versionChannelKeys = new HashSet<>();
        int releaseOrder = 0;
        for (SoftwareSnapshotInput.ReleaseInput releaseInput : releases) {
            SoftwareRelease release = new SoftwareRelease();
            release.setRevisionId(revisionId);
            release.setReleaseKey(releaseInput.getReleaseKey() == null || releaseKeyInvalid(releaseInput.getReleaseKey())
                    ? UUID.randomUUID().toString() : releaseInput.getReleaseKey().trim());
            release.setVersionName(validateVersionName(releaseInput.getVersionName()));
            release.setChannel(parseChannel(releaseInput.getChannel()));
            release.setReleaseOrder(releaseInput.getDisplayOrder() != null ? releaseInput.getDisplayOrder() : releaseOrder);
            release.setReleaseDate(releaseInput.getReleaseDate());
            if (releaseInput.getChangelog() != null && releaseInput.getChangelog().length() > MAX_CHANGELOG_LENGTH) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "更新日志最长 10000 字符");
            }
            release.setChangelog(trimToNull(releaseInput.getChangelog()));
            release.setRecommended(Boolean.TRUE.equals(releaseInput.getRecommended()));
            if (!versionChannelKeys.add(release.getVersionName() + "::" + release.getChannel())) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                        "同渠道下版本号不能重复: " + release.getVersionName());
            }
            releaseMapper.insert(release);
            replaceSources(release, releaseInput.getSources());
            releaseOrder++;
        }
        enforceSingleRecommended(revisionId);
    }

    /** releaseKey 仅接受 UUID 形态或全新生成——稳定键由服务端生成，客户端不可伪造（6.4） */
    private boolean releaseKeyInvalid(String releaseKey) {
        try {
            UUID.fromString(releaseKey.trim());
            return false;
        } catch (IllegalArgumentException e) {
            throw new BusinessException("SOFTWARE_SOURCE_INVALID",
                    "releaseKey 必须为服务端签发的 UUID 或留空");
        }
    }

    private String validateVersionName(String versionName) {
        if (versionName == null || versionName.isBlank()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "版本号不能为空（1~40 字）");
        }
        String trimmed = versionName.trim();
        if (trimmed.length() > 40) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "版本号最长 40 字");
        }
        return trimmed;
    }

    private SoftwareChannel parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return SoftwareChannel.STABLE;
        }
        try {
            return SoftwareChannel.valueOf(channel.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "渠道须为 STABLE/BETA");
        }
    }

    /** 每渠道最多一个推荐版本（6.4）：保存即校验，提交时复核 */
    private void enforceSingleRecommended(Long revisionId) {
        List<SoftwareRelease> releases = releaseMapper.selectList(
                new LambdaQueryWrapper<SoftwareRelease>()
                        .eq(SoftwareRelease::getRevisionId, revisionId)
                        .eq(SoftwareRelease::getRecommended, true));
        Map<SoftwareChannel, Long> recommendedByChannel = releases.stream()
                .collect(Collectors.groupingBy(SoftwareRelease::getChannel, Collectors.counting()));
        if (recommendedByChannel.values().stream().anyMatch(count -> count > 1)) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "每个渠道最多一个推荐版本");
        }
    }

    private void replaceSources(SoftwareRelease release, List<SoftwareSnapshotInput.SourceInput> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                    "版本 " + release.getVersionName() + " 至少需要一个下载来源");
        }
        if (sources.size() > softwareProperties.getReleaseMaxSources()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                    "每版本来源最多 " + softwareProperties.getReleaseMaxSources() + " 个");
        }
        Set<String> targetKeys = new HashSet<>();
        int order = 0;
        for (SoftwareSnapshotInput.SourceInput sourceInput : sources) {
            SoftwareSourceType sourceType = parseEnum(sourceInput.getSourceType(),
                    SoftwareSourceType.class, "来源类型");
            if (sourceInput.getLabel() == null || sourceInput.getLabel().isBlank()
                    || sourceInput.getLabel().length() > 80) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "来源展示名须为 1~80 字");
            }
            SoftwarePlatform platform = parseEnum(sourceInput.getPlatform(), SoftwarePlatform.class, "平台");
            SoftwareArch arch = parseEnum(sourceInput.getArch(), SoftwareArch.class, "架构");
            SoftwareLinkValidator.ValidatedLink link = linkValidator.validate(
                    sourceType, sourceInput.getProvider(), sourceInput.getUrl(),
                    sourceInput.getExtractionCode(), sourceInput.getArchivePassword());
            validateOptionalFields(sourceInput);
            if (!targetKeys.add(platform + "::" + arch + "::" + link.urlHash())) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                        "同版本内同平台架构的来源不能重复");
            }
            SoftwareSource source = new SoftwareSource();
            source.setReleaseId(release.getId());
            source.setSourceKey(sourceInput.getSourceKey() == null
                    ? UUID.randomUUID().toString() : validateStableKey(sourceInput.getSourceKey()));
            source.setSourceType(sourceType);
            // provider 由服务端域名映射/链接类型裁定，不信任客户端标签（5.1）
            source.setProvider(link.provider());
            source.setPlatform(platform);
            source.setArch(arch);
            source.setLabel(sourceInput.getLabel().trim());
            source.setUrl(link.url());
            source.setUrlHash(link.urlHash());
            source.setContentFingerprint(link.contentFingerprint());
            source.setExtractionCode(link.extractionCode());
            source.setArchivePassword(link.archivePassword());
            source.setSizeLabel(trimToNull(sourceInput.getSizeLabel()));
            source.setClaimedSha256(trimToNull(sourceInput.getClaimedSha256()));
            source.setNote(trimToNull(sourceInput.getNote()));
            source.setDisplayOrder(sourceInput.getDisplayOrder() != null ? sourceInput.getDisplayOrder() : order);
            sourceMapper.insert(source);
            order++;
        }
    }

    private void validateOptionalFields(SoftwareSnapshotInput.SourceInput sourceInput) {
        if (sourceInput.getExtractionCode() != null && sourceInput.getExtractionCode().trim().length() > 32) {
            throw new BusinessException("SOFTWARE_SOURCE_INVALID", "提取码最长 32 字符");
        }
        if (sourceInput.getArchivePassword() != null && sourceInput.getArchivePassword().trim().length() > 64) {
            throw new BusinessException("SOFTWARE_SOURCE_INVALID", "解压密码最长 64 字符");
        }
        if (sourceInput.getSizeLabel() != null && sourceInput.getSizeLabel().length() > 40) {
            throw new BusinessException("SOFTWARE_SOURCE_INVALID", "大小描述最长 40 字");
        }
        if (sourceInput.getClaimedSha256() != null && !sourceInput.getClaimedSha256().isBlank()
                && !sourceInput.getClaimedSha256().matches("[0-9a-fA-F]{64}")) {
            throw new BusinessException("SOFTWARE_SOURCE_INVALID", "SHA-256 须为 64 位十六进制");
        }
        if (sourceInput.getNote() != null && sourceInput.getNote().length() > 200) {
            throw new BusinessException("SOFTWARE_SOURCE_INVALID", "来源说明最长 200 字");
        }
    }

    /** sourceKey 仅接受服务端签发的 UUID（跨快照稳定标识，不可客户端伪造） */
    private String validateStableKey(String sourceKey) {
        String trimmed = sourceKey.trim();
        try {
            UUID.fromString(trimmed);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("SOFTWARE_SOURCE_INVALID",
                    "sourceKey 必须为服务端签发的 UUID 或留空");
        }
        return trimmed;
    }

    // ------------------------------------------------------------------
    // 提交完整性校验（提交冻结完整快照；草稿允许暂缺）
    // ------------------------------------------------------------------

    /** 提交前完整性校验：调用方持有 PENDING 冻结前的最新快照行 */
    public void validateForSubmit(SoftwareRevision revision) {
        if (revision.getName() == null || revision.getName().length() < 2
                || revision.getName().length() > 80) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INCOMPLETE", "软件名须为 2~80 字");
        }
        if (revision.getSummary() == null || revision.getSummary().length() < 10
                || revision.getSummary().length() > 160) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INCOMPLETE", "简介须为 10~160 字");
        }
        if (revision.getCategoryId() == null) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INCOMPLETE", "必须选择分类");
        }
        if (revision.getLicenseType() == null) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INCOMPLETE", "必须选择授权类型");
        }
        if (revision.getAttributionType() == null) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INCOMPLETE", "必须选择来源声明");
        }
        List<SoftwareRelease> releases = releaseMapper.selectList(
                new LambdaQueryWrapper<SoftwareRelease>()
                        .eq(SoftwareRelease::getRevisionId, revision.getId()));
        if (releases.isEmpty()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INCOMPLETE", "至少需要一个版本及其下载来源");
        }
        // 每版本至少一个来源已由保存时强制；推荐版本约束保存时校验，此处复核
        enforceSingleRecommended(revision.getId());
        validateAccessModeForSubmit(revision);
    }

    /** 提交时查看条件强校验（草稿可暂缺，提交必须可执行）：等级须为启用等级、入口须启用 */
    private void validateAccessModeForSubmit(SoftwareRevision revision) {
        switch (revision.getAccessMode()) {
            case COMMUNITY_LEVEL -> {
                if (revision.getMinCommunityLevel() == null || revision.getMinCommunityLevel() <= 0) {
                    throw new BusinessException("SOFTWARE_SNAPSHOT_INCOMPLETE",
                            "等级模式必须选择最低社区等级");
                }
                // 须为现有启用等级（6.3）：实时读等级配置
                List<Integer> enabledLevels = communitySupportFeignClient.getLevelConfigs()
                        .data().stream()
                        .map(option -> option.level())
                        .toList();
                if (!enabledLevels.contains(revision.getMinCommunityLevel())) {
                    throw new BusinessException("SOFTWARE_SNAPSHOT_INCOMPLETE",
                            "最低社区等级须为现有启用等级");
                }
            }
            case ADMIN_PAID -> {
                if (revision.getAccessContactId() == null) {
                    throw new BusinessException("SOFTWARE_SNAPSHOT_INCOMPLETE",
                            "付款模式必须选择管理员联系入口");
                }
                SoftwareAccessContact contact =
                        accessContactMapper.selectById(revision.getAccessContactId());
                if (contact == null || !Boolean.TRUE.equals(contact.getEnabled())) {
                    throw new BusinessException("SOFTWARE_SNAPSHOT_INCOMPLETE",
                            "联系入口不存在或已停用（A43：停用后新提交付款模式失败）");
                }
            }
            case REGISTERED -> {
                // 无附加条件
            }
        }
    }

    // ------------------------------------------------------------------
    // 快照克隆（4.1.5 已发布修改：复制当前线上元信息/版本/来源，旧快照不变）
    // ------------------------------------------------------------------

    /** 从源快照深拷贝出 DRAFT 工作副本（新 revisionId/行 ID，保留 release_key/source_key） */
    public SoftwareRevision cloneInto(SoftwareRevision source, Long softwareId, Long editorId,
                                      int nextRevisionNo) {
        SoftwareRevision clone = new SoftwareRevision();
        clone.setSoftwareId(softwareId);
        clone.setEditorId(editorId);
        clone.setRevisionNo(nextRevisionNo);
        clone.setReviewStatus(SoftwareReviewStatus.DRAFT);
        clone.setSubmitSeq(0);
        clone.setLockVersion(0);
        clone.setName(source.getName());
        clone.setSummary(source.getSummary());
        clone.setCategoryId(source.getCategoryId());
        clone.setIconAssetId(source.getIconAssetId());
        clone.setDescriptionHtml(source.getDescriptionHtml());
        clone.setDescriptionText(source.getDescriptionText());
        clone.setDeveloperName(source.getDeveloperName());
        clone.setOfficialUrl(source.getOfficialUrl());
        clone.setLicenseType(source.getLicenseType());
        clone.setAttributionType(source.getAttributionType());
        clone.setAttributionNote(source.getAttributionNote());
        clone.setSystemRequirements(source.getSystemRequirements());
        clone.setLanguages(source.getLanguages());
        clone.setAccessMode(source.getAccessMode());
        clone.setMinCommunityLevel(source.getMinCommunityLevel());
        clone.setAccessContactId(source.getAccessContactId());
        clone.setAccessNote(source.getAccessNote());
        return clone;
    }

    /** 克隆子项：release/source 行 ID 全新，稳定键保留（4.3） */
    public void cloneChildren(Long sourceRevisionId, Long targetRevisionId) {
        for (SoftwareRelease release : releaseMapper.selectList(
                new LambdaQueryWrapper<SoftwareRelease>()
                        .eq(SoftwareRelease::getRevisionId, sourceRevisionId))) {
            SoftwareRelease copy = new SoftwareRelease();
            copy.setRevisionId(targetRevisionId);
            copy.setReleaseKey(release.getReleaseKey());
            copy.setVersionName(release.getVersionName());
            copy.setChannel(release.getChannel());
            copy.setReleaseOrder(release.getReleaseOrder());
            copy.setReleaseDate(release.getReleaseDate());
            copy.setChangelog(release.getChangelog());
            copy.setRecommended(release.getRecommended());
            releaseMapper.insert(copy);
            for (SoftwareSource source : sourceMapper.selectList(
                    new LambdaQueryWrapper<SoftwareSource>()
                            .eq(SoftwareSource::getReleaseId, release.getId())
                            .orderByAsc(SoftwareSource::getDisplayOrder))) {
                SoftwareSource sourceCopy = new SoftwareSource();
                sourceCopy.setReleaseId(copy.getId());
                sourceCopy.setSourceKey(source.getSourceKey());
                sourceCopy.setSourceType(source.getSourceType());
                sourceCopy.setProvider(source.getProvider());
                sourceCopy.setPlatform(source.getPlatform());
                sourceCopy.setArch(source.getArch());
                sourceCopy.setLabel(source.getLabel());
                sourceCopy.setUrl(source.getUrl());
                sourceCopy.setUrlHash(source.getUrlHash());
                sourceCopy.setContentFingerprint(source.getContentFingerprint());
                sourceCopy.setExtractionCode(source.getExtractionCode());
                sourceCopy.setArchivePassword(source.getArchivePassword());
                sourceCopy.setSizeLabel(source.getSizeLabel());
                sourceCopy.setClaimedSha256(source.getClaimedSha256());
                sourceCopy.setNote(source.getNote());
                sourceCopy.setDisplayOrder(source.getDisplayOrder());
                sourceMapper.insert(sourceCopy);
            }
        }
        for (SoftwareRevisionTag relation : revisionTagMapper.selectList(
                new LambdaQueryWrapper<SoftwareRevisionTag>()
                        .eq(SoftwareRevisionTag::getRevisionId, sourceRevisionId))) {
            SoftwareRevisionTag copy = new SoftwareRevisionTag();
            copy.setRevisionId(targetRevisionId);
            copy.setTagId(relation.getTagId());
            revisionTagMapper.insert(copy);
        }
        for (SoftwareRevisionMedia media : revisionMediaMapper.selectList(
                new LambdaQueryWrapper<SoftwareRevisionMedia>()
                        .eq(SoftwareRevisionMedia::getRevisionId, sourceRevisionId))) {
            SoftwareRevisionMedia copy = new SoftwareRevisionMedia();
            copy.setRevisionId(targetRevisionId);
            copy.setAssetId(media.getAssetId());
            copy.setKind(media.getKind());
            copy.setDisplayOrder(media.getDisplayOrder());
            copy.setAltText(media.getAltText());
            revisionMediaMapper.insert(copy);
        }
    }

    // ------------------------------------------------------------------

    private <E extends Enum<E>> E parseEnum(String raw, Class<E> type, String label) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", label + "不能为空");
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", label + "取值非法: " + raw);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
