package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetAlbumAsset;
import com.cloudmart.pet.entity.PetDiaryEntry;
import com.cloudmart.pet.entity.PetMemory;
import com.cloudmart.pet.entity.PetOnboardingProgress;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMemoryMapper;
import com.cloudmart.pet.repository.PetAlbumAssetMapper;
import com.cloudmart.pet.repository.PetDiaryEntryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetOnboardingProgressMapper;
import com.cloudmart.pet.util.PetJsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 陪伴功能后端（N01 新手引导 / N02 成长日记与相册 / N03 记忆管理）。
 *
 * <p>N01：进度由真实领域事件推进（feed/play/work/decorate 完成后调用 {@link #recordStep}），
 * 客户端不能提交 completed；关闭应用后进度持久；跳过不伪造步骤；基础家具赠送走 B01
 * 操作记录幂等（每用户一次）。</p>
 *
 * <p>N02：日记由领域事件生成（eventId 唯一去重，不可变快照）；相册仅主人可见，
 * 资源引用 mall-file 授权访问（不抓取任意外部 URL）。</p>
 *
 * <p>N03：记忆按宠物隔离；用户编辑（source=USER）不被自动抽取覆盖；删除=enabled=0
 * （删除标记防复活）；提取/使用开关独立；全部接口校验归属，记忆不进公开接口。</p>
 */
@Service
@Slf4j
public class PetCompanionFeatureService {

    public static final String GUIDE_VERSION = "V1";
    private static final List<String> STEPS = List.of("FEED", "PLAY", "WORK", "DECORATE");

    private final PetMapper petMapper;
    private final PetOnboardingProgressMapper onboardingMapper;
    private final PetDiaryEntryMapper diaryMapper;
    private final PetAlbumAssetMapper albumMapper;
    private final PetMemoryMapper memoryMapper;
    private final PetOperationService operationService;
    private final PetInventoryMapper inventoryMapper;
    private final com.cloudmart.pet.config.PetProperties properties;
    private final com.cloudmart.pet.repository.PetNotifyPrefMapper notifyPrefMapper;
    private final com.cloudmart.pet.service.PetUserGuardService guardService;
    private final com.cloudmart.pet.feign.FileFeignClient fileFeignClient;

    public PetCompanionFeatureService(PetMapper petMapper,
                                      PetOnboardingProgressMapper onboardingMapper,
                                      PetDiaryEntryMapper diaryMapper,
                                      PetAlbumAssetMapper albumMapper,
                                      PetMemoryMapper memoryMapper,
                                      PetOperationService operationService,
                                      PetInventoryMapper inventoryMapper,
                                      com.cloudmart.pet.config.PetProperties properties,
                                      com.cloudmart.pet.repository.PetNotifyPrefMapper notifyPrefMapper,
                                      com.cloudmart.pet.service.PetUserGuardService guardService,
                                      com.cloudmart.pet.feign.FileFeignClient fileFeignClient) {
        this.petMapper = petMapper;
        this.onboardingMapper = onboardingMapper;
        this.diaryMapper = diaryMapper;
        this.albumMapper = albumMapper;
        this.memoryMapper = memoryMapper;
        this.operationService = operationService;
        this.inventoryMapper = inventoryMapper;
        this.properties = properties;
        this.notifyPrefMapper = notifyPrefMapper;
        this.guardService = guardService;
        this.fileFeignClient = fileFeignClient;
    }

    /** B19：查询/更新宠物通知偏好（免打扰 + 日常问候开关）；重要业务通知不受偏好影响 */
    public com.cloudmart.pet.entity.PetNotifyPref notifyPrefs(Long userId) {
        com.cloudmart.pet.entity.PetNotifyPref pref = notifyPrefMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetNotifyPref>()
                        .eq(com.cloudmart.pet.entity.PetNotifyPref::getUserId, userId));
        if (pref == null) {
            pref = new com.cloudmart.pet.entity.PetNotifyPref();
            pref.setUserId(userId);
            pref.setMuteDailyGreeting(false);
            pref.setDailyGreetingEnabled(true);
            try {
                notifyPrefMapper.insert(pref);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                pref = notifyPrefMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetNotifyPref>()
                                .eq(com.cloudmart.pet.entity.PetNotifyPref::getUserId, userId));
            }
        }
        return pref;
    }

    public com.cloudmart.pet.entity.PetNotifyPref updateNotifyPrefs(Long userId, boolean mute, boolean greeting) {
        com.cloudmart.pet.entity.PetNotifyPref pref = notifyPrefs(userId);
        pref.setMuteDailyGreeting(mute);
        pref.setDailyGreetingEnabled(greeting);
        notifyPrefMapper.updateById(pref);
        return pref;
    }

    private void requireFeature(boolean enabled) {
        if (!enabled) {
            throw new BusinessException(PetErrorCodes.PET_FEATURE_DISABLED, "该功能暂未开放");
        }
    }

    // ---------------- N01 新手引导 ----------------

    /** 查询引导进度（首次进入自动建档；旧用户可跳过，不伪造步骤） */
    public Map<String, Object> onboarding(Long userId) {
        requireFeature(properties.getFeatureSwitches().isOnboarding());
        PetOnboardingProgress progress = requireProgress(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("guideVersion", progress.getGuideVersion());
        result.put("steps", PetJsonUtils.parse(progress.getSteps(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                }));
        result.put("completed", progress.getCompletedAt() != null);
        result.put("skipped", progress.getSkippedAt() != null);
        result.put("canSkip", progress.getCompletedAt() == null && progress.getSkippedAt() == null);
        return result;
    }

    /** 领域事件推进步骤（幂等：DONE 不回退）；全部完成置 completed */
    @Transactional
    public void recordStep(Long userId, String step) {
        if (!STEPS.contains(step)) {
            return;
        }
        PetOnboardingProgress progress = onboardingMapper.selectOne(
                new LambdaQueryWrapper<PetOnboardingProgress>().eq(PetOnboardingProgress::getUserId, userId));
        if (progress == null || progress.getCompletedAt() != null || progress.getSkippedAt() != null) {
            return;
        }
        Map<String, String> steps = PetJsonUtils.parse(progress.getSteps(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                });
        if (steps == null || "DONE".equals(steps.get(step))) {
            return;
        }
        steps.put(step, "DONE");
        progress.setSteps(PetJsonUtils.toJson(steps));
        if (STEPS.stream().allMatch(s -> "DONE".equals(steps.get(s)))) {
            progress.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        onboardingMapper.updateById(progress);
    }

    /** 跳过引导（幂等；不影响正常养成） */
    @Transactional
    public void skipOnboarding(Long userId) {
        PetOnboardingProgress progress = requireProgress(userId);
        if (progress.getSkippedAt() == null && progress.getCompletedAt() == null) {
            progress.setSkippedAt(LocalDateTime.now(ZoneOffset.UTC));
            onboardingMapper.updateById(progress);
        }
    }

    /** 新手基础家具赠送（N01）：每用户一次，走 B01 操作记录幂等，重试不重复入包 */
    @Transactional
    public void grantStarterFurniture(Long userId) {
        PetOnboardingProgress progress = requireProgress(userId);
        if (progress.getFurnitureGrantOpId() != null) {
            return;
        }
        String operationId = operationService.operationKey("ONBOARDING_GIFT", userId, GUIDE_VERSION);
        PetOnboardingProgress locked = onboardingMapper.selectOne(
                new LambdaQueryWrapper<PetOnboardingProgress>().eq(PetOnboardingProgress::getUserId, userId));
        locked.setFurnitureGrantOpId(operationId);
        onboardingMapper.updateById(locked);
        // 赠送 0 元家具直接入包（uk_pet_inventory_item 幂等；失败回滚 operationId 标记可重试）
        Pet pet = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId).eq(Pet::getIsActive, true).last("LIMIT 1"));
        if (pet != null) {
            com.cloudmart.pet.entity.PetInventory gift = new com.cloudmart.pet.entity.PetInventory();
            gift.setPetId(pet.getId());
            gift.setUserId(userId);
            gift.setItemType("FURNITURE");
            gift.setItemCode("starter_rug");
            gift.setQuantity(1);
            gift.setEquipped(false);
            gift.setAcquiredAt(LocalDateTime.now(ZoneOffset.UTC));
            try {
                inventoryMapper.insert(gift);
            } catch (DuplicateKeyException e) {
                log.debug("新手家具已拥有（幂等跳过）: userId={}", userId);
            }
        }
    }

    private PetOnboardingProgress requireProgress(Long userId) {
        PetOnboardingProgress progress = onboardingMapper.selectOne(
                new LambdaQueryWrapper<PetOnboardingProgress>().eq(PetOnboardingProgress::getUserId, userId));
        if (progress == null) {
            progress = new PetOnboardingProgress();
            progress.setUserId(userId);
            progress.setGuideVersion(GUIDE_VERSION);
            Map<String, String> steps = new HashMap<>();
            STEPS.forEach(s -> steps.put(s, "OPEN"));
            progress.setSteps(PetJsonUtils.toJson(steps));
            try {
                onboardingMapper.insert(progress);
            } catch (DuplicateKeyException e) {
                progress = onboardingMapper.selectOne(
                        new LambdaQueryWrapper<PetOnboardingProgress>().eq(PetOnboardingProgress::getUserId, userId));
            }
        }
        return progress;
    }

    // ---------------- N02 成长日记与相册 ----------------

    /** 日记写入（领域事件调用，eventId 唯一去重；客户端不可伪造） */
    @Transactional
    public void recordDiary(Long petId, Long userId, String eventId, String eventType, String snapshotJson) {
        PetDiaryEntry entry = new PetDiaryEntry();
        entry.setPetId(petId);
        entry.setUserId(userId);
        entry.setEventId(eventId);
        entry.setEventType(eventType);
        entry.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setSnapshot(snapshotJson);
        entry.setVisibility("OWNER_ONLY");
        try {
            diaryMapper.insert(entry);
        } catch (DuplicateKeyException e) {
            log.debug("日记事件已记录（幂等跳过）: petId={}, eventId={}", petId, eventId);
        }
    }

    /**
     * 时间线（游标分页；他人仅可见 PUBLIC 条目）。
     * R03：返回显式 VO（items/type/content/visibility/assetIds），不再吐数据库实体
     * 让三端猜字段；OWNER_ONLY 对外统一映射 PRIVATE（客户端枚举无 OWNER_ONLY）。
     */
    public Map<String, Object> diary(Long userId, Long petId, String cursor, int size) {
        requireFeature(properties.getFeatureSwitches().isDiary());
        Pet pet = petMapper.selectById(petId);
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "宠物不存在");
        }
        boolean owner = pet.getUserId().equals(userId);
        LambdaQueryWrapper<PetDiaryEntry> wrapper = new LambdaQueryWrapper<PetDiaryEntry>()
                .eq(PetDiaryEntry::getPetId, petId)
                .orderByDesc(PetDiaryEntry::getId)
                .last("LIMIT " + Math.min(Math.max(size, 1), 50));
        if (!owner) {
            wrapper.eq(PetDiaryEntry::getVisibility, "PUBLIC");
        }
        if (cursor != null && !cursor.isBlank()) {
            wrapper.lt(PetDiaryEntry::getId, Long.parseLong(cursor));
        }
        List<PetDiaryEntry> entries = diaryMapper.selectList(wrapper);
        String nextCursor = entries.size() == Math.min(Math.max(size, 1), 50)
                && !entries.isEmpty() ? String.valueOf(entries.get(entries.size() - 1).getId()) : null;
        Map<String, Object> result = new HashMap<>();
        result.put("items", entries.stream().map(DiaryEntryVO::of).toList());
        result.put("nextCursor", nextCursor);
        result.put("hasMore", nextCursor != null);
        return result;
    }

    /** R04 相册文件约束：PRIVATE、图片三类、≤5MiB——由文件服务按台账裁决，本地不做正则放行 */
    private static final List<String> ALBUM_ALLOWED_MIMES = List.of("image/jpeg", "image/png", "image/webp");
    private static final long ALBUM_MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final String ALBUM_BIZ_TYPE = "PET_ALBUM";

    /**
     * 上传相册资源（N02/R04）：fileId 为不透明 ID，先本地落 BINDING 行（占配额 + 日记归属校验），
     * 再调 mall-file 内部接口校验归属/READY/MIME/大小/PRIVATE 并登记幂等引用键
     * PET_ALBUM:{albumAssetId}——成功推进 BOUND+PENDING_REVIEW，远程失败留 BINDING 可重试
     * （删除该行即放弃绑定，孤儿文件由用户在文件服务侧自行清理，不占公开访问）。
     * 100 张配额含 BINDING 行（守卫行锁内串行化，T27）。
     */
    @Transactional
    public PetAlbumAsset uploadAlbumAsset(Long userId, Long petId, String fileId, Long diaryEntryId) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能管理自己宠物的相册");
        }
        Long fileAssetId = parseFileAssetId(fileId);
        if (diaryEntryId != null) {
            com.cloudmart.pet.entity.PetDiaryEntry diary = diaryMapper.selectById(diaryEntryId);
            if (diary == null || !diary.getUserId().equals(userId) || !diary.getPetId().equals(petId)) {
                throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "日记归属与相册不符");
            }
        }
        // 用户守卫行锁：并发上传的配额核验串行化（BE-11/T27：第 100/101 张不越界）
        guardService.lockGuard(userId);
        Long count = albumMapper.selectCount(new LambdaQueryWrapper<PetAlbumAsset>()
                .eq(PetAlbumAsset::getUserId, userId));
        if (count != null && count >= 100) {
            throw new BusinessException(PetErrorCodes.PET_QUOTA_EXHAUSTED, "相册已满（100 张）");
        }
        PetAlbumAsset asset = new PetAlbumAsset();
        asset.setUserId(userId);
        asset.setPetId(petId);
        asset.setDiaryEntryId(diaryEntryId);
        asset.setFileId(String.valueOf(fileAssetId));
        asset.setAuditStatus("PENDING");
        asset.setBindStatus("BINDING");
        try {
            albumMapper.insert(asset);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "该资源已存在");
        }
        // 本地 BINDING 行已提交；远程绑定失败不回滚本地行（跨服务无分布式事务）——
        // 行保留 BINDING 可重试状态，由 confirmAlbumBinding 推进/放弃
        confirmAlbumBinding(asset, fileAssetId, userId);
        return albumMapper.selectById(asset.getId());
    }

    /** 远程绑定（幂等引用键 PET_ALBUM:{id}）：成功 BOUND；失败留 BINDING 并抛出明确错误 */
    private void confirmAlbumBinding(PetAlbumAsset asset, Long fileAssetId, Long userId) {
        try {
            var response = fileFeignClient.bindReference(fileAssetId,
                    new com.cloudmart.pet.feign.FileFeignClient.BindReferenceRequest(
                            ALBUM_BIZ_TYPE, String.valueOf(asset.getId()), userId,
                            ALBUM_ALLOWED_MIMES, ALBUM_MAX_SIZE_BYTES, "PRIVATE"));
            if (response.data() == null) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "文件绑定未受理");
            }
            asset.setBindStatus("BOUND");
            albumMapper.updateById(asset);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("相册文件远程绑定失败（行保留 BINDING 可重试）, albumAssetId={}, fileId={}",
                    asset.getId(), fileAssetId, e);
            throw new BusinessException("PET_FILE_BINDING_FAILED", "文件绑定失败，请稍后重试或删除该条目");
        }
    }

    /** R04：BINDING 行补绑定（上传后响应丢失/远程失败重试的恢复入口） */
    @Transactional
    public PetAlbumAsset retryAlbumBinding(Long userId, Long assetId) {
        PetAlbumAsset asset = albumMapper.selectById(assetId);
        if (asset == null || !asset.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能管理自己宠物的相册");
        }
        if (!"BINDING".equals(asset.getBindStatus())) {
            return asset;
        }
        Long fileAssetId = parseFileAssetId(asset.getFileId());
        confirmAlbumBinding(asset, fileAssetId, userId);
        return albumMapper.selectById(assetId);
    }

    /** fileId 为十进制数字串（mall-file 资产台账自增 ID），URL/路径形态一律拒绝 */
    private Long parseFileAssetId(String fileId) {
        if (fileId == null || fileId.isBlank() || fileId.length() > 19) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "文件引用非法（fileId 必填）");
        }
        try {
            Long id = Long.parseLong(fileId.trim());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "仅支持文件服务返回的 fileId（不接受 URL/路径）");
        }
    }

    /** R04 审核队列：默认 PENDING 且已绑定；最旧优先 */
    public List<PetAlbumAsset> albumReviewQueue(String auditStatus) {
        return albumMapper.selectList(new LambdaQueryWrapper<PetAlbumAsset>()
                .eq(PetAlbumAsset::getAuditStatus, auditStatus)
                .eq(PetAlbumAsset::getBindStatus, "BOUND")
                .orderByAsc(PetAlbumAsset::getId)
                .last("LIMIT 100"));
    }

    /**
     * 审核通过（R04）：仅 BOUND+PENDING 可通过；已删除/已处理对象不能被旧 approve 复活
     * （条件更新，0 行即状态已推进）。
     */
    @Transactional
    public PetAlbumAsset approveAlbumAsset(Long assetId, Long reviewerId) {
        int updated = albumMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetAlbumAsset>()
                .set(PetAlbumAsset::getAuditStatus, "APPROVED")
                .set(PetAlbumAsset::getReviewerId, reviewerId)
                .set(PetAlbumAsset::getReviewReason, null)
                .eq(PetAlbumAsset::getId, assetId)
                .eq(PetAlbumAsset::getAuditStatus, "PENDING")
                .eq(PetAlbumAsset::getBindStatus, "BOUND"));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "相册资源不存在、未绑定完成或已处理，不能重复审核");
        }
        return albumMapper.selectById(assetId);
    }

    /** 审核驳回（R04）：理由必填留痕；被驳回条目保留在相册（状态可见），不可公开 */
    @Transactional
    public PetAlbumAsset rejectAlbumAsset(Long assetId, Long reviewerId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "驳回理由必填");
        }
        int updated = albumMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetAlbumAsset>()
                .set(PetAlbumAsset::getAuditStatus, "REJECTED")
                .set(PetAlbumAsset::getReviewerId, reviewerId)
                .set(PetAlbumAsset::getReviewReason, reason.strip())
                .eq(PetAlbumAsset::getId, assetId)
                .eq(PetAlbumAsset::getAuditStatus, "PENDING")
                .eq(PetAlbumAsset::getBindStatus, "BOUND"));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "相册资源不存在、未绑定完成或已处理，不能重复审核");
        }
        return albumMapper.selectById(assetId);
    }

    /**
     * 删除相册资源（R04）：本地行立即删除（停止一切新授权），随后尽力解绑远程文件引用
     * （幂等：引用不存在也成功；解绑失败仅告警——文件服务侧引用残留只影响文件删除检查，
     * 不产生任何宠物业务可见性，用户可重传/重新绑定同 fileId）。
     */
    @Transactional
    public void deleteAlbumAsset(Long userId, Long assetId) {
        PetAlbumAsset asset = albumMapper.selectById(assetId);
        if (asset == null || !asset.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能删除自己上传的资源");
        }
        albumMapper.deleteById(assetId);
        try {
            fileFeignClient.unbindReference(Long.parseLong(asset.getFileId()), ALBUM_BIZ_TYPE,
                    String.valueOf(asset.getId()));
        } catch (Exception e) {
            log.warn("相册文件远程解绑失败（引用键幂等可重试）, albumAssetId={}, fileId={}",
                    assetId, asset.getFileId(), e);
        }
    }

    /**
     * R04 相册列表：按权限签发短期预览地址（默认 60 秒），不返回任何持久化 URL——
     * PENDING/REJECTED 对他人不可见；BINDING 行返回处理中占位（无地址）。
     */
    public List<AlbumAssetVO> albumList(Long userId, Long petId) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "宠物不存在");
        }
        boolean owner = pet.getUserId().equals(userId);
        LambdaQueryWrapper<PetAlbumAsset> wrapper = new LambdaQueryWrapper<PetAlbumAsset>()
                .eq(PetAlbumAsset::getPetId, petId)
                .orderByDesc(PetAlbumAsset::getId);
        if (!owner) {
            // 访客：仅审核通过且已绑定完成的条目可预览
            wrapper.eq(PetAlbumAsset::getAuditStatus, "APPROVED")
                    .eq(PetAlbumAsset::getBindStatus, "BOUND");
        }
        List<PetAlbumAsset> assets = albumMapper.selectList(wrapper);
        return assets.stream().map(asset -> toAlbumVo(asset, owner)).toList();
    }

    private AlbumAssetVO toAlbumVo(PetAlbumAsset asset, boolean owner) {
        String previewUrl = null;
        if ("BOUND".equals(asset.getBindStatus())) {
            try {
                var response = fileFeignClient.internalDownloadUrl(Long.parseLong(asset.getFileId()), 60L);
                if (response.data() != null) {
                    previewUrl = String.valueOf(response.data().getOrDefault("downloadPath", ""));
                }
            } catch (Exception e) {
                // 预览地址签发失败：条目仍返回（状态可见），URL 为空由前端显示占位
                log.warn("相册预览地址签发失败, albumAssetId={}", asset.getId(), e);
            }
        }
        return new AlbumAssetVO(String.valueOf(asset.getId()), asset.getPetId() == null ? null : String.valueOf(asset.getPetId()),
                asset.getDiaryEntryId() == null ? null : String.valueOf(asset.getDiaryEntryId()),
                String.valueOf(asset.getFileId()), asset.getAuditStatus(), asset.getBindStatus(),
                asset.getReviewReason(), previewUrl,
                asset.getCreatedAt() == null ? null : asset.getCreatedAt().toString());
    }

    /** R04 相册条目 VO：fileId 为不透明 ID 字符串，预览地址为短期授权路径（禁止持久化） */
    public record AlbumAssetVO(String assetId, String petId, String diaryEntryId, String fileId,
                               String auditStatus, String bindStatus, String reviewReason,
                               String previewUrl, String createdAt) {
    }

    // ---------------- N03 记忆管理 ----------------

    /** 按宠物查询记忆（归属校验；记忆不进公开接口） */
    public List<PetMemory> memories(Long userId, Long petId) {
        requireOwner(userId, petId);
        return memoryMapper.selectList(new LambdaQueryWrapper<PetMemory>()
                .eq(PetMemory::getPetId, petId)
                .eq(PetMemory::getEnabled, true)
                .orderByDesc(PetMemory::getId));
    }

    /** 用户编辑记忆（source=USER 优先，不被自动抽取覆盖） */
    @Transactional
    public PetMemory editMemory(Long userId, Long petId, Long memoryId, String value) {
        requireOwner(userId, petId);
        PetMemory memory = memoryMapper.selectById(memoryId);
        if (memory == null || !memory.getPetId().equals(petId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "记忆不存在");
        }
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "记忆内容需 1-100 字");
        }
        memory.setMemoryValue(value);
        memory.setSource("USER");
        memoryMapper.updateById(memory);
        return memory;
    }

    /** 删除记忆（enabled=0 删除标记；旧消息重试不会复活） */
    @Transactional
    public void deleteMemory(Long userId, Long petId, Long memoryId) {
        requireOwner(userId, petId);
        PetMemory memory = memoryMapper.selectById(memoryId);
        if (memory == null || !memory.getPetId().equals(petId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "记忆不存在");
        }
        memory.setEnabled(false);
        memoryMapper.updateById(memory);
    }

    /** 批量清空（软删标记） */
    @Transactional
    public void clearMemories(Long userId, Long petId) {
        requireOwner(userId, petId);
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetMemory> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetMemory>()
                        .set(PetMemory::getEnabled, false)
                        .eq(PetMemory::getPetId, petId);
        memoryMapper.update(null, wrapper);
    }

    /**
     * R03：记忆设置读取（T11——三端先 GET 再编辑，不得以默认 true/true 覆盖服务端已关闭设置）。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> memorySettings(Long userId, Long petId) {
        requireOwner(userId, petId);
        Pet pet = petMapper.selectById(petId);
        return Map.of(
                "extract", Boolean.TRUE.equals(pet.getMemoryExtractEnabled()),
                "use", Boolean.TRUE.equals(pet.getMemoryUseEnabled()));
    }

    /** 记忆开关（提取/使用独立）；返回持久化后的值（§7.2 PUT 返回持久化结果） */
    @Transactional
    public Map<String, Object> toggleMemory(Long userId, Long petId, boolean extract, boolean use) {
        requireOwner(userId, petId);
        Pet pet = petMapper.selectById(petId);
        pet.setMemoryExtractEnabled(extract);
        pet.setMemoryUseEnabled(use);
        petMapper.updateById(pet);
        return Map.of("extract", extract, "use", use);
    }

    /**
     * R03 日记条目 VO（三端契约字段：id/petId/type/content/visibility/assetIds/createdAt/occurredAt）。
     * content 从事件载荷 JSON 渲染（未知事件类型返回空串安全文案，不吐原始 snapshot）；
     * OWNER_ONLY 对外统一映射 PRIVATE。
     */
    public record DiaryEntryVO(Long id, Long petId, String type, String content, String visibility,
                               java.util.List<Long> assetIds, java.time.LocalDateTime createdAt,
                               java.time.LocalDateTime occurredAt) {

        static DiaryEntryVO of(PetDiaryEntry entry) {
            Map<String, Object> payload = PetJsonUtils.parse(entry.getSnapshot(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
            String content = payload != null && payload.get("content") != null
                    ? String.valueOf(payload.get("content")) : "";
            Object rawAssetIds = payload != null ? payload.get("assetIds") : null;
            java.util.List<Long> assetIdList = rawAssetIds instanceof List<?> list
                    ? list.stream()
                            .map(v -> v instanceof Number number ? number.longValue() : null)
                            .filter(java.util.Objects::nonNull)
                            .toList()
                    : java.util.List.of();
            String visibility = "PUBLIC".equals(entry.getVisibility()) ? "PUBLIC" : "PRIVATE";
            return new DiaryEntryVO(entry.getId(), entry.getPetId(), entry.getEventType(), content,
                    visibility, assetIdList, entry.getCreatedAt(), entry.getOccurredAt());
        }
    }

    private void requireOwner(Long userId, Long petId) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "无权访问该宠物的记忆");
        }
    }
}
