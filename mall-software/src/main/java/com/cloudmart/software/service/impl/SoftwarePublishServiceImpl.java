package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.dto.software.SoftwareSnapshotInput;
import com.cloudmart.software.entity.SoftwareAccessContact;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareFavorite;
import com.cloudmart.software.entity.SoftwarePublisherPermission;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareReviewCheckTask;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareRevisionMedia;
import com.cloudmart.software.entity.SoftwareRevisionTag;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.enums.SoftwareCommentStatus;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.policy.SoftwarePublisherPolicy;
import com.cloudmart.software.repository.SoftwareAccessContactMapper;
import com.cloudmart.software.repository.SoftwareFavoriteMapper;
import com.cloudmart.software.repository.SoftwareLockMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareReviewCheckTaskMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import com.cloudmart.software.repository.SoftwareRevisionTagMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.service.SoftwareApprovalService;
import com.cloudmart.software.service.SoftwareAuditService;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.software.service.SoftwareRateLimiter;
import com.cloudmart.software.service.SoftwarePublishService;
import com.cloudmart.software.service.SoftwareReviewTextDigest;
import com.cloudmart.software.service.SoftwareSnapshotAssembler;
import com.cloudmart.software.vo.software.SoftwareCreatedVO;
import com.cloudmart.software.vo.software.SoftwareEditorVO;
import com.cloudmart.software.vo.software.SoftwareMineItemVO;
import com.cloudmart.software.vo.software.SoftwareReviewDecisionVO;
import com.cloudmart.software.vo.software.SoftwareSubmittedVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 发布与快照流程实现（方案 4.1/4.3/7.3/7.5/9.7）。
 *
 * <p>关键不变量：</p>
 * <ul>
 *   <li>一款软件最多一个工作快照（软件主行锁 + working 指针）；</li>
 *   <li>审核请求只定位不可变提交记录 revisionId，不接收批次/版本号；</li>
 *   <li>审核通过同事务切换 published 指针、清空 working、publication_seq+1、
 *       写 Outbox 事件（SOFTWARE_REVIEW_APPROVED）；</li>
 *   <li>SW04：submit 在短事务内锁资格行→软件→快照，复核后冻结内容并转 CHECKING，
 *       同事务落持久化检查任务；远程检查在事务外由调度器执行，提交不等待远程结果；</li>
 *   <li>撤回/拒绝保留旧提交记录并克隆新 DRAFT；自动检查与人工审核结果只能作用于
 *       提交时冻结的 revisionId（摘要一致才允许推进状态）；</li>
 *   <li>幂等重试返回旧结果前同样先检查当前是否仍允许执行受限操作（4.0）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwarePublishServiceImpl implements SoftwarePublishService {

    private static final Set<String> MINE_TABS =
            java.util.Set.of("published", "draft", "checking", "pending", "rejected", "favorites",
                    "subscriptions");

    private final SoftwareMapper softwareMapper;
    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareReleaseMapper releaseMapper;
    private final SoftwareSourceMapper sourceMapper;
    private final SoftwareRevisionTagMapper revisionTagMapper;
    private final SoftwareRevisionMediaMapper revisionMediaMapper;
    private final SoftwareFavoriteMapper favoriteMapper;
    private final SoftwareAccessContactMapper accessContactMapper;
    private final SoftwareLockMapper lockMapper;
    private final SoftwarePublisherPolicy publisherPolicy;
    private final SoftwareSnapshotAssembler assembler;
    private final SoftwareOperationService operationService;
    private final SoftwareAuditService auditService;
    private final com.cloudmart.software.service.SoftwareReviewWorkflowService reviewWorkflowService;
    private final com.cloudmart.software.service.SoftwareAssetBindingService assetBindingService;
    private final SoftwareReviewCheckTaskMapper checkTaskMapper;
    private final SoftwareApprovalService approvalService;
    private final SoftwareProperties softwareProperties;
    private final SoftwareRateLimiter rateLimiter;
    private final com.cloudmart.software.repository.SoftwareSubscriptionMapper subscriptionMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    // ------------------------------------------------------------------
    // 作者侧联系入口（ADMIN_PAID 查看条件的选择来源）
    // ------------------------------------------------------------------

    @Override
    public List<Map<String, Object>> listEnabledAccessContacts() {
        // 只暴露启用项；作者选择后其 id 进入快照 accessContactId（A43：停用后不可再选）
        return accessContactMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SoftwareAccessContact>()
                        .eq(SoftwareAccessContact::getEnabled, true)
                        .orderByAsc(SoftwareAccessContact::getSortOrder)
                        .orderByAsc(SoftwareAccessContact::getId))
                .stream().map(contact -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", String.valueOf(contact.getId()));
                    row.put("name", contact.getName());
                    row.put("contactType", contact.getContactType());
                    row.put("contactValue", contact.getContactValue());
                    return row;
                })
                .toList();
    }

    // ------------------------------------------------------------------
    // 创建（幂等）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public SoftwareCreatedVO create(Long userId, String idempotencyKey) {
        // 4.0：幂等重试返回旧结果前也要检查当前是否仍允许执行受限操作
        publisherPolicy.requireForRestrictedWrite(userId);
        return operationService.execute("USER", userId, SoftwareOperationService.OP_CREATE_SOFTWARE,
                idempotencyKey, Map.of("userId", userId), null,
                () -> doCreate(userId),
                json -> objectMapper.readValue(json, SoftwareCreatedVO.class));
    }

    private SoftwareCreatedVO doCreate(Long userId) {
        Software software = new Software();
        software.setOwnerId(userId);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        software.setPublicationSeq(0);
        software.setFeaturedRank(0);
        software.setCommentCount(0);
        software.setFavoriteCount(0);
        software.setRatingCount(0);
        software.setRatingSum(0);
        software.setLinkAccessCount(0L);
        softwareMapper.insert(software);

        SoftwareRevision revision = new SoftwareRevision();
        revision.setSoftwareId(software.getId());
        revision.setEditorId(userId);
        revision.setReviewStatus(SoftwareReviewStatus.DRAFT);
        revision.setAccessMode(com.cloudmart.software.enums.SoftwareAccessMode.REGISTERED);
        revisionMapper.insert(revision);

        software.setWorkingRevisionId(revision.getId());
        softwareMapper.updateById(software);
        auditService.record(software.getId(), revision.getId(), userId, "USER",
                "CREATE", null, "DRAFT", "创建软件", null);
        return new SoftwareCreatedVO(software.getId(), revision.getId());
    }

    // ------------------------------------------------------------------
    // 工作快照（克隆；已有工作副本直接返回）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public SoftwareEditorVO workingRevision(Long userId, Long softwareId, String idempotencyKey) {
        publisherPolicy.requireForRestrictedWrite(userId);
        Software software = ownedSoftware(userId, softwareId);
        // 已有工作副本直接返回（不消耗幂等键语义，天然幂等）
        if (software.getWorkingRevisionId() != null) {
            return buildEditor(software, software.getWorkingRevisionId());
        }
        if (software.getPublishedRevisionId() == null) {
            // 无线上内容可克隆：首次创建走 POST /software
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在或无可克隆内容");
        }
        CloneResult cloneResult = transactionTemplate.execute(status -> operationService.execute(
                "USER", userId, SoftwareOperationService.OP_CLONE_WORKING,
                idempotencyKey, Map.of("softwareId", softwareId), softwareId,
                () -> doCloneWorking(userId, softwareId),
                json -> objectMapper.readValue(json, CloneResult.class)));
        // 8.2：克隆后为新 revisionId 幂等绑定同一批素材；失败登记重绑补偿（自愈）
        bindSnapshotAssetsQuietly(userId, cloneResult.revisionId());
        return buildEditor(softwareMapper.selectById(softwareId), cloneResult.revisionId());
    }

    /** 克隆/保存后素材绑定：事务外幂等执行；失败登记 software-ref-bind 补偿 */
    private void bindSnapshotAssetsQuietly(Long ownerUserId, Long revisionId) {
        try {
            List<Long> assetIds = revisionMediaMapper.selectList(
                            new LambdaQueryWrapper<SoftwareRevisionMedia>()
                                    .eq(SoftwareRevisionMedia::getRevisionId, revisionId))
                    .stream().map(SoftwareRevisionMedia::getAssetId).distinct().toList();
            assetBindingService.bindRevisionAssets(revisionId, ownerUserId, assetIds);
        } catch (Exception e) {
            log.warn("克隆后素材绑定失败，登记重绑补偿 revisionId={}: {}", revisionId, e.getMessage());
            List<Long> assetIds = revisionMediaMapper.selectList(
                            new LambdaQueryWrapper<SoftwareRevisionMedia>()
                                    .eq(SoftwareRevisionMedia::getRevisionId, revisionId))
                    .stream().map(SoftwareRevisionMedia::getAssetId).distinct().toList();
            assetBindingService.registerBindCompensation(revisionId, ownerUserId, assetIds);
        }
    }

    /** 克隆操作结果（只存 ID/版本号，6.10） */
    private record CloneResult(Long softwareId, Long revisionId) {
    }

    private CloneResult doCloneWorking(Long userId, Long softwareId) {
        // 锁软件主行复核 working 指针（4.1.7：主行锁保证唯一工作快照）
        Software locked = lockMapper.lockSoftware(softwareId);
        if (locked.getWorkingRevisionId() != null) {
            return new CloneResult(softwareId, locked.getWorkingRevisionId());
        }
        SoftwareRevision published = revisionMapper.selectById(locked.getPublishedRevisionId());
        if (published == null
                || published.getReviewStatus() != SoftwareReviewStatus.APPROVED
                || !softwareId.equals(published.getSoftwareId())) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在或无可克隆内容");
        }
        SoftwareRevision clone = assembler.cloneInto(published, softwareId, userId);
        revisionMapper.insert(clone);
        assembler.cloneChildren(published.getId(), clone.getId());

        locked.setWorkingRevisionId(clone.getId());
        softwareMapper.updateById(locked);
        auditService.record(softwareId, clone.getId(), userId, "USER",
                "CLONE_WORKING", "APPROVED", "DRAFT", "克隆线上快照为工作副本", null);
        return new CloneResult(softwareId, clone.getId());
    }

    // ------------------------------------------------------------------
    // 编辑器读取（本人完整数据，独立鉴权）
    // ------------------------------------------------------------------

    @Override
    public SoftwareEditorVO editor(Long userId, Long softwareId) {
        Software software = ownedSoftware(userId, softwareId);
        Long revisionId = software.getWorkingRevisionId() != null
                ? software.getWorkingRevisionId() : software.getPublishedRevisionId();
        if (revisionId == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        return buildEditor(software, revisionId);
    }

    /**
     * SF08：恢复历史内容——历史快照复制为新的 DRAFT 工作副本（走同样检查/人工审核）。
     * 不直接篡改 publishedRevisionId；幂等见接口注释。
     */
    @Override
    public SoftwareEditorVO draftFromHistory(Long userId, Long softwareId,
                                             Long sourceRevisionId, String idempotencyKey) {
        Software software = ownedSoftware(userId, softwareId);
        SoftwareRevision source = revisionMapper.selectById(sourceRevisionId);
        if (source == null || !source.getSoftwareId().equals(softwareId)
                || source.getSubmittedAt() == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "历史提交不存在");
        }
        if (software.getWorkingRevisionId() != null) {
            SoftwareRevision working = revisionMapper.selectById(software.getWorkingRevisionId());
            if (working != null
                    && sourceRevisionId.equals(working.getCopiedFromRevisionId())) {
                return buildEditor(software, working.getId()); // 幂等：同一来源的既有副本
            }
            throw new BusinessException("SOFTWARE_DRAFT_EXISTS",
                    "已有未完成的工作副本，请先保存提交或删除后再恢复历史");
        }
        // 创建/获取工作副本（克隆线上；从未发布软件的历史恢复列入后续小批）
        SoftwareEditorVO working = workingRevision(userId, softwareId, idempotencyKey);
        // 历史快照 → 全量保存输入（稳定 releaseKey/sourceKey 随快照携带，装配器沿用）
        SoftwareSnapshotInput input = snapshotInputFromEditor(softwareId, sourceRevisionId);
        SoftwareEditorVO saved = saveDraft(userId, softwareId, working.revisionId(), input);
        // 标记复制来源（幂等判定与追溯依据）
        SoftwareRevision workingRow = revisionMapper.selectById(working.revisionId());
        workingRow.setCopiedFromRevisionId(sourceRevisionId);
        revisionMapper.updateById(workingRow);
        auditService.record(softwareId, working.revisionId(), userId, "USER",
                "DRAFT_FROM_HISTORY", "DRAFT", "DRAFT",
                "恢复历史内容 sourceRevisionId=" + sourceRevisionId, null);
        return saved;
    }

    /** SF08：历史快照（编辑器视图）→ 全量保存输入（含稳定键） */
    private SoftwareSnapshotInput snapshotInputFromEditor(Long softwareId, Long revisionId) {
        SoftwareEditorVO editor = revisionSnapshot(softwareId, revisionId);
        SoftwareSnapshotInput input = new SoftwareSnapshotInput();
        input.setName(editor.name());
        input.setSummary(editor.summary());
        input.setCategoryId(editor.categoryId());
        input.setIconAssetId(editor.iconAssetId());
        input.setDescriptionHtml(editor.descriptionHtml());
        input.setLicenseType(editor.licenseType());
        input.setDeveloperName(editor.developerName());
        input.setOfficialUrl(editor.officialUrl());
        input.setAttributionType(editor.attributionType());
        input.setAttributionNote(editor.attributionNote());
        input.setSystemRequirements(editor.systemRequirements());
        input.setInstallationGuide(editor.installationGuide());
        input.setKnownIssues(editor.knownIssues());
        input.setLanguages(editor.languages());
        input.setAccessMode(editor.accessMode());
        input.setMinCommunityLevel(editor.minCommunityLevel());
        input.setAccessContactId(editor.accessContactId());
        input.setAccessNote(editor.accessNote());
        input.setTagIds(editor.tagIds());
        if (editor.screenshots() != null) {
            input.setScreenshots(editor.screenshots().stream().map(media -> {
                SoftwareSnapshotInput.MediaInput mediaInput = new SoftwareSnapshotInput.MediaInput();
                mediaInput.setAssetId(media.assetId());
                mediaInput.setAltText(media.altText());
                mediaInput.setDisplayOrder(media.displayOrder());
                return mediaInput;
            }).toList());
        }
        if (editor.releases() != null) {
            input.setReleases(editor.releases().stream().map(release -> {
                SoftwareSnapshotInput.ReleaseInput releaseInput = new SoftwareSnapshotInput.ReleaseInput();
                releaseInput.setReleaseKey(release.releaseKey());
                releaseInput.setVersionName(release.versionName());
                releaseInput.setChannel(release.channel());
                releaseInput.setRecommended(release.recommended());
                releaseInput.setReleaseDate(release.releaseDate());
                releaseInput.setChangelog(release.changelog());
                releaseInput.setDisplayOrder(release.displayOrder());
                if (release.sources() != null) {
                    releaseInput.setSources(release.sources().stream().map(source -> {
                        SoftwareSnapshotInput.SourceInput sourceInput =
                                new SoftwareSnapshotInput.SourceInput();
                        sourceInput.setSourceKey(source.sourceKey());
                        sourceInput.setSourceType(source.sourceType());
                        sourceInput.setProvider(source.provider());
                        sourceInput.setPlatform(source.platform());
                        sourceInput.setArch(source.arch());
                        sourceInput.setLabel(source.label());
                        sourceInput.setUrl(source.url());
                        sourceInput.setExtractionCode(source.extractionCode());
                        sourceInput.setArchivePassword(source.archivePassword());
                        sourceInput.setSizeLabel(source.sizeLabel());
                        sourceInput.setClaimedSha256(source.claimedSha256());
                        sourceInput.setNote(source.note());
                        sourceInput.setDisplayOrder(source.displayOrder());
                        return sourceInput;
                    }).toList());
                }
                return releaseInput;
            }).toList());
        }
        return input;
    }

    public SoftwareEditorVO revisionSnapshot(Long softwareId, Long revisionId) {
        Software software = softwareMapper.selectById(softwareId);
        if (software == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        return buildEditor(software, revisionId);
    }

    private SoftwareEditorVO buildEditor(Software software, Long revisionId) {
        SoftwareRevision revision = revisionMapper.selectById(revisionId);
        if (revision == null || !software.getId().equals(revision.getSoftwareId())) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        List<Long> tagIds = revisionTagMapper.selectList(new LambdaQueryWrapper<SoftwareRevisionTag>()
                        .eq(SoftwareRevisionTag::getRevisionId, revision.getId()))
                .stream().map(SoftwareRevisionTag::getTagId).toList();
        List<SoftwareEditorVO.MediaVO> screenshots = revisionMediaMapper.selectList(
                        new LambdaQueryWrapper<SoftwareRevisionMedia>()
                                .eq(SoftwareRevisionMedia::getRevisionId, revision.getId())
                                .eq(SoftwareRevisionMedia::getKind, "SCREENSHOT")
                                .orderByAsc(SoftwareRevisionMedia::getDisplayOrder))
                .stream().map(media -> new SoftwareEditorVO.MediaVO(media.getAssetId(),
                        media.getAltText(), media.getDisplayOrder()))
                .toList();
        List<SoftwareEditorVO.ReleaseVO> releases = releaseMapper.selectList(
                        new LambdaQueryWrapper<SoftwareRelease>()
                                .eq(SoftwareRelease::getRevisionId, revision.getId())
                                .orderByAsc(SoftwareRelease::getReleaseOrder))
                .stream().map(release -> new SoftwareEditorVO.ReleaseVO(
                        release.getId(), release.getReleaseKey(), release.getVersionName(),
                        release.getChannel().name(), release.getReleaseDate(), release.getChangelog(),
                        release.getRecommended(), release.getReleaseOrder(),
                        sourceMapper.selectList(new LambdaQueryWrapper<SoftwareSource>()
                                        .eq(SoftwareSource::getReleaseId, release.getId())
                                        .orderByAsc(SoftwareSource::getDisplayOrder))
                                .stream().map(source -> new SoftwareEditorVO.SourceVO(
                                        source.getId(), source.getSourceKey(),
                                        source.getSourceType().name(), source.getProvider().name(),
                                        source.getPlatform().name(), source.getArch().name(),
                                        source.getLabel(), source.getUrl(), source.getExtractionCode(),
                                        source.getArchivePassword(), source.getSizeLabel(),
                                        source.getClaimedSha256(), source.getNote(),
                                        source.getDisplayOrder()))
                                .toList()))
                .toList();
        return new SoftwareEditorVO(software.getId(), revision.getId(),
                revision.getReviewStatus().name(),
                revision.getName(), revision.getSummary(), revision.getCategoryId(),
                revision.getIconAssetId(), revision.getDescriptionHtml(),
                revision.getLicenseType() == null ? null : revision.getLicenseType().name(),
                revision.getDeveloperName(), revision.getOfficialUrl(), revision.getAttributionType(),
                revision.getAttributionNote(), revision.getSystemRequirements(),
                parseLanguages(revision.getLanguages()),
                revision.getAccessMode() == null ? null : revision.getAccessMode().name(),
                revision.getMinCommunityLevel(), revision.getAccessContactId(), revision.getAccessNote(),
                tagIds, screenshots, releases, revision.getSubmittedAt(), revision.getReviewedAt(),
                revision.getReviewerId(), revision.getReviewReason(),
                software.getLifecycle().name(),
                software.getPublishedRevisionId(), software.getWorkingRevisionId(),
                revision.getInstallationGuide(), revision.getKnownIssues(),
                assembler.deserializeHighlights(revision.getHighlights()));
    }

    private List<String> parseLanguages(String languagesJson) {
        if (languagesJson == null || languagesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(languagesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    // ------------------------------------------------------------------
    // 保存草稿（资格矩阵：是；全量替换；版本号机制已按用户决定移除，保存即生效）
    // ------------------------------------------------------------------

    @Override
    public SoftwareEditorVO saveDraft(Long userId, Long softwareId, Long revisionId,
                                      SoftwareSnapshotInput input) {
        publisherPolicy.requireForRestrictedWrite(userId);
        Software software = ownedSoftware(userId, softwareId);
        SoftwareRevision precheck = revisionOfSoftware(software, revisionId);
        requireEditableStatus(precheck);
        // SW08：解绑意图先于本地事务持久化（进程在远程绑定后、本地事务前崩溃也可恢复），
        // 且只覆盖"本次新增"的资产——草稿原本使用的图标/截图不受回滚影响
        String operationId = newOperationId(revisionId);
        List<Long> before = collectSnapshotAssets(precheck);
        List<Long> after = collectInputAssets(input);
        List<Long> added = after.stream().filter(a -> !before.contains(a)).distinct().toList();
        assetBindingService.registerUnbindCompensation(operationId, revisionId, added, "rollback");
        // 8.2：绑定在软件库事务外幂等执行（含 icon）；失败保留草稿重试（A21），
        // 绝不在绑定未成功时写 media 行
        assetBindingService.bindRevisionAssets(revisionId, userId, after);

        transactionTemplate.execute(status -> {
            SoftwareRevision revision = lockMapper.lockRevision(revisionId);
            if (revision == null || !softwareId.equals(revision.getSoftwareId())) {
                throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
            }
            requireEditableStatus(revision);
            assembler.applyInput(revision, input);
            revisionMapper.updateById(revision);
            auditService.record(softwareId, revisionId, userId, "USER",
                    "SAVE_DRAFT", revision.getReviewStatus().name(), revision.getReviewStatus().name(),
                    "保存草稿", null);
            // 成功路径：撤销回滚解绑意图（与本事务同提交）；释放意图由扫描器在提交后核对执行
            assetBindingService.cancelUnbindCompensation(operationId, revisionId, added, "rollback");
            return null;
        });
        // SW08：提交后差量释放被移除的旧资产（按提交后的最新引用集合计算，执行器再核对一次）
        releaseRemovedAssets(operationId, revisionId, before);
        return buildEditor(software, revisionId);
    }

    /**
     * SW02：局部保存。只应用 JSON 中出现的字段；未出现字段不读取、不覆盖。
     * 与全量保存共用同一事务/审计/锁顺序，保证 save 与 patch 并发时按锁串行。
     */
    @Override
    public SoftwareEditorVO patchDraft(Long userId, Long softwareId, Long revisionId, JsonNode patch) {
        publisherPolicy.requireForRestrictedWrite(userId);
        Software software = ownedSoftware(userId, softwareId);
        SoftwareRevision precheck = revisionOfSoftware(software, revisionId);
        requireEditableStatus(precheck);
        // SW08：与全量保存同策略——意图先持久化、只覆盖本次新增、提交后差量释放
        String operationId = newOperationId(revisionId);
        List<Long> before = collectSnapshotAssets(precheck);
        List<Long> patchAssets = collectPatchAssets(patch);
        List<Long> added = patchAssets.stream().filter(a -> !before.contains(a)).distinct().toList();
        if (!added.isEmpty()) {
            assetBindingService.registerUnbindCompensation(operationId, revisionId, added, "rollback");
        }
        if (!patchAssets.isEmpty()) {
            assetBindingService.bindRevisionAssets(revisionId, userId, patchAssets);
        }

        transactionTemplate.execute(status -> {
            SoftwareRevision revision = lockMapper.lockRevision(revisionId);
            if (revision == null || !softwareId.equals(revision.getSoftwareId())) {
                throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
            }
            requireEditableStatus(revision);
            assembler.applyPatch(revision, patch);
            revisionMapper.updateById(revision);
            auditService.record(softwareId, revisionId, userId, "USER",
                    "SAVE_DRAFT", revision.getReviewStatus().name(), revision.getReviewStatus().name(),
                    "局部保存草稿", null);
            if (!added.isEmpty()) {
                assetBindingService.cancelUnbindCompensation(operationId, revisionId, added, "rollback");
            }
            return null;
        });
        releaseRemovedAssets(operationId, revisionId, before);
        return buildEditor(software, revisionId);
    }

    /** 局部保存涉及的素材：仅 iconAssetId 或 screenshots 出现时重绑，缺失字段不触碰 */
    private List<Long> collectPatchAssets(JsonNode patch) {
        List<Long> assetIds = new ArrayList<>();
        if (patch.has("iconAssetId") && !patch.get("iconAssetId").isNull()) {
            assetIds.add(patch.get("iconAssetId").asLong());
        }
        if (patch.has("screenshots") && patch.get("screenshots").isArray()) {
            for (JsonNode item : patch.get("screenshots")) {
                JsonNode assetId = item == null ? null : item.get("assetId");
                if (assetId != null && !assetId.isNull()) {
                    assetIds.add(assetId.asLong());
                }
            }
        }
        return assetIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    /** SW08：保存操作标识——每次尝试唯一，解绑意图按操作归属，互不覆盖 */
    private String newOperationId(Long revisionId) {
        return "SAVE-" + revisionId + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * SW08：提交后差量释放——按提交后的最新引用集合计算被移除资产并登记解绑意图；
     * 执行器解绑前会再次核对当前引用，旧操作意图不会撤销后续成功保存的绑定。
     */
    private void releaseRemovedAssets(String operationId, Long revisionId, List<Long> before) {
        List<Long> current = collectSnapshotAssets(revisionMapper.selectById(revisionId));
        List<Long> removed = before.stream().filter(a -> !current.contains(a)).distinct().toList();
        assetBindingService.registerUnbindCompensation(operationId, revisionId, removed, "release");
    }

    /** 快照当前绑定资产清单：icon + 截图/正文媒体（submit 自愈重绑用） */
    private List<Long> collectSnapshotAssets(SoftwareRevision snapshot) {
        List<Long> assetIds = new ArrayList<>();
        if (snapshot.getIconAssetId() != null) {
            assetIds.add(snapshot.getIconAssetId());
        }
        revisionMediaMapper.selectList(new LambdaQueryWrapper<SoftwareRevisionMedia>()
                        .eq(SoftwareRevisionMedia::getRevisionId, snapshot.getId()))
                .forEach(media -> assetIds.add(media.getAssetId()));
        return assetIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    /** 输入中的绑定资产清单：icon + 截图（正文媒体同通道） */
    private List<Long> collectInputAssets(SoftwareSnapshotInput input) {
        List<Long> assetIds = new ArrayList<>();
        if (input.getIconAssetId() != null) {
            assetIds.add(input.getIconAssetId());
        }
        if (input.getScreenshots() != null) {
            input.getScreenshots().stream()
                    .map(SoftwareSnapshotInput.MediaInput::getAssetId)
                    .filter(java.util.Objects::nonNull)
                    .forEach(assetIds::add);
        }
        return assetIds;
    }

    // ------------------------------------------------------------------
    // 提交审核（9.7：先远程审核绑定结果，再短事务复核冻结）
    // ------------------------------------------------------------------

    @Override
    public SoftwareSubmittedVO submit(Long userId, Long softwareId, Long revisionId,
                                      String idempotencyKey) {
        // 身份/资格/开关先于重放：重放也不得绕过（4.0/11.2）
        publisherPolicy.requireForRestrictedWrite(userId);
        if (!softwareProperties.isPublishEnabled()) {
            throw new BusinessException("SOFTWARE_PUBLISH_DISABLED", "软件投稿功能暂未开放");
        }
        Software software = ownedSoftware(userId, softwareId);
        SoftwareRevision snapshot = revisionOfSoftware(software, revisionId);

        // SW04：稳定意图只含操作类型/软件/工作副本；不含 contentHash、checkedAt、时间/随机值
        Map<String, Object> intent = Map.of(
                "softwareId", softwareId, "revisionId", revisionId, "intent", "SUBMIT");
        // 重放必须发生在限频、素材绑定与冻结之前，超时重试不再重复扣次数/重复入队检查
        java.util.Optional<SoftwareSubmittedVO> replay = operationService.replay(
                "USER", userId, SoftwareOperationService.OP_SUBMIT, idempotencyKey, intent,
                json -> objectMapper.readValue(json, SoftwareSubmittedVO.class));
        if (replay.isPresent()) {
            return replay.get();
        }

        // 11.2 限频：仅首次认领的新提交扣一次
        if (!rateLimiter.allowSubmit(userId)) {
            throw new BusinessException("SOFTWARE_RATE_LIMITED", "今日提交次数已达上限，请明天再试");
        }
        // 8.2.3：所有引用绑定成功才允许 submit——幂等自愈重绑（覆盖补偿缺口）
        assetBindingService.bindRevisionAssets(revisionId, userId, collectSnapshotAssets(snapshot));

        // 短本地事务（13.2.3）：锁资格行→软件→工作副本，复核后冻结内容并落检查任务；
        // 远程检查不在此处发生，提交只返回"审核检查中"。
        return transactionTemplate.execute(status -> operationService.execute("USER", userId,
                SoftwareOperationService.OP_SUBMIT, idempotencyKey, intent, softwareId,
                () -> doSubmitLocked(userId, softwareId, revisionId, idempotencyKey),
                json -> objectMapper.readValue(json, SoftwareSubmittedVO.class)));
    }

    /** 提交锁定阶段：仅在 TransactionTemplate 事务内执行（不加 @Transactional 避免自调用失效） */
    private SoftwareSubmittedVO doSubmitLocked(Long userId, Long softwareId, Long revisionId,
                                               String idempotencyKey) {
        // 短本地事务：锁资格行→软件主行→快照行，复核归属/状态
        SoftwarePublisherPermission permission = lockMapper.lockPublisherByUser(userId);
        if (permission == null || !Boolean.TRUE.equals(permission.getCanPublish())) {
            throw new BusinessException("SOFTWARE_PUBLISH_NOT_ENABLED", "发布资格已变更，提交被拒绝");
        }
        Software locked = lockMapper.lockSoftware(softwareId);
        if (!userId.equals(locked.getOwnerId()) || locked.getDeletedAt() != null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        if (!revisionId.equals(locked.getWorkingRevisionId())) {
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", "快照已不是当前工作副本，请刷新");
        }
        SoftwareRevision lockedRevision = lockMapper.lockRevision(revisionId);
        requireEditableStatus(lockedRevision);
        // 冻结前完整校验（快照必须可发布）
        assembler.validateForSubmit(lockedRevision);

        // 在锁内收集并冻结：异步检查读取同一份不可变记录重算摘要
        List<String> texts = reviewTexts(lockedRevision);
        if (texts.isEmpty()) {
            throw new BusinessException("SOFTWARE_SUBMIT_INCOMPLETE", "请先填写软件名称并保存草稿");
        }
        if (SoftwareReviewTextDigest.exceedsBatchLimit(texts)) {
            throw new BusinessException("SOFTWARE_SNAPSHOT_INVALID", "审核文本过多，请减少版本或来源说明");
        }
        String contentHash = SoftwareReviewTextDigest.digest(texts);
        String fromStatus = lockedRevision.getReviewStatus().name();
        LocalDateTime now = LocalDateTime.now(clock);

        lockedRevision.setReviewStatus(SoftwareReviewStatus.CHECKING);
        lockedRevision.setContentDigest(contentHash);
        lockedRevision.setSubmittedAt(now);
        revisionMapper.updateById(lockedRevision);

        SoftwareReviewCheckTask task = new SoftwareReviewCheckTask();
        task.setSoftwareId(softwareId);
        task.setRevisionId(revisionId);
        task.setOperationKey(idempotencyKey);
        task.setContentDigest(contentHash);
        task.setAutoPublish("DIRECT".equals(permission.getPublishMode()));
        task.setStatus("PENDING");
        task.setAttempts(0);
        task.setLeaseVersion(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        checkTaskMapper.insert(task);

        auditService.record(softwareId, revisionId, userId, "USER",
                "SUBMIT", fromStatus, "CHECKING",
                "contentHash=" + contentHash + ";autoPublish=" + task.getAutoPublish(), null);
        return new SoftwareSubmittedVO(softwareId, revisionId, "CHECKING", idempotencyKey);
    }

    /** 审核文本：介绍/亮点/版本日志/来源说明，均留空时审核软件名（避免空审核批次）。 */
    private List<String> reviewTexts(SoftwareRevision snapshot) {
        List<SoftwareRelease> releases = releaseMapper.selectList(
                new LambdaQueryWrapper<SoftwareRelease>()
                        .eq(SoftwareRelease::getRevisionId, snapshot.getId()));
        List<SoftwareSource> sources = releases.isEmpty() ? List.of()
                : sourceMapper.selectList(new LambdaQueryWrapper<SoftwareSource>()
                        .in(SoftwareSource::getReleaseId,
                                releases.stream().map(SoftwareRelease::getId).toList()));
        return SoftwareReviewTextDigest.collect(snapshot, releases, sources);
    }

    // ------------------------------------------------------------------
    // 撤回 / 下架 / 重新上架 / 删除（归属校验；撤回/下架/删除不要求资格）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public SoftwareEditorVO withdraw(Long userId, Long softwareId, Long revisionId) {
        ownedSoftware(userId, softwareId);
        Software locked = lockMapper.lockSoftware(softwareId);
        SoftwareRevision revision = lockMapper.lockRevision(revisionId);
        if (revision == null || !softwareId.equals(revision.getSoftwareId())) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "快照不存在");
        }
        // 幂等：已撤回的旧提交重复调用返回当前工作副本
        if (revision.getReviewStatus() == SoftwareReviewStatus.WITHDRAWN) {
            Long working = locked.getWorkingRevisionId();
            return buildEditor(locked, working != null ? working : revisionId);
        }
        SoftwareReviewStatus fromStatus = revision.getReviewStatus();
        if (fromStatus != SoftwareReviewStatus.PENDING
                && fromStatus != SoftwareReviewStatus.CHECKING) {
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", "仅检查中/待审快照可撤回");
        }
        // 取消尚未完成的自动检查：已领取的检查结果因租约/状态复核无法再写回
        checkTaskMapper.cancelByRevision(revisionId, LocalDateTime.now(clock));
        // 旧提交记录内容保持不变，只记录终态；编辑在新 DRAFT 上进行（旧审核页无法再作用于新稿）
        revision.setReviewStatus(SoftwareReviewStatus.WITHDRAWN);
        revisionMapper.updateById(revision);

        SoftwareRevision draft = assembler.cloneInto(revision, softwareId, userId);
        draft.setCopiedFromRevisionId(revisionId);
        revisionMapper.insert(draft);
        assembler.cloneChildren(revisionId, draft.getId());
        locked.setWorkingRevisionId(draft.getId());
        softwareMapper.updateById(locked);
        auditService.record(softwareId, revisionId, userId, "USER",
                "WITHDRAW", fromStatus.name(), "WITHDRAWN", "撤回并生成新草稿", null);
        return buildEditor(locked, draft.getId());
    }

    @Override
    @Transactional
    public SoftwareEditorVO unlist(Long userId, Long softwareId) {
        ownedSoftware(userId, softwareId);
        Software locked = lockMapper.lockSoftware(softwareId);
        if (locked.getLifecycle() != SoftwareLifecycle.ACTIVE) {
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", "仅上架中的软件可下架");
        }
        locked.setLifecycle(SoftwareLifecycle.UNLISTED);
        softwareMapper.updateById(locked);
        auditService.record(softwareId, null, userId, "USER",
                "UNLIST", "ACTIVE", "UNLISTED", "作者下架", null);
        return buildEditor(locked, locked.getWorkingRevisionId() != null
                ? locked.getWorkingRevisionId() : locked.getPublishedRevisionId());
    }

    @Override
    @Transactional
    public SoftwareEditorVO relist(Long userId, Long softwareId) {
        // 4.0 矩阵：重新上架要求发布资格（A31/A33：关闭后 relist 也无法绕过）
        publisherPolicy.requireForRestrictedWrite(userId);
        ownedSoftware(userId, softwareId);
        Software locked = lockMapper.lockSoftware(softwareId);
        if (locked.getLifecycle() == SoftwareLifecycle.BANNED) {
            throw new BusinessException("SOFTWARE_BANNED", "平台封禁的软件不能自行恢复，请联系管理员");
        }
        if (locked.getLifecycle() != SoftwareLifecycle.UNLISTED) {
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", "仅已下架的软件可重新上架");
        }
        // 4.2：relist 须存在合格已审快照
        if (locked.getPublishedRevisionId() == null
                || revisionMapper.selectById(locked.getPublishedRevisionId()) == null
                || revisionMapper.selectById(locked.getPublishedRevisionId())
                .getReviewStatus() != SoftwareReviewStatus.APPROVED) {
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", "缺少合格已审快照，无法重新上架");
        }
        locked.setLifecycle(SoftwareLifecycle.ACTIVE);
        softwareMapper.updateById(locked);
        auditService.record(softwareId, null, userId, "USER",
                "RELIST", "UNLISTED", "ACTIVE", "作者重新上架", null);
        return buildEditor(locked, locked.getWorkingRevisionId() != null
                ? locked.getWorkingRevisionId() : locked.getPublishedRevisionId());
    }

    @Override
    @Transactional
    public void delete(Long userId, Long softwareId) {
        ownedSoftware(userId, softwareId);
        Software locked = lockMapper.lockSoftware(softwareId);
        if (locked.getLifecycle() == SoftwareLifecycle.DELETED) {
            return; // 幂等
        }
        String fromStatus = locked.getLifecycle().name();
        locked.setLifecycle(SoftwareLifecycle.DELETED);
        locked.setDeletedAt(LocalDateTime.now(clock));
        softwareMapper.updateById(locked);
        auditService.record(softwareId, null, userId, "USER",
                "DELETE", "BANNED".equals(fromStatus) ? "BANNED" : null,
                "DELETED", "作者删除（不可恢复，操作留有审计记录）", null);
    }

    // ------------------------------------------------------------------
    // 管理员删除（软删；与作者删除同语义，无归属校验，审计 actor=ADMIN）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void adminDelete(Long adminId, Long softwareId) {
        Software locked = lockMapper.lockSoftware(softwareId);
        if (locked == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        if (locked.getLifecycle() == SoftwareLifecycle.DELETED) {
            return; // 幂等
        }
        String fromStatus = locked.getLifecycle().name();
        locked.setLifecycle(SoftwareLifecycle.DELETED);
        locked.setDeletedAt(LocalDateTime.now(clock));
        softwareMapper.updateById(locked);
        auditService.record(softwareId, null, adminId, "ADMIN",
                "DELETE", "BANNED".equals(fromStatus) ? "BANNED" : null,
                "DELETED", "管理员删除（软删，审计保留）", null);
    }

    // ------------------------------------------------------------------
    // 管理员审核决策（幂等；A07 旧批次 409；A33 作者资格复核）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public SoftwareReviewDecisionVO reviewDecision(Long adminId, String idempotencyKey, Long softwareId,
                                                   Long revisionId, boolean approve,
                                                   String reason, String requestId) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new BusinessException("SOFTWARE_REVIEW_INVALID", "审核说明须为 1~1000 字");
        }
        Map<String, Object> reviewHashPayload = new HashMap<>();
        reviewHashPayload.put("softwareId", softwareId);
        reviewHashPayload.put("revisionId", revisionId);
        reviewHashPayload.put("approve", approve);
        reviewHashPayload.put("reason", reason);
        return operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_REVIEW_DECISION,
                idempotencyKey, reviewHashPayload,
                softwareId,
                () -> doReview(adminId, softwareId, revisionId, approve, reason, requestId),
                json -> objectMapper.readValue(json, SoftwareReviewDecisionVO.class));
    }

    private SoftwareReviewDecisionVO doReview(Long adminId, Long softwareId, Long revisionId,
                                              boolean approve, String reason, String requestId) {
        SoftwareRevision precheck = revisionMapper.selectById(revisionId);
        if (precheck == null || !softwareId.equals(precheck.getSoftwareId())
                || precheck.getReviewStatus() != SoftwareReviewStatus.PENDING) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "待审快照不存在");
        }

        if (approve) {
            // A33：审核通过要求作者仍有发布资格（锁资格行并复核）
            Software author = softwareMapper.selectById(softwareId);
            SoftwarePublisherPermission permission =
                    lockMapper.lockPublisherByUser(author.getOwnerId());
            if (permission == null || !Boolean.TRUE.equals(permission.getCanPublish())) {
                throw new BusinessException("SOFTWARE_PUBLISH_NOT_ENABLED",
                        "作者发布资格已关闭，本次不能通过；可退回并等待资格恢复");
            }
        }
        Software locked = lockMapper.lockSoftware(softwareId);
        SoftwareRevision revision = lockMapper.lockRevision(revisionId);
        if (revision.getReviewStatus() != SoftwareReviewStatus.PENDING) {
            throw new BusinessException("SOFTWARE_SUBMIT_STALE",
                    "该提交已不在待审状态，请刷新后处理");
        }
        // SW04：审核结论必须对应提交时冻结的内容摘要
        if (revision.getContentDigest() != null) {
            String actualDigest = SoftwareReviewTextDigest.digest(reviewTexts(revision));
            if (!revision.getContentDigest().equals(actualDigest)) {
                throw new BusinessException("SOFTWARE_STATE_CONFLICT",
                        "提交内容与审核时内容不一致，请退回后重新提交");
            }
        }

        revision.setReviewedAt(LocalDateTime.now(clock));
        revision.setReviewerId(adminId);
        revision.setReviewReason(reason);

        if (approve) {
            return approvalService.publishApproved(locked, revision, adminId, "ADMIN",
                    "APPROVE", "PENDING", reason, requestId);
        }

        // 拒绝同样生成新 DRAFT（13.2.3 第 5 条）：旧提交保留审核记录，
        // Mine 的 rejected 页签通过 copiedFromRevisionId 定位当前可编辑副本
        approvalService.rejectAndClone(locked, revision, adminId, "ADMIN", "REJECT",
                "PENDING", reason, requestId);
        return new SoftwareReviewDecisionVO(softwareId, revisionId, "REJECTED");
    }

    // ------------------------------------------------------------------
    // 我的软件
    // ------------------------------------------------------------------

    @Override
    public Page<SoftwareMineItemVO> mine(Long userId, String tab, int page, int size) {
        String normalizedTab = tab == null ? "published" : tab;
        if (!MINE_TABS.contains(normalizedTab)) {
            throw new BusinessException("SOFTWARE_TAB_INVALID", "tab 取值非法");
        }
        if ("favorites".equals(normalizedTab)) {
            return mineFavorites(userId, Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)));
        }
        if ("subscriptions".equals(normalizedTab)) {
            return mineSubscriptions(userId,
                    Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)));
        }
        List<Software> all = softwareMapper.selectList(
                new LambdaQueryWrapper<Software>()
                        .eq(Software::getOwnerId, userId)
                        .isNull(Software::getDeletedAt)
                        .orderByDesc(Software::getUpdatedAt));
        // 先全量归类再内存分页：DB 行数≠当前 tab 条数，total 必须按归类后计数
        List<SoftwareMineItemVO> matched = all.stream()
                .map(software -> mineItem(software, normalizedTab))
                .filter(java.util.Objects::nonNull)
                .toList();
        int from = (int) Math.min((long) Math.max(page, 1) * Math.min(Math.max(size, 1), 50)
                - Math.min(Math.max(size, 1), 50), matched.size());
        int to = (int) Math.min((long) from + Math.min(Math.max(size, 1), 50),
                matched.size());
        Page<SoftwareMineItemVO> result = new Page<>(Math.max(page, 1),
                Math.min(Math.max(size, 1), 50), matched.size());
        result.setRecords(matched.subList(from, to));
        return result;
    }

    /** 按工作/发布快照状态归入请求的 tab（一条软件只出现在其当前状态 tab） */
    private SoftwareMineItemVO mineItem(Software software, String tab) {
        Long revisionId = software.getWorkingRevisionId() != null
                ? software.getWorkingRevisionId() : software.getPublishedRevisionId();
        SoftwareRevision revision = revisionId == null ? null : revisionMapper.selectById(revisionId);
        String status = resolveMineStatus(software, revision);
        // tab 入参为小写（MINE_TABS），status 为枚举名大写——忽略大小写比较，
        // 否则所有 tab 的 records 永远为空（total 与 records 不一致，线上实测踩坑）。
        // 检查中归入待审页签展示（状态仍返回 CHECKING，客户端据此显示"审核检查中"）
        boolean matched = tab.equalsIgnoreCase(status)
                || ("pending".equals(tab) && "CHECKING".equals(status));
        if (!matched) {
            return null;
        }
        return new SoftwareMineItemVO(software.getId(),
                revision == null ? "" : revision.getName(),
                revision == null ? "" : revision.getSummary(),
                status, revisionId, software.getFavoriteCount(), software.getUpdatedAt(), false);
    }

    /** 订阅列表（P1）：与收藏同口径——不可见软件显示不可用占位（保留 id 供取消订阅） */
    private Page<SoftwareMineItemVO> mineSubscriptions(Long userId,
                                                       Page<SoftwareMineItemVO> result) {
        Page<com.cloudmart.software.entity.SoftwareSubscription> subscriptionPage =
                subscriptionMapper.selectPage(
                        Page.of(result.getCurrent(), result.getSize()),
                        new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareSubscription>()
                                .eq(com.cloudmart.software.entity.SoftwareSubscription::getUserId,
                                        userId)
                                .orderByDesc(com.cloudmart.software.entity.SoftwareSubscription
                                        ::getCreatedAt));
        result.setTotal(subscriptionPage.getTotal());
        result.setRecords(subscriptionPage.getRecords().stream()
                .map(subscription -> {
                    Software software = softwareMapper.selectById(subscription.getSoftwareId());
                    if (software == null) {
                        return null;
                    }
                    SoftwareRevision revision = software.getPublishedRevisionId() == null ? null
                            : revisionMapper.selectById(software.getPublishedRevisionId());
                    boolean visible = revision != null
                            && revision.getReviewStatus() == SoftwareReviewStatus.APPROVED
                            && software.getLifecycle() == SoftwareLifecycle.ACTIVE
                            && software.getDeletedAt() == null;
                    if (visible) {
                        return new SoftwareMineItemVO(software.getId(), revision.getName(),
                                revision.getSummary(), "PUBLISHED", revision.getId(),
                                software.getFavoriteCount(),
                                software.getUpdatedAt(), false);
                    }
                    return new SoftwareMineItemVO(software.getId(), "", "", "UNAVAILABLE",
                            null, null, null, true);
                })
                .filter(Objects::nonNull)
                .toList());
        return result;
    }

    private String resolveMineStatus(Software software, SoftwareRevision revision) {
        if (revision == null) {
            return "DRAFT";
        }
        if (revision.getReviewStatus() == SoftwareReviewStatus.CHECKING) {
            return "CHECKING";
        }
        if (revision.getReviewStatus() == SoftwareReviewStatus.PENDING) {
            return "PENDING";
        }
        if (revision.getReviewStatus() == SoftwareReviewStatus.REJECTED) {
            return "REJECTED";
        }
        // 拒绝后自动克隆的可编辑副本仍归入 rejected 页签：保留可见的拒绝上下文，
        // 不因 working 指针切换而让作者"找不到为什么被退回"
        if (revision.getReviewStatus() == SoftwareReviewStatus.DRAFT
                && revision.getCopiedFromRevisionId() != null) {
            SoftwareRevision parent = revisionMapper.selectById(revision.getCopiedFromRevisionId());
            if (parent != null && parent.getReviewStatus() == SoftwareReviewStatus.REJECTED) {
                return "REJECTED";
            }
        }
        if (software.getPublishedRevisionId() != null && revision.getId().equals(
                software.getPublishedRevisionId())) {
            return switch (software.getLifecycle()) {
                case ACTIVE -> "PUBLISHED";
                case UNLISTED -> "UNLISTED";
                case BANNED -> "BANNED";
                default -> "DRAFT";
            };
        }
        return "DRAFT";
    }

    /** 收藏列表（4.5）：不可见软件降级为不泄露内容的占位 */
    private Page<SoftwareMineItemVO> mineFavorites(Long userId, Page<SoftwareMineItemVO> result) {
        Page<SoftwareFavorite> favoritePage = favoriteMapper.selectPage(
                Page.of(result.getCurrent(), result.getSize()),
                new LambdaQueryWrapper<SoftwareFavorite>()
                        .eq(SoftwareFavorite::getUserId, userId)
                        .orderByDesc(SoftwareFavorite::getCreatedAt));
        result.setTotal(favoritePage.getTotal());
        result.setRecords(favoritePage.getRecords().stream()
                .map(favorite -> {
                    Software software = softwareMapper.selectById(favorite.getSoftwareId());
                    if (software == null) {
                        return null;
                    }
                    SoftwareRevision revision = software.getPublishedRevisionId() == null ? null
                            : revisionMapper.selectById(software.getPublishedRevisionId());
                    boolean visible = revision != null
                            && revision.getReviewStatus() == SoftwareReviewStatus.APPROVED
                            && software.getLifecycle() == SoftwareLifecycle.ACTIVE
                            && software.getDeletedAt() == null;
                    if (visible) {
                        return new SoftwareMineItemVO(software.getId(), revision.getName(),
                                revision.getSummary(), "PUBLISHED", revision.getId(),
                                software.getFavoriteCount(),
                                software.getUpdatedAt(), false);
                    }
                    // 不可见占位：保留 id 供取消收藏，不泄露名称/摘要/状态
                    return new SoftwareMineItemVO(software.getId(), "", "", "UNAVAILABLE",
                            null, null, null, true);
                })
                .filter(Objects::nonNull)
                .toList());
        return result;
    }

    // ------------------------------------------------------------------
    // 共用校验
    // ------------------------------------------------------------------

    /** 归属校验：非本人/已删除一律 SOFTWARE_NOT_FOUND（不泄露存在性） */
    private Software ownedSoftware(Long userId, Long softwareId) {
        if (softwareId == null || softwareId <= 0) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        Software software = softwareMapper.selectById(softwareId);
        if (software == null || software.getDeletedAt() != null
                || !userId.equals(software.getOwnerId())) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        return software;
    }

    private SoftwareRevision revisionOfSoftware(Software software, Long revisionId) {
        SoftwareRevision revision = revisionMapper.selectById(revisionId);
        if (revision == null || !software.getId().equals(revision.getSoftwareId())) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        return revision;
    }

    /** 锁定快照并校验归属（调用方持有软件主行锁或已做归属校验） */
    private void requireEditableStatus(SoftwareRevision revision) {
        if (revision.getReviewStatus() != SoftwareReviewStatus.DRAFT
                && revision.getReviewStatus() != SoftwareReviewStatus.REJECTED) {
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", "仅草稿/退回快照可编辑");
        }
    }

}
