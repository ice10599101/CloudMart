package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonReward;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonRewardMapper;
import com.cloudmart.pet.service.impl.PetConfigGovernanceService;
import com.cloudmart.pet.service.impl.PetSeasonSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 赛季管理端（F2）：赛季创建/到期只能延后、奖励梯度编辑（区间连续互斥校验）、手动触发结算。
 * INTERNAL 鉴权 + mall-admin 代理（SEC-01）；操作留痕（operator 快照）。
 */
@RestController
@RequestMapping("/admin/seasons")
@Tag(name = "宠物管理·赛季", description = "赛季配置、奖励梯度、手动结算（F2）")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
public class AdminPetSeasonController {

    private final PetSeasonMapper seasonMapper;
    private final PetSeasonRewardMapper rewardMapper;
    private final com.cloudmart.pet.repository.PetSeasonSettlementJobMapper jobMapper;
    private final PetSeasonSettlementService settlementService;
    private final PetConfigGovernanceService governance;
    private final com.cloudmart.pet.repository.PetSkinConfigMapper skinConfigMapper;
    private final com.cloudmart.pet.repository.PetSeasonPassClaimMapper claimMapper;
    private final com.cloudmart.pet.service.PetSeasonPassService seasonPassService;

    /** R05：最终档赛季皮肤码（与 PetSeasonPassServiceImpl 同一配置源；空=不承诺皮肤） */
    @org.springframework.beans.factory.annotation.Value("${pet.season-pass.final-tier-skin-code:}")
    private String finalTierSkinCode;

