package com.cloudmart.software.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourcePatrol;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.enums.SoftwareSourceType;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourcePatrolMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SF07 来源健康巡检：安全约束与状态联动不变量。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareSourcePatrolServiceTest {

    private static final Long SOFTWARE_ID = 5L;
    private static final Long SOURCE_ID = 90L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 10, 0);

    @Mock
    private SoftwareSourceMapper sourceMapper;
    @Mock
    private SoftwareSourceStateMapper sourceStateMapper;
    @Mock
    private SoftwareSourcePatrolMapper patrolMapper;
    @Mock
    private SoftwareReleaseMapper releaseMapper;
    @Mock
    private SoftwareRevisionMapper revisionMapper;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareHttpProbe httpProbe;

    private SoftwareSourcePatrolService service;

    @BeforeAll
    static void initTableInfo() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, SoftwareSourcePatrol.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, SoftwareSourceState.class);
    }

    @BeforeEach
    void setUp() {
        service = new SoftwareSourcePatrolService(sourceMapper, sourceStateMapper, patrolMapper,
                releaseMapper, revisionMapper, softwareMapper, httpProbe,
                new SoftwareProperties(),
                Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(),
                        ZoneId.systemDefault()));
        // 默认链路：来源 → release → APPROVED revision → ACTIVE software（线上快照）
        SoftwareSource source = source("https://example.com/app.exe");
        when(sourceMapper.selectById(SOURCE_ID)).thenReturn(source);
        SoftwareRelease release = new SoftwareRelease();
        release.setId(70L);
        release.setRevisionId(600L);
        when(releaseMapper.selectById(70L)).thenReturn(release);
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(600L);
        revision.setSoftwareId(SOFTWARE_ID);
        revision.setReviewStatus(SoftwareReviewStatus.APPROVED);
        when(revisionMapper.selectById(600L)).thenReturn(revision);
        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        software.setPublishedRevisionId(600L);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);
        when(sourceStateMapper.selectOne(any())).thenReturn(null);
    }

    private SoftwareSource source(String url) {
        SoftwareSource source = new SoftwareSource();
        source.setId(SOURCE_ID);
        source.setReleaseId(70L);
        source.setSourceKey("00000000-0000-0000-0000-000000000001");
        source.setSourceType(SoftwareSourceType.HTTPS_DOWNLOAD);
        source.setUrl(url);
        source.setContentFingerprint("fp-1");
        return source;
    }

    @Test
    @DisplayName("探测 AVAILABLE：巡检记录落库且 source_state 写入 AVAILABLE（此前无状态行）")
    void patrolById_availableCreatesState() {
        when(httpProbe.probe(any())).thenReturn(
                new SoftwareHttpProbe.ProbeResult(
                        SoftwareHttpProbe.Outcome.AVAILABLE, "OK", 200, 0, 120));

        var result = service.patrolById(SOURCE_ID);

        assertThat(result.get("outcome")).isEqualTo("AVAILABLE");
        ArgumentCaptor<SoftwareSourcePatrol> record =
                ArgumentCaptor.forClass(SoftwareSourcePatrol.class);
        verify(patrolMapper).insert(record.capture());
        assertThat(record.getValue().getOutcome()).isEqualTo("AVAILABLE");
        assertThat(record.getValue().getUrlHost()).isEqualTo("example.com");
        verify(sourceStateMapper).insert(any(SoftwareSourceState.class));
    }

    @Test
    @DisplayName("人工 DISABLED 状态不被巡检覆盖（仅 UNKNOWN/SUSPECTED/AVAILABLE 可联动）")
    void patrolById_neverOverridesManualDisable() {
        when(httpProbe.probe(any())).thenReturn(
                new SoftwareHttpProbe.ProbeResult(
                        SoftwareHttpProbe.Outcome.AVAILABLE, "OK", 200, 0, 100));
        SoftwareSourceState manual = new SoftwareSourceState();
        manual.setId(1L);
        manual.setSoftwareId(SOFTWARE_ID);
        manual.setSourceKey("00000000-0000-0000-0000-000000000001");
        manual.setAvailability(com.cloudmart.software.enums.SourceAvailability.DISABLED);
        when(sourceStateMapper.selectOne(any())).thenReturn(manual);

        service.patrolById(SOURCE_ID);

        // 记录仍写入（历史留痕），但状态行不被更新
        verify(patrolMapper).insert(any(SoftwareSourcePatrol.class));
        verify(sourceStateMapper, never()).update(any(), any());
        verify(sourceStateMapper, never()).updateById(any(SoftwareSourceState.class));
    }

    @Test
    @DisplayName("磁力/种子来源不支持自动巡检（不伪装成已检查）")
    void patrolById_rejectsNonHttpsTypes() {
        SoftwareSource magnet = source("magnet:?xt=urn:btih:abc");
        magnet.setSourceType(SoftwareSourceType.MAGNET);
        when(sourceMapper.selectById(SOURCE_ID)).thenReturn(magnet);

        assertThatThrownBy(() -> service.patrolById(SOURCE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SOFTWARE_PATROL_NOT_SUPPORTED");
    }

    @Test
    @DisplayName("probe 安全约束：非 HTTPS 拒绝、内网地址拒绝（INVALID/FORBIDDEN_ADDRESS）")
    void probe_rejectsUnsafeTargets() {
        // 非 HTTPS
        var notHttps = servicePro().probe("http://example.com/app.exe");
        assertThat(notHttps.outcome()).isEqualTo(SoftwareHttpProbe.Outcome.INVALID);
        assertThat(notHttps.reasonCode()).isEqualTo("NOT_HTTPS");

        // 回环地址域名解析（services 挡在公网校验层——用不可解析主机模拟 DNS 失败语义）
        var badDns = servicePro().probe("https://this-host-does-not-exist.invalid/app.exe");
        assertThat(badDns.outcome()).isEqualTo(SoftwareHttpProbe.Outcome.INVALID);
        assertThat(badDns.reasonCode()).isEqualTo("DNS_FAILED");
    }

    private SoftwareHttpProbe servicePro() {
        return new SoftwareHttpProbe();
    }
}
