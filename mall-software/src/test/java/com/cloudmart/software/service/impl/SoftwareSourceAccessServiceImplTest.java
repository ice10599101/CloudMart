package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareLinkAccessDaily;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SoftwareAccessMode;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwarePlatform;
import com.cloudmart.software.enums.SoftwareProvider;
import com.cloudmart.software.enums.SoftwareSourceType;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.policy.SoftwareLinkAccessPolicy;
import com.cloudmart.software.repository.SoftwareAccessContactMapper;
import com.cloudmart.software.service.SoftwareRateLimiter;
import com.cloudmart.software.repository.SoftwareLinkAccessDailyMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 5.2/5.3/6.9 来源访问单测（A03/A11/A14/A18/A37）：
 * 归属链 stale 409、跨软件拒绝、禁用拒绝、裁决传播、日去重计数、发布者不计。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareSourceAccessServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final Long OWNER_ID = 7L;
    private static final Long SOFTWARE_ID = 100L;
    private static final Long PUBLISHED_REVISION = 501L;
    private static final Long OLD_REVISION = 500L;
    private static final Long RELEASE_ID = 3001L;
    private static final Long SOURCE_ID = 2001L;

    @Mock
    private SoftwareAccessPolicy accessPolicy;
    @Mock
    private SoftwareLinkAccessPolicy linkAccessPolicy;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareReleaseMapper releaseMapper;
    @Mock
    private SoftwareSourceMapper sourceMapper;
    @Mock
    private SoftwareSourceStateMapper sourceStateMapper;
    @Mock
    private SoftwareLinkAccessDailyMapper linkAccessDailyMapper;
    @Mock
    private SoftwareAccessContactMapper accessContactMapper;
    @Mock
    private SoftwareRateLimiter rateLimiter;
    @Mock
    private SoftwareProperties softwareProperties;
    @Mock
    private TransactionTemplate transactionTemplate;

    private SoftwareSourceAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        // TransactionTemplate 直通执行回调
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        lenient().when(rateLimiter.allowResolve(any())).thenReturn(true);
        lenient().when(softwareProperties.isResolveEnabled()).thenReturn(true);
        service = new SoftwareSourceAccessServiceImpl(accessPolicy, linkAccessPolicy,
                softwareMapper, releaseMapper, sourceMapper, sourceStateMapper,
                linkAccessDailyMapper, accessContactMapper, rateLimiter, softwareProperties,
                transactionTemplate);
    }

    private void stubVisibleChain() {
        SoftwareRevision published = new SoftwareRevision();
        published.setId(PUBLISHED_REVISION);
        published.setSoftwareId(SOFTWARE_ID);
        published.setAccessMode(SoftwareAccessMode.REGISTERED);
        when(accessPolicy.visiblePublishedRevision(SOFTWARE_ID)).thenReturn(published);

        SoftwareSource source = source("key-1", "fp-1");
        when(sourceMapper.selectById(SOURCE_ID)).thenReturn(source);
        SoftwareRelease release = new SoftwareRelease();
        release.setId(RELEASE_ID);
        release.setRevisionId(PUBLISHED_REVISION);
        when(releaseMapper.selectById(source.getReleaseId())).thenReturn(release);

        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(OWNER_ID);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(software);

        when(linkAccessPolicy.decide(any(), any(SoftwareRevision.class), eq(SOFTWARE_ID)))
                .thenReturn(SoftwareLinkAccessPolicy.LinkAccessDecision.allowed(null, null, null));
        when(sourceStateMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    }

    private SoftwareSource source(String sourceKey, String fingerprint) {
        SoftwareSource source = new SoftwareSource();
        source.setId(SOURCE_ID);
        source.setReleaseId(RELEASE_ID);
        source.setSourceKey(sourceKey);
        source.setSourceType(SoftwareSourceType.NETDISK);
        source.setProvider(SoftwareProvider.OTHER);
        source.setPlatform(SoftwarePlatform.WINDOWS);
        source.setArch(com.cloudmart.software.enums.SoftwareArch.X64);
        source.setLabel("Windows 64 位");
        source.setUrl("https://pan.baidu.com/s/1abc");
        source.setExtractionCode("a1b2");
        source.setArchivePassword("pwd");
        source.setContentFingerprint(fingerprint);
        return source;
    }

    @Test
    @DisplayName("A03：登录+REGISTERED 放行 → 返回真实链接/提取码/密码与 UNKNOWN 提示")
    void resolve_returnsRealLink() {
        stubVisibleChain();

        var vo = service.resolve(USER_ID, SOFTWARE_ID, SOURCE_ID);

        assertThat(vo.url()).isEqualTo("https://pan.baidu.com/s/1abc");
        assertThat(vo.extractionCode()).isEqualTo("a1b2");
        assertThat(vo.archivePassword()).isEqualTo("pwd");
        assertThat(vo.availability()).isEqualTo("UNKNOWN");
        assertThat(vo.availabilityTip()).isNotBlank();
    }

    @Test
    @DisplayName("A14：旧快照来源 → SOFTWARE_SOURCE_STALE（409），前端刷新来源列表")
    void resolve_staleSourceId() {
        stubVisibleChain();
        SoftwareSource source = source("key-old", "fp-old");
        when(sourceMapper.selectById(SOURCE_ID)).thenReturn(source);
        SoftwareRelease oldRelease = new SoftwareRelease();
        oldRelease.setId(RELEASE_ID);
        oldRelease.setRevisionId(OLD_REVISION);
        when(releaseMapper.selectById(source.getReleaseId())).thenReturn(oldRelease);

        assertThatThrownBy(() -> service.resolve(USER_ID, SOFTWARE_ID, SOURCE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SOURCE_STALE");
    }

    @Test
    @DisplayName("A11：跨软件/不存在来源 → SOFTWARE_NOT_FOUND")
    void resolve_unknownSourceNotFound() {
        stubVisibleChain();
        when(sourceMapper.selectById(SOURCE_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.resolve(USER_ID, SOFTWARE_ID, SOURCE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_NOT_FOUND");
    }

    @Test
    @DisplayName("A10：DISABLED/INVALID 拒绝（SOFTWARE_SOURCE_UNAVAILABLE 409）")
    void resolve_disabledRejected() {
        stubVisibleChain();
        when(sourceStateMapper.selectOne(any(Wrapper.class))).thenReturn(state(SourceAvailability.DISABLED));

        assertThatThrownBy(() -> service.resolve(USER_ID, SOFTWARE_ID, SOURCE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SOURCE_UNAVAILABLE");

        when(sourceStateMapper.selectOne(any(Wrapper.class))).thenReturn(state(SourceAvailability.INVALID));
        assertThatThrownBy(() -> service.resolve(USER_ID, SOFTWARE_ID, SOURCE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_SOURCE_UNAVAILABLE");
    }

    @Test
    @DisplayName("A36：等级不足 → SOFTWARE_LEVEL_REQUIRED 透传，不返回源信息")
    void resolve_policyDenialPropagates() {
        stubVisibleChain();
        when(linkAccessPolicy.decide(any(), any(SoftwareRevision.class), eq(SOFTWARE_ID)))
                .thenReturn(SoftwareLinkAccessPolicy.LinkAccessDecision.denied(
                        "SOFTWARE_LEVEL_REQUIRED", 1, 3, null));

        assertThatThrownBy(() -> service.resolve(USER_ID, SOFTWARE_ID, SOURCE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_LEVEL_REQUIRED");
    }

    @Test
    @DisplayName("A18：非发布者首次获取 → 日事实落库 + 累计计数+1；同日重复不计数")
    void resolve_dailyDedupCounting() {
        stubVisibleChain();

        service.resolve(USER_ID, SOFTWARE_ID, SOURCE_ID);

        ArgumentCaptor<SoftwareLinkAccessDaily> captor =
                ArgumentCaptor.forClass(SoftwareLinkAccessDaily.class);
        verify(linkAccessDailyMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        verify(softwareMapper).incrementLinkAccessCount(SOFTWARE_ID);

        // 同日重复：唯一键冲突 → 静默不计数
        when(linkAccessDailyMapper.insert(any(SoftwareLinkAccessDaily.class)))
                .thenThrow(new DuplicateKeyException("uk daily"));
        service.resolve(USER_ID, SOFTWARE_ID, SOURCE_ID);
        verify(softwareMapper, org.mockito.Mockito.times(1)).incrementLinkAccessCount(SOFTWARE_ID);
    }

    @Test
    @DisplayName("5.3：发布者本人获取不计入计数（榜单口径）")
    void resolve_ownerNotCounted() {
        stubVisibleChain();
        when(accessPolicy.visiblePublishedRevision(SOFTWARE_ID)).thenReturn(publishedRevision());

        service.resolve(OWNER_ID, SOFTWARE_ID, SOURCE_ID);

        verify(linkAccessDailyMapper, never()).insert(any(SoftwareLinkAccessDaily.class));
        verify(softwareMapper, never()).incrementLinkAccessCount(any());
    }

    @Test
    @DisplayName("11.2：获取链接开关关闭时服务端拒绝（A30，深链直达也拿不到）")
    void resolve_disabledBySwitch() {
        when(softwareProperties.isResolveEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.resolve(USER_ID, SOFTWARE_ID, SOURCE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_RESOLVE_DISABLED");
        // 开关先于任何查看条件/计数执行，不得触碰链路
        verify(linkAccessDailyMapper, never()).insert(any(SoftwareLinkAccessDaily.class));
    }

    private SoftwareRevision publishedRevision() {
        SoftwareRevision published = new SoftwareRevision();
        published.setId(PUBLISHED_REVISION);
        published.setSoftwareId(SOFTWARE_ID);
        published.setAccessMode(SoftwareAccessMode.REGISTERED);
        return published;
    }

    private SoftwareSourceState state(SourceAvailability availability) {
        SoftwareSourceState state = new SoftwareSourceState();
        state.setSoftwareId(SOFTWARE_ID);
        state.setSourceKey("key-1");
        state.setCheckedFingerprint("fp-1");
        state.setAvailability(availability);
        return state;
    }
}
