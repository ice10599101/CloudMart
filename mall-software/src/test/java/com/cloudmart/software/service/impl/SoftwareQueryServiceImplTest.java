package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SoftwareListQuery;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SoftwareChannel;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwarePlatform;
import com.cloudmart.software.enums.SoftwareProvider;
import com.cloudmart.software.enums.SoftwareSourceType;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.feign.UserSupportFeignClient;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareAccessContactMapper;
import com.cloudmart.software.repository.SoftwareCategoryMapper;
import com.cloudmart.software.repository.SoftwareFavoriteMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareQueryMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import com.cloudmart.software.repository.SoftwareRevisionTagMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import com.cloudmart.software.repository.SoftwareTagMapper;
import com.cloudmart.software.vo.software.SoftwareCardVO;
import com.cloudmart.software.vo.software.SoftwareDetailVO;
import com.cloudmart.software.vo.software.SoftwareReleaseVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * S03 出口条件单测：草稿/下架/封禁/删除不会从详情泄漏（SOFTWARE_NOT_FOUND 通用提示）；
 * 来源可用性按 4.3 指纹匹配应用（DISABLED 无条件，指纹不匹配回落 UNKNOWN）。
 */
@ExtendWith(MockitoExtension.class)
class SoftwareQueryServiceImplTest {

    @Mock
    private SoftwareAccessPolicy accessPolicy;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private SoftwareCategoryMapper categoryMapper;
    @Mock
    private SoftwareQueryMapper queryMapper;
    @Mock
    private SoftwareReleaseMapper releaseMapper;
    @Mock
    private SoftwareSourceMapper sourceMapper;
    @Mock
    private SoftwareSourceStateMapper sourceStateMapper;
    @Mock
    private SoftwareRevisionTagMapper revisionTagMapper;
    @Mock
    private SoftwareTagMapper tagMapper;
    @Mock
    private SoftwareRevisionMediaMapper revisionMediaMapper;
    @Mock
    private SoftwareFavoriteMapper favoriteMapper;
    @Mock
    private com.cloudmart.software.repository.SoftwareRatingMapper ratingMapper;
    @Mock
    private com.cloudmart.software.repository.SoftwareSubscriptionMapper subscriptionMapper;
    @Mock
    private SoftwareAccessContactMapper accessContactMapper;
    @Mock
    private com.cloudmart.software.repository.SoftwareRelationMapper relationMapper;
    @Mock
    private UserSupportFeignClient userSupportFeignClient;
    @Mock
    private com.cloudmart.software.service.SoftwareSnapshotAssembler snapshotAssembler;

