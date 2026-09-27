# -*- coding: utf-8 -*-
"""BE-01/BE-02 accept/contribution/claim 重写（一次性，用后即删）"""
import io

P = r'mall-pet/src/main/java/com/cloudmart/pet/service/impl/PetPlayFeatureService.java'
s = io.open(P, encoding='utf-8').read()

ACCEPT_NEW = '''    /**
     * 接受邀请（B02/BE-02）：双守卫行锁（userId 升序防死锁）+ 完整资格校验 + CAS 状态迁移。
     * 校验：邀请存在/待接受/未过期、非本人邀请、双方未拉黑、有效好友、本周剩余 >=3 业务日；
     * 并发：CAS（status='INVITED'）保证同一邀请只被接受一次（后来者明确冲突，不覆盖先接受者）；
     * 受邀人周名额由 uk_cooperation_invitee(invitee_user_id, week_start) 兜底。
     */
    @Transactional
    public Map<String, Object> acceptCooperation(Long userId, Long cooperationId, Long inviterUserId) {
        PetCooperation cooperation = cooperationMapper.selectById(cooperationId);
        if (cooperation == null || !"INVITED".equals(cooperation.getStatus())
                || cooperation.getInviteExpiresAt().isBefore(petClock.nowUtc())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "邀请不存在或已过期");
        }
        if (inviterUserId == null || !inviterUserId.equals(cooperation.getInviterUserId())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "邀请不存在");
        }
        if (userId.equals(cooperation.getInviterUserId())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "不能接受自己发起的邀请");
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
'''

CONTRIB_NEW = '''    /**
     * 贡献一次有效照顾（N06/B02-BE-02）：唯一事件去重 + 每人每天 1 次。
     * 计数来源改为贡献事实表 COUNT（并发安全），替代原 contributions JSON 无锁读改写（丢计数）；
     * 用户守卫行锁串行化同用户并发贡献；双方各 3 个不同业务日达成 -> CAS 置 COMPLETED。
     */
    @Transactional
    public void recordContribution(Long userId, String eventId) {
        PetCooperation cooperation = cooperationMapper.selectOne(new LambdaQueryWrapper<PetCooperation>()
                .eq(PetCooperation::getStatus, "ACTIVE")
                .and(w -> w.eq(PetCooperation::getInviterUserId, userId)
                        .or().eq(PetCooperation::getInviteeUserId, userId))
                .last("LIMIT 1"));
        if (cooperation == null) {
            return;
        }
        guardService.lockGuard(userId);
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
'''

CLAIM_NEW = '''    /**
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
            PetOperationService.WalletSettlement settlement = economyService.earn(
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
'''

# --- 替换顺序：文件中 claim(前) < requireFeature < accept(后) < coop-query ---
i = s.find('    /** N06 个人领取：COMPLETED')
j2 = s.find('    private void requireFeature(boolean enabled) {')
assert i > 0 and j2 > i, 'claim anchors'
s = s[:i] + CLAIM_NEW + '
' + s[j2:]

i = s.find('    /** 接受邀请：重验关系/周名额/剩余业务日 */')
j2 = s.find('    /** 查询当前/历史合作任务')
assert i > 0 and j2 > i, 'accept block anchors'
s = s[:i] + ACCEPT_NEW + '
' + CONTRIB_NEW + '
' + s[j2:]

io.open(P, encoding='utf-8').read()

ACCEPT_NEW = '''    /**
     * 接受邀请（B02/BE-02）：双守卫行锁（userId 升序防死锁）+ 完整资格校验 + CAS 状态迁移。
     * 校验：邀请存在/待接受/未过期、非本人邀请、双方未拉黑、有效好友、本周剩余 >=3 业务日；
     * 并发：CAS（status='INVITED'）保证同一邀请只被接受一次（后来者明确冲突，不覆盖先接受者）；
     * 受邀人周名额由 uk_cooperation_invitee(invitee_user_id, week_start) 兜底。
     */
    @Transactional
    public Map<String, Object> acceptCooperation(Long userId, Long cooperationId, Long inviterUserId) {
        PetCooperation cooperation = cooperationMapper.selectById(cooperationId);
        if (cooperation == null || !"INVITED".equals(cooperation.getStatus())
                || cooperation.getInviteExpiresAt().isBefore(petClock.nowUtc())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "邀请不存在或已过期");
        }
        if (inviterUserId == null || !inviterUserId.equals(cooperation.getInviterUserId())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "邀请不存在");
        }
        if (userId.equals(cooperation.getInviterUserId())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "不能接受自己发起的邀请");
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
'''

CONTRIB_NEW = '''    /**
     * 贡献一次有效照顾（N06/B02-BE-02）：唯一事件去重 + 每人每天 1 次。
     * 计数来源改为贡献事实表 COUNT（并发安全），替代原 contributions JSON 无锁读改写（丢计数）；
     * 用户守卫行锁串行化同用户并发贡献；双方各 3 个不同业务日达成 -> CAS 置 COMPLETED。
     */
    @Transactional
    public void recordContribution(Long userId, String eventId) {
        PetCooperation cooperation = cooperationMapper.selectOne(new LambdaQueryWrapper<PetCooperation>()
                .eq(PetCooperation::getStatus, "ACTIVE")
                .and(w -> w.eq(PetCooperation::getInviterUserId, userId)
                        .or().eq(PetCooperation::getInviteeUserId, userId))
                .last("LIMIT 1"));
        if (cooperation == null) {
            return;
        }
        guardService.lockGuard(userId);
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
'''

CLAIM_NEW = '''    /**
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
            PetOperationService.WalletSettlement settlement = economyService.earn(
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
'''

# --- 替换 accept（从注释行到 requireFeature 前） ---
i = s.find('    /** 接受邀请：重验关系/周名额/剩余业务日 */')
j = s.find('private void requireFeature(boolean enabled)')
assert i > 0 and j > i
s = s[:i] + ACCEPT_NEW + '\n    ' + s[j:]

# --- 替换 contribution ---
i = s.find('    /** 贡献一次有效照顾（N06）：唯一事件去重')
j = s.find('    /** 查询当前/历史合作任务')
assert i > 0 and j > i, 'contrib anchors'
s = s[:i] + CONTRIB_NEW + '\n' + s[j:]

# --- 替换 claim ---
i = s.find('    /** N06 个人领取：COMPLETED 后参与双方各自领取')
j = s.find('    private void requireFeature(boolean enabled) {')
assert i > 0 and j > i, 'claim anchors'
s = s[:i] + CLAIM_NEW + '\n' + s[j:]

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('ok all three rewritten')
