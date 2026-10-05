package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetCooperation;
import com.cloudmart.pet.entity.PetCooperationContribution;
import com.cloudmart.pet.entity.PetCollectionEntry;
import com.cloudmart.pet.entity.PetCollectionRecord;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetRewardClaim;
import com.cloudmart.pet.entity.PetWallMessage;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.repository.PetCooperationMapper;
import com.cloudmart.pet.repository.PetCooperationContributionMapper;
import com.cloudmart.pet.repository.PetCollectionEntryMapper;
import com.cloudmart.pet.repository.PetCollectionRecordMapper;
import com.cloudmart.pet.repository.PetFriendMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.repository.PetRewardClaimMapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.service.PetUserBlockService;
import com.cloudmart.pet.service.PetUserGuardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 好友合作周任务 + 收藏图鉴应用服务（R27 从 PetPlayFeatureService 拆分）。
 * 合作状态机/贡献事实/个人领奖与图鉴解锁投影，逻辑与拆分前完全一致。
 */
@Service
@Slf4j
public class PetCooperationService {

    private static final String COOP_DECOR_CODE = "cooperation_badge";
    private static final int COOP_ALT_STARLIGHT = 20;

    private final PetMapper petMapper;
    private final PetClock petClock;
    private final PetCooperationMapper cooperationMapper;
    private final PetCooperationContributionMapper contributionMapper;
    /** R35：周成员名额表（UNIQUE user+week，接受时原子写入双方） */
    private final com.cloudmart.pet.repository.PetCooperationMemberMapper memberMapper;
    private final PetCollectionEntryMapper collectionEntryMapper;
    private final PetCollectionRecordMapper collectionRecordMapper;
    private final com.cloudmart.pet.config.PetProperties properties;
    private final com.cloudmart.pet.repository.PetInventoryMapper inventoryMapper;
    private final PetEconomyService economyService;
    private final com.cloudmart.pet.service.PetUserGuardService guardService;
    private final com.cloudmart.pet.service.PetUserBlockService userBlockService;
    private final com.cloudmart.pet.repository.PetFriendMapper friendMapper;
    private final PetRewardClaimMapper rewardClaimMapper;
    /** R05：社交写入门控（合作发起） */
    private final PetAccessPolicy accessPolicy;

    public PetCooperationService(PetMapper petMapper, PetClock petClock,
                                 PetCooperationMapper cooperationMapper,
                                 PetCooperationContributionMapper contributionMapper,
                                 com.cloudmart.pet.repository.PetCooperationMemberMapper memberMapper,
                                 PetCollectionEntryMapper collectionEntryMapper,
                                 PetCollectionRecordMapper collectionRecordMapper,
                                 com.cloudmart.pet.config.PetProperties properties,
                                 com.cloudmart.pet.repository.PetInventoryMapper inventoryMapper,
                                 PetEconomyService economyService,
                                 com.cloudmart.pet.service.PetUserGuardService guardService,
                                 com.cloudmart.pet.service.PetUserBlockService userBlockService,
                                 com.cloudmart.pet.repository.PetFriendMapper friendMapper,
                                 PetRewardClaimMapper rewardClaimMapper,
                                 PetAccessPolicy accessPolicy) {
        this.petMapper = petMapper;
        this.petClock = petClock;
        this.cooperationMapper = cooperationMapper;
        this.contributionMapper = contributionMapper;
        this.memberMapper = memberMapper;
        this.collectionEntryMapper = collectionEntryMapper;
        this.collectionRecordMapper = collectionRecordMapper;
        this.properties = properties;
        this.inventoryMapper = inventoryMapper;
        this.economyService = economyService;
        this.guardService = guardService;
        this.userBlockService = userBlockService;
        this.friendMapper = friendMapper;
        this.rewardClaimMapper = rewardClaimMapper;
        this.accessPolicy = accessPolicy;
    }

    // ---------------- N06 好友合作周任务 ----------------