    @GetMapping
    @Operation(summary = "赛季列表", description = "分页，按结束时间倒序")
    public ApiResponse<List<PetSeason>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<PetSeason> result = seasonMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(size, 100)),
                new LambdaQueryWrapper<PetSeason>().orderByDesc(PetSeason::getEndsAt));
        return ApiResponse.ok(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** R05：皮肤承诺校验——配置存在才允许发布承诺；未配置=只发币，描述不承诺皮肤 */
    private boolean skinConfigExists(String skinCode) {
        return skinConfigMapper.selectCount(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetSkinConfig>()
                .eq(com.cloudmart.pet.entity.PetSkinConfig::getCode, skinCode)) > 0;
    }

    private String seasonPassSkinCode() {
        return finalTierSkinCode == null ? "" : finalTierSkinCode.trim();
    }

    /** 赛季创建/更新请求（R05：claimUntil 领奖截止可空=随赛季结束） */
    public record SeasonUpsertRequest(Long id, @NotBlank String name,
                                      @NotNull LocalDateTime startsAt,
                                      @NotNull LocalDateTime endsAt,
                                      LocalDateTime claimUntil) {
    }

    @PostMapping
    @Operation(summary = "新增/更新赛季", description = "带 id 为更新；ends_at 只允许延后（已结算赛季不可改）；时间一律 UTC")
    public ApiResponse<PetSeason> upsert(@Valid @RequestBody SeasonUpsertRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "结束时间必须晚于开始时间");
        }
        // R05 发布校验：承诺最终档赛季皮肤则皮肤配置必须真实存在（不允许缺资源仍承诺发放）
        String skinCode = seasonPassSkinCode();
        if (!skinCode.isBlank() && skinConfigExists(skinCode) == false) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "最终档皮肤配置不存在: " + skinCode + "，请先在皮肤配置中上架或清空 pet.season-pass.final-tier-skin-code");
        }
        // 进行中的赛季只允许一个（ACTIVE 状态唯一）
        PetSeason season = new PetSeason();
        season.setId(request.id());
        season.setName(request.name().strip());
        season.setStartsAt(request.startsAt());
        season.setEndsAt(request.endsAt());
        season.setClaimUntil(request.claimUntil());
        if (request.id() != null) {
            PetSeason existing = seasonMapper.selectById(request.id());
            if (existing == null) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "赛季不存在");
            }
            if ("SETTLED".equals(existing.getStatus())) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "已结算赛季不可修改");
            }
            if (request.endsAt().isBefore(existing.getEndsAt())) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "结束时间只允许延后");
            }
            season.setStatus(existing.getStatus());
            // R20：更新写入→快照→版本登记同事务（mutateAndRecord 事务边界），0 行回滚
            final Long seasonId = request.id();
            governance.mutateAndRecord("pet_season", () -> seasonId,
                    PetConfigGovernanceService.currentOperator(),
                    () -> {
                        if (seasonMapper.updateById(season) == 0) {
                            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND,
                                    "配置不存在: " + seasonId);
                        }
                    });
        } else {
            // R06：创建在 pet_season_guard 行锁内复验——先 count 后 insert 的读快照竞态不再产生双 ACTIVE
            season.setStatus("ACTIVE");
            // 档位写入收口在 settlementService 独立事务方法内，保持原结构，写后单独快照登记
            settlementService.createSeasonGuarded(season);
            governance.snapshotAndRecord("pet_season", season.getId(),
                    PetConfigGovernanceService.currentOperator());
        }
        return ApiResponse.ok(season);
    }

    /** 奖励梯度保存请求（整表替换） */
    public record SeasonRewardsRequest(@NotNull List<Tier> tiers) {
        public record Tier(Integer rankMin, Integer rankMax, Integer rewardCoins, Integer rewardExp) {
        }
    }

    @PostMapping("/{id}/rewards")
    @Operation(summary = "保存奖励梯度", description = "整表替换；区间必须从第 1 名起连续覆盖；已结算赛季拒绝")
    public ApiResponse<Void> saveRewards(@PathVariable("id") Long id,
                                         @Valid @RequestBody SeasonRewardsRequest request) {
        PetSeason season = seasonMapper.selectById(id);
        if (season == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "赛季不存在");
        }
        // R17：替换收口到事务应用服务（原 Controller 内先 delete 再逐条 insert 无事务，
        // 中途失败留空/半张奖励表）；FREEZING/SETTLING 一并禁止改奖励
        List<PetSeasonReward> tiers = request.tiers().stream().map(tier -> {
            PetSeasonReward reward = new PetSeasonReward();
            reward.setSeasonId(id);
            reward.setRankMin(tier.rankMin());
            reward.setRankMax(tier.rankMax());
            reward.setRewardCoins(tier.rewardCoins() != null ? tier.rewardCoins() : 0);
            reward.setRewardExp(tier.rewardExp() != null ? tier.rewardExp() : 0);
            return reward;
        }).toList();
        settlementService.validateTiers(tiers);
        settlementService.replaceTiersGuarded(id, tiers);
        governance.snapshotAndRecord("pet_season", id,
                PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/rewards")
    @Operation(summary = "奖励梯度列表")
    public ApiResponse<List<PetSeasonReward>> rewards(@PathVariable("id") Long id) {
        return ApiResponse.ok(rewardMapper.selectList(new LambdaQueryWrapper<PetSeasonReward>()
                .eq(PetSeasonReward::getSeasonId, id)
                .orderByAsc(PetSeasonReward::getRankMin)));
    }

    @PostMapping("/{id}/settle")
    @Operation(summary = "手动触发结算（R06/PET-02）", description = "把到期赛季推进 FREEZING→SETTLING 并建立结算作业，"
            + "返回作业行（jobId/游标/进度）；分批发奖由调度器按游标续跑，请求线程不同步结完全部名次；"
            + "失败不回退 ACTIVE——作业行记录错误，续跑/接管收敛；SETTLED 只在全量发奖后写")
    public ApiResponse<com.cloudmart.pet.entity.PetSeasonSettlementJob> settle(@PathVariable("id") Long id) {
        return ApiResponse.ok(settlementService.requestSettlement(id));
    }

    @GetMapping("/{id}/settlement-jobs")
    @Operation(summary = "结算作业列表（PET-02）", description = "当前与历史作业：状态、游标、成功/失败计数、"
            + "租约 owner/版本/到期、最近错误与重试时间；成功作业只读，仅失败批次可重试")
    public ApiResponse<List<com.cloudmart.pet.entity.PetSeasonSettlementJob>> settlementJobs(
            @PathVariable("id") Long id) {
        return ApiResponse.ok(jobMapper.selectList(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetSeasonSettlementJob>()
                .eq(com.cloudmart.pet.entity.PetSeasonSettlementJob::getSeasonId, id)
                .orderByDesc(com.cloudmart.pet.entity.PetSeasonSettlementJob::getId)));
    }

    @PostMapping("/{id}/settlement-jobs/{jobId}/retry")
    @Operation(summary = "重试失败批次（PET-02）", description = "清错误标记并立即驱动一轮续跑；仅 RUNNING 且有"
            + "失败记录的作业可重试。已有成功奖励不可重发——由行级奖励 CAS + 钱包幂等键收敛，只补未完成名次")
    public ApiResponse<com.cloudmart.pet.entity.PetSeasonSettlementJob> retrySettlement(
            @PathVariable("id") Long id, @PathVariable("jobId") Long jobId) {
        return ApiResponse.ok(settlementService.retrySettlement(id, jobId));
    }

    @GetMapping("/pass-claims")
    @Operation(summary = "通行证领取事实台账（T18）", description = "按状态/用户/赛季筛选，倒序；"
            + "NEEDS_ATTENTION 为交付达上限或不可重试，需人工处置")
    public ApiResponse<List<com.cloudmart.pet.entity.PetSeasonPassClaim>> passClaims(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        LambdaQueryWrapper<com.cloudmart.pet.entity.PetSeasonPassClaim> wrapper =
                new LambdaQueryWrapper<com.cloudmart.pet.entity.PetSeasonPassClaim>()
                        .eq(status != null && !status.isBlank(),
                                com.cloudmart.pet.entity.PetSeasonPassClaim::getStatus, status)
                        .eq(userId != null, com.cloudmart.pet.entity.PetSeasonPassClaim::getUserId, userId)
                        .eq(seasonId != null, com.cloudmart.pet.entity.PetSeasonPassClaim::getSeasonId, seasonId)
                        .orderByDesc(com.cloudmart.pet.entity.PetSeasonPassClaim::getId);
        Page<com.cloudmart.pet.entity.PetSeasonPassClaim> result = claimMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(size, 100)), wrapper);
        return ApiResponse.ok(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @PostMapping("/pass-claims/{id}/retry")
    @Operation(summary = "人工重试领取交付（T18）", description = "重放原事实（领取时快照），不新造奖励；"
            + "已 SETTLED 幂等返回；需填写原因留痕")
    public ApiResponse<com.cloudmart.pet.entity.PetSeasonPassClaim> retryPassClaim(
            @PathVariable("id") Long id,
            @RequestParam(value = "reason", required = false) String reason) {
        String cause = reason == null || reason.isBlank() ? "人工处置" : reason.strip();
        return ApiResponse.ok(seasonPassService.retryClaim(id, cause));
    }
}