    @InjectMocks
    private SoftwareQueryServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void stubAssembler() {
        org.mockito.Mockito.lenient()
                .when(snapshotAssembler.deserializeHighlights(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
    }

    private Software visibleSoftware() {
        Software software = new Software();
        software.setId(100L);
        software.setOwnerId(7L);
        software.setLifecycle(SoftwareLifecycle.ACTIVE);
        software.setPublishedRevisionId(501L);
        software.setFavoriteCount(3);
        software.setCommentCount(9);
        return software;
    }

    private SoftwareRevision approvedRevision() {
        SoftwareRevision revision = new SoftwareRevision();
        revision.setId(501L);
        revision.setSoftwareId(100L);
        revision.setName("示例效率工具");
        revision.setSummary("用于整理工作资料");
        revision.setAccessMode(com.cloudmart.software.enums.SoftwareAccessMode.REGISTERED);
        return revision;
    }

    private SoftwareSource source(String sourceKey, String fingerprint) {
        SoftwareSource source = new SoftwareSource();
        source.setId(2001L);
        source.setReleaseId(3001L);
        source.setSourceKey(sourceKey);
        source.setSourceType(SoftwareSourceType.NETDISK);
        source.setProvider(SoftwareProvider.OTHER);
        source.setPlatform(SoftwarePlatform.WINDOWS);
        source.setArch(com.cloudmart.software.enums.SoftwareArch.X64);
        source.setLabel("Windows 64 位");
        source.setContentFingerprint(fingerprint);
        return source;
    }

    private SoftwareSourceState state(String sourceKey, String fingerprint,
                                      SourceAvailability availability) {
        SoftwareSourceState state = new SoftwareSourceState();
        state.setSoftwareId(100L);
        state.setSourceKey(sourceKey);
        state.setCheckedFingerprint(fingerprint);
        state.setAvailability(availability);
        return state;
    }

    @Test
    @DisplayName("S03 出口条件：下架/封禁/删除/草稿软件详情一律 SOFTWARE_NOT_FOUND，不泄露状态详情")
    void detail_invisibleSoftwareThrowsNotFound() {
        when(accessPolicy.visibleSoftware(100L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(100L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_NOT_FOUND");

        // lifecycle=ACTIVE 但 publishedRevision 为 null（仅草稿）同样不可见
        Software software = visibleSoftware();
        software.setPublishedRevisionId(null);
        lenient().when(accessPolicy.visibleSoftware(100L)).thenReturn(software);
        when(accessPolicy.visiblePublishedRevision(100L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(100L, 42L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_NOT_FOUND");
    }

    @Test
    @DisplayName("详情：游客 isFavorited=null；分享者资料失败降级为 null（不阻断详情）")
    void detail_guestAndDegradeOwnerProfile() {
        when(accessPolicy.visibleSoftware(100L)).thenReturn(visibleSoftware());
        when(accessPolicy.visiblePublishedRevision(100L)).thenReturn(approvedRevision());
        when(revisionTagMapper.selectList(any())).thenReturn(List.of());
        when(revisionMediaMapper.selectList(any())).thenReturn(List.of());
        when(userSupportFeignClient.profiles(any()))
                .thenThrow(new BusinessException("USER_SERVICE_UNAVAILABLE", "down"));

        SoftwareDetailVO detail = service.detail(100L, null);

        assertThat(detail.name()).isEqualTo("示例效率工具");
        assertThat(detail.isFavorited()).isNull();
        assertThat(detail.ownerNickname()).isNull();
        assertThat(detail.ownerAvatar()).isNull();
    }

    @Test
    @DisplayName("releases：软件不可见同样 SOFTWARE_NOT_FOUND（版本列表不泄漏草稿/下架内容）")
    void releases_invisibleSoftwareThrowsNotFound() {
        when(accessPolicy.visiblePublishedRevision(100L)).thenReturn(null);

        assertThatThrownBy(() -> service.releases(100L, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOFTWARE_NOT_FOUND");
    }

    @Test
    @DisplayName("4.3 可用性：DISABLED 无条件；指纹一致应用核实结论；不一致回落 UNKNOWN")
    @SuppressWarnings("unchecked")
    void releases_availabilityByFingerprint() {
        when(accessPolicy.visiblePublishedRevision(100L)).thenReturn(approvedRevision());

        SoftwareRelease release = new SoftwareRelease();
        release.setId(3001L);
        release.setRevisionId(501L);
        release.setReleaseKey("r-key");
        release.setVersionName("1.0.0");
        release.setChannel(SoftwareChannel.STABLE);
        release.setRecommended(true);
        Page<SoftwareRelease> releasePage = new Page<>(1, 20, 1);
        releasePage.setRecords(List.of(release));
        when(releaseMapper.selectPage(any(), any())).thenReturn(releasePage);

        // 三个来源：禁用（指纹过期仍禁用）、指纹一致可用、指纹不匹配（结论不生效）
        SoftwareSource disabled = source("key-disabled", "fp-old");
        disabled.setId(1L);
        SoftwareSource verified = source("key-ok", "fp-match");
        verified.setId(2L);
        SoftwareSource stale = source("key-stale", "fp-current");
        stale.setId(3L);
        when(sourceMapper.selectList(any())).thenReturn(List.of(disabled, verified, stale));
        when(sourceStateMapper.selectList(any())).thenReturn(List.of(
                state("key-disabled", "fp-other", SourceAvailability.DISABLED),
                state("key-ok", "fp-match", SourceAvailability.AVAILABLE),
                state("key-stale", "fp-stale", SourceAvailability.INVALID)));

        Page<SoftwareReleaseVO> result = service.releases(100L, 1, 20);

        List<String> availability = result.getRecords().get(0).sources().stream()
                .map(s -> s.availability()).toList();
        assertThat(availability).containsExactly("DISABLED", "AVAILABLE", "UNKNOWN");
    }

    @Test
    @DisplayName("列表：过滤与分页下推 SQL（Mapper 脚本），无链接字段进入卡片 VO")
    @SuppressWarnings("unchecked")
    void pageCards_returnsCardVoWithoutSecrets() {
        SoftwareQueryMapper.CardRow row = new SoftwareQueryMapper.CardRow(
                100L, "示例工具", "整理资料", 50L, "FREE", 2L, "效率办公",
                java.time.LocalDateTime.now(), 3, 9, 0, 0);
        Page<SoftwareQueryMapper.CardRow> rows = new Page<>(1, 20, 1);
        rows.setRecords(List.of(row));
        when(queryMapper.pageCards(any(Page.class), any(SoftwareListQuery.class))).thenReturn(rows);
        when(softwareMapper.selectBatchIds(any())).thenReturn(List.of(visibleSoftware()));
        when(queryMapper.platformsOfRevisions(any())).thenReturn(List.of());

        SoftwareListQuery query = new SoftwareListQuery();
        Page<SoftwareCardVO> result = service.pageCards(query);

        assertThat(result.getRecords()).hasSize(1);
        SoftwareCardVO card = result.getRecords().get(0);
        assertThat(card.name()).isEqualTo("示例工具");
        assertThat(card.ratingAvg()).isNull(); // 零票显示暂无评分（null 而非 0）
        // 卡片 VO 契约：不存在 url/extractionCode 等秘密字段（编译期由 record 定义保证）
        assertThat(card.platforms()).isEmpty();
    }
}
