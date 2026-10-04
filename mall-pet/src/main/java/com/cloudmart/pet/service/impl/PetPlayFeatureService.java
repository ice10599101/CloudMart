package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetCollectionEntry;
import com.cloudmart.pet.entity.PetCollectionRecord;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetCooperation;
import com.cloudmart.pet.entity.PetCooperationContribution;
import com.cloudmart.pet.entity.PetCustodyRecord;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.repository.PetCollectionEntryMapper;
import com.cloudmart.pet.repository.PetCollectionRecordMapper;
import com.cloudmart.pet.repository.PetCooperationContributionMapper;
import com.cloudmart.pet.repository.PetCooperationMapper;
import com.cloudmart.pet.repository.PetCustodyRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.util.PetJsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 新增玩法后端（N04 接球小游戏 / N05 有限托管 / N06 好友合作周任务 / N07 收藏图鉴）。
 *
 * <p>N04：服务端生成规则快照与随机序列，客户端只提交操作（机会编号+目标+序号），
 * 服务端校验接收时间落窗口；不信客户端分数；每用户每日 5 局有收益且消耗玩耍额度；
 * 训练局 rewardEligible=false 不产生任何养成收益；结算 CAS 幂等。</p>
 *
 * <p>N05：每用户每自然周 1 次（uk）、最长 24h、期间禁止长期活动；照顾效果由
 * 状态读取惰性结算（饱食<30→50 最多 2 次、清洁<30→50 最多 1 次）；不产出任何养成收益。</p>
 *
 * <p>N06：每用户每自然周一支（双向 uk）；贡献按唯一事件去重、每人每天 1 次；
 * 接受时校验剩余业务日 ≥3；退组/解除好友停止累计，名额不恢复。</p>
 *
 * <p>N07：图鉴按用户累计、多宠共享；unlock 幂等（uk user+entry），重复获得仅解锁一次；
 * 未解锁返回线索，隐藏条目不泄漏完整正文。</p>
 */
@Service
@Slf4j
public class PetPlayFeatureService {

    private final PetMapper petMapper;
    private final PetClock petClock;
    /** R12：统一活动互斥（工作/读书/职业/捞瓶/休息/托管跨表排他） */
    private final PetActivityMutex activityMutex;
    /** R12：托管照顾公开事务应用服务 */
    private final PetCustodyCareService custodyCareService;
    /** R05：社交写入门控 */
    private final PetAccessPolicy accessPolicy;
    private final PetQuotaService quotaService;
    private final PetCustodyRecordMapper custodyMapper;
    private final PetCooperationMapper cooperationMapper;
    private final PetCooperationContributionMapper contributionMapper;
    private final PetCollectionEntryMapper collectionEntryMapper;
    private final PetCollectionRecordMapper collectionRecordMapper;
    private final com.cloudmart.pet.config.PetProperties properties;
    private final com.cloudmart.pet.repository.PetOfflineCursorMapper offlineCursorMapper;
    private final com.cloudmart.pet.repository.PetActivityMapper activityMapper;
    private final com.cloudmart.pet.repository.PetDiaryEntryMapper diaryEntryMapper;
    private final com.cloudmart.pet.repository.PetInventoryMapper inventoryMapper;
    private final PetEconomyService economyService;
    private final com.cloudmart.pet.service.PetUserGuardService guardService;
    private final com.cloudmart.pet.service.PetUserBlockService userBlockService;
    private final com.cloudmart.pet.repository.PetFriendMapper friendMapper;
    private final com.cloudmart.pet.repository.PetRewardClaimMapper rewardClaimMapper;
    private final com.cloudmart.pet.service.impl.PetStateService stateService;
    private final com.cloudmart.pet.service.PetIntimacyService intimacyService;

