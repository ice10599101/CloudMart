package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetCooperation;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetCooperationMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetEventService;
import com.cloudmart.pet.wallet.PetWalletQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量聚合读端（§7.2）：GET /bootstrap 与 GET /activity-center。
 *
 * <p>约束：只读聚合——不调 AI、不调远程文件、不查全量流水；无宠物返回空 pets
 * 而非抛"宠物不存在"（首启引导可渲染）；非本人 petId 明确拒绝；
 * 展示型子聚合（钱包/任务/活动）单项降级为 null/空，不拖垮整个 bootstrap。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetBootstrapService {

    private final PetMapper petMapper;
    private final PetClock petClock;
    private final PetProperties properties;
    private final PetActivityMutex activityMutex;
    private final PetQuotaService quotaService;
    private final PetActivityMapper activityMapper;
    private final PetCooperationMapper cooperationMapper;
    private final PetWalletQueryService walletQueryService;
    private final PetStateService stateService;
    private final PetDailyQuestService questService;
    private final PetEventService eventService;

    /** §7.2 GET /bootstrap?petId=：启动聚合快照 */
    public Map<String, Object> bootstrap(Long userId, Long petId) {
        LocalDate businessDate = petClock.businessDate();
        Map<String, Object> result = new HashMap<>();
        result.put("serverNow", petClock.nowUtc());
        result.put("businessDate", businessDate);
        result.put("nextResetAt", petClock.businessDateStartUtc(businessDate.plusDays(1)));

        Pet activePet = activePetOf(userId);
        result.put("activePetId", activePet == null ? null : activePet.getId());
        result.put("pets", petSummaries(userId));
        result.put("selectedPet", petSnapshot(userId, petId != null ? petId
                : activePet == null ? null : activePet.getId()));
        result.put("walletSummary", walletSummary(userId));
        result.put("capabilities", capabilities());
        result.put("quotas", quotas(userId));
        result.put("actionAvailability", actionAvailability(userId));
        result.put("pendingOperations", pendingOperations(userId));
        return result;
    }

    /** §7.2 GET /activity-center?petId=&cursor=&size=：活动中心聚合摘要（明细列表走既有端点） */
    public Map<String, Object> activityCenter(Long userId, Long petId) {
        Map<String, Object> result = new HashMap<>();
        result.put("serverNow", petClock.nowUtc());
        result.put("businessDate", petClock.businessDate());
        result.put("accountBusyActivity", accountBusy(userId));
        result.put("selectedPetActivity", selectedPetActivity(userId, petId));
        result.put("pendingClaimsCount", pendingClaimsCount(userId));
        result.put("dailySetSummary", dailySetSummary(userId));
        result.put("eventSummary", eventSummary(userId));
        result.put("cooperationSummary", cooperationSummary(userId));
        return result;
    }

    // ---------------- 私有聚合 ----------------

    private Pet activePetOf(Long userId) {
        return petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
    }

    private List<Map<String, Object>> petSummaries(Long userId) {
        return petMapper.selectList(new LambdaQueryWrapper<Pet>()
                        .eq(Pet::getUserId, userId)
                        .orderByDesc(Pet::getIsActive)
                        .orderByAsc(Pet::getId))
                .stream()
                .map(pet -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("petId", pet.getId());
                    item.put("name", pet.getName());
                    item.put("level", pet.getLevel());
                    item.put("isActive", Boolean.TRUE.equals(pet.getIsActive()));
                    return item;
                })
                .toList();
    }

    /** 选中宠物的权威快照；petId 为空返回 null；非本人/不存在拒绝（§7.2） */
    private Map<String, Object> petSnapshot(Long userId, Long petId) {
        if (petId == null) {
            return null;
        }
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "宠物不存在或无权访问");
        }
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("petId", pet.getId());
        snapshot.put("name", pet.getName());
        snapshot.put("level", pet.getLevel());
        snapshot.put("version", pet.getVersion());
        snapshot.put("isActive", Boolean.TRUE.equals(pet.getIsActive()));
        snapshot.put("intimacy", pet.getIntimacy());
        snapshot.put("companionSeconds", pet.getCompanionSeconds());
        snapshot.put("todayCompanionSeconds", pet.getTodayCompanionSeconds());
        snapshot.put("weak", stateService.isWeak(pet));
        snapshot.put("sick", stateService.isSick(pet));
        return snapshot;
    }

    /** 钱包摘要（展示型 Fail-Open：查询异常降级 null，不阻断 bootstrap） */
    private Map<String, Object> walletSummary(Long userId) {
        try {
            PetWalletAccount wallet = walletQueryService.getWallet(userId);
            if (wallet == null) {
                return null;
            }
            Map<String, Object> summary = new HashMap<>();
            summary.put("currency", wallet.getCurrency());
            summary.put("balance", wallet.getBalance());
            summary.put("status", wallet.getStatus());
            return summary;
        } catch (Exception e) {
            log.warn("bootstrap 钱包摘要降级: userId={}", userId, e);
            return null;
        }
    }

    private Map<String, Object> capabilities() {
        PetProperties.FeatureSwitches switches = properties.getFeatureSwitches();
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("timedRest", switches.isTimedRest());
        capabilities.put("walletIdempotent", switches.isWalletIdempotent());
        capabilities.put("minigame", switches.isMinigame());
        capabilities.put("custody", switches.isCustody());
        capabilities.put("cooperation", switches.isCooperation());
        capabilities.put("onboarding", switches.isOnboarding());
        capabilities.put("collection", switches.isCollection());
        capabilities.put("diary", switches.isDiary());
        return capabilities;
    }

    /** 关键每日额度余量（数据库权威口径，targetId=0 的全局日额度） */
    private Map<String, Object> quotas(Long userId) {
        Map<String, Object> quotas = new HashMap<>();
        quotas.put("feed", quotaService.remaining(userId, PetQuotaService.QuotaType.FEED, 0L,
                properties.getInteraction().getFeedDailyLimit()));
        quotas.put("playReward", quotaService.remaining(userId, PetQuotaService.QuotaType.PLAY_REWARD, 0L,
                properties.getInteraction().getPlayRewardDailyLimit()));
        quotas.put("wallPost", quotaService.remaining(userId, PetQuotaService.QuotaType.WALL_POST, 0L,
                properties.getWall().getDailyPostLimit()));
        quotas.put("report", quotaService.remaining(userId, PetQuotaService.QuotaType.REPORT, 0L,
                properties.getModeration().getReportDailyLimit()));
        return quotas;
    }

    private Map<String, Object> actionAvailability(Long userId) {
        Map<String, Object> quotas = quotas(userId);
        Map<String, Object> availability = new HashMap<>();
        boolean busy = activityMutex.hasBusyActivity(userId) || activityMutex.hasActiveCustody(userId);
        availability.put("busy", busy);
        availability.put("canFeed", Integer.valueOf(quotas.get("feed").toString()) > 0 && !busy);
        availability.put("canPlay", Integer.valueOf(quotas.get("playReward").toString()) > 0 && !busy);
        availability.put("canWallPost", Integer.valueOf(quotas.get("wallPost").toString()) > 0);
        return availability;
    }

    /** 待处理操作：可领取奖励数 + 收到的待回应邀请数（§7.2 pendingOperations 子集，全部本地 COUNT） */
    private Map<String, Object> pendingOperations(Long userId) {
        Map<String, Object> pending = new HashMap<>();
        pending.put("pendingActivityClaims", pendingClaimsCount(userId));
        pending.put("receivedInvites", cooperationMapper.selectCount(new LambdaQueryWrapper<PetCooperation>()
                .eq(PetCooperation::getInviteeUserId, userId)
                .eq(PetCooperation::getStatus, "INVITED")
                .gt(PetCooperation::getInviteExpiresAt, petClock.nowUtc())));
        return pending;
    }

    /** 账号级占用：互斥活动（工作/读书/职业/捞瓶/小游戏）或托管中 */
    private Map<String, Object> accountBusy(Long userId) {
        Map<String, Object> busy = new HashMap<>();
        busy.put("activity", activityMutex.hasBusyActivity(userId));
        busy.put("custody", activityMutex.hasActiveCustody(userId));
        return busy;
    }

    /** 选中宠物的进行中活动（petId 缺省取主宠；非本人拒绝） */
    private Map<String, Object> selectedPetActivity(Long userId, Long petId) {
        Long targetPetId = petId;
        if (targetPetId == null) {
            Pet activePet = activePetOf(userId);
            targetPetId = activePet == null ? null : activePet.getId();
        }
        if (targetPetId == null) {
            return null;
        }
        Pet pet = petMapper.selectById(targetPetId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "宠物不存在或无权访问");
        }
        PetActivity activity = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getPetId, targetPetId)
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name())
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 1"));
        if (activity == null) {
            return null;
        }
        Map<String, Object> view = new HashMap<>();
        view.put("activityId", activity.getId());
        view.put("activityType", activity.getActivityType());
        view.put("status", activity.getStatus());
        view.put("startedAt", activity.getStartedAt());
        view.put("finishedAt", activity.getFinishedAt());
        return view;
    }

    private long pendingClaimsCount(Long userId) {
        Long count = activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, PetActivityStatus.COMPLETED.name()));
        return count == null ? 0 : count;
    }

    /** 今日任务集摘要（展示型 Fail-Open：任务服务异常降级 null） */
    private Map<String, Object> dailySetSummary(Long userId) {
        try {
            com.cloudmart.pet.vo.PetDailyQuestVO quest = questService.list(userId);
            Map<String, Object> summary = new HashMap<>();
            summary.put("questDate", quest.questDate());
            summary.put("completedCount", quest.completedCount());
            summary.put("claimedCount", quest.claimedCount());
            summary.put("totalCount", quest.totalCount());
            summary.put("chestClaimable", quest.chestClaimable());
            return summary;
        } catch (Exception e) {
            log.warn("bootstrap 任务摘要降级: userId={}", userId, e);
            return null;
        }
    }

    /** 限时活动摘要（进行中/可领奖计数，明细走 GET /events） */
    private Map<String, Object> eventSummary(Long userId) {
        try {
            List<com.cloudmart.pet.vo.PetEventVO> events = eventService.events(userId);
            List<String> claimableCodes = events.stream()
                    .filter(event -> Boolean.TRUE.equals(event.claimable()))
                    .map(com.cloudmart.pet.vo.PetEventVO::code)
                    .limit(10)
                    .toList();
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalCount", events.size());
            summary.put("claimableCount", claimableCodes.size());
            summary.put("claimableCodes", claimableCodes);
            return summary;
        } catch (Exception e) {
            log.warn("bootstrap 活动摘要降级: userId={}", userId, e);
            return null;
        }
    }

    /** 本周合作摘要（未参与返回 participated=false；展示型 Fail-Open） */
    private Map<String, Object> cooperationSummary(Long userId) {
        try {
            LocalDate weekStart = petClock.businessDate().with(DayOfWeek.MONDAY);
            PetCooperation cooperation = cooperationMapper.selectOne(new LambdaQueryWrapper<PetCooperation>()
                    .eq(PetCooperation::getWeekStart, weekStart)
                    .and(w -> w.eq(PetCooperation::getInviterUserId, userId)
                            .or().eq(PetCooperation::getInviteeUserId, userId))
                    .last("LIMIT 1"));
            Map<String, Object> summary = new HashMap<>();
            if (cooperation == null) {
                summary.put("participated", false);
                return summary;
            }
            summary.put("participated", true);
            summary.put("cooperationId", cooperation.getId());
            summary.put("status", cooperation.getStatus());
            summary.put("role", userId.equals(cooperation.getInviterUserId()) ? "INVITER" : "INVITEE");
            return summary;
        } catch (Exception e) {
            log.warn("bootstrap 合作摘要降级: userId={}", userId, e);
            return null;
        }
    }
}
