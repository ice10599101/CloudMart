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

    private void requireFeature(boolean enabled) {
        if (!enabled) {
            throw new BusinessException(PetErrorCodes.PET_FEATURE_DISABLED, "该功能暂未开放");
        }
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
