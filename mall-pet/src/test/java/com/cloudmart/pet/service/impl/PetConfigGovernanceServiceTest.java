package com.cloudmart.pet.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.repository.PetConfigVersionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R07 配置回退保护：运行实体（pet/pet_season）禁止通用回退，
 * 防止快照把经验/version/主宠标记/赛季状态等运行字段写回覆盖。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetConfigGovernanceService 回退保护测试")
class PetConfigGovernanceServiceTest {

    @Mock
    private PetConfigVersionMapper versionMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("R07：rollback(pet) 直接拒绝，不触碰数据库")
    void rollbackPetRejected() {
        PetConfigGovernanceService service = new PetConfigGovernanceService(versionMapper, jdbcTemplate);

        assertThatThrownBy(() -> service.rollback("pet", 1L, 1, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
        verify(versionMapper, never()).selectList(any());
        verify(jdbcTemplate, never()).update(anyString(), (Object[]) any());
    }

    @Test
    @DisplayName("R07：rollback(pet_season) 直接拒绝——已结算赛季不可通过历史版本回到 ACTIVE")
    void rollbackPetSeasonRejected() {
        PetConfigGovernanceService service = new PetConfigGovernanceService(versionMapper, jdbcTemplate);

        assertThatThrownBy(() -> service.rollback("pet_season", 1L, 1, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
        verify(versionMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("R07：rollback 未知类型仍拒绝（防注入语义保持）")
    void rollbackUnknownTypeRejected() {
        PetConfigGovernanceService service = new PetConfigGovernanceService(versionMapper, jdbcTemplate);

        assertThatThrownBy(() -> service.rollback("unknown_type", 1L, 1, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
        verify(jdbcTemplate, never()).update(anyString(), (Object[]) any());
    }
}
