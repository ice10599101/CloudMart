package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetReport;
import com.cloudmart.pet.entity.PetUserSanction;
import com.cloudmart.pet.repository.PetUserSanctionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R05 处罚事实测试：USER_PET_BANNED 生成双范围处罚、同举报同动作幂等、
 * 到期惰性流转 EXPIRED、撤销理由必填。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetAccessPolicy 处罚事实测试（R05）")
class PetAccessPolicyTest {

    @Mock
    private PetUserSanctionMapper sanctionMapper;

    private PetAccessPolicy policy;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetUserSanction.class);
    }

    @BeforeEach
    void setUp() {
        policy = new PetAccessPolicy(sanctionMapper);
        lenient().when(sanctionMapper.insert(any(PetUserSanction.class))).thenReturn(1);
        lenient().when(sanctionMapper.update(any(), any())).thenReturn(1);
        lenient().when(sanctionMapper.selectList(any())).thenReturn(List.of());
        lenient().when(sanctionMapper.selectOne(any())).thenReturn(null);
    }

    private PetReport report(long id) {
        PetReport report = new PetReport();
        report.setId(id);
        report.setReporterUserId(999L);
        report.setTargetType("NICKNAME");
        report.setTargetId(1001L);
        report.setStatus("PENDING");
        return report;
    }

    @Test
    @DisplayName("R05：USER_PET_BANNED 生成 SOCIAL_MUTE + PUBLIC_CONTENT_DISABLED 双范围处罚")
    void banIssuesBothScopes() {
        policy.issueSanction(report(1L), "USER_PET_BANNED", 1001L, 86400, "恶意昵称", 200L);

        verify(sanctionMapper, times(2)).insert(any(PetUserSanction.class));
    }

    @Test
    @DisplayName("R05：USER_WARNED 仅生成 WARN 记录（不限制写入）")
    void warnIssuesSingleRecord() {
        policy.issueSanction(report(1L), "USER_WARNED", 1001L, 0, "警告", 200L);

        ArgumentCaptorAssert.singleInsert(sanctionMapper, "WARN");
    }

    @Test
    @DisplayName("R05 幂等：同举报同动作已有处罚 → 返回既有记录不重罚")
    void sameReportSameActionIdempotent() {
        PetUserSanction existing = new PetUserSanction();
        existing.setId(700L);
        existing.setSourceReportId(1L);
        existing.setAction("USER_PET_BANNED");
        when(sanctionMapper.selectOne(any())).thenReturn(existing);

        PetUserSanction result = policy.issueSanction(report(1L), "USER_PET_BANNED", 1001L, 86400, "恶意昵称", 200L);

        assertThat(result.getId()).isEqualTo(700L);
        verify(sanctionMapper, never()).insert(any(PetUserSanction.class));
    }

    @Test
    @DisplayName("R05：到期处罚惰性流转 EXPIRED——isRestricted 返回 false")
    void expiredSanctionNotRestricted() {
        PetUserSanction expired = new PetUserSanction();
        expired.setId(800L);
        expired.setUserId(1001L);
        expired.setScope(PetAccessPolicy.SCOPE_SOCIAL_MUTE);
        expired.setStatus("ACTIVE");
        expired.setExpiresAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1));
        when(sanctionMapper.selectList(any())).thenReturn(List.of(expired));

        assertThat(policy.isSociallyMuted(1001L)).isFalse();
        verify(sanctionMapper).update(any(), any());
    }

    @Test
    @DisplayName("R05：未到期 ACTIVE → isRestricted true")
    void activeSanctionRestricts() {
        PetUserSanction active = new PetUserSanction();
        active.setId(801L);
        active.setUserId(1001L);
        active.setScope(PetAccessPolicy.SCOPE_SOCIAL_MUTE);
        active.setStatus("ACTIVE");
        active.setExpiresAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(24));
        when(sanctionMapper.selectList(any())).thenReturn(List.of(active));

        assertThat(policy.isSociallyMuted(1001L)).isTrue();
    }

    @Test
    @DisplayName("R05：撤销理由必填")
    void revokeReasonRequired() {
        assertThatThrownBy(() -> policy.revoke(900L, 200L, " "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
    }

    interface ArgumentCaptorAssert {
        static void singleInsert(PetUserSanctionMapper mapper, String scope) {
            org.mockito.ArgumentCaptor<PetUserSanction> captor =
                    org.mockito.ArgumentCaptor.forClass(PetUserSanction.class);
            org.mockito.Mockito.verify(mapper).insert(captor.capture());
            assertThat(captor.getValue().getScope()).isEqualTo(scope);
        }
    }
}
