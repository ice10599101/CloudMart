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

    public PetCompanionFeatureService(PetMapper petMapper,
                                      PetOnboardingProgressMapper onboardingMapper,
                                      PetDiaryEntryMapper diaryMapper,
                                      PetAlbumAssetMapper albumMapper,
                                      PetMemoryMapper memoryMapper,
                                      PetOperationService operationService,
                                      PetInventoryMapper inventoryMapper,
                                      com.cloudmart.pet.config.PetProperties properties,
                                      com.cloudmart.pet.repository.PetNotifyPrefMapper notifyPrefMapper) {
        this.petMapper = petMapper;
        this.onboardingMapper = onboardingMapper;
        this.diaryMapper = diaryMapper;
        this.albumMapper = albumMapper;
        this.memoryMapper = memoryMapper;
        this.operationService = operationService;
        this.inventoryMapper = inventoryMapper;
        this.properties = properties;
        this.notifyPrefMapper = notifyPrefMapper;
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

    /** 时间线（游标分页；他人仅可见 PUBLIC 条目） */
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
        result.put("entries", entries);
        result.put("nextCursor", nextCursor);
        result.put("hasMore", nextCursor != null);
        return result;
    }

    /** 上传相册资源（N02）：归属=本人宠物；JPEG/PNG/WebP、≤5MB 由文件服务校验后引用 */
    @Transactional
    public PetAlbumAsset uploadAlbumAsset(Long userId, Long petId, String fileId, Long diaryEntryId) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能管理自己宠物的相册");
        }
        Long count = albumMapper.selectCount(new LambdaQueryWrapper<PetAlbumAsset>()
                .eq(PetAlbumAsset::getUserId, userId));
        if (count != null && count >= 100) {
            throw new BusinessException(PetErrorCodes.PET_QUOTA_EXHAUSTED, "相册已满（100 张）");
        }
        PetAlbumAsset asset = new PetAlbumAsset();
        asset.setUserId(userId);
        asset.setPetId(petId);
        asset.setDiaryEntryId(diaryEntryId);
        asset.setFileId(fileId);
        asset.setAuditStatus("APPROVED");
        try {
            albumMapper.insert(asset);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "该资源已存在");
        }
        return asset;
    }

    /** 删除相册资源（归属校验） */
    @Transactional
    public void deleteAlbumAsset(Long userId, Long assetId) {
        PetAlbumAsset asset = albumMapper.selectById(assetId);
        if (asset == null || !asset.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能删除自己上传的资源");
        }
        albumMapper.deleteById(assetId);
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

    /** 记忆开关（提取/使用独立） */
    @Transactional
    public void toggleMemory(Long userId, Long petId, boolean extract, boolean use) {
        requireOwner(userId, petId);
        Pet pet = petMapper.selectById(petId);
        pet.setMemoryExtractEnabled(extract);
        pet.setMemoryUseEnabled(use);
        petMapper.updateById(pet);
    }

    private void requireOwner(Long userId, Long petId) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "无权访问该宠物的记忆");
        }
    }
}