    public PetPlayFeatureService(PetMapper petMapper, PetClock petClock, PetQuotaService quotaService,
                                 PetActivityMutex activityMutex,
                                 PetCustodyCareService custodyCareService,
                                 PetCustodyRecordMapper custodyMapper,
                                 PetCooperationMapper cooperationMapper,
                                 PetCooperationContributionMapper contributionMapper,
                                 PetCollectionEntryMapper collectionEntryMapper,
                                 PetCollectionRecordMapper collectionRecordMapper,
                                 com.cloudmart.pet.config.PetProperties properties,
                                 com.cloudmart.pet.repository.PetOfflineCursorMapper offlineCursorMapper,
                                 com.cloudmart.pet.repository.PetActivityMapper activityMapper,
                                 com.cloudmart.pet.repository.PetDiaryEntryMapper diaryEntryMapper,
                                 com.cloudmart.pet.repository.PetInventoryMapper inventoryMapper,
                                 PetEconomyService economyService,
                                 com.cloudmart.pet.service.PetUserGuardService guardService,
                                 com.cloudmart.pet.service.PetUserBlockService userBlockService,
                                 com.cloudmart.pet.repository.PetFriendMapper friendMapper,
                                 com.cloudmart.pet.repository.PetRewardClaimMapper rewardClaimMapper,
                                 com.cloudmart.pet.service.impl.PetStateService stateService,
                                 com.cloudmart.pet.service.PetIntimacyService intimacyService,
                                 PetAccessPolicy accessPolicy) {
        this.petMapper = petMapper;
        this.petClock = petClock;
        this.activityMutex = activityMutex;
        this.custodyCareService = custodyCareService;
        this.quotaService = quotaService;
        this.custodyMapper = custodyMapper;
        this.cooperationMapper = cooperationMapper;
        this.contributionMapper = contributionMapper;
        this.collectionEntryMapper = collectionEntryMapper;
        this.collectionRecordMapper = collectionRecordMapper;
        this.properties = properties;
        this.offlineCursorMapper = offlineCursorMapper;
        this.activityMapper = activityMapper;
        this.diaryEntryMapper = diaryEntryMapper;
        this.inventoryMapper = inventoryMapper;
        this.economyService = economyService;
        this.guardService = guardService;
        this.userBlockService = userBlockService;
        this.friendMapper = friendMapper;
        this.rewardClaimMapper = rewardClaimMapper;
        this.stateService = stateService;
        this.intimacyService = intimacyService;
        this.accessPolicy = accessPolicy;
    }

    // ---------------- N05 离线摘要 ----------------

    /** 首次查询默认回溯窗口（小时）：无游标时按 maxIdleHours 口径回溯 */
    private static final long DIGEST_DEFAULT_LOOKBACK_HOURS = 48;

    /**
     * N05 离线摘要：按上次确认游标聚合离线期间的事实（完成任务/待领取/来访/里程碑），
     * 查询不重发奖励、不推进游标。
     */
    public Map<String, Object> offlineDigest(Long userId) {
        Pet pet = requireActivePet(userId);
        com.cloudmart.pet.entity.PetOfflineCursor cursor = offlineCursorMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetOfflineCursor>()
                        .eq(com.cloudmart.pet.entity.PetOfflineCursor::getUserId, userId)
                        .last("LIMIT 1"));
        java.time.LocalDateTime now = petClock.nowUtc();
        java.time.LocalDateTime from = cursor != null ? cursor.getLastConfirmedAt()
                : now.minusHours(DIGEST_DEFAULT_LOOKBACK_HOURS);
        // BE-10：固定摘要上界 throughAt——确认只推进到该点；阅读期间的新事件下轮可见
        java.time.LocalDateTime throughAt = now;

