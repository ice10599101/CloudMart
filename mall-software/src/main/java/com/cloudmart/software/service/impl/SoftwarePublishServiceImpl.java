package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.dto.software.InteractionContextRequest;
import com.cloudmart.software.dto.software.SoftwareReviewRequest;
import com.cloudmart.software.dto.software.SoftwareReviewOutcomeVO;
import com.cloudmart.software.dto.software.SoftwareSnapshotInput;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareFavorite;
import com.cloudmart.software.entity.SoftwarePublisherPermission;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareRevisionMedia;
import com.cloudmart.software.entity.SoftwareRevisionTag;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.enums.SoftwareCommentStatus;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.mq.SoftwareEvents;
import com.cloudmart.software.policy.SoftwarePublisherPolicy;
import com.cloudmart.software.repository.SoftwareFavoriteMapper;
import com.cloudmart.software.repository.SoftwareLockMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import com.cloudmart.software.repository.SoftwareRevisionTagMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.service.SoftwareAuditService;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.software.service.SoftwareRateLimiter;
import com.cloudmart.software.service.SoftwareReleaseFanoutService;
import com.cloudmart.software.service.SoftwarePublishService;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 发布与快照流程实现（方案 4.1/4.3/7.3/7.5/9.7）。
 *
 * <p>关键不变量：</p>
 * <ul>
 *   <li>一款软件最多一个工作快照（软件主行锁 + working 指针）；</li>
 *   <li>审核请求携带 revisionId+submitSeq+expectedVersion，旧批次 409（A07）；</li>
 *   <li>审核通过同事务切换 published 指针、清空 working、publication_seq+1、
 *       写 Outbox 事件（SOFTWARE_REVIEW_APPROVED）；</li>
 *   <li>submit 先远程审核绑定 contentHash/checkedAt，再短事务锁资格行→软件→快照
 *       复核（9.7），条件变更即 409 重新审核；三级违规词拒绝提交（失败不得发布）；</li>
 *   <li>幂等重试返回旧结果前同样先检查当前是否仍允许执行受限操作（4.0）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwarePublishServiceImpl implements SoftwarePublishService {

    private static final int SCHEMA_VERSION = 1;
    private static final Set<String> MINE_TABS =
            java.util.Set.of("published", "draft", "pending", "rejected", "favorites",
                    "subscriptions");

    private final SoftwareMapper softwareMapper;
    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareReleaseMapper releaseMapper;
    private final SoftwareSourceMapper sourceMapper;
    private final SoftwareRevisionTagMapper revisionTagMapper;
    private final SoftwareRevisionMediaMapper revisionMediaMapper;
    private final SoftwareFavoriteMapper favoriteMapper;
    private final SoftwareLockMapper lockMapper;
    private final SoftwarePublisherPolicy publisherPolicy;
    private final SoftwareSnapshotAssembler assembler;
    private final SoftwareOperationService operationService;
    private final SoftwareAuditService auditService;
    private final com.cloudmart.software.service.SoftwareAssetBindingService assetBindingService;
    private final OutboxService outboxService;
    private final com.cloudmart.software.feign.CommunitySupportFeignClient communitySupportFeignClient;
    private final com.cloudmart.software.feign.UserSupportFeignClient userSupportFeignClient;
    private final SoftwareProperties softwareProperties;
    private final SoftwareRateLimiter rateLimiter;
    private final SoftwareReleaseFanoutService fanoutService;
    private final com.cloudmart.software.repository.SoftwareSubscriptionMapper subscriptionMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

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
        software.setLockVersion(0);
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
        revision.setRevisionNo(1);
        revision.setReviewStatus(SoftwareReviewStatus.DRAFT);
        revision.setSubmitSeq(0);
        revision.setLockVersion(0);
        revision.setAccessMode(com.cloudmart.software.enums.SoftwareAccessMode.REGISTERED);
        revisionMapper.insert(revision);

        software.setWorkingRevisionId(revision.getId());
        softwareMapper.updateById(software);
        auditService.record(software.getId(), revision.getId(), 0, userId, "USER",
                "CREATE", null, "DRAFT", "创建软件", null);
        return new SoftwareCreatedVO(software.getId(), revision.getId(), software.getLockVersion());
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
    private record CloneResult(Long softwareId, Long revisionId, Integer lockVersion) {
    }

    private CloneResult doCloneWorking(Long userId, Long softwareId) {
        // 锁软件主行复核 working 指针（4.1.7：主行锁保证唯一工作快照）
        Software locked = lockMapper.lockSoftware(softwareId);
        if (locked.getWorkingRevisionId() != null) {
            return new CloneResult(softwareId, locked.getWorkingRevisionId(), locked.getLockVersion());
        }
        SoftwareRevision published = revisionMapper.selectById(locked.getPublishedRevisionId());
        if (published == null
                || published.getReviewStatus() != SoftwareReviewStatus.APPROVED
                || !softwareId.equals(published.getSoftwareId())) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在或无可克隆内容");
        }
        int nextNo = nextRevisionNo(softwareId);
        SoftwareRevision clone = assembler.cloneInto(published, softwareId, userId, nextNo);
        revisionMapper.insert(clone);
        assembler.cloneChildren(published.getId(), clone.getId());

        locked.setWorkingRevisionId(clone.getId());
        locked.setLockVersion(locked.getLockVersion() + 1);
        softwareMapper.updateById(locked);
        auditService.record(softwareId, clone.getId(), 0, userId, "USER",
                "CLONE_WORKING", "APPROVED", "DRAFT", "克隆线上快照为工作副本", null);
        return new CloneResult(softwareId, clone.getId(), locked.getLockVersion());
    }

    private int nextRevisionNo(Long softwareId) {
        Integer max = revisionMapper.selectList(new LambdaQueryWrapper<SoftwareRevision>()
                        .eq(SoftwareRevision::getSoftwareId, softwareId)
                        .orderByDesc(SoftwareRevision::getRevisionNo)
                        .last("LIMIT 1"))
                .stream().findFirst().map(SoftwareRevision::getRevisionNo).orElse(0);
        return (max == null ? 0 : max) + 1;
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

    @Override
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
        return new SoftwareEditorVO(software.getId(), revision.getId(), revision.getRevisionNo(),
                revision.getReviewStatus().name(), revision.getSubmitSeq(), revision.getLockVersion(),
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
                software.getLifecycle().name(), software.getLockVersion(),
                software.getPublishedRevisionId(), software.getWorkingRevisionId());
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
    // 保存草稿（资格矩阵：是；expectedVersion CAS；全量替换）
    // ------------------------------------------------------------------

    @Override
    public SoftwareEditorVO saveDraft(Long userId, Long softwareId, Long revisionId,
                                      Integer expectedVersion, SoftwareSnapshotInput input) {
        publisherPolicy.requireForRestrictedWrite(userId);
        Software software = ownedSoftware(userId, softwareId);
        SoftwareRevision precheck = revisionOfSoftware(software, revisionId);
        requireEditableStatus(precheck);
        // 8.2：绑定在软件库事务外幂等执行（含 icon）；失败保留草稿重试（A21），
        // 绝不在绑定未成功时写 media 行
        List<Long> assets = collectInputAssets(input);
        assetBindingService.bindRevisionAssets(revisionId, userId, assets);

        SoftwareEditorVO editor = transactionTemplate.execute(status -> {
            TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int code) {
                            if (code == org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK) {
                                // 本地回滚：预绑定引用经补偿任务幂等解绑（8.2.4）
                                assetBindingService.registerUnbindCompensation(revisionId, assets);
                            }
                        }
                    });
            SoftwareRevision revision = lockMapper.lockRevision(revisionId);
            if (revision == null || !softwareId.equals(revision.getSoftwareId())) {
                throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
            }
            requireEditableStatus(revision);
            requireVersion(revision, expectedVersion);
            assembler.applyInput(revision, input);
            revision.setLockVersion(revision.getLockVersion() + 1);
            revisionMapper.updateById(revision);
            auditService.record(softwareId, revisionId, revision.getSubmitSeq(), userId, "USER",
                    "SAVE_DRAFT", revision.getReviewStatus().name(), revision.getReviewStatus().name(),
                    "保存草稿", null);
            return null;
        });
        return buildEditor(software, revisionId);
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
                                      Integer expectedVersion, String idempotencyKey) {
        // 幂等重试返回旧结果前也要检查资格与开关（4.0/11.2）
        publisherPolicy.requireForRestrictedWrite(userId);
        if (!softwareProperties.isPublishEnabled()) {
            throw new BusinessException("SOFTWARE_PUBLISH_DISABLED", "软件投稿功能暂未开放");
        }
        // 11.2 限频：单用户每日提交审核上限（在资格/开关校验之后，不改变错误优先级）
        if (!rateLimiter.allowSubmit(userId)) {
            throw new BusinessException("SOFTWARE_RATE_LIMITED", "今日提交次数已达上限，请明天再试");
        }
        Software software = ownedSoftware(userId, softwareId);
        SoftwareRevision snapshot = revisionOfSoftware(software, revisionId);
        requireEditableStatus(snapshot);

        // 8.2.3：所有引用绑定成功才允许 submit——幂等自愈重绑（覆盖补偿缺口）
        assetBindingService.bindRevisionAssets(revisionId, userId, collectSnapshotAssets(snapshot));

        // 远程阶段（事务外，9.7）：文本审核绑定 contentHash/checkedAt + 账号可用性
        List<String> texts = collectReviewTexts(snapshot);
        String contentHash = sha256(String.join("\u0001", texts));
        long mediaCount = revisionMediaMapper.selectCount(
                new LambdaQueryWrapper<SoftwareRevisionMedia>()
                        .eq(SoftwareRevisionMedia::getRevisionId, revisionId));
        SoftwareReviewOutcomeVO outcome = communitySupportFeignClient.review(
                new SoftwareReviewRequest(idempotencyKey, contentHash, texts,
                        Math.toIntExact(mediaCount))).data();
        if (outcome == null || !outcome.approved()) {
            // 审核失败不得发布（9.6）：拒绝级违规词直接拒绝提交
            throw new BusinessException("CONTENT_REJECTED",
                    outcome == null ? "内容审核不可用" : outcome.reason());
        }
        requireAccountAvailable(userId);

        Map<String, Object> submitHashPayload = new HashMap<>();
        submitHashPayload.put("softwareId", softwareId);
        submitHashPayload.put("revisionId", revisionId);
        submitHashPayload.put("expectedVersion", expectedVersion);
        submitHashPayload.put("contentHash", contentHash);
        submitHashPayload.put("checkedAt", String.valueOf(outcome.checkedAt()));
        String operationHash = operationService.requestHash(submitHashPayload);
        // 短本地事务（9.7：本地事务绝不跨远程调用）——远程审核已完成，
        // 此处锁资格行→软件→快照复核并冻结；operation 结果记录同事务提交
        return transactionTemplate.execute(status -> operationService.execute("USER", userId,
                SoftwareOperationService.OP_SUBMIT, idempotencyKey, operationHash, softwareId,
                () -> doSubmitLocked(userId, softwareId, revisionId, expectedVersion, contentHash, outcome),
                json -> objectMapper.readValue(json, SoftwareSubmittedVO.class)));
    }

    /** 提交锁定阶段：仅在 TransactionTemplate 事务内执行（不加 @Transactional 避免自调用失效） */
    private SoftwareSubmittedVO doSubmitLocked(Long userId, Long softwareId, Long revisionId,
                                               Integer expectedVersion, String contentHash,
                                               SoftwareReviewOutcomeVO outcome) {
        // 短本地事务（9.7）：锁资格行→软件主行→快照行，复核归属/状态/版本
        SoftwarePublisherPermission permission = lockMapper.lockPublisherByUser(userId);
        if (permission == null || !Boolean.TRUE.equals(permission.getCanPublish())) {
            throw new BusinessException("SOFTWARE_PUBLISH_NOT_ENABLED", "发布资格已变更，提交被拒绝");
        }
        Software locked = lockMapper.lockSoftware(softwareId);
        if (!userId.equals(locked.getOwnerId()) || locked.getDeletedAt() != null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        if (!revisionId.equals(locked.getWorkingRevisionId())) {
            throw new BusinessException("SOFTWARE_VERSION_CONFLICT", "快照已不是当前工作副本，请刷新");
        }
        SoftwareRevision lockedRevision = lockMapper.lockRevision(revisionId);
        requireEditableStatus(lockedRevision);
        requireVersion(lockedRevision, expectedVersion);

        // 冻结前完整校验（快照必须可发布）
        assembler.validateForSubmit(lockedRevision);

        lockedRevision.setReviewStatus(SoftwareReviewStatus.PENDING);
        lockedRevision.setSubmittedAt(LocalDateTime.now(clock));
        lockedRevision.setSubmitSeq(lockedRevision.getSubmitSeq() + 1);
        lockedRevision.setLockVersion(lockedRevision.getLockVersion() + 1);
        revisionMapper.updateById(lockedRevision);
        auditService.record(softwareId, revisionId, lockedRevision.getSubmitSeq(), userId, "USER",
                "SUBMIT", "DRAFT", "PENDING",
                "contentHash=" + contentHash + ";approved=true;needsManualReview="
                        + outcome.needsManualReview(),
                null);
        return new SoftwareSubmittedVO(softwareId, revisionId, lockedRevision.getSubmitSeq(),
                lockedRevision.getLockVersion());
    }

    /** 审核文本：介绍纯文本 + 更新日志 + 链接说明（9.6：介绍/评论/链接说明一起纳入审核） */
    private List<String> collectReviewTexts(SoftwareRevision snapshot) {
        List<String> texts = new ArrayList<>();
        if (snapshot.getDescriptionText() != null && !snapshot.getDescriptionText().isBlank()) {
            texts.add(snapshot.getDescriptionText());
        }
        for (SoftwareRelease release : releaseMapper.selectList(new LambdaQueryWrapper<SoftwareRelease>()
                .eq(SoftwareRelease::getRevisionId, snapshot.getId()))) {
            if (release.getChangelog() != null && !release.getChangelog().isBlank()) {
                texts.add(release.getChangelog());
            }
        }
        for (SoftwareSource source : sourcesOfRevision(snapshot.getId())) {
            if (source.getNote() != null && !source.getNote().isBlank()) {
                texts.add(source.getNote());
            }
        }
        return texts;
    }

    private List<SoftwareSource> sourcesOfRevision(Long revisionId) {
        List<Long> releaseIds = releaseMapper.selectList(new LambdaQueryWrapper<SoftwareRelease>()
                        .eq(SoftwareRelease::getRevisionId, revisionId))
                .stream().map(SoftwareRelease::getId).toList();
        if (releaseIds.isEmpty()) {
            return List.of();
        }
        return sourceMapper.selectList(new LambdaQueryWrapper<SoftwareSource>()
                .in(SoftwareSource::getReleaseId, releaseIds));
    }

    /** 会员状态核对（9.6）：账号资格验证失败拒绝受限操作，不因资料服务不可用而放行 */
    private void requireAccountAvailable(Long userId) {
        var profiles = userSupportFeignClient.profiles(Map.of("userIds", List.of(userId))).data();
        if (profiles == null || profiles.isEmpty()) {
            throw new BusinessException("USER_SERVICE_UNAVAILABLE", "账号状态不可用，请稍后重试");
        }
        if (!profiles.get(0).accountAvailable()) {
            throw new BusinessException("ACCOUNT_UNAVAILABLE", "账号状态异常，无法执行该操作");
        }
    }

    // ------------------------------------------------------------------
    // 撤回 / 下架 / 重新上架 / 删除（归属校验；撤回/下架/删除不要求资格）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public SoftwareEditorVO withdraw(Long userId, Long softwareId, Long revisionId, Integer expectedVersion) {
        Software software = ownedSoftware(userId, softwareId);
        SoftwareRevision revision = lockRevisionOfSoftware(software, revisionId);
        if (revision.getReviewStatus() != SoftwareReviewStatus.PENDING) {
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", "仅待审快照可撤回");
        }
        requireVersion(revision, expectedVersion);
        revision.setReviewStatus(SoftwareReviewStatus.DRAFT);
        revision.setLockVersion(revision.getLockVersion() + 1);
        revisionMapper.updateById(revision);
        auditService.record(softwareId, revisionId, revision.getSubmitSeq(), userId, "USER",
                "WITHDRAW", "PENDING", "DRAFT", "撤回待审", null);
        return buildEditor(software, revisionId);
    }

    @Override
    @Transactional
    public SoftwareEditorVO unlist(Long userId, Long softwareId, Integer expectedVersion) {
        ownedSoftware(userId, softwareId);
        Software locked = lockMapper.lockSoftware(softwareId);
        requireVersion(locked, expectedVersion);
        if (locked.getLifecycle() != SoftwareLifecycle.ACTIVE) {
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", "仅上架中的软件可下架");
        }
        locked.setLifecycle(SoftwareLifecycle.UNLISTED);
        locked.setLockVersion(locked.getLockVersion() + 1);
        softwareMapper.updateById(locked);
        auditService.record(softwareId, null, null, userId, "USER",
                "UNLIST", "ACTIVE", "UNLISTED", "作者下架", null);
        return buildEditor(locked, locked.getWorkingRevisionId() != null
                ? locked.getWorkingRevisionId() : locked.getPublishedRevisionId());
    }

    @Override
    @Transactional
    public SoftwareEditorVO relist(Long userId, Long softwareId, Integer expectedVersion) {
        // 4.0 矩阵：重新上架要求发布资格（A31/A33：关闭后 relist 也无法绕过）
        publisherPolicy.requireForRestrictedWrite(userId);
        ownedSoftware(userId, softwareId);
        Software locked = lockMapper.lockSoftware(softwareId);
        requireVersion(locked, expectedVersion);
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
        locked.setLockVersion(locked.getLockVersion() + 1);
        softwareMapper.updateById(locked);
        auditService.record(softwareId, null, null, userId, "USER",
                "RELIST", "UNLISTED", "ACTIVE", "作者重新上架", null);
        return buildEditor(locked, locked.getWorkingRevisionId() != null
                ? locked.getWorkingRevisionId() : locked.getPublishedRevisionId());
    }

    @Override
    @Transactional
    public void delete(Long userId, Long softwareId, Integer expectedVersion) {
        ownedSoftware(userId, softwareId);
        Software locked = lockMapper.lockSoftware(softwareId);
        requireVersion(locked, expectedVersion);
        if (locked.getLifecycle() == SoftwareLifecycle.DELETED) {
            return; // 幂等
        }
        locked.setLifecycle(SoftwareLifecycle.DELETED);
        locked.setDeletedAt(LocalDateTime.now(clock));
        locked.setLockVersion(locked.getLockVersion() + 1);
        softwareMapper.updateById(locked);
        auditService.record(softwareId, null, null, userId, "USER",
                "DELETE", locked.getLifecycle() == SoftwareLifecycle.BANNED ? "BANNED" : null,
                "DELETED", "作者删除（软删，审计保留）", null);
    }

    // ------------------------------------------------------------------
    // 管理员审核决策（幂等；A07 旧批次 409；A33 作者资格复核）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public SoftwareReviewDecisionVO reviewDecision(Long adminId, String idempotencyKey, Long softwareId,
                                                   Long revisionId, Integer submitSeq,
                                                   Integer expectedVersion, boolean approve,
                                                   String reason, String requestId) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new BusinessException("SOFTWARE_REVIEW_INVALID", "审核说明须为 1~1000 字");
        }
        Map<String, Object> reviewHashPayload = new HashMap<>();
        reviewHashPayload.put("softwareId", softwareId);
        reviewHashPayload.put("revisionId", revisionId);
        reviewHashPayload.put("submitSeq", submitSeq);
        reviewHashPayload.put("expectedVersion", expectedVersion);
        reviewHashPayload.put("approve", approve);
        reviewHashPayload.put("reason", reason);
        return operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_REVIEW_DECISION,
                idempotencyKey, reviewHashPayload,
                softwareId,
                () -> doReview(adminId, softwareId, revisionId, submitSeq, expectedVersion,
                        approve, reason, requestId),
                json -> objectMapper.readValue(json, SoftwareReviewDecisionVO.class));
    }

    private SoftwareReviewDecisionVO doReview(Long adminId, Long softwareId, Long revisionId,
                                              Integer submitSeq, Integer expectedVersion,
                                              boolean approve, String reason, String requestId) {
        SoftwareRevision precheck = revisionMapper.selectById(revisionId);
        if (precheck == null || !softwareId.equals(precheck.getSoftwareId())
                || precheck.getReviewStatus() != SoftwareReviewStatus.PENDING) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "待审快照不存在");
        }
        if (!Objects.equals(precheck.getSubmitSeq(), submitSeq)) {
            // A07：审核旧批次（撤回后再次提交 submitSeq 已增长）——409，不覆盖新提交
            throw new BusinessException("SOFTWARE_SUBMIT_STALE",
                    "审核的提交批次已过期，请刷新后按最新批次处理");
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
        requireVersion(locked, expectedVersion);
        SoftwareRevision revision = lockMapper.lockRevision(revisionId);
        if (revision.getReviewStatus() != SoftwareReviewStatus.PENDING
                || !Objects.equals(revision.getSubmitSeq(), submitSeq)) {
            throw new BusinessException("SOFTWARE_SUBMIT_STALE",
                    "审核的提交批次已过期，请刷新后按最新批次处理");
        }

        revision.setReviewedAt(LocalDateTime.now(clock));
        revision.setReviewerId(adminId);
        revision.setReviewReason(reason);
        revision.setLockVersion(revision.getLockVersion() + 1);

        if (approve) {
            revision.setReviewStatus(SoftwareReviewStatus.APPROVED);
            revisionMapper.updateById(revision);
            // 事务内切换指针（4.1.3）：published→本快照、working 清空、上线序号+1
            int previousSeq = locked.getPublicationSeq() == null ? 0 : locked.getPublicationSeq();
            locked.setPublishedRevisionId(revisionId);
            locked.setWorkingRevisionId(null);
            locked.setPublicationSeq(previousSeq + 1);
            if (locked.getPublishedAt() == null) {
                locked.setPublishedAt(LocalDateTime.now(clock));
            }
            locked.setLastReleaseAt(LocalDateTime.now(clock));
            locked.setLockVersion(locked.getLockVersion() + 1);
            softwareMapper.updateById(locked);
            auditService.record(softwareId, revisionId, submitSeq, adminId, "ADMIN",
                    "APPROVE", "PENDING", "APPROVED", reason, requestId);
            // Outbox 与指针切换同事务（9.4）：软件事件载荷不含链接/提取码/正文
            outboxService.record(EventEnvelope.of(
                    SoftwareEvents.TAG_REVIEW_APPROVED, SCHEMA_VERSION,
                    String.valueOf(softwareId), locked.getPublicationSeq(), requestId,
                    objectMapper.writeValueAsString(Map.of(
                            "softwareId", String.valueOf(softwareId),
                            "revisionId", String.valueOf(revisionId),
                            "authorId", String.valueOf(locked.getOwnerId()),
                            "publicationSeq", String.valueOf(locked.getPublicationSeq())))));
            // P1 更新订阅（9.4）：非首次上线才广播；事件与 fanout 任务同事务登记
            if (previousSeq > 0) {
                outboxService.record(EventEnvelope.of(
                        SoftwareEvents.TAG_RELEASE_PUBLISHED, SCHEMA_VERSION,
                        String.valueOf(softwareId), locked.getPublicationSeq(), requestId,
                        objectMapper.writeValueAsString(Map.of(
                                "softwareId", String.valueOf(softwareId),
                                "revisionId", String.valueOf(revisionId),
                                "publicationSeq", String.valueOf(locked.getPublicationSeq()),
                                "authorId", String.valueOf(locked.getOwnerId())))));
                fanoutService.registerTask(softwareId, revisionId, locked.getPublicationSeq(),
                        locked.getLastReleaseAt());
            }
            return new SoftwareReviewDecisionVO(softwareId, revisionId, "APPROVED",
                    submitSeq, locked.getPublicationSeq(), locked.getLockVersion());
        }

        revision.setReviewStatus(SoftwareReviewStatus.REJECTED);
        revisionMapper.updateById(revision);
        auditService.record(softwareId, revisionId, submitSeq, adminId, "ADMIN",
                "REJECT", "PENDING", "REJECTED", reason, requestId);
        return new SoftwareReviewDecisionVO(softwareId, revisionId, "REJECTED",
                submitSeq, locked.getPublicationSeq(), locked.getLockVersion());
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
        Page<Software> softwarePage = softwareMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<Software>()
                        .eq(Software::getOwnerId, userId)
                        .isNull(Software::getDeletedAt)
                        .orderByDesc(Software::getUpdatedAt));
        Page<SoftwareMineItemVO> result = new Page<>(softwarePage.getCurrent(),
                softwarePage.getSize(), softwarePage.getTotal());
        result.setRecords(softwarePage.getRecords().stream()
                .map(software -> mineItem(software, normalizedTab))
                .filter(java.util.Objects::nonNull)
                .toList());
        return result;
    }

    /** 按工作/发布快照状态归入请求的 tab（一条软件只出现在其当前状态 tab） */
    private SoftwareMineItemVO mineItem(Software software, String tab) {
        Long revisionId = software.getWorkingRevisionId() != null
                ? software.getWorkingRevisionId() : software.getPublishedRevisionId();
        SoftwareRevision revision = revisionId == null ? null : revisionMapper.selectById(revisionId);
        String status = resolveMineStatus(software, revision);
        if (!tab.equals(status)) {
            return null;
        }
        return new SoftwareMineItemVO(software.getId(),
                revision == null ? "" : revision.getName(),
                revision == null ? "" : revision.getSummary(),
                status, revisionId, software.getLockVersion(),
                software.getFavoriteCount(), software.getUpdatedAt(), false);
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
                                software.getLockVersion(), software.getFavoriteCount(),
                                software.getUpdatedAt(), false);
                    }
                    return new SoftwareMineItemVO(software.getId(), "", "", "UNAVAILABLE",
                            null, null, null, null, true);
                })
                .filter(Objects::nonNull)
                .toList());
        return result;
    }

    private String resolveMineStatus(Software software, SoftwareRevision revision) {
        if (revision == null) {
            return "DRAFT";
        }
        if (revision.getReviewStatus() == SoftwareReviewStatus.PENDING) {
            return "PENDING";
        }
        if (revision.getReviewStatus() == SoftwareReviewStatus.REJECTED) {
            return "REJECTED";
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
                                software.getLockVersion(), software.getFavoriteCount(),
                                software.getUpdatedAt(), false);
                    }
                    // 不可见占位：保留 id 供取消收藏，不泄露名称/摘要/状态
                    return new SoftwareMineItemVO(software.getId(), "", "", "UNAVAILABLE",
                            null, null, null, null, true);
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
    private SoftwareRevision lockRevisionOfSoftware(Software software, Long revisionId) {
        SoftwareRevision revision = lockMapper.lockRevision(revisionId);
        if (revision == null || !software.getId().equals(revision.getSoftwareId())) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        return revision;
    }

    private void requireEditableStatus(SoftwareRevision revision) {
        if (revision.getReviewStatus() != SoftwareReviewStatus.DRAFT
                && revision.getReviewStatus() != SoftwareReviewStatus.REJECTED) {
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", "仅草稿/退回快照可编辑");
        }
    }

    /** 乐观锁校验（A08：并发保存/两标签页仅一方成功） */
    private void requireVersion(Object entity, Integer expectedVersion) {
        Integer actual;
        if (entity instanceof Software software) {
            actual = software.getLockVersion();
        } else if (entity instanceof SoftwareRevision revision) {
            actual = revision.getLockVersion();
        } else {
            throw new IllegalStateException("不支持的乐观锁实体");
        }
        if (expectedVersion == null || !expectedVersion.equals(actual)) {
            throw new BusinessException("SOFTWARE_VERSION_CONFLICT",
                    "内容已被其他页面修改，请刷新后重试");
        }
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
