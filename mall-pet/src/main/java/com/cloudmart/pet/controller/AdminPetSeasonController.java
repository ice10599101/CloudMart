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
    private final PetSeasonSettlementService settlementService;
    private final PetConfigGovernanceService governance;

    @GetMapping
    @Operation(summary = "赛季列表", description = "分页，按结束时间倒序")
    public ApiResponse<List<PetSeason>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<PetSeason> result = seasonMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(size, 100)),
                new LambdaQueryWrapper<PetSeason>().orderByDesc(PetSeason::getEndsAt));
        return ApiResponse.ok(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 赛季创建/更新请求 */
    public record SeasonUpsertRequest(Long id, @NotBlank String name,
                                      @NotNull LocalDateTime startsAt,
                                      @NotNull LocalDateTime endsAt) {
    }

    @PostMapping
    @Operation(summary = "新增/更新赛季", description = "带 id 为更新；ends_at 只允许延后（已结算赛季不可改）；时间一律 UTC")
    public ApiResponse<PetSeason> upsert(@Valid @RequestBody SeasonUpsertRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "结束时间必须晚于开始时间");
        }
        // 进行中的赛季只允许一个（ACTIVE 状态唯一）
        PetSeason season = new PetSeason();
        season.setId(request.id());
        season.setName(request.name().strip());
        season.setStartsAt(request.startsAt());
        season.setEndsAt(request.endsAt());
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
            seasonMapper.updateById(season);
        } else {
            // R06：创建在 pet_season_guard 行锁内复验——先 count 后 insert 的读快照竞态不再产生双 ACTIVE
            season.setStatus("ACTIVE");
            settlementService.createSeasonGuarded(season);
        }
        governance.snapshotAndRecord("pet_season", season.getId(),
                PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(season);
    }

    /** 奖励梯度保存请求（整表替换） */
    public record SeasonRewardsRequest(@NotNull List<Tier> tiers) {
        public record Tier(Integer rankMin, Integer rankMax, Integer rewardStarlight, Integer rewardExp) {
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
            reward.setRewardStarlight(tier.rewardStarlight() != null ? tier.rewardStarlight() : 0);
            reward.setRewardExp(tier.rewardExp() != null ? tier.rewardExp() : 0);
            return reward;
        }).toList();
        settlementService.validateTiers(tiers);
        settlementService.replaceTiersGuarded(id, season.getStatus(), tiers);
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
    @Operation(summary = "手动触发结算（R06）", description = "把到期 ACTIVE 赛季推进 FREEZING→SETTLING，异步按作业游标完成；"
            + "失败不回退 ACTIVE——作业行记录错误，续跑/接管收敛；SETTLED 只在全量发奖后写")
    public ApiResponse<Void> settle(@PathVariable("id") Long id) {
        settlementService.requestSettlement(id);
        return ApiResponse.ok(null);
    }
}
