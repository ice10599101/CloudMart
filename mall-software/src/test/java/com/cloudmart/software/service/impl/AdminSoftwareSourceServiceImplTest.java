package com.cloudmart.software.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SoftwareChannel;
import com.cloudmart.software.enums.SoftwarePlatform;
import com.cloudmart.software.enums.SoftwareSourceType;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import com.cloudmart.software.service.SoftwareAuditService;
import com.cloudmart.software.vo.software.AdminSoftwareSourceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * SW06：来源核验关联必须按 revision → release → source，并与脱敏视图一致。
 * 夹具刻意让 softwareId/revisionId/releaseId/sourceId 互不相同，暴露“ID 恰巧相同”的掩盖。
 */
class AdminSoftwareSourceServiceImplTest {

    private static final Long SOFTWARE_ID = 101L;
    private static final Long REVISION_ID = 203L;
    private static final Long RELEASE_ID = 307L;
    private static final String SOURCE_KEY = "11111111-1111-4111-8111-111111111111";
    private static final String FINGERPRINT = "a".repeat(64);

    private final SoftwareAccessPolicy accessPolicy = Mockito.mock(SoftwareAccessPolicy.class);
    private final SoftwareReleaseMapper releaseMapper = Mockito.mock(SoftwareReleaseMapper.class);
    private final SoftwareSourceMapper sourceMapper = Mockito.mock(SoftwareSourceMapper.class);
    private final SoftwareSourceStateMapper stateMapper = Mockito.mock(SoftwareSourceStateMapper.class);
    private final SoftwareAuditService auditService = Mockito.mock(SoftwareAuditService.class);