        // 完成=已领取（CLAIMED），待领=完成未领（COMPLETED）——不再共用同一条件（BE-10）
        Long finished = activityMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, "CLAIMED")
                .gt(PetActivity::getFinishedAt, from)
                .le(PetActivity::getFinishedAt, throughAt));
        Long claimable = activityMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, "COMPLETED")
                .gt(PetActivity::getFinishedAt, from)
                .le(PetActivity::getFinishedAt, throughAt));
        Long visits = activityMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, com.cloudmart.pet.enums.PetActivityType.VISIT.name())
                .gt(PetActivity::getFinishedAt, from)
                .le(PetActivity::getFinishedAt, throughAt));
        Long milestones = diaryEntryMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetDiaryEntry>()
                .eq(com.cloudmart.pet.entity.PetDiaryEntry::getUserId, userId)
                .gt(com.cloudmart.pet.entity.PetDiaryEntry::getOccurredAt, from)
                .le(com.cloudmart.pet.entity.PetDiaryEntry::getOccurredAt, throughAt));

        Map<String, Object> result = new HashMap<>();
        result.put("offlineHours", java.time.Duration.between(from, throughAt).toHours());
        result.put("from", from);
        result.put("throughAt", throughAt);
        result.put("petState", Map.of("level", pet.getLevel(), "hunger", pet.getHunger(),
                "happiness", pet.getHappiness(), "energy", pet.getEnergy(), "cleanliness", pet.getCleanliness()));
        // §3.5：数量为 JSON number——计数显式转 int，避免 Long 被 ID 定制器连带字符串化
        result.put("finishedTasks", finished == null ? 0 : finished.intValue());
        result.put("claimableTasks", claimable == null ? 0 : claimable.intValue());
        result.put("visits", visits == null ? 0 : visits.intValue());
        result.put("milestones", milestones == null ? 0 : milestones.intValue());
        result.put("hasCursor", cursor != null);
        return result;
    }

    /**
     * N05 确认（BE-10）：只推进到摘要展示时的固定上界 throughAt（不取确认时刻的 now），
     * 且游标不倒退——阅读确认期间新发生的事件不会被越过，下次摘要仍可见。
     */
    @Transactional
    public Map<String, Object> confirmOfflineDigest(Long userId, java.time.LocalDateTime throughAt) {
        requireActivePet(userId);
        java.time.LocalDateTime now = petClock.nowUtc();
        // R40：上界合法性——未来值拒绝（不伪装成 now）；未提供时游标不动
        //（原实现把缺失/未来值改为 now，会跳过尚未展示的事件，T76）
        if (throughAt != null && throughAt.isAfter(now)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "确认上界不能晚于当前时刻");
        }
        java.time.LocalDateTime target = throughAt;
        com.cloudmart.pet.entity.PetOfflineCursor cursor = offlineCursorMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetOfflineCursor>()
                        .eq(com.cloudmart.pet.entity.PetOfflineCursor::getUserId, userId)
                        .last("LIMIT 1"));
        if (cursor == null) {
            cursor = new com.cloudmart.pet.entity.PetOfflineCursor();
            cursor.setUserId(userId);
            cursor.setLastConfirmedAt(target);
            try {
                offlineCursorMapper.insert(cursor);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                cursor = offlineCursorMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetOfflineCursor>()
                                .eq(com.cloudmart.pet.entity.PetOfflineCursor::getUserId, userId));
            }
        }
        // R40：游标原子推进——SQL 条件 MAX（并发确认不倒退，替代读-比-写竞态）
        if (target != null) {
            int advanced = offlineCursorMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.cloudmart.pet.entity.PetOfflineCursor>()
                            .set(com.cloudmart.pet.entity.PetOfflineCursor::getLastConfirmedAt, target)
                            .eq(com.cloudmart.pet.entity.PetOfflineCursor::getId, cursor.getId())
                            .and(w -> w.isNull(com.cloudmart.pet.entity.PetOfflineCursor::getLastConfirmedAt)
                                    .or().lt(com.cloudmart.pet.entity.PetOfflineCursor::getLastConfirmedAt, target)));
            cursor.setLastConfirmedAt(advanced > 0 || cursor.getLastConfirmedAt() == null
                    ? target : cursor.getLastConfirmedAt());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("confirmedAt", cursor.getLastConfirmedAt());
        return result;
    }

    // ---------------- N06 个人奖励领取 ----------------

    /** 合作专属装饰编码（达成后每人限领一件；已拥有转固定替代星光 20，走 B01 幂等） */
    private static final String COOP_DECOR_CODE = "cooperation_badge";
    private static final int COOP_ALT_STARLIGHT = 20;

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

    private void requireFeature(boolean enabled) {
        if (!enabled) {
            throw new BusinessException(PetErrorCodes.PET_FEATURE_DISABLED, "该功能暂未开放");
        }
    }

    // ---------------- N06 好友合作周任务 ----------------

    /**
     * 创建邀请（§7.6）：明确指定受邀好友——非本人、有效好友、双方未拉黑；
     * 本周剩余业务日 ≥3；周名额由 uk_cooperation_inviter 兜底。
     */
    @Transactional
    public Map<String, Object> createCooperation(Long userId, Long inviteeUserId) {
        requireFeature(properties.getFeatureSwitches().isCooperation());
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
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "本周剩余时间不足，下周一再来组队吧");
        }
        PetCooperation cooperation = new PetCooperation();
        cooperation.setWeekStart(weekStart);
        cooperation.setInviterUserId(userId);
        cooperation.setInviterPetId(pet.getId());
        // R35：明确保存目标受邀人——好友 C 不能接受发给 B 的邀请（T63）
        cooperation.setInviteeUserId(inviteeUserId);
        cooperation.setStatus("INVITED");
        cooperation.setInviteExpiresAt(petClock.nowUtc().plusHours(24));
        cooperation.setContributions(PetJsonUtils.toJson(Map.of("inviter", 0, "invitee", 0)));
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
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ACTIVE");
        return result;
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
        if (userId < (inviteeId != null ? inviteeId : userId)) {
            guardService.lockGuard(userId);
            guardService.lockGuard(inviterId);
            guardService.lockGuard(inviteeId);
        } else {
            guardService.lockGuard(inviteeId);
            guardService.lockGuard(inviterId);
            guardService.lockGuard(userId);
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
        requireFeature(properties.getFeatureSwitches().isCollection());
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
}