    /**
     * 创建邀请（§7.6）：明确指定受邀好友——非本人、有效好友、双方未拉黑；
     * 本周剩余业务日 ≥3；周名额由 uk_cooperation_inviter 兜底。
     */
    @Transactional
    public Map<String, Object> createCooperation(Long userId, Long inviteeUserId) {
        if (!properties.getFeatureSwitches().isCooperation()) {
            throw new BusinessException(PetErrorCodes.PET_FEATURE_DISABLED, "该功能暂未开放");
        }
        // R05：SOCIAL_MUTE 处罚生效时禁止发起新合作
        if (accessPolicy.isSociallyMuted(userId)) {
            throw new BusinessException(PetErrorCodes.PET_BLOCKED,
                    "你的账号因违反社区规范被限制合作，如有疑问请联系客服申诉");
        }
        Pet pet = requireActivePet(userId);
        if (inviteeUserId == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "请选择要邀请的好友");
        }
        if (inviteeUserId.equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "不能邀请自己");
        }
        if (userBlockService.isBlockedEitherWay(userId, inviteeUserId)) {
            throw new BusinessException(PetErrorCodes.PET_BLOCKED, "无法邀请该用户");
        }
        Long friendRows = friendMapper.selectCount(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetFriend>()
                .and(w -> w.eq(com.cloudmart.pet.entity.PetFriend::getUserId, userId)
                        .eq(com.cloudmart.pet.entity.PetFriend::getFriendUserId, inviteeUserId)
                        .or()
                        .eq(com.cloudmart.pet.entity.PetFriend::getUserId, inviteeUserId)
                        .eq(com.cloudmart.pet.entity.PetFriend::getFriendUserId, userId))
                .eq(com.cloudmart.pet.entity.PetFriend::getStatus, "ACTIVE"));
        if (friendRows == null || friendRows == 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "只能邀请好友");
        }
        LocalDate today = petClock.businessDate();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        // R35：周末口径统一——周五（剩余 3 个业务日：五/六/日）可组队，周六起新队禁止
        //（原实现创建分支先算 plusDays(2) 又内嵌 >=FRIDAY，放过周六/日）
        if (today.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue()) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT,
                    "本周剩余时间不足，下周一再来组队吧");
        }
        // R35：成员表周名额预检——本周已正式参与（无论 inviter/invitee 角色）不得再发起；
        // uk_cooperation_inviter 只覆盖发起角色，挡不住"当周既已受邀入队又另起一队"
        requireCooperationWeekSlotFree(userId, weekStart);
        PetCooperation cooperation = new PetCooperation();
        cooperation.setWeekStart(weekStart);
        cooperation.setInviterUserId(userId);
        cooperation.setInviterPetId(pet.getId());
        // R35：明确保存目标受邀人——好友 C 不能接受发给 B 的邀请（T63）
        cooperation.setInviteeUserId(inviteeUserId);
        cooperation.setStatus("INVITED");
        cooperation.setInviteExpiresAt(petClock.nowUtc().plusHours(24));
        try {
            cooperationMapper.insert(cooperation);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "本周已经参加过合作任务啦");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("cooperationId", cooperation.getId());
        result.put("inviteExpiresAt", cooperation.getInviteExpiresAt());
        return result;
    }

    /**
     * 接受邀请（B02/BE-02）：双守卫行锁（userId 升序防死锁）+ 完整资格校验 + CAS 状态迁移。
     * 校验：邀请存在/待接受/未过期、非本人邀请、双方未拉黑、有效好友、本周剩余 >=3 业务日；
     * 并发：CAS（status=INVITED）保证同一邀请只被接受一次（后来者明确冲突，不覆盖先接受者）；
     * 受邀人周名额由 uk_cooperation_invitee(invitee_user_id, week_start) 兜底。
     */
    @Transactional
    public Map<String, Object> acceptCooperation(Long userId, Long cooperationId) {
        PetCooperation cooperation = cooperationMapper.selectById(cooperationId);
        if (cooperation == null || !"INVITED".equals(cooperation.getStatus())
                || cooperation.getInviteExpiresAt().isBefore(petClock.nowUtc())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "邀请不存在或已过期");
        }
        if (userId.equals(cooperation.getInviterUserId())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "不能接受自己发起的邀请");
        }
        // R35：仅目标受邀人可接受（原实现任何好友都能接受别人的邀请）
        if (cooperation.getInviteeUserId() == null || !userId.equals(cooperation.getInviteeUserId())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "这份邀请不是发给你的");
        }
        if (userBlockService.isBlockedEitherWay(userId, cooperation.getInviterUserId())) {
            throw new BusinessException(PetErrorCodes.PET_BLOCKED, "无法与该用户组队");
        }
        Long friendRows = friendMapper.selectCount(
                new LambdaQueryWrapper<com.cloudmart.pet.entity.PetFriend>()
                        .and(w -> w.eq(com.cloudmart.pet.entity.PetFriend::getUserId, userId)
                                .eq(com.cloudmart.pet.entity.PetFriend::getFriendUserId, cooperation.getInviterUserId())
                                .or()
                                .eq(com.cloudmart.pet.entity.PetFriend::getUserId, cooperation.getInviterUserId())
                                .eq(com.cloudmart.pet.entity.PetFriend::getFriendUserId, userId))
                        .eq(com.cloudmart.pet.entity.PetFriend::getStatus, "ACTIVE"));
        if (friendRows == null || friendRows == 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "只能和好友组队");
        }
        LocalDate today = petClock.businessDate();
        // R35：周末口径与创建一致（周六起不能新组队；跨周邀请已过期）
        if (!today.with(java.time.DayOfWeek.MONDAY).equals(cooperation.getWeekStart())
                || today.getDayOfWeek().getValue() >= java.time.DayOfWeek.SATURDAY.getValue()) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "邀请已过期，下周一再来组队吧");
        }
        guardService.lockGuard(userId);
        guardService.lockGuard(cooperation.getInviterUserId());
        Pet pet = requireActivePet(userId);
        int updated = cooperationMapper.update(null, new LambdaUpdateWrapper<PetCooperation>()
                .set(PetCooperation::getInviteeUserId, userId)
                .set(PetCooperation::getInviteePetId, pet.getId())
                .set(PetCooperation::getStatus, "ACTIVE")
                .set(PetCooperation::getAcceptedAt, petClock.nowUtc())
                .eq(PetCooperation::getId, cooperationId)
                .eq(PetCooperation::getStatus, "INVITED"));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "该邀请已被接受");
        }
        // R35：周名额成员表——接受时原子写入双方（UNIQUE user+week 数据库权威）；
        // 任一方本周已正式参与则整体回滚（含上方 CAS 状态迁移），不产生半队
        insertCooperationMember(cooperation.getInviterUserId(), cooperation.getWeekStart(),
                cooperation.getId(), "INVITER", cooperation.getInviterPetId());
        insertCooperationMember(userId, cooperation.getWeekStart(),
                cooperation.getId(), "INVITEE", pet.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ACTIVE");
        return result;
    }

    private void requireCooperationWeekSlotFree(Long userId, LocalDate weekStart) {
        Long memberRows = memberMapper.selectCount(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetCooperationMember>()
                .eq(com.cloudmart.pet.entity.PetCooperationMember::getUserId, userId)
                .eq(com.cloudmart.pet.entity.PetCooperationMember::getWeekStart, weekStart));
        if (memberRows != null && memberRows > 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "本周已经参加过合作任务啦");
        }
    }

    /** 邀请未接受不写成员表（不消耗周名额）；已组队退出也不删行（名额不恢复） */
    private void insertCooperationMember(Long memberUserId, LocalDate weekStart,
                                         Long cooperationId, String role, Long petId) {
        com.cloudmart.pet.entity.PetCooperationMember member = new com.cloudmart.pet.entity.PetCooperationMember();
        member.setUserId(memberUserId);
        member.setWeekStart(weekStart);
        member.setCooperationId(cooperationId);
        member.setRole(role);
        member.setPetId(petId);
        try {
            memberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "本周已经参加过合作任务啦");
        }
    }

    /**
     * 贡献一次有效照顾（N06/B02-BE-02）：唯一事件去重 + 每人每天 1 次。
     * 计数来源改为贡献事实表 COUNT（并发安全），替代原 contributions JSON 无锁读改写（丢计数）；
     * 用户守卫行锁串行化同用户并发贡献；双方各 3 个不同业务日达成 -> CAS 置 COMPLETED。
     */
    @Transactional
    public void recordContribution(Long userId, String eventId) {
        expireStaleCooperations();
        LocalDate currentWeekStart = petClock.businessDate().with(DayOfWeek.MONDAY);
        // R35：仅本周 ACTIVE 队吸收贡献（原实现无周过滤，旧队跨周继续计数）
        PetCooperation cooperation = cooperationMapper.selectOne(new LambdaQueryWrapper<PetCooperation>()
                .eq(PetCooperation::getStatus, "ACTIVE")
                .eq(PetCooperation::getWeekStart, currentWeekStart)
                .and(w -> w.eq(PetCooperation::getInviterUserId, userId)
                        .or().eq(PetCooperation::getInviteeUserId, userId))
                .last("LIMIT 1"));
        if (cooperation == null) {
            return;
        }
        // R35：双方守卫按 userId 数值升序加锁——最后一份并发贡献串行提交，
        // 后提交者的 COUNT 可见先提交者的记录，队伍必达 COMPLETED（T65）
        Long inviterId = cooperation.getInviterUserId();
        Long inviteeId = cooperation.getInviteeUserId();
        lockGuardsAscending(userId, inviterId, inviteeId);
        // PET-16：锁内重读队伍——占用守卫锁前读到的快照可能已被并发接受/退出/周流转改变，
        // 重新校验"本周 ACTIVE 且本人在队"再计贡献
        cooperation = cooperationMapper.selectOne(new LambdaQueryWrapper<PetCooperation>()
                .eq(PetCooperation::getStatus, "ACTIVE")
                .eq(PetCooperation::getWeekStart, currentWeekStart)
                .and(w -> w.eq(PetCooperation::getInviterUserId, userId)
                        .or().eq(PetCooperation::getInviteeUserId, userId))
                .last("LIMIT 1"));
        if (cooperation == null) {
            return;
        }
        LocalDate today = petClock.businessDate();
        PetCooperationContribution contribution = new PetCooperationContribution();
        contribution.setCooperationId(cooperation.getId());
        contribution.setUserId(userId);
        contribution.setEventId(eventId);
        contribution.setBusinessDate(today);
        try {
            contributionMapper.insert(contribution);
        } catch (DuplicateKeyException e) {
            return; // 重复事件/当日已贡献：不增贡献
        }
        Long inviterCount = contributionMapper.selectCount(new LambdaQueryWrapper<PetCooperationContribution>()
                .eq(PetCooperationContribution::getCooperationId, cooperation.getId())
                .eq(PetCooperationContribution::getUserId, cooperation.getInviterUserId()));
        Long inviteeCount = contributionMapper.selectCount(new LambdaQueryWrapper<PetCooperationContribution>()
                .eq(PetCooperationContribution::getCooperationId, cooperation.getId())
                .eq(PetCooperationContribution::getUserId, cooperation.getInviteeUserId()));
        if (inviterCount != null && inviteeCount != null
                && inviterCount >= 3 && inviteeCount >= 3
                && "ACTIVE".equals(cooperation.getStatus())) {
            cooperationMapper.update(null, new LambdaUpdateWrapper<PetCooperation>()
                    .set(PetCooperation::getStatus, "COMPLETED")
                    .eq(PetCooperation::getId, cooperation.getId())
                    .eq(PetCooperation::getStatus, "ACTIVE"));
        }
    }

    /**
     * PET-16/T39：全体相关用户守卫按 userId 数值升序加锁（去重）——原实现邀请者 A→B、
     * 被邀请者 B→A，双方同时贡献形成经典死锁条件；全局升序后任意调用方向一致。
     */
    private void lockGuardsAscending(Long... userIds) {
        java.util.TreeSet<Long> lockOrder = new java.util.TreeSet<>();
        for (Long id : userIds) {
            if (id != null) {
                lockOrder.add(id);
            }
        }
        for (Long id : lockOrder) {
            guardService.lockGuard(id);
        }
    }

    /**
     * R35 周截止惰性流转：上周仍未完成的 ACTIVE 队转 EXPIRED（不吸收新周贡献）；
     * 已完成的保留个人领奖（§7.6 状态机 INVITED/ACTIVE/COMPLETED/EXPIRED/ENDED）。
     */
    private void expireStaleCooperations() {
        LocalDate currentWeekStart = petClock.businessDate().with(DayOfWeek.MONDAY);
        cooperationMapper.update(null, new LambdaUpdateWrapper<PetCooperation>()
                .set(PetCooperation::getStatus, "EXPIRED")
                .set(PetCooperation::getEndedAt, petClock.nowUtc())
                .eq(PetCooperation::getStatus, "ACTIVE")
                .lt(PetCooperation::getWeekStart, currentWeekStart));
        // 过期未接受邀请同样流转（名额不占、可重新邀请）
        cooperationMapper.update(null, new LambdaUpdateWrapper<PetCooperation>()
                .set(PetCooperation::getStatus, "EXPIRED")
                .set(PetCooperation::getEndedAt, petClock.nowUtc())
                .eq(PetCooperation::getStatus, "INVITED")
                .lt(PetCooperation::getWeekStart, currentWeekStart));
    }

    /** 查询当前/历史合作任务（对方隐私最小化：只返回贡献次数与宠物摘要） */
    public List<PetCooperation> cooperations(Long userId) {
        expireStaleCooperations();
        return cooperationMapper.selectList(new LambdaQueryWrapper<PetCooperation>()
                .and(w -> w.eq(PetCooperation::getInviterUserId, userId)
                        .or().eq(PetCooperation::getInviteeUserId, userId))
                .orderByDesc(PetCooperation::getId)
                .last("LIMIT 20"));
    }

    /** 退出（停止新增贡献、保留历史；名额不恢复） */
    @Transactional
    public void leaveCooperation(Long userId, Long cooperationId) {
        cooperationMapper.update(null, new LambdaUpdateWrapper<PetCooperation>()
                .set(PetCooperation::getStatus, "ENDED")
                .set(PetCooperation::getEndedAt, petClock.nowUtc())
                .eq(PetCooperation::getId, cooperationId)
                .and(w -> w.eq(PetCooperation::getInviterUserId, userId)
                        .or().eq(PetCooperation::getInviteeUserId, userId))
                .in(PetCooperation::getStatus, "INVITED", "ACTIVE"));
    }

    // ---------------- N07 收藏图鉴 ----------------

    /** 解锁（多宠重复获得仅一次；uk 幂等）——由商城购买/进化/捞瓶 CAUGHT 等事实路径调用 */
    @Transactional
    public void unlockCollection(Long userId, Long petId, String category, String itemCode, String eventId) {
        String entryCode = category + ":" + itemCode;
        PetCollectionRecord record = new PetCollectionRecord();
        record.setUserId(userId);
        record.setEntryCode(entryCode);
        record.setFirstPetId(petId);
        record.setFirstEventId(eventId);
        record.setAcquiredAt(petClock.nowUtc());
        try {
            collectionRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.debug("图鉴已解锁（幂等跳过）: userId={}, entry={}", userId, entryCode);
        }
    }

    /** 图鉴列表（未解锁返回线索；隐藏条目不泄漏完整正文） */
    public List<Map<String, Object>> collection(Long userId, String category, int page, int size) {
        if (!properties.getFeatureSwitches().isCollection()) {
            throw new BusinessException(PetErrorCodes.PET_FEATURE_DISABLED, "该功能暂未开放");
        }
        LambdaQueryWrapper<PetCollectionEntry> wrapper = new LambdaQueryWrapper<PetCollectionEntry>()
                .orderByAsc(PetCollectionEntry::getCategory)
                .last("LIMIT " + Math.min(size, 50) + " OFFSET " + Math.max(page - 1, 0) * Math.min(size, 50));
        if (category != null && !category.isBlank()) {
            wrapper.eq(PetCollectionEntry::getCategory, category.toUpperCase());
        }
        List<PetCollectionEntry> entries = collectionEntryMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PetCollectionEntry entry : entries) {
            Map<String, Object> item = new HashMap<>();
            item.put("category", entry.getCategory());
            item.put("itemCode", entry.getItemCode());
            item.put("rarity", entry.getRarity());
            boolean unlocked = collectionRecordMapper.selectCount(new LambdaQueryWrapper<PetCollectionRecord>()
                    .eq(PetCollectionRecord::getUserId, userId)
                    .eq(PetCollectionRecord::getEntryCode, entry.getCategory() + ":" + entry.getItemCode())) > 0;
            item.put("unlocked", unlocked);
            if (unlocked) {
                item.put("resourceKey", entry.getResourceKey());
                item.put("unlockCondition", entry.getUnlockCondition());
            } else {
                item.put("hint", Boolean.TRUE.equals(entry.getHidden())
                        ? "隐藏条目" : entry.getUnlockCondition());
            }
            result.add(item);
        }
        return result;
    }

    /** 收藏统计与进度 */
    public Map<String, Object> collectionStats(Long userId) {
        long total = collectionEntryMapper.selectCount(new LambdaQueryWrapper<>());
        long unlocked = collectionRecordMapper.selectCount(new LambdaQueryWrapper<PetCollectionRecord>()
                .eq(PetCollectionRecord::getUserId, userId));
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("unlocked", unlocked);
        return result;
    }

    private Pet requireActivePet(Long userId) {
        Pet pet = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "你还没有宠物");
        }
        return pet;
    }

    /**
     * N06 个人领取（B02/BE-01）：pet_reward_claim 唯一事实裁决——同一合作每人至多领取一次，
     * 奖励类型在首次领取时冻结（无装饰则发物品；已拥有则固定替代币），只能其一；
     * 重放（换 Idempotency-Key 再领）返回冻结的原结果，杜绝"先领物再领币"双领；
     * 奖励归属合作参与时绑定的宠物（inviter/invitee petId），切换主宠不改变。
     */
    @Transactional
    public Map<String, Object> claimCooperationReward(Long userId, Long cooperationId) {
        PetCooperation coop = cooperationMapper.selectById(cooperationId);
        if (coop == null || (!userId.equals(coop.getInviterUserId()) && !userId.equals(coop.getInviteeUserId()))) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "合作任务不存在");
        }
        if (!"COMPLETED".equals(coop.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_NOT_FINISHED, "合作任务尚未达成");
        }
        guardService.lockGuard(userId);
        Long boundPetId = userId.equals(coop.getInviterUserId())
                ? coop.getInviterPetId() : coop.getInviteePetId();
        com.cloudmart.pet.entity.PetRewardClaim claim = new com.cloudmart.pet.entity.PetRewardClaim();
        claim.setUserId(userId);
        claim.setPetId(boundPetId);
        claim.setBizType("COOP_REWARD");
        claim.setBizId(String.valueOf(cooperationId));
        claim.setRewardSlot("MAIN");
        claim.setWalletDomain("PET");
        claim.setStatus("PROCESSING");
        boolean first;
        try {
            rewardClaimMapper.insert(claim);
            first = true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            first = false;
            claim = rewardClaimMapper.selectOne(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetRewardClaim>()
                    .eq(com.cloudmart.pet.entity.PetRewardClaim::getUserId, userId)
                    .eq(com.cloudmart.pet.entity.PetRewardClaim::getBizType, "COOP_REWARD")
                    .eq(com.cloudmart.pet.entity.PetRewardClaim::getBizId, String.valueOf(cooperationId))
                    .eq(com.cloudmart.pet.entity.PetRewardClaim::getRewardSlot, "MAIN"));
            if (claim == null) {
                throw new BusinessException(PetErrorCodes.PET_REQUEST_IN_PROGRESS, "奖励领取处理中，请稍后查询");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("cooperationId", cooperationId);
        if (!first) {
            if (!"COMPLETED".equals(claim.getStatus())) {
                throw new BusinessException(PetErrorCodes.PET_REQUEST_IN_PROGRESS, "奖励领取处理中，请稍后查询");
            }
            result.put("reward", PetJsonUtils.parse(claim.getResultJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    }));
            result.put("duplicate", true);
            return result;
        }

        boolean owned = inventoryMapper.selectCount(
                new LambdaQueryWrapper<PetInventory>()
                        .eq(PetInventory::getUserId, userId)
                        .eq(PetInventory::getItemType, "FURNITURE")
                        .eq(PetInventory::getItemCode, COOP_DECOR_CODE)) > 0;
        Map<String, Object> reward;
        if (!owned) {
            com.cloudmart.pet.entity.PetInventory decor = new com.cloudmart.pet.entity.PetInventory();
            decor.setPetId(boundPetId);
            decor.setUserId(userId);
            decor.setItemType("FURNITURE");
            decor.setItemCode(COOP_DECOR_CODE);
            decor.setQuantity(1);
            decor.setEquipped(false);
            decor.setAcquiredAt(petClock.nowUtc());
            try {
                inventoryMapper.insert(decor);
                reward = Map.of("type", "ITEM", "itemCode", COOP_DECOR_CODE);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                reward = Map.of("type", "ITEM", "itemCode", COOP_DECOR_CODE, "duplicate", true);
            }
        } else {
            PetEconomyService.WalletSettlement settlement = economyService.earn(
                    userId, boundPetId, "COOP_REWARD_ALT", cooperationId, COOP_ALT_STARLIGHT, null,
                    cooperationId, userId);
            if (!settlement.isCompleted()) {
                throw new BusinessException(PetErrorCodes.PET_SETTLEMENT_PENDING, "替代星光结算中，稍后按原操作查询");
            }
            reward = Map.of("type", "STARLIGHT", "amount", settlement.credited());
        }
        claim.setStatus("COMPLETED");
        claim.setRewardSnapshot(PetJsonUtils.toJson(Map.of("policy", "ITEM_FIRST_ELSE_COIN")));
        claim.setResultJson(PetJsonUtils.toJson(reward));
        rewardClaimMapper.updateById(claim);
        result.put("reward", reward);
        return result;
    }

}