    private AdminSoftwareSourceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminSoftwareSourceServiceImpl(accessPolicy, releaseMapper, sourceMapper,
                stateMapper, auditService, Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC));
        SoftwareRevision published = new SoftwareRevision();
        published.setId(REVISION_ID);
        published.setSoftwareId(SOFTWARE_ID);
        given(accessPolicy.visiblePublishedRevision(SOFTWARE_ID)).willReturn(published);

        SoftwareRelease release = new SoftwareRelease();
        release.setId(RELEASE_ID);
        release.setRevisionId(REVISION_ID);
        release.setReleaseKey("22222222-2222-4222-8222-222222222222");
        release.setVersionName("3.2.1");
        release.setChannel(SoftwareChannel.STABLE);
        release.setReleaseOrder(0);
        given(releaseMapper.selectList(any())).willReturn(List.of(release));

        SoftwareSource source = new SoftwareSource();
        source.setId(409L);
        source.setReleaseId(RELEASE_ID);
        source.setSourceKey(SOURCE_KEY);
        source.setSourceType(SoftwareSourceType.NETDISK);
        source.setPlatform(SoftwarePlatform.WINDOWS);
        source.setArch(com.cloudmart.software.enums.SoftwareArch.X64);
        source.setLabel("百度网盘");
        source.setUrl("https://pan.baidu.com/s/1secret");
        source.setContentFingerprint(FINGERPRINT);
        given(sourceMapper.selectList(any())).willReturn(List.of(source));
        given(stateMapper.selectOne(any())).willReturn(null);
        given(stateMapper.selectList(any())).willReturn(List.of());
    }

    @Test
    @DisplayName("按 revision→release→source 关联：核实用当前指纹，而不是把 revisionId 当 releaseId")
    void verifyUsesReleaseJoinAndCurrentFingerprint() {
        service.applyState(9L, SOFTWARE_ID, SOURCE_KEY, "AVAILABLE", "人工复核可用");

        ArgumentCaptor<SoftwareSourceState> captor = ArgumentCaptor.forClass(SoftwareSourceState.class);
        verify(stateMapper).insert(captor.capture());
        SoftwareSourceState saved = captor.getValue();
        assertThat(saved.getSoftwareId()).isEqualTo(SOFTWARE_ID);
        assertThat(saved.getSourceKey()).isEqualTo(SOURCE_KEY);
        assertThat(saved.getAvailability()).isEqualTo(SourceAvailability.AVAILABLE);
        assertThat(saved.getCheckedFingerprint()).isEqualTo(FINGERPRINT);
        assertThat(saved.getCheckedBy()).isEqualTo(9L);
        assertThat(saved.getReason()).isEqualTo("人工复核可用");

        verify(auditService).record(eq(SOFTWARE_ID), eq(REVISION_ID), org.mockito.ArgumentMatchers.isNull(), eq(9L), eq("ADMIN"),
                eq("SOURCE_VERIFY"), eq("NONE"), eq("AVAILABLE"),
                org.mockito.ArgumentMatchers.contains("fingerprint=" + FINGERPRINT), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    @DisplayName("来源不在当前发布快照：核实类拒绝，禁用允许且指纹为空")
    void missingSourceAllowsDisableOnly() {
        given(sourceMapper.selectList(any())).willReturn(List.of());

        assertThatThrownBy(() -> service.applyState(9L, SOFTWARE_ID, SOURCE_KEY, "AVAILABLE", "核实"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在当前线上快照");

        service.applyState(9L, SOFTWARE_ID, SOURCE_KEY, "DISABLED", "历史来源保持禁用");
        ArgumentCaptor<SoftwareSourceState> captor = ArgumentCaptor.forClass(SoftwareSourceState.class);
        verify(stateMapper).insert(captor.capture());
        assertThat(captor.getValue().getCheckedFingerprint()).isNull();
        assertThat(captor.getValue().getAvailability()).isEqualTo(SourceAvailability.DISABLED);
    }

    @Test
    @DisplayName("当前发布快照重复 sourceKey：报数据异常，不用 LIMIT 1 随机取")
    void duplicateSourceKeyRejected() {
        given(sourceMapper.selectList(any())).willReturn(List.of(new SoftwareSource(), new SoftwareSource()));

        assertThatThrownBy(() -> service.applyState(9L, SOFTWARE_ID, SOURCE_KEY, "AVAILABLE", "核实"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复 sourceKey");
    }

    @Test
    @DisplayName("来源清单：按版本/平台/架构返回，链接脱敏且不含提取码")
    void listSourcesMasksUrlAndJoinsState() {
        SoftwareSourceState state = new SoftwareSourceState();
        state.setSoftwareId(SOFTWARE_ID);
        state.setSourceKey(SOURCE_KEY);
        state.setAvailability(SourceAvailability.DISABLED);
        state.setReason("平台判定失效");
        state.setCheckedFingerprint(FINGERPRINT);
        given(stateMapper.selectList(any())).willReturn(List.of(state));

        List<AdminSoftwareSourceVO> rows = service.listSources(SOFTWARE_ID);

        assertThat(rows).hasSize(1);
        AdminSoftwareSourceVO row = rows.get(0);
        assertThat(row.versionName()).isEqualTo("3.2.1");
        assertThat(row.platform()).isEqualTo("WINDOWS");
        assertThat(row.arch()).isEqualTo("X64");
        assertThat(row.maskedUrl()).isEqualTo("https://pan.baidu.com/***");
        assertThat(row.availability()).isEqualTo("DISABLED");
        assertThat(row.statusReason()).isEqualTo("平台判定失效");
    }

    @Test
    @DisplayName("磁力链接同样脱敏")
    void listSourcesMasksMagnet() {
        SoftwareSource magnet = new SoftwareSource();
        magnet.setId(410L);
        magnet.setReleaseId(RELEASE_ID);
        magnet.setSourceKey("33333333-3333-4333-8333-333333333333");
        magnet.setSourceType(SoftwareSourceType.MAGNET);
        magnet.setPlatform(SoftwarePlatform.WINDOWS);
        magnet.setArch(com.cloudmart.software.enums.SoftwareArch.X64);
        magnet.setLabel("磁力");
        magnet.setUrl("magnet:?xt=urn:btih:abcdef");
        magnet.setContentFingerprint(FINGERPRINT);
        given(sourceMapper.selectList(any())).willReturn(List.of(magnet));

        List<AdminSoftwareSourceVO> rows = service.listSources(SOFTWARE_ID);
        assertThat(rows.get(0).maskedUrl()).isEqualTo("magnet:?xt=urn:btih:***");
    }
}
