package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
    private final PetInventoryMapper inventoryMapper;
    private final com.cloudmart.pet.config.PetProperties properties;
    private final com.cloudmart.pet.repository.PetNotifyPrefMapper notifyPrefMapper;
    private final com.cloudmart.pet.service.PetUserGuardService guardService;
    private final com.cloudmart.pet.feign.FileFeignClient fileFeignClient;
    /** PET-13/T32：相册跨服务绑定的 prepare/confirm 短事务（远端调用不进事务） */
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public PetCompanionFeatureService(PetMapper petMapper,
                                      PetOnboardingProgressMapper onboardingMapper,
                                      PetDiaryEntryMapper diaryMapper,
                                      PetAlbumAssetMapper albumMapper,
                                      PetMemoryMapper memoryMapper,
                                      PetInventoryMapper inventoryMapper,
                                      com.cloudmart.pet.config.PetProperties properties,
                                      com.cloudmart.pet.repository.PetNotifyPrefMapper notifyPrefMapper,
                                      com.cloudmart.pet.service.PetUserGuardService guardService,
                                      com.cloudmart.pet.feign.FileFeignClient fileFeignClient,
                                      org.springframework.transaction.support.TransactionTemplate transactionTemplate) {
        this.petMapper = petMapper;
        this.onboardingMapper = onboardingMapper;
        this.diaryMapper = diaryMapper;
        this.albumMapper = albumMapper;
        this.memoryMapper = memoryMapper;
        this.inventoryMapper = inventoryMapper;
        this.properties = properties;
        this.notifyPrefMapper = notifyPrefMapper;
        this.guardService = guardService;
        this.fileFeignClient = fileFeignClient;
        this.transactionTemplate = transactionTemplate;
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

    public Map<String, Object> updateNotifyPrefs(Long userId, boolean mute, boolean greeting,
                                                 Integer expectedVersion) {
        com.cloudmart.pet.entity.PetNotifyPref pref = notifyPrefs(userId);
        pref.setMuteDailyGreeting(mute);
        pref.setDailyGreetingEnabled(greeting);
        if (expectedVersion != null) {
            // §7.2：expectedVersion CAS 防多端覆盖；@Version 拦截在 updateById 返回 0
            pref.setVersion(expectedVersion);
            if (notifyPrefMapper.updateById(pref) == 0) {
                throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                        "通知偏好已被其他端修改，请刷新后重试");
            }
        } else {
            // 旧请求无版本按兼容窗口直接生效（记录使用量，为收窄窗口提供观测）
            log.info("[COMPAT] 通知偏好旧版无版本写入: userId={}", userId);
            notifyPrefMapper.updateById(pref);
        }
        com.cloudmart.pet.entity.PetNotifyPref refreshed = notifyPrefs(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("muteDailyGreeting", Boolean.TRUE.equals(refreshed.getMuteDailyGreeting()));
        result.put("dailyGreetingEnabled", Boolean.TRUE.equals(refreshed.getDailyGreetingEnabled()));
        result.put("version", refreshed.getVersion() == null ? 1 : refreshed.getVersion());
        return result;
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

    /**
     * 领域事件推进步骤（幂等：DONE 不回退）；全部完成置 completed。
     * R39：用户守卫行锁串行化并发步骤推进——原实现读改写整份 steps JSON 无锁，
     * 并发 FEED+PLAY 相互覆盖丢步骤（T73）。
     */
    @Transactional
    public void recordStep(Long userId, String step) {
        if (!STEPS.contains(step)) {
            return;
        }
        guardService.lockGuard(userId);
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
        // P03 二阶段：LEGACY operationKey 已删——本地确定性键（同格式 bizType:part1:part2）
        String operationId = "ONBOARDING_GIFT:" + userId + ":" + GUIDE_VERSION;
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

    /**
     * §7.2 PATCH /pets/{petId}/diary/{entryId}：统一可见性入口——仅本人，expectedVersion CAS
     * 防多端互相覆盖。客户端枚举无 OWNER_ONLY：入参 PRIVATE 按兼容映射为 OWNER_ONLY
     * （与查询出参的 OWNER_ONLY→PRIVATE 映射对称）。
     */
    @Transactional
    public Map<String, Object> updateDiaryVisibility(Long userId, Long petId, Long entryId,
                                                     String visibility, Integer expectedVersion) {
        requireFeature(properties.getFeatureSwitches().isDiary());
        Pet pet = petMapper.selectById(petId);
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "宠物不存在");
        }
        if (!pet.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能管理自己宠物的日记");
        }
        String targetVisibility = switch (visibility == null ? "" : visibility) {
            case "PUBLIC" -> "PUBLIC";
            case "OWNER_ONLY", "PRIVATE" -> "OWNER_ONLY";
            default -> throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "visibility 仅支持 PUBLIC/OWNER_ONLY");
        };
        if (expectedVersion == null || expectedVersion < 1) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "expectedVersion 必填（先 GET 再编辑）");
        }
        PetDiaryEntry entry = diaryMapper.selectById(entryId);
        if (entry == null || !entry.getPetId().equals(petId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "日记条目不存在");
        }
        int updated = diaryMapper.update(null, new LambdaUpdateWrapper<PetDiaryEntry>()
                .set(PetDiaryEntry::getVisibility, targetVisibility)
                .setSql("version = version + 1")
                .eq(PetDiaryEntry::getId, entryId)
                .eq(PetDiaryEntry::getVersion, expectedVersion));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "日记已被其他端修改，请刷新后重试");
        }
        return Map.of("entryId", entryId, "visibility", targetVisibility, "version", expectedVersion + 1);
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
    /**
     * PET-13/T32：上传相册资源——跨服务绑定三段式（§7.3）：prepare 短事务（占配额 +
     * 落 BINDING 行并提交）→ 远端幂等绑定（事务外，引用键 PET_ALBUM:{id}）→ confirm
     * 短事务（BINDING→BOUND）。远端失败不回滚 BINDING 行（原实现同事务内抛异常把
     * "行保留可重试"的注释变成谎言——行被一起回滚，远端已成功的引用成幽灵），
     * 行留 BINDING + 退避重试时间，由调度恢复任务与用户 retry-binding 收敛。
     *
     * <p>100 张配额含 BINDING 行（守卫行锁内串行化，T27）。§13.1：albumBinding 关闭时
     * 跳过远程校验（行留 BINDING，重试入口在开关恢复后生效）。</p>
     */
    public PetAlbumAsset uploadAlbumAsset(Long userId, Long petId, String fileId, Long diaryEntryId, String caption) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能管理自己宠物的相册");
        }
        String normalizedCaption = normalizeCaption(caption);
        Long fileAssetId = parseFileAssetId(fileId);
        if (diaryEntryId != null) {
            com.cloudmart.pet.entity.PetDiaryEntry diary = diaryMapper.selectById(diaryEntryId);
            if (diary == null || !diary.getUserId().equals(userId) || !diary.getPetId().equals(petId)) {
                throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "日记归属与相册不符");
            }
        }
        // prepare 短事务：守卫行锁内配额核验 + BINDING 行落库提交
        PetAlbumAsset asset = transactionTemplate.execute(status -> {
            guardService.lockGuard(userId);
            Long count = albumMapper.selectCount(new LambdaQueryWrapper<PetAlbumAsset>()
                    .eq(PetAlbumAsset::getUserId, userId));
            if (count != null && count >= 100) {
                throw new BusinessException(PetErrorCodes.PET_QUOTA_EXHAUSTED, "相册已满（100 张）");
            }
            PetAlbumAsset row = new PetAlbumAsset();
            row.setUserId(userId);
            row.setPetId(petId);
            row.setDiaryEntryId(diaryEntryId);
            row.setFileId(String.valueOf(fileAssetId));
            row.setAuditStatus("PENDING");
            row.setBindStatus("BINDING");
            row.setBindAttempts(1);
            row.setNextBindRetryAt(properties.getFeatureSwitches().isAlbumBinding()
                    ? java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(BIND_RETRY_BACKOFF_SECONDS)
                    : null);
            // 默认私有（OWNER_ONLY）：PUBLIC 需审核通过后由 PATCH 显式开启
            row.setVisibility("OWNER_ONLY");
            row.setCaption(normalizedCaption);
            try {
                albumMapper.insert(row);
            } catch (DuplicateKeyException e) {
                // §7.2 +请求键：同文件重复上传（响应丢失重试）幂等返回既有引用，不报错
                PetAlbumAsset existing = albumMapper.selectOne(new LambdaQueryWrapper<PetAlbumAsset>()
                        .eq(PetAlbumAsset::getUserId, userId)
                        .eq(PetAlbumAsset::getFileId, String.valueOf(fileAssetId))
                        .last("LIMIT 1"));
                if (existing != null) {
                    log.info("相册资源重复引用（幂等返回既有行）: userId={}, fileId={}, assetId={}",
                            userId, fileAssetId, existing.getId());
                    return existing;
                }
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "该资源已存在");
            }
            return row;
        });
        if (asset == null) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "相册资源落库失败");
        }
        // 幂等重入（响应丢失重试撞 uk 返回既有行）：行已 BOUND 则直接返回，不再触发远端
        if (!"BINDING".equals(asset.getBindStatus())) {
            return asset;
        }
        if (properties.getFeatureSwitches().isAlbumBinding()) {
            confirmAlbumBinding(asset, fileAssetId, userId);
        }
        return albumMapper.selectById(asset.getId());
    }

    /** 绑定自动重试退避基数（秒）：60s × 2^attempts，封顶 1 小时 */
    private static final long BIND_RETRY_BACKOFF_SECONDS = 60;
    /** 绑定自动重试上限：超过后转 FAILED 终态，留用户 retry-binding 人工处置 */
    private static final int BIND_MAX_ATTEMPTS = 5;

    /** PET-13：绑定失败 → 递增尝试、指数退避排期、留错误原因（_confirm 短事务内调用） */
    private void markBindFailed(PetAlbumAsset asset, Exception cause) {
        int attempts = (asset.getBindAttempts() != null ? asset.getBindAttempts() : 1) + 1;
        boolean exhausted = attempts > BIND_MAX_ATTEMPTS;
        String error = cause.getMessage() == null ? "unknown" : cause.getMessage();
        albumMapper.update(null, new LambdaUpdateWrapper<PetAlbumAsset>()
                .set(PetAlbumAsset::getBindStatus, exhausted ? "FAILED" : "BINDING")
                .set(PetAlbumAsset::getBindAttempts, attempts)
                .set(PetAlbumAsset::getNextBindRetryAt, exhausted ? null
                        : java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                        .plusSeconds(Math.min(BIND_RETRY_BACKOFF_SECONDS * (1L << Math.min(attempts, 6)), 3600L)))
                .set(PetAlbumAsset::getLastBindError,
                        error.length() <= 255 ? error : error.substring(0, 255))
                .eq(PetAlbumAsset::getId, asset.getId()));
    }

    /** 远程绑定（幂等引用键 PET_ALBUM:{id}）：成功 BOUND；失败留 BINDING 并抛出明确错误 */
    /**
     * PET-13/T32：远端绑定（幂等引用键 PET_ALBUM:{id}）+ confirm 短事务推进 BOUND。
     * 必须在<strong>无事务</strong>上下文调用（远端 HTTP 不进数据库事务）；失败落
     * BINDING/FAILED + 退避排期（不向调用方抛异常——主动作已提交，绑定是可恢复的异步收尾）。
     */
    private void confirmAlbumBinding(PetAlbumAsset asset, Long fileAssetId, Long userId) {
        try {
            var response = fileFeignClient.bindReference(fileAssetId,
                    new com.cloudmart.pet.feign.FileFeignClient.BindReferenceRequest(
                            ALBUM_BIZ_TYPE, String.valueOf(asset.getId()), userId,
                            ALBUM_ALLOWED_MIMES, ALBUM_MAX_SIZE_BYTES, "PRIVATE"));
            if (response.data() == null) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "文件绑定未受理");
            }
            // confirm 短事务：BINDING→BOUND（条件更新防并发恢复任务与用户重试双写）
            int updated = albumMapper.update(null, new LambdaUpdateWrapper<PetAlbumAsset>()
                    .set(PetAlbumAsset::getBindStatus, "BOUND")
                    .set(PetAlbumAsset::getNextBindRetryAt, null)
                    .set(PetAlbumAsset::getLastBindError, null)
                    .eq(PetAlbumAsset::getId, asset.getId())
                    .eq(PetAlbumAsset::getBindStatus, "BINDING"));
            asset.setBindStatus("BOUND");
            if (updated == 0) {
                log.info("相册绑定被并发收尾抢先（恢复任务/用户重试）, albumAssetId={}", asset.getId());
            }
        } catch (Exception e) {
            markBindFailed(asset, e);
            log.warn("相册文件远程绑定失败（行留 BINDING/FAILED 退避重试）, albumAssetId={}, fileId={}",
                    asset.getId(), fileAssetId, e);
        }
    }

    /**
     * PET-13/T32：BINDING 行自动恢复（调度器调用）——远端引用键幂等，重试安全；
     * 每轮小批（到期行 LIMIT 20），单条失败不阻断其余。
     *
     * @return 本轮成功推进 BOUND 的数量
     */
    public int recoverStaleBindingAssets() {
        if (!properties.getFeatureSwitches().isAlbumBinding()) {
            return 0;
        }
        java.util.List<PetAlbumAsset> due = albumMapper.selectList(new LambdaQueryWrapper<PetAlbumAsset>()
                .eq(PetAlbumAsset::getBindStatus, "BINDING")
                .isNotNull(PetAlbumAsset::getNextBindRetryAt)
                .le(PetAlbumAsset::getNextBindRetryAt, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
                .orderByAsc(PetAlbumAsset::getId)
                .last("LIMIT 20"));
        int bound = 0;
        for (PetAlbumAsset asset : due) {
            try {
                Long fileAssetId = parseFileAssetId(asset.getFileId());
                confirmAlbumBinding(asset, fileAssetId, asset.getUserId());
                if ("BOUND".equals(asset.getBindStatus())) {
                    bound++;
                }
            } catch (Exception e) {
                // confirmAlbumBinding 内部已落退避；此处兜底防单条异常阻断批次
                log.warn("相册绑定恢复单条失败: albumAssetId={}", asset.getId(), e);
            }
        }
        return bound;
    }

    /**
     * R04/PET-13：BINDING/FAILED 行补绑定（上传后响应丢失/远程失败/超限失败的恢复入口）；
     * 远端幂等键保证重试安全，确认走 confirm 短事务（远端调用不进事务）。
     */
    public PetAlbumAsset retryAlbumBinding(Long userId, Long assetId) {
        PetAlbumAsset asset = albumMapper.selectById(assetId);
        if (asset == null || !asset.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能管理自己宠物的相册");
        }
        if (!"BINDING".equals(asset.getBindStatus()) && !"FAILED".equals(asset.getBindStatus())) {
            return asset;
        }
        Long fileAssetId = parseFileAssetId(asset.getFileId());
        // 用户显式重试立即给机会：清退避排期
        albumMapper.update(null, new LambdaUpdateWrapper<PetAlbumAsset>()
                .set(PetAlbumAsset::getNextBindRetryAt, null)
                .eq(PetAlbumAsset::getId, assetId));
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
            // 访客：审核通过 + 绑定完成 + 主人显式公开（默认私有，§7.2 album visibility）
            wrapper.eq(PetAlbumAsset::getAuditStatus, "APPROVED")
                    .eq(PetAlbumAsset::getBindStatus, "BOUND")
                    .eq(PetAlbumAsset::getVisibility, "PUBLIC");
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
                asset.getCreatedAt() == null ? null : asset.getCreatedAt().toString(),
                asset.getCaption(), asset.getVisibility(), asset.getVersion());
    }

    /** R04 相册条目 VO：fileId 为不透明 ID 字符串，预览地址为短期授权路径（禁止持久化）；
     *  caption/visibility/version 供三端按"先 GET 再 PATCH（expectedVersion CAS）"编辑 */
    public record AlbumAssetVO(String assetId, String petId, String diaryEntryId, String fileId,
                               String auditStatus, String bindStatus, String reviewReason,
                               String previewUrl, String createdAt,
                               String caption, String visibility, Integer version) {
    }

    /**
     * §7.2 PATCH /pets/{petId}/album/{assetId}：修改说明/可见性（expectedVersion CAS）。
     * 审核通过前 PUBLIC 被拒绝（上传默认私有待审）；字段缺省 = 不修改（部分更新语义）。
     */
    @Transactional
    public PetAlbumAsset updateAlbumAsset(Long userId, Long petId, Long assetId, String caption,
                                          String visibility, Integer expectedVersion) {
        PetAlbumAsset asset = requireOwnedAlbumAsset(userId, assetId);
        if (!petId.equals(asset.getPetId())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "相册资源不存在");
        }
        if (expectedVersion == null || expectedVersion < 1) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "expectedVersion 必填（先 GET 再编辑）");
        }
        String targetVisibility = null;
        if (visibility != null) {
            targetVisibility = switch (visibility) {
                case "PUBLIC" -> "PUBLIC";
                case "OWNER_ONLY", "PRIVATE" -> "OWNER_ONLY";
                default -> throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                        "visibility 仅支持 PUBLIC/OWNER_ONLY");
            };
            if ("PUBLIC".equals(targetVisibility) && !"APPROVED".equals(asset.getAuditStatus())) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "审核通过后才能设为公开");
            }
        }
        LambdaUpdateWrapper<PetAlbumAsset> wrapper = new LambdaUpdateWrapper<PetAlbumAsset>()
                .setSql("version = version + 1")
                .eq(PetAlbumAsset::getId, assetId)
                .eq(PetAlbumAsset::getVersion, expectedVersion);
        if (targetVisibility != null) {
            wrapper.set(PetAlbumAsset::getVisibility, targetVisibility);
        }
        if (caption != null) {
            wrapper.set(PetAlbumAsset::getCaption, normalizeCaption(caption));
        }
        int updated = albumMapper.update(null, wrapper);
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "相册已被其他端修改，请刷新后重试");
        }
        return albumMapper.selectById(assetId);
    }

    /** 说明规范化：空白视为清除（NULL），上限 200 字符 */
    private String normalizeCaption(String caption) {
        if (caption == null) {
            return null;
        }
        String normalized = caption.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 200) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "照片说明不超过 200 字");
        }
        return normalized;
    }

    private PetAlbumAsset requireOwnedAlbumAsset(Long userId, Long assetId) {
        PetAlbumAsset asset = albumMapper.selectById(assetId);
        if (asset == null || !asset.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "相册资源不存在或无权操作");
        }
        return asset;
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
        // §7.2：version 供三端"先 GET 再 PUT（expectedVersion）"，防多端互相覆盖
        return Map.of(
                "extract", Boolean.TRUE.equals(pet.getMemoryExtractEnabled()),
                "use", Boolean.TRUE.equals(pet.getMemoryUseEnabled()),
                "version", pet.getVersion() == null ? 0 : pet.getVersion());
    }

    /**
     * 记忆开关（提取/使用独立）；返回持久化后的值与最新 version（§7.2 PUT 返回持久化结果）。
     * 新版 PUT 带 expectedVersion → @Version 乐观锁 CAS，冲突显式 409；
     * 旧请求无版本按兼容窗口直接生效并记录使用量（为收窄兼容窗口提供观测）。
     */
    @Transactional
    public Map<String, Object> toggleMemory(Long userId, Long petId, boolean extract, boolean use,
                                            Integer expectedVersion) {
        requireOwner(userId, petId);
        Pet pet = petMapper.selectById(petId);
        pet.setMemoryExtractEnabled(extract);
        pet.setMemoryUseEnabled(use);
        if (expectedVersion != null) {
            pet.setVersion(expectedVersion);
            if (petMapper.updateById(pet) == 0) {
                throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                        "记忆设置已被其他端修改，请刷新后重试");
            }
        } else {
            log.info("[COMPAT] 记忆设置旧版无版本写入: userId={}, petId={}", userId, petId);
            petMapper.updateById(pet);
        }
        Pet refreshed = petMapper.selectById(petId);
        return Map.of(
                "extract", Boolean.TRUE.equals(refreshed.getMemoryExtractEnabled()),
                "use", Boolean.TRUE.equals(refreshed.getMemoryUseEnabled()),
                "version", refreshed.getVersion() == null ? 0 : refreshed.getVersion());
    }

    /**
     * R03 日记条目 VO（三端契约字段：id/petId/type/content/visibility/assetIds/createdAt/occurredAt）。
     * content 从事件载荷 JSON 渲染（未知事件类型返回空串安全文案，不吐原始 snapshot）；
     * OWNER_ONLY 对外统一映射 PRIVATE。
     */
    public record DiaryEntryVO(Long id, Long petId, String type, String content, String visibility,
                               java.util.List<Long> assetIds, java.time.LocalDateTime createdAt,
                               java.time.LocalDateTime occurredAt, Integer version) {

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
                    visibility, assetIdList, entry.getCreatedAt(), entry.getOccurredAt(), entry.getVersion());
        }
    }

    private void requireOwner(Long userId, Long petId) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "无权访问该宠物的记忆");
        }
    }
}
