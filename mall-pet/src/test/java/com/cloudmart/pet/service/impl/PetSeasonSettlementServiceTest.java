package com.cloudmart.pet.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetSeasonReward;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * R06 赛季奖励梯度校验：null 前置拒绝（原实现在 sort 时 NPE，R17 证据）、
 * 负奖励拒绝、从第 1 名连续覆盖（间隙/重叠拒绝）。
 */
@DisplayName("PetSeasonSettlementService 梯度校验测试（R06/R17）")
class PetSeasonSettlementServiceTest {

    private PetSeasonSettlementService service;

    @BeforeEach
    void setUp() {
        service = new PetSeasonSettlementService(
                mock(com.cloudmart.pet.repository.PetSeasonMapper.class),
                mock(com.cloudmart.pet.repository.PetSeasonRewardMapper.class),
                mock(com.cloudmart.pet.repository.PetSeasonRankingMapper.class),
                mock(com.cloudmart.pet.repository.PetSeasonSettlementJobMapper.class),
                mock(com.cloudmart.pet.repository.PetMapper.class),
                new com.cloudmart.pet.config.PetProperties(),
                mock(com.cloudmart.pet.wallet.PetEconomyService.class),
                mock(com.cloudmart.pet.mq.PetEventProducer.class),
                mock(PetStateService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
    }

    @Test
    @DisplayName("合法梯度：从第 1 名连续覆盖，通过")
    void validTiers() {
        assertThatCode(() -> service.validateTiers(List.of(
                tier(1, 10, 100, 50), tier(11, 50, 50, 20), tier(51, 200, 10, 5))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("R17：rankMin 为 null 在排序前被拒绝（不再 NPE 500）")
    void nullRankMinRejected() {
        PetSeasonReward bad = new PetSeasonReward();
        bad.setRankMax(10);
        assertThatThrownBy(() -> service.validateTiers(List.of(bad)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
    }

    @Test
    @DisplayName("负奖励拒绝（R17：原实现未限制）")
    void negativeRewardRejected() {
        assertThatThrownBy(() -> service.validateTiers(List.of(tier(1, 10, -5, 50))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
    }

    @Test
    @DisplayName("间隙/重叠拒绝：梯度必须从第 1 名连续覆盖")
    void gapAndOverlapRejected() {
        assertThatThrownBy(() -> service.validateTiers(List.of(tier(2, 10, 10, 0))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
        assertThatThrownBy(() -> service.validateTiers(List.of(tier(1, 10, 10, 0), tier(10, 20, 5, 0))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
    }

    private PetSeasonReward tier(int min, int max, int starlight, int exp) {
        PetSeasonReward reward = new PetSeasonReward();
        reward.setRankMin(min);
        reward.setRankMax(max);
        reward.setRewardStarlight(starlight);
        reward.setRewardExp(exp);
        return reward;
    }
}
