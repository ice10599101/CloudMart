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
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.entity.SoftwareTag;
import com.cloudmart.software.enums.SoftwareAccessMode;
import com.cloudmart.software.enums.SoftwareArch;
import com.cloudmart.software.enums.SoftwareChannel;
import com.cloudmart.software.enums.SoftwareLicenseType;
import com.cloudmart.software.enums.SoftwarePlatform;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.enums.SoftwareSourceType;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.repository.SoftwareAccessContactMapper;
import com.cloudmart.software.repository.SoftwareCategoryMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import com.cloudmart.software.repository.SoftwareRevisionTagMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
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
    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareSourceStateMapper sourceStateMapper;
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

    // ------------------------------------------------------------------
    // SW02：局部保存（PATCH）——缺省=不修改，显式 null=清空可空标量，[]=清空集合
    // ------------------------------------------------------------------

    /** PATCH 白名单：与三端编辑字段表一一对应；未知字段直接拒绝，避免拼写错误静默丢数据 */
    private static final Set<String> PATCH_FIELDS = Set.of(
            "name", "summary", "categoryId", "iconAssetId", "descriptionHtml", "licenseType",
            "developerName", "officialUrl", "attributionType", "attributionNote",
            "systemRequirements", "languages", "accessMode", "minCommunityLevel",
            "accessContactId", "accessNote", "tagIds", "screenshots", "releases");

    /**
     * 应用局部输入。与 {@link #applyInput} 的区别：
     * <ul>
     *   <li>字段未出现在 JSON 中 → 保持数据库现值（不做任何回写）；</li>
     *   <li>标量显式 null → 清空（非空字段按空串/默认值处理，提交时再阻断）；</li>
     *   <li>集合必须用数组表达，显式 null 视为非法；数组内容整体替换该集合。</li>
     * </ul>
     */
    public void applyPatch(SoftwareRevision revision, tools.jackson.databind.JsonNode patch) {
        if (patch == null || !patch.isObject()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "局部保存请求体必须是 JSON 对象");
        }
        List<String> unknown = new ArrayList<>();
        for (String name : patch.propertyNames()) {
            if (!PATCH_FIELDS.contains(name)) {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                    "不支持的字段：" + String.join(", ", unknown));
        }

        if (patch.has("name")) {
            String value = textOrEmpty(patch.get("name"));
            if (value.length() > 80) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "软件名最长 80 字");
            }
            revision.setName(value.trim());
        }
        if (patch.has("summary")) {
            String value = textOrEmpty(patch.get("summary"));
            if (value.length() > 160) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "简介最长 160 字");
            }
            revision.setSummary(value.trim());
        }
        if (patch.has("categoryId")) {
            Long categoryId = longOrNull(patch.get("categoryId"), "categoryId");
            if (categoryId != null) {
                SoftwareCategory category = categoryMapper.selectById(categoryId);
                if (category == null || !Boolean.TRUE.equals(category.getEnabled())) {
                    throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "分类不存在或已停用");
                }
            }
            revision.setCategoryId(categoryId);
        }
        if (patch.has("iconAssetId")) {
            revision.setIconAssetId(longOrNull(patch.get("iconAssetId"), "iconAssetId"));
        }
        if (patch.has("descriptionHtml")) {
            String html = textOrEmpty(patch.get("descriptionHtml"));
            if (html.length() > MAX_DESCRIPTION_LENGTH) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                        "富文本介绍过长（≤" + MAX_DESCRIPTION_LENGTH + " 字符）");
            }
            revision.setDescriptionHtml(html);
            revision.setDescriptionText(sanitizer.extractText(html));
        }
        if (patch.has("developerName")) {
            String value = textOrNull(patch.get("developerName"));
            if (value != null && value.length() > 80) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "开发者名称最长 80 字");
            }
            revision.setDeveloperName(trimToNull(value));
        }
        if (patch.has("officialUrl")) {
            String value = textOrNull(patch.get("officialUrl"));
            if (value != null && !value.isBlank()) {
                String url = value.trim();
                if (!url.startsWith("https://") || url.length() > 2048) {
                    throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "官网链接须为 https 且 ≤2048 字符");
                }
                revision.setOfficialUrl(url);
            } else {
                revision.setOfficialUrl(null);
            }
        }
        if (patch.has("licenseType")) {
            String value = textOrNull(patch.get("licenseType"));
            revision.setLicenseType(value == null || value.isBlank()
                    ? null : parseEnum(value, SoftwareLicenseType.class, "授权类型"));
        }
        if (patch.has("attributionType")) {
            String value = textOrNull(patch.get("attributionType"));
            if (value != null && !value.isBlank() && !ATTRIBUTION_TYPES.contains(value)) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                        "来源声明须为 ORIGINAL/AUTHORIZED/OFFICIAL_PUBLIC");
            }
            revision.setAttributionType(trimToNull(value));
        }
        if (patch.has("attributionNote")) {
            String value = textOrNull(patch.get("attributionNote"));
            if (value != null && value.length() > 1000) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "来源说明最长 1000 字");
            }
            revision.setAttributionNote(trimToNull(value));
        }
        if (patch.has("systemRequirements")) {
            String value = textOrNull(patch.get("systemRequirements"));
            if (value != null && value.length() > 300) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "系统要求最长 300 字");
            }
            revision.setSystemRequirements(trimToNull(value));
        }
        if (patch.has("languages")) {
            revision.setLanguages(serializeLanguages(stringList(patch.get("languages"), "languages")));
        }
        if (patch.has("accessMode") || patch.has("minCommunityLevel") || patch.has("accessContactId")) {
            applyAccessModePatch(revision, patch);
        }
        if (patch.has("accessNote")) {
            String value = textOrNull(patch.get("accessNote"));
            if (value != null && value.length() > 500) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "查看条件说明最长 500 字");
            }
            revision.setAccessNote(trimToNull(value));
        }

        if (patch.has("tagIds")) {
            replaceTags(revision.getId(), longList(patch.get("tagIds"), "tagIds"));
        }
        if (patch.has("screenshots")) {
            replaceMedia(revision.getId(), mediaList(patch.get("screenshots")));
        }
        if (patch.has("releases")) {
            replaceReleasesAndSources(revision.getId(), releaseList(patch.get("releases")));
        }
    }

    /** 查看条件三字段联动：只改其一也按“现值 + 局部值”重新归一，避免出现半组合 */
    private void applyAccessModePatch(SoftwareRevision revision,
                                      tools.jackson.databind.JsonNode patch) {
        SoftwareAccessMode mode = revision.getAccessMode() == null
                ? SoftwareAccessMode.REGISTERED : revision.getAccessMode();
        if (patch.has("accessMode")) {
            String raw = textOrNull(patch.get("accessMode"));
            if (raw == null || raw.isBlank()) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "查看条件不能为空");
            }
            try {
                mode = SoftwareAccessMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                        "查看条件须为 REGISTERED/COMMUNITY_LEVEL/ADMIN_PAID");
            }
        }
        Integer minLevel = patch.has("minCommunityLevel")
                ? intOrNull(patch.get("minCommunityLevel"), "minCommunityLevel")
                : revision.getMinCommunityLevel();
        Long contactId = patch.has("accessContactId")
                ? longOrNull(patch.get("accessContactId"), "accessContactId")
                : revision.getAccessContactId();
        revision.setAccessMode(mode);
        switch (mode) {
            case REGISTERED -> {
                revision.setMinCommunityLevel(null);
                revision.setAccessContactId(null);
            }
            case COMMUNITY_LEVEL -> {
                revision.setMinCommunityLevel(minLevel);
                revision.setAccessContactId(null);
            }
            case ADMIN_PAID -> {
                revision.setMinCommunityLevel(null);
                revision.setAccessContactId(contactId);
            }
        }
    }

    private String textOrEmpty(tools.jackson.databind.JsonNode node) {
        return node == null || node.isNull() ? "" : node.asString();
    }

    private String textOrNull(tools.jackson.databind.JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    private Long longOrNull(tools.jackson.databind.JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            if (node.isNumber()) return node.asLong();
            String raw = node.asString();
            return raw == null || raw.isBlank() ? null : Long.valueOf(raw.trim());
        } catch (RuntimeException e) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", field + " 必须是整数或数字字符串");
        }
    }

    private Integer intOrNull(tools.jackson.databind.JsonNode node, String field) {
        Long value = longOrNull(node, field);
        if (value == null) return null;
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", field + " 超出整数范围");
        }
        return value.intValue();
    }

    private List<String> stringList(tools.jackson.databind.JsonNode node, String field) {
        if (node == null || node.isNull() || !node.isArray()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", field + " 必须是数组（清空请用 []）");
        }
        List<String> out = new ArrayList<>();
        for (tools.jackson.databind.JsonNode item : node) {
            out.add(item == null || item.isNull() ? null : item.asString());
        }
        return out;
    }

    private List<Long> longList(tools.jackson.databind.JsonNode node, String field) {
        if (node == null || node.isNull() || !node.isArray()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", field + " 必须是数组（清空请用 []）");
        }
        List<Long> out = new ArrayList<>();
        for (tools.jackson.databind.JsonNode item : node) {
            out.add(longOrNull(item, field));
        }
        return out;
    }

    private List<SoftwareSnapshotInput.MediaInput> mediaList(tools.jackson.databind.JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                    "screenshots 必须是数组（清空请用 []）");
        }
        try {
            return List.of(objectMapper.treeToValue(node, SoftwareSnapshotInput.MediaInput[].class));
        } catch (Exception e) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "screenshots 结构非法");
        }
    }

    private List<SoftwareSnapshotInput.ReleaseInput> releaseList(tools.jackson.databind.JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID",
                    "releases 必须是数组（清空请用 []）");
        }
        try {
            return List.of(objectMapper.treeToValue(node, SoftwareSnapshotInput.ReleaseInput[].class));
        } catch (Exception e) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "releases 结构非法");
        }
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
        // 2026-10-17：服务端 HTML 消毒按用户决定移除——原样保存，展示端 DOMPurify 兜底
        String descriptionHtml = input.getDescriptionHtml() == null
                ? "" : input.getDescriptionHtml().trim();
        revision.setDescriptionHtml(descriptionHtml);
        revision.setDescriptionText(sanitizer.extractText(descriptionHtml));

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

        // 4.1：草稿允许暂缺授权类型（DB 列 NULL），提交时 validateForSubmit 强制
        revision.setLicenseType(input.getLicenseType() == null || input.getLicenseType().isBlank()
                ? null
                : parseEnum(input.getLicenseType(), SoftwareLicenseType.class, "授权类型"));
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
        // SW03：稳定键不是“格式合法即可”——必须属于本软件谱系，且同一快照内唯一。
        // 先校验再删除，避免校验失败留下半份快照。
        StableKeyContext keyContext = loadStableKeyContext(revisionId);
        validateIncomingKeys(releases, keyContext);

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
            replaceSources(release, releaseInput.getSources(), keyContext);
            releaseOrder++;
        }
        enforceSingleRecommended(revisionId);
    }

    /** 本软件谱系内可复用的稳定键集合（跨修订克隆沿用同一键） */
    private record StableKeyContext(Set<String> releaseKeys,
                                    Set<String> sourceKeys,
                                    Map<String, SoftwareSourceState> disabledByFingerprint) {
    }

    private StableKeyContext loadStableKeyContext(Long revisionId) {
        SoftwareRevision revision = revisionMapper.selectById(revisionId);
        if (revision == null || revision.getSoftwareId() == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        Long softwareId = revision.getSoftwareId();
        List<Long> revisionIds = revisionMapper.selectList(
                        new LambdaQueryWrapper<SoftwareRevision>()
                                .eq(SoftwareRevision::getSoftwareId, softwareId))
                .stream().map(SoftwareRevision::getId).toList();
        if (revisionIds.isEmpty()) {
            return new StableKeyContext(Set.of(), Set.of(), Map.of());
        }
        List<SoftwareRelease> lineageReleases = releaseMapper.selectList(
                new LambdaQueryWrapper<SoftwareRelease>()
                        .in(SoftwareRelease::getRevisionId, revisionIds));
        Set<String> releaseKeys = lineageReleases.stream()
                .map(SoftwareRelease::getReleaseKey)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        List<Long> releaseIds = lineageReleases.stream().map(SoftwareRelease::getId).toList();
        if (releaseIds.isEmpty()) {
            return new StableKeyContext(releaseKeys, Set.of(), Map.of());
        }
        List<SoftwareSource> lineageSources = sourceMapper.selectList(
                new LambdaQueryWrapper<SoftwareSource>()
                        .in(SoftwareSource::getReleaseId, releaseIds));
        Set<String> sourceKeys = lineageSources.stream()
                .map(SoftwareSource::getSourceKey)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        // DISABLED 跟随稳定键；作者删除后重加同一链接时按内容指纹继承禁用状态
        Map<String, SoftwareSourceState> stateByKey = sourceStateMapper.selectList(
                        new LambdaQueryWrapper<SoftwareSourceState>()
                                .eq(SoftwareSourceState::getSoftwareId, softwareId)
                                .eq(SoftwareSourceState::getAvailability, SourceAvailability.DISABLED))
                .stream()
                .collect(Collectors.toMap(SoftwareSourceState::getSourceKey, Function.identity(), (a, b) -> a));
        Map<String, SoftwareSourceState> disabledByFingerprint = new java.util.HashMap<>();
        if (!stateByKey.isEmpty()) {
            for (SoftwareSource source : lineageSources) {
                if (source.getContentFingerprint() != null
                        && stateByKey.containsKey(source.getSourceKey())) {
                    disabledByFingerprint.putIfAbsent(source.getContentFingerprint(),
                            stateByKey.get(source.getSourceKey()));
                }
            }
        }
        return new StableKeyContext(releaseKeys, sourceKeys, disabledByFingerprint);
    }

    /** 输入中的稳定键：UUID 形态 + 本软件谱系归属 + 同快照唯一（伪造他软件键直接拒绝） */
    private void validateIncomingKeys(List<SoftwareSnapshotInput.ReleaseInput> releases,
                                      StableKeyContext context) {
        if (releases == null) {
            return;
        }
        Set<String> releaseKeys = new HashSet<>();
        Set<String> sourceKeys = new HashSet<>();
        for (SoftwareSnapshotInput.ReleaseInput release : releases) {
            String releaseKey = trimToNull(release.getReleaseKey());
            if (releaseKey != null) {
                if (!releaseKeys.add(releaseKey)) {
                    throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "同一快照内 releaseKey 不能重复");
                }
                releaseKeyInvalid(releaseKey);
                if (!context.releaseKeys().contains(releaseKey)) {
                    throw new BusinessException("SOFTWARE_LINK_INVALID",
                            "releaseKey 不属于本软件，不能引用其他软件的稳定键");
                }
            }
            if (release.getSources() == null) {
                continue;
            }
            for (SoftwareSnapshotInput.SourceInput source : release.getSources()) {
                String sourceKey = trimToNull(source.getSourceKey());
                if (sourceKey == null) {
                    continue;
                }
                if (!sourceKeys.add(sourceKey)) {
                    throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "同一快照内 sourceKey 不能重复");
                }
                if (!context.sourceKeys().contains(sourceKey)) {
                    throw new BusinessException("SOFTWARE_LINK_INVALID",
                            "sourceKey 不属于本软件，不能引用其他软件的稳定键");
                }
            }
        }
    }

    /**
     * 稳定键只做 UUID 形态前置校验；是否“属于本软件”由 {@link #validateIncomingKeys}
     * 按当前软件谱系判定——格式合法不等于服务端签发，不能只凭 UUID 形态放行。
     */
    private boolean releaseKeyInvalid(String releaseKey) {
        try {
            UUID.fromString(releaseKey.trim());
            return false;
        } catch (IllegalArgumentException e) {
            throw new BusinessException("SOFTWARE_LINK_INVALID",
                    "releaseKey 必须为 UUID 形态或留空（归属由服务端按本软件谱系校验）");
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

    private void replaceSources(SoftwareRelease release,
                                List<SoftwareSnapshotInput.SourceInput> sources,
                                StableKeyContext keyContext) {
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
            String sourceKey;
            if (sourceInput.getSourceKey() == null || sourceInput.getSourceKey().isBlank()) {
                sourceKey = UUID.randomUUID().toString();
                // SW03：同链接被作者删除后重加时继承 DISABLED，普通编辑不能自动洗掉处置
                carryDisabledState(release, sourceKey, link.contentFingerprint(), keyContext);
            } else {
                sourceKey = validateStableKey(sourceInput.getSourceKey());
            }
            source.setSourceKey(sourceKey);
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
            throw new BusinessException("SOFTWARE_LINK_INVALID", "提取码最长 32 字符");
        }
        if (sourceInput.getArchivePassword() != null && sourceInput.getArchivePassword().trim().length() > 64) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "解压密码最长 64 字符");
        }
        if (sourceInput.getSizeLabel() != null && sourceInput.getSizeLabel().length() > 40) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "大小描述最长 40 字");
        }
        if (sourceInput.getClaimedSha256() != null && !sourceInput.getClaimedSha256().isBlank()
                && !sourceInput.getClaimedSha256().matches("[0-9a-fA-F]{64}")) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "SHA-256 须为 64 位十六进制");
        }
        if (sourceInput.getNote() != null && sourceInput.getNote().length() > 200) {
            throw new BusinessException("SOFTWARE_LINK_INVALID", "来源说明最长 200 字");
        }
    }

    /** sourceKey 仅接受服务端签发的 UUID（跨快照稳定标识，不可客户端伪造） */
    /**
     * 新来源继承同一内容指纹的 DISABLED 处置：作者删除后重加同一链接不能自动洗白，
     * 仍需管理员按历史/指纹复核后才能恢复。只在同软件谱系内生效，不做全局 URL 封杀。
     */
    private void carryDisabledState(SoftwareRelease release, String newSourceKey,
                                    String fingerprint, StableKeyContext context) {
        SoftwareSourceState inherited = context.disabledByFingerprint().get(fingerprint);
        if (inherited == null) {
            return;
        }
        SoftwareRevision revision = revisionMapper.selectById(release.getRevisionId());
        if (revision == null || revision.getSoftwareId() == null) {
            return;
        }
        SoftwareSourceState copy = new SoftwareSourceState();
        copy.setSoftwareId(revision.getSoftwareId());
        copy.setSourceKey(newSourceKey);
        copy.setAvailability(SourceAvailability.DISABLED);
        copy.setCheckedFingerprint(fingerprint);
        copy.setCheckedAt(inherited.getCheckedAt());
        copy.setCheckedBy(inherited.getCheckedBy());
        copy.setReason(inherited.getReason());
        copy.setLockVersion(0);
        sourceStateMapper.insert(copy);
    }

    /** 稳定键只做 UUID 形态校验；归属已由 validateIncomingKeys 按谱系确认。 */
    private String validateStableKey(String sourceKey) {
        String trimmed = sourceKey.trim();
        try {
            UUID.fromString(trimmed);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("SOFTWARE_LINK_INVALID",
                    "sourceKey 必须为 UUID 形态或留空（归属由服务端按本软件谱系校验）");
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
            throw new BusinessException("SOFTWARE_SUBMIT_INCOMPLETE", "软件名须为 2~80 字");
        }
        // 一句话说明选填（2026-10-17 用户口径调整）：留空=对外不展示；填则 10~160 防垃圾
        String summary = revision.getSummary() == null ? "" : revision.getSummary().trim();
        if (!summary.isEmpty() && (summary.length() < 10 || summary.length() > 160)) {
            throw new BusinessException("SOFTWARE_SUBMIT_INCOMPLETE",
                    "简介须为 10~160 字（或留空不填）");
        }
        if (revision.getCategoryId() == null) {
            throw new BusinessException("SOFTWARE_SUBMIT_INCOMPLETE", "必须选择分类");
        }
        if (revision.getLicenseType() == null) {
            throw new BusinessException("SOFTWARE_SUBMIT_INCOMPLETE", "必须选择授权类型");
        }
        if (revision.getAttributionType() == null) {
            throw new BusinessException("SOFTWARE_SUBMIT_INCOMPLETE", "必须选择来源声明");
        }
        List<SoftwareRelease> releases = releaseMapper.selectList(
                new LambdaQueryWrapper<SoftwareRelease>()
                        .eq(SoftwareRelease::getRevisionId, revision.getId()));
        if (releases.isEmpty()) {
            throw new BusinessException("SOFTWARE_SUBMIT_INCOMPLETE", "至少需要一个版本及其下载来源");
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
                    throw new BusinessException("SOFTWARE_SUBMIT_INCOMPLETE",
                            "等级模式必须选择最低社区等级");
                }
                // 须为现有启用等级（6.3）：实时读等级配置
                List<Integer> enabledLevels = communitySupportFeignClient.getLevelConfigs()
                        .data().stream()
                        .map(option -> option.level())
                        .toList();
                if (!enabledLevels.contains(revision.getMinCommunityLevel())) {
                    throw new BusinessException("SOFTWARE_SUBMIT_INCOMPLETE",
                            "最低社区等级须为现有启用等级");
                }
            }
            case ADMIN_PAID -> {
                if (revision.getAccessContactId() == null) {
                    throw new BusinessException("SOFTWARE_SUBMIT_INCOMPLETE",
                            "付款模式必须选择管理员联系入口");
                }
                SoftwareAccessContact contact =
                        accessContactMapper.selectById(revision.getAccessContactId());
                if (contact == null || !Boolean.TRUE.equals(contact.getEnabled())) {
                    throw new BusinessException("SOFTWARE_SUBMIT_INCOMPLETE",
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
