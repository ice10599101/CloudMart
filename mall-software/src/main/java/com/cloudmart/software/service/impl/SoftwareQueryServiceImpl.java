package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SoftwareListQuery;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareAccessContact;
import com.cloudmart.software.entity.SoftwareCategory;
import com.cloudmart.software.entity.SoftwareFavorite;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareRevisionMedia;
import com.cloudmart.software.entity.SoftwareRevisionTag;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.entity.SoftwareTag;
import com.cloudmart.software.enums.SoftwareAccessMode;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.feign.UserSupportFeignClient;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareAccessContactMapper;
import com.cloudmart.software.repository.SoftwareCategoryMapper;
import com.cloudmart.software.repository.SoftwareFavoriteMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareQueryMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import com.cloudmart.software.repository.SoftwareRevisionTagMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import com.cloudmart.software.repository.SoftwareTagMapper;
import com.cloudmart.software.vo.software.SoftwareAccessPolicyVO;
import com.cloudmart.software.vo.software.SoftwareCardVO;
import com.cloudmart.software.vo.software.SoftwareCategoryVO;
import com.cloudmart.software.vo.software.SoftwareDetailVO;
import com.cloudmart.software.vo.software.SoftwareReleaseVO;
import com.cloudmart.software.vo.software.SoftwareSourceSummaryVO;
import com.cloudmart.software.vo.software.SoftwareTagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 公开只读查询实现（方案 7.2/3.3）：可见性一律经 SoftwareAccessPolicy，
 * 草稿/待审/下架/封禁/删除不会从列表/详情/版本/来源摘要泄漏；
 * 来源摘要按 4.3 指纹匹配应用核实结论（DISABLED 无条件，其余指纹不匹配回落 UNKNOWN）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareQueryServiceImpl implements com.cloudmart.software.service.SoftwareQueryService {

    private final SoftwareAccessPolicy accessPolicy;
    private final SoftwareMapper softwareMapper;
    private final SoftwareCategoryMapper categoryMapper;
    private final SoftwareQueryMapper queryMapper;
    private final SoftwareReleaseMapper releaseMapper;
    private final SoftwareSourceMapper sourceMapper;
    private final SoftwareSourceStateMapper sourceStateMapper;
    private final SoftwareRevisionTagMapper revisionTagMapper;
    private final SoftwareTagMapper tagMapper;
    private final SoftwareRevisionMediaMapper revisionMediaMapper;
    private final SoftwareFavoriteMapper favoriteMapper;
    private final SoftwareAccessContactMapper accessContactMapper;
    private final UserSupportFeignClient userSupportFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public Page<SoftwareCardVO> pageCards(SoftwareListQuery query) {
        Page<SoftwareQueryMapper.CardRow> rows =
                queryMapper.pageCards(Page.of(query.safePage(), query.safeSize()), query);

        // 批量加载软件指针（行 ID 即软件 ID），再按 published revision 批查平台去重集
        List<Long> softwareIds = rows.getRecords().stream()
                .map(SoftwareQueryMapper.CardRow::id).toList();
        Map<Long, Long> revisionIdBySoftware = softwareIds.isEmpty() ? Map.of()
                : softwareMapper.selectBatchIds(softwareIds).stream()
                        .filter(software -> software.getPublishedRevisionId() != null)
                        .collect(Collectors.toMap(Software::getId,
                                Software::getPublishedRevisionId));
        List<Long> revisionIds = List.copyOf(revisionIdBySoftware.values());
        Map<Long, List<String>> platformsByRevision = revisionIds.isEmpty() ? Map.of()
                : queryMapper.platformsOfRevisions(revisionIds).stream()
                        .collect(Collectors.groupingBy(
                                SoftwareQueryMapper.PlatformRow::revisionId,
                                Collectors.mapping(
                                        SoftwareQueryMapper.PlatformRow::platform,
                                        Collectors.toList())));

        Page<SoftwareCardVO> result = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
        result.setRecords(rows.getRecords().stream()
                .map(row -> new SoftwareCardVO(
                        row.id(), row.name(), row.summary(), row.iconAssetId(),
                        row.categoryId(), row.categoryName(), row.licenseType(),
                        platformsByRevision.getOrDefault(
                                revisionIdBySoftware.get(row.id()), List.of()),
                        row.lastReleaseAt(), row.favoriteCount(), row.commentCount(),
                        row.ratingCount(),
                        ratingAvg(row.ratingCount(), row.ratingSum())))
                .toList());
        return result;
    }

    @Override
    public SoftwareDetailVO detail(Long softwareId, Long viewerId) {
        Software software = accessPolicy.visibleSoftware(softwareId);
        SoftwareRevision revision = software == null ? null
                : accessPolicy.visiblePublishedRevision(softwareId);
        if (software == null || revision == null) {
            // 通用不可见提示：不泄露下架/封禁/草稿详情（7.6）
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }

        String categoryName = revision.getCategoryId() == null ? null
                : java.util.Optional.ofNullable(categoryMapper.selectById(revision.getCategoryId()))
                        .map(SoftwareCategory::getName).orElse(null);
        List<SoftwareTagVO> tags = loadTags(revision.getId());
        List<SoftwareDetailVO.ScreenshotVO> screenshots =
                revisionMediaMapper.selectList(new LambdaQueryWrapper<SoftwareRevisionMedia>()
                                .eq(SoftwareRevisionMedia::getRevisionId, revision.getId())
                                .eq(SoftwareRevisionMedia::getKind, "SCREENSHOT")
                                .orderByAsc(SoftwareRevisionMedia::getDisplayOrder))
                        .stream()
                        .map(media -> new SoftwareDetailVO.ScreenshotVO(
                                media.getAssetId(), media.getDisplayOrder(), media.getAltText()))
                        .toList();

        // 分享者展示资料：适配失败按 9.6 降级为通用头像（展示场景不阻断详情）
        String ownerNickname = null;
        String ownerAvatar = null;
        try {
            var profileResponse = userSupportFeignClient.profiles(
                    Map.of("userIds", List.of(software.getOwnerId())));
            if (profileResponse.data() != null && !profileResponse.data().isEmpty()) {
                var profile = profileResponse.data().get(0);
                ownerNickname = profile.nickname();
                ownerAvatar = profile.avatar();
            }
        } catch (BusinessException e) {
            log.warn("分享者资料降级 softwareId={} owner={}: {}",
                    softwareId, software.getOwnerId(), e.getMessage());
        }

        Boolean isFavorited = viewerId == null ? null
                : favoriteMapper.selectCount(new LambdaQueryWrapper<SoftwareFavorite>()
                        .eq(SoftwareFavorite::getSoftwareId, softwareId)
                        .eq(SoftwareFavorite::getUserId, viewerId)) > 0;

        return new SoftwareDetailVO(softwareId, revision.getName(), revision.getSummary(),
                revision.getDescriptionHtml(), revision.getIconAssetId(),
                revision.getCategoryId(), categoryName, tags, screenshots,
                revision.getDeveloperName(), revision.getOfficialUrl(),
                revision.getLicenseType() == null ? null : revision.getLicenseType().name(),
                revision.getAttributionType(), revision.getAttributionNote(),
                revision.getSystemRequirements(), parseLanguages(revision.getLanguages()),
                software.getPublishedAt(), software.getLastReleaseAt(),
                software.getCommentCount(), software.getFavoriteCount(),
                software.getRatingCount(), ratingAvg(software.getRatingCount(),
                software.getRatingSum()),
                ownerNickname, ownerAvatar, isFavorited);
    }

    private List<SoftwareTagVO> loadTags(Long revisionId) {
        List<Long> tagIds = revisionTagMapper.selectList(
                        new LambdaQueryWrapper<SoftwareRevisionTag>()
                                .eq(SoftwareRevisionTag::getRevisionId, revisionId))
                .stream().map(SoftwareRevisionTag::getTagId).toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectBatchIds(tagIds).stream()
                .filter(tag -> Boolean.TRUE.equals(tag.getEnabled()))
                .map(tag -> new SoftwareTagVO(tag.getId(), tag.getName()))
                .toList();
    }

    /** languages 为服务端写入的 JSON 数组；解析失败按空展示，不阻断详情 */
    private List<String> parseLanguages(String languagesJson) {
        if (languagesJson == null || languagesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(languagesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("languages 解析失败按空处理: {}", e.getMessage());
            return List.of();
        }
    }

    private Double ratingAvg(Integer ratingCount, Integer ratingSum) {
        if (ratingCount == null || ratingCount <= 0 || ratingSum == null) {
            return null;
        }
        // 均分保留一位小数（4.5）
        return Math.round(ratingSum * 10.0 / ratingCount) / 10.0;
    }

    @Override
    public List<SoftwareCategoryVO> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<SoftwareCategory>()
                        .eq(SoftwareCategory::getEnabled, true)
                        .orderByAsc(SoftwareCategory::getSortOrder))
                .stream()
                .map(category -> new SoftwareCategoryVO(category.getId(), category.getName(),
                        category.getSlug(), category.getIcon(), category.getSortOrder(),
                        category.getEnabled()))
                .toList();
    }

    @Override
    public Page<SoftwareTagVO> pageTags(String keyword, int page, int size) {
        Page<SoftwareTag> tagPage = tagMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<SoftwareTag>()
                        .eq(SoftwareTag::getEnabled, true)
                        .like(keyword != null && !keyword.isBlank(), SoftwareTag::getName, keyword)
                        .orderByAsc(SoftwareTag::getId));
        Page<SoftwareTagVO> result = new Page<>(tagPage.getCurrent(), tagPage.getSize(),
                tagPage.getTotal());
        result.setRecords(tagPage.getRecords().stream()
                .map(tag -> new SoftwareTagVO(tag.getId(), tag.getName()))
                .toList());
        return result;
    }

    @Override
    public SoftwareAccessPolicyVO accessPolicy(Long softwareId) {
        SoftwareRevision revision = accessPolicy.visiblePublishedRevision(softwareId);
        if (revision == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        List<SoftwareAccessPolicyVO.ContactVO> contacts =
                revision.getAccessMode() == SoftwareAccessMode.ADMIN_PAID
                        ? accessContactMapper.selectList(new LambdaQueryWrapper<SoftwareAccessContact>()
                                .eq(SoftwareAccessContact::getEnabled, true)
                                .orderByAsc(SoftwareAccessContact::getSortOrder))
                        .stream()
                        .map(contact -> new SoftwareAccessPolicyVO.ContactVO(contact.getId(),
                                contact.getName(), contact.getContactType(),
                                contact.getContactValue()))
                        .toList()
                        : List.of();
        return new SoftwareAccessPolicyVO(softwareId,
                revision.getAccessMode() == null ? null : revision.getAccessMode().name(),
                revision.getMinCommunityLevel(), revision.getAccessNote(), contacts);
    }

    @Override
    public Page<SoftwareReleaseVO> releases(Long softwareId, int page, int size) {
        SoftwareRevision revision = accessPolicy.visiblePublishedRevision(softwareId);
        if (revision == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        // 稳定版在前（7.2：默认稳定版，测试版单独标识），渠道内按服务端排序值；
        // 完整 ORDER BY 经 last() 一次性注入，禁止再叠加 orderBy 片段
        Page<SoftwareRelease> releasePage = releaseMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<SoftwareRelease>()
                        .eq(SoftwareRelease::getRevisionId, revision.getId())
                        .last("ORDER BY FIELD(channel, 'STABLE', 'BETA'), release_order ASC, id ASC"));
        List<SoftwareReleaseVO> records = releasePage.getRecords().stream()
                .map(release -> new SoftwareReleaseVO(release.getId(), release.getReleaseKey(),
                        release.getVersionName(), release.getChannel().name(),
                        release.getReleaseDate(), release.getChangelog(), release.getRecommended(),
                        sourceSummaries(softwareId, release.getId())))
                .toList();
        Page<SoftwareReleaseVO> result = new Page<>(releasePage.getCurrent(),
                releasePage.getSize(), releasePage.getTotal());
        result.setRecords(records);
        return result;
    }

    /** 来源摘要（4.3 可用性：DISABLED 无条件；其余按指纹匹配，否则 UNKNOWN） */
    private List<SoftwareSourceSummaryVO> sourceSummaries(Long softwareId, Long releaseId) {
        List<SoftwareSource> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<SoftwareSource>()
                        .eq(SoftwareSource::getReleaseId, releaseId)
                        .orderByAsc(SoftwareSource::getDisplayOrder)
                        .orderByAsc(SoftwareSource::getId));
        if (sources.isEmpty()) {
            return List.of();
        }
        Map<String, SoftwareSourceState> stateByKey = sourceStateMapper.selectList(
                        new LambdaQueryWrapper<SoftwareSourceState>()
                                .eq(SoftwareSourceState::getSoftwareId, softwareId))
                .stream()
                .collect(Collectors.toMap(SoftwareSourceState::getSourceKey,
                        Function.identity(), (a, b) -> a));
        return sources.stream()
                .map(source -> new SoftwareSourceSummaryVO(
                        source.getId(), source.getSourceKey(), source.getSourceType().name(),
                        source.getProvider().name(), source.getPlatform().name(),
                        source.getArch().name(), source.getLabel(), source.getSizeLabel(),
                        source.getClaimedSha256(), source.getNote(),
                        displayAvailability(source, stateByKey.get(source.getSourceKey()))))
                .toList();
    }

    private String displayAvailability(SoftwareSource source, SoftwareSourceState state) {
        if (state == null) {
            return SourceAvailability.UNKNOWN.name();
        }
        if (state.getAvailability() == SourceAvailability.DISABLED) {
            return SourceAvailability.DISABLED.name();
        }
        if (state.getCheckedFingerprint() != null
                && state.getCheckedFingerprint().equals(source.getContentFingerprint())
                && state.getAvailability() != null) {
            return state.getAvailability().name();
        }
        return SourceAvailability.UNKNOWN.name();
    }
}
